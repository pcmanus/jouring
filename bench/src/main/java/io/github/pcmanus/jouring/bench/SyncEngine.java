package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class SyncEngine extends Engine  {
    protected SyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    protected abstract ExecutorService createExecutorService();
    protected abstract ByteBuffer handleTask(ReadTask task);

    protected void onFinished() {}

    public void execute(Stream<ReadTask> tasks) {
        try (var executor = createExecutorService()) {
            tasks.forEach(task -> executor.submit(() -> {
                try {
                    ack(task, handleTask(task));
                } catch (Exception e) {
                    System.err.printf("Error executing %s: %s%n", task, e);
                }
            }));
        }
        this.onFinished();
    }
}
