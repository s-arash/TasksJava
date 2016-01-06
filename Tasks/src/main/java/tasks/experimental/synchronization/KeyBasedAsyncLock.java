package tasks.experimental.synchronization;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import tasks.Function;
import tasks.Task;

/**
 * Created by sahebolamri on 1/2/2016.
 */
public class KeyBasedAsyncLock<T> {
    private static class Stuff{
        public final AsyncLock asyncLock;
        //we keep the count of people trying to get the lock.
        //if it reaches 0, we let go of this object, to avoid stacking up unused objects and
        //potential memory leak
        public int waitQueue;

        public Stuff(AsyncLock asyncLock) {
            this.asyncLock = asyncLock;
        }
    }
    private final java.util.concurrent.ConcurrentHashMap<T,Stuff> locks = new ConcurrentHashMap<>();
    public Task<LockHolder> enter(final T key){

        Stuff newStuff = new Stuff(new AsyncLock());
        Stuff existingStuff = locks.putIfAbsent(key, newStuff);
        final Stuff lockStuff = existingStuff != null ? existingStuff : newStuff;

        synchronized (lockStuff) {
            if(lockStuff.waitQueue < 0){ //the lock is no longer valid, retry
                return enter(key);
            }else {
                lockStuff.waitQueue++;
                return lockStuff.asyncLock.enter().map(new Function<AsyncLock.LockHolder, LockHolder>() {
                    @Override
                    public LockHolder call(AsyncLock.LockHolder lockHolder) throws Exception {
                        return new LockHolder(key, lockStuff, lockHolder);
                    }
                });
            }
        }
    }

    public class LockHolder{
        private final AsyncLock.LockHolder underlyingLockHolder;
        private final T key;
        private final Stuff lockStuff;

        public LockHolder(T key, Stuff lockStuff, AsyncLock.LockHolder underlyingLockHolder) {
            this.underlyingLockHolder = underlyingLockHolder;
            this.key = key;
            this.lockStuff = lockStuff;
        }

        public void release(){
            synchronized (lockStuff){
                if(--lockStuff.waitQueue <= 0) {
                    locks.remove(key, lockStuff);
                    lockStuff.waitQueue = -1; //signals that this lock is gone
                }

            }
            underlyingLockHolder.release();

        }
    }

    private static Executor immediateExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };
}
