package tasksandroid;

import java.util.concurrent.Executor;

/**
 * Created by Arash on 8/11/2015.
 */
public class AndroidExecutors {
    /**
     * wraps the Handler in an Executor object, useful when using Tasks in android ui
     */
    public static Executor toExecutor(final Handler handler){
        notNull(handler, "handler cannot be null");
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * returns an Executor that schedules commands on the activity's UI thread
     */
    public static Executor toExecutor(final Activity activity){
        notNull(activity, "activity cannot be null");
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                activity.runOnUiThread(command);
            }
        };
    }

    /**
     * returns an Executor that schedules commands on the app's main thread
     */
    public static Executor executorFromMainThread(){
        //just to make things contrived
        return sExecutorFromUiThread != null? sExecutorFromUiThread : (sExecutorFromUiThread = toExecutor(new Handler(Looper.getMainLooper())));
    }

}
