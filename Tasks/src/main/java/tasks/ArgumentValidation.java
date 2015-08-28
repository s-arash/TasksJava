package tasks;

/**
 * Created by sahebolamri on 7/6/2015.
 */
public class ArgumentValidation {
    public static <T> T notNull(T value, String message){
        if(value == null){
            throw new NullPointerException(message);
        }
        return value;
    }
    public static <T> T notNull(T value){
        if(value == null){
            throw new NullPointerException();
        }
        return value;
    }

    public static <T> T[] elementsNotNull(T[] array, String message){
        for(T x : array){
            if (x == null) throw new NullPointerException(message);
        }
        return array;
    }
}
