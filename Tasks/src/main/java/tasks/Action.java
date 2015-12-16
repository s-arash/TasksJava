package tasks;

/**
 * a functional interface that represents a parameterized action.
 */
public interface Action<T> {
    void call(T t) throws Exception;
}
