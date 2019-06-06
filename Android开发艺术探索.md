## Android开发艺术探索



### 第一章 Activity的生命周期和启动模式

- 假设当前 Activity 为 A，如果这时用户打开一个新 Activity B，那么是 B 的 onResume 先执行，还是 A 的 onPause 先执行？

  > A  的 onPause 先执行， B 的 onResume 后执行。
  >
  > 更加完整的流程是：A先进入onPause状态，然后才会启动B（onCreate-onStart-onResume），然后A进入onStop状态。
  >
  > 如果想要新的界面尽快显示出来，需要少在 onPause 做操作，可以移到 onStop 中。

  其实，不用分析源码也可以猜出来，因为如果 B 的 onResume 先执行，那么此时，已经有一个界面出现在 A 的上面了，但是 A 却没有 onPause，这与 Activity 的生命周期特征不符。

- 关于保存和恢复View层次结构，系统的工作流程是这样的：

  > 首先Activity被意外终止时，Activity会调用onSaveInstanceState去保存数据，然后Activity会委托Window去保存数据，接着Window再去委托它上面的顶层容器去保存数据。顶层容器是一个ViewGroup，一般来说它很可能是DecorView。最后顶层容器再去一一通知它的子元素来保存数据，这样整个数据保存过程就完成了。

- 当我们使用 ApplicationContext 去启动 standard 模式的 Activity 的时候**可能会**报错。

  > 当我测试的时候，是没有报错的，怀疑与系统版本有关系，在 28 上运行没有问题，在 21 上运行有问题，不知道具体是从那个版本开始改变的。没有去跟踪源码，猜想是 applicationContext 也有所属的任务栈了。

- TaskAffinity 属性主要和 singleTask 启动模式或者 allowTaskReparenting 属性配对使用，在其他情况下没有意义。

- 当 TaskAffinity 和 allowTaskReparenting 结合的时候，这种情况比较复杂，会产生特殊的效果。

  > 从应用 A 启动 应用 B 的某个 ActivityC，这个 Activity 的 allowTaskReparenting 为 true。
  >
  > 然后按 Home 回到桌面，然后单击B的桌面图标，这个时候不是启动了 B 的MainActivity，而是重新显示刚开启动的ActivityC。这是因为 ActivityC 从 A 的任务栈移动到了 B 的任务栈。

- 有3个 Activity，Activity1，Activity2，Activity3，将2与3都设置为 singleTask 模式，并指定taskAffinity 为 "com.aprz.task"（与包名不一致）。

  然后做如下操作：

  *1 中单击一个按钮，启动 2，*

  *2 中再单击按钮，启动 3，*

  *3 中再单击按钮，启动 1，*

  *1 中再单击按钮，启动 2。*

  *最后按返回键，会到哪个界面？*

  > 1 -> 2，任务栈 t1 的顺序为 1，任务栈 t2 的顺序为 2
  >
  > 这里是因为 singleTask 与 taskAffinity 同时起作用，所以会新创建一个任务栈。t2 是前台任务栈，t1是后台任务栈。
  >
  > 2 -> 3，任务栈 t1 的顺序为 1，任务栈 t2 的顺序为  2、 3
  >
  > 3 -> 1，任务栈 t1 的顺序为 1，任务栈 t2 的顺序为  2、 3、 1
  >
  > 1 -> 2，任务栈 t1 的顺序为 1，任务栈 t2 的顺序为 2
  >
  > 这里是以为 singleTask 模式会弹出上面的所有Activity。
  >
  > 按返回键，t2里面就没有Activity了，这个任务栈就不存在了。所以后台任务栈 t1 会显示出来，即 t1 中的 1 会显示出来。
  >
  > 再按返回键，t1 也没有 Activity 了，就回到桌面了。

- FLAG_ACTIVITY_NEW_TASK 这个标志的行为与 singleTask 并不一样。

  > **singleTask 会只存在一个。**
  >
  > 
  >
  > FLAG_ACTIVITY_NEW_TASK，首先会查找是否存在和被启动的Activity具有相同的亲和性的任务栈（即taskAffinity，注意同一个应用程序中的activity的亲和性相同），如果有，则直接把这个栈整体移动到前台，并保持栈中旧activity的顺序不变，然后被启动的Activity会被压入栈，如果没有，则新建一个栈来存放被启动的activity，注意，默认情况下同一个应用中的所有Activity拥有相同的关系(taskAffinity)。**它的实例并不会只存在一个。**
  >
  > 
  >
  > FLAG_ACTIVITY_NEW_TASK 与 FLAG_ACTIVITY_CLEAR_TOP 同时使用时，**会将它连同它之上的所有 ACtivity 都销毁**。
  >
  > 
  >
  > FLAG_ACTIVITY_NEW_TASK 不能用于需要返回值的界面（startActivityForResult）。

- IntentFilter的匹配规则

  只有一个 Intent 同时匹配 action 类别， category 类别，data 类别才算完全匹配。

  action 匹配：

  > 需要与过滤规则中的其中一个匹配，如果Intent中没有指定 action，则匹配失败。
  >
  > action的匹配要求 Intent 中的 action 存在且必须和过滤规则中的其中一个 action 相同。
  >
  > action 区分大小写。

  category 匹配：

  > 如果Intent中出现了 category，不管有几个 category，对于每个category来说，它必须是过滤规则中已经定义了的category。
  >
  > 
  >
  > 系统在startActivity 或者 startActivityForResult 的时候会默认为 Intent 加上 “android.intent.category.DEFAULT”这个 category。所以我们的activity如果想接受隐式调用，就必须在 intent-filter 中指定“android.intent.category.DEFAULT”这个category。

  data 匹配：

  > data 的匹配规则和 action 类似。
  >
  > 需要了解 data 的结构。data由两部分组成，mineType和URI。
  >
  > 当指定了 mineType，却没有指定 URI 时。过滤规则有默认的 URI 值，为 content 和 file。所以我们必须要使用 setDataAndType 方法来指定。

- queryIntentActivities 与 resolveActivity

  > 第二个参数需要注意，MATCH_DEFAULT_ONLY这个标记位，它表示仅仅匹配那些在 Intent-filter 中声明 \<category android:name="android.intent.category.DEFAULT">这个category的Activity。
  >
  > 因为只有返回的activity有\<category android:name="android.intent.category.DEFAULT">这个category，才能一定启动成功。



### 第二章 IPC机制

- 指定 android:process 属性的值有两种方式
  1. 使用 ： ，比如 android:process=":remote"，那么进程名为 `包名 + :remote`。该进程为应用的私有进程，其他应用的组件不可以和它跑在同一个进程中
  2. 直接写完整进程名，比如 android:process="com.test.remote"。该进程属于全局进程，其他应用可以通过 shareUID 的方式来和它跑在同一个进程中（这两个应用的 ShareUID一样，而且签名也要一样）。跑在同一个进程中，那么除了能够共享data目录，组件信息，还可以共享内存数据，或者说它们看起来就像是一个应用的两个部分。

- 运行在同一个进程的组件是属于同一个虚拟机和同一个Application的，同理，运行在不同进程中的组件是属于两个不同的虚拟机和Application的。

- serialVersionUID的详细工作机制是这样的：

  >  序列化的时候系统会把当前类的 serialVersionUID 写入序列化流中，当反序列化时，系统回去检测文件中的 serialVersionUID，看他是否和当前类的serialVersionUID一致，如果一致就说明序列化的版本与当前类的版本相同。这个时候是可以反序列化成功的。否则就说明当前类与序列化的类相比发生了变化，比如成员增加或者减少了，这个时候是无法正常序列化的。
  >
  > 一般的，我们应该手动指定 serialVersionUID 的值，因为不指定系统会自行计算（利用类结构），这样的话，类发生变化之后，serialVersionUID的值就不一样了，那么就无法反序列化了。

- AIDL文件生成的类的各个方法

  > asInterface
  >
  > 将服务端的Binder对象转换成客户端所需的AIDL接口类型的对象。如果客服端和服务端在同一个进程，那么此方法返回的就是服务端的Stub对象本省，否则返回的是系统封装的Stub.Proxy对象。
  >
  > onTransact
  >
  > 这个方法运行在服务端中的Binder线程池中，当客服端发起跨进程请求时，远程请求会通过系统底层封装后交由此方法来处理。如果此方法返回false，那么客服端的请求会失败。
  >
  > Proxy#yourCustomFunction
  >
  > 这个方法运行在客服端，当客服端远程调用此方法时，它的内部实现是这样的：先创建输入参数，输出参数，然后调用transact方法来发起RPC请求，同时当前线程挂起，然后服务端的 onTransact 方法会被调用，直到RPC调用过程返回，当前线程继续执行，处理返回结果。
  >
  > 当客服端发起远程调用请求时，由于当前线程会被挂起直至服务端进程返回数据，所以如果一个远程方法很耗时，那么不能在UI线程中发起此远程请求。其次，由于服务端的Binder方法运行在BInder的线程池中，所以Binder方法不管是否耗时都应该采用同步的方式去实现。

- Android中的IPC方式

  1. Bunble

     > Activity、Service、Receiver 都支持在Intent中传递Bundle数据。在一个进程中启动另一个进程的 Activity、Service、Receiver 时使用。

  2. 使用文件共享

     > 适合在对数据同步要求不高的进程之间进行通信，并且要妥善处理并发读写的问题。
     >
     > SharedPrefences，系统会对它的读写有一定的缓存，因此，多进程中，系统对它的读写不可靠。

  3. 使用Messenger

     > Messenger 是基于AIDL的。
     >
     > 它一次处理一个请求，因此在服务端我们不用考虑线程同步的问题。

  4. 使用AIDL

     > List只支持ArrayList，但是在服务端可以使用别的集合，比如：CopyOnWriteArrayList。这是因为AIDL中所支持的是抽象的List，而List只是一个接口。因此，虽然服务端返回的是CopyOnWriteArrayList，但是在Binder中会按照List的规范去访问数据并最终形成一个新的ArrayList传递给客服端。
     >
     > 
     >
     > RemoteCallbackList 是系统专门提供的用于删除跨进程 listener 的接口。
     >
     > 因为虽然我们注册和接注册传递的是同一个对象，但是通过Binder传递到服务端后，会产生两个全新的对象。
     >
     > 
     >
     > 重新连接服务，第一种方式是使用 DeathRecipient 监听，它的 binderDied 在客服端的Binder线程池中被回调。第二种是在 onServiceDisconnected 中重连远程服务，它在客服端的UI线程中回调。
     
  5. ContentProvider
  
     > ContentProvider 主要以表格的形式来组织数据，并且可以包含多个表，对于每个表格来说它们都具有行和列的层次性，行往往对应一条记录，而列对应一条记录的一个字段。
     >
     > 除了表格的形式，ContentProvider还支持文件数据，如图片、视频。处理这种数据可以在 ContentProvider 中返回文件的句柄以供外界访问。详细实现可以参考MediaStore。
     >
     > ContentProvider对底层的数据结构存储方式没有任何要求，可以使用文件，甚至内存中的一个对象来储存。
     >
     > 
     >
     > ContentProvider 通过 Uri 来区分外界要访问的数据集合，比如某个ContentProvider的底层有多个表，为了能够区分外界到底要访问哪个表，我们需要为它定义单独的 Uri 与 Uri_Code，并且使用UriMatcher的 addURI 方法将Uri与Uri_Code关联起来。
     >
     > 
     >
     > query 方法与其他3个方法有点不同，其他3个方法会引起数据源的改变，所以需要使用 notifyChange 方法来通知外界当前 ContentProvider 中的数据已经改变了。
     >
     > 
     >
     > CRUD 四个方法都运行在 Binder 线程池中，所以需要进行线程同步。
  
  6. 使用Socket
  
     > 在Service中建立TCP服务，在界面中连接TCP服务。

### 第三章 View的事件体系

- View的位置参数

  > top、right、bottom、left 是相对于父容器说的。
  >
  > x、y、translationX、translateY 的关系：x = left + translationX，y = top + translationY
  >
  > getX、getY 是相对于当前 View 左上角的 x 与 y 坐标，getRawX、getRawY 返回的是相对于手机屏幕上左上角的 x、y坐标。

- View 的滑动

  - 使用 scrollTo/scrollBy，scrollBy 也是内部调用了 scrollTo 方法

    需要注意一下scrollX与 scrollY值的正负

    ![](F:\note-markdown\Android开发艺术探索\view_scroll.png)

  - 使用动画，注意补间动画与属性动画的区别
  
  - 改变布局参数
  
- View 的弹性滑动

  - 使用 Scroller，需要复写 computeScroll 方法

    ```java
    @Override
    public void computeScroll () {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }
    ```

    其原理是：startScroll 方法会触发 view 的 invalidate 方法，invalidate 方法会调用 computeScroll 方法，computeScroll 调用 computeScrollOffset 方法，computeScrollOffset 会按照动画执行的时间计算出滚动的位置。所以，mScroller 可以拿到动画需要滚动的x，y位置， 利用 scrollTo 就可以让内容滚动。然后再次触发 postInvalidate，实现循环，直到 computeScrollOffset 方法返回 false，表示动画执行完了。

  - 通过动画与插值器

  - 使用延时策略，手动更新

- View 的事件分发机制

  **public boolean dispathTouchEvent(MotionEvent ev)**

  用来进行事件的分发。表示是否消耗当前事件，返回值受到当前 View 的 onTouchEvent 与下级 View 的 dispatchTouchEvent 影响。比如，之前非常流行的头部拖动方法效果：一个专辑界面，头部有一张专辑图片，下面是专辑列表，滑动专辑列表到最上面，然后继续滑动，此时，头部的专辑图片会放大。整个滑动体验非常流畅。按照一般的理解，这是很难做到的，毕竟，将事件交给底部专辑列表处理之后，后续所有的事件都只能给它处理，这样就做不到连续滑动到顶部再滑动时头部放大的效果了。所以这里只能手动的干预滑动事件，在未滑动到列表顶部时，把事件派给专辑列表，在滑动到列表顶部时，继续滑动时，把事件派给专辑图片。

  **public boolean onInterceptTouchEvent(MotionEvent event)**

  在 dispathTouchEvent 方法的内部调用，用来判断是否拦截某个事件，如果当前 View 拦截了某个事件，那么在同一个事件序列当中，此方法不会被再次调用。

  **public boolean onTouchEvent(MotionEvent event)**

  在 dispathTouchEvent 方法的内部调用，返回结果表示是否消耗当前事件，如果不消耗，则在同一事件序列中，当前 View 无法再次接收到事件。

  

  当一个触摸事件产生后，它的传递过程如下：

  Activity -> Window -> DecorView

  由于 DecorView 是一个 ViewGroup，所以事件的传递要从 ViewGroup 说起。

  ViewGroup 的 dispathTouchEvent 被调用，

  -  如果它的 onInterceptTouchEvent 返回 true，那么事件会交它来处理，接着它的 onTouchEvent 方法被调用。

    -  如果它的 onTouchEvent  返回 true，表示它可以处理这个事件，那么后续事件都会交给这个 ViewGroup 来处理，并且它的 onInterceptTouchEvent 在后续事件中不再调用。

    - 如果它的 onTouchEvent   返回 false，表示它无法处理该事件，那么后续事件不会再传递到该 ViewGroup 这一层。

  - 如果它的 onInterceptTouchEvent 返回 false，事件就会向它的 child 传递（会循环遍历所有的 child，以 z-index 顺序，找到 View 的范围包含点击区域的可见View，这里的描述可能不太准确，好像还与动画有关，然后把这个事件派发给这个 View）。如果这个 child 是一个 ViewGroup 那么，就重复上面的逻辑，如果 child 是 view...

  View 的 dispathTouchEvent 方法被调用，

  - 由于它没有 onInterceptTouchEvent 方法，所以会直接调用它的 onTouchEvent 方法。
    -  如果它的 onTouchEvent  返回 true，表示它可以处理这个事件，那么后续事件都会交给它
    - 如果它的 onTouchEvent   返回 false，表示它无法处理该事件，
  - 需要注意的是，View 可以设置 onTouchListener，
    - 当 listener 的 onTouch 方法返回了 false 的时候，onTouchEvent 可以正常调用
    - 当 listener 的 onTouch 方法返回了 true 的时候，onTouchEvent 不会被调用
  - onClick 的调用是在 onTouchEvent 中
  - onClick 与 onLongClick，onLongClick 的原理

- View 的滑动冲突

