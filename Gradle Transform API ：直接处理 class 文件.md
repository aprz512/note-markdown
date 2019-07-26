# Gradle Transform API ：直接修改 class 文件

首先，我要说的是，我没想到写这篇文章会遇到那么多的难点。其次在写这篇文章的时候，我还是处于一个半吊子的状态，但是我想应该还是会比现有的大部分blog要好的多。我几乎将Google到的索引到的前几页文章全部看了一遍，但是大部分都是相同的内容，就只有一篇我印象比较深，写的比较全面，但是我仍然还有很多疑问。

下面的文章我会提出我自己在学习这个知识点时想要问的问题，有些问题我可以自己解答，但是有些还是摸棱两可。

首先列出阅读这篇文章所需要的基础知识，如果你连这些都没有掌握的话，就不建议往下看了，会很痛苦，除非你只是想了解一下。

- Goorvy 基本语法
- Gradle 构建
- [ASM](https://my.oschina.net/ta8210?tab=newest&catalogId=388001)

前两个知识点有一个快速掌握的方法，阅读这个 [PDF](http://wiki.jikexueyuan.com/project/deep-android-gradle/) 文件，写的还是非常不错的，我花了一个小时看完，我看的比较快，因为我看过《Gradle权威指南》这本书。

好了，从这里开始，我就当你已经掌握了上面的相关知识点。



### Gradle 工作流程

Gradle 是一个框架，它定义一套自己的游戏规则。我们要玩转 Gradle，必须要遵守它设计的规则。

下面我们来讲讲 Gradle 的基本组件：

- Gradle 中，**每一个待编译的工程都叫一个 Project**。每一个 Project 在构建的时候都包含一系列的 Task。比如
  一个 Android APK 的编译可能包含：Java 源码编译 Task、资源编译 Task、JNI 编译 Task、lint 检查 Task、打包生成 APK 的 Task、签名 Task 等。
- **一个 Project 到底包含多少个 Task，其实是由编译脚本指定的插件决定**。插件是什么呢？插件就是用来定义 Task，并具体执行这些 Task 的东西。

Gradle 作为框架，它负责定义流程和规则，而具体的编译工作则是通过插件的方式来完成的。比如编译 Java 有 Java 插件，编译 Groovy 有 Groovy 插件，编译 Android APP 有 Android APP 插件，编译 Android Library 有 Android Library 插件。好了，到现在为止，你知道 Gradle 中每一个待编译的工程都是一个 Project，一个具体的编译过程是由一个一个的 Task 来定义和执行的。



![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获.PNG)



在 Android Stuido 中，每个 moudle  都有自己的 build.gradle 文件。在构建的时候，**每一个 build.gradle 文件都会转换成一个 Project 对象**。

一个 Project 会包含若干 Tasks。另外，由于 Project 对应具体的工程，所以需要为 Project 加载所需要的插件，比如：为 Java 工程加载 Java 插件，为 Android 工程加载 Android 插件。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获3.PNG)

这里就为该工程加载了 3 个插件。一般的插件可以直接使用，但是有的插件可能还需要配置扩展。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获2.PNG)

如上图所示，这是属于  `com.android.application` 插件的一个 android 扩展。在这里我们就可以配置该扩展的一些属性。

了解了这些，我们继续。

Gradle 工作包含三个阶段：

1. 首先是`Initiliazation`阶段：对我们前面的 multi-project build 而言，就是执行 settings.gradle。
2. 然后是 `Configration` 阶段：`Configration` 阶段的目标是解析每个 project 中的 build.gradle。在这两个阶段之间，我们可以加一些定制化的Hook。这当然是通过 API 来添加的。
3. `Configuration` 阶段完了后，整个 build 的 project 以及内部的 Task 关系就确定了。前面说过，一个Project 包含很多 Task，每个 Task 之间有依赖关系。Configuration 会建立一个有向图来描述 Task 之间的依赖关系。所以，我们可以添加一个 HOOK，即当 Task 关系图建立好后，执行一些操作。
4. 最后一个阶段就是执行任务了。



### Transform API 为什么可以修改 class 文件

我们知道，一个 project 的构建是由很多 task 组成的，而这些 task 是有依赖关系的。我们结合一下 App 的打包流程来看一下，各个 task 是发生在什么时候。

在 App 打包的时候，首先需要先将 java 文件编译为 class 文件（这里不关心一些其他的 AIDL 之类的），然后将 jar 与 class 文件达成 dex 文件。由于工程是 Gradle 构建的，Gradle 的构建是基于 Task 的，所以这些编译java文件，打包 class 文件都是在 task 中执行的。

在构建的过程中，这些 Task 都是由 TaskManager 管理的：

> com.android.build.gradle.internal.TaskManager#createCompileTask

```java
    protected void createCompileTask(@NonNull VariantScope variantScope) {
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }
```

这里是先执行了 javac 的编译任务，然后执行 post 编译任务。

> com.android.build.gradle.internal.TaskManager#createPostCompilationTasks

```java
    public void createPostCompilationTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        ...
        // ----- External Transforms -----
        // 添加自定义的 Transform
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size() ; i < count ; i++) {
            Transform transform = customTransforms.get(i);
            AndroidTask<TransformTask> task = transformManager
                    .addTransform(tasks, variantScope, transform);
            ...
        }
        ...
        // ----- Minify next -----
        // minifyEnabled 为 true 表示开启混淆
        // 添加 Proguard Transform
        if (isMinifyEnabled) {
            boolean outputToJarFile = isMultiDexEnabled && isLegacyMultiDexMode;
            createMinifyTransform(tasks, variantScope, outputToJarFile);
        }
        ...
        
        // non Library test are running as native multi-dex
        if (isMultiDexEnabled && isLegacyMultiDexMode) {
            ...
            // 添加 JarMergeTransform
            // create a transform to jar the inputs into a single jar.
            if (!isMinifyEnabled) {
                // merge the classes only, no need to package the resources since they are
                // not used during the computation.
                JarMergingTransform jarMergingTransform = new JarMergingTransform(
                        TransformManager.SCOPE_FULL_PROJECT);
                variantScope.addColdSwapBuildTask(
                        transformManager.addTransform(tasks, variantScope, jarMergingTransform));
            }

            // 添加 MultiDex Transform
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            MultiDexTransform multiDexTransform = new MultiDexTransform(
                    variantScope,
                    extension.getDexOptions(),
                    null);
            multiDexClassListTask = transformManager.addTransform(
                    tasks, variantScope, multiDexTransform);
            multiDexClassListTask.optionalDependsOn(tasks, manifestKeepListTask);
            variantScope.addColdSwapBuildTask(multiDexClassListTask);
        }
        ...
        // 添加 Dex Transform
        // create dex transform
        DefaultDexOptions dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
        ...
    }
```

这个方法会遍历所有的 Transform，然后一一添加进 TransformManager。 添加完自定义的 Transform 之后，再添加 Proguard, JarMergeTransform, MultiDex, Dex 等 Transform。所以 Transform API 可以接触到 class 文件，这个时机是最好的处理时机。

### Transform API 的使用

根据 Transform API 的 [文档](http://tools.android.com/tech-docs/new-build-system/transform-api)，我们先自定义一个 Transform，然后将这个 Transform 注册到 android 扩展中即可。

自定义 Transform 我们后面会说到，这里我们想说如何注册。文档中对注册的描述只有一句话：

> To insert a transform into a build, you simply create a new class implementing one of the Transform interfaces, and register it with android.registerTransform(theTransform) or android.registerTransform(theTransform, dependencies).

但是实际上设计到的问题很多。比如：如何拿到 android 扩展？？？

要拿到 android 扩展，**一般我们是使用自定义一个插件的方式**。由于 android 扩展中提供了 `registerTransform ` 方法，所以是可以直接在 build.gradle 中调用的，但是它蛋疼的地方是这样搞的话，所有的逻辑都糅合在一起了。



下面我们介绍如何自定义一个插件，但是我们先来了解一下 Gradle 编程模型会好很多。 

### Gradle 编程模型

Gradle 基于 Groovy，Groovy 又基于 Java。所以，Gradle 执行的时候和 Groovy 一样，会把脚本转换成 Java对象。Gradle 主要有三种对象，这三种对象和三种不同的脚本文件对应，在 gradle 执行的时候，会将脚本转换成对应的对端：

- Gradle 对象：当我们执行 gradle xxx 或者什么的时候，**gradle 会从默认的配置脚本中构造出一个 Gradle对象**。在整个执行过程中，只有这么一个对象。Gradle 对象的数据类型就是 Gradle。我们一般很少去定制这个默认的配置脚本。

- Project 对象：每一个 build.gradle 会转换成一个 Project 对象。

- Settings 对象：显然，每一个 **settings.gradle 都会转换成一个 Settings 对象**。

  

### 自定义 Gradle 插件

#### 新建一个 module

删除其他目录，只留下 src/main 目录与 build.gradle 文件。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获4.PNG)

然后在 main 目录下面，新建 groovy 目录 与 resouce 目录。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获5.PNG)

**groovy 就是用来放 groovy 文件的，与 java 目录的作用一样**。

resources 目录是用来配置插件的相关信息的。接着我们在 resources 目录下新建一个 META-INF 目录，再在 META-INF 目录下新建一个 gradle-plugins 目录。然后在 gradle-plugins 新建一个 aaa.bbb.properties 文件。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获6.PNG)

需要注意的是，**这个文件的名字很重要，aaa.bbb 是你定义的插件的名字**。什么意思呢？还记得我们是如何加载一个插件的吗？

```groovy
apply plugin: 'log.inject'
```

看，这个文件的名字就是加载插件时用到的名字。

文件的内容，比较简单，由于我们是要自定义一个插件，所以需要在这里声明一下插件的名字。

```properties
implementation-class=com.aprz.log.LogPlugin
```

这样，我们的第一步就完成了。



#### 实现一个插件类

一般的，我们需要自定义一个东西，都会有一个父类给我们使用，插件也不例外。我们需要实现 Plugin 接口：

```groovy
class LogPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        println('welcome to log inject plugin...')

        // 找到项目中的 某个继承至 BaseExtension 的扩展
        def ext = project.extensions.getByType(BaseExtension)
        // 往该扩展中添加 transform
        // 这里其实就是将我们自定义的这个 transform 添加到了集合中
        // 但是这里让我想不明白的是，为什么这个添加 transform 的方法是在 extension 里面
        // 而不是 project 里面，如果像 Java 工程，没有使用有扩展的插件该怎么办

        // 查看源码发现了这样的代码：com.android.build.gradle.internal.TaskManager.createPostCompilationTasks
        // AndroidConfig extension = variantScope.getGlobalScope().getExtension();
        // 它获取到了 android 扩展，然后拿到了其中的所有 transform
        // 嗯，看来这个是针对 Android 构建的
        ext.registerTransform(new LogsTransform(project))
    }

}
```

当这个插件按被加载（build.gradle 执行到 `apply plugin: 'xxxx'  `）的时候，它的 apply 方法就会被调用，我们在这里可以注册我们的 Transform 了。其实如果不需要注册 transform，我们只想打印一下 log 的话，它也是一个插件，只不过是一个没啥屌用的插件而已。

要想自定义一个有用的插件，还需要对 Groovy 语法，gradle 文档有相当的了解才行。



#### 发布插件

前面我们自定义了一个插件，但是需要发布之后，自己以及别人的项目才能使用，这里简单的说一下，**如何发布到本地**自己使用，想要发布到 bintray 等网站，可以自行查阅文档。

为了方便，我是使用了第三方的辅助插件。首先我们在根目录的 build.gradle 中添加依赖：

> TransformAPIDemo\build.gradle

```groovy
    dependencies {
        classpath 'com.novoda:bintray-release:0.9'
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
```

然后在插件工程的 build.gradle 中添加配置：

> TransformAPIDemo\log_transform\build.gradle

```groovy
apply plugin: 'com.novoda.bintray-release'

uploadArchives {
    repositories {
        mavenDeployer {
            pom.groupId = 'com.aprz.log.inject'
            pom.artifactId = 'log'
            pom.version = '1.0.0'
            repository(url: uri('E:/maven/repository'))
        }
    }
}

publish {
    userOrg = 'aprz512'
    groupId = 'com.aprz.log.inject'
    artifactId = 'log'
    publishVersion = '1.0.0'
    desc = 'log inject demo'
    website = 'https://github.com/aprz512/Transform-API-demo'
    repoName = 'gradle_plugins'
}
```

`groupId`,`artifactId`,`version`这3个应该经常接触，就不说了，这里看 `repository` 的配置，是可以配置本地路径的。这里我配置的是 E 盘。执行发布命令：

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获7.PNG)

点击这个玩意，查看控制台输出：

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获8.PNG)

然后就可以在E盘看到发布的插件了。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\捕获9.PNG)



### 自定义 Transform

上面我们自定义插件时提到了 Transform 的注册，里面创建了一个 Transform，但是我们没有深究，这里会仔细的分析一下。

自定义 Transform 同样的也需要继承一个父类：

```groovy
class LogsTransform extends Transform {

    LogsTransform() {

    }

    @Override
    String getName() {
        return this.getClass().getSimpleName()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
    }
    
}

```

我们先用一张图来说明，transform 是如何工作的，然后再细说上面的每个方法是什么意思。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\6965.png)

Transform每次都是将**一个输入进行处理，然后将处理结果输出，而输出的结果将会作为另一个Transform的输入**。每个 Transform 会处理某些特定的资源流，如何指定需要处理的资源流是通过 `getInputTypes` 与 `getScopes` 一起决定的。

下面一一解释上面的几个方法：

- getName

  指明本Transform的名字，随意，但是**不能包含某些特殊字符，否则会报错**。

- getInputTypes

  指明Transform的输入类型，例如，返回 TransformManager.CONTENT_CLASS 表示配置 Transform 的输入类型为 Class。

- getScopes

  指明Transform的作用域，例如，返回 TransformManager.SCOPE_FULL_PROJECT 表示配置 Transform 的作用域为全工程。

- isIncremental

  指明是否是增量构建

- transform

  用于处理具体的输入输出，核心操作都在这里。上例中，配置 Transform 的输入类型为 Class， 作用域为全工程，因此在`transform`方法中，inputs 会传入工程内所有的 class 文件。

通过 Scope 和 ContentType 可以组成一个资源流。例如，PROJECT 和 CLASSES，表示了主项目中java 编译成的 class 组成的一个资源流。再如，SUB_PROJECTS 和 CLASSES ，表示的是本地子项目中的 java 编译成的 class 组成的一个资源流。Transform 用来处理和转换这些流。

查看我们的代码中指定的域与类型，表示我们处理的是整个工程的 java 编译成的 class 文件。

接下来，我们来实现一个实例，自定义一个 Transform，这个 Transform 的作用是可以给所有父类是 `androidx.appcompat.app.AppCompatActivity`的类的 onCreate 方法都加入一个 log 语句。比如，原来的代码是这样：

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
}
```

改变 class 之后应该是这样：

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("log_inject", "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
}
```

废话不多说，我们直接进入正题，由于 Transform 的主要内容都在 com.aprz.log.LogsTransform#transform 方法里面，我们就直接说这个方法：

> com.aprz.log.LogsTransform#transform

```java
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        // inputs 包含了 jar 包和目录。
        // 子 module 的 java 文件在编译过程中也会生成一个 jar 包然后编译到主工程中。
        transformInvocation.inputs.each {
            input ->

                // 遍历目录
                // 文件夹里面包含的是我们手写的类以及R.class、BuildConfig.class以及R$XXX.class等
                input.directoryInputs.each {
                    DirectoryInput directoryInput ->
                        directoryInput.file.eachFileRecurse {
                            File file ->
                                if (checkFileName(file.name)) {
                                    injectClassFile(file)
                                }
                        }
                        copyDirectory(directoryInput, transformInvocation.outputProvider)
                }


                // 遍历 jar，我们不需要对 jar 进行处理，所以直接跳过
                // 但是后面的 transform 可能需要处理，所以需要从输入流原封不动的写到输出流
                input.jarInputs.each {
                    jarInput ->
                        copyJar(jarInput, transformInvocation.outputProvider)
                }
        }
    }
```

需要注意的是，inputs 分为两种类型的资源，一种是目录（目录里面都是生成的 class 文件），一种是 jar 包，它们需要分开遍历。由于我们只需要要处理目录，所以只针对目录讲解。

首先，我们对目录集合进行遍历，在对集合中的每个目录进行递归处理，最终到每个 class 文件，拿到这个 class 文件，我们就可以使用 ASM 修改这个 class 文件了。

> com.aprz.log.LogsTransform#injectClassFile

```groovy
    static void injectClassFile(File file) {
        ClassReader classReader = new ClassReader(file.bytes)
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
        ClassVisitor cv = new LogClassVisitor(classWriter)
        // 访问者模式
        classReader.accept(cv, ClassReader.EXPAND_FRAMES)
        byte[] code = classWriter.toByteArray()
        FileOutputStream fos = new FileOutputStream(
                file.parentFile.absolutePath + File.separator + file.name)
        fos.write(code)
        fos.close()
    }
```

从这里开始，就是 ASM 的使用了。



### ASM 的使用

这里不介绍 API 的使用了，网上很多，还有官方文档，哪个都比我写得好。

这里介绍一下 ASM 的设计思想。

从上面的代码可以看到，`ClassReader` 的 `accept` 方法中传进来了一个参数`ClassVisitor`。在内部，`ClassVisitor`会不断的读取`ClassReader`的二进制byte[]，然后在解析后通过参数`classVisitor`的抽象`visitXXX`方法将属性全部转发出去，将其中的`visitXXX`方法按顺序抽离出来就是：

```java
classVisitor.visit(readInt(items[1] - 7), access, name, signature,superClass, interfaces);
classVisitor.visitSource(sourceFile, sourceDebug);
classVisitor.visitOuterClass(enclosingOwner, enclosingName,enclosingDesc);
classVisitor.visitTypeAnnotation(context.typeRef,context.typePath, readUTF8(v, c);
classVisitor.visitAttribute(attributes);
classVisitor.visitInnerClass(readClass(v, c),readClass(v + 2, c), readUTF8(v + 4, c),readUnsignedShort(v + 6));
classVisitor.visitField(access, name, desc,signature, value);
classVisitor.visitMethod(context.access,context.name, context.desc, signature, exceptions);
classVisitor.visitEnd();
```

这里有很多 visit 方法，但是与真正的 class 文件的处理有关的，只有几个，比如：visitMethod，visitField 等。 这些 visit 方法，会创建一个相对应的 Writer 对象。

Writer 对象是什么呢？？？Writer 对象是 Visitor 的一个实现类。

ASM 在读取一个 class 文件的时候，会创建出一个 ClassWriter 对象，但是它不会把对元素（字段，方法，注解）的访问放到 ClassWriter 中，而是使用 **访问者模式**，将对这个元素的访问放到了 ClassVisitor 中。为何这样做，可以去看访问者模式。

当 ClassWriter 处理 class 文件的字节码的时候，比如遇到了一个方法，就会调用 ClassVisitor 的 visitMethod 方法。而 visitor 的实现实际上是 writer 类，所以会调用 writer 类的对应方法。这些方法会将字节码对应的部分给保存起来。而最后 `classWriter.toByteArray` 就会将所有的 writer 保存的字节码全部合并在一起，生成一个新的 class 文件。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\ASM.png)

如果，我们自定义一个 MethodVisitor 就可以改变字节码。

![](F:\note-markdown\Gradle Transform API ：直接处理 class 文件\ASM2.png)

这里，我们覆盖 ClassVisitor 的 visitMethod 方法：

> com.aprz.log.asm.LogClassVisitor#visitMethod

```groovy
    @Override
    MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (!checkSuperClass(this.superName)) {
            return super.visitMethod(access, name, desc, signature, exceptions)
        }

        // 由于是一个例子，我们就只处理 onCreate 方法了，想要深入应该去研究一下一个正规的开源项目

        // 我的 demo 是 kotlin，tools里面有工具可以直接查看字节码，就非常方便
        if ('onCreate(Landroid/os/Bundle;)V' == (name + desc)) {

            println "log >>> method name = ${name + desc}"

            MethodVisitor methodVisitor = this.cv.visitMethod(access, name, desc, signature, exceptions)
            return new LogMethodVisitor(methodVisitor, name)
        }

        return super.visitMethod(access, name, desc, signature, exceptions)
    }
```

可以看到，我们返回了我们自己定义的 LogMethodVisitor，将原来的 MethodVisitor 作为成员变量保存起来 。当 MethodVisitor 的相关方法被调用的时候，实际上会调用 LogMethodVisitor 的方法。这样，我们就可以搞事情了。比如：在刚进入方法的时候，会触发 visitCode 的调用：

> com.aprz.log.asm.LogMethodVisitor#visitCode

```groovy
    /**
     *    L2
     *     LINENUMBER 13 L2
     *     LDC "log_inject"
     *     LDC "onCreate"
     *     INVOKESTATIC android/util/Log.e (Ljava/lang/String;Ljava/lang/String;)I
     *     POP
     */
    @Override
    void visitCode() {
        super.visitCode()
        // 在方法之前插入 Log.e("", "")
        // 这两个是参数
        this.mv.visitLdcInsn('log_inject')
        this.mv.visitLdcInsn(this.name)
        this.mv.visitMethodInsn(Opcodes.INVOKESTATIC, 'android/util/Log', 'e', '(Ljava/lang/String;Ljava/lang/String;)I', false)
        // 这里的用法有点奇怪，还需要研究一下
        // visitXXX 实际上会触发 MethodWriter 的方法，这些方法会将我们想要写入的字节码存放起来
        // 最后统一的写入到输出的 class 文件中
    }
```

在这里，我们就可以插入我们的字节码了。

到这里，东西就都介绍完毕了，只说了皮毛，要深入还是要看一个真正的项目才行。

### demo

[**Transform-API-demo**](https://github.com/aprz512/Transform-API-demo/tree/master)