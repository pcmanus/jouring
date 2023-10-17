package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class JasyncfioVThreadEngine extends SyncEngine {
    private final JasyncfioWrapper wrapper;

    protected JasyncfioVThreadEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new JasyncfioWrapper(parameters);
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(parameters.blockSize());
            return wrapper.read(task, buffer).get();
        } catch (Exception e) {
            System.err.printf("Error submitting %s:%s%n", task, e);
            return ByteBuffer.allocate(0);
        }
    }
}
