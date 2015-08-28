package tasks;

import tasks.internal.Utils;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static tasks.ArgumentValidation.notNull;
/**
 * a collection of helper methods useful when working with Tasks and other threading constructs
 */
public class TaskUtils {
    private static Executor sExecutorFromUiThread;

    public static <T> Runnable toRunnable(final Action<T> action, final T value){
        return new Runnable() {
            @Override
            public void run() {
                try {
                    action.call(value);
                }catch (Exception ex){
                    throw Utils.getRuntimeException(ex);
                }
            }
        };
    }

    /**
     * returns the result of calling taskFactory, or if it throws an exception, a task that holds the exception thrown by it
     */
    public static <T> Task<T> fromFactory(Callable<Task<T>> taskFactory){
        try{
            return taskFactory.call();
        }catch (Exception ex){
            return Task.fromException(ex);
        }
    }
}
