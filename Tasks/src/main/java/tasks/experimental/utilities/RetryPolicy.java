package tasks.experimental.utilities;

import tasks.Function;
import tasks.Ref;
import tasks.Task;
import tasks.TaskUtils;
import tasks.internal.Utils;

import java.util.concurrent.Callable;


public abstract class RetryPolicy {
    public static class HandleResult {
        public boolean shouldHandle;
        public int waitTime;
        public RetryPolicy nextPolicy;

        public HandleResult(boolean shouldHandle, int waitTime, RetryPolicy nextPolicy) {
            this.shouldHandle = shouldHandle;
            this.waitTime = waitTime;
            this.nextPolicy = nextPolicy;
        }

        public HandleResult() {
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
            public Task<T> call(Void _) throws Exception {
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

    public static <T extends Exception> RetryPolicy create(final Class<T> exceptionClass, int retryCount, int waitTimeMilliseconds) {
        return create(retryCount, waitTimeMilliseconds, new Function<Exception, Boolean>() {
            @Override
            public Boolean call(Exception e) throws Exception {
                return exceptionClass.isInstance(e);
            }
        });
    }

    public static <T extends Exception> RetryPolicy create(final Class<T> exceptionClass, int retryCount, Function<Integer,Integer> waitTimeFunc) {
        return create(retryCount, waitTimeFunc, exceptionClass);
    }

    public static RetryPolicy create(int retryCount, int waitTimeMilliseconds, final Function<Exception, Boolean> shouldHandle) {
        return new SimpleRetryPolicy(waitTimeMilliseconds, retryCount) {
            @Override
            public boolean shouldHandle(Exception ex) {
                try {
                    return shouldHandle.call(ex);
                } catch (Exception thrownException) {
                    throw Utils.getRuntimeException(thrownException);
                }
            }
        };
    }

    @SafeVarargs
    public static RetryPolicy create(int retryCount, int waitTimeMilliseconds, final Class<? extends Exception>... exceptionTypes) {
        return new SimpleRetryPolicy(waitTimeMilliseconds, retryCount) {
            @Override
            public boolean shouldHandle(Exception ex) {
                return objectIsOfTypes(ex, exceptionTypes);
            }
        };
    }

    @SafeVarargs
    public static RetryPolicy create(int retryCount, Function<Integer,Integer> waitTimeFunc, final Class<? extends Exception>... exceptionTypes) {
        return create(retryCount, waitTimeFunc, new Function<Exception, Boolean>() {
            @Override
            public Boolean call(Exception ex) throws Exception {
                return objectIsOfTypes(ex, exceptionTypes);
            }
        });
    }

    public static RetryPolicy create(int retryCount, final Function<Integer,Integer> waitTimeFunc, final Function<Exception, Boolean> shouldHandle) {
        return new SimpleRetryPolicy(0, retryCount) {
            @Override
            public boolean shouldHandle(Exception ex) {
                try {
                    return shouldHandle.call(ex);
                } catch (Exception thrownException) {
                    throw Utils.getRuntimeException(thrownException);
                }
            }

            @Override
            protected int getWaitTime(int attemptNo) {
                try {
                    return waitTimeFunc.call(attemptNo);
                } catch (Exception e) {
                    throw Utils.getRuntimeException(e);
                }
            }
        };
    }


    static class SimpleRetryPolicy extends RetryPolicy {

        private final int mRetryCount;
        private int mWaitTime;
        private int mAttemptNo = 1;

        SimpleRetryPolicy(int waitTime, int retryCount) {
            this.mWaitTime = waitTime;
            this.mRetryCount = retryCount;
        }

        protected int getWaitTime(int attemptNo){
            return mWaitTime;
        };

        public boolean shouldHandle(Exception ex) {
            return true;
        }

        @Override
        public HandleResult handle(Exception ex) {

            HandleResult result = new HandleResult();
            result.shouldHandle = this.mRetryCount > 0 && shouldHandle(ex);
            result.waitTime = getWaitTime(this.mAttemptNo);
            final SimpleRetryPolicy thisRetryPolicy = this;

            SimpleRetryPolicy nextPolicy = new SimpleRetryPolicy(mWaitTime, Math.max(0, mRetryCount - 1)) {
                @Override
                public boolean shouldHandle(Exception ex) {
                    return thisRetryPolicy.shouldHandle(ex);
                }

                @Override
                protected int getWaitTime(int attemptNo) {
                    return thisRetryPolicy.getWaitTime(attemptNo);
                }
            };
            nextPolicy.mAttemptNo = this.mAttemptNo + 1;
            result.nextPolicy = nextPolicy;
            return result;
        }
    }

    public static boolean objectIsOfTypes(Object object, final Class<?>... types){
        for(Class<?> klass: types){
            if(klass.isInstance(object)){
                return true;
            }
        }
        return false;
    }

}