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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    public void testAsyncLock() throws Exception {

        final int bizillion = 1000000;
        final AsyncLock asyncLock = new AsyncLock();
        final Ref<Integer> counter = new Ref<>(0);
        final CountDownLatch countDownLatch = new CountDownLatch(bizillion);

        parallelFor(range(0, bizillion), new Action<Integer>() {
            @Override
            public void call(Integer integer) throws Exception {
                asyncLock.enter().thenSync(new Action<AsyncLock.LockHolder>() {
                    @Override
                    public void call(AsyncLock.LockHolder lockHolder) throws Exception {
                        counter.value++;
                        lockHolder.release();
                        countDownLatch.countDown();
                    }
                });
                //taskFactory.call();
            }
        });

        countDownLatch.await();
        //Thread.sleep(5000);
        assertEquals((Integer) bizillion, counter.value);
    }

    @Test
    public void testAsyncLockMultipleTimes() throws Exception {
        for(int i =0; i <3; i ++) {
            Logger.getGlobal().log(Level.INFO, "test #" + i);
            testAsyncLock();
        }
    }

    Executor immediateExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    public static List<Integer> range(final int from, final int count) {
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

    //private static ExecutorService threadPool = Executors.newCachedThreadPool();
    //private static ExecutorService threadPool = new ForkJoinPool(20000);
    public static <T> void parallelFor(int dop, List<T> seq, final Action<T> body) throws Exception {
        final ExecutorService threadPool =new ThreadPoolExecutor(0,dop,60, TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>() );
        final CountDownLatch countDownLatch = new CountDownLatch(seq.size());
        final List<Exception> thrownExceptions = new ArrayList<>();
        for (final T item : seq) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        body.call(item);
                    } catch (Exception ex) {
                        thrownExceptions.add(ex);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }
        countDownLatch.await();

        if (thrownExceptions.size() > 0) {
            throw thrownExceptions.get(0);
        }
    }
    public static <T> void parallelFor(List<T> seq, Action<T> body) throws Exception {
        parallelFor(100,seq,body);
    }
}