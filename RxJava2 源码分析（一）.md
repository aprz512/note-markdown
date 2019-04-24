## RxJava2 源码分析（一）

这是第二次写源码分析，之前的一次已经是一年前了。

为何要重写？

主要是由于今天看到了一些关于线程池的东西，我联想到了RxJava2中的线程分类。再想到项目中的线程池相关的地方，感觉很乱，所以有一个整合的想法，想将原来自己创建的线程池替换成RxJava2中的线程池，于是就有了翻看源码的心思。

择日不如撞日，反正是看源码，顺便把以前的东西再整理一下，还有就是以前写的东西，思路太乱，看着不舒服。

回想起来，RxJava2的源码有很多套路，只要掌握了这个套路，阅读源码起来就会有一切尽在掌握的感觉，否则，就会觉得源码很绕。

所以第一篇文章的主要目的，是讲明白这个套路，然后配上图，能够更容易让人理解，如果以后忘记了，再回来看一遍也能迅速跟上思路，不会又要再次撸一遍源码。



### 从一个简单的例子开始

```kotlin
// 观察者 -- 蝙蝠侠
// 这里之所以没有用 Consumer，是怕引起歧义
// 毕竟源码利用将我们传递进去的 Consumer， 又封装了一层，封装成了 Observer
val observerBatMan = object : Observer<Int> {

    override fun onComplete() {
    }

    override fun onNext(t: Int) {
        Assert.assertEquals(1, t)
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
}

// 开始观察
Observable.create<Int>(sourceClown)
    .subscribe(observerBatMan)
```

嗯，果然 kotlin 还是看起来舒服。

这个例子非常简单了，数据源发送一个int值 1，然后接收者判断值是不是1。



现在开始分析源码了，先看 Observable 的 create 方法：

> Observable.java
>
> 该方法创建一个 Observeable 对象。分析完成之后，你就会发现实际上就是创建了一个 ObservableCreate 对象。

```java
public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
    // 这个是判空，嗯，没啥好说的，我一般用注解。
    ObjectHelper.requireNonNull(source, "source is null");
    return RxJavaPlugins.onAssembly(new ObservableCreate<T>(source));
}
```



#### 套路一

> 别看有些人表面上风风光光，背地里却连只大熊猫都没有。

上面的 create 方法中，看起来有两行代码，感觉做了一些了不得的东西，但是实际上只有半行代码在起主要作用。



第二行代码的前半行：

> RxJavaPlugins.java
>
> 该方法在 onObservableAssembly 不会空的情况下会对 source 做一个变换，否则返回 source。

```java

    public static <T> Observable<T> onAssembly(@NonNull Observable<T> source) {
        Function<? super Observable, ? extends Observable> f = onObservableAssembly;
        if (f != null) {
            return apply(f, source);
        }
        return source;
    }
```

因为 onObservableAssembly 绝大部分情况下为空，其实就是返回了传进来的参数。所以该方法基本可以忽略。

**需要注意，这个套路在源码中很常见。**



所以最后，我们可以把 Observeable 的 create 方法理解为：

> Observable.java
>
> create 简化后的代码

```java
    public static <T> Observable<T> create(@NotNull ObservableOnSubscribe<T> source) {
        return new ObservableCreate<T>(source);
    }
```

这样看是不是很简单！！！



继续深入，看看 ObservableCreate 有何德何能！

> ObservableCreate .java
>
> ObservableCreate 继承至 Observable。

```java
public final class ObservableCreate<T> extends Observable<T> {...}
```

别看 Observable 有**1w 多行代码**，但是实际上**只有一个抽象方法**，其他的都是用来做操作符等等。



下面来看看这个抽象方法，后面会分析到。

> Observable.java
>
> 该方法由 Observable 的 subscribe 方法调用，即 Observable.create(xxx).subscribe(xxx);
>
> subscribe 就会调用 subscribeActual

```java
    protected abstract void subscribeActual(Observer<? super T> observer);
```



#### 套路二

> 遵循模板：
>
> 1. 将source封装一下，变成一个 Observable
>
> 2. 将 observer 封装一下，变成一个Emitter，
>
> 3. 然后调用 source 的 onSubscribe 方法，
>
> 4. 然后调用 source 的 subscribe 方法，将 Emitter 传进去。

**其实只要你知道 observer 是谁，source 是谁，很简单的啦。**



ObservableCreate 的核心代码就在这个被覆盖的抽象方法里面，嗯，一起来看看吧。

> ObservableCreate.java
>
> 该方法由 Observable 的 subscribe 方法调用，即 Observable.create(xxx).subscribe(xxx);

```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        observer.onSubscribe(parent);

        try {
            source.subscribe(parent);
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            parent.onError(ex);
        }
    }
```

看上面的代码，需要搞清楚几个变量，不然绕着绕着就糊涂了。

1. **source 是我们创建并传递进来的**。额，忘记贴构造函数了，里面有赋值，这个 source 就是我们在 create 方法里面创建的对象啦。

   > ObservableCreate.java
   >
   > 构造方法

   ```java
       public ObservableCreate(ObservableOnSubscribe<T> source) {
           this.source = source;
       }
   ```

   ![](rxjava2(2).png)

   

2. observer 这里暂时分析不出来，因为是父类调用了这个方法，所以我们去父类看看

   > Observable.java
   >
   > 这个方法的主要作用，就是将数据源与观察者关联起来
   >
   > 它还调用了 subscribeActual 方法，子类必须实现 subscribeActual  方法。

   ```java
       public final void subscribe(Observer<? super T> observer) {
           ObjectHelper.requireNonNull(observer, "observer is null");
           try {
               observer = RxJavaPlugins.onSubscribe(this, observer);
   
               ObjectHelper.requireNonNull(observer, "Plugin returned null Observer");
   
               subscribeActual(observer);
           } catch (NullPointerException e) { // NOPMD
               throw e;
           } catch (Throwable e) {
               Exceptions.throwIfFatal(e);
               // can't call onError because no way to know if a Disposable has been set or not
               // can't call onSubscribe because the call might have set a Subscription already
               RxJavaPlugins.onError(e);
   
               NullPointerException npe = new NullPointerException("Actually not, but can't throw other exceptions due to RS");
               npe.initCause(e);
               throw npe;
           }
       }
   ```

   

   使用套路一，我们简化一下代码：

   > Observable.java 
   >
   > subscribe 简化后的代码

   ```java
       public final void subscribe(Observer<? super T> observer) {
           try {
               observer = RxJavaPlugins.onSubscribe(this, observer);
               subscribeActual(observer);
           } catch (NullPointerException e) { // NOPMD
               throw e;
           } catch (Throwable e) {
               ...
               RxJavaPlugins.onError(e);
   			...
               throw npe;
           }
       }
   ```

   

   如果，是走正常流程，没有错误，还可以简化（第一次分析主流程，就是要这样简化简化再简化）：

   > Observable.java 
   >
   > subscribe 简化后的代码

   ```java
       public final void subscribe(Observer<? super T> observer) {
           observer = RxJavaPlugins.onSubscribe(this, observer);
           subscribeActual(observer);
       }
   ```

   

   实际上，RxJavaPlugins.onSubscribe 也含有套路一，所以再次简化：

   >Observable.java 
   >
   >subscribe 简化后的代码

   ```java
       public final void subscribe(Observer<? super T> observer) {
           // 可以忽略
           observer = observer;
           subscribeActual(observer);
       }
   ```

   所以，最终实际上 subscribe 方法，就是调用了 subscribeActual 方法而已，只不过它增加了错误与钩子处理。

   

   看到这里，**不知道你有没有反应过来，这个 subscribe(observer) 方法是不是很熟悉呢**？

   这个方法，就是我们上面例子中的：

   > 我们写的 demo 的代码

   ```kotlin
   // 开始观察
   Observable.create<Int>(sourceClown)
   	// 这里就是调用的 subscribe 方法
       .subscribe(observerBatMan)
   ```

   是不是有点恍然大悟的感觉呢！

   

   所以到这里，心里应该由一个大致框架了。

   

   同时也会发现，`ObservableCreate `  的 `subscribeActual` 方法中**的 `observer` 参数，也是我们new出来的对象**。

   ![](rxjava2(3).png)



分析到了这里，一个轮廓就出来了!!!

`ObservableCreate `  的 `subscribeActual` 方法中的 参数分别对应如下：

> ObservableCreate.java
>
> ObservableCreate 继承至 Observable，所以它必须实现 subscribeActual 方法。
>
> 这个方法也是核心，是链式调用的核心，线程切换的核心

```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        // observer 就是 observerBatMan
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        observer.onSubscribe(parent);

        try {
            // source 就是 sourceClown
            // 这个 subscribe 就将两个包装的观察者与数据源对象关联起来了
            source.subscribe(parent);
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            parent.onError(ex);
        }
    }
```



如果，不考虑错误的情况，我们简化一下代码：

> ObservableCreate.java
>
> subscribeActual 简化后的代码

```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        // observer 就是 observerBatMan
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        // 调用 observerBatMan 的 onSubscribe 方法，这个是一个钩子方法
        // 一般专门用来告诉 observerBatMan，我，sourceClown，要搞事情了
        observer.onSubscribe(parent);
        // source 就是 sourceClown
        source.subscribe(parent);
    }
```



由于，onSubscribe 我们暂时也不用，所以去掉，再简化：

>ObservableCreate.java
>
>subscribeActual 简化后的代码

```java
    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        CreateEmitter<T> parent = new CreateEmitter<T>(observer);
        // 注意这里 subscribe 传递的是 parent
        source.subscribe(parent);
    }
```

嘿嘿嘿，这样就舒服多了，就3个变量，两个是我们自己创建的，知根知底，还有一个货，`CreateEmitter`我们先放一放，为啥呢，因为关于 source 的代码还没有分析完成呢。



别看  `source.subscribe(parent);`就一行代码，但是由于 source 对象是我们自己创建的，所以这个方法实际上调用了我们写的代码：

> ObservableOnSubscribe.java

```java
public interface ObservableOnSubscribe<T> {
    void subscribe(@NonNull ObservableEmitter<T> e) throws Exception;
}
```



ObservableOnSubscribe`是一个接口，所以，我们实际上是创建了一个匿名内部类，传递给了 source，然后 source 又调用了 subscribe 方法，所以也就调用了我们写的代码：

> 我们自己写的 demo 代码

```java
// 这里的 it 是 ObservableEmitter
it.onNext(1)
it.onComplete()
```



那么，当 it.onNext(1) 执行之后，又会发生什么呢？

这个 it 就是 CreateEmitter，嗯，虽然有点突然，但是这个应该没有疑问吧？！！

> 1. 我们把 sourceClown 传进去，并且调用了 ObservableEmitter 的 onNext 等方法
> 2. sourceClown 被封装成了 CreateEmitter
> 3. source 的 subscribe 方法接收的是 CreateEmitter，
>
> 所以，ObservableEmitter 在运行时就是 CreateEmitter 对象。

我们先不忙着去看它的 onNext 方法，先看看这个类。



#### 套路三

> 由老父亲来替你打理一切

我们知道在**套路二**里面，我们传递的 sourceClown 被封装了一下，变成了一个 `CreateEmitter` 。

`CreateEmitter` 这个变量名就很叼，一看就是 observer 的老父亲，那么，可以先猜一猜，为啥它要起这样一个名呢？



由于`ObservableOnSubscribe` 的 `subscribe`方法只接受 `ObservableEmitter` ，所以  `CreateEmitter`  必须要实现这个接口。

好，我们看源代码：

> CreateEmitter.java

```java
    static final class CreateEmitter<T>
    extends AtomicReference<Disposable>
    implements ObservableEmitter<T>, Disposable {...}
```

`AtomicReference`是java类，就不展开讲了，不知道的人（比如我）这个时候应该打开了文档，开始学习了。



继续看构造方法：

> CreateEmitter.java

```java
        CreateEmitter(Observer<? super T> observer) {
            this.observer = observer;
        }
```

嗯，很好，observer 被保存起来了。



由于，在 observerClown 中我们调用了：

> 我们写的 demo 的代码

```java
it.onNext(1)
```

所以，它的 onNext 方法会被调用。



现在，我们来分析它的 onNext 方法：

> CreateEmitter.java

```java
        @Override
        public void onNext(T t) {
            if (t == null) {
                onError(new NullPointerException("onNext called with null. Null values are generally not allowed in 2.x operators and sources."));
                return;
            }
            if (!isDisposed()) {
                observer.onNext(t);
            }
        }
```

RxJava2 中不允许数据源发射的数据为 null，所以我们简化一下：

> CreateEmitter.java
>
> onNext 简化后的代码

```java
        @Override
        public void onNext(T t) {
            if (!isDisposed()) {
                observer.onNext(t);
            }
        }
```

isDisposed 方法，就是判断观察者有没有解除订阅，毕竟，蝙蝠侠也会心累，带不动，带不动。

这上面做了这么多判断，现在知道为啥起名叫 parent 了不？



在我们的例子中，我们没有解除订阅，再简化一下，就是：

> CreateEmitter.java
>
> onNext 简化后的代码

```java
        @Override
        public void onNext(T t) {
            observer.onNext(t);
        }
```

这下，够直白了吧，直接调用了 observer 的 onNext 方法。



还记得 observer 是谁吗，就是你，蝙蝠侠，observerBatMan。所以它的 onNext 方法会被调用。

> 我们写的 demo 的代码

```java
override fun onNext(t: Int) {
    Assert.assertEquals(1, t)
}
```

那么，整个流程就跑通了。

至于，onComplete 方法，差不多的啦。

最后上一张图：

![](rxjava2(1).png)