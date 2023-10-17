package io.github.pcmanus.jouring;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// Kinda ugly as we never remove from the cache (except on closing) but ...
public class FileDescriptorCache implements Closeable {
    private static final Field fdField;
    static {
        try {
            fdField = FileDescriptor.class.getDeclaredField("fd");
            fdField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<Path, CacheEntry> cache = new HashMap<>();

    public int fd(Path path) {
        CacheEntry entry = this.cache.get(path);
        if (entry == null) {
            try {
                var file = new RandomAccessFile(path.toFile(), "r");
                var javaFd = file.getFD();
                int fd = (int) fdField.get(javaFd);
                entry = new CacheEntry(file, fd);
                this.cache.put(path, entry);
            } catch (IOException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return entry.fd;
    }

    private record CacheEntry(RandomAccessFile openFile, int fd) {}

    public void close() throws IOException {
        for (CacheEntry entry : this.cache.values()) {
            entry.openFile.close();
        }
    }
}