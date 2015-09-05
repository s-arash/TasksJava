package tasks;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * unit tests for the tasks library
 */

public class TaskTests {
    @Test
    public void testDelay() throws Exception {
        long start = System.nanoTime();
        final Ref<Long> end = new Ref<>();
        Task<Void> delayTask = Task.delay(400);
        delayTask.then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                end.value = System.nanoTime();
                return Task.fromResult(null);
            }
        }).result();
        Assert.assertTrue(delayTask.isDone());
        Assert.assertTrue(end.value - start >= 1000000 * 390);
    }

    @Test
    public void testThen() throws Exception {
        Task<Integer> t1 = Task.delay(30).then(new Function<Void, Task<Integer>>() {
            @Override
            public Task<Integer> call(Void arg) throws Exception {
                return Task.fromResult(66);
            }
        });
        Assert.assertEquals(66, (int) t1.result());
    }

    @Test
    public void testWhenAny() throws Exception {
        Task<Void> t1 = Task.delay(100);
        Task<Void> t2 = Task.delay(200);
        Task<Void> t3 = Task.delay(500);
        Task<Void> t4 = Task.delay(5000);

        Task<Task<?>> first = Task.whenAny(t4, t2, t1, t3);

        Assert.assertEquals(t1, first.result());
        Assert.assertEquals(Task.State.CompletedSuccessfully, first.result().getState());
        Assert.assertFalse(t4.isDone());
    }

    @Test
    public void testWhenAnySucceeds() throws Exception {
        Task<Void> t1 = Task.fromException(new Exception("OOPS!"));
        Task<Void> t2 = Task.delay(30).thenSync(new Action<Void>() {
            @Override
            public void call(Void arg) throws Exception {
                throw new Exception("OOPS! 2.0");
            }
        });
        Task<Void> t3 = Task.delay(30);
        Task<Void> t4 = Task.delay(2000);
        Task<Void> t5 = Task.delay(5000);

        Task<Task<?>> first = Task.whenAnySucceeds(t5, t4, t2, t1, t3);

        Assert.assertEquals(t3, first.result());
        Assert.assertEquals(Task.State.CompletedSuccessfully, first.result().getState());
        Assert.assertFalse(t4.isDone());

    }

    @Test
    public void testWhenAnySucceeds2() throws Exception {
        Task<Void> t1 = Task.fromException(new Exception("OOPS!"));
        Task<Void> t2 = Task.delay(30).thenSync(new Action<Void>() {
            @Override
            public void call(Void arg) throws Exception {
                throw new Exception("OOPS! 2.0");
            }
        });
        Task<Void> t3 = Task.delay(30).then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void x) throws Exception {
                return Task.fromException(new Exception("OOPS! 3.0"));
            }
        });

        Task<Task<?>> first = Task.whenAnySucceeds(t2, t1, t3);

        first.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError, first.getState());
        Assert.assertTrue(t2.isDone());
        Assert.assertTrue(t3.isDone());
    }

    @Test
    public void testWhenAnyAndWhenAll() throws Exception {
        Task<Void> tMother = Task.delay(10).then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                return Task.delay(5);
            }
        });
        Task<Void> t1 = tMother.then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                return Task.delay(30);
            }
        });
        Task<Void> t2 = tMother.then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                return Task.delay(100);
            }
        });
        Task<Void> t3 = tMother.then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                return Task.delay(150);
            }
        });
        Task<Void> tBad = tMother.then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void arg) throws Exception {
                return Task.delay(160);
            }
        }).thenSync(new Action<Void>() {
            @Override
            public void call(Void arg) throws Exception {
                throw new Exception("task gone awry!");
            }
        });

        Task<Task<?>> first = Task.whenAny(tBad, t2, t1, t3);

        Assert.assertEquals(t1, first.result());
        Assert.assertEquals(Task.State.CompletedSuccessfully, first.result().getState());
        Assert.assertFalse(tBad.isDone());


        final Task<?>[] whenAllResult = Task.whenAll(t3, t2, t1, tBad).result();
        Assert.assertEquals(Task.State.CompletedInError, whenAllResult[3].getState());

        for (Task<Void> t : new Task[]{t1, t2, t3, tBad}) {
            Assert.assertTrue(t.isDone());
        }
    }

    @Test
    public void testZip() throws Exception {
        Task<Integer> t1 = Task.delay(10).map(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                return 42;
            }
        });
        assertEquals(new Pair<>(42, "hi"), t1.zip(Task.fromResult("hi")).result());

        Task<String> tFail = Task.fromException(new Exception("oh crap!"));
        Task<Pair<Void, String>> zip = Task.delay(50000000).zip(tFail);
        zip.waitForCompletion();
        assertEquals("oh crap!", zip.getException().getMessage());

        assertEquals(new Pair<>(42, "hi"), t1.zip(Task.fromResult("hi")).result());
    }

    @Test
    public void testTryCatch2() throws Exception {
        Task<String> t0 = Task.delay(10).then(new Function<Void, Task<String>>() {
            @Override
            public Task<String> call(Void arg) throws Exception {
                throw new Exception("no!");
            }
        });
        Task<String> t1 = t0.tryCatch(new Function<Exception, Task<String>>() {
            @Override
            public Task<String> call(Exception arg) throws Exception {
                return Task.fromResult("yes!");
            }
        });

        Thread.sleep(30);
        Assert.assertEquals("t0 should be in error state", Task.State.CompletedInError, t0.getState());
        //Assert.assertEquals("t1 should be done", Task.State.CompletedSuccessfully, t1.getState());
        t1.result();
    }

    @Test
    public void testTryCatch() throws Exception {

        TaskManualCompletion<Integer> tmc = new TaskManualCompletion<>();
        tmc.setException(new Exception(":]"));
        Task<Integer> t0 = tmc.getTask().tryCatch(new Function<Exception, Task<Integer>>() {
            @Override
            public Task<Integer> call(Exception arg) throws Exception {
                return Task.fromResult(42);
            }
        });

        Assert.assertEquals(42, ((int) t0.result()));

        Task<Integer> t1 = Task.delay(30).then(new Function<Void, Task<Integer>>() {
            @Override
            public Task<Integer> call(Void arg) throws Exception {
                throw new Exception(":)");
            }
        });
//        Exception thrown= null;
//        try{
//            t1.result();
//        }catch (Exception ex){thrown = ex;}
//        Assert.assertNotNull(thrown);

        Task<Integer> t2 = t1.tryCatch(new Function<Exception, Task<Integer>>() {
            @Override
            public Task<Integer> call(Exception arg) throws Exception {
                return Task.fromResult(42);
            }
        });
        int result = t2.result();

        Assert.assertEquals(42, result);
    }

    @Test
    public void testTryCatch3() throws Exception {
        Task<Integer> t0 = Task.<Integer>fromException(new IllegalArgumentException("yup, somebody threw me"))
            .tryCatch(ArithmeticException.class, new Function<ArithmeticException, Task<Integer>>() {
                @Override
                public Task<Integer> call(ArithmeticException x) throws Exception {
                    return Task.fromResult(666);
                }
            });
        final Task<Integer> t1 = t0.tryCatch(IllegalArgumentException.class, new Function<IllegalArgumentException, Task<Integer>>() {
            @Override
            public Task<Integer> call(IllegalArgumentException x) throws Exception {
                return Task.fromResult(4);
            }
        });
        Task<Integer> t2 = t0.tryCatchSync(IllegalArgumentException.class, new Function<IllegalArgumentException, Integer>() {
            @Override
            public Integer call(IllegalArgumentException x) throws Exception {
                return 5;
            }
        });

        t0.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError, t0.getState());

        Assert.assertEquals(4, (int) t1.result());
        Assert.assertEquals(5, (int) t2.result());
    }

    @Test
    public void testTryFinally() throws Exception {
        final Ref<Boolean> ranFinally = new Ref<>(false);
        Task<Integer> t0 = Task.<Integer>fromException(new IllegalArgumentException("yup, somebody threw me"))
            .tryFinally(new Function<Void, Task<Void>>() {
                @Override
                public Task<Void> call(Void x) throws Exception {
                    ranFinally.value = true;
                    return Task.fromResult(null);
                }
            });
        t0.waitForCompletion();
        Assert.assertEquals(Task.State.CompletedInError, t0.getState());
        Assert.assertTrue(ranFinally.value);


        final Task<Integer> t1 = Task.delay(5).thenSync(new Function<Void, Integer>() {
            @Override
            public Integer call(Void x) throws Exception {
                return 42;
            }
        }).tryFinally(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void x) throws Exception {
                throw new Exception("Finally blew up!");
            }
        });
        t1.waitForCompletion();
        Assert.assertEquals("Finally blew up!", t1.getException().getMessage());
    }

    @Test
    public void testTryWithResource() {
        //test with failing body
        final Ref<Integer> closeCalls = new Ref<>(0);
        Closeable res = new Closeable() {
            @Override
            public void close() {
                closeCalls.value++;
            }
        };
        Task<Integer> task = Task.tryWithResource(res, new Function<Closeable, Task<Integer>>() {
            @Override
            public Task<Integer> call(Closeable closeable) throws Exception {
                throw new Exception("HEHE");
            }
        });
        task.waitForCompletion();
        assertTrue(task.getException().getMessage().equals("HEHE"));
        assertEquals((Integer) 1, closeCalls.value);


        //test with resource that throws in close()
        Closeable badRes = new Closeable() {
            @Override
            public void close() throws IOException {
                throw new IOException("From badRes");
            }
        };
        Task<Integer> taskWithBadRes = Task.tryWithResource(badRes, new Function<Closeable, Task<Integer>>() {
            @Override
            public Task<Integer> call(Closeable closeable) throws Exception {
                return Task.delay(5).thenSync(new Function<Void, Integer>() {
                    @Override
                    public Integer call(Void aVoid) throws Exception {
                        return 42;
                    }
                });
            }
        });
        taskWithBadRes.waitForCompletion();
        assertEquals("From badRes", taskWithBadRes.getException().getMessage());

        //test with failing body and resource that throws in close(). the returned Task's exception must come from body
        Task<Integer> badTaskWithBadRes = Task.tryWithResource(badRes, new Function<Closeable, Task<Integer>>() {
            @Override
            public Task<Integer> call(Closeable closeable) throws Exception {
                return Task.fromException(new Exception("from badTask"));
            }
        });
        badTaskWithBadRes.waitForCompletion();
        assertEquals("from badTask", badTaskWithBadRes.getException().getMessage());
    }

    @Test
    public void testWithTimeout() throws Exception {
        Task<Void> task = Task.delay(10000);
        Task<Void> taskWithTimeOut = task.withTimeout(10);
        taskWithTimeOut.waitForCompletion();
        assertTrue(taskWithTimeOut.getException() instanceof TaskTimeoutException
            && ((TaskTimeoutException) taskWithTimeOut.getException()).getTimedOutTask() == task);

        Task<Integer> task2 = Task.delay(10).thenSync(new Function<Void, Integer>() {
            @Override
            public Integer call(Void aVoid) throws Exception {
                return 42;
            }
        });
        Task<Integer> task2WithTimeOut = task2.withTimeout(20);
        task2WithTimeOut.waitForCompletion();
        assertEquals((Integer)42, task2WithTimeOut.result());
    }

    @Test
    public void testForeachGenerate() throws Exception {
        final List<Integer> input = Arrays.asList(1, 2, 3, 4);
        final Task<List<Integer>> t = Task.forEachGenerate(input, new Function<Integer, Task<Integer>>() {
            @Override
            public Task<Integer> call(final Integer x) throws Exception {
                return Task.delay(10).then(new Function<Void, Task<Integer>>() {
                    @Override
                    public Task<Integer> call(Void _) throws Exception {
                        return Task.fromResult(x * x);
                    }
                });
            }
        });

        Assert.assertEquals(Task.State.NotCompleted, t.getState());
        final List<Integer> result = t.result();
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(input.get(i) * input.get(i), (int) result.get(i));
        }
    }

    @Test
    public void testWhileLoop() throws Exception {

        final Ref<Integer> i = new Ref<>(0);
        final Ref<Integer> res = new Ref<>(0);

        final Task<Void> t =
            Task.whileLoop(new Callable<Boolean>() {
                               @Override
                               public Boolean call() throws Exception {
                                   return i.value <= 10;
                               }
                           },
                new Callable<Task<Void>>() {
                    @Override
                    public Task<Void> call() throws Exception {
                        return Task.delay(5).thenSync(new Action<Void>() {
                            @Override
                            public void call(Void x) throws Exception {
                                res.value += i.value;
                                i.value++;
                            }
                        });
                    }
                });

        t.result();
        Assert.assertEquals(10 * 11 / 2, (int) res.value);
    }

    @Test
    public void testContinueWith() throws Exception {
        Task<Integer> t1 = Task.delay(30).continueWithSync(new Function<Task<Void>, Integer>() {
            @Override
            public Integer call(Task<Void> x) throws Exception {
                if (x.getState() == Task.State.CompletedInError) {
                    throw x.getException();
                }
                return 66;
            }
        });
        Assert.assertEquals(66, (int) t1.result());

        Task<Integer> t2 = Task.delay(25).continueWith(new Function<Task<Void>, Task<Integer>>() {
            @Override
            public Task<Integer> call(Task<Void> x) throws Exception {
                throw new Exception("I throw");
            }
        }).continueWith(new Function<Task<Integer>, Task<Integer>>() {
            @Override
            public Task<Integer> call(Task<Integer> x) throws Exception {
                if (x.getState() == Task.State.CompletedInError) {
                    return Task.fromResult(42);
                }
                return Task.fromResult(9);
            }
        });
        Assert.assertEquals(42, (int) t2.result());

    }

    @Test
    public void testContinueOn() throws Exception {
        final Ref<Exception> whatWentWrong = new Ref<Exception>();
        final Ref<Thread> currentThread = new Ref<>(null);
        final ExecutorService executorBase = Executors.newFixedThreadPool(14);
        Executor executor = new Executor() {
            @Override
            public void execute(final Runnable command) {
                executorBase.execute(new Runnable() {
                    @Override
                    public void run() {
                        currentThread.value = Thread.currentThread();
                        command.run();
                        currentThread.value = null;

                    }
                });
            }
        };


        Task.delay(5).continueOn(executor).thenSync(new Action<Void>() {
            @Override
            public void call(Void arg) throws Exception {
                try {
                    Assert.assertEquals(Thread.currentThread(), currentThread.value);
                } catch (Exception ex) {
                    whatWentWrong.value = ex;
                }
            }
        }).then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void x) throws Exception {
                return Task.delay(50);
            }
        }).then(new Function<Void, Task<Integer>>() {
            @Override
            public Task<Integer> call(Void x) throws Exception {
                try {
                    Assert.assertEquals(Thread.currentThread(), currentThread.value);
                } catch (Exception ex) {
                    whatWentWrong.value = ex;
                }
                return
                    Task.fromResult(42);
            }
        }).result();

        Assert.assertNull(whatWentWrong.value);
    }

    @Test
    public void testContinueOn2() throws Exception {
        final ExecutorService executor1 = Executors.newSingleThreadExecutor();
        final ExecutorService executor2 = Executors.newSingleThreadExecutor();
        final Ref<Thread> thread1 = new Ref<>();
        final Ref<Thread> thread2 = new Ref<>();
        executor1.submit(new Runnable() {
            @Override
            public void run() {
                thread1.value = Thread.currentThread();
            }
        }).get();
        executor2.submit(new Runnable() {
            @Override
            public void run() {
                thread2.value = Thread.currentThread();
            }
        }).get();

        final Ref<Exception> whatWentWrong = new Ref<Exception>();

        Task.delay(5).continueOn(executor1).then(new Function<Void, Task<Integer>>() {
            @Override
            public Task<Integer> call(Void x) throws Exception {
                return Task.fromResult(42);
            }
        }).thenSync(new Action<Integer>() {
            @Override
            public void call(Integer arg) throws Exception {
                try {
                    Assert.assertEquals(Thread.currentThread(), thread1.value);
                } catch (Exception ex) {
                    whatWentWrong.value = ex;
                }
            }
        }).continueOn(executor2).then(new Function<Void, Task<Void>>() {
            @Override
            public Task<Void> call(Void x) throws Exception {
                return Task.delay(50);
            }
        }).then(new Function<Void, Task<Integer>>() {
            @Override
            public Task<Integer> call(Void x) throws Exception {
                try {
                    Assert.assertEquals(Thread.currentThread(), thread2.value);
                } catch (Exception ex) {
                    whatWentWrong.value = ex;
                }
                return Task.fromResult(42);
            }
        }).result();

        Assert.assertNull(whatWentWrong.value);
    }

}
