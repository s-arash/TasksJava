package tasks;


public interface Function2<T1, T2, TOut> {
    TOut call(T1 t1, T2 t2) throws Exception;
}
