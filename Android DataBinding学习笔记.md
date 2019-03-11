# Android DataBinding 学习笔记



### @BindingConversion

- 作用于方法
- 被该注解标记的方法，被视为dataBinding的转换方法。
- 方法必须为公共静态（public static）方法，且有且只能有1个参数

作用：

​	**在使用databinding的时候，对属性值进行转换，以匹配对应的属性。** 

例子，比如android:background属性，看源码：

```java
public void setBackground(Drawable background) {
    //noinspection deprecation
    setBackgroundDrawable(background);
}
```

android:background接收的值是drawable，如果给的值是@color/blue:

```xml
android:background="@{@color/blue}"
```

若没有进行转换，则会报错。 
所以需要定义一个转换方法，把@color/blue转换为drawable类型：

```java
@BindingConversion
public static ColorDrawable convertColorToDrawable(int color) {
   return new ColorDrawable(color);
}
```

对于convertColorToDrawable()方法，在databinding的jar包中已经定义，所以我们不要重复定义。

如何工作：

​	**在xml中使用databinding方式赋值的属性，在对应的类中都有相应的setter，如果在xml中设置的属性值的类型与对应的setter的参数类型不符，这时 dataBinding就会去寻找可以让属性值转换为正确类型的方法，而寻找的根据就是所有被@BindingConversion注解标记的方法，这时convertColorToDrawable方法就会被调用了。**

```
如果我们自己定义了一个功能相同的convertColorToDrawable方法，那么dataBinding会优先使用我们自己定义的方法。
```



### @BindingMethods

有一些控件的 setXXX 方法，并不与属性名匹配。对于这种情况，可以使用 BindingMethods 注解来处理。

例如：

```xml
<ImageView
           android:id="@+id/imageView"
           android:layout_width="wrap_content"
           android:layout_height="wrap_content"
           android:layout_marginEnd="24dp"
           android:layout_marginTop="24dp"
           android:tint="@color/colorAccent" />
```

拿这个例子来说，按照一般的情况，`android:tint` 会调用 ImageView 的 `setTint` 方法，但是 ImageView 里面没有这个 `setTint` 方法，但是却有个 `setImageTintList`方法，所以我们要将属性转换到对应的方法，具体如下：

```java
@BindingMethods({
       @BindingMethod(type = "android.widget.ImageView",
                      attribute = "android:tint",
                      method = "setImageTintList"),
})
```



### @BindingAdapter

有一些控件只有属性，却没有 setXXX 方法，比如 View：

```java
    protected int mPaddingLeft = 0;
```

而搜索类发现并没有 `setPaddingLeft` 方法，只有 `setPadding` 方法。那么这种情况，我们就可以提供一个适配方法：

```java
@BindingAdapter("android:paddingLeft")
public static void setPaddingLeft(View view, int padding) {
   view.setPadding(padding,
                   view.getPaddingTop(),
                   view.getPaddingRight(),
                   view.getPaddingBottom());
}
```

其实，本质上还是利用了 `setPaddingLeft`来打到目的。





参考链接：

https://blog.csdn.net/lixpjita39/article/details/79049872



