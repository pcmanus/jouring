package io.github.pcmanus.jouring;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.nio.ByteBuffer;

public class PooledBuffer implements AutoCloseable {
    private static final long ALIGNMENT = 512;

    private final BufferPool owner;
    private final MemorySegment segment;
    private final long address;

    private boolean closed;

    PooledBuffer(BufferPool owner, int size) {
        this.owner = owner;
        this.segment = MemorySegment.allocateNative(size, ALIGNMENT, SegmentScope.auto());
        this.address = this.segment.address();
    }

    long address() {
        return this.address;
    }

    void reopen() {
        this.closed = false;
    }

    public ByteBuffer asByteBuffer() {
        return this.segment.asByteBuffer();
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.owner.returnBuffer(this);
    }
}
