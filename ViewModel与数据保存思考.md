# ViewModel 与数据保存思考

今天在做一个需求的时候，为了用户的体验，我想在进程被回收的时候保存一下数据，等 activity 重新创建的时候再取出来。

我之前看过一些文章，关于 ViewModel 的，说是 ViewModel 在屏幕方向发生变化的时候不用重新储存数据（与版本有关系）。我也是看了相关代码，确实是这样。

我们可以看看 ViewModel 是如何创建与保存的。

当我们调用 `ViewModelProviders.of(this).get(xxx.class); `来获取（创建一个）viewModel 的时候：

> android.arch.lifecycle.ViewModelProvider#get(java.lang.String, java.lang.Class<T>)

```java
    public <T extends ViewModel> T get(@NonNull String key, @NonNull Class<T> modelClass) {
        ViewModel viewModel = mViewModelStore.get(key);

        if (modelClass.isInstance(viewModel)) {
            //noinspection unchecked
            return (T) viewModel;
        } else {
            //noinspection StatementWithEmptyBody
            if (viewModel != null) {
                // TODO: log a warning.
            }
        }

        viewModel = mFactory.create(modelClass);
        mViewModelStore.put(key, viewModel);
        //noinspection unchecked
        return (T) viewModel;
    }
```

可以看到这个方法内部实际上是缓存了 ViewModel 对象的，所以你多次调用get方法，不会创建多个。再看看 key 值：

```java
DEFAULT_KEY + ":" + canonicalName
```

可以看到 key 只与 class 有关，所以只要 class 一样，就会获取到同一个对象。

那么，即使屏幕旋转之后，activity创建了新的对象，但是它的 class 是不会变的，所以仍然可以引用到同一个 ViewModel。



嗯，上面是有关activity方向的处理，但是我的需求是内存不足被回收，如果进程都被回收掉了，堆里面的对象就也都被回收了，ViewModel 对象也就不存在了，也就是说，ViewModel 无法满足我的需求。

但是我又想到了一件事，ViewModel 之所以能感知宿主的声明周期，是因为它内部创建了 `android.arch.lifecycle.HolderFragment` 对象（不了解原理的可以去看 Glide 源码）。而这个对象的构造方法里面有这样的一行代码：

> android.arch.lifecycle.HolderFragment#HolderFragment

```java
setRetainInstance(true);
```

我以前一直对这个方法有误解，没有理解它的真正作用，现在来看看为什么写这个方法：

调用了这个方法的fragment不会随着activity一起被销毁。相反，它会一直保留(**进程不消亡的前提下**)，并在需要时原封不动地传递给新的Activity。

当设备配置发生变化时，FragmentManager首先销毁队列中fragment的视图（因为可能有更合适的匹配资源）； 
紧接着，FragmentManager将检查每个fragment的retainInstance属性值。

如果retainInstance属性值为false，FragmentManager会立即销毁该fragment实例。 随后，为适应新的设备配置，新的Activity的新的FragmentManager会创建一个新的fragment及其视图。

如果retainInstance属性值为true，则**该fragment的视图立即被销毁，但fragment本身不会被销毁**。 为适应新的设备配置，当新的Activity创建后，新的FragmentManager会找到被保留的fragment，并重新创建其试图。

虽然保留的fragment没有被销毁，但它已脱离消亡中的activity并处于保留状态。 尽管此时的fragment仍然存在，但已经没有任何activity托管它。

只有调用了fragment的setRetainInstance(true)方法， 并且因设备配置改变，托管Activity正在被销毁的条件下， 
fragment才会短暂的处于保留状态。

**如果activity是因操作系统需要回收内存而被销毁，则所有的fragment也会随之销毁**。

理解了上面的话，现在想一下，为何要保存 fragment 的实例？是因为 

> android.arch.lifecycle.HolderFragment

```java
private ViewModelStore mViewModelStore = new ViewModelStore();
```

它有这样的一个变量，而这个 ViewModelStore 实际上就是一个 map，它保存它对应的 ViewMode 对象。如果 fragment被回收了，那么这个 ViewModel 肯定也会被回收（没有其他地方引用它了），这样ViewModel就达不到设计的目的了。



所以最终，还是要配合 onSaveInstanceState 来保存数据，因为 onSaveInstanceState 是将数据存到了系统进程中。