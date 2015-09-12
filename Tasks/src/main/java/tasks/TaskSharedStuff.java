package tasks;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class TaskSharedStuff {
    static final Executor defaultExecutor;

    static {
        defaultExecutor = Executors.newCachedThreadPool();
    }

}
