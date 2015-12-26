package tasks.experimental.synchronization;

import java.util.concurrent.Executor;

import tasks.Function;
import tasks.Task;
import tasks.TaskBuilder;

/**
 * Created by sahebolamri on 12/26/2015.
 */
public class AsyncLock {

    private TaskBuilder<Void> currentLockTB = null;
    private volatile int currentLockId = 0;
    private final Object internalSyncObj = new Object();

    public Task<LockHolder> enter() {
        TaskBuilder<Void> currentLockTBLocal = this.currentLockTB;
        if (currentLockTBLocal != null) {
            return currentLockTBLocal.getTask().then(new Function<Void, Task<LockHolder>>() {
                @Override
                public Task<LockHolder> call(Void aVoid) throws Exception {
                    return enter();
                }
            });
        } else {
            synchronized (internalSyncObj) {
                currentLockTBLocal = currentLockTB;
                if (currentLockTBLocal == null) {
                    this.currentLockTB = new TaskBuilder<>(immediateExecutor);
                    return Task.fromResult(new LockHolder(currentLockId));
                }
            }
            return currentLockTBLocal.getTask().then(new Function<Void, Task<LockHolder>>() {
                @Override
                public Task<LockHolder> call(Void aVoid) throws Exception {
                    return enter();
                }
            });
        }
    }


    public class LockHolder {
        private final int lockId;

        public LockHolder(int lockId) {this.lockId = lockId;}

        public void release() {
            TaskBuilder<Void> currentLock = null;
            synchronized (AsyncLock.this.internalSyncObj) {
                // if they are not equal, it means the lock has already been released, so we do nothing
                if (this.lockId == AsyncLock.this.currentLockId) {
                    AsyncLock.this.currentLockId++;
                    currentLock = AsyncLock.this.currentLockTB;
                    AsyncLock.this.currentLockTB = null;
                }
            }
            if (currentLock != null)
                currentLock.setResult(null);
        }


    }

    private static final Executor immediateExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };
}
