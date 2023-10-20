#ifndef LIBJOURING_H__
#define LIBJOURING_H__

//#include <stdio.h>
#include <liburing.h>

struct read_submission {
    int fd;
    int buf_length;
    void* buf_base;
    long offset;
    long id;
};

extern struct io_uring* create_ring(
    int depth,
    bool enableSQPoll,
    bool enableIOPoll
);


struct read_submission_result {
    int submitted;
    int completed;
};

// Returns how many completed there is.
extern int submit_and_check_completions(
    struct io_uring* ring,
    struct read_submission *reads,
    int nr_reads,
    bool check_completion_pre_submission,
    struct read_submission_result *result,
    long completed_ids[]
);


extern void destroy_ring(struct io_uring* ring);


extern int open_file(struct io_uring* ring, const char* path, bool direct);

#endif
