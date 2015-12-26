package tasks.experimental.utilities;


import tasks.Task;
import tasks.TaskUtils;

import java.util.concurrent.Callable;

/**
 * Use this class to wrap task returning code that you don't want to run concurrently.
 * This class also has the ability to cache the result of a task object.
 */
public class TaskGuard<T> {

    private final Callable<Task<T>> mTaskFactory;
    private final boolean mCacheResult;
    private Task<T> mTask;

    /***
     * Creates a new TaskGuard object
     * @param taskFactory the factory that produces tasks
     * @param cacheResult if true, the result of a successful Task will be cached, and taskFactory won't be called again.
     */
    public TaskGuard(Callable<Task<T>> taskFactory, boolean cacheResult) {
        this.mTaskFactory = taskFactory;
        this.mCacheResult = cacheResult;

    }

    public Callable<Task<T>> getTaskFactory() {
        return mTaskFactory;
    }

    public boolean cachesResult(){
        return mCacheResult;
    }

    /**
     * returns the guarded Task.
     * @return the guarded Task
     */
    public synchronized Task<T> get(){
        if(this.mTask == null || this.mTask.getState() == Task.State.Failed){
            this.mTask = TaskUtils.fromFactory(mTaskFactory);
        }else if(mTask.getState() == Task.State.Succeeded && ! mCacheResult){
            this.mTask = TaskUtils.fromFactory(mTaskFactory);
        }
        return mTask;
    }

    /**
     * resets the TaskGuard. After this call, calling {@link TaskGuard#get()} results in creating a new task.
     */
    public synchronized void reset(){
        this.mTask = null;
    }

}
