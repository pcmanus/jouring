package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class IOUringNativeThreadEngine extends SyncEngine {
    private final IOUringWrapper wrapper;
    private static final ThreadLocal<ByteBuffer> buffers = new ThreadLocal<>();

    protected IOUringNativeThreadEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new IOUringWrapper(parameters);
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(parameters.threads());
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        try {
            ByteBuffer buffer = buffers.get();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(parameters.blockSize());
                buffers.set(buffer);
            }
            ByteBuffer dup = buffer.duplicate();

            return wrapper.read(task, dup).get();
        } catch (Exception e) {
            System.err.printf("Error submitting %s:%s%n", task, e);
            throw new RuntimeException(e);
        }
    }
}
