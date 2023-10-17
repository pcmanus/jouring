package io.github.pcmanus.jouring.bench;

public enum EngineDescriptor {
    NOOP(
            "Does not preform real reads, returns the same empty buffer for every read; for sanity checks",
            NoopEngine::new
    ),

    SYNC_NATIVE(
            "Reads are performed by native threads using `FileChannel.read`",
            SyncIONativeThreadEngine::new
    ),

    SYNC_VTHREAD(
            "Reads are performed by virtual threads using `FileChannel.read`",
            SyncIOVThreadEngine::new
    ),

    MMAP_NATIVE(
            "Reads are performed by native threads using mmaping of the files",
            MappingNativeThreadEngine::new
    ),

    MMAP2_NATIVE(
            "Reads are performed by native threads using mmaping of the files (through MMapBuffer)",
            Mapping2NativeThreadEngine::new
    ),

    MMAP_VTHREAD(
            "Reads are performed by virtual threads using mmaping of the files",
            MappingVThreadEngine::new
    ),

    JASYNCFIO_VTHREAD(
            "Reads are performed by virtual threads using the jacincfio lib (io_uring underneath)",
            JasyncfioVThreadEngine::new
    ),

    JASYNCFIO_NATIVE(
            "Reads are performed by native threads using io_uring",
            JasyncfioNativeThreadEngine::new
    ),

    JASYNCFIO_ASYNC(
            "Reads are asynchronously using io_uring",
            JasyncAsyncEngine::new
    ),

    JASYNCFIO_BATCH(
            "Reads are asynchronously using io_uring, batching stuffs",
            JasyncfioBatchedAsyncEngine::new
    ),

    JASYNCFIO_MULTI(
            "Reads are asynchronously using io_uring with one ring per configured threads",
            JasyncfioMultiRingEngine::new
    ),

    IOURING_VTHREAD(
            "Reads are asynchronously using io_uring with one ring, through nalim",
            IOUringVThread::new
    ),

    IOURING_ASYNC(
            "Reads are asynchronously using io_uring with one ring, through nalim",
            IOUringAsync::new
    );

    public final String description;
    public final Engine.Ctor ctor;

    EngineDescriptor(String description, Engine.Ctor ctor) {
        this.description = description;
        this.ctor = ctor;
    }

    boolean isNonJasyncfioIOUring() {
        return this == IOURING_VTHREAD || this == IOURING_ASYNC;
    }

    boolean isJasyncfio() {
        return switch (this) {
            case JASYNCFIO_NATIVE, JASYNCFIO_VTHREAD, JASYNCFIO_ASYNC, JASYNCFIO_BATCH, JASYNCFIO_MULTI -> true;
            default -> false;
        };
    }
}
