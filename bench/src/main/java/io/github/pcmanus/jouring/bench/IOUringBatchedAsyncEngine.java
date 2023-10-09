package io.github.pcmanus.jouring.bench;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class IOUringBatchedAsyncEngine extends Engine {
    private static final int BUFFER_SIZE_MULTIPLIER = 3;
    private final IOUringWrapper wrapper;
    private final MessagePassingQueue<ByteBuffer> bufferQueue;

    protected IOUringBatchedAsyncEngine(BiConsumer<ReadTask, ByteBuffer> completedTaskCallback, Benchmark.Parameters parameters) {
        super(completedTaskCallback, parameters);
        this.wrapper = new IOUringWrapper(parameters);
        this.bufferQueue = new MpscArrayQueue<>(this.wrapper.depth() * BUFFER_SIZE_MULTIPLIER);
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
        int depth = wrapper.depth();
        for (int i = 0; i < depth * BUFFER_SIZE_MULTIPLIER; i++) {
            bufferQueue.offer(ByteBuffer.allocateDirect(parameters.blockSize()));
        }
        try (ExecutorService completionExecutor = Executors.newFixedThreadPool(parameters.threads() - 1)) {
            List<CompletableFuture<ByteBuffer>> submissions = new ArrayList<>(depth);
            List<ReadTask> submissionsTasks = new ArrayList<>(depth);
            List<ByteBuffer> submissionsBuffers = new ArrayList<>(depth);

            int[] submissionsToClear = new int[depth];
            int inFlight = 0;
            Iterator<ReadTask> iter = tasks.iterator();
            while (iter.hasNext()) {
                int toPrep;
                if (inFlight < depth) {
                    toPrep = depth - inFlight;
                    for (int i = 0; i < toPrep && iter.hasNext(); i++) {
                        ReadTask task = iter.next();
                        ByteBuffer buffer = borrowBuffer();
                        CompletableFuture<ByteBuffer> future = wrapper.read(task, buffer.duplicate());
                        submissions.add(future);
                        submissionsTasks.add(task);
                        submissionsBuffers.add(buffer);
                        inFlight++;
                    }
                }
                int toClear = 0;
                for (int i = 0; i < submissions.size(); i++) {
                    CompletableFuture<ByteBuffer> future = submissions.get(i);
                    if (future.isDone()) {
                        ReadTask task = submissionsTasks.get(i);
                        try {
                            ByteBuffer buffer = future.get();
                            // Note that the buffer from the future is typically a dupe of the buffer we passed initially,
                            // so let's restock on the original buffer for sanity (even if they are admittedly the same thing).
                            ByteBuffer toReturn = submissionsBuffers.get(i);
                            completionExecutor.submit(() -> {
                                ack(task, buffer);
                                // Note that we're basically sure to have room for it.
                                returnBuffer(toReturn);
                            });
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        submissionsToClear[toClear++] = i;
                        --inFlight;
                    }
                }

                clearAll(submissions, submissionsToClear, toClear);
                clearAll(submissionsTasks, submissionsToClear, toClear);
                clearAll(submissionsBuffers, submissionsToClear, toClear);
            }

            // After we've submitted everything, just wait on any remaining submissions
            for (int i = 0; i < submissions.size(); i++) {
                CompletableFuture<ByteBuffer> future = submissions.get(i);
                ReadTask task = submissionsTasks.get(i);
                try {
                    ByteBuffer buffer = future.get();
                    ack(task, buffer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static <T> void clearAll(List<T> list, int[] clearIndices, int clearCount) {
        if (clearCount == 0) {
            return;
        }

        int clearIdx = 0;
        int listIdx = clearIndices[clearIdx++];
        int nextIdxToCopy = listIdx + 1;
        for (; clearIdx < clearCount; clearIdx++) {
            int nextIdxToClear = clearIndices[clearIdx];
            for (; nextIdxToCopy < nextIdxToClear; nextIdxToCopy++) {
                list.set(listIdx++, list.get(nextIdxToCopy));
            }
            // We then skip over `nextIdxToClear`.
            nextIdxToCopy = nextIdxToClear + 1;
        }
        // Copy anything remaining
        for (; nextIdxToCopy < list.size(); nextIdxToCopy++) {
            list.set(listIdx++, list.get(nextIdxToCopy));
        }

        if (list.size() > listIdx) {
            list.subList(listIdx, list.size()).clear();
        }
    }
}
