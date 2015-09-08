package tasks.rx;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import tasks.*;

import static tasks.ArgumentValidation.notNull;

/**
* This class contains helper methods for converting Tasks to Observables and vice versa
*/
public class TaskRx {
    /**
     * creates a Task that subscribes to the given observable, and completes on the first item emitted by the Observable
     */
    public static <T> Task<T> fromObservable(Observable<T> observable){
        ArgumentValidation.notNull(observable, "observable cannot be null");

        final TaskManualCompletion<T> tmc = new TaskManualCompletion<>();
        final Ref<Boolean> done = new Ref<>(false);

        final Ref<Subscription> subscription = new Ref<>();
        subscription.value = observable.subscribe(new Observer<T>() {
            @Override
            public void onCompleted() {
                if(!done.value){
                    tmc.setException(new Exception("Empty observable source"));
                    done.value = true;
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (!done.value) {
                    Exception ex= throwable instanceof Exception ? (Exception) throwable : new Exception(throwable.getMessage(),throwable);
                    tmc.setException(ex);
                    done.value = true;
                }
                if(subscription.value != null && !subscription.value.isUnsubscribed())
                    subscription.value.unsubscribe();
            }

            @Override
            public void onNext(T t) {
                if (!done.value) {
                    tmc.setResult(t);
                    done.value = true;
                }
                if(subscription.value != null && !subscription.value.isUnsubscribed())
                    subscription.value.unsubscribe();
            }
        });
        return tmc.getTask();
    }

    /**
     * creates an Observable that emits the single value returned by the given task.
     */
    public static <T> Observable<T> toObservable(final Task<T> task) {
        ArgumentValidation.notNull(task, "task cannot be null");

        return Observable.create(new Observable.OnSubscribe<T>(){
            @Override
            public void call(final Subscriber<? super T> subscriber) {
                if(!subscriber.isUnsubscribed()) {
                    task.registerCompletionCallback(new Action<Task<T>>() {
                        @Override
                        public void call(Task<T> arg) throws Exception {
                            if (arg.getState() == Task.State.CompletedInError) {
                                subscriber.onError(arg.getException());
                            } else {
                                subscriber.onNext(arg.result());
                                subscriber.onCompleted();
                            }
                        }
                    });
                }
            }
        });
    }
}
