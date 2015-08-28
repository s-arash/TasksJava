package tasks;


public interface Function<TIn,TOut> {
    TOut call(TIn tIn) throws Exception;
}
