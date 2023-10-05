package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

abstract class AbstractMappingEngine extends Engine {
    private final Map<Path, MappedFile> mappedFiles = new HashMap<>();

    AbstractMappingEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        parameters.files().forEach(path -> mappedFiles.put(path, MappedFile.map(path)));
    }

    @Override
    protected ByteBuffer handleTask(ReadTask task) {
        MappedFile mappedFile = mappedFiles.get(task.file());
        assert mappedFile != null;
        return mappedFile.read(task.offset(), (int) Math.min(parameters.blockSize(), mappedFile.length - task.offset()));
    }
}
