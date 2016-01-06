package tasks;

import java.util.concurrent.Executor;

import tasks.annotations.Experimental;

/**
 * Created by sahebolamri on 12/28/2015.
 */
@Experimental
public class TaskExecutors {
    public static Executor defaultExecutor(){
        return TaskSharedStuff.defaultExecutor;
    }
}
