package tasks;

import tasks.annotations.Experimental;

@Experimental
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

