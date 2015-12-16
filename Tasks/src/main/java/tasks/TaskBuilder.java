package tasks;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static tasks.ArgumentValidation.notNull;
import static tasks.internal.Utils.getRuntimeException;

/**
 * This class can be used to create {@link Task} objects whose completion results or exceptions can be manually set
 */
public class TaskBuilder<T> {
    private T mResult;
    private Exception mException;
    private Task<T> mTheTask;
    private boolean isDone;
    private Executor continuationExecutor;

    private LinkedList<Action<Task<T>>> continuations = new LinkedList<>();
    private LinkedList<Action<Task<T>>> immediateContinuations = new LinkedList<>();
    private CountDownLatch resultSignal = new CountDownLatch(1);
    private final Object syncLock = new Object();

    public TaskBuilder() {
        this(TaskSharedStuff.defaultExecutor);
    }

    public TaskBuilder(final Executor continuationExecutor) {
        this.continuationExecutor = notNull(continuationExecutor);

        mTheTask = new Task<T>() {
            @Override
            public State getState() {
                if (!isDone) return State.NotDone;
                else if (mException != null) return State.Failed;
                else return State.Succeeded;
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
                    scheduleContinuation(callback);
                } else {
                    boolean shouldCallContinuation = false;
                    synchronized (syncLock) {
                        if (isDone)
                            shouldCallContinuation = true;
                        else
                            continuations.add(callback);
                    }
                    if (shouldCallContinuation) scheduleContinuation(callback);
                }
            }

            @Override
            void registerImmediateCompletionCallback(Action<Task<T>> callback) {
                if(isDone){
                    try{
                        callContinuation(callback);
                    }catch (Exception ex){
                        throw getRuntimeException(ex);
                    }
                }else{
                    boolean shouldCallContinuation = false;
                    synchronized (syncLock){
                        if (isDone)
                            shouldCallContinuation = true;
                        else
                            immediateContinuations.add(callback);
                    }
                    if(shouldCallContinuation) {
                        try {
                            callContinuation(callback);
                        } catch (Exception ex) {
                            throw getRuntimeException(ex);
                        }
                    }
                }
            }

            @Override
            protected Executor getContinuationExecutor() {
                return continuationExecutor;
            }
        };
    }

    /**
     * @return the {@link Task} object whose completion status is controlled by this {@link TaskBuilder} instance
     */
    public Task<T> getTask() {
        return mTheTask;
    }

    private void scheduleContinuation(final Action<Task<T>> callback) {

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

    private void callContinuation(Action<Task<T>> callback) throws Exception {
        callback.call(mTheTask);
    }

    /**
     * marks the Task given by this {@link TaskBuilder} instance as succeeded with the given result
     * @throws UnsupportedOperationException if the task was already done
     */
    public void setResult(T result) {
        setExceptionOrResult(null, result);
    }

    /**
     * marks the Task given by this {@link TaskBuilder} instance as failed with the given Exception
     *
     * @param exception the exception to set on the task
     * @throws UnsupportedOperationException if the task was already done
     */
    public void setException(Exception exception) {
        setExceptionOrResult(notNull(exception, "exception cannot be null"), null);
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
        while (! immediateContinuations.isEmpty()){
            try{
                callContinuation(immediateContinuations.remove());
            }catch (Exception ex){
                java.util.logging.Logger.getLogger("Tasks").severe("Exception in immediate task continuation: \r\n" + ex.getMessage());
                ex.printStackTrace();
            }
        }

        while (!continuations.isEmpty())
            scheduleContinuation(continuations.remove());
    }

    /**
     * Binds the completion result or Exception of the Task returned by this instance to the completion result of the given task
     */
    public void bindToATask(Task<T> task) {
        notNull(task, "task cannot be null").registerImmediateCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(Task<T> arg) throws Exception {
                Task.State state = arg.getState();
                if (state == Task.State.Failed)
                    setException(arg.getException());
                else if (state == Task.State.Succeeded)
                    setResult(arg.result());
                else
                    throw new IllegalStateException("the task is in an illegal state");
            }
        });
    }

    /**
     * Binds the completion result or Exception of the Task returned by this instance to the completion result of the task provided by the given taskFactory
     */
    public void bindToATaskFactory(Callable<Task<T>> taskFactory) {
        notNull(taskFactory, "taskFactory cannot be null");
        try {
            bindToATask(taskFactory.call());
        } catch (Exception ex) {
            setException(ex);
        }
    }
}
