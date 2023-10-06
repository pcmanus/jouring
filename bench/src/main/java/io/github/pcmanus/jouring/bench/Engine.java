package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class Engine {
    private final BiConsumer<ReadTask, ByteBuffer> completedTaskCallback;
    protected final Benchmark.Parameters parameters;

    protected Engine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        this.completedTaskCallback = completedTaskCallback;
        this.parameters = parameters;
    }

    protected void ack(ReadTask task,  ByteBuffer result) {
        completedTaskCallback.accept(task,  result);
    }

    public abstract void execute(Stream<ReadTask> tasks);

    public interface Ctor {
        Engine create(
                BiConsumer<ReadTask, ByteBuffer> completedTaskCallback,
                Benchmark.Parameters parameters
        );
    }
}
