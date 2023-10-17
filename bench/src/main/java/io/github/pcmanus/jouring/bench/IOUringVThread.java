package io.github.pcmanus.jouring.bench;

import io.github.pcmanus.jouring.IOExecutor;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class IOUringVThread extends Engine {
    protected IOUringVThread(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
    }

    static IOExecutor initIOExecutor(Benchmark.Parameters parameters) {
        var builder = IOExecutor
                .builder()
                .depth(parameters.depth())
                .bufferSize(parameters.blockSize())
                .useNalim(parameters.useNalim());
        return parameters.ringCount() == 1 ? builder.build() : builder.buildMulti(parameters.ringCount());
    }

    @Override
    public void execute(Stream<ReadTask> tasks) {
        // Order matter: we want to wait on the close of the executor first, that is to wait that all tasks are finished
        // before we close the loop.
        try (IOExecutor ioExecutor = initIOExecutor(this.parameters);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()
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
