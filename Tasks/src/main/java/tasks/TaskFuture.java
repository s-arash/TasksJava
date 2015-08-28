package tasks;

import tasks.internal.Utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static tasks.ArgumentValidation.notNull;
/**
 * Created by sahebolamri on 7/5/2015.
 */
class TaskFuture {
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
            final TaskManualCompletion<T> tmc = new TaskManualCompletion<>();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        tmc.setResult(future.get());
                    } catch (InterruptedException e) {
                        tmc.setException(e);
                    } catch (ExecutionException e) {
                        tmc.setException(e.getCause() != null ? Utils.getRuntimeException(e.getCause()) : e);
                    }
                }
            }).start();
            return tmc.getTask();
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
