## RxJava2 源码分析（五）

### 目的

分析Flowable的相关源码，了解一下背压的知识。



### 从例子开始

> demo

```kotlin
        Flowable
                .create<Int>({
                    it.onNext(1)
                    it.onNext(2)
                    it.onComplete()
                }, BackpressureStrategy.BUFFER)
                .subscribe {
                    System.out.println(it)
                }
```

看看 create 方法创建了一个什么对象：

#### Flowable

> io.reactivex.Flowable#create

```java
    public static <T> Flowable<T> create(FlowableOnSubscribe<T> source, BackpressureStrategy mode) {
        ObjectHelper.requireNonNull(source, "source is null");
        ObjectHelper.requireNonNull(mode, "mode is null");
        return RxJavaPlugins.onAssembly(new FlowableCreate<T>(source, mode));
    }
```

创建了一个 FlowableCreate 对象，由于 Flowable 与 Observerable 差不多，所以下面我们按照分析 Observerable 的思路来分析 Flowable。



####FlowableCreate

> 类结构

```
public final class FlowableCreate<T> extends Flowable<T> {...}
```

这个没啥说的，与 Observerable 一样。



> 构造函数

```java
    public FlowableCreate(FlowableOnSubscribe<T> source, BackpressureStrategy backpressure) {
        this.source = source;
        this.backpressure = backpressure;
    }
```

也与 Observerable 差不多，就是两套代码吧。一个以 Observerable  开头，一个以 Flowable 开头，嗯，现在我对源码的探究就只到了这个程度，可能还有别的不同，暂时还未发现。



> subscribeActual 方法
>
> demo 中 backpressure 我们传递的是 BackpressureStrategy.BUFFER，所以 emitter 的值是 BufferAsyncEmitter。

```java
    @Override
    public void subscribeActual(Subscriber<? super T> t) {
        BaseEmitter<T> emitter;

        switch (backpressure) {
        case MISSING: {
            emitter = new MissingEmitter<T>(t);
            break;
        }
        case ERROR: {
            emitter = new ErrorAsyncEmitter<T>(t);
            break;
        }
        case DROP: {
            emitter = new DropAsyncEmitter<T>(t);
            break;
        }
        case LATEST: {
            emitter = new LatestAsyncEmitter<T>(t);
            break;
        }
        default: {
            // 走这里
            emitter = new BufferAsyncEmitter<T>(t, bufferSize());
            break;
        }
        }

        // 下面的就不分析了，参考前面的文章
        t.onSubscribe(emitter);
        try {
            source.subscribe(emitter);
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            emitter.onError(ex);
        }
    }

```

所以，我们只需要分析 BufferAsyncEmitter 这个类就好了。



####FlowableCreate.BufferAsyncEmitter

> 类结构
>
> 这个类是 FlowableCreate 的内部类，可以想到其他的 Emitter 应该也是内部类
>
> 继承了 BaseEmitter

```java
    static final class BufferAsyncEmitter<T> extends BaseEmitter<T> {...}
```



####FlowableCreate.BaseEmitter

> 类结构
>
> 注意这个类继承至 AtomicLong

```java
    abstract static class BaseEmitter<T>
    extends AtomicLong
    implements FlowableEmitter<T>, Subscription {...}
```

这个类实现了一些通用的方法，比如 onError，onComplete，cancel等等。

其中只有一个方法需要看看：

> BaseEmitter#request

```java
        @Override
        public final void request(long n) {
            // 判断 n 是不是正数
            if (SubscriptionHelper.validate(n)) {
                // 是正数，将 n 设置给自己
                // 上面有说，这个类继承了 AtomicLong，所以它可以持有一个 Long 型的引用
                BackpressureHelper.add(this, n);
                // 钩子方法
                onRequested();
            }
        }
```

好了，到这里 BaseEmitter 的方法就分析的差不多了，这个类只是提供了一些默认的实现方法，没有别的逻辑，把它当成一个普通的父类就好了。



####FlowableCreate.BufferAsyncEmitter

再回到 BufferAsyncEmitter 里面，看构造函数

> 构造函数

```java
        BufferAsyncEmitter(Subscriber<? super T> actual, int capacityHint) {
            super(actual);
            // 这里有一个新类
            this.queue = new SpscLinkedArrayQueue<T>(capacityHint);
            this.wip = new AtomicInteger();
        }
```

SpscLinkedArrayQueue 这个类不展开介绍了，贴一段注释就明白了。

```
A single-producer single-consumer array-backed queue which can allocate new arrays in case the consumer is slowerthan the producer.

一个单生产者单消费者数组支持的队列，它可以在消费者比生产者慢的情况下分配新的数组（自动增长）。
```

机翻都能看的懂吧，源码就是实现了这样功能的队列，有兴趣的可以看看。

我们把 SpscLinkedArrayQueue 当成一个队列就好。注意这里的初始容量是 128，但是会自动增长。



看完构造方法，再看 onNext 方法。

> onNext

```java
        @Override
        public void onNext(T t) {
            // isCancelled 是父类的方法
            // 当我们调用 setDisposable 方法的时候，父类就会保存这个 Disposable 的引用到 serial 字段
            // 如果该 Disposable 调用了 dispose 方法，那么 isCancelled 会返回 true
            // 调用父类的 cancel 方法，isCancelled 也会返回 true（内部调用了 serial.dispose()）
            // 数据发送完毕或者发生错误，isCancelled 也会返回 true（内部调用了 serial.dispose()）
            // done 只有数据发生完毕或者发生错误才会为true
            if (done || isCancelled()) {
                return;
            }

            // 不允许发送的数据为null
            if (t == null) {
                onError(new NullPointerException("onNext called with null. Null values are generally not allowed in 2.x operators and sources."));
                return;
            }
            // 先将数据添加到队列里面
            queue.offer(t);
            // 调用 drain 方法
            drain();
        }
```

看来，onNext 的主要目的，就是先将数据加入到队列里面，然后调用 drain 方法。继续看 drain 方法。

> drain 
>
> 这个方法有点长，变量名也有点蛋疼，但是慢慢分析，不难
>
> 在阅读源码的时候，一定要有耐心。阅读源码的过程其实是一个提升自己理解力的过程。
>
> 既然你需要阅读别人的源代码，那么你的理解力肯定是处于弱势的，不然的话，你一看就懂，那就说明源码的东西在作者写出来之前你都已经掌握了，那么阅读源码对自己没有任何好处。
>
> 所以，阅读源码的过程，就是需要慢慢提升自己的理解力，来达到与作者的理解力一样的程度。

```java
        void drain() {
            // wip 这个变量到后面分析，因为涉及到别的方法，单看这一个方法看不出什么东西来
            if (wip.getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            // 这两个变量名有点吊
            final Subscriber<? super T> a = actual;
            final SpscLinkedArrayQueue<T> q = queue;

            for (;;) {
                // 这里，获取设置的引用值，因为这个类是继承的 AtomicLong
                // 前面我们分析过，只有 request 方法才会设置引用值
                // 至于哪里调用了 request 方法，我们后面分析
                // 这里将这个值当作 Long.MAX_VALUE
                long r = get();
                long e = 0L;

                // 进入循环
                while (e != r) {
                    // 判断是否取消了
                    if (isCancelled()) {
                        // 取消了就清空队列
                        q.clear();
                        return;
                    }

                    // 时候发送完毕了，或者出现了错误
                    boolean d = done;

                    // 从队列中取出一个数据
                    T o = q.poll();

                    // 队列是否为空
                    boolean empty = o == null;

                    // 数据发送完毕了，并且队列为空
                    if (d && empty) {
                        Throwable ex = error;
                        if (ex != null) {
                            // 调用父类 error 方法，会调用 onError
                            error(ex);
                        } else {
                            // 调用父类 complete 方法，会调用 onComplete
                            complete();
                        }
                        return;
                    }

                    // 队列为空，则跳出循环
                    if (empty) {
                        break;
                    }

                    // 调用 onNext 方法，o 是从队列取出来的数据
                    a.onNext(o);

                    // e 的值累加
                    e++;
                }

                // 这里要判断一下 e 与 r 是否相等，
                // 因为如果队列为空的话，e 是不等于 r 的
                // 相等的话，说明请求的个数刚好等于发送的个数，做一下收尾工作就好了
                if (e == r) {
                    // 下面的一段与上面的很相似，不一句一句写了
                    if (isCancelled()) {
                        q.clear();
                        return;
                    }

                    boolean d = done;

                    boolean empty = q.isEmpty();

                    if (d && empty) {
                        Throwable ex = error;
                        if (ex != null) {
                            error(ex);
                        } else {
                            complete();
                        }
                        return;
                    }
                }

                if (e != 0) {
                    // 因为发送了 e 个数据，所以将引用值更新
                    // 内部就是将引用自减少了 e
                    BackpressureHelper.produced(this, e);
                }

                // 这个后面分析
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
```

drain 的主要作用，就是当我们调用 request(num) 请求 num 个数据的时候，这个方法会从队列中取出 num 个数据出来给我们。

可以思考一下，如果我们没有调用，request 方法，会怎么样？

> drain
>
> 简化后的代码

```java
        void drain() {

            ...
            for (;;) {
                long r = get();
                long e = 0L;

                // 没有调用 request，导致 r 为 0，这里进不去
                while (e != r) {
                    ...
                }

                // 相等
                if (e == r) {
                    // 没有取消
                    if (isCancelled()) {
                        ...
                    }

                    boolean d = done;

                    // 队列不为空
                    boolean empty = q.isEmpty();

                    // 进不去
                    if (d && empty) {
                        ...
                    }
                }

                // 进不去
                if (e != 0) {
                    ...
                }

                missed = wip.addAndGet(-missed);
                // 满足，跳出循环
                if (missed == 0) {
                    break;
                }
            }
        }
```

可以看到，如果我们没有调用 request 方法，虽然 onNext 方法被调用了，将数据加入到了队列中，但是却无法从队列中取出数据，即下游的观察者收不到任何数据。



还有两个遗留的问题：

1. wip 变量的作用？
2. request 方法在哪里调用的？

先看第一个问题：

> drain

```java
        void drain() {
            // 将值加 1，返回未加 1 之前的值
            // 就是判断 wip 的引用值是否是0，然后加1
            if (wip.getAndIncrement() != 0) {
                return;
            }

            // 到了这里 wip 引用的值理论上为1
            // 但是多线程的情况下， onUnsubscribed 会将 wip 加 1
            int missed = 1;
            ...
	
            for (;;) {
                ...
                    
                // 将 wip 的值减 - missed
                // 这里处在一个循环中，最终 wip 的值会变成 0，missed 也会变成0，跳出循环
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
    }
```

> onUnsubscribed

```java
        @Override
        void onUnsubscribed() {
            // 取消的时候，wip 的值为 0 的话，清空队列
            // 就是说，此时，drain 方法运行到了 for 循环的最后几行
            // 这里应该是为了保证，数据从队列里面取出来，一定要发送出去才行
            if (wip.getAndIncrement() == 0) {
                queue.clear();
            }
        }
```

在 BufferAsyncEmitter 这个类中，使用到 wip 这个变量的，只有上面这几个地方。

onUnsubscribed 会在父类的 cancel 方法中调用，所以我们调用 cancel 就会改变 wip 的值：

#### FlowableCreate.BaseEmitter

> cancel

```java
        @Override
        public final void cancel() {
            serial.dispose();
            onUnsubscribed();
        }
```



好了，第一个问题说完了，我们来看看第2个问题：

首先我们回到 demo 中，既然 create 方法中没有调用 request 的地方，那么我们只能从 subscribe 方法入手了。

####Flowable

> io.reactivex.Flowable#subscribe(io.reactivex.functions.Consumer<? super T>)

```
public final Disposable subscribe(Consumer<? super T> onNext) {
    return subscribe(onNext, Functions.ON_ERROR_MISSING,
            Functions.EMPTY_ACTION, FlowableInternalHelper.RequestMax.INSTANCE);
}
```

它调用了名方法：

> io.reactivex.Flowable#subscribe(io.reactivex.functions.Consumer<? super T>, io.reactivex.functions.Consumer<? super java.lang.Throwable>, io.reactivex.functions.Action, io.reactivex.functions.Consumer<? super org.reactivestreams.Subscription>)

```java
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<? super Throwable> onError,
            Action onComplete, Consumer<? super Subscription> onSubscribe) {
        ObjectHelper.requireNonNull(onNext, "onNext is null");
        ObjectHelper.requireNonNull(onError, "onError is null");
        ObjectHelper.requireNonNull(onComplete, "onComplete is null");
        ObjectHelper.requireNonNull(onSubscribe, "onSubscribe is null");

        LambdaSubscriber<T> ls = new LambdaSubscriber<T>(onNext, onError, onComplete, onSubscribe);

        subscribe(ls);

        return ls;
    }
```

创建了一个 LambdaSubscriber 对象，并调用了 subscribe 方法，将 ls 传递了进去。

所以这个方法其实就是将我们的 consumer 包装成了 LambdaSubscriber，然后调用真正的 subscribe 方法。





#### LambdaSubscriber

看看 LambdaSubscriber 类：

> 构造方法

```java
    public LambdaSubscriber(Consumer<? super T> onNext, Consumer<? super Throwable> onError,
            Action onComplete,
            Consumer<? super Subscription> onSubscribe) {
        super();
        this.onNext = onNext;
        this.onError = onError;
        this.onComplete = onComplete;
        this.onSubscribe = onSubscribe;
    }
```

保存了一些变量，onSubscribe 值得注意，因为这个 Consumer 在订阅的时候会回调，是调用 request 的最好时机。

直接看 onSubscribe 的实现。

> onSubscribe

```java
    @Override
    public void onSubscribe(Subscription s) {
        if (SubscriptionHelper.setOnce(this, s)) {
            try {
                onSubscribe.accept(this);
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                s.cancel();
                onError(ex);
            }
        }
    }
```

这里调用了 onSubscribe 对象的 accept 方法。我们看看这个对象的实现吧。



#### FlowableInternalHelper.RequestMax.INSTANCE

从前面的代码中（Flowable.subscribe()方法中），我们知道 onSubscribe 对象是 FlowableInternalHelper.RequestMax.INSTANCE。所以看看这个类的代码吧。

> FlowableInternalHelper.RequestMax.INSTANCE

```java
    public enum RequestMax implements Consumer<Subscription> {
        INSTANCE;
        @Override
        public void accept(Subscription t) throws Exception {
            t.request(Long.MAX_VALUE);
        }
    }
```

嗯，果然是这里调用了 request 方法。



到这里，Flowable 的分析就告一段落。