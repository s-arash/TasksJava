package tasks;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static tasks.ArgumentValidation.elementsNotNull;
import static tasks.ArgumentValidation.notNull;

/**
 * A class that represents asynchronous work. Tasks can either complete successfully or with an error.
 * This class also contains methods for building and composing Tasks.
 *
 * @param <T> The type of the value that the Task is going to contain once it is completed
 */
public abstract class Task<T> {
    public enum State {
        NotCompleted,
        CompletedSuccessfully,
        CompletedInError,
    }

    public abstract State getState();

    /**
     * registers the given continuation to run when the current Task object completes, either successfully or in error.
     *
     * @return a Task that is given by the continuation
     */
    public final <U> Task<U> continueWith(final Function<Task<T>, Task<U>> continuation) {
        notNull(continuation, "continuation cannot be null");
        final TaskManualCompletion<U> continuationManualCompletion = new TaskManualCompletion<>(getContinuationExecutor());
        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                continuationManualCompletion.bindToATaskFactory(new Callable<Task<U>>() {
                    @Override
                    public Task<U> call() throws Exception {
                        return continuation.call(Task.this);
                    }
                });
            }
        });
        return continuationManualCompletion.getTask();
    }

    /**
     * registers the given continuation to run when the current Task object completes, either successfully or in error.
     *
     * @return a Task that wraps the value returned by the continuation
     */
    public final <U> Task<U> continueWithSync(final Function<Task<T>, U> continuation) {
        notNull(continuation, "continuation cannot be null");
        return continueWith(new Function<Task<T>, Task<U>>() {
            @Override
            public Task<U> call(Task<T> x) throws Exception {
                return Task.fromResult(continuation.call(x));
            }
        });
    }

    /**
     * registers the given continuation to run when the current Task object completes, either successfully or in error.
     */
    public final Task<Void> continueWithSync(final Action<Task<T>> continuation) {
        notNull(continuation, "continuation cannot be null");
        return continueWithSync(new Function<Task<T>, Void>() {
            @Override
            public Void call(Task<T> x) throws Exception {
                continuation.call(x);
                return null;
            }
        });
    }

    /**
     * continues this task with the given continuation, if this Task completes successfully.
     *
     * @param continuation
     * @return a Task that:
     * - if this Task completes successfully, is given by continuation
     * - if this Task fails, so does the returned Task
     */
    public final <U> Task<U> then(final Function<? super T, Task<U>> continuation) {
        notNull(continuation, "continuation cannot be null");
        final TaskManualCompletion<U> continuationManualCompletion = new TaskManualCompletion<>(getContinuationExecutor());

        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                State state = arg.getState();
                if (state == State.CompletedInError) {
                    continuationManualCompletion.setException(arg.getException());
                } else if (state == State.CompletedSuccessfully) {
                    continuationManualCompletion.bindToATaskFactory(new Callable<Task<U>>() {
                        @Override
                        public Task<U> call() throws Exception {
                            return continuation.call(arg.result());
                        }
                    });
                } else throw new Exception("ERROR in then(). invalid task state");
            }
        });
        return continuationManualCompletion.getTask();
    }

    /**
     * sync version of {@link #then}
     *
     * @param continuation the synchronous continuation
     */
    public final <U> Task<U> thenSync(final Function<? super T, ? extends U> continuation) {
        notNull(continuation, "continuation cannot be null");
        return this.then(new Function<T, Task<U>>() {
            @Override
            public Task<U> call(T x) throws Exception {
                return Task.fromResult((U) continuation.call(x));
            }
        });
    }

    /**
     * sync version of {@link #then}
     *
     * @param continuation the void returning synchronous continuation
     */
    public final Task<Void> thenSync(final Action<? super T> continuation) {
        notNull(continuation, "continuation cannot be null");
        return this.thenSync(new Function<T, Void>() {
            @Override
            public Void call(T x) throws Exception {
                continuation.call(x);
                return null;
            }
        });
    }

    /**
     * This is an alias for {@link #thenSync(Function)}
     *
     * @see #thenSync(Function)
     */
    public final <U> Task<U> map(final Function<? super T, ? extends U> mapFunction) {
        return thenSync(mapFunction);
    }

    /**
     * returns a Task that catches an exception of type exceptionType from this task.
     * the returned Task completes successfully if this Task does, otherwise (if this Task completes in error),
     * is the result of the given catchBody
     */
    public final <TException extends Exception> Task<T> tryCatch(final Class<TException> exceptionType, final Function<? super TException, Task<T>> catchBody) {
        notNull(exceptionType, "exceptionType cannot be null");
        notNull(catchBody, "catchBody cannot be null");

        final TaskManualCompletion<T> tmc = new TaskManualCompletion<T>(getContinuationExecutor());

        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                State state = arg.getState();
                if (state == State.CompletedSuccessfully) {
                    tmc.setResult(arg.result());
                } else if (state == State.CompletedInError) {
                    final Exception taskException = arg.getException();
                    if (exceptionType.isInstance(taskException)) {
                        final TException exceptionAsTException = (TException) taskException;
                        tmc.bindToATaskFactory(new Callable<Task<T>>() {
                            @Override
                            public Task<T> call() throws Exception {
                                return catchBody.call(exceptionAsTException);
                            }
                        });
                    } else {
                        tmc.setException(taskException);
                    }
                } else throw new Exception("ERROR in tryCatch(). invalid task state");
            }
        });

        return tmc.getTask();
    }

    /**
     * returns a Task that completes successfully if this Task does, otherwise (if this Task completes in error),
     * is the result of the given catchBody
     */
    public final Task<T> tryCatch(final Function<? super Exception, Task<T>> catchBody) {
        return tryCatch(Exception.class, catchBody);
    }

    /**
     * sync version of {@link #tryCatch}
     */
    public final Task<T> tryCatchSync(final Function<? super Exception, ? extends T> syncCatchBody) {
        notNull(syncCatchBody, "syncCatchBody cannot be null");
        return this.tryCatch(new Function<Exception, Task<T>>() {
            @Override
            public Task<T> call(Exception x) throws Exception {
                return Task.fromResult(syncCatchBody.call(x));
            }
        });
    }

    /**
     * sync version of {@link #tryCatch}
     */
    public final <TException extends Exception> Task<T> tryCatchSync(final Class<TException> exceptionType, final Function<? super TException, ? extends T> syncCatchBody) {
        notNull(syncCatchBody, "syncCatchBody cannot be null");
        return this.tryCatch(exceptionType, new Function<TException, Task<T>>() {
            @Override
            public Task<T> call(TException x) throws Exception {
                return Task.fromResult(syncCatchBody.call(x));
            }
        });
    }

    /**
     * The finally block runs when the task completes, either successfully or in error.
     * The task returned by this method still has the state and value of the original task.
     */
    public final Task<T> tryFinally(final Function<Void, Task<Void>> finallyBlock) {
        notNull(finallyBlock, "finallyBlock cannot be null");
        final TaskManualCompletion<T> tmc = new TaskManualCompletion<T>(getContinuationExecutor());
        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(Task<T> arg) throws Exception {
                tmc.bindToATaskFactory(new Callable<Task<T>>() {
                    @Override
                    public Task<T> call() throws Exception {
                        return finallyBlock.call(null).then(new Function<Void, Task<T>>() {
                            @Override
                            public Task<T> call(Void x) throws Exception {
                                return Task.this;
                            }
                        });
                    }
                });
            }
        });

        return tmc.getTask();
    }

    /**
     * sync version of {@link #tryFinally}
     */
    public final Task<T> tryFinallySync(final Action<Void> finallyBlock) {
        notNull(finallyBlock, "finallyBlock cannot be null");
        return this.tryFinally(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void x) throws Exception {
                finallyBlock.call(null);
                return Task.fromResult(null);
            }
        });
    }

    /**
     * returns a Task that closes the given resource after body is done whether successfully or in error. The result of the returned Task will be
     * the same as the result of body.
     *
     * @param resource the given resource that will be closed when body is done
     * @param body     performs an async operation with resource
     */
    public static <T, U extends Closeable> Task<T> tryWithResource(final U resource, final Function<? super U, Task<T>> body) {
        return TaskUtils.fromFactory(new Callable<Task<T>>() {
            @Override
            public Task<T> call() throws Exception {
                return body.call(resource);
            }
        }).continueWith(new Function<Task<T>, Task<T>>() {
            @Override
            public Task<T> call(Task<T> bodyTask) throws Exception {
                try {
                    if (resource != null) resource.close();
                } catch (Exception ex) {
                    if (bodyTask.getException() != null)
                        return bodyTask;
                    else
                        throw ex;
                }
                return bodyTask;
            }
        });
    }


    /**
     * returns a Task that bundles the results of the two tasks together in a {@link Pair}
     *
     * @return a Task that holds the results of the two tasks in a pair, or an exception if any of the given tasks fails
     */
    public final <U> Task<Pair<T, U>> zip(final Task<U> other) {
        return Task.whenAny(this, notNull(other, "other cannot be null")).then(new Function<Task<?>, Task<Pair<T, U>>>() {
            @Override
            public Task<Pair<T, U>> call(Task<?> task) throws Exception {
                if (task.getState() == State.CompletedInError) {
                    return Task.fromException(task.getException());
                } else {
                    Task<?> unfinished = task == Task.this ? other : Task.this;
                    return unfinished.then(new Function<Object, Task<Pair<T, U>>>() {
                        @Override
                        public Task<Pair<T, U>> call(Object o) throws Exception {
                            return Task.fromResult(new Pair<T, U>(Task.this.result(), other.result()));
                        }
                    });
                }
            }
        });

    }

    /**
     * returns a Task that schedules its continuations on the given {@link Executor}.
     * the given executor propagates to all the Tasks created from this Task (using then(), tryCatch(), and their siblings)
     */
    public final Task<T> continueOn(Executor continuationExecutor) {
        TaskManualCompletion<T> tmc = new TaskManualCompletion<>(continuationExecutor);
        tmc.bindToATask(this);
        return tmc.getTask();
    }

    protected Executor getContinuationExecutor() {
        return TaskSharedStuff.defaultExecutor;
    }

    /**
     * returns the value computed by the task, or in the case of a Task completed in error, throws the exception.
     * If the task isn't completed yet, blocks the current thread until it completes.
     * NOTE: use this method with caution, if the Task's completion depends on running code on the current thread's
     * message queue, calling this method results in a deadlock
     */
    public abstract T result() throws Exception;

    /**
     * blocks the current thread until the task completes
     */
    public final void waitForCompletion() {
        try {
            result();
        } catch (Exception ex) {
        }
    }

    /**
     * @return if the task resulted in an exception, returns the exception, otherwise returns null
     */
    public abstract Exception getException();

    /**
     * @return if the task has completed (successfully or with and exception) returns true, otherwise returns false
     */
    public final boolean isDone() {
        State state = this.getState();
        return state == State.CompletedSuccessfully || state == State.CompletedInError;
    }

    /**
     * registers the given callback to run on this Task's continuationExecutor when it completes.
     */
    public abstract void registerCompletionCallback(Action<Task<T>> callback);

    /**
     * wraps the Task as a Future object
     */
    public final Future<T> asFuture() {
        return new TaskFuture.FutureFromTask<T>(this);
    }
    //--------------Factory methods----------------

    /**
     * Creates a Task object from a {@link Future}.
     * Note that in order to get completion notification, this method spawns a new thread, which is suboptimal to say the least. You can blame Java's Future design for that.
     * That's one reason why you should steer clear from using Java's Futures as much as possible if you wanna right async code.
     *
     * @return the Task created form the Future
     */
    public static <T> Task<T> fromFuture(Future<? extends T> future) {
        notNull(future, "future cannot be null");
        return TaskFuture.toTask(future);
    }

    private static Timer delayTimer = new Timer(false);


    /**
     * returns a task that completes after the given timeout. This is the {@link Thread#sleep(long)} of the async world!
     */
    public static Task<Void> delay(long timeout, TimeUnit timeUnit) {
        return delay(notNull(timeUnit, "timeUnit cannot be null").toMillis(timeout));
    }

    /**
     * returns a task that completes after the given timeout. This is the {@link Thread#sleep(long)} of the async world!
     */
    public static Task<Void> delay(long timeoutMilliseconds) {
        final TaskManualCompletion<Void> tmc = new TaskManualCompletion<>();
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                tmc.setResult(null);
            }
        }, timeoutMilliseconds);
        return tmc.getTask();
    }

    /**
     * creates a Task already completed with {@code result}
     */
    public static <T> Task<T> fromResult(T result) {
        return new CompletedTask<T>(result, TaskSharedStuff.defaultExecutor);
    }


    /**
     * a completed {@link Task}&lt;{@link Void}&gt;
     */
    public static final Task<Void> unit = Task.fromResult(null);

    /**
     * creates a Task already completed with {@code result}, which schedules its continuations on the given {@link Executor}
     */
    public static <T> Task<T> fromResult(T result, Executor continuationExecutor) {
        return new CompletedTask<>(result, continuationExecutor);
    }

    /**
     * creates a Task already in CompletedInError state with the given {@code exception}
     */
    public static <T> Task<T> fromException(Exception exception) {
        return new FaultedTask<T>(exception, TaskSharedStuff.defaultExecutor);
    }

    /**
     * creates a Task already in CompletedInError state with the given {@code exception}, which schedules its continuations on the given {@link Executor}
     */
    public static <T> Task<T> fromException(Exception exception, Executor continuationExecutor) {
        return new FaultedTask<T>(exception, continuationExecutor);
    }

    /**
     * unwraps the wrapped task
     */
    public static <T> Task<T> unwrap(Task<Task<T>> wrappedTask) {
        return wrappedTask.then(new Function<Task<T>, Task<T>>() {
            @Override
            public Task<T> call(Task<T> tTask) throws Exception {
                return tTask;
            }
        });
    }


    /**
     * returns a Task that completes when any of the give tasks complete (either successfully or with error)
     * the returned task will contain the first finished task as its result
     */
    public static Task<Task<?>> whenAny(Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) throw new IllegalArgumentException("tasks cannot be empty");
        elementsNotNull(tasks, "elements of tasks cannot be empty");

        final AtomicBoolean done = new AtomicBoolean();
        final TaskManualCompletion<Task<?>> tmc = new TaskManualCompletion<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> arg) throws Exception {
                if (done.compareAndSet(false, true)) { // if you DID set the value
                    tmc.setResult(arg);
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return tmc.getTask();
    }

    /**
     * returns a Task that completes when any of the given tasks complete successfully.
     * the returned task will contain the first successfully completed task as its result. If none of
     * the tasks complete successfully, the returned task will complete in error with the exception of the last task of tasks that completed in error.
     */
    public static Task<Task<?>> whenAnySucceeds(final Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) throw new IllegalArgumentException("tasks cannot be empty");
        elementsNotNull(tasks, "elements of tasks cannot be empty");

        final AtomicBoolean done = new AtomicBoolean();
        final AtomicInteger faultedTasksCount = new AtomicInteger();
        final TaskManualCompletion<Task<?>> tmc = new TaskManualCompletion<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> task) throws Exception {
                if (!done.get()) {
                    if (task.getState() == State.CompletedSuccessfully) {
                        if (done.compareAndSet(false, true)) { // if you DID set the value
                            tmc.setResult(task);
                        }
                    } else {
                        if (faultedTasksCount.incrementAndGet() == tasks.length) {
                            tmc.setException(task.getException());
                        }
                    }
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return tmc.getTask();
    }

    /**
     * returns a task that completes when all of the given tasks complete (either successfully or with error)
     */
    public static Task<Task<?>[]> whenAll(final Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) return Task.fromResult(tasks);

        final AtomicInteger doneCount = new AtomicInteger();
        final TaskManualCompletion<Task<?>[]> tmc = new TaskManualCompletion<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> arg) throws Exception {
                if (doneCount.incrementAndGet() == tasks.length) {
                    tmc.setResult(tasks);
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return tmc.getTask();
    }

    /**
     * creates a Task object that runs the given function on the given {@link Executor}
     */
    public static <T> Task<T> run(final Callable<? extends T> function, Executor executor) {
        notNull(function, "function cannot be null");
        final TaskManualCompletion tmc = new TaskManualCompletion(executor);
        notNull(executor, "executor cannot be null").
            execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                tmc.setResult(function.call());
                            } catch (Exception ex) {
                                tmc.setException(ex);
                            }
                        }
                    }
            );
        return tmc.getTask();
    }

    /**
     * creates a Task object that runs the given {@code function} asynchronously
     */
    public static <T> Task<T> run(final Callable<? extends T> function) {
        return Task.run(function, TaskSharedStuff.defaultExecutor);
    }

    /**
     * returns a Task that is the result of applying the body function to elements in seq in succession,
     * each task is created after the last one is completed
     */
    public static <T> Task<Void> forEach(Iterable<? extends T> seq, final Function<? super T, Task<Void>> body) {
        notNull(seq, "seq cannot be null");
        notNull(body, "body cannot be null");

        final TaskManualCompletion<Void> tmc = new TaskManualCompletion<>();
        final Iterator<? extends T> iterator = seq.iterator();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (iterator.hasNext()) {
                        T item = iterator.next();
                        Task<Void> itemTask = body.call(item);
                        itemTask.registerCompletionCallback(new Action<Task<Void>>() {
                            @Override
                            public void call(Task<Void> arg) throws Exception {
                                if (arg.getException() != null)
                                    tmc.setException(arg.getException());
                                else
                                    run();
                            }
                        });
                    } else {
                        tmc.setResult(null);
                    }
                } catch (Exception ex) {
                    tmc.setException(ex);
                }
            }
        };
        runnable.run();
        return tmc.getTask();
    }

    /**
     * returns a {@code Task<List<TOut>>} that contains the results of the tasks of the body function, applied to elements of seq,
     * each task is created after the last one is completed
     */
    public static <TIn, TOut> Task<List<TOut>> forEachGenerate(Iterable<? extends TIn> seq, final Function<? super TIn, Task<TOut>> body) {
        notNull(body, "body cannot be null");

        final ArrayList<TOut> results = new ArrayList<>();
        return forEach(seq, new Function<TIn, Task<Void>>() {
            @Override
            public Task<Void> call(TIn arg) throws Exception {
                return body.call(arg).then(new Function<TOut, Task<Void>>() {
                    @Override
                    public Task<Void> call(TOut arg) throws Exception {
                        results.add(arg);
                        return Task.fromResult(null);
                    }
                });
            }
        }).then(new Function<Void, Task<List<TOut>>>() {
            @Override
            public Task<List<TOut>> call(Void arg) throws Exception {
                return Task.fromResult((List<TOut>) results);
            }
        });
    }

    /**
     * the async equivalent of a while(condition){body} loop
     *
     * @return a Task that is the result of running body as long as condition is true
     */
    public static Task<Void> whileLoop(final Callable<Boolean> condition, final Callable<Task<Void>> body) {
        notNull(condition, "condition cannot be null");
        notNull(body, "body cannot be null");

        final TaskManualCompletion<Void> tmc = new TaskManualCompletion<>();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (condition.call()) {
                        Task<Void> itemTask = body.call();
                        itemTask.registerCompletionCallback(new Action<Task<Void>>() {
                            @Override
                            public void call(Task<Void> arg) throws Exception {
                                if (arg.getException() != null)
                                    tmc.setException(arg.getException());
                                else
                                    run();
                            }
                        });
                    } else {
                        tmc.setResult(null);
                    }
                } catch (Exception ex) {
                    tmc.setException(ex);
                }
            }
        };
        runnable.run();
        return tmc.getTask();
    }

    /**
     * the async equivalent of a do{ body }while(condition); loop
     */
    public static Task<Void> doWhile(final Callable<Task<Void>> body, final Callable<Boolean> condition) {
        final Ref<Boolean> isFirstRun = new Ref<>(true);
        return whileLoop(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                if (isFirstRun.value) {
                    isFirstRun.value = false;
                    return true;
                }
                return condition.call();
            }
        }, body);
    }

}

