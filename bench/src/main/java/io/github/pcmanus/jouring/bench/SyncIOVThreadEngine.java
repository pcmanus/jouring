package io.github.pcmanus.jouring.bench;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class SyncIOVThreadEngine extends SyncEngine {
    private final Map<Path, FileChannel> channels = new HashMap<>();

    protected SyncIOVThreadEngine(
            BiConsumer<ReadTask, ByteBuffer> completedTaskCallback,
            Benchmark.Parameters parameters
    ) {
        super(completedTaskCallback, parameters);
        parameters.files().forEach(path -> {
            try {
                channels.put(path, FileChannel.open(path, StandardOpenOption.READ));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        FileChannel channel = channels.get(task.file());
        assert channel != null;
        ByteBuffer buffer = ByteBuffer.allocate(parameters.blockSize());
        try {
            channel.read(buffer, task.offset());
        } catch (IOException e) {
            System.err.printf("Error executing %s: %s", task, e);
        }
        buffer.flip();
        return buffer;
    }
}
