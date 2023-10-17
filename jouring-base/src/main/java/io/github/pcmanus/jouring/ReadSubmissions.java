package io.github.pcmanus.jouring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class ReadSubmissions {
    //struct read_s {
    //    int fd;
    //    int buf_length;
    //    void* buf_base;
    //    long offset;
    //    long id;
    //};
    private static final int READ_STRUCT_SIZE = 32;

    final ByteBuffer serialized;

    private final int capacity;
    private int size;

    ReadSubmissions(int capacity) {
        this.capacity = capacity;
        this.serialized = ByteBuffer.allocateDirect(capacity * READ_STRUCT_SIZE).order(ByteOrder.nativeOrder());
    }

    int capacity() {
        return this.capacity;
    }

    int size() {
        return this.size;
    }

    boolean isEmpty() {
        return this.size == 0;
    }

    int remaining() {
        return this.capacity - this.size;
    }

    void addRead(long id, int fd, long address, long offset, int length) {
        if (this.size == this.capacity) {
            throw new IllegalStateException("Read submission is full");
        }

        this.serialized.putInt(fd);
        this.serialized.putInt(length);
        this.serialized.putLong(address);
        this.serialized.putLong(offset);
        this.serialized.putLong(id);
        this.size++;
    }

    void clear(int count) {
        if (count >= this.size) {
            this.serialized.clear();
            this.size = 0;
        } else {
            int remaining = this.size - count;
            for (int i = 0; i < remaining; i++) {
                moveRead(count + i, i);
            }
            this.size = remaining;
            this.serialized.position(this.size * READ_STRUCT_SIZE);
        }
    }

    private void moveRead(int from, int to) {
        int fromOffset = from * READ_STRUCT_SIZE;
        int toOffset = to * READ_STRUCT_SIZE;
        for (int i = 0; i < READ_STRUCT_SIZE; i += 8) {
            this.serialized.putLong(toOffset + i, this.serialized.getLong(fromOffset + i));
        }
    }

    @Override
    public String toString() {
        if (this.size == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < size * READ_STRUCT_SIZE; i += READ_STRUCT_SIZE) {
            if (i > 0) {
                sb.append("\n");
            }
            int fd = this.serialized.getInt(i);
            int length = this.serialized.getInt(i + 4);
            long address = this.serialized.getInt(i + 8);
            long offset = this.serialized.getInt(i + 16);
            long id = this.serialized.getInt(i + 24);
            sb.append("  { fd=").append(fd);
            sb.append(", length=").append(length);
            sb.append(", address=0x").append(String.format("%x", address));
            sb.append(", offset=").append(offset);
            sb.append(", id=").append(id);
            sb.append(" }");
        }
        sb.append("\n]");
        return sb.toString();
    }

}
