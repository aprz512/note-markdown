# ViewGroup的事件分发机制

之前的某一篇文章中分析过，Android屏幕的触摸事件最终会传递到Activity的dispatchTouchEvent()方法。



文章分析的源码是Android 2.2的源码。



那么就从这个方法开始，在重新梳理一遍事件分发流程。

```java
    public boolean dispatchTouchEvent(MotionEvent ev) {
        
		//是个空的方法, 我们直接跳过这里看下面的实现
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        //如果getWindow().superDispatchTouchEvent(ev)返回false，这个事件就交给Activity
		//来处理， Activity的onTouchEvent()方法直接返回了false
        return onTouchEvent(ev);
    }
```

这个方法中我们还是比较关心getWindow()的superDispatchTouchEvent()方法，getWindow()返回当前Activity的顶层窗口Window对象，我们直接看Window API的superDispatchTouchEvent()方法：

```java
public abstract boolean superDispatchTouchEvent(MotionEvent event);
```

Window的唯一子类是PhoneWindow,我们就看看PhoneWindow的superDispatchTouchEvent()方法：

```java
    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return mDecor.superDispatchTouchEvent(event);
    }
```

里面直接调用DecorView类的superDispatchTouchEvent()方法。

```java
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }
```

在里面调用了父类FrameLayout的dispatchTouchEvent()方法，而FrameLayout中并没有dispatchTouchEvent()方法，所以我们直接看ViewGroup的dispatchTouchEvent()方法：

```java

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final float xf = ev.getX();
        final float yf = ev.getY();
        final float scrolledXFloat = xf + mScrollX;
        final float scrolledYFloat = yf + mScrollY;
        final Rect frame = mTempRect;
 
        //这个值默认是false, 然后我们可以通过requestDisallowInterceptTouchEvent(boolean disallowIntercept)方法
        //来改变disallowIntercept的值
        boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
 
        //这里是ACTION_DOWN的处理逻辑
        if (action == MotionEvent.ACTION_DOWN) {
        	//清除mMotionTarget, 每次ACTION_DOWN都设置mMotionTarget为null
            if (mMotionTarget != null) {
                mMotionTarget = null;
            }
 
            //disallowIntercept默认是false, 就看ViewGroup的onInterceptTouchEvent()方法
            if (disallowIntercept || !onInterceptTouchEvent(ev)) {
                ev.setAction(MotionEvent.ACTION_DOWN);
                final int scrolledXInt = (int) scrolledXFloat;
                final int scrolledYInt = (int) scrolledYFloat;
                final View[] children = mChildren;
                final int count = mChildrenCount;
                //遍历其子View
                for (int i = count - 1; i >= 0; i--) {
                    final View child = children[i];
                    
                    //如果该子View是VISIBLE或者该子View正在执行动画, 表示该View才
                    //可以接受到Touch事件
                    if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE
                            || child.getAnimation() != null) {
                    	//获取子View的位置范围
                        child.getHitRect(frame);
                        
                        //如Touch到屏幕上的点在该子View上面
                        if (frame.contains(scrolledXInt, scrolledYInt)) {
                            // offset the event to the view's coordinate system
                            final float xc = scrolledXFloat - child.mLeft;
                            final float yc = scrolledYFloat - child.mTop;
                            ev.setLocation(xc, yc);
                            child.mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;
                            
                            //调用该子View的dispatchTouchEvent()方法
                            if (child.dispatchTouchEvent(ev))  {
                                // 如果child.dispatchTouchEvent(ev)返回true表示
                            	//该事件被消费了，设置mMotionTarget为该子View
                                mMotionTarget = child;
                                //直接返回true
                                return true;
                            }
                            // The event didn't get handled, try the next view.
                            // Don't reset the event's location, it's not
                            // necessary here.
                        }
                    }
                }
            }
        }
 
        //判断是否为ACTION_UP或者ACTION_CANCEL
        boolean isUpOrCancel = (action == MotionEvent.ACTION_UP) ||
                (action == MotionEvent.ACTION_CANCEL);
 
        if (isUpOrCancel) {
            //如果是ACTION_UP或者ACTION_CANCEL, 将disallowIntercept设置为默认的false
        	//假如我们调用了requestDisallowInterceptTouchEvent()方法来设置disallowIntercept为true
        	//当我们抬起手指或者取消Touch事件的时候要将disallowIntercept重置为false
        	//所以说上面的disallowIntercept默认在我们每次ACTION_DOWN的时候都是false
            mGroupFlags &= ~FLAG_DISALLOW_INTERCEPT;
        }
 
        // The event wasn't an ACTION_DOWN, dispatch it to our target if
        // we have one.
        final View target = mMotionTarget;
        //mMotionTarget为null意味着没有找到消费Touch事件的View, 所以我们需要调用ViewGroup父类的
        //dispatchTouchEvent()方法，也就是View的dispatchTouchEvent()方法
        if (target == null) {
            // We don't have a target, this means we're handling the
            // event as a regular view.
            ev.setLocation(xf, yf);
            if ((mPrivateFlags & CANCEL_NEXT_UP_EVENT) != 0) {
                ev.setAction(MotionEvent.ACTION_CANCEL);
                mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;
            }
            return super.dispatchTouchEvent(ev);
        }
 
        //这个if里面的代码ACTION_DOWN不会执行，只有ACTION_MOVE
        //ACTION_UP才会走到这里, 假如在ACTION_MOVE或者ACTION_UP拦截的
        //Touch事件, 将ACTION_CANCEL派发给target，然后直接返回true
        //表示消费了此Touch事件
        if (!disallowIntercept && onInterceptTouchEvent(ev)) {
            final float xc = scrolledXFloat - (float) target.mLeft;
            final float yc = scrolledYFloat - (float) target.mTop;
            mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;
            ev.setAction(MotionEvent.ACTION_CANCEL);
            ev.setLocation(xc, yc);
            
            if (!target.dispatchTouchEvent(ev)) {
            }
            // clear the target
            mMotionTarget = null;
            // Don't dispatch this event to our own view, because we already
            // saw it when intercepting; we just want to give the following
            // event to the normal onTouchEvent().
            return true;
        }
 
        if (isUpOrCancel) {
            mMotionTarget = null;
        }
 
        // finally offset the event to the target's coordinate system and
        // dispatch the event.
        final float xc = scrolledXFloat - (float) target.mLeft;
        final float yc = scrolledYFloat - (float) target.mTop;
        ev.setLocation(xc, yc);
 
        if ((target.mPrivateFlags & CANCEL_NEXT_UP_EVENT) != 0) {
            ev.setAction(MotionEvent.ACTION_CANCEL);
            target.mPrivateFlags &= ~CANCEL_NEXT_UP_EVENT;
            mMotionTarget = null;
        }
 
        //如果没有拦截ACTION_MOVE, ACTION_DOWN的话，直接将Touch事件派发给target
        return target.dispatchTouchEvent(ev);
    }

```





#### 参考链接

<https://blog.csdn.net/xiaanming/article/details/21696315>

