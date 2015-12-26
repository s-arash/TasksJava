package tasks.experimental.utilities;


import org.junit.Assert;
import org.junit.Test;
import tasks.Ref;
import tasks.Task;

import java.util.concurrent.Callable;

public class TaskGuardTest{
    @Test
    public void testTaskGuard() throws Exception {
        final Ref<Integer> callCount = new Ref<>(0);
        Callable<Task<Integer>> taskFactory = new Callable<Task<Integer>>() {

            @Override
            public Task<Integer> call() throws Exception {
                return Task.run(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Thread.sleep(40);
                        callCount.value ++;
                        return 42;
                    }
                });
            }
        };
        TaskGuard<Integer> taskGuard = new TaskGuard<>(taskFactory, false);
        Task<Integer> t1 = taskGuard.get();
        Task<Integer> t2 = taskGuard.get();
        Assert.assertSame(t1, t2);
        Assert.assertEquals((Integer)42,t1.result());
        Task<Integer> t3 = taskGuard.get();
        Assert.assertNotSame(t1,t3);
        Assert.assertEquals((Integer)42,t3.result());
    }
}