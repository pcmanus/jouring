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

    protected abstract ExecutorService createExecutorService();

    protected abstract ByteBuffer handleTask(ReadTask task);

    public void execute(Stream<ReadTask> tasks) {
        try (var executor = createExecutorService()) {
            tasks.forEach(task -> executor.submit(() -> {
                try {
                    completedTaskCallback.accept(task, handleTask(task));
                } catch (Exception e) {
                    System.err.printf("Error executing %s: %s%n", task, e);
                }
            }));
        }
    }

    public interface Ctor {
        Engine create(
                BiConsumer<ReadTask, ByteBuffer> completedTaskCallback,
                Benchmark.Parameters parameters
        );
    }
}
