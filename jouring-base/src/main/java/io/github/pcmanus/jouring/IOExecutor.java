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
        private boolean useSQPolling = false;

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

        public Builder useSQPolling(boolean useSQPolling) {
            this.useSQPolling = useSQPolling;
            return this;
        }

        private Ring.Parameters parameters() {
            return new Ring.Parameters(this.depth, this.useSQPolling);
        }

        private Ring newRing(Ring.Parameters params) {
            return this.useNalim ? new NalimRing(params) : new PanamaRing(params);
        }

        public IOExecutor build() {
            return new EventLoop(newRing(parameters()), this.bufferSize);
        }

        public IOExecutor buildMulti(int count) {
            var params = parameters();
            EventLoop[] loops = new EventLoop[count];
            for (int i = 0; i < count; i++) {
                loops[i] = new EventLoop(newRing(params), this.bufferSize);
            }
            return new EventLoops(loops);
        }
    }
}
