package io.github.pcmanus.jouring;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

class EventLoops implements IOExecutor {
    private final EventLoop[] loops;
    private final AtomicInteger idx = new AtomicInteger(0);

    EventLoops(EventLoop[] loops) {
        this.loops = loops;
    }

    private EventLoop next() {
        return this.loops[idx.getAndIncrement() % this.loops.length];
    }

    public PooledBuffer read(Path file, long offset, int length) {
        return next().read(file, offset, length);
    }

    public CompletableFuture<PooledBuffer> readAsync(Path file, long offset, int length) {
        return next().readAsync(file, offset, length);
    }

    @Override
    public void close() throws Exception {
        for (EventLoop loop : this.loops) {
            loop.close();
        }
    }
}
