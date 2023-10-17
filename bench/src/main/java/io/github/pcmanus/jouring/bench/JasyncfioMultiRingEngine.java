package io.github.pcmanus.jouring.bench;

import io.github.pcmanus.jouring.bench.JasyncfioWrapper.TaskSupplier;
import one.jasyncfio.MemoryUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class JasyncfioMultiRingEngine extends Engine {
    protected JasyncfioMultiRingEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
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
        private final JasyncfioWrapper wrapper;
        private final TaskSupplier taskSupplier;

        private Worker(TaskSupplier taskSupplier) {
            this.wrapper = new JasyncfioWrapper(parameters);
            this.taskSupplier = taskSupplier;
        }

        @Override
        public void run() {
            int depth = parameters.depth();
            Deque<ByteBuffer> buffers = new ArrayDeque<>(depth);
            for (int i = 0; i < depth; i++) {
                buffers.add(MemoryUtils.allocateAlignedByteBuffer(parameters.blockSize(), MemoryUtils.getPageSize()));
            }

            wrapper.batchSubmit(
                    taskSupplier,
                    buffers::poll,
                    (task, result, toReturn) -> {
                        ack(task, result);
                        buffers.add(toReturn);
                    }
            );
        }
    }
}
