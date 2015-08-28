package tasks;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static tasks.ArgumentValidation.notNull;
/**
 * Use this class to create Task objects whose completion values or errors can be manually set
 */
public class TaskManualCompletion<T> {
    private T mResult;
    private Exception mException;
    private Task<T> mTheTask;
    private boolean isDone;
    private Executor continuationExecutor;

    private LinkedList<Action<Task<T>>> continuations = new LinkedList<>();
    private CountDownLatch resultSignal = new CountDownLatch(1);
    private final Object syncLock = new Object();

    public TaskManualCompletion() {
        this(TaskSharedStuff.defaultExecutor);
    }

    public TaskManualCompletion(final Executor continuationExecutor) {
        this.continuationExecutor = notNull(continuationExecutor);

        mTheTask = new Task<T>() {
            @Override
            public State getState() {
                if (!isDone) return State.NotCompleted;
                else if (mException != null) return State.CompletedInError;
                else return State.CompletedSuccessfully;
            }

            @Override
            public T result() throws Exception {
                resultSignal.await();
                if (isDone) {
                    if (mException == null) return mResult;
                    else throw mException;
                } else
                    throw new Exception("internal error in TaskManualCompletion");
            }

            @Override
            public Exception getException() {
                return mException;
            }


            @Override
            public void registerCompletionCallback(Action<Task<T>> callback) {
                //this if is for optimization
                if (isDone) {
                    callContinuation(callback);
                } else {
                    boolean shouldCallContinuation = false;
                    synchronized (syncLock) {
                        if (isDone)
                            shouldCallContinuation = true;
                        else
                            continuations.add(callback);
                    }
                    if (shouldCallContinuation) callContinuation(callback);
                }
            }

            @Override
            protected Executor getContinuationExecutor() {
                return continuationExecutor;
            }
        };
    }

    public Task<T> getTask() {
        return mTheTask;
    }

    void callContinuation(final Action<Task<T>> callback) {

        continuationExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    callback.call(mTheTask);
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger("Tasks").severe("Exception in task continuation: \r\n" + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }

    public void setResult(T result) {
        setExceptionOrResult(null, result);
    }

    public void setException(Exception exception) {
        setExceptionOrResult(notNull(exception,"exception cannot be null"), null);
    }

    /**
     * at least one of the parameters must be null
     */
    private void setExceptionOrResult(Exception exception, T result) {
        synchronized (syncLock) {
            if (!isDone) {
                mResult = result;
                mException = exception;
                isDone = true;
                resultSignal.countDown();
            } else throw new UnsupportedOperationException("The task is already completed.");
        }

        while (!continuations.isEmpty())
            callContinuation(continuations.remove());
    }

    public void bindToATask(Task<T> task) {
        notNull(task,"task cannot be null").registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(Task<T> arg) throws Exception {
                Task.State state = arg.getState();
                if (state == Task.State.CompletedInError)
                    setException(arg.getException());
                else if (state == Task.State.CompletedSuccessfully)
                    setResult(arg.result());
                else
                    throw new IllegalStateException("the task is in an illegal state");
            }
        });
    }

    public void bindToATaskFactory(Callable<Task<T>> taskFactory) {
        notNull(taskFactory, "taskFactory cannot be null");
        try {
            bindToATask(taskFactory.call());
        } catch (Exception ex) {
            setException(ex);
        }
    }
}
