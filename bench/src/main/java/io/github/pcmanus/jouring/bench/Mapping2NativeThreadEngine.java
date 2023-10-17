package io.github.pcmanus.jouring.bench;

import com.indeed.util.mmap.MMapBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class Mapping2NativeThreadEngine extends SyncEngine {
    private final Map<Path, MMapBuffer> mappedBuffers = new HashMap<>();
    private static final ThreadLocal<ByteBuffer> perThreadBuffers = new ThreadLocal<>();

    protected Mapping2NativeThreadEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        parameters.files().forEach(path -> {
            try {
                mappedBuffers.put(
                        path,
                        new MMapBuffer(path, FileChannel.MapMode.READ_ONLY, ByteOrder.BIG_ENDIAN)
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(parameters.threads());
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        MMapBuffer mappedBuffer = mappedBuffers.get(task.file());
        assert mappedBuffer != null;
        ByteBuffer buffer = perThreadBuffers.get();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(parameters.blockSize());
            perThreadBuffers.set(buffer);
        }
        ByteBuffer dup = buffer.duplicate();
        byte[] array = dup.array();
        int length = (int) Math.min(parameters.blockSize(), mappedBuffer.memory().length() - task.offset());
        mappedBuffer.memory().getBytes(task.offset(), array, 0, length);
        dup.limit(length);
        return dup;
    }
}
