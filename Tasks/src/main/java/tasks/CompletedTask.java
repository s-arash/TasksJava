package tasks;

import java.util.concurrent.Executor;

import static tasks.ArgumentValidation.notNull;

/**
 * represents an already completed Task
 */
class CompletedTask<T> extends Task<T> {

    private final T _result;
    private final Executor _continuationExecutor;


    public CompletedTask(T result, Executor continuationExecutor) {
        this._result = result;
        this._continuationExecutor = notNull(continuationExecutor);
    }

    @Override
    public State getState() {
        return State.Succeeded;
    }

    @Override
    public T result() throws Exception {
        return _result;
    }

    @Override
    public Exception getException() {
        return null;
    }

    @Override
    public void registerCompletionCallback(final Action<Task<T>> callback) {
        getContinuationExecutor().execute(TaskUtils.toRunnable(callback, this));
    }

    @Override
    protected Executor getContinuationExecutor() {
        return _continuationExecutor;
    }

}

/**
 * represents a task with Failed state
 */
class FaultedTask<T> extends Task<T>{

    private final Exception _exception;
    private final Executor _continuationExecutor;

    FaultedTask(Exception exception, Executor continuationExecutor) {
        this._exception = notNull(exception);
        this._continuationExecutor = notNull(continuationExecutor);
    }

    @Override
    public State getState() {
        return State.Failed;
    }

    @Override
    public T result() throws Exception {
        throw _exception;
    }

    @Override
    public Exception getException() {
        return _exception;
    }

    @Override
    public void registerCompletionCallback(Action<Task<T>> callback) {
        getContinuationExecutor().execute(TaskUtils.toRunnable(callback, this));
    }

    @Override
    protected Executor getContinuationExecutor() {
        return _continuationExecutor;
    }
}