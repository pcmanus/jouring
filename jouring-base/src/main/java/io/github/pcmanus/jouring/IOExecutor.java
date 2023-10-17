package io.github.pcmanus.jouring;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface IOExecutor extends AutoCloseable {
    static Builder builder() {
        return new Builder();
    }

    PooledBuffer read(Path file, long offset, int length);
    CompletableFuture<PooledBuffer> readAsync(Path file, long offset, int length);

    class Builder {
        private int depth = 128;
        private int bufferSize = 4096;
        private boolean useNalim = false;

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder useNalim(boolean useNalim) {
            this.useNalim = useNalim;
            return this;
        }

        private Ring newRing() {
            return this.useNalim ? new NalimRing(this.depth) : new PanamaRing(this.depth);
        }

        public IOExecutor build() {
            return new EventLoop(newRing(), this.bufferSize);
        }

        public IOExecutor buildMulti(int count) {
            EventLoop[] loops = new EventLoop[count];
            for (int i = 0; i < count; i++) {
                loops[i] = new EventLoop(newRing(), this.bufferSize);
            }
            return new EventLoops(loops);
        }
    }
}
