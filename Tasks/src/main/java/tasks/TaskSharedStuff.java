package tasks;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
/**
 * Created by sahebolamri on 7/11/2015.
 */
class TaskSharedStuff {
    static final Executor defaultExecutor;

    static {
        defaultExecutor = Executors.newCachedThreadPool();
    }

}
