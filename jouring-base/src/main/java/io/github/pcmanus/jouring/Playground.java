package io.github.pcmanus.jouring;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

public class Playground {
    private static final int RING_SIZE = 4;

    public static void main(String[] args) throws Throwable {
        GroupLayout myStructLayout = MemoryLayout.structLayout(
                FFMHelper.C_INT.withName("foo"),
                FFMHelper.C_INT.withName("bar")
        );

        VarHandle myStruct$foo = myStructLayout
                .varHandle(MemoryLayout.PathElement.groupElement("foo"));
        VarHandle myStruct$bar = myStructLayout
                .varHandle(MemoryLayout.PathElement.groupElement("bar"));


        try (IOUring ioUring = new IOUring()) {
            ioUring.init(RING_SIZE);

            MemorySegment myStruct = ioUring.arena.allocate(myStructLayout);
            myStruct$foo.set(myStruct, 1);
            myStruct$bar.set(myStruct, 2);

            ioUring.submitNop(myStruct);


        }

        //MemorySegment cqes = MemorySegment.allocateNative(io_uring_cqe.$LAYOUT(), session);
        //ret = liburing.io_uring_wait_cqe(ring, cqes);
        //if (ret < 0) {
        //    System.out.printf("wait_cqe: %d", ret);
        //    return;
        //}

        //// struct io_uring_cqe **cqeRef = malloc(sizeof *cqeRef);
        //MemorySegment cqeRef = MemorySegment.allocateNative(Constants.C_POINTER, session);
        //liburing.io_uring_wait_cqe(ring, cqeRef);
        //// struct io_uring_cqe *cqe = *cqeRef;
        //MemoryAddress cqe = cqeRef.get(Constants.C_POINTER, 0);
        //MemoryAddress user_data = liburing.io_uring_cqe_get_data(cqe);
        //MemorySegment user_data_segment = MemorySegment.ofAddress(user_data, myStructLayout.byteSize(), session);
        //int foo = (int) myStruct$foo.get(user_data_segment);
        //int bar = (int) myStruct$bar.get(user_data_segment);
        //System.out.println("foo: " + foo + ", bar: " + bar);

        //liburing.io_uring_cqe_seen(ring, cqe);

    }
}
