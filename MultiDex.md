## MultiDex 

### 出现的原因

单个Dex文件中，method个数采用使用原生类型short来索引，即2个字节最多65536个method，field、class的个数也均有此限制。

对于Dex文件，则是将工程所需全部class文件合并且压缩到一个DEX文件期间，也就是使用Dex工具将class文件转化为Dex文件的过程中， 单个Dex文件可被引用的方法总数（自己开发的代码以及所引用的Android框架、类库的代码）被限制为65536。

这就是65535问题的根本来源。



### Android 5.0 之前版本的 Dalvik 可执行文件分包支持

Android 5.0（API 级别 21）之前的平台版本使用 Dalvik 运行时来执行应用代码。默认情况下，Dalvik 限制应用的每个 APK 只能使用单个 `classes.dex` 字节码文件。想要绕过这个限制，就需要使用Google提供的[Dalvik 可执行文件分包支持库](https://developer.android.com/tools/support-library/features.html?hl=zh-cn#multidex)。

因为Android系统在启动应用时只加载了主dex（Classes.dex），其他的 dex 需要我们在应用启动后进行动态加载安装。

```java
public class MyApplication extends SomeOtherApplication {
  @Override
  protected void attachBaseContext(Context base) {
     super.attachBaseContext(base);
     // 加载其他的dex文件
      // 原理就是：通过反射手动添加其他Dex文件中的class到 ClassLoader 的 pathList字段中，就可以实现类的动态加载
     MultiDex.install(this);
  }
}
```

这个过程一般只在第一次冷启动应用的时候比较耗时，除了要抽取其他的 dex 文件，Dalvik 虚拟机还会使用 dex2oat 将 dex 文件优化成 odex 文件，将生成的文件放在手机的data/dalvik-cache目录下，便于以后使用。以后再次运行时，因为不用再次生成 odex，所以运行速度很快。



### Android 5.0 及更高版本的 Dalvik 可执行文件分包支持

Android 5.0（API 级别 21）及更高版本使用名为 ART 的运行时，后者原生支持从 APK 文件加载多个 DEX 文件。ART 在应用安装时执行预编译，扫描 `classesN.dex` 文件，并将它们编译成单个 `.oat` 文件，供 Android 设备执行。因此，如果您的 `minSdkVersion` 为 21 或更高值，则不需要 Dalvik 可执行文件分包支持库。



### MultiDex.install 带来的问题

当我们使用了分包支持库之后，在运行app时可能会出现这样的错误



####  java.lang.NoClassDefFoundError

出现这个问题的原因是：在应用启动期间，需要该类，但是这个类不在 MainDex 中，所以解决方案就是将这个类放到 MainDex 中。

具体可以参考官方文档： [声明主 DEX 文件中需要的类](https://developer.android.com/studio/build/multidex?hl=zh-cn#keep) 。



#### dexopt failed

dalvik的dexopt程序分配一块内存来统计你的app的dex里面的classes的信息，由于classes太多方法太多超过这个linearAlloc 的限制 。

解决方案就是减少 dex 的大小。

```groovy
android.applicationVariants.all {
    variant ->
        dex.doFirst{
            dex->
            if (dex.additionalParameters == null) {
                dex.additionalParameters = []
            }
                dex.additionalParameters += '--set-max-idx-number=48000'
       }
}
```



#### 启动过程中 ANR

启动期间在设备数据分区中安装 DEX 文件的过程相当复杂，如果辅助 DEX 文件较大，可能会导致应用无响应 (ANR) 错误。在此情况下，您应该[通过 ProGuard 应用代码压缩](https://developer.android.com/studio/build/shrink-code.html?hl=zh-cn)以尽量减小 DEX 文件的大小，并移除未使用的那部分代码。

这个是官方给出的建议，但是显然不太合适中国程序员国情，所以需要使用别的方案：

- [异步加载方案](<https://www.cnblogs.com/CharlesGrant/p/5112597.html>)
- [多进程加载方案](<https://www.jianshu.com/p/c2d7b76ff063>)
- 插件化

这两个实现其实差不多，都是提供了一种避免在其他Dex文件未加载完成时，造成的ClassNotFoundException的手段。



### 分包后 MainDex 仍然爆掉

- [MainDex 瘦身](<https://juejin.im/post/5c5bee986fb9a049bc4d1b58#heading-1>)
- 自行分包，直接指定哪些类放到 MainDex