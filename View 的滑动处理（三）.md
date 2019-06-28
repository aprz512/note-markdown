# View 的滑动处理（三）

## ViewDragHelper 定义

Android 官方对 ViewDragHelper 的定义是：

- 可以用于自定义 ViewGroup
- 可以进行拖拽移动或者重新定位ViewGroup中子视图View
- 提供有效操作和状态追踪



##使用 ViewDragHelper

下面说说，如何使用 ViewDragHelper（下面简称 VDH） 。



创建一个 VDH 对象：

> androidx.customview.widget.ViewDragHelper#create(android.view.ViewGroup, androidx.customview.widget.ViewDragHelper.Callback)

```kotlin
    public static ViewDragHelper create(@NonNull ViewGroup forParent, @NonNull Callback cb) {
        return new ViewDragHelper(forParent.getContext(), forParent, cb);
    }
```

VDH 构造函数是私有的，但是有多个可供访问的静态方法。使用它就可以创建一个 VDH 对象。

上面的静态方法中，cb 参数很重要，因为，**ViewDragHelper.Callback是用来连接ViewDragHelper和parent view的**。



要让VDH能够处理相关的拖动事件就需要**将拖动时触发事件状态传给VDH**，所以我们要针对onInterceptTouchEvent、onTouchEvent 做特别的处理。

```kotlin
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return viewDragHelper.shouldInterceptTouchEvent(ev!!)
    }
```

直接委托给 vdh，让它判断是否需要拦截事件，如果这里有自己的逻辑，也可以添加，比如：

```kotlin
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        val myIntercept = someCondition(ev)
        return myIntercept and viewDragHelper.shouldInterceptTouchEvent(ev!!)
    }
```

然后，就是 onTouchEvent，一般情况下，自定义ViewGroup，而 ViewGroup 默认不会处理事件，所以我们需要在 ACTION_DOWN 的时候，处理这个事件：

```kotlin
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {

        // 在这里，将事件传递给 VDH，让它去处理
        viewDragHelper.processTouchEvent(event!!)

        // 处理下 down 事件按，让后续事件都传过来
        if (event.action == MotionEvent.ACTION_DOWN) {
            return true
        }

        return super.onTouchEvent(event)
    }
```



下面，看看 ViewDragHelper.Callback 中的几个常用方法：

> pointerId：区分多点触控时的 id position

```kotlin
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            // 限制指定的控件才可以拖动
            return child.id == R.id.video
        }
```



```kotlin
        override fun getViewVerticalDragRange(child: View): Int {
            return verticalRange
        }
```

这个方法，需要返回一个大于0的数，然后指定的 View 才会在垂直方向移动。我试过，只要大于 0 即可，似乎没有别的要求。暂时没有去探究这个值有什么意义。



> top： 表示拖动指定 view 时，view 的 top 值
>
> dy ：是每次的差值

```kotlin
        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            // 限制竖向拖动的范围为 【0，verticalRange】
            val min = Math.min(top, verticalRange)
            return Math.max(0, min)
        }
```

上面的代码，是将 top 的值限制了，避免 view 被拖出指定的范围。



```kotlin
override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            changeVideo(changedView, top)
            changeVideoDetail(top)
            changeVideoTitle(top)
            changePlayButton(top)
            changeCloseButton(top)
        }
```

这个方法表示，当指定的 View 被拖动时，这个方法就会被回调，然后我们就可以在这个方法里面做一些操作，比如改变另外的View 的位置，这样就可以实现一个联动效果。



```kotlin
        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
        }
```

当指定的View 被释放的时候（手指抬起等），这个方法会被回调。如果我们想要一个回弹效果，在这里处理是一个很好的解决方法。比如：

```kotlin
        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            dragHelper.settleCapturedViewAt(mDragOriLeft , mDragOriLeft)
            invalidate()
        }
```

settleCapturedViewAt 是 VDH 提供的一个方法，实际上是使用的 scroller，所以调用这个方法，还需要和使用 Scroller 一样，**实现 computeScroll 方法**。



还有一些判断边缘拖拽的方法就不介绍了，我实现了一些效果，给我的感觉用起来还是挺方便的。

但是这里有一个问题，就是如果你想实现一些很复杂的效果，其实核心不在拖拽的处理上，而是在一些计算方面，就比如你想要一个回弹效果，直接使用 scroller 可以实现，但是这个回弹效果很普通，UI想要更加炫酷的回弹效果。这个时候考验的不是你对View的理解，**而是你对数学的理解**。就像我刚接触自定义控件的时候，看的aige的系列文章，其中有一个翻书效果，要想实现这个效果，如果你没有空间想象能力，没有一定的数学知识，无论你对View的绘制，对各种工具有多么熟练，你仍然无从下手。



我实现的一些demo：

[**NestedScrollingDemos**](<https://github.com/aprz512/NestedScrollingDemos>)

