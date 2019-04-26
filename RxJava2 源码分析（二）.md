## RxJava2 源码分析（二）

### 目的

这篇文章的主要目的就是弄清楚链式调用与线程切换。

如果你读懂了第一篇文章，这篇文章阅读起来还是非常简单的。因为链式调用和线程切换无非就是多了些 “数据源-观察者” 对。

线程切换还涉及到线程池相关的东西，由于篇幅问题，线程池相关的东西会放到下一篇。



### 从一个例子开始：

```kotlin
        // 观察者 -- 蝙蝠侠
        val observerBatMan = object : Observer<Int> {

            override fun onComplete() {
            }

            override fun onNext(t: Int) {
                System.out.println(Thread.currentThread().name + "--observer")
            }

            override fun onError(e: Throwable) {
            }

            override fun onSubscribe(d: Disposable) {
            }

        }


        // 数据源 -- 小丑
        val sourceClown = ObservableOnSubscribe<Int> {
            it.onNext(1)
            it.onComplete()
            System.out.println(Thread.currentThread().name + "--source")
        }

        // 开始观察
        Observable
                // ①
                .create<Int>(sourceClown)
                // ② 指定数据源执行的线程
                .subscribeOn(Schedulers.computation())
                // ③ 指定观察者执行的线程
                .observeOn(Schedulers.io())
                // ④
                .subscribe(observerBatMan)

    }
```

嗯，和上一篇文章的例子差不多，就多了几行线程切换而已，我们先给出数据的日志。有一个大致的印象，再来深入分析。

```shel
RxComputationThreadPool-1--source
RxCachedThreadScheduler-1--observer
```



### demo 里面的 ② 处

有了第一篇的基础，那么我们直接从 **demo 里面的 ② 处**入手：

>  Observable.java

```java
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new ObservableSubscribeOn<T>(this, scheduler));
    }
```



你使用了套路一，效果拔群，得到结果如下：

> Observable.java
>
> 简化之后的 subscribeOn 代码，实际上就是创建并返回了一个 ObservableSubscribeOn 对象

```java
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        return new ObservableSubscribeOn<T>(this, scheduler);
    }
```



我们继续看 ObservableSubscribeOn。

> ObservableSubscribeOn.java
>
> AbstractObservableWithUpstream.java

```java
// ObservableSubscribeOn 继承了 AbstractObservableWithUpstream
public final class ObservableSubscribeOn<T> extends AbstractObservableWithUpstream<T, T> {...}

// AbstractObservableWithUpstream 继承了 Observable
abstract class AbstractObservableWithUpstream<T, U> extends Observable<U> implements HasUpstreamObservableSource<T> {...}
```

所以，ObservableSubscribeOn 最终还是继承至 Observable的。



在第一篇文章中，我们分析的对象是 ObservableCreate，而现在我们分析的对象是 ObservableSubscribeOn。为了能够更加清晰的列出不同点，还是搞一个表格吧：

|                  | ObservableSubscribeOn                                     | ObservableCreate                         |
| ---------------- | --------------------------------------------------------- | ---------------------------------------- |
| 直接父类         | AbstractObservableWithUpstream（最终还是继承 Observable） | Observable                               |
| 构造方法参数个数 | 两个：(ObservableSource\<T> source, Scheduler scheduler)  | 一个：(ObservableOnSubscribe\<T> source) |

主要的不同点，还是在构造参数这里。

1. 参数个数不同，这个好理解，因为需要切换线程，肯定需要指定切换到哪个线程，所以多一个参数
2. source 类类型不同，这个需要深入分析，往下看



看看 source 类有哪些不同之处：

> ObservableOnSubscribe.java
>
> ObservableSource.java

```java
public interface ObservableOnSubscribe<T> {
    void subscribe(@NonNull ObservableEmitter<T> e) throws Exception;
}

public interface ObservableSource<T> {
    void subscribe(@NonNull Observer<? super T> observer);
}
```

这两个类都是接口，都只有一个 subscribe 方法，看起来比较类似。

除了类名不同之外，就只有**方法的参数不同**了。



ObservableOnSubscribe 接收的参数 ObservableEmitter，上一篇文章说过，是将 Observer 包装了一层。

ObservableSource 直接接收了 Observer，嗯，很牛逼，后面我们来分析看看，它为啥不要包装，你也可以思考一下，嘿嘿嘿。



经过上面的分析，总的来说，区别不大，所以我们仍然可以套第一篇的套路来分析。

到这里先上个图，为后面做好心理准备。

![](rxjava2(21).png)



### demo 里面的 ③ 处

接下来，**我们看 demo 里面的 ③ 处**：

> Observable.java

```java
    public final Observable<T> observeOn(Scheduler scheduler) {
        return observeOn(scheduler, false, bufferSize());
    }
```



调用了同名方法：

> Observable.java

```java
    public final Observable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return RxJavaPlugins.onAssembly(new ObservableObserveOn<T>(this, scheduler, delayError, bufferSize));
    }
```



使用套路一，简化代码：

> Observable.java
>
> observeOn 简化后的代码

```
    public final Observable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        return new ObservableObserveOn<T>(this, scheduler, delayError, bufferSize);
    }
```

可以看出，实际上就是创建了一个 ObservableObserveOn 对象。



看看这个对象吧，不出意外，和 ObservableOnSubscribe 应该很像。

先看类的结构：

>ObservableObserveOn.java

```java
public final class ObservableObserveOn<T> extends AbstractObservableWithUpstream<T, T> {...}
```

与 ObservableOnSubscribe 一样，继承同一个类。



再看类的构造方法：

> ObservableObserveOn.java

```java
    public ObservableObserveOn(ObservableSource<T> source, Scheduler scheduler, boolean delayError, int bufferSize) {
        super(source);
        this.scheduler = scheduler;
        this.delayError = delayError;
        this.bufferSize = bufferSize;
    }
```

这个方法的参数就更多了，前面两个我们应该熟悉了，看看后面的两个参数是什么意思。虽然这里没有注释，但是这个参数的值是从别处传来的，所以只要找到源头，还是可以找到相关注释的。

1. delayError 

   ```
   indicates if the onError notification may not cut ahead of onNext notification on the other side of the scheduling boundary. If true a sequence ending in onError will be replayed in the same order as was received from upstream
   ```

   额，我只能明白一个大概：**若存在Error事件，则如常执行，执行完后再抛出错误异常**

2. bufferSize 缓存大小，暂时还不知道缓存在哪里，所以先放着。



到了这里，我们再上一个图吧，后面的流程要起飞了。

![](rxjava2(22).png)



###  demo 里面的 ④ 处

接下来，就到了一个转折点，就是 demo 中的 ④ 处，它调用了 subscribe 方法。

在第一篇中，我们分析过，subscribe 方法会调用 subscribeActual 方法，所以这里，我们直接进入到 ObservableObserveOn 的 subscribeActual 方法中，看看它做了什么。

> ObservableObserveOn .java

```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        if (scheduler instanceof TrampolineScheduler) {
            source.subscribe(observer);
        } else {
            Scheduler.Worker w = scheduler.createWorker();

            source.subscribe(new ObserveOnObserver<T>(observer, w, delayError, bufferSize));
        }
    }
```

由于，我们传递的 scheduler 肯定不是 TrampolineScheduler，所以会直接进入到 else 分支。

else 分支里面的代码也很简单，我们先跳过线程池相关的东西，所以需要分析的就只有一行代码：

> ObservableObserveOn.java
>
> subscribeActual 方法的 else 分支

```java
source.subscribe(new ObserveOnObserver<T>(observer, w, delayError, bufferSize));
```

按照套路二，ObserveOnObserver 其实就是对 observer 做了一个包装。

这个 observer 就是我们自己创建的 observerBatMan，上个图：

![](rxjava2(23).png)



现在比较绕的是，ObservableObserveOn 类中的 subscribeActual  方法中的 source 变量是谁。由于 source 是从构造函数传递进来的，我们再回到 Observerable 的 observeOn 方法：

> Observable.java

```java
    public final Observable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return RxJavaPlugins.onAssembly(new ObservableObserveOn<T>(this, scheduler, delayError, bufferSize));
    }
```

可以看到，在创建 ObservableObserveOn 对象的时候，第一个参数传递的是 this。

这里有一个稍微绕的点，**因为 observerOn 方法是 ObservableSubscribeOn 对象调用的，所以 this，指向的是 ObservableSubscribeOn。**

所以，这里相当于 ObservableObserveOn  是一个桥梁，让 ObservableSubscribeOn  与 ObservableOnObserve 搭上了关系。

上一个图：

![](rxjava2(24).png)



同样的，我们再来分析 `ObservableSubscribeOn`类的 `subscribeActual` 方法：

> ObservableSubscribeOn.java

```java
    @Override
    public void subscribeActual(final Observer<? super T> s) {
        final SubscribeOnObserver<T> parent = new SubscribeOnObserver<T>(s);

        s.onSubscribe(parent);

        parent.setDisposable(scheduler.scheduleDirect(new SubscribeTask(parent)));
    }
```

这个 subscribeActual 内部的代码风格与想象的完全不一样啊，虽然前2行很熟悉，但是第3行完全没见过啊。



现在，我们来分析第3行代码，先看 parent.setDisposable(xxx)：

> SubscribeOnObserver.java

```java
void setDisposable(Disposable d) {
    DisposableHelper.setOnce(this, d);
}
```

> DisposableHelper.java
>
> 该方法，用来将 field 的值，设置为 d。
>
> 如果，设置的时候，field 已经有值了，返回false。
>
> 如果 field 有值， 并且值不是 DISPOSED，抛出异常。

```java
    public static boolean setOnce(AtomicReference<Disposable> field, Disposable d) {
        ObjectHelper.requireNonNull(d, "d is null");
        if (!field.compareAndSet(null, d)) {
            d.dispose();
            if (field.get() != DISPOSED) {
                // 抛出异常
                reportDisposableSet();
            }
            return false;
        }
        return true;
    }
```

这样来看，其实  `parent.setDisposable(xxx) `这行代码也没做什么，正常情况下，就是将 `scheduler.scheduleDirect(new SubscribeTask(parent))` 的值设置给了 parent。



下面，继续看 `scheduler.scheduleDirect()`做了什么，由于篇幅问题，这里不分析线程池的东西，只说一下这个方法的作用，**其实就是将一个 runnable，放到线程池中去执行，这里可以知道，线程切换了**。



再继续，看看 SubscribeTask 类：

> SubscribeTask.java

```java
public final class ObservableSubscribeOn<T> extends AbstractObservableWithUpstream<T, T> {
    ...
    // 内部类
	final class SubscribeTask implements Runnable {
        private final SubscribeOnObserver<T> parent;

        SubscribeTask(SubscribeOnObserver<T> parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            // source 变量是 ObservableSubscribeOn 的变量
            source.subscribe(parent);
        }
    }
}
```

继承了 Runnable，在 run 方法中，我们终于看到了 subscribe 方法。



继续深入，看看 source 对象是谁：

> Observable.java

```java
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new ObservableSubscribeOn<T>(this, scheduler));
    }
```

source 参数传递的是this，所以 source 对象是 ObservableCreate 对象。

到这里，还可以解释上面的问题：为啥 ObservableSource 的 subscribe 方法的参数是一个 Observer？

> ObservableSource.java
>
> 为了方便，我又把这个接口代码贴了一遍

```java
public interface ObservableSource<T> {
    void subscribe(@NonNull Observer<? super T> observer);
}
```

在我们的例子中，observer 参数就是 ObservableCreate  对象。

其实，从这个类名都可以看出 ObservableSource 这个接口表示的是数据源，因为 Observerable 类就实现了这个接口，所以这个接口的 subscribe 方法的实现就是 Observable 的内部实现。

当我们调用 subscribeOn 与 observeOn 这两个方法的时候，其实是将调者这做为数据源的意思。

另外，可以推出 ObservableOnSubscribe 这个接口，应该是专门**用来处理数据源的源头的**（方便我们调用 emitter.onNext 等方法），两个接口的意义不一样。



所以，我们可以得到这样的一个图：

![](rxjava2(25).png)



把这几个类，整合到调用图里面，得到如下图：

![](rxjava2(26).png)



上面的图，看起来还算清晰，但是没有线程切换的内容，下面会说到。

与上一篇一样，我们再来整理一下这个demo的执行流程图，你可以先自行想一下这个图应该是什么样子的。

![](rxjava2(27).png)



图中灰色的长方体，表示的是调用 subscribeOn 与 observerOn 产生的 “数据源-观察者”对。

图中 subscribeOn 蓝色方块表示的是 subscribeOn  指定的线程切换的地方，可以看到，它指定的线程会影响到后续的所有流程。

图中 observerOn 黄色的方块表示的是 observerOn 指定的线程切换的地方，它会影响后续流程。



在回想一下，其实每次 observerOn 或者 subscribeOn  都创建了一个链条一样的节点，然后在 subscribeActual 将这些节点连接起来：

![](rxjava2(28).png)

好了，这篇文章的东西就差不多了，关于线程剩下的东西，本章中都只是一笔带过，详细的内容，留到下一篇。