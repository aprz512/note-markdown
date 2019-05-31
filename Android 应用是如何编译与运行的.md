## Android 应用是如何编译与运行的

Android Studio 负责如何构建与部署我们的应用。但是你有没有想过当你按下Run按钮时发生了什么？

### 构建

#### Java compilation

我们的代码是用Java编写的。但是，Java代码的编译和运行方式与Web应用程序相同吗？

Android应用程序的编译过程与其他Java应用程序有很大不同。

但是它们的开始过程都是一样的：

使用javac命令将Java源代码文件编译为.class文件。

![](F:\note-markdown\Android 应用是如何编译与运行的\javaCompile.png)

它会将下面的java代码：

```java
public MainActivity() {
  super();
  currentPosition = 0;
}
```

转换成这样的java字节码：

```
public com.hfad.bitsandpizzas.MainActivity();
  Code:
   0:	aload_0
   1:	invokespecial	#5; //Method android/app/Activity."<init>":()V
   4:	aload_0
   5:	iconst_0
   6:	putfield	#3; //Field currentPosition:I
   9:	return
```

#### Conversion to Dalvik bytecodes

.class文件包含标准的Oracle JVM Java字节码。但Android设备不使用此字节码格式。相反，Android有自己独特的字节码格式，称为Dalvik。

Dalvik字节码，与Oracle JVM字节码一样，是理论处理器的机器代码指令。

编译过程需要将.class文件和任何.jar库转换为包含Dalvik字节码的单个classes.dex文件。这是通过dx命令完成的：

![](F:\note-markdown\Android 应用是如何编译与运行的\dxConvert.png)

dx命令将所有.class和.jar文件拼接成一个以Dalvik字节码格式编写的classes.dex文件。

```
0x0000: iput-object v1, v0, Lcom/hfad/bitsandpizzas/MainActivity; com.hfad.bitsandpizzas.MainActivity$2.this$0 // field@4869
0x0002: invoke-direct {v0}, void java.lang.Object.<init>() // method@13682
0x0005: return-void
```

#### Put classes.dex and resources into a package file

然后将classes.dex文件和应用程序中的资源（如图像和布局）压缩为类似zip的文件，称为Android Package或.apk文件。这是通过 *Android Asset Packaging Tool* 或 aapt 完成的：

![](F:\note-markdown\Android 应用是如何编译与运行的\apkPackage.png)

这个步骤完成之后，.apk文件就可以安装了。但是，还有一个步骤需要做...

#### You might then also sign the .apk file

如果想要通过Google Play商店分发应用，则需要对其进行签名。对应用程序包进行签名意味着您在.apk中存储了一个附加文件，该文件基于.apk内容的校验和以及单独生成的私钥。

.apk文件使用标准的jarsigner工具，该工具是Oracle Java Development Kit的一部分。创建jarsigner工具是为了签署.jar文件，但它也可以使用.apk文件，因为它们也是压缩文件。 

如果您对.apk文件进行签名，则还需要通过名为zipalign的工具运行它，这将确保文件的压缩部分在字节边界上排列。 Android希望它们按字节对齐，以便它可以轻松读取它们而无需解压缩文件。

![](F:\note-markdown\Android 应用是如何编译与运行的\signApk.png)

网上还有上面所有步骤的详细图：

![](F:\note-markdown\Android 应用是如何编译与运行的\8f422997.png)

### 部署

#### The adb server starts if it’s not already running

该应用程序将通过Android Debug Bridge部署到Android设备。

在我们的开发端上开启一个 adb 服务进程，在 Android 设备上开启一个类似的 adb 服务（adbd）。

如果您的计算机上未运行adb进程，则adb命令将启动它。

![](F:\note-markdown\Android 应用是如何编译与运行的\adbUse.png)

adb进程将打开网络套接字，并在端口5037上侦听命令。您输入的每个adb命令都会将其指令发送到此端口。

#### The .apk file is transferred to the device

adb命令用于将.apk文件传输到Android设备上的文件系统中。该位置由应用程序的包名定义。

因此，例如，如果包是com.hfad.bitsandpizzas，则.apk文件将放在/data/app/com.hfad.bitsandpizzas中。

![](F:\note-markdown\Android 应用是如何编译与运行的\storeApk.png)

### 运行

Android应用程序的运行方式最近发生了变化。

从API级别21开始，旧的Dalvik虚拟机已被新的Android Runtime取代。

让我们看一下应用程序运行时一步一步发生的事情。

#### A user asks for an app to be launched

一个名为Zygote的过程用于启动应用程序。 Zygote是Android进程的不完整版本 —— 其内存空间包含任何应用程序所需的所有核心库，但它尚未包含特定于特定应用程序的任何代码。 Zygote使用fork系统调用创建自己的副本。

 Android是一个Linux系统，fork调用可以很快复制像Zygote这样的进程。**这就是使用Zygote进程的原因：复制像Zygote这样的半启动进程比从主系统文件加载新进程要快得多。** Zygote意味着您的应用程序启动速度更快。

![](F:\note-markdown\Android 应用是如何编译与运行的\forkProcess.png)

#### Android converts the .dex code to native OAT format

新的app进程现在需要加载我们的应用程序的代码。请记住，您的应用代码存储在.apk包中的classes.dex文件中。因此，会从.apk中提取classes.dex文件并放入一个单独的目录中。但是，不是简单地放置classes.dex文件的副本，Android会将classes.dex中的Dalvik字节码转换为本机机器码。

所有以Java代码开头的代码现在都转换为一段本机编译代码。从技术上讲，classes.dex将转换为ELF共享对象。 Android调用此库格式OAT，转换classes.dex文件的工具称为dex2oat。（**Android 5.0 开始，dex -> oat 这个转换过程发生在安装过程中**）

![](F:\note-markdown\Android 应用是如何编译与运行的\runDex2Oat.png)

转换后的文件存储在如下的目录中：

```
/data/dalvik-cache/x86/data@app@com.hfad.bitsandpizzas@base.apk@classes.dex
```

**该路径将包含应用程序的包名称，以确保它不会覆盖任何其他应用程序。**

转换后的代码将在特定于Android设备CPU的机器代码中。例如，如果Android设备是x86，则OAT文件将如下所示：

```
0x001db888:         85842400E0FFFF    	test    eax, [esp + -8192]
suspend point dex PC: 0x0000
GC map objects:  v0 (r5), v1 (r6)
0x001db88f:                 83EC1C    	sub     esp, 28
0x001db892:               896C2410    	mov     [esp + 16], ebp
0x001db896:               89742414    	mov     [esp + 20], esi
0x001db89a:               897C2418    	mov     [esp + 24], edi
0x001db89e:                   8BF8    	mov     edi, eax
0x001db8a0:                 890424    	mov     [esp], eax
...
```

#### The app loads the native library

然后将 native library 直接映射到应用程序进程的内存中。

![](F:\note-markdown\Android 应用是如何编译与运行的\mapOat.png)

此时开始，应用程序将启动初始界面，应用程序将出现在屏幕上。