package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class IOUringMultiRingEgnine extends AsyncEngine {

    private final List<IOUringWrapper> loops;
    private int idx;

    protected IOUringMultiRingEgnine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        int count = parameters.threads();
        this.loops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.loops.add(new IOUringWrapper(parameters));
        }
    }

    @Override
    protected CompletableFuture<ByteBuffer> submitTask(ReadTask task) {
        IOUringWrapper wrapper = loops.get((idx++) % loops.size());
        return wrapper.read(task,  ByteBuffer.allocateDirect(parameters.blockSize()));
    }
}
