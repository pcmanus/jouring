package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class JasyncAsyncEngine extends AsyncEngine<ByteBuffer> {
    private final JasyncfioWrapper wrapper;

    protected JasyncAsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new JasyncfioWrapper(parameters);
    }

    @Override
    protected CompletableFuture<ByteBuffer> submitTask(ReadTask task) {
        return wrapper.read(task,  ByteBuffer.allocateDirect(parameters.blockSize()));
    }

    @Override
    protected ByteBuffer buffer(ByteBuffer byteBuffer) {
        return byteBuffer;
    }
}
