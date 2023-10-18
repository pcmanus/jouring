package io.github.pcmanus.jouring;

import one.nalim.FieldOffset;
import one.nalim.Library;
import one.nalim.Link;
import one.nalim.Linker;

@Library("jouring")
class NalimRing extends Ring {
    private final long ringPtr;

    private final long pendingSubmissionsAddress;
    private final long completionIdsAddress;

    protected final Result result = new Result();

    NalimRing(Parameters parameters) {
        super(parameters);
        this.ringPtr = create_ring(parameters.depth(), parameters.useSQPolling());
        this.pendingSubmissionsAddress = ByteBuffers.address(this.pendingSubmissions.serialized);
        this.completionIdsAddress = ByteBuffers.address(completionIdsBuffer);
    }

    @Override
    protected void submitAndCheckCompletionsInternal(boolean forceCheckCompletions) {
        int tries = 0;
        for(;;) {
            try {
                submit_and_check_completions(
                        this.ringPtr,
                        this.pendingSubmissionsAddress,
                        this.pendingSubmissions.size(),
                        forceCheckCompletions,
                        this.result,
                        this.completionIdsAddress
                );
                return;
            } catch (UnsatisfiedLinkError e) {
                if (tries++ > 3) {
                    throw e;
                } else {
                    System.err.println("Error calling native method; retrying...");
                }
            }
        }
    }

    @Override
    protected void destroyRing() {
        destroy_ring(this.ringPtr);
    }

    @Override
    int lastCompleted() {
        return this.result.completed;
    }

    @Override
    int lastSubmitted() {
        return this.result.submitted;
    }

    @Link
    private static native long create_ring(int depth, boolean enableSQPolling);

    @Link
    private static native int submit_and_check_completions(
            long ring,
            long serializedReadStructs,
            int nrReads,
            boolean forceCheckCompletions,
            @FieldOffset("submitted") Result result,
            long completedIdsPrt
    );

    @Link
    private static native void destroy_ring(long ring);

    static class Result {
        int submitted;
        int completed;
    }

    static {
        Linker.linkClass(NalimRing.class);
    }
}
