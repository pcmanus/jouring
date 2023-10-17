#include <stdlib.h>
#include <liburing.h>
//#include <stdio.h>
#include "libjouring.h"

extern struct io_uring* create_ring(int depth) {
    struct io_uring *ring = malloc(sizeof(struct io_uring));
    io_uring_queue_init(depth, ring, 0);
    return ring;
}

// Assumes that `completed` is big enough; basically must have size "depth * 2" to be safe.
extern int submit_and_check_completions(
    struct io_uring* ring,
    struct read_submission *reads,
    int nr_reads,
    bool check_completion_pre_submission,
    struct read_submission_result *res,
    long completed_ids[]
) {
    //fprintf(stdout, "Ok, I'm called\n");

    unsigned head;
    struct io_uring_cqe *cqe;
    struct io_uring_sqe *sqe;

    res->submitted = 0;
    res->completed = 0;

    //fprintf(stdout, "Set the result values\n");

    if (check_completion_pre_submission) {
        unsigned i = 0;
        io_uring_for_each_cqe(ring, head, cqe) {
            long id = (long) io_uring_cqe_get_data(cqe);
            //fprintf(stdout, "[C %d] completed[%ld] = %ld, \n", res->completed, id);
            completed_ids[res->completed++] = id;
            i++;
        }
        io_uring_cq_advance(ring, i);
    }

    bool has_submitted = false;
    for (int i = 0; i < nr_reads; i++) {
        sqe = io_uring_get_sqe(ring);
        if (!sqe) {
            break;
        }

        has_submitted = true;
        //fprintf(stdout, "[S %d] id=%ld\n", i, reads->id);
        //fprintf(stdout, "[S %d] fd=%d\n", i, reads->fd);
        //fprintf(stdout, "[S %d] address=%p\n", i, reads->buf_base);
        //fprintf(stdout, "[S %d] offset=%ld\n", i, reads->offset);
        //fprintf(stdout, "[S %d] length=%d\n", i, reads->buf_length);
        io_uring_prep_read(sqe, reads->fd, reads->buf_base, reads->buf_length, reads->offset);
        io_uring_sqe_set_data(sqe, (void*) (uintptr_t) reads->id);
        reads++;
        res->submitted++;
    }
    if (has_submitted) {
        io_uring_submit(ring);
    }
    if (has_submitted || !check_completion_pre_submission) {
        unsigned i = 0;
        io_uring_for_each_cqe(ring, head, cqe) {
            long id = (long) io_uring_cqe_get_data(cqe);
            //fprintf(stdout, "[C %d] completed[%ld] = %ld, \n", res->completed, id);
            completed_ids[res->completed++] = id;
            i++;
        }
        io_uring_cq_advance(ring, i);
    }

    return 0;
}

extern void destroy_ring(struct io_uring* ring) {
    io_uring_queue_exit(ring);
    free(ring);
}
