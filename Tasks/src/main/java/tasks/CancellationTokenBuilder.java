package tasks;

/**
 * Created by sahebolamri on 12/9/2015.
 */
public class CancellationTokenBuilder {
    TaskBuilder<Void> cancellationTaskBuilder = new TaskBuilder<>();


    CancellationToken cancellationToken = new CancellationToken() {
        @Override
        public boolean isCanceled() {
            return cancellationTaskBuilder.getTask().isDone();
        }

        @Override
        public void registerCanceledCallback(final Action<CancellationToken> callback) {
            cancellationTaskBuilder.getTask().thenSync(new Action<Void>() {
                @Override
                public void call(Void __) throws Exception {
                    callback.call(cancellationToken);
                }
            });
        }
    };

    public void cancel() {
        if(isCanceled()) return;
        synchronized (cancellationTaskBuilder) {
            if (!isCanceled())
                cancellationTaskBuilder.setResult(null);
        }
    }

    public CancellationToken get() {
        return cancellationToken;
    }

    public boolean isCanceled() {
        return cancellationToken.isCanceled();
    }

}
