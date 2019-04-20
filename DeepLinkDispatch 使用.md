# DeepLinkDispatch 使用



###引入

```groovy
dependencies {
  implementation 'com.airbnb:deeplinkdispatch:3.1.1'
  annotationProcessor 'com.airbnb:deeplinkdispatch-processor:3.1.1'
}
```

如果为 kotlin 工程，使用 kapt 代替 annotationProcessor。



### 创建Module

主工程：

```java
@DeepLinkModule
public class AppDeepLinkModule {
}
```

其他 module 工程：

```java
@DeepLinkModule
public class LibraryDeepLinkModule {
}
```

与Glide类似，比较好理解。



### 声明一个入口 Activity

```xml
<activity
    android:name="com.example.DeepLinkActivity"
    android:theme="@android:style/Theme.NoDisplay">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="foo" />
    </intent-filter>
</activity>
```

建议使用，这样，所有的 deepLink 响应都会走这个Activity，然后由这个 Activity 来转发，便于管理。



### 将请求转发

```java
@DeepLinkHandler({ AppDeepLinkModule.class, LibraryDeepLinkModule.class })
public class DeepLinkActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // DeepLinkDelegate, LibraryDeepLinkModuleLoader and AppDeepLinkModuleLoader
    // are generated at compile-time.
    DeepLinkDelegate deepLinkDelegate = 
        new DeepLinkDelegate(new AppDeepLinkModuleLoader(), new LibraryDeepLinkModuleLoader());
    // Delegate the deep link handling to DeepLinkDispatch. 
    // It will start the correct Activity based on the incoming Intent URI
    deepLinkDelegate.dispatchFrom(this);
    // Finish this Activity since the correct one has been just started
    finish();
  }
}
```

DeepLinkDelegate 类与XXXModuleLoader 类都会自动生成，所以该类里面的逻辑几乎不用改动。



### 配置跳转Intent



#### 1. 使用注解（推荐）

```java
@DeepLink("foo://example.com/methodDeepLink/{param1}")
public static Intent intentForDeepLinkMethod(Context context, Bundle extras) {
  Uri.Builder uri = Uri.parse(extras.getString(DeepLink.URI)).buildUpon();
  return new Intent(context, MainActivity.class)
      .setData(uri.appendQueryParameter("bar", "baz").build())
      .setAction(ACTION_DEEP_LINK_METHOD);
}

/**
 * 一次性打开多个Activity（按次序）
 */
@DeepLink("http://example.com/deepLink/{id}/{name}")
public static TaskStackBuilder intentForTaskStackBuilderMethods(Context context) {
  Intent detailsIntent =  new Intent(context, SecondActivity.class).setAction(ACTION_DEEP_LINK_COMPLEX);
  Intent parentIntent =  new Intent(context, MainActivity.class).setAction(ACTION_DEEP_LINK_COMPLEX);
  TaskStackBuilder  taskStackBuilder = TaskStackBuilder.create(context);
  taskStackBuilder.addNextIntent(parentIntent);
  taskStackBuilder.addNextIntent(detailsIntent);
  return taskStackBuilder;
}
```



#### 2.对Activiy单独声明 （上面的方法二选一）

```java
@DeepLink({"foo://example.com/deepLink/{id}", "foo://example.com/anotherDeepLink"})
public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    if (intent.getBooleanExtra(DeepLink.IS_DEEP_LINK, false)) {
      Bundle parameters = intent.getExtras();
      String idString = parameters.getString("id");
      // Do something with idString
    }
  }
}
```



### 添加防止混淆代码

1. ```properties
   -keep @interface com.airbnb.deeplinkdispatch.DeepLink
   -keepclasseswithmembers class * {
       @com.airbnb.deeplinkdispatch.DeepLink <methods>;
   }
   ```

2. 按照官方文档，添加之后发现，@DeepLink 被注释的方法仍然被移除了，**所以需要使用 @Keep 保留一下**




### 测试

使用 shell 测试。

测试代码：

```shell
am start -W -a android.intent.action.VIEW -d "your url" your-package-name
```

-d 后面为链接，可以接参数。







