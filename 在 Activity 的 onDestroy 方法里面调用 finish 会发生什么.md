## 在 Activity 的 onDestroy 方法里面调用 finish 会发生什么

发现这个奇怪问题的起因在于同事看了MVVM的相关文章，里面提到**屏幕旋转时，Activity 销毁重建不会导致 ViewModel随着销毁而重建**。



于是，就在项目中写了测试代码来验证。然后就发现了一个诡异的问题，**打开的界面旋转后自动关闭了！！**后来断点调试发现是因为 onDestroy 走了两次，分析逻辑之后，还原了执行流程，如下代码所示：

### Demo

```java
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 由于历史原因，我们的项目里面出现了这样的代码（经过简化逻辑之后）。
        finish();
    }
```



那么问题就来了，**Activity 在重建时，是创建了新的对象，为何旧的对象调用 finish 方法会将新创建的 Activity 对象给销毁？**



查看 finish 的源码：

### Activity

> android.app.Activity#finish(int)

```java
    private void finish(int finishTask) {
        // mParent 为空，走里面的逻辑
        if (mParent == null) {
            
            ...

            try {
                ...
                // 这里是最可疑的地方，AMS 是根据 mToken 的值去判断该 finish 哪个 Activity    
                if (ActivityManager.getService()
                        .finishActivity(mToken, resultCode, resultData, finishTask)) {
                    mFinished = true;
                }
            } catch (RemoteException e) {
                // Empty
            }
        } else {
            mParent.finishFromChild(this);
        }

        ...
    }
```



那么，打印 mToken 的值看一下：

#### Demo

```java
@Override
    protected void onDestroy() {
        super.onDestroy();
        Class aClass = this.getClass();

        while (aClass != Activity.class) {
            aClass = aClass.getSuperclass();
        }

        try {
            Field mToken = aClass.getDeclaredField("mToken");
            mToken.setAccessible(true);
            Object o = mToken.get(this);
            Log.e("mToken", o.toString());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        finish();
    }
```

输出 log 如下：

```shell
E/mToken: android.os.BinderProxy@57d9656
E/mToken: android.os.BinderProxy@57d9656
```

不出所料，那么现在就来看看源码，看 AMS 是如何根据 mToken 来管理 Activity 的。



Android 是如何根据手机方向来旋转屏幕的，这个我没法探究，但是 Activity 有一个方法，也可以设置屏幕方向，想来分析这个方法也是可行的。

### Activity

> android.app.Activity#setRequestedOrientation

```java
    public void setRequestedOrientation(@ActivityInfo.ScreenOrientation int requestedOrientation) {
        // mParent 为null
        if (mParent == null) {
            try {
                // 走这里
                ActivityManagerNative.getDefault().setRequestedOrientation(
                        mToken, requestedOrientation);
            } catch (RemoteException e) {
                // Empty
            }
        } else {
            mParent.setRequestedOrientation(requestedOrientation);
        }
    }
```

根据 Binder 机制，ActivityManagerNative.getDefault() 会返回 ActivityManagerProxy 对象，然后会调用到 ActivityManagerService 的 setRequestedOrientation 方法。

### ActivityManagerService

> com.android.server.am.ActivityManagerService#setRequestedOrientation

```java
    @Override
    public void setRequestedOrientation(IBinder token, int requestedOrientation) {
        synchronized (this) {
            ...
            if (config != null) {
                r.frozenBeforeDestroy = true;
                // 这里处理了方向的改变，里面还涉及到对 activity 方向改变的回调
                if (!updateConfigurationLocked(config, r, false)) {
                    mStackSupervisor.resumeFocusedStackTopActivityLocked();
                }
            }
            Binder.restoreCallingIdentity(origId);
        }
    }
```

后面的代码还是比较深的，这里就不贴出来了，跟踪到后面，会发现它调用了这样的一个方法。

> com.android.server.am.ActivityStack#ensureActivityConfigurationLocked

```java
    boolean ensureActivityConfigurationLocked(
            ActivityRecord r, int globalChanges, boolean preserveWindow) {
        ...
		relaunchActivityLocked(r, r.configChangeFlags, true, preserveWindow);
        ...
    }
```

这里它重新启动了这个 Activity（demo里面没有对方向变化做任何处理）。



我们知道，AMS 处理 Activity 的方法，都会通知到到应用进程，由应用进程自己处理。

### ActivityThread

> android.app.ActivityThread#handleRelaunchActivity

```java
    private void handleRelaunchActivity(ActivityClientRecord tmp) {
        
        ...
            // tmp 是从需要重启的集合中找出来的
            // 这里再次找一下，按照正常逻辑这里找出来的，应该还是同一个对象
            ActivityClientRecord r = mActivities.get(tmp.token);
        ...
        
            	// r.token 传递进去
                handleDestroyActivity(r.token, false, configChanges, true);
        
        ...
        		// r 传递进去
                handleLaunchActivity(r, currentIntent, "handleRelaunchActivity");
        
        ...
    }
```

ActivityClientRecord 是属于应用进程的，它里面存有 Activity 的信息。一个 ActivityClientRecord 对应一个 Activity。



这里可以看出，Activity销毁重建的时候，都使用的是同一个 ActivityClientRecord，mToken 没有变化。

当我们在 onDestroy 里面，调用 finish 的时候，传递的 mToken 值与重新创建的 Activity 的 mToken 值是一样的，所以会销毁掉刚刚启动的 Activity。



### 参考文章

<https://juejin.im/post/5c88fac76fb9a049c16013c6>

<https://blog.csdn.net/guoqifa29/article/details/46819377>

<https://www.jianshu.com/p/94816e52cd77>

