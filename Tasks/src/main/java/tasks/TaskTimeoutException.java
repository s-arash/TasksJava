package tasks;

/**
 * Created by sahebolamri on 9/5/2015.
 */
public class TaskTimeoutException extends Exception {
    private final Task<?> timedOutTask;

    public TaskTimeoutException(Task<?> timedoutTask,String message) {
        super(message);
        this.timedOutTask = timedoutTask;

    }

    public Task<?> getTimedOutTask(){
        return timedOutTask;
    }

}

