## 	Kotlin learn



### Kotlin 泛型通配符 in 与 out

在Java中，通配符的使用通常有以下两种方式：

```java
? extends T
? super T
```

从字面上看，还是非常容易理解的。但是容易让人搞混的是“具有泛型类型的类之间的关系”。

比如：

```java
Array<String> sArr;
Array<Object> oArr;
```

这两者之间是没有什么关系的，尽管Object是String的父类。

将 sArr 赋值给 oArr 是禁止的，因为很容易被误用，导致类型转换异常：

```java
Array<String> sArr;
Array<Object> oArr;
oArr = sArr; // error
oArr.put(0, obj);
String str = sArr.get(0); // class cast error
```

仔细思考，储存 Object 的数组也是可以储存 String 的，那么问题就是出现在对 oArr 执行了 put 操作，如果我使用语法对 oArr 进行约束，让它不能执行 put 操作不就可以了吗？

Kotlin 中使用 out 关键字来实现这样的约束（out 对应于 ？ extend T）：

```java
fun copy(from : Array<out Any>, to:Array<Any>) {
    from.set(1, "") // error
}
```

这里对 from 参数进行了泛型约束，此时 from 的 set 方法不能设置任何东西，就相当于避免了类型转换。我们使用这个方法的时候，就可以传给任何泛型，from 都可以接收。

in 与 out 类似，但是表示相反的意思 （in 对应于 ？ super T）。

```java
fun fill(dest:Array<in String>, value:String) {}
```



### 延迟初始化

Koltin中属性在声明的同时也要求要被初始化，否则会报错。

```kotlin
private var name0: String //报错
private var name1: String = "xiaoming" //不报错
private var name2: String? = null //不报错
```

可是有的时候，**我并不想声明一个类型可空的对象，而且我也没办法在对象一声明的时候就为它初始化**，那么这时就需要用到Kotlin提供的**延迟初始化**。

Kotlin中有两种延迟初始化的方式。一种是**lateinit var**，一种是**by lazy**。

#### lateinit var

```kotlin
private lateinit var name: String
```

`lateinit` 只能用来修饰类属性，不能用来修饰局部变量，并且只能用来修饰对象，不能用来修饰基本类型(因为基本类型的属性在类加载后的准备阶段都会被初始化为默认值)。

`lateinit` 的作用也比较简单，就是让编译期在检查时不要因为属性变量未被初始化而报错。

Kotlin相信当开发者显式使用 `lateinit` 关键字的时候，他一定也会在后面某个合理的时机将该属性对象初始化的。

#### by lazy

by lazy本身是一种属性委托。属性委托的关键字是`by`。by lazy 的写法如下：

```kotlin
//用于属性延迟初始化
val name: Int by lazy { 1 }

//用于局部变量延迟初始化
public fun foo() {
    val bar by lazy { "hello" }

    println(bar)
}
```

以下以`name`属性为代表来讲解`by lazy`的原理，局部变量的初始化也是一样的原理。

`by lazy`要求属性声明为`val`，即不可变变量，在java中相当于被`final`修饰。

这意味着该变量一旦初始化后就不允许再被修改值了(基本类型是值不能被修改，对象类型是引用不能被修改)。`{}`内的操作就是返回唯一一次初始化的结果。

`by lazy`可以使用于类属性或者局部变量。

字节码分析链接：https://juejin.im/post/5affc369f265da0b9b079629



### @JvmStatic

**指定从该元素中生成静态方法。需要注意：此注解只能用于被object关键字修饰的类的方法，或者companion object中的方法**

```kotlin
object A {
    @JvmStatic fun a() {}
}
```

```kotlin
class A {
    companion object {
        @JvmStatic fun a() {}
    }
}
```

也可以修饰属性：

```kotlin
class AnnotationTest{
   companion object {
       @JvmStatic
       var name:String = ""
   }
}
```

会自动生成静态的 get 与 set 方法，由于半身对象的特殊使用方式，会给 AnnotationTest 也生成一个 name 变量以及 get 与 set 方法。



### let

let扩展函数的实际上是一个作用域函数，当你需要去定义一个变量在一个特定的作用域范围内，let函数的是一个不错的选择；let函数另一个作用就是可以避免写一些判断null的操作。

1、let函数的使用的一般结构

```kotlin
object.let{
    it.todo()//在函数体内使用it替代object对象去访问其公有的属性和方法
    ...
}
```

//另一种用途 判断object为null的操作

```kotlin  
object?.let{//表示object不为null的条件下，才会去执行let函数体
	it.todo()
}
```

2、let函数底层的inline扩展函数+lambda结构

```kotlin
@kotlin.internal.InlineOnly
public inline fun <T, R> T.let(block: (T) -> R): R = block(this)
```


3、let函数inline结构的分析

​	从源码let函数的结构来看它是只有一个lambda函数块block作为参数的函数,调用T类型对象的let函数，则该对象为函数的参数。在函数块内可以通过 it 指代该对象。返回值为函数块的最后一行或指定return表达式。

4、let函数的kotlin和Java转化

 //kotlin

```kotlin
 fun main(args: Array<String>) {
    val result = "testLet".let {
        println(it.length)
        1000
    }
    println(result)
 }
```

 //java

```java
 public final class LetFunctionKt {
   public static final void main(@NotNull String[] args) {
      Intrinsics.checkParameterIsNotNull(args, "args");
      String var2 = "testLet";
      int var4 = var2.length();
      System.out.println(var4);
      int result = 1000;
      System.out.println(result);
   }
}
```


5、let函数适用的场景

场景一: 最常用的场景就是使用let函数处理需要针对一个可null的对象统一做判空处理。

场景二: 然后就是需要去明确一个变量所处特定的作用域范围内可以使用

6、let函数使用前后的对比

没有使用let函数的代码是这样的，看起来不够优雅

```kotlin
mVideoPlayer?.setVideoView(activity.course_video_view)
mVideoPlayer?.setControllerView(activity.course_video_controller_view)
mVideoPlayer?.setCurtainView(activity.course_video_curtain_view)
```


使用let函数后的代码是这样的

```kotlin
mVideoPlayer?.let {
    it.setVideoView(activity.course_video_view)
    it.setControllerView(activity.course_video_controller_view)
    it.setCurtainView(activity.course_video_curtain_view)
}
```



### 属性委托

```kotlin
class Example {
    // 这里也可以是表达式
    var p: String by Delegate()
}
```

```kotlin
class Delegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return "$thisRef, thank you for delegating '${property.name}' to me!"
    }
 
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        println("$value has been assigned to '${property.name}' in $thisRef.")
    }
}
```

当我们访问 p 时：

```kotlin
var e = Example()
e.p // 会触发 Delegate 的 getValue 方法
e.p = xxx // 会触发 Delefate 的 setValue 方法
```

在Android中，可以使用它写一个自动回收的例子：

```kotlin
class AutoClearedValue<T : Any>(val fragment: Fragment) : ReadWriteProperty<Fragment, T> {
    private var _value: T? = null

    init {
        // 监听 fragment 的声明周期，走到 onDestroy 的时候，将引用设置为 null
        fragment.lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                _value = null
            }
        })
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return _value ?: throw IllegalStateException(
            "should never call auto-cleared-value get when it might not be available"
        )
    }

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        _value = value
    }
}

```





参考链接：

https://blog.csdn.net/u013064109/article/details/78786646#2