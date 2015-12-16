package tasks;

/**
 * Created by sahebolamri on 12/9/2015.
 */
public class OperationCanceledException extends RuntimeException {
    public OperationCanceledException() {
    }

    public OperationCanceledException(String message) {
        super(message);
    }
}
