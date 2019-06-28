# View 的滑动处理（二）

## CoordinatorLayout.Behavior



CoordinatorLayout 根据 [官方文档](<https://developer.android.com/reference/android/support/design/widget/CoordinatorLayout.html>) 的描述，它是一个“*超级FrameLayout*“，专门用来帮助实现布局中的View相互交互。我们只需要为布局里面的 Views 创建自定义的或者分配现有的 **Behavior** 即可。**Behavior** 是 *Material Design* 独一无二的核心，例如滑动抽屉和面板，滑动消失元素，和跟随其他空间移动的按钮等等。



我们先来看看 Behavior 是如何工作的，后面还会配上几个例子便于理解。



Behavior 用来表示同一个布局中2个或者2个以上的控件之间的交互。通常分为以下几类：



### Layout-Based Behaviors:

先来看一个效果图：

![](F:\note-markdown\View 的滑动处理（二）\1_FBOsM15NY4pFSXjhots-IQ.gif) 



当 snackbar 从底部出现的时候，FAB 会立即向上平移一个 SnackBar 的高度。



**Anchoring** 是 **Layout-Based Behaviors** 的一种，比如我们将 FAB **anchored to** AppBarLayout，看下面的效果图（注意观察红色的类似短信图标的按钮）：

![](F:\note-markdown\View 的滑动处理（二）\1_fVKOTpH7S2ZlGrpmLcuyZQ.gif)



可以看到 FAB 随着 AppBarLayout 在移动，而且最后还会消失。这种 anchored to 的行为，在 CoordinatorLayout 中对应着一个属性，我们下面会说到。



### Scroll-Based Behaviors

还是看上面的图，这次不看 FAB，而是观察下面的长文本。TextView 本身是不可滚动的，所以它的外面有一层 NestedScrollView 包裹。

一个 CoordinatorLayout 中有两个可以滚动的控件，一个是 AppBarLayout，一个是 NestedScrollView 。通过效果图，可以看出，他们并没有产生冲突，而是将滑动联动起来了，我们向上滚动 NestedScrollView  的时候，会向将 AppBarLayout 往上推，推到顶部之后，NestedScrollView  才开始滚动，之间没有停顿，无缝衔接。

之所以能够产生这样的行为，是因为这两个控件都被分配了一个 Behavior。

```java
@CoordinatorLayout.DefaultBehavior(AppBarLayout.Behavior.class)
public class AppBarLayout extends LinearLayout {
```

```java
    <android.support.v4.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"
        >
```



### Implementing the Behaviors

介绍完了两类 Behaviors，接下来我们看看如何自定义一个 Behavior。

首先，我们的 Behavior 必须继承至 Coordinator.Behaviors\<V>，V 就是需要这个Behavior 的控件的类型。比如我想给 TextView 指定一个 Behavior，那么 V 就是 TextView 类型。

然后，Coordinator.Behaviors\<V> 有一些方法需要覆盖来达到我们想要的效果，其中有3个方法尤其重要（额，还有滚动相关的方法，与第一篇是以一样的就不介绍了）。



#### layoutDependsOn(…)

这个方法用来决定，当前 View 需要依赖哪个 View。比如你需要根据同一布局中的 ImageView 的位置来决定自己的位置，那么就可以这样写：

```kotlin
    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency is ImageView
    }
```

这个方法的返回值，true 表示当前 View 是有依赖对象的，反之则无。但它返回 false 的时候，onDependentViewChanged 方法不会被调用。

嗯，有一种情况我还没有搞清楚，当我们新建一个空项目的时候，模板选择第一个，那么我们的MainActivity 的布局是这样的：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="match_parent"
        android:id="@+id/cl"
        android:layout_height="match_parent"
        tools:context=".MainActivity">

    <com.google.android.material.appbar.AppBarLayout
            android:layout_height="wrap_content"
            android:layout_width="match_parent"
            android:theme="@style/AppTheme.AppBarOverlay">

        <androidx.appcompat.widget.Toolbar
                android:id="@+id/toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                android:background="?attr/colorPrimary"
                app:popupTheme="@style/AppTheme.PopupOverlay"/>

    </com.google.android.material.appbar.AppBarLayout>

    <include layout="@layout/content_main"/>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/fab_margin"
            app:srcCompat="@android:drawable/ic_dialog_email"/>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

可以看到最下面有一个 FloatingActionButton。

MainActivity 的部分代码如下：

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Log.e("e", "h = ${cl.measuredHeight}")
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            Log.e("e", "h = ${cl.measuredHeight}")
        }
    }
```

当我们点击 FAB 的时候，发现，随着 Snackbar 的出现， FAB 上移了，而且 **CoordinatorLayout 的高度并没有改变**，也就是说，FAB ”*依赖*“了 Snackbar ，随着 Snackbar 的上移，FAB 也移动了自己的位置。

那么，我找了以下 FAB 的 Behavior 源码，发现它的 layoutDependsOn 返回了 false（它根本就没有复写这个方法）。那么它是怎么做到的呢？

我在源码里面发现了这样的一个字段：

> androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams

```java
        /**
         * A {@link Gravity} value describing how this child view dodges any inset child views in
         * the CoordinatorLayout. Any views which are inset on the same edge as this view is set to
         * dodge will result in this view being moved so that the views do not overlap.
         */
        public int dodgeInsetEdges = Gravity.NO_GRAVITY;
```

然后我在布局里面添加了一个 View，设置了如下属性：

```xml
    <View
        android:id="@+id/test"
        android:layout_width="wrap_content"
        android:layout_height="100dp"
        android:layout_gravity="bottom|end"
        android:layout_margin="@dimen/fab_margin"
        android:background="@color/colorPrimary"
        app:layout_dodgeInsetEdges="bottom"/>
```

运行之后，果然可以跟随 Snackbar 。那么现在可以得出结论，FAB 跟随 Snackbar 是 CoordinatorLayout 自带的功能，与 Behavior 没有关系。

但是这里还是有一个疑问，CoordinatorLayout  是从哪里获取到 Snackbar  的高度的？？？



#### onDependentViewChanged(…)

一旦我们确定了依赖关系，那么就可以根据依赖关系来处理交互逻辑了。比如：我想让一个 TextView 跟随 ImageView 的底部：

```java
    override fun onDependentViewChanged(parent: CoordinatorLayout, child: TextView, dependency: View): Boolean {
        // 让 child 跟随 dependency 的底部
        val bottom = dependency.y.roundToInt() + dependency.height
        child.top = bottom
        return true
    }
```

这里我们让 child （TextView）的 top 值等于 dependency （ImageView）的 bottom 值就可以达到我们想要的效果了。



#### onDependentViewRemoved(…)

这个方法看名字就很好理解了，就是当依赖的View被删除的时候，会调用这个方法。



### 例子项目

[**NestedScrollingDemos**](<https://github.com/aprz512/NestedScrollingDemos>)

这个项目里面的例子有很详细的注释，可以参考。