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
            names = {"--nalim"},
            description = "Whether to use the nalim (instead of panama) for the io_uring engines, default to false"
    )
    private boolean useNalim = false;

    @CommandLine.Option(
            names = {"-d", "--depth"},
            description = "The depth of the underlying ring for io_uring engines, default to 128",
            paramLabel = "<int>"
    )
    private int depth = 128;

    @CommandLine.Option(
            names = {"--sq-polling"},
            description = "Whether to use submission queue polling for the io_uring engines, default to false"
    )
    private boolean useSQPolling = false;

    @CommandLine.Option(
            names = {"--rings"},
            description = "The number of rings used for io_uring engines, default to 1",
            paramLabel = "<int>"
    )
    private int ringCount = 1;

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
            description = "Number of reads to perform, default to 10M",
            paramLabel = "<int>"
    )
    private int readCount = 5_000_000;

    @CommandLine.Option(
            names = {"-s", "--seed"},
            description = "Seed used for random generation",
            paramLabel = "<long>"
    )
    private long seed = new Random().nextLong();

    @CommandLine.Option(
            names = {"--direct"},
            description = "Use direct IO"
    )
    private boolean directIO = false;

    @CommandLine.Option(
            names = {"--output-format"},
            description = "Format of the output. Supported: ${COMPLETION-CANDIDATES}",
            paramLabel = "<format>"
    )
    private OutputFormat outputFormat = OutputFormat.HUMAN;

    @Override
    public Integer call() throws Exception {
        Parameters parameters = new Parameters(
                this.files,
                this.threads,
                this.blockSizeKb * 1024,
                this.readCount,
                this.seed,
                this.depth,
                this.useNalim,
                this.directIO,
                this.ringCount,
                this.useSQPolling
        );

        printParameters(parameters);

        Stream<ReadTask> tasks = ReadTaskGenerators.uniform(parameters);
        CompletionTracker completionTracker = new CompletionTracker();

        MetricsPrinter printer = new MetricsPrinter(completionTracker, outputFormat);
        try (ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor()) {
            scheduledExecutor.scheduleAtFixedRate(printer::printProgress, 1, 1, TimeUnit.SECONDS);
            engine.ctor.create(completionTracker::onCompleted, parameters).execute(tasks);
        }
        printer.printFinalSummary();
        return 0;
    }

    private void printParameters(Parameters parameters) {
        if (outputFormat != OutputFormat.HUMAN) {
            return;
        }

        System.out.printf(
                "Running with: engine=%s%s, reads=%s, threads=%d, blockSize=%s, %s I/O.%n",
                engine.name().toLowerCase(),
                getEngineOptions(parameters),
                formatQuantity(parameters.readCount()),
                parameters.threads(),
                formatBytes(parameters.blockSize()),
                parameters.directIO() ? "direct" : "buffered"
        );
    }

    private String getEngineOptions(Parameters parameters) {
        if (engine.isJasyncfio()) {
            return String.format("(depth=%d)", parameters.depth());
        } else if (engine.isNonJasyncfioIOUring()) {
            String moreOpts = "";
            if (parameters.useSQPolling()) {
                moreOpts += ", sq-polling";
            }
            return String.format(
                    "(%s, depth=%d, rings=%d%s)",
                    parameters.useNalim ? "nalim" : "panama",
                    parameters.depth(),
                    parameters.ringCount(),
                    moreOpts);
        }
        return "";
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
            long seed,
            int depth,
            boolean useNalim,
            boolean directIO,
            int ringCount,
            boolean useSQPolling
    ) {
    }

    static class MetricsPrinter {
        private final CompletionTracker tracker;
        private final OutputFormat format;
        private final long startNanos;
        private long completedAtLastTick = 0;
        private long bytesReadAtLastTick = 0;

        private boolean hasPrintedProgress = false;

        MetricsPrinter(CompletionTracker tracker, OutputFormat format) {
            this.tracker = tracker;
            this.format = format;
            this.startNanos = System.nanoTime();
        }

        void printProgress() {
            hasPrintedProgress = true;
            long now = System.nanoTime();
            long completed = this.tracker.completed();
            long bytesRead = this.tracker.bytesRead();

            long completedSinceLastTick = completed - this.completedAtLastTick;
            this.completedAtLastTick = completed;

            long bytesReadSinceLastTick = bytesRead - this.bytesReadAtLastTick;
            this.bytesReadAtLastTick = bytesRead;

            if (format == OutputFormat.CSV) {
                long elapsed = now - startNanos;
                long elapsedSec = Math.round(((double)elapsed) / TimeUnit.SECONDS.toNanos(1));
                // elapsed_seconds, iops, bw(bytes)
                System.out.printf("%d,%d,%d%n", elapsedSec, completedSinceLastTick, bytesReadSinceLastTick);
            } else {
                System.out.printf("IOPS: %s, BW=%s/s%n", formatQuantity(completedSinceLastTick), formatBytes(bytesReadSinceLastTick));
            }
        }

        void printFinalSummary() {
            if (format != OutputFormat.HUMAN) {
                return;
            }

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

    enum OutputFormat {
        HUMAN,
        CSV
    }

    private static String formatQuantity(long iops) {
        if (iops < 1_000) {
            return String.format("%d", iops);
        } else if (iops < 1_000_000) {
            if (iops % 1_000 == 0) {
                return String.format("%dk", iops / 1_000);
            } else {
                return String.format("%.2fk", iops / 1000.0);
            }
        } else if (iops < 1_000_000_000) {
            if (iops % 1_000_000 == 0) {
                return String.format("%dM", iops / 1_000_000);
            } else {
                return String.format("%.2fM", iops / 1_000_000.0);
            }
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
