## RxJava2 源码分析（三）

### 目的

本篇主要分析 RxJava2 中的线程池与线程调度时的源码流程。

顺便介绍RxJava2中常用的几个线程池。



上一篇文章，我们的demo中，指定线程时，使用的是 Schedulers 这个类。那我们直接从这个类入手。

> Schedulers.java

```java
/**
 * Static factory methods for returning standard Scheduler instances.
 * <p>
 */
public final class Schedulers {...}
```

一般看一个类的时候，先看注释会让你对这个类又一个全局的概念，它起一个什么作用。

上面的注释说的比较清楚了，它是一个工厂方法，返回一些 Scheduler 对象的实例。



那么下面我们看看 Scheduler 类。

> Scheduler.java
>
> 这个类提供了API，用来调度工作单元。你可以指定延迟时间，周期性。
>
> 我们可以想到很多别的东西，Timer，Executors.newScheduledThreadPool(2)等等

```java
public abstract class Scheduler {...}
```



这个类的代码不多，我们打开 Structure 视图，可以看到该类的一个结构，这里展示一下 Scheduler 最核心的定义部分：

> Scheduler.java

```java
public abstract class Scheduler {

    @NonNull
    public abstract Worker createWorker();

    public Disposable scheduleDirect(@NonNull Runnable run) {
        ...
    }

    public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        ...
    }
    
    @NonNull
    public Disposable schedulePeriodicallyDirect(@NonNull Runnable run, long initialDelay, long period, @NonNull TimeUnit unit) {
        ...
    }

    public abstract static class Worker implements Disposable {
      
        @NonNull
        public Disposable schedule(@NonNull Runnable run) {
            ...
        }

        @NonNull
        public abstract Disposable schedule(@NonNull Runnable run, long delay, @NonNull TimeUnit unit);

        @NonNull
        public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
            ...
        }
    }
}
```

从上面的定义可以看出，Scheduler 本质上就是用来调度 Runnable 的，支持**立即、延时和周期形式的调用**。



我们从其中一个方法入手，就选择最简单的 `public Disposable scheduleDirect(@NonNull Runnable run) {...}` 方法。分析完这个方法之后，在看其他的方法，应该就是差不多的了。



> Scheduler.java

```java
    public Disposable scheduleDirect(@NonNull Runnable run) {
        return scheduleDirect(run, 0L, TimeUnit.NANOSECONDS);
    }
```

上面说过，Scheduler 支持延迟调用，那么这里传递0，就表示不延迟。



> Scheduler.java

```java
    public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        // ① 创建了一个 Worker
        final Worker w = createWorker();

        // 装饰一下，但是通常会将 run 原封不动的返回
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        // ② 创建 Task
        DisposeTask task = new DisposeTask(decoratedRun, w);

        // ③ 执行 task
        w.schedule(task, delay, unit);

        return task;
    }
```

上面的代码中，我加了一点注释，下面来一行一行的分析。



> io.reactivex.Scheduler#scheduleDirect(java.lang.Runnable, long, java.util.concurrent.TimeUnit)

```java
final Worker w = createWorker();
```

由于，Scheduler 是一个抽象类，所以只有它的子类才知道具体的实现，这里我们用 Schedulers.io 为例。

由于篇幅问题，如果这里深入的话，会很容易丢失目标，所以我将 IoScheduler 这个类的分析提出来了，放到了[另外一篇文章](RxJava2 源码分析（2.5）)中。

看完这篇文章之后，就知道 Worker 有点像命令，它里面指定了任务需要执行的线程池（因为Worker是Scheduler的子类创建的，Scheduler的子类创建了自己的线程池），当我们调用 `work.schedule` 的时候，就会将任务交给work中指定的线程池去执行。

这样一来，①处与③处的代码都说清楚了。现在还剩②处的代码。看看 DisposeTask 这个类吧。



#### DisposeTask

> DisposeTask
>
> 看它的构造方法与类结构，可以大概猜到它可能会起一个代理委托的作用。

```java
    static final class DisposeTask implements Runnable, Disposable {
        final Runnable decoratedRun;
        final Worker w;

        Thread runner;

        DisposeTask(Runnable decoratedRun, Worker w) {
            this.decoratedRun = decoratedRun;
            this.w = w;
        }

        @Override
        public void run() {
            runner = Thread.currentThread();
            try {
                // 运行 task 中的代码
                decoratedRun.run();
            } finally {
                // 执行完之后，调用 dispose 
                dispose();
                runner = null;
            }
        }

        @Override
        public void dispose() {
            if (runner == Thread.currentThread() && w instanceof NewThreadWorker) {
                // 如果 worker 是 NewThreadWorker，则关闭自己 
                ((NewThreadWorker)w).shutdown();
            } else {
                // 调用 worker 的 dispose
                // 之前我们分析过 IoScheduler.EventLoopWorker 类
                // 它的 dispose 会将自己重新放到线程池中，重复利用
                w.dispose();
            }
        }

        @Override
        public boolean isDisposed() {
            return w.isDisposed();
        }
    }
```

所以这个类的主要作用是起到一个让 worker 重复利用的作用，但是这只是针对 IoScheduler 的 Worker 来说，别的 Worker 可能会不一样。

因为它调用了 Worker 的 dispose 方法，所以 Worker 的收尾工作可以全部放到这个方法中。



Scheduler类的重要方法都分析完了，其他的方法，就交给你们了。看完了这几篇文章，你应该具有了能够看懂其他 Scheduler 的能力，GOOD LUCK !!!



### 参考文档

<https://juejin.im/post/5b75207ce51d45565d23e093>

