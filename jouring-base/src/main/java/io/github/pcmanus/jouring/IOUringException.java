package io.github.pcmanus.jouring;

public class IOUringException extends RuntimeException {
    public IOUringException(String msg, Object... msgArgs) {
        super(String.format(msg, msgArgs));
    }
}
