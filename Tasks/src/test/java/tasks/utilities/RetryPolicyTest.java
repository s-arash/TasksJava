package tasks.utilities;

import junit.framework.Assert;
import junit.framework.TestCase;

import net.denavas.tasks.Function;
import net.denavas.tasks.Ref;
import net.denavas.tasks.Task;

import org.apache.commons.lang3.time.StopWatch;

import java.util.Random;
import java.util.concurrent.Callable;

public class RetryPolicyTest extends TestCase {

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
    public void testRun() throws Exception{
        RetryPolicy retryPolicy = RetryPolicy.create(NumberFormatException.class, 2, 66);
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
        Assert.assertTrue( stopWatch.getTime() >= 2 * 66);
    }
    public void testRunAsync() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.create(NumberFormatException.class, 2, 50);
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
        Assert.assertEquals(Task.State.CompletedSuccessfully,t1.getState());

        Task<Integer> t2 = retryPolicy.runAsync(createFlakyTaskFactory(2, numberFormatExceptionFactory));
        t2.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedSuccessfully,t2.getState());

        Task<Integer> t3 = retryPolicy.runAsync(createFlakyTaskFactory(3, numberFormatExceptionFactory));
        t3.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError,t3.getState());
        Assert.assertTrue(t3.getException() instanceof NumberFormatException);

        Task<Integer> t4 = retryPolicy.runAsync(createFlakyTaskFactory(1, illegalStateExceptionFactory));
        t4.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError,t4.getState());
        Assert.assertTrue(t4.getException() instanceof IllegalStateException);

    }

    public void testCombine() throws Exception {
        RetryPolicy retryPolicy = RetryPolicy.create(NumberFormatException.class, 2, 50).combine(RetryPolicy.create(TypeNotPresentException.class,4,30));
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
        Assert.assertEquals(Task.State.CompletedSuccessfully, t1.getState());
        exceptionCounter.value = 0;

        Task<Integer> t2 = retryPolicy.runAsync(createFlakyTaskFactory(6, exceptionFactory));
        t2.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedSuccessfully, t2.getState());
        exceptionCounter.value = 0;

        Task<Integer> t3 = retryPolicy.runAsync(createFlakyTaskFactory(7, exceptionFactory));
        t3.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError, t3.getState());
        exceptionCounter.value = 0;

    }
}