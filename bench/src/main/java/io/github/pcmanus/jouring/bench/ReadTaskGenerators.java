package io.github.pcmanus.jouring.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public final class ReadTaskGenerators {
    private ReadTaskGenerators() {
    }

    public static Stream<ReadTask> uniform(long seed, List<Path> files, long count) throws IOException {
        var rnd = new Random(seed);
        final long[] offsets = new long[files.size()];
        long totalSize = 0;
        for (int i = 0; i < files.size(); i++) {
            offsets[i] = totalSize;
            totalSize += Files.size(files.get(i));
        }
        return rnd.longs(count, 0, totalSize)
                .mapToObj(l -> {
                    int offsetIndex = Arrays.binarySearch(offsets, l);
                    if (offsetIndex < 0) {
                        offsetIndex = (-offsetIndex - 1) - 1;
                    }
                    Path file = files.get(offsetIndex);
                    return new ReadTask(file, l - offsets[offsetIndex]);
                });
    }
}
