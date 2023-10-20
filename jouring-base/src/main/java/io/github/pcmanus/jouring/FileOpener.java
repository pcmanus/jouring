package io.github.pcmanus.jouring;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.nio.file.Path;

abstract class FileOpener {
    protected final boolean useDirect;

    protected FileOpener(boolean useDirect) {
        this.useDirect = useDirect;
    }

    public int open(Path path) {
        String absolutePath = path.toAbsolutePath().toString();
        int length = absolutePath.length();
        MemorySegment segment = MemorySegment.allocateNative(length + 1, SegmentScope.global());
        segment.setUtf8String(0, absolutePath);
        return openInternal(segment);
    }

    protected abstract int openInternal(MemorySegment filePathAsSegment);
}
