package io.github.pcmanus.jouring.microbench;

import io.github.pcmanus.jouring.bench.*;
import io.github.pcmanus.jouring.bench.Benchmark.Parameters;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Benchmark;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 10, time = 5)
@Fork(warmups = 1, value = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Threads(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LatencyBench {
    Random random;
    File file;

    Parameters parameters;
    Engine engine;
    CompletionTracker tracker;

    @Param({ "4096", "8192"})
    int chunkSize = 4096;

    @Param({"1000"})
    int numReads = 1000;

    @Param({"256"})
    long fileSizeInKChunks = 256;

    @Param({"SYNC_NATIVE", "IOURING_ASYNC"})
    EngineDescriptor engineType = EngineDescriptor.SYNC_NATIVE;

    @Setup(Level.Trial)
    public void setup() throws Throwable
    {
        random = new Random(System.currentTimeMillis());

        // if testing different disks, replace "." with the right path, e.g. /mnt/xxx
        file = new File(".", "bench.tmp");
        file.deleteOnExit();

        try (FileOutputStream fileOutputStream = new FileOutputStream(file, false);
             FileChannel writeChannel = fileOutputStream.getChannel())
        {
            byte[] data = new byte[chunkSize];
            ByteBuffer src = ByteBuffer.wrap(data);

            for (int i = 0; i < fileSizeInKChunks * 1024; i++)
            {
                random.nextBytes(data);
                writeChannel.write(src);
            }
            writeChannel.force(true);
        }

        setupEngine();
    }

    protected void setupEngine()
    {
        var files = List.of(file.toPath());
        parameters = new Parameters(
                files,
                Runtime.getRuntime().availableProcessors(),
                chunkSize,
                Integer.MAX_VALUE,
                System.currentTimeMillis()
        );
        tracker = new CompletionTracker();
        engine = engineType.ctor.create(tracker::onCompleted, parameters);
    }

    @Benchmark
    public void doReads() throws IOException
    {
        engine.execute(ReadTaskGenerators.uniform(parameters.seed(), parameters.files(), numReads));
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

}
