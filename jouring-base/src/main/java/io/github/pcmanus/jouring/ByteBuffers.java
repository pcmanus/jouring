package io.github.pcmanus.jouring;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

public class ByteBuffers {
    private static final VarHandle ADDRESS_VAR_HANDLE;

    static {
        try {
            Class<?> dbbClass = Class.forName("java.nio.DirectByteBuffer");
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(dbbClass, MethodHandles.lookup());
            ADDRESS_VAR_HANDLE = lookup.findVarHandle(dbbClass, "address", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static long address(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer is not direct");
        }
        return (long) ADDRESS_VAR_HANDLE.get(buffer);
    }

}