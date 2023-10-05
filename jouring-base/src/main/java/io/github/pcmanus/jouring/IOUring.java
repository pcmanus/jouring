package io.github.pcmanus.jouring;

import jdk.jfr.Event;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class IOUring implements AutoCloseable {
    public final Arena arena;
    private final MemorySegment ring;

    public IOUring() {
        // TODO: confined is probably not what we want, at least not for everything, but starting with this for now.
        this.arena = Arena.ofConfined();
        this.ring = arena.allocate(Layouts.ringLayout);
    }

    // Probably worth be separated just so "flags" can be provided somehow.
    public void init(int ringSize) {
        queueInit(ringSize, this.ring, 0);
    }

    private void queueInit(int entries, MemorySegment ring, int flags) throws IOUringException {
        try {
            int res = (int) Layouts.queueInitHandle.invokeExact(entries, ring, flags);
            if (res < 0) {
                throw new IOUringException("Error running %s (error code: %d)", Layouts.queueInitHandle, -res);
            }
        } catch (Throwable ex) {
            throw FFMHelper.invokeError(Layouts.queueInitHandle);
        }
    }

    private MemorySegment getSqe() {
        try {
            MemorySegment sqe = (MemorySegment) Layouts.getSqeHandle.invokeExact(this.ring);
            // TODO: obviously, that's not what we want to do, but well...
            if (sqe == MemorySegment.NULL) {
                throw new IOUringException("Error getting next ring entry");
            }
            return sqe;
        } catch (Throwable ex) {
            throw FFMHelper.invokeError(Layouts.getSqeHandle);
        }
    }

    private void prepareNop(MemorySegment sqe) {
        try {
            Layouts.prepNopHandle.invokeExact(sqe);
        } catch (Throwable ex) {
            throw FFMHelper.invokeError(Layouts.prepNopHandle);
        }
    }

    private void setData(MemorySegment sqe, MemorySegment data) {
        try {
            Layouts.setDataHandle.invokeExact(sqe, data);
        } catch (Throwable ex) {
            throw FFMHelper.invokeError(Layouts.setDataHandle);
        }
    }

    private void submit() {
        try {
            Layouts.submitHandle.invokeExact(this.ring);
        } catch (Throwable ex) {
            throw FFMHelper.invokeError(Layouts.submitHandle);
        }
    }

    public void submitNop(MemorySegment data) {
        MemorySegment sqe = this.getSqe();
        prepareNop(sqe);
        setData(sqe, data);
        submit();
    }

    @Override
    public void close() throws Exception {
        this.arena.close();
    }
}