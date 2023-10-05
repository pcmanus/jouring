package io.github.pcmanus.jouring.bench;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class MappedFile {
    private static final int MAX_MAPPING_SIZE = Integer.MAX_VALUE;

    public final long length;
    private final ByteBuffer[] buffers;
    private final long[] offsets;

    private MappedFile(long length, ByteBuffer[] buffers, long[] offsets) {
        this.length = length;
        this.buffers = buffers;
        this.offsets = offsets;
    }

    public static MappedFile map(Path file) {
        try {
            try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long length = channel.size();
                long count = length / MAX_MAPPING_SIZE;
                if (length % MAX_MAPPING_SIZE != 0) {
                    count++;
                }
                assert count <= Integer.MAX_VALUE;
                ByteBuffer[] buffers = new ByteBuffer[(int) count];
                long[] offsets = new long[buffers.length];

                for (int i = 0; i < buffers.length; i++) {
                    long offset = (long) i * MAX_MAPPING_SIZE;
                    long size = Math.min(MAX_MAPPING_SIZE, length - offset);
                    buffers[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, size);
                    offsets[i] = offset;
                }

                return new MappedFile(length, buffers, offsets);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error mapping " + file, e);
        }
    }

    public ByteBuffer read(long position, int size) {
        assert 0 <= position && position < length;
        assert position + size <= length;
        int blockIdx = Arrays.binarySearch(offsets, position);
        if (blockIdx < 0) {
            blockIdx = -blockIdx - 2;
        }
        assert blockIdx >= 0;
        long offset = offsets[blockIdx];
        int blockOffset = (int) (position - offset);
        ByteBuffer buffer;
        if (blockOffset > MAX_MAPPING_SIZE - size) {
            // We need some in the current block, and the rest in the next one; so we allocate a separate
            // buffer and copy the data. Note that we could probably have a way to reuse that buffer instead
            // of allocating every time, at least in the "native thread" case, but leaving that aside for now
            // (maybe we should also force some sort of aligned reads? which may avoid this).
            buffer = ByteBuffer.allocate(size);

            // First transfer the relevant part of the initial block.
            ByteBuffer toCopy = buffers[blockIdx].duplicate();
            toCopy.position(blockOffset);
            buffer.put(toCopy);

            // Then transfer what remains from the next block.
            int remaining = size - (MAX_MAPPING_SIZE - blockOffset);
            toCopy = buffers[blockIdx + 1].duplicate();
            toCopy.limit(remaining);
            buffer.put(toCopy);

            buffer.flip();
        } else {
            // It all fits in the initial block; no need to allocate any.
            buffer = buffers[blockIdx].duplicate();
            buffer.position(blockOffset);
            buffer.limit(blockOffset + size);
        }
        return buffer;
    }
}
