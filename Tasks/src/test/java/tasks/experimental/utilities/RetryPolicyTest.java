package tasks.experimental.utilities;


import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;
import tasks.Function;
import tasks.Ref;
import tasks.Task;

import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class RetryPolicyTest {

    static Callable<Task<Integer>> createFlakyTaskFactory(int throwCount, final Callable<Exception> exceptionFactory){
        final Ref<Integer> remainingThrows = new Ref<>(throwCount);
        return new Callable<Task<Integer>>() {
            @Override
            public Task<Integer> call() throws Exception {
                return Task.delay(new Random().nextInt(50)).thenSync(new Function<Void, Integer>() {
                    @Override
                    public Integer call(Void aVoid) throws Exception {
                        // --> is the 'goes to' operator!
                        if(remainingThrows.value --> 0){
                            throw exceptionFactory.call();
                        }
                        return 42;
                    }
                });
            }
        };
    }

    @Test
    public void testRun() throws Exception{
        RetryPolicy retryPolicy = RetryPolicy.create(2, 66, NumberFormatException.class);
        final Ref<Integer> remainingThrows = new Ref<>(2);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Integer result = retryPolicy.run(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                if (remainingThrows.value-- > 0) {
                    throw new NumberFormatException();
                }
                return 42;
            }
        });
        stopWatch.stop();
        assertTrue(stopWatch.getTime() >= 2 * 66);
    }

    @Test
    public void testRunAsync() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.create(2, 50, NumberFormatException.class);
        Callable<Exception> numberFormatExceptionFactory = new Callable<Exception>() {

            @Override
            public Exception call() throws Exception {
                return new NumberFormatException();
            }
        };
        Callable<Exception> illegalStateExceptionFactory = new Callable<Exception>() {

            @Override
            public Exception call() throws Exception {
                return new IllegalStateException();
            }
        };

        Task<Integer> t1 = retryPolicy.runAsync(createFlakyTaskFactory(1, numberFormatExceptionFactory));
        t1.waitForCompletion();
        assertEquals(Task.State.Succeeded, t1.getState());

        Task<Integer> t2 = retryPolicy.runAsync(createFlakyTaskFactory(2, numberFormatExceptionFactory));
        t2.waitForCompletion();
        assertEquals(Task.State.Succeeded, t2.getState());

        Task<Integer> t3 = retryPolicy.runAsync(createFlakyTaskFactory(3, numberFormatExceptionFactory));
        t3.waitForCompletion();
        assertEquals(Task.State.Failed, t3.getState());
        assertTrue(t3.getException() instanceof NumberFormatException);

        Task<Integer> t4 = retryPolicy.runAsync(createFlakyTaskFactory(1, illegalStateExceptionFactory));
        t4.waitForCompletion();
        assertEquals(Task.State.Failed, t4.getState());
        assertTrue(t4.getException() instanceof IllegalStateException);

    }

    @Test
    public void testCombine() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.create(2, 50, NumberFormatException.class).combine(RetryPolicy.create(4, 30, TypeNotPresentException.class));
        final Exception[] exceptions = {
                new TypeNotPresentException("Object!", new Exception()),
                new NumberFormatException(),
                new TypeNotPresentException("Object!", new Exception()),
                new TypeNotPresentException("Object!", new Exception()),
                new NumberFormatException(),
                new TypeNotPresentException("Object!", new Exception()),
        };
        final Ref<Integer> exceptionCounter = new Ref<>(0);
        Callable<Exception> exceptionFactory = new Callable<Exception>() {

            @Override
            public Exception call() throws Exception {
                return exceptions[exceptionCounter.value++];
            }
        };

        Task<Integer> t1 = retryPolicy.runAsync(createFlakyTaskFactory(5, exceptionFactory));
        t1.waitForCompletion();
        assertEquals(Task.State.Succeeded, t1.getState());
        exceptionCounter.value = 0;

        Task<Integer> t2 = retryPolicy.runAsync(createFlakyTaskFactory(6, exceptionFactory));
        t2.waitForCompletion();
        assertEquals(Task.State.Succeeded, t2.getState());
        exceptionCounter.value = 0;

        Task<Integer> t3 = retryPolicy.runAsync(createFlakyTaskFactory(7, exceptionFactory));
        t3.waitForCompletion();
        assertEquals(Task.State.Failed, t3.getState());
        exceptionCounter.value = 0;

    }
}