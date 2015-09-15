# Tasks
This is a library for simplifying asynchronous programming in Java using promises/futures.

To get a taste of what this library looks like, take a look at this piece of code:
```java
    public Task<String> downloadUserPictureAsync(int userId) {
        return getPictureUrlAsync(userId).then(url ->
                        getSavePathAsync(userId).then(path ->
                                downloadFileAsync(url, path).map(__ -> path))
        ).continueWith(task -> {
            if (task.getException() != null)
                Logger.getGlobal().log(Level.WARNING, "downloading user picture failed", task.getException());
            return task;
        });
    }
```

This piece of code downloads the picture of a user and returns the saved path, all asynchronously using the *`Task<T>`* class.

 `Task<T>` is the heart of this library. It represents an operation that will finish at some point in the future, and produce a result of type `T`, *or* fail with an `Exception`. This is what you might know as a *Futue*.

As you can see, this library makes it possible to write code pretty much like it is normal, synchronous code. Of course there is a lot more to this library than what is shown in the example above. There is support for loops, error handling and resource management, dealing with multuple concurrent async operations, controlling threading, creating `Task<T>` objects and more.



For a more comprehensive overview of this libraryr, take a look at [**this introduction page**](https://github.com/s-arash/TasksJava/wiki/The-Tasks-Library).

This library is for now, available in preview form:
###Tasks binary
gradle:

```compile 'net.s-arash:tasks:0.2.0```

maven:
```xml
<dependency>
    <groupId>net.s-arash</groupId>
    <artifactId>tasks</artifactId>
    <version>0.2.0</version>
</dependency>
```
###TasksRx binary
TaskRx provides facilites to uset the Tasks library and RxJava together.

gradle:

```compile 'net.s-arash:tasksrx:0.2.0```

maven:
```xml
<dependency>
    <groupId>net.s-arash</groupId>
    <artifactId>tasksrx</artifactId>
    <version>0.2.0</version>
</dependency>
```

###TasksAndroid binary
TasksAndroid provides useful android specific APIs for working with Tasks in android projects.

gradle:

```compile 'net.s-arash:tasksandroid:0.2.0```

maven:
```xml
<dependency>
    <groupId>net.s-arash</groupId>
    <artifactId>tasksandroid</artifactId>
    <version>0.2.0</version>
</dependency>
```
