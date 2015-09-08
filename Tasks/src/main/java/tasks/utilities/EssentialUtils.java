package tasks.utilities;

public class EssentialUtils {
    /**
     * if o is of the given type, returns o cast to it; otherwise returns null.
     */
    public static <T extends U,U> T as(Class<T> type, U o){
        if(type.isInstance(o)){
            return (T) o;
        }
        return null;
    }
}
