package io.github.pcmanus.jouring;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import static io.github.pcmanus.jouring.FFMHelper.*;

final class Layouts {
    static final GroupLayout ringLayout = MemoryLayout.structLayout(
            MemoryLayout.structLayout(
                    C_POINTER.withName("khead"),
                    C_POINTER.withName("ktail"),
                    C_POINTER.withName("kring_mask"),
                    C_POINTER.withName("kring_entries"),
                    C_POINTER.withName("kflags"),
                    C_POINTER.withName("kdropped"),
                    C_POINTER.withName("array"),
                    C_POINTER.withName("sqes"),
                    C_INT.withName("sqe_head"),
                    C_INT.withName("sqe_tail"),
                    C_SIZE_T.withName("ring_sz"),
                    C_POINTER.withName("ring_ptr"),
                    C_INT.withName("ring_mask"),
                    C_INT.withName("ring_entries"),
                    MemoryLayout.sequenceLayout(2, C_INT).withName("pad")
            ).withName("sq"),
            MemoryLayout.structLayout(
                    C_POINTER.withName("khead"),
                    C_POINTER.withName("ktail"),
                    C_POINTER.withName("kring_mask"),
                    C_POINTER.withName("kring_entries"),
                    C_POINTER.withName("kflags"),
                    C_POINTER.withName("koverflow"),
                    C_POINTER.withName("cqes"),
                    C_SIZE_T.withName("ring_sz"),
                    C_POINTER.withName("ring_ptr"),
                    C_INT.withName("ring_mask"),
                    C_INT.withName("ring_entries"),
                    MemoryLayout.sequenceLayout(2, C_INT).withName("pad")
            ).withName("cq"),
            C_INT.withName("flags"),
            C_INT.withName("ring_fd"),
            C_INT.withName("features"),
            C_INT.withName("enter_ring_fd"),
            C_CHAR.withName("int_flags"),
            MemoryLayout.sequenceLayout(3, C_CHAR).withName("pad"),
            C_INT.withName("pad2")
    ).withName("io_uring");

    static final FunctionDescriptor queueInitDescriptor = FunctionDescriptor.of(
            C_INT,
            C_INT, C_POINTER, C_INT
    );

    static final MethodHandle queueInitHandle = downCallHandle("io_uring_queue_init", queueInitDescriptor);

    static final FunctionDescriptor getSqeDescriptor = FunctionDescriptor.of(
            C_POINTER,
            C_POINTER
    );

    static final MethodHandle getSqeHandle = downCallHandle("io_uring_get_sqe", getSqeDescriptor);

    static final FunctionDescriptor getPrepNopDescriptor = FunctionDescriptor.ofVoid(C_POINTER);

    static final MethodHandle prepNopHandle = downCallHandle("io_uring_prep_nop", getPrepNopDescriptor);

    static final FunctionDescriptor setDataDescriptor = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

    static final MethodHandle setDataHandle = downCallHandle("io_uring_sqe_set_data", setDataDescriptor);

    static final FunctionDescriptor submitDescriptor = FunctionDescriptor.of(
            C_INT,
            C_POINTER
    );

    static final MethodHandle submitHandle = downCallHandle("io_uring_submit", submitDescriptor);

    private Layouts(){}
}
