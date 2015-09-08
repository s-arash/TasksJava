package tasks.utilities;

import junit.framework.Assert;
import junit.framework.TestCase;

import net.denavas.tasks.Ref;
import net.denavas.tasks.Task;

import java.util.EnumSet;
import java.util.concurrent.Callable;

public class TaskGuardTest extends TestCase {

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
        Assert.assertSame(t1,t2);
        Assert.assertEquals((Integer)42,t1.result());
        Task<Integer> t3 = taskGuard.get();
        Assert.assertNotSame(t1,t3);
        Assert.assertEquals((Integer)42,t1.result());
    }
}