# Drawable 介绍

Drawable 的内部宽高比较重要，通过 getIntrinsicWidth 等可以获取。

不是所有的 Drawble 都有内部宽高，对于图片形成的 drawble ，它的内部宽高就是图片的宽高，对于颜色形成的 drawable，它没有内部宽高。

Drawble 没有大小的概念，都会拉伸到与 View 一样大。



## BitmapDrawable

属性介绍：

- antialias： 抗锯齿，就是画线的时候锯齿会减少，特别是画斜线与园

- dither： 开启抖动效果。当图片的像素配置和手机屏幕的像素配置不一样的时候，开启这个可以让高质量的图片在低质量的屏幕上还能保持较好的显示效果。

  > 比如：图片的色彩模式为 ARGB8888，手机屏幕只支持 RGB555，这个时候，开启抖动选项可以让图片显示不会太过失真。

- filter：图片拉伸或者压缩时可以保持较好的效果

- gravity：与 scaleType 类似

- tileMode：平铺模式，有重复平铺（repeat），镜像平铺（mirror），边缘拉伸（clamp），禁止（disable）

BitmapDrawable 用起来比较简单。

.9 图片需要特殊说明以下：

- left 线：表示纵向可拉伸区域

- top 线：表示横向可拉伸区域

- bottom 线 与 right 线交叉的区域：表示内容显示的区域

具体的请点击[这里](<https://blog.csdn.net/lastwarmth/article/details/49991445>) 。



## ShapeDrawable 

比较常用，可以画各种图形，渐变，只有 size 属性需要说一下。

- size ： 这个属性可以设置 getIntrinsicWidth 的返回值。表示 drawble 的内部宽高。

这里有个比较神奇的地方：

首先，我们写一个 shape：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
       android:dither="true"
       android:shape="rectangle">

    <size
          android:width="1dp"
          android:height="1dp" />

    <solid android:color="@color/colorAccent" />

    <corners android:radius="5dp" />

    <stroke
            android:width="2dp"
            android:color="@color/colorPrimaryDark"
            android:dashWidth="3dp"
            android:dashGap="3dp" />

</shape>
```

然后，设置到一个 View 上面：

```xml
    <TextView
        android:id="@+id/tv"
        android:layout_width="200dp"
        android:layout_height="100dp"
        android:layout_centerInParent="true"
        android:background="@drawable/shape_drawable"
        android:gravity="center"
        android:text="Hello World!" />
```

然后，获取它的 background：

```xml
TextView tv = (TextView)findViewById(R.id.tv);
Drawble drawable = (GradientDrawable) tv.getBackground();
```

你会发现，它获取到的不是 ShapeDrawable，而是 GradientDrawable。



## LayerDrawable 

层叠的 drawble。它可以画出单一边线的效果，其实就是利用几个大小差一点点的矩形做成的效果：

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">

    <!--底层使用蓝色填充色-->
    <item>
        <shape>
            <solid android:color="#02a0ef"/>
        </shape>
    </item>

    <!--上面一层距离底层的顶部1dp,类似marginTop,填充色为白色，这样就形成了一个带有蓝色顶部边线的白色背景的图-->
    <item android:top="1dp">
        <shape>
            <solid android:color="#fff"/>
        </shape>
    </item>
</layer-list>
```

![](F:\note-markdown\Drawable 介绍\20161206100146670.jpg)



## StateListDrawble

就是对应于 selector 标签，还是非常常用的。

不过需要注意的是，系统会根据当前的状态从selector中选择对应的 item，系统会按照从上往下的顺序查找，知道找到第一条匹配的 item。

这里就要注意了，如果你把默认的item放在第一条的话，系统寻找的时候，无论是选中，按压等所有的情况都会匹配第一条，因为默认的不带任何状态，系统认为它匹配所有的状态。



## LevelListDrawble

对应于 level-list 标签。

可以用于切换图片，一个等级对应一张图，换图的时候只需要调用 img.setImageLevel 就好了。



## TransitionDrawable

对应于 transition 标签。

可以用于给一张图切换到另一张图时的过渡动画。



## InsetDrawable

对应于 inset 标签。

当一个 View 需要背景比内容区域要小的时候，可以使用这个。



## ScaleDrawable

对应于 scale 标签。

ScaleDrawable 有一个等级的概念，等级不仅会影响缩放的比例，还会影响绘制。

> android.graphics.drawable.ScaleDrawable#draw

```java
    @Override
    public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d != null && d.getLevel() != 0) {
            d.draw(canvas);
        }
    }
```

显然，如果 level 是 0 的时候，不会绘制 drawble。

对于缩放比例，它内部还有这样的一个公式：

```java
// 伪代码
w -= (int) (w * (10000 - level) * mScaleState.mScaleWidth / 10000)
```

可以看到，如果 level 越接近于0（需要大于0），那么缩放的比例就与 （1- mScaleState.mScaleWidth） 越相近。如果 level 为 10000，那么就是不缩放，不管 mScaleState.mScaleWidth 的值为多少。

mScaleState.mScaleWidth 就是我们在 xml 中设置的 scaleWidth 等属性。

看一个例子，将一张图缩小为原来的30%左右（之所以是为左右，是因为 level 最小为1，所有会有 1/10000的 误差）：

```java
<?xml version="1.0" encoding="utf-8"?>
<scale xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/logo"
    android:scaleGravity="center_vertical|center_horizontal"
    android:scaleHeight="70%"
    android:scaleWidth="70%"/>
```

注意，这里是缩小 70%。由于 level 默认为0，所以我们还需要用代码设置以下：

```java
scaleDrawable.setLevel(1);
```



## ClipDrawable

对应于 clip 标签。

对一个drawable进行裁剪。其中 gravity 属性表示裁剪方向。

需要设置 leve 来表示裁剪多少，范围是 0~10000，0表示完全裁剪，即整个drawable都不见了，而 10000 表示不裁剪。