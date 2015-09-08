package tasks.rx;
import rx.functions.*;
import tasks.Action;

/**
 * Created by Arash on 8/11/2015.
 */
public class Utils {
    public static <T> Action0 toAction0(final Action<T> action, final T value) {
        return new Action0() {
            @Override
            public void call() {
                try {
                    action.call(value);
                }catch (Exception ex){
                    throw tasks.internal.Utils.getRuntimeException(ex);
                }
            }
        };
    }
}
