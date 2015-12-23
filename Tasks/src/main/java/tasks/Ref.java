package tasks;

/**
 * This class wraps a value of type T; useful when shared state is needed while working with closures in java
 */
public class Ref<T> {
    public volatile T value;

    @Deprecated
    public Ref() { }

    /**
     * sets the given parameter as the initial value
     */
    public Ref(T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("Ref(%s)", String.valueOf(value));
    }
}
