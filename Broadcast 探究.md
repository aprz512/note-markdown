# Broadcast 探究

最近终于把自己的状态调整过来了，看起书来也能有很多收获。

嗯，今天在看《Android艺术开发探索》这本书的时候，看到了四大组件的工作原理，关于 Broadcast，有这样的一个疑问：

> 当App安装了没有运行的时候，如果App内部有静态注册的 Receiver，那么当一个满足 filter 条件的广播被发送的时候，是不是需要先启动这个 App，创建一个进程？关于 Activity 启动的时候，创建进程的代码，我还是有点印象的，但是 Broadcast 就完全不了解，所以既然有了疑问，那么就肯定要去找出答案才行。

下面所写的都是我翻了很多文章才记录下来的，嗯，希望通过写这次记录，能够对 Broadcast 有更好的理解，毕竟，我们项目里面用的很浅。

**PS**：我刚开始是翻了很多文章，有 《Android 应用的安装流程解析》，《Broadcast 的注册》，《Broadcast 的工作原理》之类的但是都没有我想要的。但是最后我找了一篇非常牛逼的文章[品茗论道说广播(Broadcast内部机制讲解)](https://my.oschina.net/youranhongcha/blog/226274) ，看了之后恍然大悟，写的真的思路清晰。



## 概述

我们知道，Activity 的都是由 AMS 管理的，其实 Broadcast 也是。

![](Broadcast 探究\201441_jyH9_174429.png)



在Android系统中，接收广播的组件叫作receiver，而且**receiver还分为动态和静态的**。动态receiver是在运行期通过调用registerReceiver()注册的，而静态receiver则是在AndroidManifest.xml中声明的。

除了这个，Android 还有有序广播和普通广播的概念。有序广播就是广播接收者会按照优先级依次接受广播，而且还可以中断广播，让后面的收不到。



## 两种 Receiver

这里我们先介绍动态的 Receiver。

### 动态 Receiver

动态的 Receiver 就是在代码中注册的 Receiver，当App没有运行的时候（进程不存在），是无法接受到广播的，说的更直白一点，就是 AMS 那里没有这个 Receiver 相关的信息（这个后面会说到源码的东西）。

这里我们看一下注册相关的源码，因为有几个类非常的重要，毕竟我们阅读源码还是要对几个常见的类熟悉一下，以后阅读起来就会更快。