package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class AsyncEngine<SubmitRes> extends Engine {
    protected AsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    protected abstract CompletableFuture<SubmitRes> submitTask(ReadTask task);

    protected abstract ByteBuffer buffer(SubmitRes res);

    protected void afterAck(SubmitRes res) {}

    @Override
    public void execute(Stream<ReadTask> tasks) {
        CountDownLatch latch = new CountDownLatch(parameters.readCount());

        try (ExecutorService completionExecutor = Executors.newFixedThreadPool(this.parameters.threads() - this.parameters.ringCount())) {
            tasks.forEach(task -> submitTask(task)
                    .thenAcceptAsync(res -> {
                        ack(task, buffer(res));
                        afterAck(res);
                    }, completionExecutor)
                    .whenComplete((_void, exception) -> {
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
}
