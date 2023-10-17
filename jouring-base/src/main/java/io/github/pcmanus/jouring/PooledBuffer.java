package io.github.pcmanus.jouring;

import java.nio.ByteBuffer;

public class PooledBuffer implements AutoCloseable {
    private final BufferPool owner;
    private final ByteBuffer buffer;
    private final long address;

    private boolean closed;

    PooledBuffer(BufferPool owner, int size) {
        this.owner = owner;
        this.buffer = ByteBuffer.allocateDirect(size);
        this.address = ByteBuffers.address(this.buffer);
    }

    long address() {
        return this.address;
    }

    void reopen() {
        this.closed = false;
    }

    public ByteBuffer asByteBuffer() {
        return this.buffer.duplicate();
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
