# Intent 数据传输限制

当我们启动一个Activity的时候，这个过程是需要与AMS进行进程之间通信的。

启动 Activity 的时候，需要使用Intent，所以我们在intent中携带的数据也要从APP进程传输到AMS进程，再由AMS进程传输到目标Activity所在进程。

通过阅读 Activity 的启动过程，我们知道app与AMS 的进程通信是使用Binder来完成的，看一张图：

![](F:\note-markdown\Intent 数据传输限制\1460468-1f61b4f411c35094.webp)

普通的由Zygote孵化而来的用户进程，所映射的Binder内存大小是不到1M的，准确说是 **(1x1024x1024) - (4096 x2)** ，但是由于Intent中还有其他的信息，所以能够放入的数据肯定比这个值还要小。

这个限制定义在`frameworks/native/libs/binder/processState.cpp`类中：

```c++
#define BINDER_VM_SIZE ((1*1024*1024) - (4096 *2))
```

如果传输说句超过这个大小，系统就会报错，因为Binder本身就是为了进程间频繁而灵活的通信所设计的，并不是为了拷贝大数据而使用的。

PS：**注意上面的图：Binder进行数据传递的时候，只需要一次数据拷贝。**

这是因为Binder借助内存映射，在`内核空间`和接收方的`用户空间`的数据缓存区做了一层内存映射。也就是说，在发送方将数据拷贝到内存空间的时候，内核空间的这部分地址同时也会被映射到接收方的内存缓存中，这样子，就少了一次从内核空间拷贝到用户空间。



### 代替方案

- 写入临时文件或者数据库，通过FileProvider将该文件或者ContentProvider通过Uri发送至目标。一般适用于不同进程，比如分离进程的UI和后台服务，或不同的App之间。之所以采用FileProvider是因为7.0以后，对分享本App文件存在着严格的权限检查。

  因为 ContentProvider 是使用匿名共享内存来交换数据的，所以没有限制。

- 同一个进程中，通过单利来传递数据。





### 参考文档

<https://www.jianshu.com/p/4537270be897>