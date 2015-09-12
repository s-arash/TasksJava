package tasks.experimental.utilities;

import tasks.Function;
import tasks.Task;
import tasks.TaskUtils;
import java.util.concurrent.Callable;

/**
 * Use this class to cache results of async ({@link Task} returning) computations
 */
public class TaskCache<T> {
    private final Callable<Task<T>> mTaskFactory;
    private final long mCacheTtl;
    private final long mErrorCacheTtl;
    private Task<T> mTask;
    private long taskCompletionTimeNano;


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
    }

    /**
     * @return the taskFactory provided to this {@link TaskCache} instance
     */
    public Callable<Task<T>> getTaskFactory() {
        return mTaskFactory;
    }

    private Task<T> createTask() {
        Task<T> task = TaskUtils.fromFactory(mTaskFactory);
        return task.continueWith(new Function<Task<T>, Task<T>>() {
            @Override
            public Task<T> call(Task<T> x) throws Exception {
                synchronized (TaskCache.this) {
                    taskCompletionTimeNano = System.nanoTime();
                    return x;
                }
            }
        });
    }

    /**
     * @return returns a possibly cached {@link Task} object created by the taskFactory.
     */
    public synchronized Task<T> get() {
        if (this.mTask == null) {
            this.mTask = createTask();
            return mTask;
        } else if (mTask.getState() == Task.State.NotDone) {
            return mTask;
        } else if (mTask.getState() == Task.State.Succeeded && (mCacheTtl < 0 || System.nanoTime() - taskCompletionTimeNano <= mCacheTtl * 1000000)) {
            return mTask;
        } else if (mTask.getState() == Task.State.Failed && (mErrorCacheTtl < 0 || System.nanoTime() - taskCompletionTimeNano <= mErrorCacheTtl * 1000000)) {
            return mTask;
        } else {
            this.mTask = createTask();
            return mTask;
        }
    }

    /**
     * resets this {@link TaskCache} instance, so a subsequent call to {@link #get()} results in creating a new {@link Task} from the provided taskFactory.
     */
    public void Reset() {
        this.mTask = null;
    }

}
