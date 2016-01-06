package tasks;

import tasks.annotations.Experimental;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static tasks.ArgumentValidation.elementsNotNull;
import static tasks.ArgumentValidation.notNull;
import static tasks.internal.Utils.getRuntimeException;

/**
 * A class that represents asynchronous work. Tasks can either complete successfully or with an error.
 * This class also contains methods for building and composing Tasks.
 *
 * @param <T> The type of the value that the Task is going to contain once it is completed
 */
public abstract class Task<T> {
    public enum State {
        NotDone,
        Succeeded,
        Failed,
    }

    public abstract State getState();

    /**
     * returns the value computed by this Task, or in the case of a Task that has failed, throws the exception.
     * If the Task isn't done yet, blocks the current thread until it completes.
     * NOTE: Use this method with caution, if the Task's completion depends on running code on the current thread's
     * message queue, calling this method results in a deadlock
     */
    public abstract T result() throws Exception;

    /**
     * @return if the Task resulted in an {@link Exception}, returns the {@code Exception},
     * otherwise (if it succeeded or it's not done yet) returns null
     */
    public abstract Exception getException();

    /**
     * registers the given callback to run on this Task's continuationExecutor when it completes.
     */
    public abstract void registerCompletionCallback(Action<Task<T>> callback);

    /**
     * registers the given callback to run immediately when this Task is done (without scheduling it
     * on the continuationExecutor).
     * The registered callback MUST be a short running action and not hog the thread! It also should
     * not throw any exceptions. Any exceptions thrown by the callback WILL BE SILENTLY SWALLOWED
     * because the show must go on!
     */
    abstract void registerImmediateCompletionCallback(Action<Task<T>> callback);

    protected Executor getContinuationExecutor() {
        return TaskSharedStuff.defaultExecutor;
    }

    /**
     * registers the given continuation to run when the current Task object is done, either successfully or in failure.
     *
     * @return a Task that is given by the continuation
     */
    public final <U> Task<U> continueWith(final Function<Task<T>, Task<U>> continuation) {
        notNull(continuation, "continuation cannot be null");
        final TaskBuilder<U> continuationTaskBuilder = new TaskBuilder<>(getContinuationExecutor());
        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                continuationTaskBuilder.bindToATaskFactory(new Callable<Task<U>>() {
                    @Override
                    public Task<U> call() throws Exception {
                        return continuation.call(Task.this);
                    }
                });
            }
        });
        return continuationTaskBuilder.getTask();
    }

    /**
     * registers the given continuation to run when the current Task is done, either successfully or in failure.
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
     * registers the given continuation to run when the current Task object completes, either successfully or in failure.
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
     * continues this task with the given continuation, if this Task succeeds.
     *
     * @param continuation
     * @return a Task that:
     * - if this Task succeeds, is given by continuation
     * - if this Task fails, so does the returned Task
     */
    public final <U> Task<U> then(final Function<? super T, Task<U>> continuation) {
        notNull(continuation, "continuation cannot be null");
        final TaskBuilder<U> continuationTaskBuilder = new TaskBuilder<>(getContinuationExecutor());

        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                State state = arg.getState();
                if (state == State.Failed) {
                    continuationTaskBuilder.setException(arg.getException());
                } else if (state == State.Succeeded) {
                    continuationTaskBuilder.bindToATaskFactory(new Callable<Task<U>>() {
                        @Override
                        public Task<U> call() throws Exception {
                            return continuation.call(arg.result());
                        }
                    });
                } else throw new Exception("ERROR in then(). invalid task state");
            }
        });
        return continuationTaskBuilder.getTask();
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
     * the returned Task completes successfully if this Task does, otherwise (if this Task fails),
     * is the result of the given catchBody
     */
    public final <TException extends Exception> Task<T> tryCatch(final Class<TException> exceptionType, final Function<? super TException, Task<T>> catchBody) {
        notNull(exceptionType, "exceptionType cannot be null");
        notNull(catchBody, "catchBody cannot be null");

        final TaskBuilder<T> taskBuilder = new TaskBuilder<T>(getContinuationExecutor());

        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(final Task<T> arg) throws Exception {
                State state = arg.getState();
                if (state == State.Succeeded) {
                    taskBuilder.setResult(arg.result());
                } else if (state == State.Failed) {
                    final Exception taskException = arg.getException();
                    if (exceptionType.isInstance(taskException)) {
                        final TException exceptionAsTException = (TException) taskException;
                        taskBuilder.bindToATaskFactory(new Callable<Task<T>>() {
                            @Override
                            public Task<T> call() throws Exception {
                                return catchBody.call(exceptionAsTException);
                            }
                        });
                    } else {
                        taskBuilder.setException(taskException);
                    }
                } else throw new Exception("ERROR in tryCatch(). invalid task state");
            }
        });

        return taskBuilder.getTask();
    }

    /**
     * returns a Task that completes successfully if this Task does, otherwise (if this Task fails),
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
     * The finally block runs when the task is done.
     * The task returned by this method has the completion state and value of the original task.
     *
     * @return a Task that runs finallyBlock after this Task is done, and retains the completion state of this Task
     * (unless finallyBlock fails, in which case it will fail with the same Exception)
     */
    public final Task<T> tryFinally(final Function<Void, Task<Void>> finallyBlock) {
        notNull(finallyBlock, "finallyBlock cannot be null");
        final TaskBuilder<T> taskBuilder = new TaskBuilder<T>(getContinuationExecutor());
        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(Task<T> arg) throws Exception {
                taskBuilder.bindToATaskFactory(new Callable<Task<T>>() {
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

        return taskBuilder.getTask();
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
     * returns a Task that closes the given resource after body is done (whether it succeeds or fails). The result of the returned Task will be
     * the same as the result of body.
     *
     * @param resource the given resource that will be closed when body is done
     * @param body     performs an async operation with resource
     */
    @Experimental
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
     * @return a Task that holds the results of the two Tasks in a pair, or an Exception if any of the given tasks fails
     */
    public final <U> Task<Pair<T, U>> zip(final Task<U> other) {
        return this.zip(other, new Function2<T, U, Pair<T, U>>() {
            @Override
            public Pair<T, U> call(T t, U u) throws Exception {
                return new Pair<>(t, u);
            }
        });
    }

    /**
     * returns a Task that bundles the results of the two tasks together using the given {@code zipper}
     * @return a Task that holds the results of the two Tasks zipped together, or an Exception if any of the given tasks fails
     */
    public final <T2,TOut> Task<TOut> zip(final Task<T2> other, final Function2<T,T2,TOut> zipper){
        return Task.whenAny(this, notNull(other, "other cannot be null")).then(new Function<Task<?>, Task<TOut>>() {
            @Override
            public Task<TOut> call(Task<?> task) throws Exception {
                if (task.getState() == State.Failed) {
                    return Task.fromException(task.getException());
                } else {
                    Task<?> unfinished = task == Task.this ? other : Task.this;
                    return unfinished.then(new Function<Object, Task<TOut>>() {
                        @Override
                        public Task<TOut> call(Object o) throws Exception {
                            return Task.fromResult(zipper.call(Task.this.result(), other.result()));
                        }
                    });
                }
            }
        });
    }

    /**
     * returns a Task like this one, with the difference being that the returned Task will schedule its continuations on the given {@link Executor}.
     * the given executor propagates to all the Tasks created from the returned Task (using then(), tryCatch(), and their siblings)
     *
     * @return a Task that schedules its continuations on the given {@link Executor}.
     */
    public final Task<T> continueOn(Executor continuationExecutor) {
        TaskBuilder<T> taskBuilder = new TaskBuilder<>(continuationExecutor);
        taskBuilder.bindToATask(this);
        return taskBuilder.getTask();
    }

    /**
     * returns a Task that, if this Task does not complete in the amount of time given by {@code timeout} and {@code timeUnit}, fails with a {@link TaskTimeoutException}
     *
     * @param timeout  the amount of time to wait before waiting with a {@link TaskTimeoutException}
     * @param timeUnit the unit of {timeout}
     */
    @Experimental
    public final Task<T> withTimeout(final long timeout, final TimeUnit timeUnit) {
        return Task.whenAny(this, Task.delay(timeout, timeUnit)).then(new Function<Task<?>, Task<T>>() {
            @Override
            public Task<T> call(Task<?> task) throws Exception {
                if (task == Task.this)
                    return Task.this;
                else
                    return Task.fromException(new TaskTimeoutException(Task.this, String.format("a task didn't finish in %d %s", timeout, timeUnit.name())));
            }
        }).continueOn(Task.this.getContinuationExecutor());
    }

    /**
     * returns a Task that, if this Task does not complete in the amount of time given by {@code timeoutMillis}, fails with a {@link TaskTimeoutException}
     *
     * @param timeoutMillis the amount of time to wait before waiting with a {@link TaskTimeoutException}
     */
    @Experimental
    public final Task<T> withTimeout(final long timeoutMillis) {
        return withTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * returns a Task created from this Task that can be canceled by the given {@link CancellationToken}
     * @param cancellationToken
     */
    @Experimental
    public final Task<T> withCancellation(CancellationToken cancellationToken) {
        final TaskBuilder<T> taskBuilder = new TaskBuilder<>(this.getContinuationExecutor());
        this.registerCompletionCallback(new Action<Task<T>>() {
            @Override
            public void call(Task<T> tTask) throws Exception {
                if (taskBuilder.getTask().isDone()) return;
                try {
                    if (getException() != null)
                        taskBuilder.setException(getException());
                    else
                        taskBuilder.setResult(result());
                } catch (UnsupportedOperationException ex) {
                    //this means that the task was canceled. Don't need to do anything here.
                }
            }
        });
        cancellationToken.registerCanceledCallback(new Action<CancellationToken>() {
            @Override
            public void call(CancellationToken cancellationToken) throws Exception {
                if (taskBuilder.getTask().isDone()) return;
                try {
                    taskBuilder.setException(new CancellationException());
                } catch (UnsupportedOperationException ex) {
                    //this means that the task was already completed. Don't need to do anything here.
                }
            }
        });
        return taskBuilder.getTask();
    }


    public final T resultOr(T fallBackValue) {
        if (this.getState() == State.Succeeded) {
            try {
                return this.result();
            } catch (Exception e) {
                throw getRuntimeException(e);
            }
        } else {
            return fallBackValue;
        }
    }

    /**
     * blocks the current thread until this Task is done
     */
    public final void waitForCompletion() {
        try {
            result();
        } catch (Exception ex) {
        }
    }

    /**
     * @return if the task has completed (either succeeded or failed) returns true, otherwise returns false
     */
    public final boolean isDone() {
        State state = this.getState();
        return state == State.Succeeded || state == State.Failed;
    }

    /**
     * converts the Task to a Future object
     */
    public final Future<T> toFuture() {
        return new TaskFuture.FutureFromTask<T>(this);
    }

    //--------------Factory methods----------------

    /**
     * Creates a Task object from a {@link Future}.
     * Note that in order to get completion notification, this method spawns a new thread, which is suboptimal to say the least. You can blame Java's Future design for that.
     *
     * @return the Task created form {@code future}
     */
    public static <T> Task<T> fromFuture(Future<? extends T> future) {
        notNull(future, "future cannot be null");
        return TaskFuture.toTask(future);
    }

    /**
     * returns a task that completes after the given timeout. This is the {@link Thread#sleep(long)} of the async world!
     */
    public static Task<Void> delay(long timeout, TimeUnit timeUnit) {
        return delay(notNull(timeUnit, "timeUnit cannot be null").toMillis(timeout));
    }

    private static Timer delayTimer = new Timer(false);
    /**
     * returns a task that completes after the given timeout. This is the {@link Thread#sleep(long)} of the async world!
     */
    public static Task<Void> delay(long timeoutMilliseconds) {
        final TaskBuilder<Void> taskBuilder = new TaskBuilder<>();
        delayTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                taskBuilder.setResult(null);
            }
        }, timeoutMilliseconds);
        return taskBuilder.getTask();
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
     * creates a Task already in Failed state with the given {@code exception}
     */
    public static <T> Task<T> fromException(Exception exception) {
        return new FaultedTask<T>(exception, TaskSharedStuff.defaultExecutor);
    }

    /**
     * creates a Task already in Failed state with the given {@code exception}, which schedules its continuations on the given {@link Executor}
     */
    public static <T> Task<T> fromException(Exception exception, Executor continuationExecutor) {
        return new FaultedTask<T>(exception, continuationExecutor);
    }

    /**
     * unwraps the wrapped task
     */
    @Experimental
    public static <T> Task<T> unwrap(Task<Task<T>> wrappedTask) {
        return wrappedTask.then(new Function<Task<T>, Task<T>>() {
            @Override
            public Task<T> call(Task<T> tTask) throws Exception {
                return tTask;
            }
        });
    }


    /**
     * returns a Task that completes when any of the give tasks complete (either successfully or in failure)
     * the returned task will contain the first finished task as its result
     *
     * @return a Task that completes when any of the given Tasks is done, with that Task as its result
     */
    public static Task<Task<?>> whenAny(Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) throw new IllegalArgumentException("tasks cannot be empty");
        elementsNotNull(tasks, "elements of tasks cannot be empty");

        final AtomicBoolean done = new AtomicBoolean();
        final TaskBuilder<Task<?>> taskBuilder = new TaskBuilder<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> arg) throws Exception {
                if (done.compareAndSet(false, true)) { // if you DID set the value
                    taskBuilder.setResult(arg);
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return taskBuilder.getTask();
    }

    /**
     * returns a Task that completes when any of the given tasks complete successfully.
     * the returned task will contain the first successfully completed task as its result. If none of the tasks complete successfully,
     * the returned task will fail with the exception of the last task of tasks failed.
     *
     * @return a Task that completes when any of the given Tasks succeeds, with that Task as its result
     */
    public static Task<Task<?>> whenAnySucceeds(final Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) throw new IllegalArgumentException("tasks cannot be empty");
        elementsNotNull(tasks, "elements of tasks cannot be empty");

        final AtomicBoolean done = new AtomicBoolean();
        final AtomicInteger faultedTasksCount = new AtomicInteger();
        final TaskBuilder<Task<?>> taskBuilder = new TaskBuilder<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> task) throws Exception {
                if (!done.get()) {
                    if (task.getState() == State.Succeeded) {
                        if (done.compareAndSet(false, true)) { // if you DID set the value
                            taskBuilder.setResult(task);
                        }
                    } else {
                        if (faultedTasksCount.incrementAndGet() == tasks.length) {
                            taskBuilder.setException(task.getException());
                        }
                    }
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return taskBuilder.getTask();
    }

    /**
     * returns a task that completes when all of the given tasks are done (either in success or failure)
     */
    public static Task<Task<?>[]> whenAll(final Task<?>... tasks) {
        notNull(tasks, "tasks cannot be null");
        if (tasks.length == 0) return Task.fromResult(tasks);

        final AtomicInteger doneCount = new AtomicInteger();
        final TaskBuilder<Task<?>[]> taskBuilder = new TaskBuilder<>();
        Action<Task<Object>> callback = new Action<Task<Object>>() {
            @Override
            public void call(Task<Object> arg) throws Exception {
                if (doneCount.incrementAndGet() == tasks.length) {
                    taskBuilder.setResult(tasks);
                }
            }
        };
        for (Task<?> task : tasks) {
            Task<Object> t = (Task<Object>) task;
            t.registerCompletionCallback(callback);
        }
        return taskBuilder.getTask();
    }

    /**
     * creates a Task object that runs the given {@code code} on the given {@link Executor}
     */
    public static <T> Task<T> run(final Callable<? extends T> code, Executor executor) {
        notNull(code, "code cannot be null");
        final TaskBuilder taskBuilder = new TaskBuilder(executor);
        notNull(executor, "executor cannot be null").
            execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                taskBuilder.setResult(code.call());
                            } catch (Exception ex) {
                                taskBuilder.setException(ex);
                            }
                        }
                    }
            );
        return taskBuilder.getTask();
    }

    /**
     * creates a Task object that runs the given {@code code} asynchronously
     */
    public static <T> Task<T> run(final Callable<? extends T> code) {
        return Task.run(code, TaskSharedStuff.defaultExecutor);
    }

    /**
     * the async equivalent of the for-each loop in Java
     *
     * @return a Task that is the result of applying the body function to elements in {@code seq} in succession,
     * each task is created after the last one is completed
     */
    public static <T> Task<Void> forEach(Iterable<? extends T> seq, final Function<? super T, Task<Void>> body) {
        notNull(seq, "seq cannot be null");
        notNull(body, "body cannot be null");

        final TaskBuilder<Void> taskBuilder = new TaskBuilder<>();
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
                                    taskBuilder.setException(arg.getException());
                                else
                                    run();
                            }
                        });
                    } else {
                        taskBuilder.setResult(null);
                    }
                } catch (Exception ex) {
                    taskBuilder.setException(ex);
                }
            }
        };
        runnable.run();
        return taskBuilder.getTask();
    }

    /**
     * returns a {@code Task<List<TOut>>} that contains the results of the Tasks of the body function, applied to elements of seq,
     * each Task is created after the last one is completed
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
     * using the async accumulator function, and the initial {@code seed},
     * this method reduces the given sequence ({@code seq}) to a single value asynchronously.
     * @return a {@link Task} that will hold the reduced value.
     */
    public static <T, TAcc> Task<TAcc> reduce(Iterable<? extends T> seq, TAcc seed, final Function2<TAcc,T,Task<TAcc>> accumulator){
        final Ref<TAcc> acc = new Ref<>(seed);
        return forEach(seq, new Function<T, Task<Void>>() {
            @Override
            public Task<Void> call(T t) throws Exception {
                return accumulator.call(acc.value,t).thenSync(new Function<TAcc, Void>() {
                    @Override
                    public Void call(TAcc tAcc) throws Exception {
                        acc.value = tAcc;
                        return null;
                    }
                });
            }
        }).thenSync(new Function<Void, TAcc>() {
            @Override
            public TAcc call(Void __) throws Exception {
                return acc.value;
            }
        });
    }

    /**
     * the async equivalent of a {@code while(condition){body}} loop
     *
     * @return a Task that is the result of running body as long as condition is true
     */
    public static Task<Void> whileLoop(final Callable<Boolean> condition, final Callable<Task<Void>> body) {
        notNull(condition, "condition cannot be null");
        notNull(body, "body cannot be null");

        final TaskBuilder<Void> taskBuilder = new TaskBuilder<>();
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
                                    taskBuilder.setException(arg.getException());
                                else
                                    run();
                            }
                        });
                    } else {
                        taskBuilder.setResult(null);
                    }
                } catch (Exception ex) {
                    taskBuilder.setException(ex);
                }
            }
        };
        runnable.run();
        return taskBuilder.getTask();
    }

    /**
     * the async equivalent of a {@code do{ body }while(condition);} loop
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

