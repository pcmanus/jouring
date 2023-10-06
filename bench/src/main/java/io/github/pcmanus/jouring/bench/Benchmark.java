package io.github.pcmanus.jouring.bench;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@CommandLine.Command(
        name = "jfio",
        mixinStandardHelpOptions = true,
        version = "jfio benchmark 0.1",
        description = "Benchmark various ways to do read operations")
public class Benchmark implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1..*")
    private List<Path> files = Collections.emptyList();

    @CommandLine.Option(
            names = {"-e", "--engine"},
            description = "The engine to use for reads default to 'sync_native'. Supported: ${COMPLETION-CANDIDATES}",
            paramLabel = "<engine>"
    )
    private EngineDescriptor engine = EngineDescriptor.SYNC_NATIVE;

    @CommandLine.Option(
            names = {"-t", "--threads"},
            description = "Number of threads, default to cpu count",
            paramLabel = "<int>"
    )
    private int threads = Runtime.getRuntime().availableProcessors();

    @CommandLine.Option(
            names = {"-b", "--block"},
            description = "Block size in kb, default to 64",
            paramLabel = "<int>"
    )
    private int blockSizeKb = 64;

    @CommandLine.Option(
            names = {"-r", "--reads"},
            description = "Number of reads to perform, default to 1M",
            paramLabel = "<int>"
    )
    private int readCount = 1_000_000;

    @CommandLine.Option(
            names = {"-s", "--seed"},
            description = "Seed used for random generation",
            paramLabel = "<long>"
    )
    private long seed = new Random().nextLong();

    @Override
    public Integer call() throws Exception {
        Parameters parameters = new Parameters(
                this.files,
                this.threads,
                this.blockSizeKb * 1024,
                this.readCount,
                this.seed
        );

        printParameters(parameters);

        Stream<ReadTask> tasks = ReadTaskGenerators.uniform(this.seed, this.files, this.readCount);
        CompletionTracker completionTracker = new CompletionTracker();

        MetricsPrinter printer = new MetricsPrinter(completionTracker);
        try (ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor()) {
            scheduledExecutor.scheduleAtFixedRate(printer::printProgress, 1, 1, TimeUnit.SECONDS);
            engine.ctor.create(completionTracker::onCompleted, parameters).execute(tasks);
        }
        printer.printFinalSummary();
        return 0;
    }

    private void printParameters(Parameters parameters) {
        System.out.printf("Running with: threads=%d, blockSize=%s.%n", parameters.threads(), formatBytes(parameters.blockSize()));
    }

    public static void main(String[] args) {
        CommandLine cmdline = new CommandLine(new Benchmark());
        cmdline.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmdline.execute(args);
        System.exit(exitCode);
    }

    public record Parameters(
            List<Path> files,
            int threads,
            int blockSize,
            int readCount,
            long seed
    ) {
    }

    static class MetricsPrinter {
        private final CompletionTracker tracker;
        private final long startNanos;
        private long lastTick;
        private long completedAtLastTick = 0;
        private long bytesReadAtLastTick = 0;

        private boolean hasPrintedProgress = false;

        MetricsPrinter(CompletionTracker tracker) {
            this.tracker = tracker;
            this.startNanos = System.nanoTime();
            this.lastTick = this.startNanos;
        }

        void printProgress() {
            hasPrintedProgress = true;
            long now = System.nanoTime();
            long completed = this.tracker.completed();
            long bytesRead = this.tracker.bytesRead();

            long elapsed = now - this.lastTick;
            this.lastTick = now;

            long completedSinceLastTick = completed - this.completedAtLastTick;
            this.completedAtLastTick = completed;

            long bytesReadSinceLastTick = bytesRead - this.bytesReadAtLastTick;
            this.bytesReadAtLastTick = bytesRead;

            System.out.printf("IOPS: %s, BW=%s/s%n", formatQuantity(completedSinceLastTick), formatBytes(bytesReadSinceLastTick));
        }

        void printFinalSummary() {
            long totalElapsed = System.nanoTime() - this.startNanos;
            long totalCompleted = this.tracker.completed();
            long totalBytesRead = this.tracker.bytesRead();
            long checksum = this.tracker.checksum();

            long avgIops, avgBw;
            long elapsedSec = TimeUnit.NANOSECONDS.toSeconds(totalElapsed);
            if (elapsedSec == 0) {
                double multiplier = ((double)TimeUnit.SECONDS.toNanos(1)) /  totalElapsed;
                avgIops = (long) (totalCompleted * multiplier);
                avgBw = (long) (totalBytesRead * multiplier);
            } else {
                avgIops = totalCompleted / elapsedSec;
                avgBw = totalBytesRead / elapsedSec;
            }

            if (hasPrintedProgress) {
                System.out.println();
            }

            System.out.printf(
                    "%s OPS (%s) in %s, Avg IOPS: %s, Avg BW=%s/s%n",
                    formatQuantity(totalCompleted),
                    formatBytes(totalBytesRead),
                    formatElapsed(totalElapsed),
                    formatQuantity(avgIops),
                    formatBytes(avgBw)
            );
            System.out.printf("checksum: %d%n", checksum);
        }

    }

    private static String formatQuantity(long iops) {
        if (iops < 1000) {
            return String.format("%d", iops);
        } else if (iops < 1_000_000) {
            return String.format("%.2fk", iops / 1000.0);
        } else if (iops < 1_000_000_000) {
            return String.format("%.2fM", iops / 1_000_000.0);
        } else {
            return String.format("%.2fG", iops / 1_000_000_000.0);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return String.format("%dB", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2fKiB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2fMiB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2fGiB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private static String formatElapsed(long elapsedNanos) {
        if (elapsedNanos < 1000) {
            return String.format("%dns", elapsedNanos);
        } else if (elapsedNanos < 1_000_000) {
            return String.format("%.2fµs", elapsedNanos / 1000.0);
        } else if (elapsedNanos < 1_000_000_000) {
            return String.format("%.2fms", elapsedNanos / 1_000_000.0);
        } else {
            return String.format("%.2fs", elapsedNanos / 1_000_000_000.0);
        }

    }
}
