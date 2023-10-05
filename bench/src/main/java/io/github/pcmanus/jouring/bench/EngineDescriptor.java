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

    MMAP_VTHREAD(
            "Reads are performed by virtual threads using mmaping of the files",
            MappingVThreadEngine::new
    );

    public final String description;
    public final Engine.Ctor ctor;

    EngineDescriptor(String description, Engine.Ctor ctor) {
        this.description = description;
        this.ctor = ctor;
    }
}
