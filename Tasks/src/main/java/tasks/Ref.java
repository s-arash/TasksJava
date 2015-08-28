package tasks;

/**
 * Created by Arash on 2/23/2015.
 */

/**
 * wraps a value of type T. useful when shared state is needed while working with closures in java
 */
public class Ref<T> {
    public T value;

    public Ref() { }

    public Ref(T value) {
        this.value = value;
    }
}
