package tasks.experimental.synchronization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import tasks.Action;
import tasks.Function;
import tasks.Task;
import tasks.TaskBuilder;
import tasks.TaskUtils;

/**
 * Provides an asynchronous synchronization mechanism, similar to Monitors.
 * Designed to be used with {@link Task}s.
 * the async equivalent of the following code:
 *  <pre>{@code
 *  Object lockObj = new Object();
 *  ...
 *  synchronized(lockObj){
 *      addItem(x);
 *  }}</pre>
 *  would be something similar to this:
 *  <pre>{@code
 *  AsyncLock asyncLock = new AsyncLock();
 *  ...
 *  asyncLock.protect( () ->
 *      addItemAsync(x));
 *  }</pre>
 *  where {@code addItemAsync()} returns a {@link Task} object.
 */
public class AsyncLock {

    private final Queue<TaskBuilder<LockHolder>> waiters = new ArrayDeque<>();
    private volatile boolean isLockHeld = false;
    private volatile int currentLockId = 0;

    /**
     * acquires the lock asynchronously. the returned {@link LockHolder} object's {@link LockHolder#release()} method MUST be called
     * at some point after the lock has been acquired to release the lock.
     * PLEASE consider using {@link AsyncLock#protect(Callable)} instead, to ensure that the acquired lock will be released.
     * @return a Task that when done, means the lock has been acquired. The Task will contain a LockHolder
     * object, whose {@link LockHolder#release()} method must be called to release the lock.
     */
    public Task<LockHolder> enter(){
        synchronized (waiters) {
            if (isLockHeld) {
                TaskBuilder<LockHolder> waitTB = new TaskBuilder<LockHolder>();

                waiters.add(waitTB);

                return waitTB.getTask();
            } else {
                return Task.fromResult(holdLock());
            }
        }
    }

    private LockHolder holdLock(){
        isLockHeld = true;
        return new LockHolder(currentLockId);
    }

    /**
     * an object representing the lock held. MAKE SURE to call {@link LockHolder#release()} at some point.
     */
    public class LockHolder{
        private final int lockId;

        public LockHolder(int lockId) {this.lockId = lockId;}

        /**
         * releases the asynchronously acquired lock.
         */
        public void release(){
            synchronized (waiters) {
                if(lockId != AsyncLock.this.currentLockId){
                    return;
                }
                AsyncLock.this.currentLockId ++;
                TaskBuilder<LockHolder> holderTaskBuilder = waiters.poll();
                if (holderTaskBuilder != null) {
                    holderTaskBuilder.setResult(holdLock());
                }else{
                    isLockHeld = false;
                }
            }
        }
    }

    public <T> Task<T> protect(final Callable<Task<T>> taskFactory){
        return enter().then(new Function<LockHolder, Task<T>>() {
            @Override
            public Task<T> call(final LockHolder lockHolder) throws Exception {
                return TaskUtils.fromFactory(taskFactory).tryFinallySync(new Action<Void>() {
                    @Override
                    public void call(Void __) throws Exception {
                        lockHolder.release();
                    }
                });
            }
        });

    }


}
