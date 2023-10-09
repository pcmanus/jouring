package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class IOUringMultiRingEngine extends Engine {
    protected IOUringMultiRingEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    @Override
    public void execute(Stream<ReadTask> tasks) {
        Iterator<ReadTask> iter = tasks.iterator();
        final ReentrantLock lock = new ReentrantLock();
        TaskSupplier taskSupplier = (count, out) -> {
            lock.lock();
            try {
                int i = 0;
                while (i < count && iter.hasNext()) {
                    out[i++] = iter.next();
                }
                return i;
            } finally {
                lock.unlock();
            }
        };

        List<Worker> workers = new ArrayList<>(parameters.threads());
        for (int i = 0; i < parameters.threads(); i++) {
            workers.add(new Worker(taskSupplier));
        }

        workers.forEach(Thread::start);
        workers.forEach(worker -> {
            try {
                worker.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private class Worker extends Thread {
        private final IOUringWrapper wrapper;
        private final TaskSupplier taskSupplier;

        private Worker(TaskSupplier taskSupplier) {
            this.wrapper = new IOUringWrapper(parameters);
            this.taskSupplier = taskSupplier;
        }

        @Override
        public void run() {
            int depth = wrapper.depth();
            Deque<ByteBuffer> buffers = new ArrayDeque<>(depth);
            for (int i = 0; i < depth; i++) {
                buffers.add(ByteBuffer.allocateDirect(parameters.blockSize()));
            }

            List<CompletableFuture<ByteBuffer>> submissions = new ArrayList<>(depth);
            List<ReadTask> submissionsTasks = new ArrayList<>(depth);
            List<ByteBuffer> submissionsBuffers = new ArrayList<>(depth);

            ReadTask[] tasks = new ReadTask[depth];

            int[] submissionsToClear = new int[depth];
            int inFlight = 0;
            boolean done = false;
            while (!done) {
                int toPrep;
                if (inFlight < depth) {
                    toPrep = depth - inFlight;
                    int available = taskSupplier.transferTask(toPrep, tasks);
                    if (available == 0) {
                        done = true;
                    } else {
                        for (int i = 0; i < available; i++) {
                            ReadTask task = tasks[i];
                            ByteBuffer buffer = buffers.poll();
                            assert buffer != null;
                            CompletableFuture<ByteBuffer> future = wrapper.read(task, buffer.duplicate());
                            submissions.add(future);
                            submissionsTasks.add(task);
                            submissionsBuffers.add(buffer);
                            inFlight++;
                        }
                    }
                }
                int toClear = 0;
                for (int i = 0; i < submissions.size(); i++) {
                    CompletableFuture<ByteBuffer> future = submissions.get(i);
                    if (future.isDone()) {
                        ReadTask task = submissionsTasks.get(i);
                        try {
                            ByteBuffer buffer = future.get();
                            // Note that the buffer from the future is typically a dupe of the buffer we passed initially,
                            // so let's restock on the original buffer for sanity (even if they are admittedly the same thing).
                            ack(task, buffer);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        buffers.add(submissionsBuffers.get(i));
                        submissionsToClear[toClear++] = i;
                        --inFlight;
                    }
                }

                IOUringBatchedAsyncEngine.clearAll(submissions, submissionsToClear, toClear);
                IOUringBatchedAsyncEngine.clearAll(submissionsTasks, submissionsToClear, toClear);
                IOUringBatchedAsyncEngine.clearAll(submissionsBuffers, submissionsToClear, toClear);
            }

            // After we've submitted everything, just wait on any remaining submissions
            for (int i = 0; i < submissions.size(); i++) {
                CompletableFuture<ByteBuffer> future = submissions.get(i);
                ReadTask task = submissionsTasks.get(i);
                try {
                    ByteBuffer buffer = future.get();
                    ack(task, buffer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    interface TaskSupplier {
        int transferTask(int count, ReadTask[] tasks);
    }
}
