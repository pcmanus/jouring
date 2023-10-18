package io.github.pcmanus.jouring;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

class PanamaRing extends Ring {
    private static final MethodHandle createRingMH;
    private static final MethodHandle submitAndCheckCompletionsMH;
    private static final MethodHandle destroyRingMH;

    private static final StructLayout resultLayout;
    private static final VarHandle submittedHandle;
    private static final VarHandle completedHandle;

    static {
        System.loadLibrary("jouring");

        Linker linker = Linker.nativeLinker();
        SymbolLookup loaderLookup = SymbolLookup.loaderLookup();
        SymbolLookup lookByName = name -> loaderLookup.find(name).or(() -> linker.defaultLookup().find(name));

        FunctionDescriptor createRingDesc = FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_BOOLEAN
        );
        createRingMH = lookByName
                .find("create_ring")
                .map(addr -> linker.downcallHandle(addr, createRingDesc))
                .orElseThrow();

        resultLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("submitted"),
                ValueLayout.JAVA_INT.withName("completed")
        );
        submittedHandle = resultLayout.varHandle(MemoryLayout.PathElement.groupElement("submitted"));
        completedHandle = resultLayout.varHandle(MemoryLayout.PathElement.groupElement("completed"));

        FunctionDescriptor submitAndCheckCompletionsDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
        );
        submitAndCheckCompletionsMH = lookByName
                .find("submit_and_check_completions")
                .map(addr -> linker.downcallHandle(addr, submitAndCheckCompletionsDesc))
                .orElseThrow();

        FunctionDescriptor destroyRingDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
        destroyRingMH = lookByName
                .find("destroy_ring")
                .map(addr -> linker.downcallHandle(addr, destroyRingDesc))
                .orElseThrow();
    }

    private final MemorySegment ringPtr;
    private final MemorySegment pendingSubmissionsPtr;
    private final MemorySegment result;
    private final MemorySegment completionIdsPtr;

    PanamaRing(Parameters parameters) {
        super(parameters);

        try {
            this.ringPtr = (MemorySegment)createRingMH.invoke(parameters.depth(), parameters.useSQPolling());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        this.pendingSubmissionsPtr = MemorySegment.ofBuffer(this.submissions().serialized);
        this.result = MemorySegment.allocateNative(resultLayout, SegmentScope.auto());
        this.completionIdsPtr = MemorySegment.ofBuffer(this.completionIdsBuffer);
    }

    @Override
    protected void submitAndCheckCompletionsInternal(boolean forceCheckCompletions) {
        try {
            submitAndCheckCompletionsMH.invoke(
                    this.ringPtr,
                    this.pendingSubmissionsPtr,
                    this.pendingSubmissions.size(),
                    forceCheckCompletions,
                    this.result,
                    this.completionIdsPtr
            );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void destroyRing() {
        try {
            destroyRingMH.invoke(this.ringPtr);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    int lastCompleted() {
        return (int) completedHandle.get(this.result);
    }

    @Override
    int lastSubmitted() {
        return (int) submittedHandle.get(this.result);
    }
}
