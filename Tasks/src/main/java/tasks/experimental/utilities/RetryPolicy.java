package tasks.experimental.utilities;

import tasks.Function;
import tasks.Ref;
import tasks.Task;
import tasks.TaskUtils;
import tasks.internal.FunctionNoEx;
import tasks.internal.Utils;

import java.util.concurrent.Callable;


public abstract class RetryPolicy {
    public static class HandleResult {
        public final boolean shouldHandle;
        public final int waitTime;
        public final RetryPolicy nextPolicy;

        public HandleResult(boolean shouldHandle, int waitTime, RetryPolicy nextPolicy) {
            this.shouldHandle = shouldHandle;
            this.waitTime = waitTime;
            this.nextPolicy = nextPolicy;
        }

    }

    public abstract HandleResult handle(Exception ex);

    public <T> Task<T> runAsync(final Callable<Task<T>> taskFactory) {
        final Ref<Task<T>> resultingTask = new Ref<>();
        final Ref<RetryPolicy> currentPolicy = new Ref<>(this);
        return Task.whileLoop(new Callable<Boolean>() {
                                  @Override
                                  public Boolean call() throws Exception {
                                      return resultingTask.value == null;
                                  }
                              }
            , new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() throws Exception {
                    return TaskUtils.fromFactory(taskFactory).continueWith(new Function<Task<T>, Task<Void>>() {
                        @Override
                        public Task<Void> call(Task<T> tTask) throws Exception {
                            if (tTask.getState() == Task.State.Succeeded) {
                                resultingTask.value = tTask;
                            } else {
                                HandleResult handleResult = currentPolicy.value.handle(tTask.getException());
                                if (handleResult.shouldHandle) {
                                    currentPolicy.value = handleResult.nextPolicy;
                                    if (handleResult.waitTime > 0) {
                                        return Task.delay(handleResult.waitTime);
                                    }
                                } else {
                                    resultingTask.value = tTask;
                                }
                            }
                            return Task.fromResult(null);
                        }
                    });
                }
            }).then(new Function<Void, Task<T>>() {
            @Override
            public Task<T> call(Void __) throws Exception {
                return resultingTask.value;
            }
        });
    }

    public <T> T run(final Callable<T> valueFactory) throws Exception {
        RetryPolicy currentPolicy = this;
        while (true) {
            try {
                return valueFactory.call();
            } catch (Exception ex) {
                HandleResult handleResult = currentPolicy.handle(ex);
                if (handleResult.shouldHandle) {
                    currentPolicy = handleResult.nextPolicy;
                    if (handleResult.waitTime > 0) {
                        Thread.sleep(handleResult.waitTime);
                    }
                } else {
                    throw ex;
                }
            }
        }
    }

    /*
    combines two RetryPolicies, which means the first one will be asked to handle the exception,
    if it says the exception should not be handled, then the second one will be asked.
    example: RetryPolicy.create(ServerBusyException.class, 3, 5000).combine(RetryPolicy.create(IOException.class, 2, 1000))
    retries 3 times on ServerBusyExceptions, with a wait time of 5 seconds, and retries twice on IOExceptions, with a wait time of 1 second.
     */
    public RetryPolicy combine(final RetryPolicy retryPolicy2) {
        final RetryPolicy retryPolicy1 = this;
        return new RetryPolicy() {
            @Override
            public HandleResult handle(Exception ex) {
                HandleResult rp1Result = retryPolicy1.handle(ex);
                if (rp1Result.shouldHandle) {
                    return new HandleResult(true, rp1Result.waitTime, rp1Result.nextPolicy.combine(retryPolicy2));
                } else {
                    HandleResult rp2Result = retryPolicy2.handle(ex);
                    return new HandleResult(rp2Result.shouldHandle, rp2Result.waitTime, retryPolicy1.combine(rp2Result.nextPolicy));
                }
            }
        };
    }

    public static <T extends Exception> RetryPolicy create(int retryCount, int waitTimeMilliseconds, final Class<T> exceptionClass) {
        return create(retryCount,waitTimeMilliseconds,new Class[]{exceptionClass});

    }

    public static <T extends Exception> RetryPolicy create(int retryCount, FunctionNoEx<Integer, Integer> waitTimeFunc, final Class<T> exceptionClass) {
        return create(retryCount, waitTimeFunc, new Class[]{exceptionClass});
    }

    public static RetryPolicy create(int retryCount, int waitTimeMilliseconds, final FunctionNoEx<Exception, Boolean> shouldHandle) {
        return new SimpleRetryPolicy(shouldHandle, waitTimeMilliseconds, retryCount);
    }

    /**
     * Creates a {@link RetryPolicy} object that retries {@code retryCount} times, with the wait time of {@code waitTimeMilliseconds} between them;
     * if the exceptions thrown are of a type found in {@code exceptionTypes}.
     *
     * @param retryCount           the maximum number of time to retry
     * @param waitTimeMilliseconds the time to wait between each retry
     * @param exceptionTypes       the list of exception types that RetryPolicy should keep retrying on.
     */
    @SafeVarargs
    public static RetryPolicy create(int retryCount, int waitTimeMilliseconds, final Class<? extends Exception>... exceptionTypes) {
        return new SimpleRetryPolicy(new FunctionNoEx<Exception, Boolean>() {
            @Override
            public Boolean call(Exception ex) {
                return objectIsOfTypes(ex, exceptionTypes);
            }
        }, waitTimeMilliseconds, retryCount);
    }

    /**
     * Creates a {@link RetryPolicy} object that retries {@code retryCount} times;
     * if the exceptions thrown are of a type found in {@code exceptionTypes}.
     *
     * @param retryCount     the maximum number of time to retry
     * @param waitTimeFunc   the function that determines the time to wait based on the try number.
     * @param exceptionTypes the list of exception types that RetryPolicy should keep retrying on.
     */
    @SafeVarargs
    public static RetryPolicy create(int retryCount, FunctionNoEx<Integer, Integer> waitTimeFunc, final Class<? extends Exception>... exceptionTypes) {
        return create(retryCount, waitTimeFunc, new FunctionNoEx<Exception, Boolean>() {
            @Override
            public Boolean call(Exception ex) {
                return objectIsOfTypes(ex, exceptionTypes);
            }
        });
    }

    /**
     * Creates a {@link RetryPolicy} object that retries a maximum of {@code retryCount} times;
     *
     * @param retryCount   the maximum number of time to retry
     * @param waitTimeFunc the function that determines the time to wait based on the try number.
     * @param shouldHandle a function that given an exception thrown, determines if the RetryPolicy object should keep retrying.
     */
    public static RetryPolicy create(int retryCount, final FunctionNoEx<Integer, Integer> waitTimeFunc, final FunctionNoEx<Exception, Boolean> shouldHandle) {
        return new SimpleRetryPolicy(shouldHandle, waitTimeFunc, retryCount);
    }


    static class SimpleRetryPolicy extends RetryPolicy {

        private final int mRetryCount;
        private int mAttemptNo = 1;
        private FunctionNoEx<Integer, Integer> mWaitTimeFunc;
        private FunctionNoEx<Exception, Boolean> mShouldHandleFunc;

        SimpleRetryPolicy(FunctionNoEx<Exception, Boolean> shouldHandleFunc, final int waitTime, int retryCount) {
            this(shouldHandleFunc, new FunctionNoEx<Integer, Integer>() {
                @Override
                public Integer call(Integer integer) {
                    return waitTime;
                }
            }, retryCount);

        }

        SimpleRetryPolicy(FunctionNoEx<Exception, Boolean> shouldHandleFunc, FunctionNoEx<Integer, Integer> waitTimeFunc, int retryCount) {
            mWaitTimeFunc = waitTimeFunc;
            mRetryCount = retryCount;
            mShouldHandleFunc = shouldHandleFunc != null ? shouldHandleFunc : new FunctionNoEx<Exception, Boolean>() {
                @Override
                public Boolean call(Exception e) {
                    return true;
                }
            };
        }

        public boolean shouldHandle(Exception ex) {
            return true;
        }

        @Override
        public HandleResult handle(Exception ex) {
            boolean shouldHandle = this.mRetryCount > 0 && mShouldHandleFunc.call(ex);
            int waitTime = mWaitTimeFunc.call(this.mAttemptNo);
            final SimpleRetryPolicy thisRetryPolicy = this;

            SimpleRetryPolicy nextPolicy = new SimpleRetryPolicy(mShouldHandleFunc, mWaitTimeFunc, Math.max(0, mRetryCount - 1));
            nextPolicy.mAttemptNo = this.mAttemptNo + 1;
            return new HandleResult(shouldHandle, waitTime, nextPolicy);
        }
    }

    public static boolean objectIsOfTypes(Object object, final Class<?>... types) {
        for (Class<?> klass : types) {
            if (klass.isInstance(object)) {
                return true;
            }
        }
        return false;
    }

}