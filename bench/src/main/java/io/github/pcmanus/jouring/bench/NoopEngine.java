package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class NoopEngine extends Engine {
    private final ByteBuffer fakeRead;

    NoopEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.fakeRead = ByteBuffer.allocateDirect(parameters.blockSize());
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        return fakeRead;
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newSingleThreadExecutor();
    }
}
