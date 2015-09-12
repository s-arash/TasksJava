package tasks.experimental.utilities;

import java.util.concurrent.Callable;

public class BasicUtilities {
    /**
     * if o is of the given type, returns o cast to it; otherwise returns null.
     */
    public static <T extends U,U> T as(Class<T> type, U o){
        if(type.isInstance(o)){
            return (T) o;
        }
        return null;
    }

    /**
     * returns the value provided by valueProvider if it succeeds, if valueProvider fails with an {@link Exception} (and not a {@link Throwable}), fallbackValue is returned
     */
    public static <T> T tryOr(Callable<T> valueProvider, T fallbackValue){
        try{
            return valueProvider.call();
        }catch (Exception ex){
            return fallbackValue;
        }
    }
    /**
     * returns the value provided by valueProvider if it succeeds, if valueProvider fails with an {@link Exception} (and not a {@link Throwable}), the value provided by fallbackValueProvider is returned
     */
    public static <T> T tryOr(Callable<T> valueProvider, Callable<T> fallbackValueProvider) throws Exception{
        try{
            return valueProvider.call();
        }catch (Exception ex){
            return fallbackValueProvider.call();
        }
    }
}
