package tasks;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class TaskSharedStuff {
    static final Executor defaultExecutor;

    static {
        //Todo this is not good. We need a better defaultExecutor.
        //it was practically unbounded, so lets give it a high number
        int nThreads = 1000; // Math.max(Runtime.getRuntime().availableProcessors(), 4) * 2;
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, nThreads,
            10L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
        //defaultExecutor = Executors.newCachedThreadPool();
        defaultExecutor = threadPoolExecutor;
    }

}
