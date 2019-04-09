## Android JetPack 学习笔记



### VectorDrawable

Android API 21（5.0）引入了一个Drawable的子类**VectorDrawable**目的就是用来渲染矢量图。

在小于API 21的手机上运行需要配置：

```groovy
defaultConfig {
    vectorDrawables.useSupportLibrary true
}
```

在布局中使用`app:srcCompat`标签，需要使用`activity`继承于`AppCompatActivity`。

```xml
app:srcCompat="@drawable/empty_dice"
```

```kotlin
class MainActivity : AppCompatActivity() {}
```



### Navigation Component

一些属性说明。

```xm
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    app:startDestination="@[+]id/name">
    
     <fragment
            android:id="@+id/name"
            android:name="[package:].name"
            android:label="String"
            tools:layout="@layout/layout_name" 
            <action
                android:id="@+id/name"
                app:destination="@id/name" 
                app:launchSingleTop="[true|false]"
                app:launchDocument="[true|false]"
                app:clearTask="[true|false]"
                app:popUpTo="@id/name"
                app:popUpToInclusive="[true|false]"
                app:enterAnim="@anim/slide_in_right"
                app:exitAnim="@anim/slide_out_left"
                app:popEnterAnim="@anim/slide_in_left"
                app:popExitAnim="@anim/slide_out_right"/>
     </fragment>

</navigation>
```

#### `<navigation>`

```xml
app:startDestination="@+id/titleFragment"
```

属性:
`app:startDestination="@[+]id/name"`
这就是代表一启动显示哪个元素，我这里使用的是fragment,所以后面的id填写的就是下面某个fragment的id,这样默认就会先显示这个fragment。



#### `<fragment>`

```xml
android:id="@+id/titleFragment"
```

属性:
`android:id`
id就是给这fragment取的唯一标识



#### `<action>`

```xml
app:destination="@id/gameFragment"
```

`app:destination`
 这是这个action要跳转的目的地，需要填写上目的地的id。每一次跳转，会产生一个新的实例(Fragment1@01>Fragment1@02)，**这时Fragment1@01实例不会被销毁，只执行到onFragmentViewDestroyed**



```xml
app:launchSingleTop="[true|false]"
```

`app:launchSingleTop`
 这是在跳转本身时保存单实例，就是说F1跳转F1，虽然会产生一个新的实例（`Fragment1@01>Fragment1@02`），但Fragment1@01实例会被销毁。`Fragment1@01>Fragment2@02>Fragment1@03`，这样的跳转不会起作用，Fragment1@01实例依旧存在。



```xml
app:clearTask="[true|false]"
```

`app:clearTask`
 为true会清空栈中的元素。`Fragment1@01>Fragment2@02>Fragment3@03`，
 这时Fragment3启动Fragment4的action设置这标记为true(Fragment3@03>Fragment4@04),此时Fragment1、Fragment2、Fragment3实例统统销毁，栈中只存Fragment4@04。官方不推荐，推荐使用`app:popUpTo="@id/name" app:popUpToInclusive="[true|false]"`这两属性组合。



```xml
app:popUpTo="@+id/gameFragment"
```

`app:popUpTo`
 这是出栈直到某个元素。`Fragment1@01>Fragment2@02>Fragment3@03`，在Fragment3启动Fragment4时设置出栈到Fragment1，那栈中的Fragment2，Fragment3会出栈销毁，只存Fragment1和Fragment4。



```xml
app:popUpToInclusive="true"
```

`app:popUpToInclusive`
 这属性配合`app:popUpTo`使用，用来判断到达指定元素时是否把指定元素也出栈。同样上面的例子，true的话Fragment1也会出栈销毁，栈中只存留Fragment4。



学习项目的时候，翻看了一下源码，发现 `navigation-fragment` 的跳转实现仍然是使用的 replace 等方法。

比如，我们通常需要指定一个 `NavHostFragment`，然后给它指定一个导航图（navigation.xml），查看 `NavHostFragment ` 的源码：

```kotlin
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = new FrameLayout(inflater.getContext());
        // When added via XML, this has no effect (since this FrameLayout is given the ID
        // automatically), but this ensures that the View exists as part of this Fragment's View
        // hierarchy in cases where the NavHostFragment is added programmatically as is required
        // for child fragment transactions
        frameLayout.setId(getId());
        return frameLayout;
    }

```

看这个里面啥都没有，就是创建了一个 FrameLayout 作为根布局，根布局的 id 设置为 `getId()`的值，这个很重要，之后会用到。

然后我们导航的时候，会调用：

```kotlin
Navigation.createNavigateOnClickListener(R.id.action_titleFragment_to_gameFragment)
```

这个其实是一种变种写法，点进去看看就会发现，还是调用了 findNavController().navigate(id)。

主要看看 navigate 方法，是如何切换 fragment 的，其实要我们自己做也是使用 replace，add 等方案：

FragmentNavigator.java

```kotlin
    /**
     * {@inheritDoc}
     * <p>
     * This method should always call
     * {@link FragmentTransaction#setPrimaryNavigationFragment(Fragment)}
     * so that the Fragment associated with the new destination can be retrieved with
     * {@link FragmentManager#getPrimaryNavigationFragment()}.
     * <p>
     * Note that the default implementation commits the new Fragment
     * asynchronously, so the new Fragment is not instantly available
     * after this call completes.
     */
    @Nullable
    @Override
    public NavDestination navigate(@NonNull Destination destination, @Nullable Bundle args,
            @Nullable NavOptions navOptions, @Nullable Navigator.Extras navigatorExtras) {
        ...
        final Fragment frag = instantiateFragment(mContext, mFragmentManager,
                className, args);
        frag.setArguments(args);
        final FragmentTransaction ft = mFragmentManager.beginTransaction();

        ...

        ft.replace(mContainerId, frag);
        
        ...
        ft.commit();
        // The commit succeeded, update our view of the world
        if (isAdded) {
            mBackStack.add(destId);
            return destination;
        } else {
            return null;
        }
    }
```

这里面我省略了大量的逻辑，有些还很重要（会退栈的处理等），可以自行查看，这里我们只要明白，fragment的切换其实原理是使用了 replace。那么接下来的问题就是 replace 的参数 id，是对应的哪里。其实就是我们上面提到过的 `getId()` 的值，也就是说将`NavHostFragment`替换成需要切换的Fragment。



### MediatorLiveData

Mediator 让我想起了中介者模式，虽然我还不知道中介者模式是什么样的。

在学习 GoogleSamples 的时候遇到了这个类，读了一些注释和源码，大致搞清楚了它是做什么的。

作用：

​	**它是 LiveData 的子类，可以用来观察其他 LiveData 对象，被观察对象发送事件时，它的 `onChanged` 方法会做出响应。**

例子一：

假设我们有两个 LiveData 实例，一个叫 LiveData1，一个叫 LiveData2，我们想将他们的发送事件合并到一个对象中（我们叫它 LiveDataMerger）。合并后，LiveData1 与 LiveData2 就成了LiveDataMerger的事件源。每当 LiveData1 或者 LiveData2 发送事件时，LiveDataMerger 的 onChanged 方法就会被调用：

```java
LiveData<Integer> liveData1 = ...;
LiveData<Integer> liveData2 = ...;

MediatorLiveData<Integer> liveDataMerger = new MediatorLiveData<>();
liveDataMerger.addSource(liveData1, value -> liveDataMerger.setValue(value));
liveDataMerger.addSource(liveData2, value -> liveDataMerger.setValue(value));
```

例子二：

假设我们只想要 LiveData1 的前 10 个事件，接受了 10 个事件之后我们需要停止监听：

```java
liveDataMerger.addSource(liveData1, new Observer<Integer>() {
    private int count = 1;

    @Override 
    public void onChanged(@Nullable Integer s) {
         count++;
         liveDataMerger.setValue(s);
         if (count > 10) {
             liveDataMerger.removeSource(liveData1);
         }
     }
});
```

上面的两个例子，是类注释上的例子，应该可以说明很多事情，但是我们在使用时，会碰到其他方法。

比如 BasicSample 里面，是这样使用的：

```java
    private MediatorLiveData<List<ProductEntity>> mObservableProducts;

    private fun1() {
        mObservableProducts.addSource(mDatabase.productDao().loadAllProducts(),
                productEntities -> {
                    if (mDatabase.getDatabaseCreated().getValue() != null) {
                        mObservableProducts.postValue(productEntities);
                    }
                });
    }
```

我们可以看出，它是对发送的值做了判空处理，但是它重新设置值的时候，使用的是 `postValue`方法，而不是 `setValue` 方法。

那么，这两个方法之间有何不同呢？

其实，可以参考 View 的 invalidate 方法与 postInvalidate 方法之间的区别。这是一个很重要的点，但是除了这个点之外，还有其他的需要注意。

我们先来看 postValue 方法：

```java
    /**
     * Posts a task to a main thread to set the given value. So if you have a following code
     * executed in the main thread:
     * <pre class="prettyprint">
     * liveData.postValue("a");
     * liveData.setValue("b");
     * </pre>
     * The value "b" would be set at first and later the main thread would override it with
     * the value "a".
     * <p>
     * If you called this method multiple times before a main thread executed a posted task, only
     * the last value would be dispatched.
     *
     * @param value The new value
     */
    protected void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            postTask = mPendingData == NOT_SET;
            mPendingData = value;
        }
        if (!postTask) {
            return;
        }
        ArchTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
    }
```

注释上说了比较清楚了，如果我们连续调用：

```java
liveData.postValue("a");
liveData.setValue("b");
```

value 的值先是等于a，然后会被 b 给覆盖掉。

举个例子吧：

```java
        MutableLiveData<Integer> mutableLiveData = new MutableLiveData<>();
        mutableLiveData.observe(this, integer -> {
            Log.e(TAG, "onCreate: " + integer);
        });
        mutableLiveData.setValue(1);
        Log.e(TAG, "onCreate: ---" + mutableLiveData.getValue());
        mutableLiveData.postValue(2);
        Log.e(TAG, "onCreate: ---" + mutableLiveData.getValue());
```

输出log如下：

```java
2019-03-07 10:57:28.412 29654-29654/? E/MainActivity: onCreate: ---1
2019-03-07 10:57:28.412 29654-29654/? E/MainActivity: onCreate: ---1
2019-03-07 10:57:28.470 29654-29654/? E/MainActivity: onCreate: 1
2019-03-07 10:57:28.490 29654-29654/? E/MainActivity: onCreate: 2
```

这里之所以会输出两个1，是因为 post 的 Runnable 还没有被主线程执行。

如果我们多次调用：

```java
liveData.postValue("a");
```

那么，当主线程执行 runnable 的时候，在这个 runnable 之前 post 的值都会被更新，只有这个 runnable 的值会被分发出去。

那么为啥会这样呢，查看源代码：

```java
    private final Runnable mPostValueRunnable = new Runnable() {
        @Override
        public void run() {
            Object newValue;
            synchronized (mDataLock) {
                newValue = mPendingData;
                mPendingData = NOT_SET;
            }
            //noinspection unchecked
            setValue((T) newValue);
        }
    };
```

这里可以看到，内部其实调用了 setValue，而传递的值，是 mPendingData，那么 mPendingData 是在哪里赋值的呢，查看上面 postValue 的方法可以发现：

```java
            mPendingData = value;
```

所以，每次调用 postValue 方法，都会更新这个值，这样就导致最后一次调用 postValue 的时候，会将之前所有的 Runnable 的值更新（因为使用的是成员变量），所以才会出现只有``最后设置``的才会生效的现象。



### Transformations.switchMap

对输入源的每一个元素都执行 `switchMapFunction`函数变换，返回一个 LiveData。这与 RxJava 中的 flatMap 类似。

其实 ``map` 与 `switchMap` 作用差不多。



### BoundaryCallback

当本地存储缓存了网络数据时，我们通常会搭建这样的一个数据流：

网络数据被分页到数据库中，数据库数据被分页到UI中。我们可以使用 LiveData\<PageList> 将数据分页到UI中，但是我们需要知道什么时候该要去请求网络数据。

`BoundaryCallback` 这个接口就是用来做这件事的。

```java
    @MainThread
    public abstract static class BoundaryCallback<T> {
        /**
         * Called when zero items are returned from an initial load of the PagedList's data source. 一开始本地存储没有数据，需要直接去网络中请求
         */
        public void onZeroItemsLoaded() {}

        /**
         * Called when the item at the front of the PagedList has been loaded, and access has
         * occurred within {@link Config#prefetchDistance} of it.
         * <p>
         * No more data will be prepended to the PagedList before this item.
         *
         * @param itemAtFront The first item of PagedList
         */
        public void onItemAtFrontLoaded(@NonNull T itemAtFront) {}

        /**
         * Called when the item at the end of the PagedList has been loaded, and access has
         * occurred within {@link Config#prefetchDistance} of it.
         * <p>
         * No more data will be appended to the PagedList after this item.
         *
         * @param itemAtEnd The first item of PagedList
         */
        public void onItemAtEndLoaded(@NonNull T itemAtEnd) {}
    }
```

`onItemAtFrontLoaded` 与 `onItemAtEndLoaded` 是对应的，一个到达了数据源的最前面，一个到达了数据源的最后面，此时，都需要去加载网络数据。当然，一般情况下 `onItemAtFrontLoaded` 不需要处理，除非你有一个双向列表。

由于，该接口的回调可能会被触发多次，所以，还提供了一个帮助类来辅助请求网络数据，用法如下：

```kotlin
class MyBoundaryCallback(
        private val ioExecutor: Executor,
        private val networkPageSize: Int)
    : PagedList.BoundaryCallback<RedditPost>() {

    // 创建 helper
    val helper = PagingRequestHelper(ioExecutor)


    /**
     * Database returned 0 items. We should query the backend for more items.
     */
    @MainThread
    override fun onZeroItemsLoaded() {
        // 注意使用的是 runIfNotRunning 方法
        // INITIAL 表示这是一个初始化请求
        helper.runIfNotRunning(PagingRequestHelper.RequestType.INITIAL) {
            webservice.getData(xxx)
                    .enqueue(createWebserviceCallback(it))
        }
    }

    /**
     * User reached to the end of the list.
     */
    @MainThread
    override fun onItemAtEndLoaded(itemAtEnd: RedditPost) {
        // AFTER 表示数据到了最后
        helper.runIfNotRunning(PagingRequestHelper.RequestType.AFTER) {
            webservice.getData(xxx)
                    .enqueue(createWebserviceCallback(it))
        }
    }


    override fun onItemAtFrontLoaded(itemAtFront: RedditPost) {
        // 一般情况下不用处理这个，列表被拉到最顶端了
    }

    private fun createWebserviceCallback(it: PagingRequestHelper.Request.Callback)
            : Callback<RedditApi.ListingResponse> {
        return object : Callback<RedditApi.ListingResponse> {
            override fun onFailure(
                    call: Call<RedditApi.ListingResponse>,
                    t: Throwable) {
                // 需要调用 helper 的失败回调
                it.recordFailure(t)
            }

            override fun onResponse(
                    call: Call<RedditApi.ListingResponse>,
                    response: Response<RedditApi.ListingResponse>) {
                // 需要调用 helper 的成功回调
                // 当然，真的app不会在这里处理，这里只是演示
          		it.recordSuccess()
                // 将请求的数据放入数据库
                insertItemsIntoDb(response)
            }
        }
    }
        
	/**
     * every time it gets new items, boundary callback simply inserts them into the database and
     * paging library takes care of refreshing the list if necessary.
     */
    private fun insertItemsIntoDb(
            response: Response<RedditApi.ListingResponse>) {
        ...
    }
}
```



### ItemKeyedDataSource 与 PageKeyedDataSource 使用

通常，DataSource 与 PageList 是成对存在的。他们可以看作是数据集的一份快照。当数据集被更新之后，需要重新创建一个新的 DataSouce 与 PageList 对。

举个例子：

当更新某个数据源之后，我们需要调用它的 invalidate 方法：

```java
mDataSource.invalidate();
```

查看源码，发现这个方法，只是遍历了一个回调集合，一个个的触发它们：

```java
    @AnyThread
    public void invalidate() {
        if (mInvalid.compareAndSet(false, true)) {
            for (InvalidatedCallback callback : mOnInvalidatedCallbacks) {
                callback.onInvalidated();
            }
        }
    }
```

那么，这些回调是怎么使用的呢？是需要我们自行实现吗？

在我翻看了一部分源码之后，找到了这样的逻辑：

在 `LivePagedListBuilder`中：

```java
    @AnyThread
    @NonNull
    @SuppressLint("RestrictedApi")
    private static <Key, Value> LiveData<PagedList<Value>> create(
            ...) {
        return new ComputableLiveData<PagedList<Value>>(fetchExecutor) {
            ...

            private final DataSource.InvalidatedCallback mCallback =
                    new DataSource.InvalidatedCallback() {
                        @Override
                        public void onInvalidated() {
                            invalidate();
                        }
                    };

            @SuppressWarnings("unchecked") // for casting getLastKey to Key
            @Override
            protected PagedList<Value> compute() {
                ...

                do {
                    ...

                    mDataSource = dataSourceFactory.create();
                    mDataSource.addInvalidatedCallback(mCallback);

                    ...
                } while (mList.isDetached());
                return mList;
            }
        }.getLiveData();
    }
```

我们可以看到，使用LivePageListBuilder时，它的 create 方法返回了一个`ComputableLiveData`对象，在这个对象中，它为 mDataSource 添加了 callback，我们跟踪一下，看看这个回调做了什么。

```java
ComputableLiveData.invalidate() ->
ComputableLiveData.mInvalidationRunnable ->
ComputableLiveData.mRefreshRunnable ->
ComputableLiveData.compute() ->
```

我们看到，最终还是出发了 compute 方法，相当于又重新创建了一个 mDataSource 对象，所以这也刚好满足了 DataSource 的规范，当我们对数据源进行更新时，需要调用它的 invalidate 方法，这样 UI 才能收到变化通知。并且，如果要自己实现 DataSource，记得在 callback 回调里面重新创建 DataSouce，否则，也不会刷新。

那么，还有一个疑问，没有解决，当我们使用 Room 的时候，对数据库进行更新时，我们没有使用DataSource，那么它是如何做到能够将更新反馈给UI的呢？

以下面的一个方法为例：

```kotlin
@Dao
public interface ProductDao {

    @Query("SELECT products.* FROM products JOIN productsFts ON (products.id = productsFts.rowid) "
        + "WHERE productsFts MATCH :query")
    LiveData<List<ProductEntity>> searchAllProducts(String query);

    @Update
    void updatePrice(ProductEntity product);
}

```

我们会在 ViewModel 中，获取 searchAllProducts 返回的 LiveData<List\<ProductEntity>>，然后，在 UI 中订阅这个对象，当我们使用 updatePrice 方法，更新了数据源时，LiveData<List\<ProductEntity>> 会将变化返回到 UI 中，现在，我们来看看这是如何实现的。

```java
  @Override
  public LiveData<List<ProductEntity>> loadAllProducts() {
    final String _sql = "SELECT * FROM products";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[]{"products"}, new Callable<List<ProductEntity>>() {
      @Override
      public List<ProductEntity> call() throws Exception {
        ...
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
```

其实是利用 `createLiveData` 方法来返回了一个 LiveData 对象，注意这个对象复写了 finalize 方法，很少见。

```java
    public <T> LiveData<T> createLiveData(String[] tableNames, Callable<T> computeFunction) {
        return mInvalidationLiveDataContainer.create(
                validateAndResolveTableNames(tableNames), computeFunction);
    }
```

注释我没贴，意思是说，当数据库变得无效的时候，它会利用 computeFunction 函数来重新创建一个 LiveData。

那么，我们需要研究的就是，它是如何知道数据库变得无效的。继续追踪下去：

```java
    <T> LiveData<T> create(String[] tableNames, Callable<T> computeFunction) {
        return new RoomTrackingLiveData<>(mDatabase, this, computeFunction, tableNames);
    }
```

直接返回了一个 RoomTrackingLiveData 对象，这个名字就很有意思了，很大的可能，就是这个类能监听到数据库的变化。

```java
    RoomTrackingLiveData(...) {
        ...
        mObserver = new InvalidationTracker.Observer(tableNames) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                ArchTaskExecutor.getInstance().executeOnMainThread(mInvalidationRunnable);
            }
        };
    }
```

看到了一个 mObserver 对象，它被触发时会在主线程执行一个 mInvalidationRunnable。这里有点似曾相识的感觉啊！

```java
    final Runnable mInvalidationRunnable = new Runnable() {
        @MainThread
        @Override
        public void run() {
            ...
            if (...{
                if (...){
                    mDatabase.getQueryExecutor().execute(mRefreshRunnable);
                }
            }
        }
    };
```

调用了 mRefreshRunnable。

```java
    final Runnable mRefreshRunnable = new Runnable() {
        @WorkerThread
        @Override
        public void run() {
            ...
            do {
                ...
                if (...) {
                    
                    try {
                        ...
                        while (...) {
                            ...
                            try {
                                value = mComputeFunction.call();
                            } catch (Exception e) {
                                ...
                            }
                        }
                        ...
                    } finally {
                        ...
                    }
                }
                ...
            } while (computed && mInvalid.get());
        }
    };
```

重新调用了 mComputeFunction 函数，这样就重新创建了 LiveData，那么我们还需要看看 mObserver 的回调是什么时候被触发的，被谁触发的。

```java
final Runnable mRefreshRunnable = new Runnable() {
        @WorkerThread
        @Override
        public void run() {
            if (mRegisteredObserver.compareAndSet(false, true)) {
                mDatabase.getInvalidationTracker().addWeakObserver(mObserver);
            }
            ...
        }
    };
```

在 mRefreshRunnable 中，添加了这个观察者。

```java
    @Override
    protected void onActive() {
        super.onActive();
        mContainer.onActive(this);
        mDatabase.getQueryExecutor().execute(mRefreshRunnable);
    }
```

mRefreshRunnable 不仅仅只是被 mInvalidationRunnable 调用，在 LiveData 接收第一个订阅者的时候，也会执行，这样就将 mObserver 注册为了观察者。

```java
    public void addWeakObserver(Observer observer) {
        addObserver(new WeakObserver(this, observer));
    }
```

```java
    public void addObserver(@NonNull Observer observer) {
        ...
        ObserverWrapper wrapper = new ObserverWrapper(observer, tableIds, tableNames, versions);
        ObserverWrapper currentObserver;
        synchronized (mObserverMap) {
            currentObserver = mObserverMap.putIfAbsent(observer, wrapper);
        }
        if (currentObserver == null && mObservedTableTracker.onAdded(tableIds)) {
            syncTriggers();
        }
    }
```

这里都好理解，将 mObserver 添加到了一个 map 中。我们看看 map 中的订阅者，什么时候被循环调用。

```java
    Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            final Lock closeLock = mDatabase.getCloseLock();
            boolean hasUpdatedTable = false;
            try {
                ...
            } finally {
                closeLock.unlock();
            }
            if (hasUpdatedTable) {
                synchronized (mObserverMap) {
                    for (Map.Entry<Observer, ObserverWrapper> entry : mObserverMap) {
                        entry.getValue().checkForInvalidation(mTableVersions);
                    }
                }
            }
        }
```

还是在 mRefreshRunnable 中，如果检查到了 table 有更新，那么就会遍历这个集合。我们暂时不去研究 hasUpdatedTable 这个值是如何得出的，因为涉及到数据库的知识，一时我也弄不清楚。再看，mRefreshRunnable 什么时候被调用。

```java
    /**
     * Enqueues a task to refresh the list of updated tables.
     * <p>
     * This method is automatically called when {@link RoomDatabase#endTransaction()} is called but
     * if you have another connection to the database or directly use {@link
     * SupportSQLiteDatabase}, you may need to call this manually.
     */
    @SuppressWarnings("WeakerAccess")
    public void refreshVersionsAsync() {
        // TODO we should consider doing this sync instead of async.
        if (mPendingRefresh.compareAndSet(false, true)) {
            ArchTaskExecutor.getInstance().executeOnDiskIO(mRefreshRunnable);
        }
    }
```

注释说的很清楚了，当 RoomDatabase#endTransaction() 调用的时候，会自动触发，这样就说的通了。

当我们使用 Room 更新数据库的时候，自动生成的代码，是使用了事务的，也就是说它会自动触发观察的的订阅方法。

参考链接：

https://medium.com/@chet.deva/android-paging-for-network-uncovered-b9cb85ca637b