# DataBinding 原理分析

DataBinding 是一个支持库，它可以将布局中的界面组件绑定到数据源上，做到UI与数据的单项或者双向监听。说白一点就是数据发生变化可以直接反映到界面上，不用再次手动操作了。当然它的作用远远不止于此，本文从这个点入手，来略微深入一下它的实现过程。



首先，DataBinding可以将数据的变化反应到UI上，实际上就是帮助我们更新UI，那么它肯定需要持有（直接或者间接）UI的引用，不然的话，是没法操作UI的。用过DataBinding 的小伙伴应该都知道，DataBinding 会根据布局生成一个类，这个类里会有许多成员变量，每个变量对应着布局里面各个控件。



PS：**因为每个项目生成的代码不一致，而且我使用了多个项目生成的代码，所以看的时候不要太纠结，尽量理解为主。**



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

> androidx.databinding.ViewDataBinding#mapBindings(androidx.databinding.DataBindingComponent, android.view.View, java.lang.Object[], androidx.databinding.ViewDataBinding.IncludedLayouts, android.util.SparseIntArray, boolean)

```java
        if (isRoot && tag != null && tag.startsWith("layout")) {
            final int underscoreIndex = tag.lastIndexOf('_');
            if (underscoreIndex > 0 && isNumeric(tag, underscoreIndex + 1)) {
                // 这里的 index 就是 content_main_0 的 0
                final int index = parseTagInt(tag, underscoreIndex + 1);
                if (bindings[index] == null) {
                    bindings[index] = view;
                }
                indexInIncludes = includes == null ? -1 : index;
                isBound = true;
            } else {
                indexInIncludes = -1;
            }
        }
```

首先是获取到 tag 以 layout 开头的 View，将这个view 放入到 bindings[0] 中。



```java
        } else if (tag != null && tag.startsWith(BINDING_TAG_PREFIX)) {
            // 这里的 index 是 binding_1 的 1，当然不只是 1，还有 2，3....
            int tagIndex = parseTagInt(tag, BINDING_NUMBER_START);
            if (bindings[tagIndex] == null) {
                bindings[tagIndex] = view;
            }
            isBound = true;
            indexInIncludes = includes == null ? -1 : tagIndex;
        }
```

然后获取以 tag 为 binding_ 开头的 View，放入到 bindings[1...n] 中。



```java
        if (!isBound) {
            final int id = view.getId();
            if (id > 0) {
                int index;
                if (viewsWithIds != null && (index = viewsWithIds.get(id, -1)) >= 0 &&
                        bindings[index] == null) {
                    bindings[index] = view;
                }
            }
        }
```

最后，如果控件没有id，但是使用了 `@{}` 的用法，也会存入 bindings 数组中，这个index也是接着上面 binding_ 的数字，比如，上面最后一个是 binding_5，这里的 index 就是从 6 开始了，这些数值都是编译器生成好了的。我猜想是在处理 xml 的时候，就需要生成对应的类，然后将index对应好。



有了这个数组，显然只需要将它赋值给对应的变量就好了。我们可以生成控件的成员变量，然后以驼峰式命名，将数组的值赋值给对应的变量。

> com.aprz.snackbardemo.databinding.ContentMainBindingImpl#ContentMainBindingImpl(androidx.databinding.DataBindingComponent, android.view.View, java.lang.Object[])

```java
    private ContentMainBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (android.widget.TextView) bindings[4]
            , (android.widget.TextView) bindings[1]
            , (android.widget.TextView) bindings[2]
            );
        this.mboundView0 = (android.widget.LinearLayout) bindings[0];
        this.mboundView0.setTag(null);
        this.mboundView3 = (android.widget.TextView) bindings[3];
        this.mboundView3.setTag(null);
        this.tvName.setTag(null);
        this.tvSex.setTag(null);
        setRootTag(root);
        // listeners
        invalidateAll();
    }

----------------------------------------
    
  protected ContentMainBinding(Object _bindingComponent, View _root, int _localFieldCount,
      TextView tvClassName, TextView tvName, TextView tvSex) {
    super(_bindingComponent, _root, _localFieldCount);
    this.tvClassName = tvClassName;
    this.tvName = tvName;
    this.tvSex = tvSex;
  }
```

从代码里面可以看出，它确实是将bindings赋值给了成员变量。没有id的无法外部使用 ，所以是 ContentMainBindingImpl 的成员变量，内部名字叫做 mboundViewXXX。

![](F:\note-markdown\DataBinding原理分析\未命名表单.png)



 说了这么多，只是讲了一下它的如何不用 findViewById 的。但是 DataBinding 还有更重要的作用，就是数据绑定，我们接下来分析分析，它是如何将数据绑定到 UI 的，而且数据更新之后，是如何改变 UI 的！！！



实现数据绑定，我们需要调用binding.setVariable或者binding.setViewModel，两者效果一样，因为setVariable会间接调用setViewModel方法。

> com.aprz.databinding.ContentMainBindingImpl

```java
    // variableId 是生成的BR文件中的一个变量，对应于你在 xml 中设置的变量
	@Override
    public boolean setVariable(int variableId, @Nullable Object variable)  {
        boolean variableSet = true;
        if (BR.viewModel == variableId) {
            setViewModel((com.aprz.snackbardemo.User) variable);
        }
        else {
            variableSet = false;
        }
        return variableSet;
    }

    public void setViewModel(@Nullable com.aprz.snackbardemo.User ViewModel) {
        // 这个方法有个坑，后面会说到
        this.mViewModel = ViewModel;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.viewModel);
        super.requestRebind();
    }
```

可以看到实际上主要是调用了一下 notifyPropertyChanged 方法。notifyPropertyChanged  内部就是做了一个回调监听的操作，和我们的观察者模式没有区别，但是这里比较搞笑的就是，此时监听是为 null 的，也就是说没有注册观察者。

它在代码中表现的行为是这样的：我们创建一个对象A，将A通过 binding.setVariable 方法绑定到数据上，是可以正常显示出数据的，但是如果我们改变了对象A的某个属性，这个时候，属性的变化是无法反映到UI上的，我们还需要手动更新UI。

那么当我们改变了对象A的某个属性时，怎么才能自动更新UI 呢？参考官方文档的一个方法是使用 @Bindable 注解，比如我们的对象长这样：

> com.aprz.aboutme.MyName

```kotlin
data class MyName(var name: String) : BaseObservable() {

    @get:Bindable
    var nickname: String = "aprz"
        set(value) {
            field = value
            notifyPropertyChanged(com.aprz.aboutme.BR.nickname)
        }

}
```

可以看到，每当 set 方法调用的时候，我们需要手机调用一下 notifyPropertyChanged 方法，这个时候，我们再看生成的文件，查看 `setViewModel` 方法：

> com.aprz.databinding.ContentMainBindingImpl#setViewModel

```java
    public void setViewModel(@Nullable com.aprz.snackbardemo.User ViewModel) {
        // hhh
        updateRegistration(0, ViewModel);
        this.mViewModel = ViewModel;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.viewModel);
        super.requestRebind();
    }
```

可以看到，第一行多了一行代码：updateRegistration，应该可以猜到，这个方法里面**应该就是注册了观察者**。为了验证我们的想法，查看一下这个方法：

> androidx.databinding.ViewDataBinding#updateRegistration(int, androidx.databinding.Observable)

```java
    protected boolean updateRegistration(int localFieldId, Observable observable) {
        return updateRegistration(localFieldId, observable, CREATE_PROPERTY_LISTENER);
    }
```

这里的调用链比较深，我们只关心重要的方法，最后发现调用到了如下方法

> androidx.databinding.ViewDataBinding.WeakListener#setTarget

```java
        public void setTarget(T object) {
            unregister();
            mTarget = object;
            if (mTarget != null) {
                mObservable.addListener(mTarget);
            }
        }
```

这里的 mTarget 是上面的 viewModel 变量，mObservable 是一个叫做 WeakPropertyListener 的类，因为我们省略了中间的调用过程，所以会有点突兀，但是我们把它当作一个 WeakListener 的一个包装类就好了，它持有 WeakListener 的引用而已。

再往下最终，会发现调用到了这里：

> androidx.databinding.BaseObservable#addOnPropertyChangedCallback

```java
    @Override
    public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
        synchronized (this) {
            if (mCallbacks == null) {
                mCallbacks = new PropertyChangeRegistry();
            }
        }
        mCallbacks.add(callback);
    }
```

这里就比较熟悉了吧，就是 notifyPropertyChanged 会触发监听回调，而这个监听就是在这里添加（注册）的。

经过上面的一连串调用，viewModel，WeakPropertyListener ，WeakListener ，就建立这样的一个关系：

![](F:\note-markdown\DataBinding原理分析\未命名表单 (1).png)



因为 ViewModel 继承至 BaseObservable，所以它有一个成员变量：mCallbacks，而 **updateRegistration 方法主要是添加了一个观察者**。实际上DataBinding的自动更新UI原理还是观察者，但是它的高明之处是编译器自动生成逻辑代码。

好的，说完了观察者的注册，还有一步需要完成，就是通知观察者数据发生了变化。应该还记得，我们的 ViewModel 里面，set 方法都调用了一个方法`notifyPropertyChanged`：

```kotlin
    @get:Bindable
    var nickname: String = "aprz"
        set(value) {
            field = value
            notifyPropertyChanged(com.aprz.aboutme.BR.nickname)
        }
```

这个很显然就是通知观察者，我们的数据发生了变化，我们看看源码吧（其实不看都知道，最终触发了 mCallbacks 的回调）。同样的经过多层调用，到了下面的方法：

> androidx.databinding.ViewDataBinding#handleFieldChange

```java
    private void handleFieldChange(int mLocalFieldId, Object object, int fieldId) {
        if (mInLiveDataRegisterObserver) {
            // We're in LiveData registration, which always results in a field change
            // that we can ignore. The value will be read immediately after anyway, so
            // there is no need to be dirty.
            return;
        }
        boolean result = onFieldChange(mLocalFieldId, object, fieldId);
        if (result) {
            requestRebind();
        }
    }
```

主要是两个方法，先看第一个，看名字就应该是字段发生了变化的处理，该方法会调用到下面的方法：

> com.aprz.aboutme.databinding.ActivityMainBindingImpl#onChangeMyName

```java
    private boolean onChangeViewModel(com.foxlee.testdatabinding.NewsViewModel ViewModel, int fieldId) {
        switch (fieldId) {
            case BR.name: {
                synchronized(this) {
                        mDirtyFlags |= 0x2L;
                }
                return true;
            }
            case BR.value1: {
                synchronized(this) {
                        mDirtyFlags |= 0x4L;
                }
                return true;
            }
            case BR._all: {
                synchronized(this) {
                        mDirtyFlags |= 0x1L;
                }
                return true;
            }
        }
        return false;
    }
```

这里其实啥都没做，就只给 mDirtyFlags 设置了一个标记位，这里就很灵性了，它不是与我们通常的想法一样，给每个字段分别处理，而是只是设置一个标记。

再看 requestRebind，从名字也可以看出来，应该是重新绑定，**因为 onChangeMyName 给字段发生了变化的位设置了标记，所以在这个方法里面，应该就是根据标志位来刷新UI了**，好，我们看看：

> androidx.databinding.ViewDataBinding#requestRebind

```java
    protected void requestRebind() {
        if (mContainingBinding != null) {
            mContainingBinding.requestRebind();
        } else {
            ...
            if (USE_CHOREOGRAPHER) {
                mChoreographer.postFrameCallback(mFrameCallback);
            } else {
                mUIThreadHandler.post(mRebindRunnable);
            }
        }
    }
```

如果对View的绘制源码有一点了解的，这里应该很好理解，这里就是刷新UI 了。然后继续往下追踪，它会调用到这个方法：

> com.aprz.aboutme.databinding.ActivityMainBindingImpl#executeBindings

```java
        if ((dirtyFlags & 0xfL) != 0) {


            if ((dirtyFlags & 0xbL) != 0) {

                    if (viewModel != null) {
                        // read viewModel.name
                        viewModelName = viewModel.name;
                    }
            }
            if ((dirtyFlags & 0xdL) != 0) {

                    if (viewModel != null) {
                        // read viewModel.value1
                        viewModelValue1 = viewModel.value1;
                    }
            }
        }
        // batch finished
        if ((dirtyFlags & 0xdL) != 0) {
            // api target 1

            com.foxlee.testdatabinding.NewsViewModel.onTestChange(this.mboundView3, viewModelValue1);
            com.foxlee.testdatabinding.NewsViewModel.onTestChange(this.tvValue, viewModelValue1);
        }
        if ((dirtyFlags & 0xbL) != 0) {
            // api target 1

            com.foxlee.testdatabinding.NewsViewModel.onTestChange(this.tvName, viewModelName);
        }
```

可以看到，这个方法里面就是根据 dirtyFlags 的标志位来更新UI的。这个标志位的算法需要说一下，我们拿 name 的更新举例子：

在 `onChangeViewModel` 方法中，name字段更新的时候，给 mDirtyFlags 设置的标志位是 `mDirtyFlags |= 0x2L;`，而在 `executeBindings` 方法中，判断 name 字段的更新是使用的 `dirtyFlags & 0xbL` 来判断的，这是为啥呢？

这里不去深入研究它的计算规则了，只是简单的说一下：

0x1，0x2，0x4，0xb，0xd，0xf，他们转换成二进制是这样的：

```shell
0001	// 全部需要更新
0010	// 字段1需要更新
0100	// 字段2需要更新
---------------------------------------------------
1011	// 字段1需要更新，第2位必定为1，所以满足if条件
1101	// 字段2需要更新，第3位必定为1，所以满足if条件
```

所以，dirtyFlags 的某一位代表着某个字段需要更新。这样自动刷新UI的逻辑也分析完了。

还有一个问题，就是为啥，第一次会主动更新UI呢？？？

其实就是 setViewModel 会将 mDirtyFlags 的 第一位设置为1，会导致所有的if条件都满足，就会更新所有UI。

