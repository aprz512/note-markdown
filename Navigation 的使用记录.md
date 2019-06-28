# Navigation 的使用记录

接到一个处理流程的需求，有好几个界面，界面之间的跳转有点复杂，还需要支持回退，于是想到使用 Jetpack 的 Navigation 包。

具体的使用可以参考[官方文档](<https://developer.android.com/guide/navigation/>) 。暂时不做介绍，考虑做一个翻译系列，将 Jetpack 全部翻译一遍，不过现在没有精力。

等使用 Navigation 写完全部的跳转之后，发现一个严重的问题，那么就是 Navigation 不支持 Fragment 的状态保存。也就是说我在 FragmentA 做了一些操作，然后调到 FragmentB，在 FragmentB 中准备做一些操作的时候，突然发现有问题，想回到 FragmentA ，于是我点击了返回键回到 FragmentA ，就会发现 FragmentA  中的数据被重置了，我在 FragmentA  的操作没有了，这是用户无法接受的。

然后我去看了官方的 demo，发现它居然可以记录操作，我对比了一下工程代码，发现官方demo里面的是 EditText，我的项目里面是 CheckBox，只有控件的区别，于是我果断在官方的demo里面添加了CheckBox控件，发现也无法保存CheckBox的状态。**这就让我无法理解了，为啥 EditText 可以保存状态，CheckBox 就不行？**这个问题我现在还没有答案。

我又去翻了一下，Navigation 的相关源码，控制 Fragment 跳转的是`androidx.navigation.fragment.FragmentNavigator`。

它有一个 navigate 方法：

> androidx.navigation.fragment.FragmentNavigator#navigate

```java
    @Nullable
    @Override
    public NavDestination navigate(@NonNull Destination destination, @Nullable Bundle args,
            @Nullable NavOptions navOptions, @Nullable Navigator.Extras navigatorExtras) {
        ...
        final Fragment frag = instantiateFragment(mContext, mFragmentManager,
                className, args);
        frag.setArguments(args);
        final FragmentTransaction ft = mFragmentManager.beginTransaction();
		...

        ft.replace(mContainerId, frag);
        ft.setPrimaryNavigationFragment(frag);

        ...
        ft.commit();
        ...
    }
```

这个逻辑很明显了，首先实例化要跳转到的 Fragment，然后直接替换原来的 fragment。

这里说一下，当使用 replace 的时候会发生什么：

假设 A 已经添加到了 mContainerId，这个时候调用了 replace B。

> Fragment B

```
onAttach

onCreate
```

> Fragment A

```
onPause

onStop

onDestroyView

onDestroy

onDetach
```

> Fragment B

```
onCreateView

onActivityCreated

onStart

onResume
```

可以看到，Fragment A 执行了 onDestroyView 与 onDetach，也就是说它完全从 Activity 上脱离了。再显示出来需要重新走一遍生命周期流程，**但是这里需要注意的是，这个生命周期流程是没有添加到会退栈的，添加到会退栈的有点不一样。**

因为 FragmentNavigator 内部将 Fragment A 添加到了会退栈，所以调用 replace B 的生命周期流程如下：

> Fragment B

```
onAttach

onCreate
```

> Fragment A

```
onPause

onStop

onDestroyView
```

> Fragment B

```
onCreateView

onActivityCreated

onStart

onResume
```

可以看到与上面的区别是，Fragment A 只执行到了 onDestroyView，并没有走下面的 onDestroy 等方法。虽然它只执行到了 onDestroyView，**但是它重新显示的时候，肯定要重新走 onCreateView**，而一般我们的初始化逻辑都是在 onCreateView 中执行的，所以操作就都被重置了。



要想解决这个问题，现在就有两种选择：一是放弃 Navigation，二是自定义 FragmentNavigation，虽然我知道

第二种方法比较蛋疼，但是我还是想试一下，虽然最后还是失败了，但是还是记录一下过程。

自定义 Navigation 的文档在 [这里](<https://developer.android.com/guide/navigation/navigation-add-new>)，也可以参考这个[项目](<https://github.com/STAR-ZERO/navigation-keep-fragment-sample>) 。

首先要处理 Fragment 每次都实例化的问题，这里可以自己缓存起来。

然后是想办法将 replace 替换为 add 之类的方法。我是覆盖了 navigate 方法，然后copy出了一部分源码，效果是达到了。

最后是处理返回键，这个我没有处理好。其实，不继承 FragmentNavigation，而是继承 Navigator，完全自己实现一个，或许会更简单一些，不过需要对 Fragment 的理解比较深入。

