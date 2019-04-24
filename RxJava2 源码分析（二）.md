## RxJava2 源码分析（二）

### 目的

这篇文章的主要目的就是弄清楚线程切换，只要搞清楚了第一篇文章，线程切换无非就是多了些 “数据源-观察者” 对。

由于线程切换还涉及到线程池相关的东西，所以为了篇幅问题，线程池相关的东西会放到下一篇。



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
        Observable.create<Int>(sourceClown)
                // 指定数据源执行的线程
                .subscribeOn(Schedulers.computation())
                // 指定观察者执行的线程
                .observeOn(Schedulers.io())
                .subscribe(observerBatMan)

    }
```

嗯，和上一篇文章的例子差不多，就多了几行线程切换而已，我们先给出数据的日志。有一个大致的印象，再来深入分析。

```shel
RxComputationThreadPool-1--source
RxCachedThreadScheduler-1--observer
```



有了第一篇的基础，那么我们直接从 subscribeOn 方法入手：

```java
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new ObservableSubscribeOn<T>(this, scheduler));
    }
```

你使用了套路一，得到结果如下：

```java
    public final Observable<T> subscribeOn(Scheduler scheduler) {
        return new ObservableSubscribeOn<T>(this, scheduler);
    }
```

效果拔群，我们继续看 ObservableSubscribeOn。

```java
public final class ObservableSubscribeOn<T> extends AbstractObservableWithUpstream<T, T> {...}

abstract class AbstractObservableWithUpstream<T, U> extends Observable<U> implements HasUpstreamObservableSource<T> {...}
```

所以，ObservableSubscribeOn 还是继承至 Observable的（窃喜一下，看情况我们也可以使用套路二了）。



在第一篇文章中，我们分析的对象是 ObservableCreate，而现在我们分析的对象是 ObservableSubscribeOn。为了能够更加清晰的列出不同点，我还是搞一个表格吧：

|                  | ObservableSubscribeOn                                     | ObservableCreate                         |
| ---------------- | --------------------------------------------------------- | ---------------------------------------- |
| 直接父类         | AbstractObservableWithUpstream（最终还是继承 Observable） | Observable                               |
| 构造方法参数个数 | 两个：(ObservableSource\<T> source, Scheduler scheduler)  | 一个：(ObservableOnSubscribe\<T> source) |

主要的不同点，还是在构造参数这里。

1. 参数个数不同，这个好理解，因为需要切换线程，肯定需要执行切换到哪个线程
2. source 类型不同



看看 source 的不同之处：

```java
public interface ObservableOnSubscribe<T> {
    void subscribe(@NonNull ObservableEmitter<T> e) throws Exception;
}

public interface ObservableSource<T> {
    void subscribe(@NonNull Observer<? super T> observer);
}
```

嗯，除了类名不同之外，就只有参数不同了。

ObservableOnSubscribe 接收的参数 ObservableEmitter，是将 Observer 包装了一层。

ObservableSource 直接接收了 Observer，嗯，很牛逼，后面我们来分析看看，它为啥不要包装，你也可以思考一下，嘿嘿嘿。



经过上面的分析，总的来说，区别不大，所以我们仍然可以套第一篇的套路来分析。

到这里先上个图，为后面做好心理准备。

