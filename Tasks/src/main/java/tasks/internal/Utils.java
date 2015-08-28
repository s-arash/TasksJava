package tasks.internal;

/**
 * Created by Arash on 8/28/2015.
 */
public class Utils {
    /**
     * wraps the Throwable in a RuntimeException if necessary
     */
    public static RuntimeException getRuntimeException(Throwable error){
        if(error instanceof RuntimeException){
            return (RuntimeException)error;
        }
        else{
            return new RuntimeException(error.getMessage(),error);
        }
    }
}
