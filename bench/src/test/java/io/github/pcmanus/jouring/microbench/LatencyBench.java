package io.github.pcmanus.jouring.microbench;

import io.github.pcmanus.jouring.bench.Benchmark.Parameters;
import io.github.pcmanus.jouring.bench.Engine;
import io.github.pcmanus.jouring.bench.ReadTask;
import io.github.pcmanus.jouring.bench.ReadTaskGenerators;
import io.github.pcmanus.jouring.bench.SyncIONativeThreadEngine;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 10, time = 5)
@Fork(warmups = 1, value = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Threads(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LatencyBench {
    Random random;
    ByteBuffer[] buffers;
    File file;

    Stream<ReadTask> tasks;

    Engine engine;
    CompletableFuture<ByteBuffer>[] futures;

    public enum ReadType
    {
        BLOCKING,
        IOURING,
    }

    @Param({ "4096", "8192"})
    int chunkSize = 4096;

    @Param({"8", "16"})
    int numBuffers = 8;

    @Param({"256"})
    long fileSizeInKChunks = 256;

    @Param({"BLOCKING", "IOURING"})
    ReadType readType = ReadType.BLOCKING;

    @Setup(Level.Trial)
    public void setup() throws Throwable
    {
        random = new Random(System.currentTimeMillis());
        buffers = new ByteBuffer[numBuffers];
        for (int i = 0; i < numBuffers; i++)
            buffers[i] = ByteBuffer.allocateDirect(chunkSize);

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

        setupReaders();

        futures = new CompletableFuture[numBuffers];
    }

    protected void setupReaders() throws IOException
    {
        var files = List.of(file.toPath());
        long seed = System.currentTimeMillis();
        tasks = ReadTaskGenerators.uniform(seed, files, Long.MAX_VALUE);
        Parameters parameters = new Parameters(
                files,
                Runtime.getRuntime().availableProcessors(),
                chunkSize,
                Integer.MAX_VALUE,
                seed
        );
        engine = readType == ReadType.BLOCKING ? new SyncIONativeThreadEngine()

        channel = AsynchronousChannelProxy.create(file, false);
        ChannelProxy blockingChannel = channel.newBlockingChannel();
        if (readType == ReadType.BLOCKING || readType == ReadType.AIO)
        {
            chunkReader = ChunkReader.simple(channel, blockingChannel, file.length(), chunkSize);
        }
        else
        {
            batchedChannel = channel.maybeBatched(readType == ReadType.VECTORED);
            chunkReader = ChunkReader.simple(batchedChannel, blockingChannel, file.length(), chunkSize);
        }
    }

    @TearDown(Level.Trial)
    public void teardown()
    {
        channel.close();
        chunkReader.close();

        if (batchedChannel != null)
            batchedChannel.close();
    }

    @Benchmark
    public void readBuffers()
    {
        channel.tryToSkipCache(0, numBuffers*chunkSize);

        long randomStartChunkOffset = ThreadLocalRandom.current().nextLong((fileSizeInKChunks * 1024) - numBuffers);
        for (int i = 0; i < numBuffers; i++)
        {
            long chunkOffset = randomStartChunkOffset + chunkSize * i;
            if (readType == ReadType.BLOCKING)
            {
                chunkReader.readChunkBlocking(chunkOffset, buffers[i]);
            }
            else
                futures[i] = chunkReader.readChunk(chunkOffset, buffers[i]);
        }

        if (batchedChannel != null)
            batchedChannel.submitBatch();

        if (readType != ReadType.BLOCKING)
            for (CompletableFuture f : futures) f.join();

    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

}
