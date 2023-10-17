package io.github.pcmanus.jouring.bench;

import one.jasyncfio.MemoryUtils;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class JasyncfioBatchedAsyncEngine extends Engine {
    private static final int BUFFER_SIZE_MULTIPLIER = 3;
    private final JasyncfioWrapper wrapper;
    private final MessagePassingQueue<ByteBuffer> bufferQueue;

    protected JasyncfioBatchedAsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new JasyncfioWrapper(parameters);
        this.bufferQueue = new MpscArrayQueue<>(parameters.depth() * BUFFER_SIZE_MULTIPLIER);
    }

    private ByteBuffer borrowBuffer() {
        ByteBuffer buffer;
        while ((buffer = bufferQueue.poll()) == null) {
            // Completions are lagging behind; spin-waiting on them
            Thread.onSpinWait();
        }
        return buffer;
    }

    private void returnBuffer(ByteBuffer buffer) {
        // We know we have room for it since the queue has exactly the capacity of the total number of buffers we
        // juggle with.
        bufferQueue.offer(buffer);
    }

    @Override
    public void execute(Stream<ReadTask> tasks) {
        int depth = this.parameters.depth();
        for (int i = 0; i < depth * BUFFER_SIZE_MULTIPLIER; i++) {
            bufferQueue.offer(MemoryUtils.allocateAlignedByteBuffer(parameters.blockSize(), MemoryUtils.getPageSize()));
        }
        try (ExecutorService completionExecutor = Executors.newFixedThreadPool(parameters.threads() - 1)) {
            Iterator<ReadTask> iter = tasks.iterator();
            wrapper.batchSubmit(
                    (count, out) -> {
                        int i = 0;
                        while (i < count && iter.hasNext()) {
                            out[i++] = iter.next();
                        }
                        return i;
                    },
                    this::borrowBuffer,
                    (task, result, toReturn) -> {
                        completionExecutor.submit(() -> {
                            ack(task, result);
                            // Note that we're basically sure to have room for it.
                            returnBuffer(toReturn);
                        });
                    }
             );
        }
    }

}
