# View 的工作原理



### ViewRootImpl & DecorView

当我们在 Activity 中调用 setContentView 方法的时候，实际上 Activity 是将这个方法转发给了 Window。

> android.app.Activity#setContentView(int)

```java
    public void setContentView(@LayoutRes int layoutResID) {
        getWindow().setContentView(layoutResID);
        initWindowDecorActionBar();
    }
```



而 Window 的 setContentView 方法会创建 DecorView 对象。

```java
 @Override
    public void setContentView(int layoutResID) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
        if (mContentParent == null) {
            // 这个方法里面就实例化了一个 DecorView
            installDecor();
        }
        ...
    }
```



那么，有了 DecorView 之后，后面的流程就是解析我们在 xml 中写的布局，将xml转换成真正的 View 对象，然后添加到 DecorView 中（DecorView 是继承至 FrameLayout）。



---

但是我们都知道，在 onCreate 中我们是无法看到布局的，就像我们在内存中创建了一个 View 对象，我们是看不到它的，只有将它添加到界面上我们才能看到。

那么，如何将一个 View 添加到界面上呢？想必大家都知道以前手机上的悬浮按钮很流行，我们自己想要做一个这样的效果，一般都是通过 WindowManager 来实现的。所以说，使用 WindowManger 就可以将一个 View 显示到界面上了。当一个 View 已经显示到界面上之后，在给他添加子 View 就不用 WindowManager 了，可以直接添加，这就是我们可以动态更改布局，而不用通过WindowManager 的原因，因为 Activity 已经通过 WindowManager 将 DecorView 添加到了 PhoneWindow 上。我们操作的是 DecorView 的子 View。

> android.app.ActivityThread#handleResumeActivity

```java
// 这里的 l 是 WindowManager.LayoutParams 对象的类型
wm.addView(decor, l);
```

可以看到在 Activity 的 onResume 执行后，View 才会被添加到 Window 上，所以在这之前，我们是看不到界面的，故而不要在这之前做太多的事件，以免黑（白）屏时间太长，给用户一个不好的体验。

看到这里，我们已经知道 DecorView 与 WindowManager 是如何产生关系的了！



----

那么 ViewRootImpl 又是如何插一脚的呢？

上面的 addView 方法中，其实创建了一个 ViewRootImpl 对象：

> android.view.WindowManagerGlobal#addView

```java
ViewRootImpl root;
...
root = new ViewRootImpl(view.getContext(), display);
...
// 这个 view 就是上面的 DecorView 对象
// 这里将 view 保存到了自己的成员变量 mView 中
root.setView(view, wparams, panelParentView);
```

我们先来看看官方对 ViewRootImpl 的介绍：

> ViewRootImpl是View中的最高层级，属于所有View的根（`但ViewRootImpl不是View，只是实现了ViewParent接口`），实现了View和WindowManager之间的通信协议，实现的具体细节在WindowManagerGlobal这个类当中。

也就是说，View 并不会与 WindowManager 直接交流，他们有一个中间人，就是 ViewRootImpl，但是它的功能不止于此，它还负责 View 的测量-布局-绘制流程。可以看作是 View 树的操纵者。



### View的测量过程

对于 DecorView ，其 MeasureSpec 由**窗口的尺寸**和其**自身的 LayoutParams** 来共同决定。

对于普通的 View，其 MeasureSpec 由**父容器的 MeasureSpec** 和**自身的 LayoutParams** 来共同决定。

具体一点来说：

- 当 View 采用具体数值的宽高时，不管父容器的 MeasureSpec 是什么，View 的 MeasureSpec 都是EXACTLY并且其大小遵循 LayoutParams 中的大小。
- 当 View 的宽/高是 match_parent 时，如果父容器的模式是EXACTLY模式，那么 View 也是EXACTLY模式并且其大小是父容器的剩余空间。如果父容器是AT_MOST模式，那么View 也是AT_MOST模式并且大小为父容器的剩余空间（可以看出，View 与 父容器的模式一样）。
- 当 View 的宽/高是 warp_content 是时，不管父容器的 MeasureSpec 是什么，View 的 MeasureSpec 都是 AT_MOST 模式并且大小为父容器的剩余空间。

决定了 View 的 MeasureSpec 值之后，就可以开始执行 View 的 measure 方法了。

measure 方法就是执行测量过程的方法，它会调用 onMeasure 方法，经常自定义控件的就很熟悉这个方法了。我们可以通过复写 onMeasure 这个方法来绝定一个控件在各种情况下应该有多大。

onMeasure  方法有两个参数：

> android.view.View#onMeasure

```java
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
```

widthMeasureSpec 与 heightMeasureSpec 这两个值就是根据上面的规则得到的（虽然上面我们只说了测量模式，没有测量大小）。View 有了这两个值就可以知道自己在各种情况下应该占多大的位置了。

一般情况下，View 测量出来的大小就是 widthMeasureSpec / heightMeasureSpec 中的 size 值。

但是也有例外，这个时候，View 的宽/高由 minWidth 与 background 一起决定，如果 background 为空，那么值就是 minWidth/minHeight，如果有 background，那么值就取 minWidth/minHeight 与 background 的原始宽/高 中的较大者。

当我们继承一个 View 来自定义控件的时候，如果不复写 onMeasure 就会出现一个问题：

在使用 wrap_content 属性的时候，是不生效的，效果与 match_parent 一样！！

导致这个的原因是：View 为 wrap_content 的时候，模式是 AT_MOST，大小为父容器的剩余空间。这样的话，与match_parent 的表现形式是一样的。

所以，可以这样解决：

```java
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int modeW = MeasureSpec.getMode(widthMeasureSpec);
        int sizeW = MeasureSpec.getSize(widthMeasureSpec);

        int modeH = MeasureSpec.getMode(heightMeasureSpec);
        int sizeH = MeasureSpec.getSize(heightMeasureSpec);

        int width, height;
        if (modeW == MeasureSpec.AT_MOST) {
            width = mMyWidth;
        } else {
            width = sizeW;
        }
        if (modeH == MeasureSpec.AT_MOST) {
            height = mMyHeight;
        } else {
            width = sizeH;
        }
        
        setMeasuredDimension(width, height);
    }
```

其实，只是指定了 AT_MOST 模式下的值，其他的没有改变。



### ViewGroup 的测量过程

ViewGroup 在测量的时候，会先去测量所有的 Child。调用 child 的 measure 方法来测量 child 的大小，然后根据 child 的大小以及自己的布局规则来决定自己的大小。

之所以，还涉及 ViewGroup 的布局规则，是因为每个ViewGroup 都不一样，比如：LinearLayout 是按照线程布局，FrameLayout是按照层叠布局，即使他们的所有child都一样，测量出来的结果肯定不是一样的。

> android.view.ViewGroup#measureChildren

```java
    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        final int size = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < size; ++i) {
            final View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
            }
        }
    }
```



> android.view.ViewGroup#measureChild

```java
    protected void measureChild(View child, int parentWidthMeasureSpec,
            int parentHeightMeasureSpec) {
        final LayoutParams lp = child.getLayoutParams();

        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom, lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
```

显然，measureChild 就是取出LayoutParams，然后和 parentMeasureSpec 一起来决定child 的 MeasureSpec，这个过程与 View 的测量过程是一样的。



### android.view.ViewRootImpl#performTraversals 是谁触发的

我们知道在 ViewRootImpl 创建出来之前，View是不会执行测量等一系列流程的，这是因为View的测量等都是由 ViewRootImpl 操纵的。

那么，View的第一次测量是从什么时候开始的呢？

> android.view.ViewRootImpl#setView

```java
// Schedule the first layout -before- adding to the window
// manager, to make sure we do the relayout before receiving
// any other events from the system.
requestLayout();
```

当 View 被添加到 window 上之前，ViewRootImpl 会执行一次 requestLayout，这个货会触发一系列连锁反应，最后调用到 performTraversals 里面。

### 为什么使用 view.post(xxx) 可以获取到 view 的宽高？？？

这是因为 view 将 runnable 都存到了自己维护的一个队列中。

> android.view.HandlerActionQueue#postDelayed

```java
    public void postDelayed(Runnable action, long delayMillis) {
        final HandlerAction handlerAction = new HandlerAction(action, delayMillis);

        synchronized (this) {
            if (mActions == null) {
                mActions = new HandlerAction[4];
            }
            mActions = GrowingArrayUtils.append(mActions, mCount, handlerAction);
            mCount++;
        }
    }
```

等到 view 显示出来的时候，才会取出来执行。

> android.view.ViewRootImpl#performTraversals

```java
        // Execute enqueued actions on every traversal in case a detached view enqueued an action
        getRunQueue().executeActions(mAttachInfo.mHandler);
```

可以看到，每次执行 performTraversals 的时候，就调用了 View 维护的队列的 executeActions 方法。

```java
    public void executeActions(Handler handler) {
        synchronized (this) {
            final HandlerAction[] actions = mActions;
            for (int i = 0, count = mCount; i < count; i++) {
                final HandlerAction handlerAction = actions[i];
                handler.postDelayed(handlerAction.action, handlerAction.delay);
            }

            mActions = null;
            mCount = 0;
        }
    }
```

而这个队列，将 runnable 交给了 handler 去执行，这个 handler 是主线程的 handler。那么这个 runnable 会什么时候执行呢？这里我们先不管，我能只需要知道这个 runnable 与 测量流程 那个先执行就好了。

performTraversals 在调用了 executeActions 之后，将 runnable 放入主线程的队列，然后就接续往下执行，下面就是 View 的测量-布局-绘制流程了，所以说不管 runnable 什么时候执行，它肯定是在测量流程的后面执行，这也是为什么能在 view.post 的 runnable 可以拿到 view 的宽高的原因。



### View 的 layout 过程

一个 view 在 layout 方法中会决定自己在父布局的位置，如果这个 view 还有 child，那么它会在 onLayout 方法中调用 child 的 layout 方法，决定 child 的位置。这样达到一个循环...

其实自定义一个 ViewGroup，它的 onLayout 实现还是比较简单的，只要按照业务流程，慢慢写就好了，就和摆东西一样，每个东西的大小你都知道了，想怎么摆就怎么摆。就是里面的 margin 比较蛋疼，需要细心一点。



### View 的 draw 过程

1. 先绘制背景（肯定的，不然内容被背景盖住了）

2. 绘制自己（onDraw）

3. 绘制 children （dispatchDraw）

4. 绘制装饰 （onDrawScrollBars）

与这个过程有关的有一个很重要的东西，就是补间动画执行的原理。

```java
boolean draw(Canvas canvas, ViewGroup parent, long drawingTime) {
    ...
    
    //获取当前Animation
    final Animation a = getAnimation();
    if (a != null) {
        more = applyLegacyAnimation(parent, drawingTime, a, scalingRequired);
        ...
    }
    ...
    //处理滑动
    if (offsetForScroll) {
        canvas.translate(mLeft - sx, mTop - sy);
    } else {
        if (!drawingWithRenderNode) {
             //处理滑动
            canvas.translate(mLeft, mTop);
        }
        if (scalingRequired) {
            ...
            // mAttachInfo cannot be null, otherwise scalingRequired == false
            final float scale = 1.0f / mAttachInfo.mApplicationScale;
            //处理缩放
            canvas.scale(scale, scale);
        }
    }
    //处理透明度
    float alpha = drawingWithRenderNode ? 1 : (getAlpha() * getTransitionAlpha());
    ...
	if (drawingWithRenderNode) {
        renderNode.setAlpha(alpha * getAlpha() * getTransitionAlpha());
    } else if (layerType == LAYER_TYPE_NONE) {
        canvas.saveLayerAlpha(sx, sy, sx + getWidth(), sy + getHeight(),
                              multipliedAlpha);
    }
    ...
    return more;
}   
```

可以看到，在 View 的绘制过程中，会获取 view 相关的动画，然后根据动画来计算当前 view 应该所处的位置，透明度等等。所以，如果一个 view 不执行重绘，动画是显示不出来的。