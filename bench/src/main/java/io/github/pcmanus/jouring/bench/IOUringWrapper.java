package io.github.pcmanus.jouring.bench;

import one.jasyncfio.AsyncFile;
import one.jasyncfio.EventExecutor;
import one.jasyncfio.OpenOption;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IOUringWrapper {
    private final EventExecutor executor;
    private final Map<Path, AsyncFile> asyncFiles = new HashMap<>();

    IOUringWrapper(Benchmark.Parameters parameters) {
        this.executor = EventExecutor.builder()
                .entries(128)
                .ioRingSetupIoPoll()
                .build();
        parameters.files().forEach(path -> {
            try {
                asyncFiles.put(path, AsyncFile.open(path, executor, OpenOption.READ_ONLY, OpenOption.NOATIME).get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<ByteBuffer> read(ReadTask task, ByteBuffer buffer) {
        AsyncFile file = asyncFiles.get(task.file());
        assert file != null;

        return file.read(buffer, task.offset()).thenApply(readBytes -> {
            buffer.flip();
            return buffer;
        });
    }
}
