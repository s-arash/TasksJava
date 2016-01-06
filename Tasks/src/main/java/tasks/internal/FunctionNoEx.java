package tasks.internal;

import tasks.Function;

/**
 * Created by sahebolamri on 1/4/2016.
 */
public interface FunctionNoEx<TIn,TOut> extends Function<TIn,TOut> {
    TOut call(TIn tIn);
}
