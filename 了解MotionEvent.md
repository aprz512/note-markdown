##了解MotionEvent

### MotionEvent事件对象

一般我们是在View的onTouchEvent方法中处理MotionEvent对象的.

```java
public boolean onTouchEvent(MotionEvent event)
```

在这里我们需要从一个MotionEvent对象中获得哪些信息呢?

###  

#### (1)首先应该是事件的类型吧?

可以通过**getAction()**,在android2.2之后加入多点触控支持之后使用**getActionMasked()**方法.

这两个方法的区别见后文.

主要的事件类型有:

- **ACTION_DOWN**: 表示用户开始触摸.

- **ACTION_MOVE**: 表示用户在移动(手指或者其他)

- **ACTION_UP**:表示用户抬起了手指 

- **ACTION_CANCEL**:表示手势被取消了,

一些关于这个事件类型的讨论见:<http://stackoverflow.com/questions/11960861/what-causes-a-motionevent-action-cancel-in-android>



还有一个不常见的:

- **ACTION_OUTSIDE**: 表示用户触碰超出了正常的UI边界.

但是对于多点触控的支持,Android加入了以下一些事件类型.来处理,如另外有手指按下了,

有的手指抬起来了.等等:

- **ACTION_POINTER_DOWN**:有一个非主要的手指按下了.

- **ACTION_POINTER_UP**:一个非主要的手指抬起来了
- 

#### (2)事件发生的位置,x,y轴

**getX()** 获得事件发生时,触摸的中间区域在屏幕的X轴.

**getY()** 获得事件发生时,触摸的中间区域在屏幕的X轴.

在多点触控中还可以通过:    

**getX(int pointerIndex)** ,来获得对应手指事件的发生位置. 获得Y轴用**getY(int pointerIndex)**



#### (3)其他属性

**getEdgeFlags():** 当事件类型是ActionDown时可以通过此方法获得,手指触控开始的边界. 如果是的话,有如下几种值:EDGE_LEFT,EDGE_TOP,EDGE_RIGHT,EDGE_BOTTOM



### 一些讨论

(1)首先是MotionEvent 中**getAction()**与**getActionMasked()**的区别:

 首先看代码:

```java
	/**
     * Bit mask of the parts of the action code that are the action itself.
     */
    public static final int ACTION_MASK = 0xff;

	/**
     * Return the kind of action being performed.
     * Consider using {@link #getActionMasked} and {@link #getActionIndex} to retrieve
     * the separate masked action and pointer index.
     * @return The action, such as {@link #ACTION_DOWN} or
     * the combination of {@link #ACTION_POINTER_DOWN} with a shifted pointer index.
     */
    public final int getAction() {
        return mAction;
    }

  	/**
     * Return the masked action being performed, without pointer index information.
     * Use {@link #getActionIndex} to return the index associated with pointer actions.
     * @return The action, such as {@link #ACTION_DOWN} or {@link #ACTION_POINTER_DOWN}.
     */
    public final int getActionMasked() {
        return mAction & ACTION_MASK;
    }
```

上面的代码是基于android2.2的,注释是android4.X中最新的.



他们有什么区别呢？如果mAction的值是在0x00到0xff之间的话。getAction()返回的值，和

getActionMasked()的返回的值是一样的。



**（Q1）**那什么时候返回的值是一样的呢？即当mAction值大于0xff时，那什么时候会大于0xff呢？

这就是是当有多点触控时。当有多点触控时。

mAction的低8位即0x00到0xff用来表示动作的类型信息。

例如：

```
MotionEvent#ACTION_DOWN的值是 0,即0x00。

MotionEvent#ACTION_UP的值是 1，即0x01。
```

等等。

但是，我们知道Android是支持多点触控的，那么怎么知道这个一个MotionEvent是哪一个触控点触发的呢？那么就还需要MotionEvent带有触控点索引信息。

Android的解决方案时在；mAction的第二个8位中存储。

例如：

```
如果mAction的值是0x0000，则表示是第一个触控点的ACTION_DOWN操作。

如果mAction的值是0x0100呢，则表示是第二个触控点的ACTION_DOWN操作。

第三个的ACTION_DOWN呢？相信你可以推出来是0x0200。
```

总而言之，mAction时的低8位（也就是0-7位）是动作类型信息。mAction的8-15位呢，是触控点的索引信息。（即表示是哪一个触控点的事件）。



**（Q2)**为什么不用两个字段来表示？

如：   int mAction，int mPointer。

mAction表示动作类型，mPointer表示第几个触控点。

因为，动作类型只要0-255就可以了，动作类型，mPointer也是。

只要一个字段（32位），否则需要两个字段(32*2=64位），即可以节约内存。又可以方便提高处理速度。

不过通常我们都是以不同的字段来存储不同的信息。但是在计算机内部他们还是变成了0,1。

计算机始终还是以位来存储信息的。如果我们多我熟悉以位为基本单位来理解信息的存储。对于理解android中的很多变量是很有帮助的。因为他其中的很多东西使用的这样的节约内在的技巧。

如onMeasure中的MeasureSpec。



先看关于这两个方法注释:

我简单的翻译如下:

```Java
	/**
     * action码的位掩码部分就是action本身
     */
    public static final int ACTION_MASK             = 0xff;

	/**
  返回action的类型,考虑使用getActionMasked()和getActionIndex()来获得单独的经过掩码的action和触控点的索引.
 @return action例如ACTION_DOWN或者ACTION_POINTER_DOWN与转换的触控点索引的合成值
     */
    public final int getAction() {
        return mAction;
    }

  	/**
   返回经过掩码的action,没有触控点索引信息.
   通过getActionIndex()来得到触控操作点的索引.
@return action,例如ACTION_DOWN,ACTION_POINTER_DOWN

 
     */
    public final int getActionMasked() {
        return mAction & ACTION_MASK;
    }
```

在上面的两个方法中注释出现差异的地方是对于ACTION_POINTER_DOWN的描述：

通过getAction()返回的ACTION_POINTER_DOWN的是与转换触控点索引的合成值，而getActionMasked()则就是一个ACTION_POINTER_DOWN的值:

 

这么来看我们知道一个action的代码值还包含了action是那个触控点的索引值:

现在我们对比来看看ACTION_MASK和ACTION_POINTER_INDEX_MASK

```Java
public static final int ACTION_MASK             = 0xff;
public static final int ACTION_POINTER_INDEX_MASK  = 0xff00;
```

还没有看出来什么吗?

您把ACTION_MASK看成是0x00ff，就知道了吧.

也就是说,一个MotionEvent中的action代码,

前8位是实实在在包含表示哪一个动作常量.

后八位呢就是包含了触控点的索引信息.

因为ACTION_MASK = 0x00ff所以,经过ACTION_MASK掩码过后的action码就没有索引信息了.

**如何得索引值呢?**

原理:

先将action跟0xff00相与清除前8位用于存储动作常量的信息,

然后将action右移8位就可以得到索引值了.

我们就可以自己想办法得到索引信息了.

即先对action用ACTION_POINTER_INDEX_MASK进行掩码处理,

即  maskedIndex = action&ACTION_POINTER_INDEX_MASK = action&0xff00

这各掩码也就是将action这个数的前8位清零.

然后再将maskedIndex向右移8位就能够得到索引值了.

再看看android真实是怎么做的吧,

用于右移8位的常量.

```java
	/**
     * Bit shift for the action bits holding the pointer index as
     * defined by {@link #ACTION_POINTER_INDEX_MASK}.
     */
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
```

再年得到索引值方法源代码,如下:

```
public final int getActionIndex() {
        return (mAction & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
    }
```

 

**为什么要有索引信息?**

因为，这样说吧，android中，当有触摸事件发生时(假设已经注册了事件监听器)，调用你注册监听器中的方法onTouch(MotionEvent ev);传递了一个MotionEvent的对象过来.

但是，想想，上面只传递进来一个MotionEvent过来，如果只是单点触控那是没有问题.

问题就是当你多个手指触控的时候也是只传递这一个MotionEvent进来，这个时候，你当然想知道每个手指的所对应的触控点数据信息啦。

所以MotionEvent中有就要索引信息了.

事件是你可以很容易通过API看到,MotionEvent还包含了移动操作中其它历史移动数据.

方便处理触控的移动操作.

android sdk对于这个类的描述中就有这么一句:

> For efficiency, motion events with ACTION_MOVE may batch together multiple movement samples within a single object.

我翻译下:"出于效率的考虑,事件代码为ACTION_MOVE的Motion,会在一个MotionEvent对象中包含多个移动数据采样."

 

现在我们对于MotionEvent有了初步的了解了。

PS:

我发现android4中MotionEvent中的代码大多变成了原生代码了:

例如如getX(int)在2.2中是这样的:

```
public final float getX(int pointerIndex) {
        return mDataSamples[(pointerIndex*NUM_SAMPLE_DATA) + SAMPLE_X];
    }
```

但到了4.x是这样的了:

```
public final float getX(int pointerIndex) {
        return nativeGetAxisValue(mNativePtr, AXIS_X, pointerIndex, HISTORY_CURRENT);
    }
```

是不是进步了呢?哈哈!



参考文档：

<https://my.oschina.net/banxi/blog/56421>

