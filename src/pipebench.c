/*
 *
 * pipebench.c
 *
 * pipe: Benchmark for pipe()
 *
 * Based on pipe-test-1m.c by Ingo Molnar <mingo@redhat.com>
 *  http://people.redhat.com/mingo/cfs-scheduler/tools/pipe-test-1m.c
 */

#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/wait.h>
#include <string.h>
#include <errno.h>
#include <assert.h>
#include <sys/time.h>
#include <sys/types.h>
#include <pthread.h>
#include <stdbool.h>

// Utility macros and constants
#define BUG_ON(condition) do { if (condition) { fprintf(stderr, "Bug on: %s\n", #condition); exit(1); } } while (0)
#define USEC_PER_SEC 1000000
#define USEC_PER_MSEC 1000

struct thread_data {
    int nr;
    int pipe_read;
    int pipe_write;
    pthread_t pthread;
};

#define LOOPS_DEFAULT 1000000
static int loops = LOOPS_DEFAULT;
static bool threaded;

static void print_usage(const char *prog_name) {
    printf("Usage: %s [options]\n", prog_name);
    printf("Options:\n");
    printf("  -l, --loop <number>     Specify number of loops (default: %d)\n", LOOPS_DEFAULT);
    printf("  -T, --threaded          Use threads instead of processes\n");
}

static void parse_options(int argc, const char **argv) {
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-l") == 0 || strcmp(argv[i], "--loop") == 0) {
            if (i + 1 < argc) {
                loops = atoi(argv[++i]);
            } else {
                fprintf(stderr, "Error: --loop requires a value\n");
                print_usage(argv[0]);
                exit(1);
            }
        } else if (strcmp(argv[i], "-T") == 0 || strcmp(argv[i], "--threaded") == 0) {
            threaded = true;
        } else {
            print_usage(argv[0]);
            exit(1);
        }
    }
}

static void *worker_thread(void *__tdata) {
    struct thread_data *td = (struct thread_data *)__tdata;
    int m = 0, ret;

    for (int i = 0; i < loops; i++) {
        if (!td->nr) {
            ret = read(td->pipe_read, &m, sizeof(int));
            BUG_ON(ret != sizeof(int));
            ret = write(td->pipe_write, &m, sizeof(int));
            BUG_ON(ret != sizeof(int));
        } else {
            ret = write(td->pipe_write, &m, sizeof(int));
            BUG_ON(ret != sizeof(int));
            ret = read(td->pipe_read, &m, sizeof(int));
            BUG_ON(ret != sizeof(int));
        }
    }

    return NULL;
}

int main(int argc, const char **argv) {
    struct thread_data threads[2];
    int pipe_1[2], pipe_2[2];
    struct timeval start, stop, diff;
    unsigned long long result_usec = 0;
    int nr_threads = 2;

    parse_options(argc, argv);

    BUG_ON(pipe(pipe_1));
    BUG_ON(pipe(pipe_2));

    gettimeofday(&start, NULL);

    for (int t = 0; t < nr_threads; t++) {
        threads[t].nr = t;
        if (t == 0) {
            threads[t].pipe_read = pipe_1[0];
            threads[t].pipe_write = pipe_2[1];
        } else {
            threads[t].pipe_write = pipe_1[1];
            threads[t].pipe_read = pipe_2[0];
        }
    }

    if (threaded) {
        for (int t = 0; t < nr_threads; t++) {
            int ret = pthread_create(&threads[t].pthread, NULL, worker_thread, &threads[t]);
            BUG_ON(ret);
        }

        for (int t = 0; t < nr_threads; t++) {
            int ret = pthread_join(threads[t].pthread, NULL);
            BUG_ON(ret);
        }
    } else {
        pid_t pid = fork();
        assert(pid >= 0);

        if (!pid) {
            worker_thread(&threads[0]);
            exit(0);
        } else {
            worker_thread(&threads[1]);
        }

        int wait_stat;
        pid_t retpid = waitpid(pid, &wait_stat, 0);
        assert((retpid == pid) && WIFEXITED(wait_stat));
    }

    gettimeofday(&stop, NULL);
    timersub(&stop, &start, &diff);

    printf("# Executed %d pipe operations between two %s\n\n", loops, threaded ? "threads" : "processes");

    result_usec = diff.tv_sec * USEC_PER_SEC;
    result_usec += diff.tv_usec;

    printf(" %14s: %lu.%03lu [sec]\n\n", "Total time", diff.tv_sec, (unsigned long)(diff.tv_usec / USEC_PER_MSEC));
    printf(" %14lf usecs/op\n", (double)result_usec / (double)loops);
    printf(" %14d ops/sec\n", (int)((double)loops / ((double)result_usec / (double)USEC_PER_SEC)));

    return 0;
}