# ViewGroup 的 generateLayoutParams 方法的作用

不知道大家在编写布局的时候有没有这样的疑问：在 RelativeLayout 布局里面可以对子控件使用 layout_alignParentRight 等属性，但是在 FrameLayout 或者 LinearLayout 中就没有，这是为什么呢？

```xml
    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_alignParentRight="true" />

    </RelativeLayout>
```



而且，在代码中，我们获取子控件的 LayoutParams 的时候，默认是 ViewGroup.LayoutParams  类型的，我们想要使用某些特殊的字段，还需要转换为 RelativeLayout.LayoutParams 或其他具体的 LayoutParams 才行。这又是为什么呢？



下面，我们一一道来。

当我们自定义一个控件的时候，如果我们做的灵活一点，一般会提供一些属性配置方法。

```java
    public TagGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TagGroup);

        int targetColorInt = typedArray.getInt(R.styleable.TagGroup_targetColor, 0);
        if (targetColorInt != 0) {
            targetColor = context.getResources().getColor(targetColorInt);
        } else {
            targetColor = Color.WHITE;
        }

        typedArray.recycle();
    }
```

在 attrs.xml 中配置：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <declare-styleable name="TagGroup">
        <attr name="targetColor" format="integer"/>
    </declare-styleable>

</resources>
```

但是使用这种方式的话，不管你在什么 ViewGroup 下使用，它都有这个属性。而且，在xml中，这个属性只能给自己用，无法给它的子控件使用。 显然，RelativeLayout 没有使用这种方式。那它是怎么做的呢？答案是通过复写 ViewGroup 的 generateLayoutParams 方法实现的。

这里我们据一个例子来说明，我们自定义一个控件：

> com.aprz.myapplication.MyViewGroup

```kotlin
class MyViewGroup @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attributeSet, defStyleAttr) {

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // TODO("not implemented")
    }
    
}
```

由于我们关注的重点不在控件的功能，所以我们不重写 onLayout 方法。这里我们需要重写的是它的 generateLayoutParams  方法：

> com.aprz.myapplication.MyViewGroup#generateLayoutParams

```kotlin
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MyLayoutParams(context, attrs)
    }
```

这里我们返回了我们自己创建的 MyLayoutParams 对象。它的实现如下：

> com.aprz.myapplication.MyViewGroup.MyLayoutParams

```kotlin
    class MyLayoutParams(context: Context, attrs:AttributeSet?) : ViewGroup.MarginLayoutParams(context, attrs) {

        private var stayLeft : Int = 0
        private var stayRight : Int = 0

        init {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.MyViewGroup_Layout
            )

            stayLeft = a.getResourceId(R.styleable.MyViewGroup_Layout_stayLeft, 0)
            stayRight = a.getResourceId(R.styleable.MyViewGroup_Layout_stayRight, 0)

            a.recycle()
        }

    }
```

这里的代码应该就很熟悉了，与自定义控件的属性的配置方式与流程基本是一摸一样的。同样的也需要在 attrs.xml 中配置：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <declare-styleable name="MyViewGroup_Layout">
        <attr name="stayLeft" format="reference" />
        <attr name="stayRight" format="reference" />
    </declare-styleable>

</resources>
```

这样，我们在 xml 中使用该布局的时候，就可以给子控件配置这两个属性了：

```xml
    <com.aprz.myapplication.MyViewGroup
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
            android:id="@+id/tv1"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            app:stayRight="@id/tv1"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>

    </com.aprz.myapplication.MyViewGroup>
```

其实generateLayoutParams方法的作用其实就是定义你的控件下所有子控件所使用的layoutParams类，通过这种形式使你的控件可以按自己想要的方式和属性来操作它的子view，你甚至不需要关心子view本身，只要你重写过generateLayoutParams方法，他们就一定会使用你给的LayoutParams来修饰自己。



但是，这里有个问题，就是 IDE 好像无法识别自定义的属性，在 xml 中使用的时候会报红线，但是可以运行。