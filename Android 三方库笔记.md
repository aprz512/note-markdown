## Android 三方库学习笔记

### Timber

我每次写log的时候，都特别不爽的是，为啥我每次都要写一个TAG，如果能自动获取当前类的类名该多好。只是我一直没有想到获取的方法，现在，刚接触了Timber，我就立即看了相关部分源码，发现了它的解决方案。

```java
@Override final String getTag() {
      String tag = super.getTag();
      if (tag != null) {
        return tag;
      }

      // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
      // because Robolectric runs them on the JVM but on Android the elements are different.
      StackTraceElement[] stackTrace = new Throwable().getStackTrace();
      if (stackTrace.length <= CALL_STACK_INDEX) {
        throw new IllegalStateException(
            "Synthetic stacktrace didn't have enough elements: are you using proguard?");
      }
      return createStackElementTag(stackTrace[CALL_STACK_INDEX]);
    }
```

使用 Throwable 来获取栈信息（没有用线程，看注释解释）。

```java
    protected String createStackElementTag(@NotNull StackTraceElement element) {
      String tag = element.getClassName();
      Matcher m = ANONYMOUS_CLASS.matcher(tag);
      if (m.find()) {
        tag = m.replaceAll("");
      }
      tag = tag.substring(tag.lastIndexOf('.') + 1);
      // Tag length limit was removed in API 24.
      if (tag.length() <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return tag;
      }
      return tag.substring(0, MAX_TAG_LENGTH);
    }
```

从 element 的 className 中截取 tag。

那么我们写个例子测试一下

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    Handler().post(Runnable {
            Throwable().stackTrace.forEach {
                Timber.e(it.className)
                Timber.e(it.methodName)
            }
        })
}
```

输出：

```java
2019-02-28 16:42:55.871 14227-14227/com.example.android.dessertpusher E/MainActivity$onCreate: com.example.android.dessertpusher.MainActivity$onCreate$2
2019-02-28 16:42:55.871 14227-14227/com.example.android.dessertpusher E/MainActivity$onCreate: run
```

因为，源码里面对匿名内部类的tag做了处理，所以该匿名内部类的 TAG 为 `MainActivity$onCreate`。后面的 `$2`被正则表达式替换掉了。

