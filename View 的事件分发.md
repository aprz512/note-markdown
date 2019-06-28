# View 的事件分发



### 事件的传递流

Activity -> Window -> ViewGroup - > View

> Activity.java

```java
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        // 交给 window
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
    }
```

> PhoneWindow.java

因为 PhoneWindow 暂时是 Window 类的唯一实现：

```java
    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return mDecor.superDispatchTouchEvent(event);
    }
```

这里的 mDecor 就是 DecorView 了，它继承至 FrameLayout，所以它是一个 ViewGroup。

然后 ViewGroup **可能会**将事件就传递给它的子 View。



### onTouch & onTouchEvent & onClick

在 View 的 dispatchTouchEvent 方法中，在调用 onTouchEvent 之前，会先调用 mListenerInfo.mOnTouchListener.onTouch  方法，这个就是我们在外部设置的监听了。如果这个方法返回了 true，那么 onTouchEvent 方法就不会调用。

```java
            ListenerInfo li = mListenerInfo;
            if (li != null && li.mOnTouchListener != null
                    && (mViewFlags & ENABLED_MASK) == ENABLED
                    && li.mOnTouchListener.onTouch(this, event)) {
                result = true;
            }
			// result 为 true 是无法进入这个条件的
            if (!result && onTouchEvent(event)) {
                result = true;
            }
```

onClick 是在 onTouchEvent 中调用的，现在的 Android studio 在我们复写 View 的 onTouchEvent 的时候，都会给这么一个警告：

> Custom view MotionView overrides onTouchEvent but not performClick less... (Ctrl+F1) 
> Inspection info:If a View that overrides onTouchEvent or uses an OnTouchListener does not also implement performClick and call it when clicks are detected, the View may not handle accessibility actions properly. Logic handling the click actions should ideally be placed in View#performClick as some accessibility services invoke performClick when a click action should occur.

简单来说就是，复写这个方法可能会导致 performClick 方法不会触发。从而影响 accessibility  的动作行为。



### onClick & onLongClick

一般的，手指点击然后抬起，就会触发一个 onClick 时间，但是不知道你有没有想过，onLongClick 是如何触发的呢？系统是如何判断我们是长按，而不是点击？

话说，肯定是一句时间来判断，但是具体的思路是怎么样的呢？

> 使用 Handler post 一个延时 xxx ms消息，如果收到这个延时消息就会触发一个事件。 然而某些情况下，会提前将该消息 remove 掉，这样就收不到这个消息，无法触发事件。
>
> 拿长按事件举例：比如长按1s算一个长按事件，那么我们就可以post一个延时1000ms的消息，收到这个消息就触发长按回调，如果没到1s用户就放开了手指，那么就移除这个消息，这样就不会触发长按回调了。

onLongClick 方法有返回值，返回 true 表示消耗这个事件，那么 onClick 就无法触发了。



### clickable & enable

一个 View 只有是 clickable 才会消耗事件，与 enable 没有关系。



### onInterceptTouchEvent 的调用

onInterceptTouchEvent  这个方法只有 ViewGroup 才有。

这个方法的调用条件有两个：

```java
if (actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null)
```

第一个是按下事件才会触发，第二个是**有可以消耗这次事件的子 View**。

根据这个我们可以推理出如下结论：

当一个 ViewGroup 拦截事件的时候，它会将事件交给自己处理，那么它不会把事件传递给子 View，也就是说，**它没有可以消耗这次事件的子View**。即在接下来的 MOVE、UP等事件，都不会调用这个方法。



### requestDisallowInterceptTouchEvent

当子View调用这个方法之后，ViewGroup **无法拦截除了 ACTION_DOWN 以外的事件**。

这是因为，ACTION_DOWN 事件会重置和清除一些状态，其中就包括 FLAG_DISALLOW_INTERCEPT。



### ViewGroup 拦截事件

当 ViewGroup 决定拦截事件，那么 mFirstTouchTarget == null，那么 ViewGroup 的 dispatchTouchEvent 方法会调用 super.dispatchTouchEvent(event); 方法。

```java
            if (mFirstTouchTarget == null) {
                // No touch targets so treat this as an ordinary view.
                handled = dispatchTransformedTouchEvent(ev, canceled, null,
                        TouchTarget.ALL_POINTER_IDS);
            }
```

```java
            if (child == null) {
                handled = super.dispatchTouchEvent(event);
            }
```

这里的 super 就是 View，因为 ViewGroup 也是继承至 View 的。

所以就调用到了 View 的 dispatchTouchEvent 方法，我们都知道，View 的 dispatchTouchEvent 方法会直接调用自己的 onTouchEvent 方法，而**这里自己表示ViewGroup**。所以，当 ViewGroup 决定拦截事件，会将事件交给自己处理。



### ACTION_DOWN

当某个 View 可以消耗事件的时候，这个 View 的父布局的 mFirstTouchTarget 变量就指向了这个 View，然后接下来的事件都会交给这个 View 来处理。

```java
            if (child == null) {
                handled = super.dispatchTouchEvent(event);
            } else {
                handled = child.dispatchTouchEvent(event);
            }
```

可以看到，直接调用 child （mFirstTouchTarget  指向的值） 的 dispatchTouchEvent。



当 View 不消耗 ACTION_DOWN 事件的时候，它的父布局的 mFirstTouchTarget 就为null，后面的事件该View 的 父布局就不会再往下传递了。它也就接受不到后续事件了。



### ACTION_CANCEL

当一个 ViewGroup 不拦截 ACTION_DOWN 事件，并且有可以消耗 ACTION_DOWN 的子 View 时，接下来的事件都会交给这个子 View 来处理，但是每次都会询问父布局是否拦截：

```java
            if (actionMasked == MotionEvent.ACTION_DOWN
                    || mFirstTouchTarget != null) {
                final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
                if (!disallowIntercept) {
                	// 因为 mFirstTouchTarget 不会空，所以会走这里
                    intercepted = onInterceptTouchEvent(ev);
                    ev.setAction(action); // restore action in case it was changed
                } else {
                    intercepted = false;
                }
            }
```

而如果在后续的事件中，父布局突然拦截了事件，即 intercepted 为 true，那么先前可以处理该事件的子View会收到一个 ACTION_CANCEL 事件。后面的事件就都会传递给父布局，而不往下传递。

```java
// intercepted 为 true，则 cancelChild 也为 true
final boolean cancelChild = resetCancelNextUpFlag(target.child) || intercepted;
if (dispatchTransformedTouchEvent(ev, cancelChild, target.child, target.pointerIdBits)) {
    handled = true;
}
```

```java
        final int oldAction = event.getAction();
        if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
            // 强制将 action 改成 cancel
            event.setAction(MotionEvent.ACTION_CANCEL);
            if (child == null) {
                handled = super.dispatchTouchEvent(event);
            } else {
                handled = child.dispatchTouchEvent(event);
            }
            event.setAction(oldAction);
            return handled;
        }
```

可以看到，这里它将 ACTION_CANCEL  传递给了子 View。

传递 cancel 成功之后，会重置一些状态，包括 mFirstTouchTarget，将它的值清空。