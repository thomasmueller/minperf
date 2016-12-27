package org.minperf.generator;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * A tool that either runs tasks one after the other (in the caller thread), or
 * uses a ForkJoinPool to run the tasks in parallel.
 */
public class ConcurrencyTool {

    private final ForkJoinPool pool;

    public ConcurrencyTool(int parallelism) {
        if (parallelism > 1) {
            pool = new ForkJoinPool(parallelism);
        } else {
            pool = null;
        }
    }

    public <T> T invoke(ForkJoinTask<T> task) {
        if (pool != null) {
            return pool.invoke(task);
        }
        return task.invoke();
    }

    public void invokeAll(ForkJoinTask<?>... tasks) {
        if (pool != null) {
            ForkJoinTask.invokeAll(tasks);
            return;
        }
        for (ForkJoinTask<?> t : tasks) {
            t.invoke();
        }
    }

    public void shutdown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

}
