package io.github.pcmanus.jouring.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class CompletionTracker {
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong checksum = new AtomicLong();

    public long completed() {
        return completed.get();
    }

    public long bytesRead() {
        return bytesRead.get();
    }

    public long checksum() {
        return checksum.get();
    }

    public void onCompleted(ReadTask task, ByteBuffer result) {
        long sum = 0;
        for (int i = result.position(); i < result.limit(); i++) {
            sum += result.get(i);
        }
        checksum.addAndGet(sum);
        bytesRead.addAndGet(result.remaining());
        completed.incrementAndGet();
    }
}
