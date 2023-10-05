package io.github.pcmanus.jouring.bench;

import java.nio.file.Path;

public record ReadTask(Path file, long offset) { }
