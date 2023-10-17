package io.github.pcmanus.jouring;

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;

class BufferPool {
    private final MessagePassingQueue<PooledBuffer> queue;

    BufferPool(int capacity, int bufferSize) {
        this.queue = new MpscArrayQueue<>(capacity);
        for (int i = 0; i < capacity; i++) {
            this.queue.offer(new PooledBuffer(this, bufferSize));
        }
    }

    PooledBuffer borrow() {
        for (;;) {
            PooledBuffer buffer = this.queue.poll();
            if (buffer != null) {
                buffer.reopen();
                return buffer;
            }
            Thread.onSpinWait();
        }
    }

    void returnBuffer(PooledBuffer buffer) {
        boolean offered = this.queue.offer(buffer);
        assert offered : "Buffer pool should not be full";
    }

}