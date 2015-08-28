package tasks;

/**
 * Created by Arash on 2/17/2015.
 */
public interface Action<T> {
    void call(T t) throws Exception;
}
