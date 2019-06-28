# Broadcast 探究

最近终于把自己的状态调整过来了，看起书来也能有很多收获。

嗯，今天在看《Android艺术开发探索》这本书的时候（这本书还真的不错，每次看都有新的发现），看到了四大组件的工作原理，关于 Broadcast，有这样的一个疑问：

> 当App安装了没有运行的时候，如果App内部有静态注册的 Receiver，那么当一个满足 filter 条件的广播被发送的时候，是不是需要先启动这个 App，创建一个进程？关于 Activity 启动的时候，创建进程的代码，我还是有点印象的，但是 Broadcast 就完全不了解，所以既然有了疑问，那么就肯定要去找出答案才行。

下面所写的都是我翻了很多文章才记录下来的，嗯，希望通过写这次记录，能够对 Broadcast 有更好的理解，毕竟，我们项目里面用的很浅。

**PS**：我刚开始是翻了很多文章，有 《Android 应用的安装流程解析》，《Broadcast 的注册》，《Broadcast 的工作原理》之类的但是都没有我想要的。但是最后我找了一篇非常牛逼的文章[品茗论道说广播(Broadcast内部机制讲解)](https://my.oschina.net/youranhongcha/blog/226274) ，看了之后恍然大悟，写的真的思路清晰。



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

ReceiverList继承于ArrayList\<BroadcastFilter>，而BroadcastFilter又继承于IntentFilter，所以ReceiverList可以被理解为一个IntentFilter数组列表。

现在，我们就有了一个大概的注册模型了，还是上一张图吧。

