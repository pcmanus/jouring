package io.github.pcmanus.jouring;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// Kinda ugly as we never remove from the cache (except on closing) but ...
class FileDescriptorCache implements Closeable {
    private static final Field fdField;
    static {
        try {
            fdField = FileDescriptor.class.getDeclaredField("fd");
            fdField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private final FileOpener opener;

    FileDescriptorCache(FileOpener opener) {
        this.opener = opener;
    }

    private final Map<Path, CacheEntry> cache = new HashMap<>();

    public int fd(Path path) {
        CacheEntry entry = this.cache.get(path);
        if (entry == null) {
            int fd = opener.open(path);
            entry = new CacheEntry(fd);
            this.cache.put(path, entry);
        }
        return entry.fd;
    }

    private record CacheEntry(int fd) {}

    public void close() throws IOException {
        // TODO: well, we could close the files for instance :)
    }
}