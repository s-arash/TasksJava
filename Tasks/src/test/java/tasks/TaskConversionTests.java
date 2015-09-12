package tasks;


import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * Created by sahebolamri on 7/5/2015.
 */
public class TaskConversionTests  {

    @Test
    public void testConvertFromFuture() throws Exception {
        FutureTask<Integer> failingFutureTask = new FutureTask<Integer>(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(100);
                throw new UnsupportedOperationException("Test Exception");
            }
        });
        FutureTask<Integer> succeedingFutureTask = new FutureTask<Integer>(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(10);
                return 42;
            }
        });
        failingFutureTask.run();
        Task<Integer> taskFromFailingFuture = Task.fromFuture(failingFutureTask);
        taskFromFailingFuture.waitForCompletion();
        Assert.assertEquals(Task.State.Failed, taskFromFailingFuture.getState());
        Assert.assertTrue( taskFromFailingFuture.getException() instanceof UnsupportedOperationException);

        Task<Integer> task2FromFailingFuture = Task.fromFuture(failingFutureTask);
        Assert.assertEquals(Task.State.Failed, task2FromFailingFuture.getState());
        Assert.assertTrue(task2FromFailingFuture.getException() instanceof UnsupportedOperationException);

        succeedingFutureTask.run();
        Task<Integer> taskFromSucceedingFuture = Task.fromFuture(succeedingFutureTask);
        taskFromSucceedingFuture.waitForCompletion();
        Assert.assertEquals(Task.State.Succeeded, taskFromSucceedingFuture.getState());
        Assert.assertEquals((Integer) 42, taskFromSucceedingFuture.result());

    }
    @Test
    public void testConvertToFuture() throws Exception {
        Task<Integer> task = Task.delay(100).thenSync(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                return 42;
            }
        });
        Future<Integer> future = task.toFuture();
        Assert.assertEquals((Integer) 42, future.get());


        Task<Integer> faultingTask = Task.delay(10).thenSync(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                throw new IllegalAccessException("what?");
            }
        });
        Future<Integer> faultingFuture = faultingTask.toFuture();
        try {
            faultingFuture.get();
            Assert.fail("should have thrown");
        } catch (ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IllegalAccessException);
        }

        Task<Integer> longRunningTask = Task.delay(10000).thenSync(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                return 666;
            }
        });
        Future<Integer> longRunningFuture = longRunningTask.toFuture();
        try {
            longRunningFuture.get(50, TimeUnit.MILLISECONDS);
            Assert.fail("should have thrown");
        } catch (TimeoutException ex) {
        }
    }
}
