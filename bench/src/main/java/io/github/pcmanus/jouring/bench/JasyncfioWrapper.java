package io.github.pcmanus.jouring.bench;

import one.jasyncfio.AsyncFile;
import one.jasyncfio.EventExecutor;
import one.jasyncfio.OpenOption;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JasyncfioWrapper {
    private final Benchmark.Parameters parameters;
    private final EventExecutor executor;
    private final Map<Path, AsyncFile> asyncFiles = new HashMap<>();

    JasyncfioWrapper(Benchmark.Parameters parameters) {
        this.parameters = parameters;
        this.executor = EventExecutor.builder()
                .entries(parameters.depth())
                .ioRingSetupIoPoll()
                .build();
        parameters.files().forEach(path -> {
            try {
                if (parameters.directIO()) {
                    asyncFiles.put(path, AsyncFile.open(path, executor, OpenOption.READ_ONLY, OpenOption.NOATIME, OpenOption.DIRECT).get());
                } else {
                    asyncFiles.put(path, AsyncFile.open(path, executor, OpenOption.READ_ONLY, OpenOption.NOATIME).get());
                }
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

    void batchSubmit(
            TaskSupplier taskSupplier,
            Supplier<ByteBuffer> borrowBuffer,
            CompletionHandler completionHandler
    ) {
        int depth = parameters.depth();

        List<CompletableFuture<ByteBuffer>> submissions = new ArrayList<>(depth);
        List<ReadTask> submissionsTasks = new ArrayList<>(depth);
        List<ByteBuffer> submissionsBuffers = new ArrayList<>(depth);

        ReadTask[] tasks = new ReadTask[depth];
        int[] submissionsToClear = new int[depth];
        int inFlight = 0;
        boolean done = false;

        CompletedSubmissionReaper reaper = (shouldGetFuture) -> {
            int toClear = 0;
            for (int i = 0; i < submissions.size(); i++) {
                CompletableFuture<ByteBuffer> future = submissions.get(i);
                if (shouldGetFuture.test(future)) {
                    ReadTask task = submissionsTasks.get(i);
                    try {
                        ByteBuffer buffer = future.get();
                        ByteBuffer toReturn = submissionsBuffers.get(i);
                        completionHandler.onCompletion(task, buffer, toReturn);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    submissionsToClear[toClear++] = i;
                }
            }
            return toClear;
        };

        while (!done) {
            int toPrep;
            if (inFlight < depth) {
                toPrep = depth - inFlight;
                int available = taskSupplier.transferTask(toPrep, tasks);
                if (available == 0) {
                    done = true;
                } else {
                    for (int i = 0; i < available; i++) {
                        ReadTask task = tasks[i];
                        ByteBuffer buffer = borrowBuffer.get();
                        assert buffer != null;
                        CompletableFuture<ByteBuffer> future = read(task, buffer.duplicate());
                        submissions.add(future);
                        submissionsTasks.add(task);
                        submissionsBuffers.add(buffer);
                        inFlight++;
                    }
                }
            }

            int toClear = reaper.reapCompleted(Future::isDone);
            inFlight -= toClear;

            clearAll(submissions, submissionsToClear, toClear);
            clearAll(submissionsTasks, submissionsToClear, toClear);
            clearAll(submissionsBuffers, submissionsToClear, toClear);
        }

        reaper.reapCompleted(f -> true);
    }
    static <T> void clearAll(List<T> list, int[] clearIndices, int clearCount) {
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

    private interface CompletedSubmissionReaper {
        int reapCompleted(Predicate<CompletableFuture<ByteBuffer>> shouldGetFuture);
    }

    interface TaskSupplier {
        int transferTask(int count, ReadTask[] tasks);
    }

    interface CompletionHandler {
        void onCompletion(ReadTask task,  ByteBuffer result, ByteBuffer bufferToReturn);
    }
}
