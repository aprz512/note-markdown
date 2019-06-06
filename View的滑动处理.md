# View的滑动处理

## NestedScrolling

NestedScrolling 是从Lollipop开始出现的，用来专门处理嵌套滑动的一套机制。

NestedScrolling 仍然是基于View与ViewGroup的事件滑动机制，但是它将一个滑动事件的参与者分成了两个角色，child 与 parent。

比如：ScrollView 中嵌套了 RecyclerView，ScrollView 在这里扮演 parent，RecyclerView扮演 child。

要想触发 NestedScrolling，首先 child 需要要能够处理滑动事件，因为 NestedScrolling 的思想是：

> 由 child 来接收滑动事件，然后在它的 onTouchEvent 中，做滑动处理。
>
> 做任何处理之前，先询问 parent，看parent能不能处理

所以整个流程就是，在一个move产生后：

1. child 先询问 parent，能够消耗多少，没有消耗完

2. child 自己消耗，没有消耗完

3. child 再次询问 parent，我这还有没消耗完的，你能消耗多少，如果 parent 还是没有消耗完

4. child 自己处理

可以仔细思考一下整个处理流程，刚开始可能会觉得有些怪异，但是要想让嵌套滚动无缝衔接，这样的逻辑是必要的。

了解了 NestedScrolling 的流程之后，那么就会产生许多问题：

第一个是：**child 是如何认定 parent 的？**

答案就是通过接口：

```java
androidx.core.view.NestedScrollingChild
androidx.core.view.NestedScrollingParent
```

当然现在，这两个接口已经发展到第3个版本了，NestedScrollingChild3 与 NestedScrollingParent3。

在我们上面的例子中，RecyclerView 就需要实现 NestedScrollingChild 这个接口，ScrollView 需要实现 NestedScrollingParent 接口。

然后，通过循环遍历 parent 的方式找到实现了 NestedScrollingParent 接口的 parent ：

```java
            ViewParent p = mView.getParent();
            while (p != null) {
                if (parent instanceof NestedScrollingParent) {
                    ...
                    return true;
                }
                p = p.getParent();
            }
```

从这里，可以看出，嵌套关系不需要是直接关系，隔几层也没有问题。

当然，上面的代码是不需要我们自己实现的，Google已经替我们实现了两个工具类，后面会详细说到。



第二个是：**child 与 parent 是如何传递各自需要消耗的距离的？**

这个问题比较复杂了，虽然Google替我们实现了两个工具，但是遗憾的是，由于业务的逻辑的多样性，工具里面只封装了一些通用的操作，所以我们需要学习如何使用这两个工具来实现我们想要的效果。

但是幸运的是，使用这两个工具是有模板的，我们只要照着来，问题不大。



下面，我们就参考 RecyclerView 的做法来仔细说道说道。

### 先看ACTION_DOWN的处理

> androidx.recyclerview.widget.RecyclerView#onTouchEvent

```java
            case MotionEvent.ACTION_DOWN: {
                startNestedScroll(nestedScrollAxis, TYPE_TOUCH);
            } break;
```

一般的，我们在 ACTION_DOWN 事件中来开启嵌套滚动，那么具体怎么开启呢？使用工具类就好了：

> androidx.recyclerview.widget.RecyclerView#startNestedScroll(int, int)

```java
    @Override
    public boolean startNestedScroll(int axes, int type) {
        return getScrollingChildHelper().startNestedScroll(axes, type);
    }
```

可以看到，它是直接使用了 NestedScrollingChildHelper 的 startNestedScroll 方法。所以说，使用很简单。

但是这里需要注意了，startNestedScroll 方法**内部会先判断该控件是否开启了支持嵌套滚动**，如果没有开启的话也是不行的，具体请看 NestedScrollingChild 接口的 isNestedScrollingEnabled 方法，要想支持嵌套滚动，这个方法返回 true 就好了。

然后，startNestedScroll 方法**内部还会调用 ViewParentCompat.onStartNestedScroll(p, child, mView, axes, type) 这个方法**。

> androidx.core.view.ViewParentCompat#onStartNestedScroll(android.view.ViewParent, android.view.View, android.view.View, int, int)
>
> 这个方法里面做了一些转发操作。

```java
        if (parent instanceof NestedScrollingParent2) {
            // First try the NestedScrollingParent2 API
            return ((NestedScrollingParent2) parent).onStartNestedScroll(child, target,
                    nestedScrollAxes, type);
        } 
```

可以看到，它拿到了 parent，然后调用了 parent 的 onStartNestedScroll 方法，**它将一些滑动信息传递到了 parent 中**。

> androidx.core.view.NestedScrollingParent2#onStartNestedScroll

```java
boolean onStartNestedScroll(@NonNull View child, @NonNull View target, @ScrollAxis int axes,
        @NestedScrollType int type);
```

仔细介绍一下这个方法的各个参数：

- **child：**是 parent 的某个直接子View，这里parent 就是 ViewParentCompat.onStartNestedScroll 中的参数 p。

- **target：**就是嵌套的控件，在我们的例子中，就是 RecyclerView。

- **axes：**是滚动的方向，横向与竖向

- **type：**是触摸类型，一种是用户触摸，另一种一般是惯性滑动

- **返回值：**true，表示 parent 接收滑动操作，false 则不会，后面分发滚动的流程也就不会走了。



PS: *一般的，我们在实现 parent 的 NestedScrollingParent 接口时，onStartNestedScroll 这个方法一般根据滚动方向来返回值，比如：(axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;*



### 再看ACTION_MOVE的处理

> androidx.recyclerview.widget.RecyclerView#onTouchEvent

```java
case MotionEvent.ACTION_MOVE: {
    if (dispatchNestedPreScroll(dx, dy, mReusableIntPair, mScrollOffset, TYPE_TOUCH)) {

    }
}
```

一般，在 ACTION_MOVE 中来分发滑动事件，这里的分发与事件分发不是一个东西。

> androidx.recyclerview.widget.RecyclerView#dispatchNestedPreScroll(int, int, int[], int[], int)

```java
    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow,
            int type) {
        return getScrollingChildHelper().dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow,
                type);
    }
```

同样的，这里我们使用工具来帮助我们处理。它的内部调用了 ViewParentCompat.onNestedPreScroll(parent, mView, dx, dy, consumed, type); 方法。

> androidx.core.view.ViewParentCompat#onNestedPreScroll(android.view.ViewParent, android.view.View, int, int, int[], int)

```java
        if (parent instanceof NestedScrollingParent2) {
            // First try the NestedScrollingParent2 API
            ((NestedScrollingParent2) parent).onNestedPreScroll(target, dx, dy, consumed, type);
        }
```

它调用了 NestedScrollingParent2 的 onNestedPreScroll 方法。

> androidx.core.view.NestedScrollingParent2#onNestedPreScroll

```java
    void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed,
            @NestedScrollType int type);
```

**基本上是与前面的 startNestedScroll 是一样的流程**，所以也好理解。这里重要的是需要搞清楚这个方法的参数是什么意思！！！

 -  **target：** 就是嵌套的控件，在我们的例子中，就是 RecyclerView。

 - dx：**滑动的x方向距离，一般计算如下：

   ```java
           switch (action) {
               case MotionEvent.ACTION_DOWN: {
                  
                   mLastTouchX = (int) (e.getX() + 0.5f);
                   mLastTouchY = (int) (e.getY() + 0.5f);
   
                
               } break;
                   
               case MotionEvent.ACTION_MOVE: {
   
                   final int x = (int) (e.getX(index) + 0.5f);
                   final int y = (int) (e.getY(index) + 0.5f);
                   int dx = mLastTouchX - x;
                   int dy = mLastTouchY - y;
                   
                   mLastTouchX = x;
                   mLastTouchY = y;
           }
   ```

	- **dy：**滑动的y方向的距离，计算方式同 dx
	- **consumed：**这个就比较奇特了，它是由child创建的，然后作为参数传递进取，方法内部需要改变它的值。嗯，有点抽象，举个例子，比如由一个函数 fun1，它没有返回值，但是它接收一数组作为参数，函数的内部会给这个数组赋值。consumed 的工作方式就是这样。**一般情况下，我们声明一个成员变量 final int[] mReusableIntPair = new int[2]; 传递给 consumed就好了**。
	- **type：**是触摸类型，一种是用户触摸，另一种一般是惯性滑动



consumed 被传递到了 parent 中，**我们根据需要来处理这个值，比如我们需要竖向消耗 pdy个距离，那么我们在 parent 的 onNestedPreScroll 方法中调用 consumed[1] = pdy 就好了，全部消耗则 consumed[1] = dy**。



然后 child 中就需要减去 parent 中消耗的值：

> androidx.recyclerview.widget.RecyclerView#onTouchEvent

```java
case MotionEvent.ACTION_MOVE: {
    if (dispatchNestedPreScroll(dx, dy, mReusableIntPair, mScrollOffset, TYPE_TOUCH)) {
		dx -= mReusableIntPair[0];
		dy -= mReusableIntPair[1];
    }
}
```

然后，**判断自己是否可以滚动，并且对应的滚动方向的值是否有剩余**：

```
if (canScrollVertically && Math.abs(dy) > mTouchSlop)
```

如果有剩余，自己来处理滚动：

```java
mReusableIntPair[0] = 0;
mReusableIntPair[1] = 0;
scrollStep(x, y, mReusableIntPair);
```

然后再次分发滑动事件：

```java
dispatchNestedScroll(consumedX, consumedY, unconsumedX, unconsumedY, mScrollOffset,
                TYPE_TOUCH, mReusableIntPair);
```

同样的，这个方法会调用到 parent 的 onNestedScroll 方法，我就不贴逻辑了，与上面的传递步骤还是一样的。

说一下该方法的参数：

> androidx.core.view.NestedScrollingParent2#onNestedScroll

```java
    void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, @NestedScrollType int type);
```

- **target：**不说了

- **dxConsumed：**就是child处理自己的滚动，横向消耗的值

- **dyConsumed：**就是child处理自己的滚动，竖向消耗的值

- **dxUnconsumed：**就是**一次滑动的距离 - parent 在 onNestedPreScroll 未消耗 - child也未消耗的**，最后剩余的横向的值

- **dxUnconsumed：**同 dxUnconsumed

- **type：**不说了

最后，再判断一下，还有没有剩余的，如果还有剩余的，child 自己处理：

```java
pullGlows(ev.getX(), unconsumedX, ev.getY(), unconsumedY);
```

RecyclerView 这里是利用未消耗完的给出了一个 overScroll 效果。



PS：*在自己能够处理滑动事件的时候，不能让parent拦截掉事件*

```java
getParent().requestDisallowInterceptTouchEvent(true);
```



### 再看ACTION_UP等的处理

> androidx.recyclerview.widget.RecyclerView#onTouchEvent

```java
            case MotionEvent.ACTION_UP: {
                stopNestedScroll(TYPE_TOUCH);
            } break;
```

同样的，也借用工具的方法：

```java
    @Override
    public void stopNestedScroll(int type) {
        getScrollingChildHelper().stopNestedScroll(type);
    }
```

最后，也会调用到 parent 的 onStopNestedScroll 方法。



现在，回过头来看看这两个接口：

> NestedScrollingChild

```java
public void setNestedScrollingEnabled(boolean enabled);
public boolean isNestedScrollingEnabled();
public boolean startNestedScroll(int axes);
public void stopNestedScroll();
public boolean hasNestedScrollingParent();
public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow);
public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow);
public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed);
public boolean dispatchNestedPreFling(float velocityX, float velocityY);
```

> NestedScrollingParent

```java
public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes);
public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes);
public void onStopNestedScroll(View target);
public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed);
public void onNestedPreScroll(View target, int dx, int dy, int[] consumed);
public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed);
public boolean onNestedPreFling(View target, float velocityX, float velocityY);
public int getNestedScrollAxes();
```

这些方法都是有调用关系的，理清楚了就好了。



这里关于 NestedScrollingParent，我说的不太多，只是说了方法的参数意思，下面附上一个demo，实现了微信运行排行榜的滑动效果，希望可以加深理解。

[项目地址](<https://github.com/aprz512/NestedScrollingDemos>)

有兴趣的可以自己添加demo进去。

### 参考文档

[Android NestedScrolling全面解析 - 带你实现一个支持嵌套滑动的下拉刷新（上篇）](<https://www.jianshu.com/p/f09762df81a5>)

[**NestedScrollWebView.java**](<https://github.com/tobiasrohloff/NestedScrollWebView/blob/master/lib/src/main/java/com/tobiasrohloff/view/NestedScrollWebView.java>)