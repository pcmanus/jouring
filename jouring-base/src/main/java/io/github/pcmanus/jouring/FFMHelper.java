package io.github.pcmanus.jouring;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

final class FFMHelper {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOL_LOOKUP;

    static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;
    static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT.withByteAlignment(2);
    static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withByteAlignment(4);
    static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG.withByteAlignment(8);
    static final ValueLayout.OfLong C_SIZE_T = ValueLayout.JAVA_LONG.withByteAlignment(8);
    static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT.withByteAlignment(4);
    static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE.withByteAlignment(8);
    static final AddressLayout C_POINTER = ValueLayout.ADDRESS.withByteAlignment(8);

    static {
        System.loadLibrary("liburing");
        SymbolLookup loaderLookup = SymbolLookup.loaderLookup();
        SYMBOL_LOOKUP = name -> loaderLookup.find(name).or(() -> LINKER.defaultLookup().find(name));
    }

    private FFMHelper() {
    }

    static MethodHandle downCallHandle(String name, FunctionDescriptor descriptor) {
        return SYMBOL_LOOKUP.find(name)
                .map(addr -> LINKER.downcallHandle(addr, descriptor))
                .orElseThrow(() ->
                        new IOUringException(String.format("Could not find handle for native function '%s'", name))
                );
    }

    static RuntimeException invokeError(MethodHandle handle) {
        return new RuntimeException(String.format("Unexpected error invoking native method '%s'", handle));
    }
}