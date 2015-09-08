package tasks.utilities;

import org.junit.Assert;
import org.junit.Test;
import tasks.Ref;
import tasks.Task;


import java.util.concurrent.Callable;

public class TaskCacheTest {

    @Test
    public void testTaskCache() throws Exception {
        final Ref<Integer> count = new Ref<Integer>(0);
        TaskCache<Integer> taskCache = new TaskCache<>(new Callable<Task<Integer>>() {
            @Override
            public Task<Integer> call() throws Exception {
                return Task.run(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Thread.sleep(5);
                        return count.value++;
                    }
                });
            }
        }, 25, 0);

        Task<Integer> t1 = taskCache.get();
        Task<Integer> t2 = taskCache.get();
        Assert.assertSame(t1, t2);

        t1.result();
        Task<Integer> t3 = taskCache.get();

        Assert.assertSame(t1,t3);

        Thread.sleep(55);
        Task<Integer> t4 = taskCache.get();
        Assert.assertNotSame(t1,t4);
        Assert.assertEquals((Integer)1,t4.result());
    }
    @Test
    public void testTaskCacheWithNoResultCache() throws Exception {
        final Ref<Integer> count = new Ref<Integer>(0);
        TaskCache<Integer> taskCache = new TaskCache<>(new Callable<Task<Integer>>() {
            @Override
            public Task<Integer> call() throws Exception {
                return Task.run(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Thread.sleep(5);
                        return count.value++;
                    }
                });
            }
        }, 0, 0);

        Task<Integer> t1 = taskCache.get();
        Task<Integer> t2 = taskCache.get();
        Assert.assertSame(t1,t2);

        t1.result();
        Task<Integer> t3 = taskCache.get();
        Assert.assertNotSame(t1,t3);
        Assert.assertEquals((Integer)1,t3.result());
    }
}