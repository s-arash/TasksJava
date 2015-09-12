package tasks.android;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import static tasks.ArgumentValidation.notNull;


public class AndroidExecutors {
    /**
     * wraps the Handler in an Executor object
     */
    public static Executor from(final Handler handler){
        notNull(handler, "handler cannot be null");
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * returns an Executor that schedules actions on the activity's UI thread
     */
    public static Executor from(final Activity activity){
        notNull(activity, "activity cannot be null");
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                activity.runOnUiThread(command);
            }
        };
    }

    private static Executor executorFromMainThread;
    /**
     * returns an Executor that schedules actions on the app's main thread
     */
    public static Executor fromMainThread(){
        //just to make things contrived
        return executorFromMainThread != null? executorFromMainThread : (executorFromMainThread = from(new Handler(Looper.getMainLooper())));
    }

}
