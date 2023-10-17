package io.github.pcmanus.jouring;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

class EventLoop implements IOExecutor {
    private static final int QUEUE_CHUNKS = 4096;
    private final Thread loopThread = new Thread(this::run, "EventLoop Thread");
    private final MessagePassingQueue<ReadCommand> queue = new MpscUnboundedArrayQueue<>(QUEUE_CHUNKS);
    private final Ring ring;
    private final BufferPool pool;
    private final FileDescriptorCache fdCache = new FileDescriptorCache();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final int bufferSize;
    private final ReadCommand[] submittedCommands;
    private int submittedIdx;

    EventLoop(Ring ring, int bufferSize) {
        int depth = ring.depth();
        this.bufferSize = bufferSize;
        this.ring = ring;
        this.pool = new BufferPool(depth * 3, bufferSize);
        this.submittedCommands = new ReadCommand[this.ring.maxInFlight()];
        this.loopThread.start();
    }

    public PooledBuffer read(Path file, long offset, int length) {
        assert length <= this.bufferSize : "Can read at most " + this.bufferSize + " bytes at once";

        ReadCommand cmd = new ReadCommand(Thread.currentThread(), null, fdCache.fd(file), offset, length);
        boolean offered = queue.offer(cmd);
        assert offered: "Queue is unbounded or what?";

        do {
            LockSupport.park();
        } while (!cmd.completed);

        assert cmd.buffer != null;
        return cmd.buffer;
    }

    public CompletableFuture<PooledBuffer> readAsync(Path file, long offset, int length) {
        assert length <= this.bufferSize : "Can read at most " + this.bufferSize + " bytes at once";

        ReadCommand cmd = new ReadCommand(null, new CompletableFuture<>(), fdCache.fd(file), offset, length);
        boolean offered = queue.offer(cmd);
        assert offered: "Queue is unbounded or what?";

        return cmd.future;
    }

    private int nextId() {
        for (int i = 0; i < ring.maxInFlight(); i++) {
            int candidate = submittedIdx;
            submittedIdx = (submittedIdx + 1) % ring.maxInFlight();
            if (this.submittedCommands[candidate] == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("Couldn't acquire an ID");
    }

    private void run() {
        for (;;) {
            int room = ring.roomAvailable();
            if (room > 0) {
                queue.drain(cmd -> {
                    PooledBuffer buffer = pool.borrow();
                    cmd.buffer = buffer;
                    int id = this.nextId();
                    submittedCommands[id] = cmd;
                    ring.submissions().addRead(id, cmd.fd, buffer.address(), cmd.offset, cmd.length);
                }, room);
            }
            if (ring.inFlight() == 0 && ring.submissions().isEmpty()) {
                if (stopped.get()) {
                    break;
                }
                // We have nothing that could be completed, and we have nothing in the queue either.
                // TODO: can do better
                LockSupport.parkNanos(100);
            } else {
                ring.submitAndCheckCompletions();
                int completed = ring.lastCompleted();
                for (int i = 0; i < completed; i++) {
                    int id = (int) ring.completed(i);
                    ReadCommand cmd = submittedCommands[id];
                    assert cmd != null : "No command at " + id;
                    assert !cmd.completed : "Command at " + id + " is already completed";
                    cmd.completed = true;
                    if (cmd.callingThread != null) {
                        LockSupport.unpark(cmd.callingThread);
                    } else {
                        assert cmd.future != null;
                        cmd.future.complete(cmd.buffer);
                    }
                    submittedCommands[id] = null;
                }
            }
        }

        try {
            fdCache.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ring.close();
    }

    @Override
    public void close() throws Exception {
        this.stopped.set(true);
        this.loopThread.join();
    }

    private static class ReadCommand {
        private final Thread callingThread;
        private final CompletableFuture<PooledBuffer> future;
        private final int fd;
        private final long offset;
        private final int length;

        private volatile boolean completed;
        private volatile PooledBuffer buffer;

        ReadCommand(Thread callingThread, CompletableFuture<PooledBuffer> future, int fd, long offset, int length) {
            this.callingThread = callingThread;
            this.future = future;
            this.fd = fd;
            this.offset = offset;
            this.length = length;
        }
    }

}
