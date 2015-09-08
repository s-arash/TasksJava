package tasks.rx;

import org.junit.Test;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;
import tasks.Function;
import tasks.Task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by Arash on 8/28/2015.
 */
public class TaskRxTests {

    private static <T> List<T> iteratorToCollection(Iterator<? extends T> iterator) {
        ArrayList<T> res = new ArrayList<>();
        while (iterator.hasNext()) {
            res.add(iterator.next());
        }
        return res;
    }

    @Test(timeout = 100)
    public void testFromObservable() throws Exception {
        Observable<Integer> observable = Observable.just(0L).concatWith(Observable.interval(100, TimeUnit.HOURS)).flatMap(new Func1<Long, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Long aLong) {
                return Observable.just(42);
            }
        });

        assertEquals((Integer) 42, TaskRx.fromObservable(observable).result());

        Observable<Integer> multiValueObservable = Observable.just(0L).concatWith(Observable.interval(100, TimeUnit.HOURS)).flatMap(new Func1<Long, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Long aLong) {
                return Observable.just(142, 143, 144);
            }
        });

        assertEquals((Integer) 142, TaskRx.fromObservable(multiValueObservable).result());

        Observable<Object> failingObservable = Observable.interval(3, TimeUnit.MILLISECONDS).flatMap(new Func1<Long, Observable<?>>() {
            @Override
            public Observable<?> call(Long aLong) {
                return Observable.error(new Exception("HI!"));
            }
        });

        Task<Object> taskFromFailingObservable = TaskRx.fromObservable(failingObservable);
        taskFromFailingObservable.waitForCompletion();
        assertTrue(taskFromFailingObservable.getState() == Task.State.CompletedInError);
        assertTrue(taskFromFailingObservable.getException().getMessage().equals("HI!"));
    }

    @Test
    public void testToObservable() throws Exception {
        Task<Integer> task = Task.delay(10).map(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                return 42;
            }
        });
        Observable<Integer> observable = TaskRx.toObservable(task);

        observable.subscribe();
        List<Integer> list = iteratorToCollection(observable.toBlocking().getIterator());
        assertEquals(1, list.size());
        assertEquals((Integer) 42, list.get(0));

        Task<Object> failingTask = Task.delay(5).thenSync(new Function<Void, Object>() {
            @Override
            public Object call(Void aVoid) throws Exception {
                throw new Exception("HEY!");
            }
        });

        Observable<Object> observableFromFailingTask = TaskRx.toObservable(failingTask);
        List<Notification<Object>> failingObservableNotifications = iteratorToCollection(observableFromFailingTask.materialize().toBlocking().getIterator());
        assertEquals(1,failingObservableNotifications.size());
        assertTrue(failingObservableNotifications.get(0).hasThrowable() &&
            failingObservableNotifications.get(0).getThrowable().getMessage().equals("HEY!"));
    }
}