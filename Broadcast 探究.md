# Broadcast 探究

最近终于把自己的状态调整过来了，看起书来也能有很多收获。

嗯，今天在看《Android艺术开发探索》这本书的时候（这本书还真的不错，每次看都有新的发现），看到了四大组件的工作原理，关于 Broadcast，有这样的一个疑问：

> 当App安装了没有运行的时候，如果App内部有静态注册的 Receiver，那么当一个满足 filter 条件的广播被发送的时候，是不是需要先启动这个 App，创建一个进程？关于 Activity 启动的时候，创建进程的代码，我还是有点印象的，但是 Broadcast 就完全不了解，所以既然有了疑问，那么就肯定要去找出答案才行。

下面所写的都是我翻了很多文章才记录下来的，嗯，希望通过写这次记录，能够对 Broadcast 有更好的理解，毕竟，我们项目里面用的很浅。

**PS**：我刚开始是翻了很多文章，有 《Android 应用的安装流程解析》，《Broadcast 的注册》，《Broadcast 的工作原理》之类的但是都没有我想要的。但是最后我找了一篇非常牛逼的文章[品茗论道说广播(Broadcast内部机制讲解)](https://my.oschina.net/youranhongcha/blog/226274) ，看了之后恍然大悟，写的真的思路清晰，这篇文章基本都是此文的内容，只是按照我自己的思路整理了一下。



**注1**：代码版本是 api 21，突然发现代码版本越低代码越好理解，反正本质是不会变的，一些行为变化可以专门去了解。

**注2**：请原谅我盗图，我懒得自己画了。



## 概述

我们知道，Activity 的都是由 AMS 管理的，其实 Broadcast 也是，当一个广播被发送的时候，AMS 会对自己管理的所有 Receiver 做一个决策，将回调满足条件的 Receiver 的 onReceive 方法。

![](Broadcast 探究\201441_jyH9_174429.png)



在Android系统中，接收广播的组件叫作receiver，而且**receiver还分为动态和静态的**。动态receiver是在运行期通过调用registerReceiver()注册的，而静态receiver则是在AndroidManifest.xml中声明的。

除了这个，Android 还有有序广播和普通广播的概念。有序广播就是广播接收者会按照优先级依次接受广播，而且还可以中断广播，让后面的收不到。



## 两种 Receiver

这里我们先介绍动态的 Receiver。

### 动态 Receiver

动态的 Receiver 就是在代码中注册的 Receiver，当App没有运行的时候（进程不存在），是无法接受到广播的，说的更直白一点，就是 AMS 那里没有这个 Receiver 相关的信息（这个后面会说到源码的东西）。

这里我们看一下注册相关的源码，因为有几个类非常的重要，毕竟我们阅读源码还是要对几个常见的类熟悉一下，以后阅读起来就会更快。

首先，当我们使用代码注册一个 receiver 的时候，会调用到下面的方法：

> android.app.ContextImpl#registerReceiverInternal

```java
    private Intent registerReceiverInternal(BroadcastReceiver receiver, int userId,
            IntentFilter filter, String broadcastPermission,
            Handler scheduler, Context context) {
        IIntentReceiver rd = null;
        if (receiver != null) {
            // mPackageInfo 与 context 都不为空
            if (mPackageInfo != null && context != null) {
                if (scheduler == null) {
                    scheduler = mMainThread.getHandler();
                }
                // 查找和context对应的“子哈希表”里的ReceiverDispatcher，如果找不到，就重新new一个
                // 这个 rd 对象非常重要
                rd = mPackageInfo.getReceiverDispatcher(
                    receiver, context, scheduler,
                    mMainThread.getInstrumentation(), true);
            } else {
                if (scheduler == null) {
                    scheduler = mMainThread.getHandler();
                }
                rd = new LoadedApk.ReceiverDispatcher(
                        receiver, context, scheduler, null, true).getIIntentReceiver();
            }
        }
        try {
            // 看到 ActivityManagerNative 这种东西，就知道会调到 AMS 里面
            return ActivityManagerNative.getDefault().registerReceiver(
                    mMainThread.getApplicationThread(), mBasePackageName,
                    rd, filter, broadcastPermission, userId);
        } catch (RemoteException e) {
            return null;
        }
    }
```

ReceiverDispatcher 这个类要重点说一下。

我们知道，**BroadcastReceiver 作为一个 Android 的组件是不能直接跨进程传递的，它有另外一个类可以实现跨进程传递，就是 IIntentReceiver 类，它是一个 Binder 接口，具体实现是 LoadedApk.ReceiverDispatcher.InnerReceiver**。

ReceiverDispatcher  这个类内部同时保存了 BroadcastReceiver 和 InnerReceiver。BroadcastReceiver 就是App这边注册的，InnerReceiver 用于跨进程传输给与 AMS 通信。等到 AMS 通知app端，需要触发 BroadcastReceiver 的 onReceive 方法的时候，就可以通过这个 ReceiverDispatcher  来找到 BroadcastReceiver 。

![](F:\note-markdown\Broadcast 探究\210455_ny00_174429.png)

由于一个应用里可能会注册多个动态receiver，所以这种一一对应关系最好整理成表，这个表就位于LoadedApk中。

在Android的架构里，应用进程里是用LoadedApk来对应一个apk的，进程里加载了多少个apk，就会有多少LoadedApk。每个LoadedApk里会有一张”*关于本apk动态注册的所有receiver*“的哈希表（mReceivers）。

> android.app.LoadedApk

```java
    private final ArrayMap<Context, ArrayMap<BroadcastReceiver, ReceiverDispatcher>> mReceivers
        = new ArrayMap<Context, ArrayMap<BroadcastReceiver, LoadedApk.ReceiverDispatcher>>();
```

该表的key项是我们比较熟悉的Context，也就是说可以是Activity、Service或Application。而value项则是另一张“子哈希表”。

这个“子哈希表”，key值为BroadcastReceiver，value项为ReceiverDispatcher。

我们回想一下上面的内容，根据 context，我们可以找到一个“表中表”，根据 BroadcastReceiver，我们可以从“表中表”里面获取到 ReceiverDispatcher，因为 ReceiverDispatcher 里面存了 InnerReceiver 对象，所以这样关系就对应起来了。为啥要说这个，是因为这就是数据结构哒，有蛋疼的面试官会问的。

现在，我们就有了一个大概的注册模型了，还是上一张图吧。嗯，画的不太好，反正理解就好了。

![](F:\note-markdown\Broadcast 探究\android广播.png)



好的，我们接着往下看：

> com.android.server.am.ActivityManagerService#registerReceiver

```java
// 其中的receiver参数为IIntentReceiver型，正对应着ReceiverDispatcher中那个binder实体。
// 也就是说，每个客户端的ReceiverDispatcher，会对应AMS端的一个ReceiverList。
ReceiverList rl = (ReceiverList)mRegisteredReceivers.get(receiver.asBinder());
if (rl == null) {
    rl = new ReceiverList(this, callerApp, callingPid, callingUid,
                          userId, receiver);
    if (rl.app != null) {
        rl.app.receivers.add(rl);
    } else {
        try {
            receiver.asBinder().linkToDeath(rl, 0);
        } catch (RemoteException e) {
            return sticky;
        }
        rl.linkedToDeath = true;
    }
    // 如果map里面没有，就创建一个新的，存进去
    mRegisteredReceivers.put(receiver.asBinder(), rl);
}
// 创建 BroadcastFilter
// filter参数指明了用户对哪些intent感兴趣。
// 对同一个BroadcastReceiver对象来说，可以注册多个感兴趣的filter
BroadcastFilter bf = new BroadcastFilter(filter, rl, callerPackage,
                                         permission, callingUid, userId);
// 最终 IntentFilter 信息汇总到AMS的mRegisteredReceivers表中。
rl.add(bf);
```

**ReceiverList继承于ArrayList\<BroadcastFilter>**，而BroadcastFilter又继承于IntentFilter，所以ReceiverList可以被理解为一个IntentFilter数组列表。因为一个广播可以添加多个 IntentFilter，所以一个 BroadcastReceiver 对应一个列表，方便遍历。

![](F:\note-markdown\Broadcast 探究\210642_v7QA_174429.png)



### 静态 Receiver

静态receiver是指那些在AndroidManifest.xml文件中声明的receiver，它们的信息会在系统启动时，由Package Manager Service（PMS）解析并记录下来。以后，当AMS调用PMS的接口来查询“和intent匹配的组件”时，PMS内部就会去查询当初记录下来的数据，并把结果返回AMS。有的同学认为静态receiver是常驻内存的，这种说法并不准确。因为常驻内存的只是静态receiver的描述性信息，并不是receiver实体本身。

**这部分的东西，可以去查找应用安装的过程源码解析。**

### 发送广播

使用 sendBroadcast() 就可以发送一个广播。而sendOrderedBroadcast()，则是用来向系统发出有序广播(Ordered broadcast)的。

这种有序广播对应的所有接收器只能按照一定的优先级顺序，依次接收intent。这些优先级一般记录在AndroidManifest.xml文件中，具体位置在<intent-filter>元素的android:priority属性中，其数值越大表示优先级越高，取值范围为-1000到1000。另外，有时候我们也可以调用IntentFilter对象的setPriority()方法来设置优先级。

对于有序广播而言，前面的接收者可以对接收到的广播intent进行处理，并将处理结果放置到广播intent中，然后传递给下一个接收者。需要注意的是，前面的接收者有权终止广播的进一步传播。也就是说，如果广播被前面的接收者终止了，那么后面的接收器就再也无法接收到广播了。

还有一个怪东西，叫做sticky广播，它又是什么呢？简单地说，sticky广播可以保证“在广播递送时尚未注册的receiver”，一旦日后注册进系统，就能够马上接到“错过”的sticky广播。**感觉有些不好理解，但是幸运的是它现在被废弃了。**

下面，举一个例子，从代码入手：

```java
mContext = getApplicationContext(); 
Intent intent = new Intent();  
intent.setAction("com.android.xxxxx");  
intent.setFlags(1);  
mContext.sendBroadcast(intent);
```

sendBroadcast 会通过 ContextImpl 走到 AMS 侧。然后通过层层调用又会调用到 broadcastIntentLocked 方法。

> com.android.server.am.ActivityManagerService#broadcastIntentLocked

在分析这个方法之前，我们可以思考一下几个问题。

首先，有些广播intent只能由具有特定权限的进程发送，而有些广播intent在发送之前需要做一些其他动作。当然，如果发送方进程是系统进程、phone进程、shell进程，或者具有root权限的进程，那么必然有权发出广播。

发送广播时，还需要考虑所发送的广播是否需要有序（ordered）递送。而且，receiver本身又分为动态注册和静态声明的，这让我们面对的情况更加复杂。从目前的代码来看，静态receiver一直是按照有序方式递送的，而动态receiver则需要根据ordered参数的值，做不同的处理。当我们需要有序递送时，AMS会把动态receivers和静态receivers合并到一张表中，这样才能依照receiver的优先级，做出正确的处理，此时动态receivers和静态receivers可能呈现一种交错顺序。

另一方面，有些广播是需要发给特定目标组件的，这个也要加以考虑。

现在我们来分析broadcastIntentLocked()函数，我们可以将其逻辑大致整理成以下几步：

1） 为intent添加FLAG_EXCLUDE_STOPPED_PACKAGES标记；
2） 处理和package相关的广播；
3） 处理其他一些系统广播；
4） 判断当前是否有权力发出广播；
5） 如果要发出sticky广播，那么要更新一下系统中的sticky广播列表；
6） 查询和intent匹配的静态receivers；
7） 查询和intent匹配的动态receivers；
8） 尝试向并行receivers递送广播；
9） 整合（剩下的）并行receivers，以及静态receivers，形成一个串行receivers表；
10） 尝试逐个向串行receivers递送广播。

下面我们来详细说这几个部分。

#### 为intent添加FLAG_EXCLUDE_STOPPED_PACKAGES标记

> com.android.server.am.ActivityManagerService#broadcastIntentLocked

```java
        intent = new Intent(intent);

        // By default broadcasts do not go to stopped apps.
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
```

为什么intent要添加FLAG_EXCLUDE_STOPPED_PACKAGES标记呢？

原因是这样的，在Android 3.1之后，PMS加强了对“处于停止状态的”应用的管理。如果一个应用在安装后从来没有启动过，或者已经被用户强制停止了，那么这个应用就处于停止状态（stopped state）。为了达到精细调整的目的，Android增加了2个flag：FLAG_INCLUDE_STOPPED_PACKAGES和FLAG_EXCLUDE_STOPPED_PACKAGES，以此来表示intent是否要激活“处于停止状态的”应用。

**默认情况下，AMS是不会把intent广播发给“处于停止状态的”应用的。**据说Google这样做是为了防止一些流氓软件或病毒干坏事。当然，如果广播的发起者认为自己的确需要广播到“处于停止状态的”应用的话，它可以让intent携带FLAG_INCLUDE_STOPPED_PACKAGES标记，从这个标记的注释可以了解到，**如果这两个标记同时设置的话，那么FLAG_INCLUDE_STOPPED_PACKAGES标记会“取胜”，它会覆盖掉framework自动添加的FLAG_EXCLUDE_STOPPED_PACKAGES标记。**

#### 处理和package相关的广播

接下来需要处理一些系统级的“Package广播”，这些主要从PKMS（Package Manager Service）处发来。比如，当PKMS处理APK的添加、删除或改动时，一般会发出类似下面的广播：ACTION_PACKAGE_ADDED、ACTION_PACKAGE_REMOVED、ACTION_PACKAGE_CHANGED、ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE、ACTION_UID_REMOVED。

AMS必须确保发送“包广播”的发起方具有BROADCAST_PACKAGE_REMOVED权限，如果没有，那么AMS会抛出异常（SecurityException）。接着，AMS判断如果是某个用户id被删除了的（Intent.ACTION_UID_REMOVED），那么必须把这件事通知给“电池状态服务”（Battery Stats Service）。另外，如果是SD卡等外部设备上的应用不可用了，这常常是因为卡被unmount了，此时PKMS会发出Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE，而AMS则需要把SD卡上的所有包都强制停止（forceStopPackageLocked()），并立即发出另一个“Package广播”——EXTERNAL_STORAGE_UNAVAILABLE。

如果只是某个外部包被删除或改动了，则要进一步判断intent里是否携带了EXTRA_DONT_KILL_APP额外数据，如果没有携带，说明需要立即强制结束package，否则，不强制结束package。看来有些应用即使在删除或改动了包后，还会在系统（内存）中保留下来并继续运行。另外，如果是删除包的话，此时要发出PACKAGE_REMOVED广播。

#### 处理其他一些系统广播

broadcastIntentLocked()不但要对“Package广播”进行处理，还要关心其他一些系统广播。比如ACTION_TIMEZONE_CHANGED、ACTION_CLEAR_DNS_CACHE、PROXY_CHANGE_ACTION等等，感兴趣的同学可以自行研究这些广播的意义。

#### 必要时更新一下系统中的sticky广播列表

一开始会判断一下发起方是否具有发出sticky广播的能力，比如说要拥有android.Manifest.permission.BROADCAST_STICKY权限等等。判断合格后，broadcastIntentLocked()会更新AMS里的一张表——mStickyBroadcasts。

看一下它的结构：

> com.android.server.am.ActivityManagerService#mStickyBroadcasts

```java
    /**
     * State of all active sticky broadcasts per user.  Keys are the action of the
     * sticky Intent, values are an ArrayList of all broadcasted intents with
     * that action (which should usually be one).  The SparseArray is keyed
     * by the user ID the sticky is for, and can include UserHandle.USER_ALL
     * for stickies that are sent to all users.
     */
    final SparseArray<ArrayMap<String, ArrayList<Intent>>> mStickyBroadcasts =
            new SparseArray<ArrayMap<String, ArrayList<Intent>>>();
```

SparseArray 就不介绍了。它的key是user id，我们知道在Android里面，每个应用都有一个自己的user id，所以这里储存了所有应用的sticky广播。它的 value 是一个 ArrayMap<String, ArrayList\<Intent>>。上面的英文注释解释的比较清楚了，String是action，ArrayList 是 Intent 集合。也就是说当我们注册一个 receiver 的时候，有可能会收到多个 sticky 广播。

![](F:\note-markdown\Broadcast 探究\211259_PixD_174429.png)

#### 尝试向并行receivers递送广播

> com.android.server.am.ActivityManagerService#broadcastIntentLocked

```java
        int NR = registeredReceivers != null ? registeredReceivers.size() : 0;
		// 非有序广播
        if (!ordered && NR > 0) {
            // If we are not serializing this broadcast, then send the
            // registered receivers separately so they don't wait for the
            // components to be launched.
            final BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType, requiredPermission,
                    appOp, registeredReceivers, resultTo, resultCode, resultData, map,
                    ordered, sticky, false, userId);
            if (DEBUG_BROADCAST) Slog.v(
                    TAG, "Enqueueing parallel broadcast " + r);
            final boolean replaced = replacePending && queue.replaceParallelBroadcastLocked(r);
            if (!replaced) {
                queue.enqueueParallelBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
            // 将集合清空
            registeredReceivers = null;
            NR = 0;
        }
```

简单地说就是，new一个BroadcastRecord节点，并插入BroadcastQueue内的并行处理队列，最后发起实际的广播调度（scheduleBroadcastsLocked()）。

scheduleBroadcastsLocked 实际上只是发送了一个消息，触发了下面的这个方法，这很常见。

> com.android.server.am.BroadcastQueue#processNextBroadcast

在分析这个方法之前，我们先说说两个变量：

> com.android.server.am.ActivityManagerService#broadcastIntentLocked

```java
        // Figure out who all will receive this broadcast.
        List receivers = null;
        List<BroadcastFilter> registeredReceivers = null;
```

前文已经说过，有些广播是需要有序递送的。为了合理处理“有序递送”和“并行递送”，所以就搞出了两个集合。

receivers**主要用于记录“有序递送”的receiver**，而registeredReceivers则用于**记录与intent相匹配的动态注册的receiver。**

于这两个list的大致运作是这样的，我们先利用包管理器的queryIntentReceivers()接口，查询出和intent匹配的所有静态receivers，此时所返回的查询结果本身已经排好序了，代码如下：

```java
// 代码内部调用了 AppGlobals.getPackageManager().queryIntentReceivers(intent, resolvedType, STOCK_PM_FLAGS, user);
receivers = collectReceiverComponents(intent, resolvedType, callingUid, users);
```

而对于动态注册的receiver信息，就不是从包管理器获取了，这些信息本来就记录在AMS之中，此时只需调用：

```java
registeredReceivers = mReceiverResolver.queryIntent(intent, resolvedType, false, userId);
```

就可以了。注意，此时返回的registeredReceivers中的子项是**没有经过排序**的。

如果我们要“并行递送”广播， 只需要遍历 registeredReceivers中的值就好了，遍历完成之后，需要将改值改为 null，因为代码下面的逻辑是合并 registeredReceivers 与 receivers，不置为null的话就会出问题（所以说，这块代码写得像裹脚布一样） 。

如果我们要“串行递送”广播，那么必须考虑把**registeredReceivers表合并到receivers表**中去。我们知道，**一开始receivers列表中只记录了一些静态receiver，这些receiver将会被“有序递送”。现在我们只需再遍历一下registeredReceivers列表，并将其中的每个子项插入到receivers列表的合适地方，就可以合并出一条顺序列表了**。当然，如果registeredReceivers已经被设为null了（如果广播是有序的话，就不会为空），就无所谓合并了。

为什么静态声明的receiver只会“有序递送”呢？我想也许和这种receiver的复杂性有关系，因为在需要递送广播时，receiver所属的进程可能还没有启动呢，所以也许会涉及到启动进程的流程或者启动多个进程，这些都是比较复杂的流程。

搞清楚了这两个变量，现在，我们回到 `com.android.server.am.BroadcastQueue#processNextBroadcast` 函数中：

> com.android.server.am.BroadcastQueue#processNextBroadcast

```java
            // First, deliver any non-serialized broadcasts right away.
            while (mParallelBroadcasts.size() > 0) {
                r = mParallelBroadcasts.remove(0);
                r.dispatchTime = SystemClock.uptimeMillis();
                r.dispatchClockTime = System.currentTimeMillis();
                final int N = r.receivers.size();
                if (DEBUG_BROADCAST_LIGHT) Slog.v(TAG, "Processing parallel broadcast ["
                        + mQueueName + "] " + r);
                for (int i=0; i<N; i++) {
                    Object target = r.receivers.get(i);
                    if (DEBUG_BROADCAST)  Slog.v(TAG,
                            "Delivering non-ordered on [" + mQueueName + "] to registered "
                            + target + ": " + r);
                    deliverToRegisteredReceiverLocked(r, (BroadcastFilter)target, false);
                }
                addBroadcastToHistoryLocked(r);
                if (DEBUG_BROADCAST_LIGHT) Slog.v(TAG, "Done with parallel broadcast ["
                        + mQueueName + "] " + r);
            }
```

可以看到是一个循环，调用了 deliverToRegisteredReceiverLocked 就不管了，也就是说，AMS 是不会管哪个进程先收到广播，反正我全部发出去了，这里就是无需广播的处理逻辑，至于怎么回调到 APP 端的，下面再说。

#### 尝试逐个向串行receivers递送广播

现在要开始尝试逐个向串行receivers递送广播了。

> com.android.server.am.ActivityManagerService#broadcastIntentLocked

```java
        if ((receivers != null && receivers.size() > 0)
                || resultTo != null) {
            BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType,
                    requiredPermission, appOp, receivers, resultTo, resultCode,
                    resultData, map, ordered, sticky, false, userId);
            if (DEBUG_BROADCAST) Slog.v(
                    TAG, "Enqueueing ordered broadcast " + r
                    + ": prev had " + queue.mOrderedBroadcasts.size());
            if (DEBUG_BROADCAST) {
                int seq = r.intent.getIntExtra("seq", -1);
                Slog.i(TAG, "Enqueueing broadcast " + r.intent.getAction() + " seq=" + seq);
            }
            boolean replaced = replacePending && queue.replaceOrderedBroadcastLocked(r); 
            if (!replaced) {
                queue.enqueueOrderedBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
        }
```

与并行广播的逻辑差不多，都创建了新的 BroadcastRecord，只不过进入的队列不一样。并行广播被添加到了 mParallelBroadcasts 中，有序广播被添加到了 mOrderedBroadcasts 中。

我们接着看 `com.android.server.am.BroadcastQueue#processNextBroadcast` 中的处理逻辑：

嗯，代码比较长，我就不贴了，大致流程是这样的。

如果目标进程已经存在了，那么app.thread肯定不为null，直接调用processCurBroadcastLocked()即可，否则就需要启动新进程了。启动的过程是异步的，可能很耗时，所以要把BroadcastRecord节点记入mPendingBroadcast。

其实看到这里，我的疑问就已经解答了，但是我又有一个别的疑问了。**AMS 是如何挂起等待一个 APP 进程 收到广播才通知下一个 APP 进程的？是循环还是wait？**（既然使用了消息队列，那么肯定是在一个线程中处理的）

我在 com.android.server.am.BroadcastQueue#processNextBroadcast 方法中看到了下面的代码：

> com.android.server.am.BroadcastQueue#processNextBroadcast

```java
            if (mPendingBroadcast != null) {
                ...

                // 查看接收广播的进程死了没
                boolean isDead;
                synchronized (mService.mPidsSelfLocked) {
                    ProcessRecord proc = mService.mPidsSelfLocked.get(mPendingBroadcast.curApp.pid);
                    isDead = proc == null || proc.crashing;
                }
                // 如果没死，这里直接返回了，就是说不处理下一个接收者了
                // 那么等app端处理完之后，肯定还需要触发一下 AMS 的 processNextBroadcast
                // 不然循环就中断了
                if (!isDead) {
                    // It's still alive, so keep waiting
                    return;
                } else {
                    // 挂了就下一个
                    Slog.w(TAG, "pending app  ["
                            + mQueueName + "]" + mPendingBroadcast.curApp
                            + " died before responding to broadcast");
                    mPendingBroadcast.state = BroadcastRecord.IDLE;
                    mPendingBroadcast.nextReceiver = mPendingBroadcastRecvIndex;
                    mPendingBroadcast = null;
                }
            }
```

根据我们的思路，看看app端回调完了会不会触发 processNextBroadcast 这个方法，恢复循环。代码流程就不贴了，直接查看[这篇文章](http://gityuan.com/2016/06/04/broadcast-receiver/) 。可以看到最后 AMS.finishReceiver 这个方法里面还是调用了这个方法的。



至于广播的超时与广播的拦截这里就不分析了，因为暂时对这些不太感兴趣，太概可以猜个七七八八，很多东西都是一样的，有兴趣的可以去看看原文。



额外说一个东西，我在跟踪消息队列的时候，发现了两个 BroadcastQueue。

> com.android.server.am.ActivityManagerService

```java
        mFgBroadcastQueue = new BroadcastQueue(this, mHandler,
                "foreground", BROADCAST_FG_TIMEOUT, false);
        mBgBroadcastQueue = new BroadcastQueue(this, mHandler,
                "background", BROADCAST_BG_TIMEOUT, true);
```

从前面的源码中我们可以看到，将一个广播放到哪个队列里面，是通过下面的方法：

```java
    BroadcastQueue broadcastQueueForIntent(Intent intent) {
        final boolean isFg = (intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0;
        if (DEBUG_BACKGROUND_BROADCAST) {
            Slog.i(TAG, "Broadcast intent " + intent + " on "
                    + (isFg ? "foreground" : "background")
                    + " queue");
        }
        return (isFg) ? mFgBroadcastQueue : mBgBroadcastQueue;
    }
```

看来，我们只需要通过 Intent 设置 flag，就可以指定放入的队列。

mFgBroadcastQueue 与 mBgBroadcastQueue 除了超时时间不一样（如果你想要更长的超时时间，可以指定到后台队列，默认是后台），还有一个参数不一样 `mDelayBehindServices`。这个变量有注释，但是我还没太理解。它对有序广播会有一定的影响，大致的意思是在某个特殊的情况下，会延迟广播的发送。

