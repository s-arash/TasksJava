package tasks;

import tasks.internal.Utils;

import java.util.concurrent.*;

import static tasks.ArgumentValidation.notNull;

/**
 * static methods to convert Tasks to Java's Futures and vice versa
 */
class TaskFuture {

    private static Object futureCompletionThreadPoolSyncLock = new Object();
    private static Executor futureCompletionThreadPool;
    private static Executor getFutureCompletionThreadPool(){
        //this is Executors.newCachedThreadPool()'s body:
        //return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
        //        60L, TimeUnit.SECONDS,
        //        new SynchronousQueue<Runnable>());

        if(futureCompletionThreadPool == null){
            synchronized (futureCompletionThreadPoolSyncLock) {
                if(futureCompletionThreadPool == null) {
                    futureCompletionThreadPool =
                            new ThreadPoolExecutor(0, 1024,
                                    5L, TimeUnit.SECONDS,
                                    new SynchronousQueue<Runnable>());
                }
            }
        }
        return futureCompletionThreadPool;
    }
    public static <T> Task<T> toTask(final Future<? extends T> future) {
        if (notNull(future, "future cannot be null").isDone()) {
            try {
                return Task.fromResult((T)future.get());
            } catch (InterruptedException e) {
                return Task.fromException(e);
            } catch (ExecutionException e) {
                return Task.fromException(e.getCause() != null ? Utils.getRuntimeException(e.getCause()) : e);
            }
        } else {
            final TaskBuilder<T> taskBuilder = new TaskBuilder<>();
            getFutureCompletionThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        taskBuilder.setResult(future.get());
                    } catch (InterruptedException e) {
                        taskBuilder.setException(e);
                    } catch (ExecutionException e) {
                        taskBuilder.setException(e.getCause() != null ? Utils.getRuntimeException(e.getCause()) : e);
                    }
                }
            });
            return taskBuilder.getTask();
        }
    }

    public static class FutureFromTask<T> implements Future<T> {

        private final Task<? extends T> mTask;

        public FutureFromTask(Task<? extends T> task) {
            this.mTask = notNull(task, "task cannot be null");
        }

        /**
         * You cannot cancel tasks like this.
         *
         * @param mayInterruptIfRunning dc
         * @return false
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            //no, we can't cancel tasks like this
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return mTask.isDone();
        }

        @Override
        public T get() throws ExecutionException {
            try {
                return mTask.result();
            } catch (Exception ex) {
                throw new ExecutionException(ex.getMessage(), ex);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
            Task<?> whenAnyResult;
            try {
                whenAnyResult = Task.whenAny(Task.delay(TimeUnit.MILLISECONDS.convert(timeout, unit)), mTask).result();
            } catch (Exception ex) {
                throw new Error();
            }
            if (whenAnyResult == mTask) {
                return get();
            } else {
                throw new TimeoutException("waiting for Future timed out. wait time: " + timeout + " " + unit.name());
            }
        }
    }

}
