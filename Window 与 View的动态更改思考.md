# Window 与 View的动态更改思考

我们创建一个悬浮窗的时候，需要使用 WindowManager 来创建。

WindowManager 是一个接口，它继承至 ViewManager，主要有3个方法供我们使用：

```java
    public void addView(View view, ViewGroup.LayoutParams params);
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
    public void removeView(View view);
```

我们获取WindowManager：

```java
WindowManager windowManager = (WindowManager)context().getSystemService(Context.WINDOW_SERVICE);
```

一般通过这种方法调用的，最终都会触发一个 IPC 调用。

那么，这就引发了我的一个猜想：我们在 Activity 的布局里面，每次动态添加和删除 View 的时候，里面有没有触发 IPC 调用？？？



我们拿 WindowManager的addView方法来分析一下，它的实现类是 `android.view.WindowManagerGlobal`。

> android.view.WindowManagerGlobal#updateViewLayout

```java
root.setLayoutParams(wparams, false);
```

它调用了 ViewRootImpl 的 setLayoutParams 方法。



> android.view.ViewRootImpl#setLayoutParams

```java
scheduleTraversals();
```

这个方法内部又会调用 performTraversals方法，这个方法我们就很熟悉了，它就是 View 的绘制流程的入口。



> android.view.ViewRootImpl#performTraversals

```java
relayoutWindow(...)
```

这个方法就会发起IPC调用来更新Window。



我们的普通的 ViewGroup 的addView等方法，会触发 requestLayout 方法，同样的会导致 scheduleTraversals 方法的调用，所以也会有 IPC 调用。

可以想象到，addView 是一个比较重量的操作。