package tasks.experimental.synchronization;

import org.junit.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import tasks.Action;
import tasks.Ref;
import tasks.Task;

import static org.junit.Assert.*;

/**
 * Created by sahebolamri on 12/26/2015.
 */
public class AsyncLockTests {

    @Test
    public void testEnter() throws Exception {

        final int bizillion = 1000000;
        final AsyncLock asyncLock = new AsyncLock();
        final Ref<Integer> counter = new Ref<>(0);

        parallelFor(range(0, bizillion), new Action<Integer>() {
            @Override
            public void call(Integer integer) throws Exception {
                Callable<Task<Void>> taskFactory = new Callable<Task<Void>>() {
                    @Override
                    public Task<Void> call() throws Exception {
                        counter.value++;
                        return Task.fromResult(null);
                    }
                };
                asyncLock.enter().continueOn(immediateExecutor).thenSync(new Action<AsyncLock.LockHolder>() {
                    @Override
                    public void call(AsyncLock.LockHolder lockHolder) throws Exception {
                        counter.value++;
                        lockHolder.release();
                    }
                }).result();
                //taskFactory.call();
            }
        });

        //Thread.sleep(5000);
        assertEquals((Integer)bizillion, counter.value);
    }

    Executor immediateExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    public static List<Integer> range(final int from, final int count){
        return new AbstractList<Integer>() {
            @Override
            public Integer get(int index) {
                return from + index;
            }

            @Override
            public int size() {
                return count;
            }
        };
    }
    public static <T> void parallelFor(List<T> seq, final Action<T> body) throws Exception {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        final CountDownLatch countDownLatch = new CountDownLatch(seq.size());
        final List<Exception> thrownExceptions = new ArrayList<>();
        for (final T item : seq) {
            threadPool.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    try {
                        body.call(item);
                    } catch (Exception ex) {
                        thrownExceptions.add(ex);
                    } finally {
                        countDownLatch.countDown();
                    }
                    return null;
                }

            });
        }
        countDownLatch.await();

        if (thrownExceptions.size() > 0){
            throw thrownExceptions.get(0);
        }
    }
}