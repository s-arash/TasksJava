package tasks.utilities;

import net.denavas.tasks.Function;
import net.denavas.tasks.Task;
import net.denavas.tasks.TaskUtils;

import org.apache.commons.lang3.time.StopWatch;

import java.util.EnumSet;
import java.util.concurrent.Callable;

/**
 * Created by sahebolamri on 5/27/2015.
 */
public class TaskCache<T> {
    private final Callable<Task<T>> mTaskFactory;
    private final long mCacheTtl;
    private final long mErrorCacheTtl;
    private Task<T> mTask;
    private StopWatch mStopWatch;

    /**
     * creates a new instance of TaskCache
     * @param taskFactory the task factory used to generate cached tasks
     * @param cacheTtlMilliseconds the amount of time in milliseconds that the taskFactory computation result is cached. -1 means the result is cached forever.
     * @param errorCacheTtlMilliseconds the amount of time in milliseconds that the taskFactory computation error is cached. -1 means the error is cached forever.
     */
    public TaskCache(Callable<Task<T>> taskFactory, long cacheTtlMilliseconds, long errorCacheTtlMilliseconds) {
        this.mCacheTtl = cacheTtlMilliseconds;
        this.mErrorCacheTtl = errorCacheTtlMilliseconds;
        this.mTaskFactory = taskFactory;
        this.mStopWatch = new StopWatch();
    }

    public Callable<Task<T>> getTaskFactory() {
        return mTaskFactory;
    }

    private Task<T> createTask() {
        Task<T> task = TaskUtils.fromFactory(mTaskFactory);
        return task.continueWith(new Function<Task<T>, Task<T>>() {
            @Override
            public Task<T> call(Task<T> x) throws Exception {
                synchronized (TaskCache.this) {
                    mStopWatch.reset();
                    mStopWatch.start();
                    return x;
                }
            }
        });
    }

    public synchronized Task<T> get() {
        if (this.mTask == null) {
            this.mTask = createTask();
        } else if (mTask.getState() == Task.State.NotCompleted) {
            return mTask;
        } else if (mTask.getState() == Task.State.CompletedSuccessfully && (mCacheTtl < 0 || mStopWatch.getNanoTime() <= mCacheTtl * 1000000)) {
            return mTask;
        } else if (mTask.getState() == Task.State.CompletedInError && (mCacheTtl < 0 || mStopWatch.getNanoTime() <= mErrorCacheTtl * 1000000)) {
            return mTask;
        } else {
            this.mTask = createTask();
        }

        return mTask;
    }

    public void Reset() {
        this.mTask = null;
    }

    public enum Options {
        DoNotCacheResult,
    }
}
