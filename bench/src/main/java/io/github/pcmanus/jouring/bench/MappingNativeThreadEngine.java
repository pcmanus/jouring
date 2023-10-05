package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class MappingNativeThreadEngine extends AbstractMappingEngine {
    MappingNativeThreadEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(parameters.threads());
    }
}
