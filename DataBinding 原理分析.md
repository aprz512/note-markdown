# DataBinding 原理分析

DataBinding 是一个支持库，它可以将布局中的界面组件绑定到数据源上，做到UI与数据的单项或者双向监听。说白一点就是数据发生变化可以直接反映到界面上，不用再次手动操作了。当然它的作用远远不止于此，本文从这个点入手，来略微深入一下它的实现过程。



首先，DataBinding可以将数据的变化反应到UI上，实际上就是帮助我们更新UI，那么它肯定需要持有（直接或者间接）UI的引用，不然的话，是没法操作UI的。用过DataBinding 的小伙伴应该都知道，DataBinding 会根据布局生成一个类，这个类里会有许多成员变量，每个变量对应着布局里面各个控件。



举个例子吧，我们的布局如下：

> app\src\main\res\layout\content_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <data>

        <variable
            name="viewModel"
            type="com.test.user" />

        <import type="android.view.View" />
    </data>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@{viewModel.name}" />

        <TextView
            android:id="@+id/tv_sex"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="50dp"
            android:text="@{viewModel.sex}" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="50dp" />

        <TextView
            android:id="@+id/tv_class"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="50dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="50dp"
            android:text="@{viewModel.age}" />
    </LinearLayout>
</layout>
```



这里面看起来有很多高级的用法，实际上它经过编译之后，是下面这个样子的（因为没有在工程里面找到生成的文件，可能是新版本又换了位置，所以只能看apk里面的资源文件了）：



```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:orientation="1"
    android:tag="layout/content_main_0"
    android:layout_width="-1"
    android:layout_height="-1">

    <TextView
        android:id="@ref/0x7f0800c8"
        android:tag="binding_1"
        android:layout_width="-2"
        android:layout_height="-2" />

    <TextView
        android:id="@ref/0x7f0800c9"
        android:tag="binding_2"
        android:layout_width="-2"
        android:layout_height="-2"
        android:layout_marginTop="dimension(12801)" />

    <TextView
        android:layout_width="-2"
        android:layout_height="-2"
        android:layout_marginTop="dimension(12801)" />

    <TextView
        android:id="@ref/0x7f0800c7"
        android:layout_width="-2"
        android:layout_height="-2"
        android:layout_marginTop="dimension(12801)" />

    <TextView
        android:tag="binding_3"
        android:layout_width="-2"
        android:layout_height="-2"
        android:layout_marginTop="dimension(12801)" />
</LinearLayout>

```



不要在意哪些 dimension/ref 之类的东西，关键点在于它给**使用了 `@{}` 的控件都生成了一个 tag 属性**。可以看出，tag 是有规律的：

> 如果是根布局，为xml的名字，跟一个数字0，本例为 content_main_0。

> 如果不是根布局，为binding_x，x是数值，从1开始（根布局把0用了）。

我们手动添加的layout，data，以及 @{viewModel.name} 这些看似高级的东西，其实在编译后都去掉了。那么它为什么要添加一个 tag 呢？？？其实是因为它在内部是使用了这个tag来获取view的引用。



我们知道，要使用 DataBinding，除了布局需要特殊写法，加载布局的时候，也需要特殊处理。拿 Activity 举例，我们要使用 DataBinding 加载布局，就不能像以前一样直接调用 setContentView，而是要使用 DataBindingUtil.setContentView 这个方法，那么我们就来分析一下这个方法。

> androidx.databinding.DataBindingUtil#setContentView(android.app.Activity, int)

这个方法里面调用了其他方法，我们一直追踪下去，发现了它的核心方法是这个：

> androidx.databinding.DataBindingUtil#bind(androidx.databinding.DataBindingComponent, android.view.View, int)

```java
	private static DataBinderMapper sMapper = new DataBinderMapperImpl();   

...

    static <T extends ViewDataBinding> T bind(DataBindingComponent bindingComponent, View root,
            int layoutId) {
        return (T) sMapper.getDataBinder(bindingComponent, root, layoutId);
    }
```

DataBinderMapperImpl 是编译器生成了一个类，它的 getDataBinder 内容大致如下：

> com.aprz.snackbardemo.DataBinderMapperImpl#getDataBinder(androidx.databinding.DataBindingComponent, android.view.View, int)

```java
  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
        case  LAYOUT_CONTENTMAIN: {
          if ("layout/content_main_0".equals(tag)) {
            return new ContentMainBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for content_main is invalid. Received: " + tag);
        }
      }
    }
    return null;
  }
```

这个方法的 layoutId 就是 R.layout.content_main （我们使用DataBindingUtil#setContentView传入的值 ）。由于编译器自己生成了一个 Map，这个Map储存了所有需要 DataBinding 处理的 layoutId，layoutId 是key，value 是一个整数值。这里是我没有想通的地方，为啥要对应一个整数值，而不是直接使用 layoutId 呢？？？比如像下面这样写：

```java
      switch(layoutId) {
        case  R.layout.content_main: {
          if ("layout/content_main_0".equals(tag)) {
            return new ContentMainBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for content_main is invalid. Received: " + tag);
        }
      }
```



这个不影响，我们继续往下看，它最后返回了一个对象，叫做 ContentMainBindingImpl。使用过 DataBinding 的都应该会有点眼熟，因为我们使用的对象是 ContentMainBinding，而 ContentMainBindingImpl 看起来是 ContentMainBinding 的一个实现类。看一下他们的关系：

```java
public abstract class ContentMainBinding extends ViewDataBinding

public class ContentMainBindingImpl extends ContentMainBinding
```

也就是说，虽然我们使用的是 ContentMainBinding，但是它实际上是一个 ContentMainBindingImpl 对象。



我们继续，看 ContentMainBindingImpl 的构造方法：

> com.aprz.databindingdemo.databinding.ContentMainBindingImpl#ContentMainBindingImpl(android.databinding.DataBindingComponent, android.view.View)

```java
    public ContentMainBindingImpl(@Nullable android.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 5, sIncludes, sViewsWithIds));
    }
```

这里调用了一个叫做 mapBindings 的方法，就是它解析了View的 tag ，然后将view存储到了一个数组中，在将这个数组赋值给成员变量，这样我们就不用 findViewById 了，因为它的方法比较长，所以我不贴代码了，就简单的说一下它的工作过程。

