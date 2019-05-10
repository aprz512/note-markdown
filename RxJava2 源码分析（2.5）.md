## RxJava2 源码分析（2.5）

### 目的

分析 IoScheduler 类。



首先要说明的是，RxJava2 中虽然使用到了 Java 的线程池，但是还有很多其他的东西，比如，接下来你就会看到 IoScheduler 中自己实现一个简单的线程池。

### 从使用说起

```java
// RxJava
Scheduler.Worker worker = Schedulers.io().createWorker();
worker.schedule();

// Java
ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
scheduledExecutorService.schedule()
```

先不谈运行原理，这里我们比较一下它与我们平时使用的线程池的不同之处。

一般我们使用的时候，都是先获取线程池的对象，然后传递一个 runnable 给它，让它取执行。

但是 RxJava 里面，似乎有点不一样。它需要先创建 worker 对象，然后才能将 action 传递给它。与Java方式比较，它多了一个创建 worker 的过程，那么显然本文的核心就是弄清楚，worker 对象是做什么的？为什么设计成需要 worker 对象才能提交任务？



先说说每个类具体的作用：

| 类名                 | 作用                                                         |
| -------------------- | ------------------------------------------------------------ |
| Worker               | **抽象类**，提供了执行 action 的几个方法，支持单次执行，定时多次执行。定时多次执行是基于单次执行，默认已经在该类中实现。 |
| NewThreadWorker      | **Worker 的子类**，实现了单次执行方法，使用的是 Executors.newScheduledThreadPool 方法。 |
| ThreadWorker         | NewThreadWorker 的子类，增强了一下，提供了过期时间的判断方法。 |
| SequentialDisposable | **一种容器**，用来放入 Disposable 的引用，允许以原子方式更新/替换引用值。经过不断的替换引用，从而达到可以支持 Worker 多次执行任务仍然能够 dispose 的功能。 |
| ScheduledRunnable    | 该类是一个集大成的类，它是一个可以**取消任务**，也可以**取消订阅**的 runnable。 |
| AbstractDirectTask   | 这货虽然名字是 task，然是实际上与 ScheduledRunnable 是一路货色，当成 runnable 也可以。 |
| ScheduledDirectTask  | AbstractDirectTask 的子类                                    |
| EventLoopWorker      | **Worker 的子类**，使用的是 **NewThreadWorker** 实现了单次执行。 |
| CachedWorkerPool     | Rxjava自己实现的一个线程池。                                 |



### 从内部类开始

#### IoScheduler

IoScheduler 源码不长，但是它有3个内部类，我们从内部类开始，因为每个内部类的功能都是比较单一的，分析起来很方便，知道了内部类的作用，外部类的分析就更简单了。

> IoScheduler.java

```java
public final class IoScheduler extends Scheduler {
    
        static final class CachedWorkerPool implements Runnable {...}
    
        static final class EventLoopWorker extends Scheduler.Worker {...}
    
        static final class ThreadWorker extends NewThreadWorker {...}
    
}
```



#### IoScheduler.ThreadWorker

我们从代码量最少的 ThreadWorker 来开始：

> ThreadWorker.java
>
> 可以看出来，ThreadWorker 是对 NewThreadWorker 的一个增强，增加了过期的功能。

```java
    static final class ThreadWorker extends NewThreadWorker {
        private long expirationTime;

        ThreadWorker(ThreadFactory threadFactory) {
            super(threadFactory);
            this.expirationTime = 0L;
        }

        // 获取过期时间
        public long getExpirationTime() {
            return expirationTime;
        }

        // 设置过期时间
        public void setExpirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
        }
    }
```

ThreadWorker 继承了 NewThreadWorker，那我们就需要看看 NewThreadWorker 是用来做什么的。

#### NewThreadWorker

>NewThreadWorker.java
>
>它继承了 Scheduler.Worker。
>
>从注释上来看，它持有一个单线程的ScheduledExecutorService。

```java
/**
 * Base class that manages a single-threaded ScheduledExecutorService as a
 * worker but doesn't perform task-tracking operations.
 *
 */
public class NewThreadWorker extends Scheduler.Worker implements Disposable {...}
```



####Scheduler.Worker

由于 NewThreadWorker 类继承了 Scheduler.Worker，Scheduler.Worker 是一个抽象类，它的核心方法如下：

> io.reactivex.Scheduler.Worker.java

```java
    public abstract static class Worker implements Disposable {

        @NonNull
        public Disposable schedule(@NonNull Runnable run) {
            // 调用了抽象方法
            return schedule(run, 0L, TimeUnit.NANOSECONDS);
        }

		// 子类需要实现该方法
        @NonNull
        public abstract Disposable schedule(@NonNull Runnable run, long delay, @NonNull TimeUnit unit);

		// 有一个默认实现周期性的方法，子类如果有特殊需要，可以覆盖该方法
        @NonNull
        public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
			...
            return sd;
        }


        public long now(@NonNull TimeUnit unit) {
            return unit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

    }
```

所以我们只需要分析两个方法：

1. io.reactivex.Scheduler.Worker#schedule(java.lang.Runnable, long, java.util.concurrent.TimeUnit)
2. io.reactivex.Scheduler.Worker#schedulePeriodically

第一个方法是抽象的，所以我们先放着。

我们先看 `schedulePeriodically` 这个方法是如何实现周期性的。

> io.reactivex.Scheduler.Worker.java

```java
        public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
            final SequentialDisposable first = new SequentialDisposable();

            ...

            return sd;
        }
```

第一行就出现了一个新的类，SequentialDisposable，这个类比较简单，我们看看：

#### SequentialDisposable

>  SequentialDisposable.java
>
> 该类是一种容器，用来放入 Disposable 的引用，允许以原子方式更新/替换引用值。

```java
public final class SequentialDisposable
extends AtomicReference<Disposable>
implements Disposable {

    private static final long serialVersionUID = -754898800686245608L;

    public SequentialDisposable() {
        // nothing to do
    }

    public SequentialDisposable(Disposable initial) {
        lazySet(initial);
    }

    /**
     * 原子的：将引用值换成next，会对之前的引用对象调用 dispose
     */
    public boolean update(Disposable next) {
        return DisposableHelper.set(this, next);
    }

    /**
     * 原子的：将引用值换成next，不会对之前的引用对象调用 dispose
     */
    public boolean replace(Disposable next) {
        return DisposableHelper.replace(this, next);
    }

    @Override
    public void dispose() {
        DisposableHelper.dispose(this);
    }

    @Override
    public boolean isDisposed() {
        return DisposableHelper.isDisposed(get());
    }
}

```

这个类很是很简单的，没有什么复杂的代码，我们把它当作一个容器就好了。

再回到 schedulePeriodically 方法中：

```java
        public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
            // 创建一个容器，引用值为null
            final SequentialDisposable first = new SequentialDisposable();
			// 创建一个容易，引用值为 first
            final SequentialDisposable sd = new SequentialDisposable(first);

            // 这个暂时没啥作用，当作 decoratedRun = run 理解就好
            final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

            // 计算时间，我就不细说了，有兴趣自己算一算
            final long periodInNanoseconds = unit.toNanos(period);
            final long firstNowNanoseconds = now(TimeUnit.NANOSECONDS);
            final long firstStartInNanoseconds = firstNowNanoseconds + unit.toNanos(initialDelay);

            // 这里调用了 schedule ，它是一个抽象方法
            // 暂时不会去分析 schedule，我们这里可以把它理解为 pool 的 execute 方法
            // 反正它会去执行我们传递的 runnable
            // PeriodicTask 就是一个 runnable
            // 后面的参数，是指定延时长度与单位
            Disposable d = schedule(new PeriodicTask(firstStartInNanoseconds, decoratedRun, firstNowNanoseconds, sd,
                    periodInNanoseconds), initialDelay, unit);

            if (d == EmptyDisposable.INSTANCE) {
                return d;
            }
            // 将 first 的引用值改为 d
            // 这里之所以要赋值，是因为第一次执行时，如果使用者调用了 dispose，那么我们必须取消订阅
            // 由于返回了 sd，所以 sd 会调用 first 的 dispose，first 会调用 d 的 dispose
            // 这样就取消订阅了
            first.replace(d);

            return sd;
        }
```

上面注释写的比较详细了，下面我们看 PeriodicTask 是如何周期执行的，这里我们可以想一下我们使用 Hander 写一个定时器的做法，其实是一样的。

#### Scheduler.Worker.PeriodicTask

> io.reactivex.Scheduler.Worker.PeriodicTask
>
> PeriodicTask 是 Worker 的一个内部类，Worker 是 Scheduler 的内部类。
>
> 该类就是用来实现周期任务的。

```java
        final class PeriodicTask implements Runnable {...}
```

既然是实现了 Runnable，那我们只看 run 方法。

> io.reactivex.Scheduler.Worker.PeriodicTask#run

```java
            @Override
            public void run() {
                // 这里就是 run 执行的位置，我们需要执行的 runnable 就是在这里执行的
                decoratedRun.run();

                // 没有取消订阅
                if (!sd.isDisposed()) {

                    // 下面的一段是计算下一次运行的时间，嗯，自己看看吧
                    long nextTick;

                    long nowNanoseconds = now(TimeUnit.NANOSECONDS);
                    // If the clock moved in a direction quite a bit, rebase the repetition period
                    if (nowNanoseconds + CLOCK_DRIFT_TOLERANCE_NANOSECONDS < lastNowNanoseconds
                            || nowNanoseconds >= lastNowNanoseconds + periodInNanoseconds + CLOCK_DRIFT_TOLERANCE_NANOSECONDS) {
                        nextTick = nowNanoseconds + periodInNanoseconds;
                        /*
                         * Shift the start point back by the drift as if the whole thing
                         * started count periods ago.
                         */
                        startInNanoseconds = nextTick - (periodInNanoseconds * (++count));
                    } else {
                        nextTick = startInNanoseconds + (++count * periodInNanoseconds);
                    }
                    lastNowNanoseconds = nowNanoseconds;

                    long delay = nextTick - nowNanoseconds;
                    // 这里将 sd 的引用替换为新的 disposable
                    // 因为旧的任务在第一行代码已经执行完了，所以替换掉
                    // schedule 方法有安排了下一次的任务，这样就成了一个循环，达到了周期性的目的
                    sd.replace(schedule(this, delay, TimeUnit.NANOSECONDS));
                }
            }
```

嗯，到了这里，`io.reactivex.Scheduler.Worker`类就分析完了，下面回到我们的 NewThreadWorker 类中。

####NewThreadWorker

看构造方法：

> NewThreadWorker.java

```java
    public NewThreadWorker(ThreadFactory threadFactory) {
        executor = SchedulerPoolFactory.create(threadFactory);
    }
```

构造函数中调用 SchedulerPoolFactory 的 create 方法，创建了一个 executor 对象。



#### SchedulerPoolFactory

看看 SchedulerPoolFactory 的 create 方法：

> SchedulerPoolFactory .java

```java
    public static ScheduledExecutorService create(ThreadFactory factory) {
        final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, factory);
        // PURGE_ENABLED 的值与系统有关，暂且不提
        if (PURGE_ENABLED && exec instanceof ScheduledThreadPoolExecutor) {
            ScheduledThreadPoolExecutor e = (ScheduledThreadPoolExecutor) exec;
            POOLS.put(e, exec);
        }
        return exec;
    }
```

这里我们只关注第一行代码，与注释说的一样，它创建了一个单线程的 ScheduledExecutorService 对象。



####NewThreadWorker

构造方法与父类，我们都分析完了，现在看看它是怎么实现那个抽象方法的。

> NewThreadWorker

```java
    @NonNull
    @Override
    public Disposable schedule(@NonNull final Runnable run) {
        // 这里它覆盖了原来的默认实现，传递了一个null
        // 没看懂，方法参数不接受 null 啊
        return schedule(run, 0, null);
    }

    @NonNull
    @Override
    public Disposable schedule(@NonNull final Runnable action, long delayTime, @NonNull TimeUnit unit) {
        // disposed 就没啥说的
        if (disposed) {
            return EmptyDisposable.INSTANCE;
        }
        // 调用了 scheduleActual 方法
        return scheduleActual(action, delayTime, unit, null);
    }

    /**
     * 将runnnable 包装成 ScheduledRunnable，交给 ScheduledExecutorService 去执行 
     * 如果调度被驳回， ScheduledRunnable.wasScheduled 的值为 false。
     */
    @NonNull
    public ScheduledRunnable scheduleActual(final Runnable run, long delayTime, @NonNull TimeUnit unit, @Nullable DisposableContainer parent) {
        // 直接理解成 decoratedRun = run
        Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        // 将 decoratedRun 包装一下
        ScheduledRunnable sr = new ScheduledRunnable(decoratedRun, parent);

        // 传递的参数为 null，这一段跳过
        if (parent != null) {
            if (!parent.add(sr)) {
                return sr;
            }
        }

        Future<?> f;
        try {
            if (delayTime <= 0) {
                // 不延迟就直接执行
                f = executor.submit((Callable<Object>)sr);
            } else {
                // 延迟执行
                f = executor.schedule((Callable<Object>)sr, delayTime, unit);
            }
            // 设置 future，便于获取执行结果
            sr.setFuture(f);
        } catch (RejectedExecutionException ex) {
            if (parent != null) {
                parent.remove(sr);
            }
            RxJavaPlugins.onError(ex);
        }

        return sr;
    }
```

嗯，现在，我们需要看看 ScheduledRunnable 到底是个什么，起什么作用。

#### ScheduledRunnable

> ScheduledRunnable

```java
public final class ScheduledRunnable extends AtomicReferenceArray<Object>
implements Runnable, Callable<Object>, Disposable {...}
```

这个类的源码比较奇特，我就不展开了，我们把它当作一个 Callable 使用就可以了，但是它也支持 Disposable。源码里面都是一些原子性的赋值更新处理。

回到 NewThreadWorker 类，它的 schedule 方法就是将我们传递的 runnable 包装一下，然后将这个包装好的 ScheduledRunnable 交给 ScheduledExecutorService 去处理。

因为 ScheduledRunnable 实现了 Disposable，所以直接返回它，可以用于取消订阅。ScheduledRunnable 还实现了 Callable 是因为我们取消订阅的时候，可以用于线程池取消任务（f.cancel(true)）。



#### NewThreadWorker

到了这里 NewThreadWorker 的一系列 schedule 方法就分析完了，然而，我们发现，它除了 schedule  方法之外，还提供了一些别的public的方法，顺便看了算求。

> io.reactivex.internal.schedulers.NewThreadWorker#scheduleDirect
>
> 这个方法也是提交一个 runnable 给线程池执行，然是不是使用了 ScheduledRunnable 来包装，
>
> 而是使用的 ScheduledDirectTask

```java
    public Disposable scheduleDirect(final Runnable run, long delayTime, TimeUnit unit) {
        // 这里出了一个新类
        ScheduledDirectTask task = new ScheduledDirectTask(RxJavaPlugins.onSchedule(run));
        try {
            // 下面的代码与之前分析的一样，就跳过
            Future<?> f;
            if (delayTime <= 0L) {
                f = executor.submit(task);
            } else {
                f = executor.schedule(task, delayTime, unit);
            }
            task.setFuture(f);
            return task;
        } catch (RejectedExecutionException ex) {
            RxJavaPlugins.onError(ex);
            return EmptyDisposable.INSTANCE;
        }
    }
```



那我们就来分析分析 ScheduledDirectTask 类。

#### ScheduledDirectTask 

> ScheduledDirectTask 
>
> 继承了 AbstractDirectTask 类
>
> 主要是实现了 call 方法，给 runner 赋值，调用 runnable 的 run 方法

```java
public final class ScheduledDirectTask extends AbstractDirectTask implements Callable<Void> {

    private static final long serialVersionUID = 1811839108042568751L;

    public ScheduledDirectTask(Runnable runnable) {
        super(runnable);
    }

    @Override
    public Void call() throws Exception {
        runner = Thread.currentThread();
        try {
            runnable.run();
        } finally {
            // 设置为完成状态
            // 还是没有搞太明白，set 与 lazySet 的区别，什么内存屏障啊
            lazySet(FINISHED);
            runner = null;
        }
        return null;
    }
}
```

#### AbstractDirectTask

> AbstractDirectTask
>
> 源码不长，我就全部贴出来了

```java
abstract class AbstractDirectTask
extends AtomicReference<Future<?>>
implements Disposable {

    private static final long serialVersionUID = 1811839108042568751L;

    protected final Runnable runnable;

    protected Thread runner;

	// 哦，我还不知道有个 Functions.EMPTY_RUNNABLE 可以用
    protected static final FutureTask<Void> FINISHED = new FutureTask<Void>(Functions.EMPTY_RUNNABLE, null);

    protected static final FutureTask<Void> DISPOSED = new FutureTask<Void>(Functions.EMPTY_RUNNABLE, null);

    AbstractDirectTask(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public final void dispose() {
        Future<?> f = get();
        if (f != FINISHED && f != DISPOSED) {
        	// 将状态设置为取消
            if (compareAndSet(f, DISPOSED)) {
            	// 取消线程池里的任务
                if (f != null) {
                    f.cancel(runner != Thread.currentThread());
                }
            }
        }
    }

    @Override
    public final boolean isDisposed() {
        Future<?> f = get();
        return f == FINISHED || f == DISPOSED;
    }

    public final void setFuture(Future<?> future) {
        for (;;) {
            Future<?> f = get();
            if (f == FINISHED) {
                break;
            }
            if (f == DISPOSED) {
            // 取消线程池里的任务
                future.cancel(runner != Thread.currentThread());
                break;
            }
            // 设置引用值为 f
            if (compareAndSet(f, future)) {
                break;
            }
        }
    }
}
```

感觉，ScheduledDirectTask  这个类的功能与 ScheduledRunnable 的功能差不多啊，ScheduledRunnable 更强力一点。至少在我们分析的这个流程里面，感觉不到区别，嗯，不过想到 `scheduleActual` 方法比 `scheduleDirect`多了一个参数，想来，玄机应该在这里，有兴趣的自己再去找找。



如果实在心里想不通的，把 ScheduledDirectTask  与 ScheduledRunnable  都当成具有解除订阅与取消任务的增强型 Runnable 就好了。



#### NewThreadWorker

再次回到 NewThreadWorker，还有一个方法没有说到：

> io.reactivex.internal.schedulers.NewThreadWorker#schedulePeriodicallyDirect
>
> 该方法用于周期性的执行任务

```java
    public Disposable schedulePeriodicallyDirect(Runnable run, long initialDelay, long period, TimeUnit unit) {
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);
        // 这里如果周期性设置为0，也就是说没有间隔的重复执行任务
        if (period <= 0L) {

            // 没有间隔的话，使用 InstantPeriodicTask
            InstantPeriodicTask periodicWrapper = new InstantPeriodicTask(decoratedRun, executor);
            try {
                Future<?> f;
                if (initialDelay <= 0L) {
                    f = executor.submit(periodicWrapper);
                } else {
                    f = executor.schedule(periodicWrapper, initialDelay, unit);
                }
                periodicWrapper.setFirst(f);
            } catch (RejectedExecutionException ex) {
                RxJavaPlugins.onError(ex);
                return EmptyDisposable.INSTANCE;
            }

            return periodicWrapper;
        }
        // 有间隔的话，使用 ScheduledDirectPeriodicTask
        ScheduledDirectPeriodicTask task = new ScheduledDirectPeriodicTask(decoratedRun);
        try {
            Future<?> f = executor.scheduleAtFixedRate(task, initialDelay, period, unit);
            task.setFuture(f);
            return task;
        } catch (RejectedExecutionException ex) {
            RxJavaPlugins.onError(ex);
            return EmptyDisposable.INSTANCE;
        }
    }
```

里面，线程池的使用，Task 的分析就不展开了，因为之前已经分析过一个 Task，想一想都能知道里面的代码大概长什么样，有兴趣的自己去看看吧。



好了，到这里，NewThreadWorker 类，我们就分析完了，没想到这么短的一个内部类，里面有这么多东西。不过，总结一下，里面的东西其实并不多。

1. schedule 等方法，用于提交任务，内部使用的是 ScheduledExecutorService 线程池
2. schedule 等方法返回一个 Disposable，用于解除订阅，解除订阅时，也会取消该任务。
3. 周期性的实现，有两种方式，一种是自己调用自己实现的，一种是使用 ScheduledExecutorService 的 scheduleAtFixedRate 方法实现的。



#### IoScheduler.EventLoopWorker

接下来，我们趁热打铁，看另外一个继承至 Scheduler.Worker 的内部类。

> io.reactivex.internal.schedulers.IoScheduler.EventLoopWorker
>
> EventLoopWorker 继承至 Scheduler.Worker
>
> Worker 上面分析过，是用来执行任务的。

```java
    static final class EventLoopWorker extends Scheduler.Worker {
        private final CompositeDisposable tasks;
        // 这里有一个新东西
        private final CachedWorkerPool pool;
        private final ThreadWorker threadWorker;

        final AtomicBoolean once = new AtomicBoolean();

        EventLoopWorker(CachedWorkerPool pool) {
            this.pool = pool;
            this.tasks = new CompositeDisposable();
            this.threadWorker = pool.get();
        }

        @Override
        public void dispose() {
            if (once.compareAndSet(false, true)) {
                tasks.dispose();

                // releasing the pool should be the last action
                // 将线程返回线程池里面去
                pool.release(threadWorker);
            }
        }

        @Override
        public boolean isDisposed() {
            return once.get();
        }

        @NonNull
        @Override
        public Disposable schedule(@NonNull Runnable action, long delayTime, @NonNull TimeUnit unit) {
            if (tasks.isDisposed()) {
                // don't schedule, we are unsubscribed
                return EmptyDisposable.INSTANCE;
            }
			// 让 ThreadWorker 去执行一个 aciton 任务，这里最后一个参数注意一下
            // threadWorker 里面只有一个线程，所以，任务是顺序执行的
            return threadWorker.scheduleActual(action, delayTime, unit, tasks);
        }
    }
```

看里面的代码还是非常简单的，它的构造方法接收一个 CachedWorkerPool，这个是 EventLoopWorker 类里面，唯一一个陌生类，看看这个类：

####  IoScheduler.CachedWorkerPool

欸，太好了，这个也是 IoScheduler 的内部类，这样的话，把这个类分析完了之后， IoScheduler 的内部类就分析完成了。

```java
    static final class CachedWorkerPool implements Runnable {
        private final long keepAliveTime;
        // 队列
        private final ConcurrentLinkedQueue<ThreadWorker> expiringWorkerQueue;
        final CompositeDisposable allWorkers;
        // java 的线程池
        private final ScheduledExecutorService evictorService;
        private final Future<?> evictorTask;
        // 线程工程，一般就 new 一个 Thread，然后给个名字就完事了
        private final ThreadFactory threadFactory;

        CachedWorkerPool(long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
            this.keepAliveTime = unit != null ? unit.toNanos(keepAliveTime) : 0L;
            this.expiringWorkerQueue = new ConcurrentLinkedQueue<ThreadWorker>();
            this.allWorkers = new CompositeDisposable();
            this.threadFactory = threadFactory;

            ScheduledExecutorService evictor = null;
            Future<?> task = null;
            if (unit != null) {
                // 这里也创建了一个单个线程的线程池
                evictor = Executors.newScheduledThreadPool(1, EVICTOR_THREAD_FACTORY);
                // 创建定时任务，runnable 是 this，就是 run 里面的代码
                task = evictor.scheduleWithFixedDelay(this, this.keepAliveTime, this.keepAliveTime, TimeUnit.NANOSECONDS);
            }
            evictorService = evictor;
            evictorTask = task;
        }

        @Override
        public void run() {
            evictExpiredWorkers();
        }

        ThreadWorker get() {
            // 如果线程池关闭了，则返回一个关闭的线程池
            if (allWorkers.isDisposed()) {
                return SHUTDOWN_THREAD_WORKER;
            }
            // 队列不为空，从队列中取一个 worker 出来
            while (!expiringWorkerQueue.isEmpty()) {
                ThreadWorker threadWorker = expiringWorkerQueue.poll();
                if (threadWorker != null) {
                    return threadWorker;
                }
            }

            // 队列没有 worker，new 一个出来
            // No cached worker found, so create a new one.
            ThreadWorker w = new ThreadWorker(threadFactory);
            // ThreadWorker 继承 NewThreadWorker
            // NewThreadWorker 实现了 Disposable
            // 调用 dispose 会关闭里面的线程池
            allWorkers.add(w);
            return w;
        }

        /**
         * 将 worker 重新放入到线程池（队列）中，将过期时间重置
         */
        void release(ThreadWorker threadWorker) {
            // Refresh expire time before putting worker back in pool
            threadWorker.setExpirationTime(now() + keepAliveTime);

            expiringWorkerQueue.offer(threadWorker);
        }

        /**
         * 移除过期 worker
         */
        void evictExpiredWorkers() {
            if (!expiringWorkerQueue.isEmpty()) {
                long currentTimestamp = now();

                // 遍历队列
                for (ThreadWorker threadWorker : expiringWorkerQueue) {
                    // 看看 threadWorker 过期了没有
                    if (threadWorker.getExpirationTime() <= currentTimestamp) {
                        // 过期了就移除
                        if (expiringWorkerQueue.remove(threadWorker)) {
                            // 从队列移除成功后，将 Disposable 也移除掉
                            allWorkers.remove(threadWorker);
                        }
                    } else {
                        // 因为队列是按照时间排列的，所以找到第一个没有过期的就可以退出循环了
                        // Queue is ordered with the worker that will expire first in the beginning, so when we
                        // find a non-expired worker we can stop evicting.
                        break;
                    }
                }
            }
        }

        long now() {
            return System.nanoTime();
        }

        /**
         * 关闭线程池
         */
        void shutdown() {
            allWorkers.dispose();
            if (evictorTask != null) {
                evictorTask.cancel(true);
            }
            if (evictorService != null) {
                evictorService.shutdownNow();
            }
        }
    }
```

注释很清晰，如果你阅读过Java的线程池源码，会更容易理解，这里就是教我们如果实现一个自己的 CacheThreadPool 线程池啊。只不过这个线程池（ConcurrentLinkedQueue）的每一个 worker 都是一个线程池（NewThreadWorker）。

嗯，到这里，IoScheduler 的内部类就讲完了，我们看看它的其他方法吧。

首先，IoScheduler 是继承至 Scheduler 的，所以我们先从它对抽象方法的实现看起。

#### IoScheduler

> IoScheduler

```java
public Worker createWorker() {
    return new EventLoopWorker(pool.get());
}
```

它是直接创建一个 EventLoopWorker 对象并返回。

根据我们上面的分析，EventLoopWorker 将任务给了 ThreadWorker 去执行，ThreadWorker 又将任务给了一个单个线程的线程池去执行。

而且，ThreadWorker 是线程池中的线程池，因为它就一个线程，所以可以把它当作单个线程而不是线程池，那么就是说，IoScheduler 的线程池就是一个功能与Java中的`Executors.newCachedThreadPool()`方法创建出来的线程池功能是差不多的。

好吧，虽然差不多，但是毕竟这里里面的线程池与线程提供了对 Disposable 接口的支持。



感觉，EventLoopWorker 的作用，只是一个代理啊，最后还是将任务转给了 CachedWorkerPool。可能为了支持 dispose 操作花费了很多心思。

