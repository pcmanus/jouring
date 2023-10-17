package io.github.pcmanus.jouring.bench;

import io.github.pcmanus.jouring.IOExecutor;
import io.github.pcmanus.jouring.PooledBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class IOUringAsync extends AsyncEngine<PooledBuffer> {
    private final IOExecutor ioExecutor;

    protected IOUringAsync(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.ioExecutor = IOUringVThread.initIOExecutor(parameters);
    }

    @Override
    protected CompletableFuture<PooledBuffer> submitTask(ReadTask task) {
        return ioExecutor.readAsync(task.file(), task.offset(), parameters.blockSize());
    }

    @Override
    protected ByteBuffer buffer(PooledBuffer buffer) {
        return buffer.asByteBuffer();
    }

    @Override
    protected void afterAck(PooledBuffer buffer) {
        buffer.close();
    }
}
