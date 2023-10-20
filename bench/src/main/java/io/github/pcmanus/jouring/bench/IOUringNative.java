package io.github.pcmanus.jouring.bench;

import io.github.pcmanus.jouring.IOExecutor;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.github.pcmanus.jouring.bench.IOUringVThread.initIOExecutor;

public class IOUringNative extends Engine {
    protected IOUringNative(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    @Override
    public void execute(Stream<ReadTask> tasks) {
        try (IOExecutor ioExecutor = initIOExecutor(this.parameters);
             ExecutorService executor = Executors.newFixedThreadPool(parameters.threads() - 1)
        ) {
            tasks.forEach(task -> {
                executor.submit(() -> {
                    try (var buffer = ioExecutor.read(task.file(), task.offset(), parameters.blockSize())) {
                        ack(task, buffer.asByteBuffer());
                    }
                });
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
