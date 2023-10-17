package io.github.pcmanus.jouring.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class ReadTaskGenerators {
    private ReadTaskGenerators() {
    }

    public static Stream<ReadTask> uniform(Benchmark.Parameters parameters) throws IOException {
        var files = parameters.files();
        var rnd = new Random(parameters.seed());
        final long[] offsets = new long[files.size()];
        long totalSize = 0;
        for (int i = 0; i < files.size(); i++) {
            offsets[i] = totalSize;
            totalSize += Files.size(files.get(i));
        }
        return rnd.longs(parameters.readCount(), 0, totalSize)
                .mapToObj(l -> {
                    int offsetIndex = Arrays.binarySearch(offsets, l);
                    if (offsetIndex < 0) {
                        offsetIndex = (-offsetIndex - 1) - 1;
                    }
                    Path file = files.get(offsetIndex);
                    long offset = l - offsets[offsetIndex];
                    // Do not generate offsets that would make the read cross a mmap region. This is because the path
                    // that handle reads that cross mmap regions ends up doing more copying and it's not necessarilly
                    // very fair for our purpose.
                    long remainder = offset % MappedFile.MAX_MAPPING_SIZE;
                    if (remainder + parameters.blockSize() > MappedFile.MAX_MAPPING_SIZE) {
                        offset -= parameters.blockSize() - (MappedFile.MAX_MAPPING_SIZE - remainder);
                    }
                    return new ReadTask(file, offset);
                });
    }

    public static Stream<ReadTask> sequential(Benchmark.Parameters parameters) throws IOException {
        var files = parameters.files();
        var rnd = new Random(parameters.seed());
        final long[] offsets = new long[files.size()];
        long totalSize = 0;
        for (int i = 0; i < files.size(); i++) {
            offsets[i] = totalSize;
            totalSize += Files.size(files.get(i));
        }

        var max = totalSize;
        return LongStream.range(0, parameters.readCount())
                .map(l -> (l * 10000) % max)
                .mapToObj(l -> {
                    int offsetIndex = Arrays.binarySearch(offsets, l);
                    if (offsetIndex < 0) {
                        offsetIndex = (-offsetIndex - 1) - 1;
                    }
                    Path file = files.get(offsetIndex);
                    long offset = l - offsets[offsetIndex];
                    return new ReadTask(file, offset);
                });
    }
}
