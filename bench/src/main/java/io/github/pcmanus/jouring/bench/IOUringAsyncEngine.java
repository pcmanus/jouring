package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class IOUringAsyncEngine extends AsyncEngine {
    private final IOUringWrapper wrapper;

    protected IOUringAsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new IOUringWrapper(parameters);
    }

    @Override
    protected CompletableFuture<ByteBuffer> submitTask(ReadTask task) {
        return wrapper.read(task,  ByteBuffer.allocateDirect(parameters.blockSize()));
    }
}
