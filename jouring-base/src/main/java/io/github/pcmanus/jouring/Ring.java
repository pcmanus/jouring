package io.github.pcmanus.jouring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class Ring implements AutoCloseable {
    protected final Parameters parameters;
    protected final ReadSubmissions pendingSubmissions;

    protected final ByteBuffer completionIdsBuffer;

    private final int maxInFlight;
    private int inFlight;
    private boolean closed;

    Ring(Parameters parameters) {
        int depth = parameters.depth;
        this.parameters = parameters;
        this.pendingSubmissions = new ReadSubmissions(depth);
        this.completionIdsBuffer = ByteBuffer
                .allocateDirect(8 * depth * 2)
                .order(ByteOrder.nativeOrder());
        this.maxInFlight = depth * 2;
    }

    int depth() {
        return parameters.depth;
    }

    int maxInFlight() {
        return maxInFlight;
    }

    int roomAvailable() {
        return Math.min(this.maxInFlight - this.inFlight - this.pendingSubmissions.size(), this.pendingSubmissions.remaining());
    }

    ReadSubmissions submissions() {
        return this.pendingSubmissions;
    }

    protected abstract void submitAndCheckCompletionsInternal(boolean forceCheckCompletions);
    protected abstract void destroyRing();

    abstract int lastCompleted();
    abstract int lastSubmitted();

    // Modifies the `ReadSubmission` to remove any completed reads.
    // Completions must be fetched after that with lastCompleted/completed(int)
    void submitAndCheckCompletions() {
        if (this.closed) {
            throw new IllegalStateException("Ring is closed");
        }

        this.submitAndCheckCompletionsInternal(inFlight > 0);
        int submitted = this.lastSubmitted();
        inFlight += submitted;
        inFlight -= this.lastCompleted();
        this.pendingSubmissions.clear(submitted);
    }


    long completed(int i) {
        assert i >= 0 && i < this.lastCompleted();
        return this.completionIdsBuffer.getLong(i * 8);
    }


    public int inFlight() {
        return this.inFlight;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;
        this.destroyRing();
    }

    record Parameters(
            int depth,
            boolean useSQPolling
    ) {}
}
