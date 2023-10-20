package io.github.pcmanus.jouring.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ReadTaskGenerators {
    private ReadTaskGenerators() {
    }

    public static Stream<ReadTask> uniform(Benchmark.Parameters parameters) {
        Random rnd = new Random(parameters.seed());
        OffsetTranslator calculator = new OffsetTranslator(parameters);
        return rnd
                .longs(parameters.readCount(), 0, calculator.totalSize)
                .mapToObj(calculator::translate);
    }

    public static Stream<ReadTask> zipf(Benchmark.Parameters parameters) {
        if (parameters.verbose()) {
            System.out.println("Initialising Zipf generator ...");
        }

        OffsetTranslator calculator = new OffsetTranslator(parameters);
        long items = calculator.totalSize / parameters.blockSize();
        ZipfGenerator generator = new ZipfGenerator(parameters.seed(), items, true);

        return Stream.generate(() -> calculator.translate(generator.nextValue())).limit(parameters.readCount());
    }

    private static class OffsetTranslator {
        private final Benchmark.Parameters parameters;
        private final long[] offsets;
        private final long totalSize;

        OffsetTranslator(Benchmark.Parameters parameters) {
            this.parameters = parameters;
            List<Path> files = parameters.files();
            this.offsets = new long[files.size()];
            long size = 0;
            for (int i = 0; i < files.size(); i++) {
                offsets[i] = size;
                try {
                    size += Files.size(files.get(i));
                } catch (IOException e) {
                    throw new RuntimeException("Cannot figure out size of " + files.get(i), e);
                }
            }
            this.totalSize = size;
        }

        ReadTask translate(long totalOffset) {
            int offsetIndex = Arrays.binarySearch(offsets, totalOffset);
            if (offsetIndex < 0) {
                offsetIndex = (-offsetIndex - 1) - 1;
            }
            Path file = parameters.files().get(offsetIndex);
            long offset = totalOffset - offsets[offsetIndex];
            // Do not generate offsets that would make the read cross a mmap region. This is because the path
            // that handle reads that cross mmap regions ends up doing more copying, and it's not necessarily
            // very fair for our purpose.
            long remainder = offset % MappedFile.MAX_MAPPING_SIZE;
            if (remainder + parameters.blockSize() > MappedFile.MAX_MAPPING_SIZE) {
                offset -= parameters.blockSize() - (MappedFile.MAX_MAPPING_SIZE - remainder);
            }
            // Also, align offset on 512 bytes boundaries as that's the underlying device block size and we
            // need offset to be aligned with direct IO.
            remainder = offset % 512;
            if (remainder != 0) {
                offset -= remainder;
            }
            return new ReadTask(file, offset);
        }
    }

    /**
     * "Stolen" for YCSB and "cleaned" up.
     */
    private static class ZipfGenerator {
        public static final long FNV_offset_basis_64 = 0xCBF29CE484222325L;
        public static final long FNV_prime_64 = 1099511628211L;

        private static final double ALPHA = 2;

        private final long items;
        private final double theta = (1.0 / ALPHA) - 1.0;
        private final double limit_for_one = 1.0 + Math.pow(0.5, theta);
        private final double zetan;
        private final double eta;

        private final Random random;
        private final boolean scramble;

        public ZipfGenerator(long seed, long items, boolean scramble) {
            this.random = new Random(seed);
            this.items = items;
            this.scramble = scramble;

            this.zetan = computeZeta(items);
            double zeta2 = computeZeta(2);
            double one_minus_theta = 1.0 - theta;
            this.eta = (1 - Math.pow(2.0 / items, one_minus_theta)) / (1 - zeta2 / zetan);
        }

        double computeZeta(long n) {
            double sum = 0;
            for (long i = 0; i < n; i++) {
                sum += 1 / (Math.pow(i + 1, theta));
            }
            return sum;
        }

        long nextValue() {
            // From "Quickly Generating Billion-Record Synthetic Databases", Jim Gray et al, SIGMOD 1994

            double u = random.nextDouble();
            double uz = u * zetan;

            if (uz < 1.0) {
                return 0;
            }

            if (uz < limit_for_one) {
                return 1;
            }

            long value = (long)(items * Math.pow(eta * u - eta + 1, ALPHA));
            if (scramble) {
                value = fnvHash64(value) % items;
            }
            return value;
        }

        private static long fnvHash64(long val)
        {
            //from http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
            long hashval = FNV_offset_basis_64;
            for (int i=0; i<8; i++)
            {
                long octet=val&0x00ff;
                val=val>>8;

                hashval = hashval ^ octet;
                hashval = hashval * FNV_prime_64;
            }
            return Math.abs(hashval);
        }
    }
}
