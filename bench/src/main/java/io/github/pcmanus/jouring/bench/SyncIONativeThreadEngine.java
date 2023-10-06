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

public class SyncIONativeThreadEngine extends SyncEngine {
    private record ThreadState(Map<Path, FileChannel> channels, ByteBuffer buffer) {}
    private static final ThreadLocal<ThreadState> threadState = new ThreadLocal<>();

    SyncIONativeThreadEngine(
            BiConsumer<ReadTask, ByteBuffer> completedTaskCallback,
            Benchmark.Parameters parameters
    ) {
        super(completedTaskCallback, parameters);
    }

    @Override
    protected ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(parameters.threads());
    }

    private ThreadState getState() {
        var state = threadState.get();
        if (state == null) {
            var channels = new HashMap<Path, FileChannel>();
            parameters.files().forEach(path -> {
                try {
                    channels.put(path, FileChannel.open(path, StandardOpenOption.READ));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            state = new ThreadState(channels, ByteBuffer.allocate(parameters.blockSize()));
            threadState.set(state);
        }
        return state;
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        ThreadState state = getState();
        FileChannel channel = state.channels.get(task.file());
        assert channel != null;
        ByteBuffer dup = state.buffer.duplicate();
        try {
            channel.read(dup, task.offset());
        } catch (IOException e) {
            System.err.printf("Error executing %s: %s", task, e);
        }
        dup.flip();
        return dup;
    }


}
