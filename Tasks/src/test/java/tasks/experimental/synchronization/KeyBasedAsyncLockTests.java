package tasks.experimental.synchronization;

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import tasks.Action;
import tasks.Ref;
import tasks.Task;

import static org.junit.Assert.assertEquals;
import static tasks.experimental.synchronization.AsyncLockTests.parallelFor;
import static tasks.experimental.synchronization.AsyncLockTests.range;

/**
 * Created by sahebolamri on 1/2/2016.
 */
public class KeyBasedAsyncLockTests {


    @Test
    public void testKeyBasedAsyncLock() throws Exception {

        final int bizillion = 100000, chunkCount = 10;
        final KeyBasedAsyncLock<Integer> keyBasedAsyncLock = new KeyBasedAsyncLock<>();
        final int[] chunks = new int[chunkCount];

        final CountDownLatch countDownLatch = new CountDownLatch(bizillion * chunkCount);

        parallelFor(range(0, bizillion * chunkCount), new Action<Integer>() {
            @Override
            public void call(final Integer integer) throws Exception {

                keyBasedAsyncLock.enter(integer % chunkCount).thenSync(new Action<KeyBasedAsyncLock<java.lang.Integer>.LockHolder>() {
                    @Override
                    public void call(KeyBasedAsyncLock<Integer>.LockHolder lockHolder) throws Exception {
                        chunks[integer % chunkCount]++;
                        lockHolder.release();
                        countDownLatch.countDown();
                    }
                });
            }
        });

        countDownLatch.await();
        for (int chunk : chunks) {
            assertEquals(bizillion, chunk);
        }
    }

    @Test
    public void testKeyBasedAsyncLock2() throws Exception {

        final int bizillion = 100000, chunkCount = 2;
        final KeyBasedAsyncLock<Integer> keyBasedAsyncLock = new KeyBasedAsyncLock<>();
        final int[] chunks = new int[chunkCount];
        final Random r = new Random();
        final CountDownLatch countDownLatch = new CountDownLatch(bizillion * chunkCount);

        parallelFor(10, range(0, bizillion * chunkCount), new Action<Integer>() {
            @Override
            public void call(final Integer integer) throws Exception {
                if (integer > 1000 && integer < 1200)
                    if (r.nextFloat() < 0.9) {
                        Thread.sleep(r.nextInt(10));
                    }
                keyBasedAsyncLock.enter(integer % chunkCount).thenSync(new Action<KeyBasedAsyncLock<java.lang.Integer>.LockHolder>() {
                    @Override
                    public void call(KeyBasedAsyncLock<Integer>.LockHolder lockHolder) throws Exception {
                        chunks[integer % chunkCount]++;
                        lockHolder.release();
                        countDownLatch.countDown();
                    }
                });
            }
        });

        countDownLatch.await();
        for (int chunk : chunks) {
            assertEquals(bizillion, chunk);
        }
    }
}
