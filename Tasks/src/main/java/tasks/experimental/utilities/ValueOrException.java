package tasks.experimental.utilities;

public class ValueOrException<T> {

    final private T mValue;
    final private Exception mException;
    final private boolean mHasValue;

    public ValueOrException(T value, Exception exception) {
        if(exception != null && value != null){
            throw new IllegalArgumentException("value and exception can't both be not-null");
        }
        if(exception != null){
            this.mException = exception;
            this.mValue = null;
            this.mHasValue = false;
        }
        else{
            this.mException = null;
            this.mValue = value;
            this.mHasValue = true;
        }
    }

    public static <T> ValueOrException<T> createValue(T value){
        return new ValueOrException<>(value, null);
    }
    public static <T> ValueOrException<T> createException(Exception exception){
        return new ValueOrException<>(null, exception);
    }

    public boolean hasValue() {
        return mHasValue;
    }

    public boolean hasException() {
        return !mHasValue;
    }

    public T getValue() {
        if(!mHasValue){
            throw new UnsupportedOperationException("the ValueOrException has an Exception");
        }
        return mValue;
    }

    public T getValueOrThrow() throws Exception{
        if(!mHasValue){
            throw mException;
        }
        return mValue;
    }

    public Exception getException() {
        if(mHasValue){
            throw new UnsupportedOperationException("the ValueOrException has a value");
        }
        return mException;
    }


}
