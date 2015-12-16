package tasks;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static tasks.internal.Utils.getRuntimeException;

/**
 * Created by sahebolamri on 12/9/2015.
 */
public abstract class CancellationToken {

    public abstract boolean isCanceled();

    public abstract void registerCanceledCallback(Action<CancellationToken> callback);

    public void throwIfCanceled() {
        if (isCanceled()) {
            throw new CancellationException();
        }
    }

    public void throwIfCanceled(String message) {
        if (isCanceled()) {
            throw new CancellationException(message);
        }
    }


    private static CancellationToken neverCancellationToken;

    /**
     * @return a {@link CancellationToken} that will never be canceled.
     */
    public static CancellationToken never() {
        if (neverCancellationToken == null) {
            synchronized (CancellationToken.class) {
                if (neverCancellationToken == null) {
                    neverCancellationToken = new CancellationToken() {
                        @Override
                        public boolean isCanceled() {
                            return false;
                        }

                        @Override
                        public void registerCanceledCallback(Action<CancellationToken> callback) {
                            //no op!
                        }
                    };
                }
            }
        }
        return neverCancellationToken;
    }

    private static CancellationToken canceledCancellationToken;

    /**
     * @return a {@link CancellationToken} that has already been canceled.
     */
    public static CancellationToken canceled() {
        if (canceledCancellationToken == null) {
            synchronized (CancellationToken.class) {
                if (canceledCancellationToken == null) {
                    canceledCancellationToken = new CancellationToken() {
                        @Override
                        public boolean isCanceled() {
                            return true;
                        }

                        @Override
                        public void registerCanceledCallback(Action<CancellationToken> callback) {
                            try {
                                callback.call(canceledCancellationToken);
                            } catch (Exception e) {
                                throw getRuntimeException(e);
                            }
                        }
                    };
                }
            }
        }
        return canceledCancellationToken;
    }

    /**
     * returns a {@link CancellationToken} that will be canceled when the given {@link Task} is done (whether successfully or with an exception)
     */
    public static <T> CancellationToken fromTask(final Task<T> task) {
        return new CancellationToken() {


            @Override
            public boolean isCanceled() {
                return task.isDone();
            }

            @Override
            public void registerCanceledCallback(final Action<CancellationToken> callback) {
                final CancellationToken me = this;
                task.registerCompletionCallback(new Action<Task<T>>() {
                    @Override
                    public void call(Task<T> tTask) throws Exception {
                        callback.call(me);
                    }
                });
            }
        };
    }

    /**
     * returns a {@link CancellationToken} that will be canceled after the given timeout.
     */
    public static CancellationToken timeout(int timeoutTime, TimeUnit timeUnit){
        return fromTask(Task.delay(timeoutTime,timeUnit));
    }
}
