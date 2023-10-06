package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class AsyncEngine extends Engine {
    protected AsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    protected abstract CompletableFuture<ByteBuffer> submitTask(ReadTask task);

    public void execute(Stream<ReadTask> tasks) {
        CountDownLatch latch = new CountDownLatch(parameters.readCount());

        tasks.forEach(task -> submitTask(task).thenAcceptAsync(buffer -> {
            ack(task, buffer);
        }).whenComplete((_void, exception) -> {
            if (exception != null) {
                System.err.printf("Unexpected error for %s: %s%n", task, exception);
            }
            latch.countDown();
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
