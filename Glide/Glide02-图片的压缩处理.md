## Glide02-图片的压缩处理



### 引言

记得刚初出茅庐的时候，去新浪微博面试，面试官问了一个图片压缩的问题：如何把一个`1200x900`的图片压缩到`400x300`的尺寸？

当初的我只能回答出用 BitmapFactory.Options 的 inSampleSize 字段，不过只能压缩到 600x450。令我影响深刻的是面试官补充了一句`600x450`的图片可以用OPENGL的手段再压缩到`400x300`。回到宿舍我把这个问题问了室友，他也说出了OPENGL，啥也不懂的我就觉得好牛逼！

然而现在，我发现根本不需要啥OPENGL，现在的公司在做一个拍照的功能，需要用到图片压缩，而我刚看完《Android高性能编程》这本书，里面就介绍了图片的压缩处理，有一段代码就是用来解决非2次幂压缩的问题的。

### 实例

然是用上面的例子吧，首先我们要计算出inSampleSize的值，因为使用inSampleSize运算会比较快。比如：`1200x900`的图是可以直接压缩到 `400x300`，但是先使用 inSampleSize，压缩一半到 `600x450`，再压缩到`400x300`会比直接压缩速度更快。为什么会更快，请看最后的分析。

附上一份常见的计算 inSampleSize 值的代码：

```java
public static int calInSampleSize(BitmapFactory.Options options,
                                  int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;
        while ((halfHeight / inSampleSize) > reqHeight
               && (halfWidth / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
    }
    return inSampleSize;
}
```

接下来就是重点了，使用 BitmapFactory.Options 的 inDensity 字段与 inTargetDensity 字段，就可以做到任意比例的压缩。

我们先贴上代码，然后再说为什么，下面的方法可以提供一个 options，用来做到任意比例的压缩：

```java
public static BitmapFactory.Options getOptions(
    BitmapFactory.Options options, int reqWidth, int reqHeight) {
    options.inSampleSize = calInSampleSize(options, reqWidth, reqHeight);
    // 需要设置这个字段，否则设置了 inDensity 和 inTargetDensity 也不生效
    // 不过它默认值是 true
    options.inScaled = true;
    options.inDensity = options.outHeight;
    options.inTargetDensity = reqHeight * options.inSampleSize;
    options.inJustDecodeBounds = false;
    return options;
}
```

这里有个坑，我第一次凭着记忆写这个代码的时候，是用的除法：

```java
public static BitmapFactory.Options getOptions(
    BitmapFactory.Options options, int reqWidth, int reqHeight) {
    ...
    options.inDensity = options.outHeight / options.inSampleSize;
    options.inTargetDensity = reqHeight;
    ...
    return options;
}
```

虽然与上面看起来效果一样，但是由于是整除，所以会有误差，我写完这个代码，总觉得哪里不对，把《Android高性能编程》翻出来看了一遍才知道问题所在。

现在来说说为什么这段代码可以做到任意比例的压缩，我们从图片的解码开始看起吧，需要读一点 native 代码，不过不用深究细节，所以难度不大。

### 图片加载源码

从**BitmapFactory.java**中选取一个解码图片的方法：

```java
    public static Bitmap decodeStream(InputStream is) {
        return decodeStream(is, null, null);
    }
```

```java
    public static Bitmap decodeStream(InputStream is, Rect outPadding, Options opts) {
        ...
        try {
            if (is instanceof AssetManager.AssetInputStream) {
                final long asset = ((AssetManager.AssetInputStream) is).getNativeAsset();
                bm = nativeDecodeAsset(asset, outPadding, opts);
            } else {
                bm = decodeStreamInternal(is, outPadding, opts);
            }

            ...
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_GRAPHICS);
        }

        return bm;
    }

```

这里假设我们不是从 asset 目录中读取文件，所以会走 else 中的代码，其实读取哪里的文件都一样，反正最终都会走到 native 代码里面。

```java
    private static Bitmap decodeStreamInternal(InputStream is, Rect outPadding, Options opts) {
        ...
        return nativeDecodeStream(is, tempStorage, outPadding, opts);
    }
```

```java
    private static native Bitmap nativeDecodeStream(InputStream is, byte[] storage,
            Rect padding, Options opts);
```

从这里开始就cpp代码了，我们找到 **BitmapFactory.cpp** 文件（这里拿的是 5.1 的源码，因为我的机子上暂时也没有 cpp 代码）。

进入到 BitmapFactory.cpp 后，代码并不多，很容易就看到了 BitmapFactory.java 中的 native 方法：

```c++
static jobject nativeDecodeStream(JNIEnv* env, jobject clazz, jobject is, jbyteArray storage,
        jobject padding, jobject options) {
    //创建一个bitmap对象
    jobject bitmap = NULL;
    //创建一个输入流适配器,(SkAutoTUnref)自解引用
    SkAutoTUnref<SkStream> stream(CreateJavaInputStreamAdaptor(env, is, storage));

    if (stream.get()) {
        SkAutoTUnref<SkStreamRewindable> bufferedStream(
                SkFrontBufferedStream::Create(stream, BYTES_TO_BUFFER));
        SkASSERT(bufferedStream.get() != NULL);
        //图片解码
        bitmap = doDecode(env, bufferedStream, padding, options);
    }
    //返回图片对象,加载失败的时候返回空
    return bitmap;
}
```

```c++
static jobject nativeDecodeFileDescriptor(JNIEnv* env, jobject clazz, jobject fileDescriptor,
            jobject padding, jobject bitmapFactoryOptions) {

    ...
        
    return doDecode(env, stream, padding, bitmapFactoryOptions);
}
```

doDecode 方法比较长，我们只关系我们想要看到的代码：

```c++
/**
 * JNIEnv* env jni指针
 *   SkStreamRewindable* stream 流对象
 * jobject padding 边距对象
 * jobject options 图片选项参数对象
 */
static jobject doDecode(JNIEnv* env, SkStreamRewindable* stream, jobject padding, jobject options) {
	//缩放值,默认不缩放
    int sampleSize = 1;

    ...

    //javabitmap对象
    jobject javaBitmap = NULL;

    //对于options的参数选项初始化
    if (options != NULL) {
        //获得参数中是否需要缩放
        sampleSize = env->GetIntField(options, gOptions_sampleSizeFieldID);
        if (optionsJustBounds(env, options)) {
            //确定现在的图片解码模式
            //在java中可以设置inJustDecodeBounds参数
            //public boolean inJustDecodeBounds;true的时候,只会去加载bitmap的大小
            decodeMode = SkImageDecoder::kDecodeBounds_Mode;
        }


        ...

        // 判断是否需要缩放
        if (env->GetBooleanField(options, gOptions_scaledFieldID)) {
            const int density = env->GetIntField(options, gOptions_densityFieldID);
            const int targetDensity = env->GetIntField(options, gOptions_targetDensityFieldID);
            const int screenDensity = env->GetIntField(options, gOptions_screenDensityFieldID);
            if (density != 0 && targetDensity != 0 && density != screenDensity) {
                // 注意这里，用到了设置的 inDensity 与 inTargetDensity
                // 计算出缩放比列
                scale = (float) targetDensity / density;
            }
        }
    }

    //通过缩放比例判断是否需要缩放
    const bool willScale = scale != 1.0f;

    ...

    //缩放后的大小,decodingBitmap.width()是默认是图片大小
    int scaledWidth = decodingBitmap.width();
    int scaledHeight = decodingBitmap.height();

    //缩放
    if (willScale && decodeMode != SkImageDecoder::kDecodeBounds_Mode) {
        scaledWidth = int(scaledWidth * scale + 0.5f);
        scaledHeight = int(scaledHeight * scale + 0.5f);
    }
    
    ...

    // justBounds mode模式下,直接返回,不继续加载
    if (decodeMode == SkImageDecoder::kDecodeBounds_Mode) {
        return NULL;
    }

    ...

    //创建bitmap对象返回
    return GraphicsJNI::createBitmap(env, outputBitmap, javaAllocator.getStorageObj(),
            bitmapCreateFlags, ninePatchChunk, ninePatchInsets, -1);
}
```

我把无关的地方都给注释掉了，虽然不连贯，但是能看到我们想看到的东西。

归根结底就是我们设置的这些字段，在加载图片的时候，native代码会用来辅助计算。

说了这么多，也是一个只是储备，明白了这些东西，我们才好去研究Glide的源码，看看Glide是怎么进行图片压缩的。

### Glide的图片压缩

如果我们需要使用Glide加载一张图片到一个 `200 x 200` 的ImageView上，大致代码如下：

```java
GlideApp.with(this)
    .load(url)
    .override(200, 200)
    .into(imageView);
```

我一直觉得分析源码最好的方式，就是先使用，使用的代码就是相当好的切入点。大家都知道，当我们使用 override 方法设置了图片的目标大小之后，Glide就会帮我们自动缩放图片到目标大小。所以要分析它是如何完成缩放的，就需求看看它是如何使用我们设置的值的。

**下面会涉及到一串连续的调用，记录下来仅为以后作为参考，可以跳过**。

**GlideRequest.java**

```java
  public GlideRequest<TranscodeType> override(int width, int height) {
    if (getMutableOptions() instanceof GlideOptions) {
      this.requestOptions = ((GlideOptions) getMutableOptions()).override(width, height);
    } else {
      this.requestOptions = new GlideOptions().apply(this.requestOptions).override(width, height);
    }
    return this;
  }
```

**GlideOptions.java** -- 注解自动生成的代码

```java
  public final GlideOptions override(int width, int height) {
    return (GlideOptions) super.override(width, height);
  }
```

**RequestOptions.java**

```java
  public RequestOptions override(int width, int height) {
    if (isAutoCloneEnabled) {
      return clone().override(width, height);
    }

    this.overrideWidth = width;
    this.overrideHeight = height;
    fields |= OVERRIDE;

    return selfOrThrowIfLocked();
  }
```

嗯，可以看到RequestOptions把我们设置的值保存为了成员变量。看看有谁使用了这个类的成员变量。

**RequestBuilder.java**

```java
  private Request buildRequest(
      Target<TranscodeType> target,
      @Nullable RequestListener<TranscodeType> targetListener,
      RequestOptions requestOptions) {
    return buildRequestRecursive(
        target,
        targetListener,
        /*parentCoordinator=*/ null,
        transitionOptions,
        requestOptions.getPriority(),
        requestOptions.getOverrideWidth(),
        requestOptions.getOverrideHeight(),
        requestOptions);
  }
```

其实，是调用了 into 方法才会走到这里来，暂且不提。

接下来是同一个类中多个方法的调用，为了节约空间我省略了很多东西，请务必自己戳一下源码：

```java
private Request buildRequestRecursive(...) {

    ...

    Request mainRequest =
        buildThumbnailRequestRecursive(...);

    ...

    return errorRequestCoordinator;
  }
```

```java
  private Request buildThumbnailRequestRecursive(...) {
    if (thumbnailBuilder != null) {
      ...
      ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
      Request fullRequest =
          obtainRequest(...);
      isThumbnailBuilt = true;
      // Recursively generate thumbnail requests.
      Request thumbRequest =
          thumbnailBuilder.buildRequestRecursive(...);
      isThumbnailBuilt = false;
      coordinator.setRequests(fullRequest, thumbRequest);
      return coordinator;
    } else if (thumbSizeMultiplier != null) {
      // Base case: thumbnail multiplier generates a thumbnail request, but cannot recurse.
      ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
      Request fullRequest =
          obtainRequest(...);
      RequestOptions thumbnailOptions = requestOptions.clone()
          .sizeMultiplier(thumbSizeMultiplier);

      Request thumbnailRequest =
          obtainRequest(...);

      coordinator.setRequests(fullRequest, thumbnailRequest);
      return coordinator;
    } else {
      // Base case: no thumbnail.
      return obtainRequest(...);
    }
  }
```

发现每个分析里面都有 obtainRequest 方法，获取一个请求，又想到面向对象会将请求抽象为 Request 类。

可以看出一个 coordinator 协调了两个 request。coordinator 不是我们的目标，request 才是本体。

```java
  private Request obtainRequest(...) {
    return SingleRequest.obtain(...);
  }
```

啊，终于到了尽头，发现了一个 SingleRequest 类。

**SingleRequest.java**

```java
public static <R> SingleRequest<R> obtain(
      ...
      int overrideWidth,
      int overrideHeight,
      ...) {
    @SuppressWarnings("unchecked") SingleRequest<R> request =
        (SingleRequest<R>) POOL.acquire();
    if (request == null) {
      request = new SingleRequest<>();
    }
    request.init(
        ...
        overrideWidth,
        overrideHeight,
        ...);
    return request;
  }
```

```java
private void init(
      ...
      int overrideWidth,
      int overrideHeight,
      ...) {
    ...
    this.overrideWidth = overrideWidth;
    this.overrideHeight = overrideHeight;
    ...
  }
```

所以，也是将设置的值放到了成员变量中。看看它的变量又被谁使用了。

**SingleRequest.java**

```java
  @Override
  public void begin() {
    ...
    if (model == null) {
      if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        width = overrideWidth;
        height = overrideHeight;
      }
      ...
      return;
    }

    ...
  }
```

在 begin 方法中，将值赋值给了 width 与 height，为什么这样做暂时不深究。看看哪里用到了这两个变量。

**SingleRequest.java**

```java
  public void onSizeReady(int width, int height) {
    ...

    float sizeMultiplier = requestOptions.getSizeMultiplier();
    this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
    this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

    if (IS_VERBOSE_LOGGABLE) {
      logV("finished setup for calling load in " + LogTime.getElapsedMillis(startTime));
    }
    loadStatus = engine.load(
        ...
        this.width,
        this.height,
        ...);

    ...
  }
```

提一下，这里计算了 sizeMultiplier，也是我们在设置的，如果没有设置，maybeApplySizeMultiplier 这个方法就不起作用。

这里还有一个可以扩展的地方，就是 onSizeReady 方法什么时候调用，当我们不设置 override 的时候，Glide是怎么拿到 ImageView 的大小的？

这里我们看到了 engine，还是非常重要的一个类。

**Engine.java**

```java
  public <R> LoadStatus load(...) {
    ...

    DecodeJob<R> decodeJob =
        decodeJobFactory.build(
            ...
            width,
            height,
            ...);

    ...
    return new LoadStatus(cb, engineJob);
  }

```

**Engine.DecodeJobFactory.java**

```java
  static class DecodeJobFactory {
    ...

    <R> DecodeJob<R> build(...
        int width,
        int height,
        ...) {
      DecodeJob<R> result = Preconditions.checkNotNull((DecodeJob<R>) pool.acquire());
      return result.init(
          ...
          width,
          height,
          ...);
    }
  }
```

**DecodeJob.java**

```java
  DecodeJob<R> init(
      ...
      int width,
      int height,
      ...) {
    decodeHelper.init(
        ...
        width,
        height,
        ...);
    ...
    this.width = width;
    this.height = height;
    ...
    return this;
  }

```

这里 DecodeHepler 储存了一份，DecodeJob 也储存了一份。

**DecodeHelper.java**

```java
  <R> void init(
      ...
      int width,
      int height,
      ...) {
    ...
    this.width = width;
    this.height = height;
    ...
  }

```

DecodeJob一看就是用来解码图片的类，DecodeHelper是一个辅助类，所以选择 DecodeJob 分支继续跟踪。

点击变量，查看引用的地方，不多，但是需要仔细查看，有个 onResourceDecoded 方法里面也用到了，但是看这个名字就知道，已经解码完成了，所以不必理会。

**DecodeJob.java**

```java
  private <Data, ResourceType> Resource<R> runLoadPath(Data data, DataSource dataSource,
      LoadPath<Data, ResourceType, R> path) throws GlideException {
    ...
    try {
      // ResourceType in DecodeCallback below is required for compilation to work with gradle.
      return path.load(
          rewinder, options, width, height, new DecodeCallback<ResourceType>(dataSource));
    } finally {
      rewinder.cleanup();
    }
  }
```

**LoadPath.java**

```java
  public Resource<Transcode> load(DataRewinder<Data> rewinder, @NonNull Options options, int width,
      int height, DecodePath.DecodeCallback<ResourceType> decodeCallback) throws GlideException {
    ...
    try {
      return loadWithExceptionList(rewinder, options, width, height, decodeCallback, throwables);
    } finally {
      ...
    }
  }
```

```java
  private Resource<Transcode> loadWithExceptionList(DataRewinder<Data> rewinder,
      @NonNull Options options,
      int width, int height, DecodePath.DecodeCallback<ResourceType> decodeCallback,
      List<Throwable> exceptions) throws GlideException {
    ...
    for (int i = 0, size = decodePaths.size(); i < size; i++) {
      ...
      try {
        result = path.decode(rewinder, width, height, options, decodeCallback);
      } catch (GlideException e) {
        ...
      }
      ...
    }

    ...

    return result;
  }
```

注意这里的 path 是一个 DecodePath 变量。

**DecodePath.java**

```java
  public Resource<Transcode> decode(DataRewinder<DataType> rewinder, int width, int height,
      @NonNull Options options, DecodeCallback<ResourceType> callback) throws GlideException {
    Resource<ResourceType> decoded = decodeResource(rewinder, width, height, options);
    ...
    return transcoder.transcode(transformed, options);
  }
```

```java
  private Resource<ResourceType> decodeResource(DataRewinder<DataType> rewinder, int width,
      int height, @NonNull Options options) throws GlideException {
    ...
    try {
      return decodeResourceWithList(rewinder, width, height, options, exceptions);
    } finally {
      ...
    }
  }
```

```java
  private Resource<ResourceType> decodeResourceWithList(DataRewinder<DataType> rewinder, int width,
      int height, @NonNull Options options, List<Throwable> exceptions) throws GlideException {
    ...
    for (int i = 0, size = decoders.size(); i < size; i++) {
      ...
      try {
        ...
        if (decoder.handles(data, options)) {
          ...
          result = decoder.decode(data, width, height, options);
        }
        // Some decoders throw unexpectedly. If they do, we shouldn't fail the entire load path, but
        // instead log and continue. See #2406 for an example.
      } catch (IOException | RuntimeException | OutOfMemoryError e) {
        ...
      }

      ...
    }

    ...
    return result;
  }

```

这里的 decoder 是一个接口，所以戳进去啥都没有。

**ResourceDecoder.java**

```java
  Resource<Z> decode(@NonNull T source, int width, int height, @NonNull Options options)
      throws IOException;
```

到了这里你就需要戳以下旁边的绿色小圆圈（有个I字的），然后它就会显示一大串实现类，你需要选一个。我选择了 StreamBitmapDecoder，看它的类注释：

**StreamBitmapDecoder.java**

```java
/**
 * Decodes {@link android.graphics.Bitmap Bitmaps} from {@link java.io.InputStream InputStreams}.
 */
```

完美！！！

查看它的 decode 方法：

**StreamBitmapDecoder.java**

```java
  public Resource<Bitmap> decode(@NonNull InputStream source, int width, int height,
      @NonNull Options options)
      throws IOException {

    ...
    try {
      return downsampler.decode(invalidatingStream, width, height, options, callbacks);
    } finally {
      ...
    }
  }
```

唉，删掉了很多细节，很多注释也能学到意想不到的东西。

**Downsampler.java**

```java
  public Resource<Bitmap> decode(InputStream is, int requestedWidth, int requestedHeight,
      Options options, DecodeCallbacks callbacks) throws IOException {
    ...
    try {
      Bitmap result = decodeFromWrappedStreams(is, bitmapFactoryOptions,
          downsampleStrategy, decodeFormat, isHardwareConfigAllowed, requestedWidth,
          requestedHeight, fixBitmapToRequestedDimensions, callbacks);
      return BitmapResource.obtain(result, bitmapPool);
    } finally {
      ...
    }
  }

```

参数名变成了 requestedWidth 与 requestedHeight，嗯，好名字。

```java
  private Bitmap decodeFromWrappedStreams(InputStream is,
      BitmapFactory.Options options, DownsampleStrategy downsampleStrategy,
      DecodeFormat decodeFormat, boolean isHardwareConfigAllowed, int requestedWidth,
      int requestedHeight, boolean fixBitmapToRequestedDimensions,
      DecodeCallbacks callbacks) throws IOException {
      
    ...
        
    // source 就是原图片的宽高
    // getDimensions 就是我们常写的 decodeStream，把 options.inJustDecodeBounds 设置为 true
    int[] sourceDimensions = getDimensions(is, options, callbacks, bitmapPool);
    int sourceWidth = sourceDimensions[0];
    int sourceHeight = sourceDimensions[1];
      
    ...
        
        
    // 将 request 的宽高赋值给 target 的宽高    
    int targetWidth = requestedWidth == Target.SIZE_ORIGINAL ? sourceWidth : requestedWidth;
    int targetHeight = requestedHeight == Target.SIZE_ORIGINAL ? sourceHeight : requestedHeight;

    ImageType imageType = ImageHeaderParserUtils.getType(parsers, is, byteArrayPool);

    calculateScaling(
        imageType,
        is,
        callbacks,
        bitmapPool,
        downsampleStrategy,
        degreesToRotate,
        sourceWidth,
        sourceHeight,
        targetWidth,
        targetHeight,
        options);

    ...

    return rotated;
  }

```

上面的代码中，将图片方向与角度的处理省略了，将图片的复用也省略了，感兴趣的可以自行查看。

最主要的就是这个 calculateScaling 方法，它用来计算如何缩放图片，将对应的字段设置到 options 里面。

```java
  private static void calculateScaling(
      ImageType imageType,
      InputStream is,
      DecodeCallbacks decodeCallbacks,
      BitmapPool bitmapPool,
      DownsampleStrategy downsampleStrategy,
      int degreesToRotate,
      int sourceWidth,
      int sourceHeight,
      int targetWidth,
      int targetHeight,
      BitmapFactory.Options options) throws IOException {
    ...

    final float exactScaleFactor;
    // 假设我们的图是正的，走else的分支
    if (degreesToRotate == 90 || degreesToRotate == 270) {
      ...
    } else {
      // 这里是根据 scaleType 获取一个缩放因子
      // 为了简化理解，我们假设设置的 DownsampleStrategy 是 Nono，所以返回 1f.
      // 可以自己查看其他类型返回的值
      exactScaleFactor =
          downsampleStrategy.getScaleFactor(sourceWidth, sourceHeight, targetWidth, targetHeight);
    }

    ...
        
    // 这是一个枚举类型，只有两个值，别以为你类名长就会吓到我！！！
    SampleSizeRounding rounding = downsampleStrategy.getSampleSizeRounding(sourceWidth,
        sourceHeight, targetWidth, targetHeight);
    ...

    int outWidth = round(exactScaleFactor * sourceWidth);
    int outHeight = round(exactScaleFactor * sourceHeight);

    // 计算缩放因子
    // 感觉只是修正了一下 exactScaleFactor 的值
    // 如果 DownsampleStrategy 是 Nono 的话，这个两个值都是 1。
    int widthScaleFactor = sourceWidth / outWidth;
    int heightScaleFactor = sourceHeight / outHeight;

    // 如果是内存模式，那么我们取比较大的值，占用的内存小
    int scaleFactor = rounding == SampleSizeRounding.MEMORY
        ? Math.max(widthScaleFactor, heightScaleFactor)
        : Math.min(widthScaleFactor, heightScaleFactor);

    int powerOfTwoSampleSize;
    // BitmapFactory does not support downsampling wbmp files on platforms <= M. See b/27305903.
    if (Build.VERSION.SDK_INT <= 23
        && NO_DOWNSAMPLE_PRE_N_MIME_TYPES.contains(options.outMimeType)) {
      powerOfTwoSampleSize = 1;
    } else {
      // 根据缩放因子来计算 inSampleSize 的值
      // 因为我选取了一个最简单的 Nono，所以 inSampleSize 的值是 1
      // 有兴趣的可以取看看别的模式，如 FitCenter
      // 注意这个算法很有趣
      powerOfTwoSampleSize = Math.max(1, Integer.highestOneBit(scaleFactor));
      if (rounding == SampleSizeRounding.MEMORY
          && powerOfTwoSampleSize < (1.f / exactScaleFactor)) {
        powerOfTwoSampleSize = powerOfTwoSampleSize << 1;
      }
    }

    // Here we mimic framework logic for determining how inSampleSize division is rounded on various
    // versions of Android. The logic here has been tested on emulators for Android versions 15-26.
    // PNG - Always uses floor
    // JPEG - Always uses ceiling
    // Webp - Prior to N, always uses floor. At and after N, always uses round.
    options.inSampleSize = powerOfTwoSampleSize;
    ...

    double adjustedScaleFactor = downsampleStrategy.getScaleFactor(
        powerOfTwoWidth, powerOfTwoHeight, targetWidth, targetHeight);

    // Density scaling is only supported if inBitmap is null prior to KitKat. Avoid setting
    // densities here so we calculate the final Bitmap size correctly.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // 这里我们终于找到了想要的代码，不过这两个方法有点蛋疼
      options.inTargetDensity = adjustTargetDensityForError(adjustedScaleFactor);
      options.inDensity = getDensityMultiplier(adjustedScaleFactor);
    }
    if (isScaling(options)) {
      options.inScaled = true;
    } else {
      options.inDensity = options.inTargetDensity = 0;
    }

    ...
  }
```

计算 options.inTargetDensity  与 options.inDensity 的值的时候，都用上了误差修正，额，我还没看懂，但是我用数学公式带入进去的时候， options.inTargetDensity  / options.inDensity = adjustedScaleFactor。

最终的实现，与之前是差不多的，只不过它用了缩放因子来计算，然后乘以`Integer.MAX_VALUE`来减少误差。



### 为什么这3个参数要结合使用，而不是直接使用 inDensity 与 inTargetDensity

下面的代码基于 Android 9.0 分析而来，刚接触 JNI，很多东西不明白，强行分析可能有误，看看就好。

**static jobject doDecode(JNIEnv* env, std::unique_ptr<SkStreamRewindable> stream, jobject padding, jobject options)**

```c++
        if (env->GetBooleanField(options, gOptions_scaledFieldID)) {
            const int density = env->GetIntField(options, gOptions_densityFieldID);
            const int targetDensity = env->GetIntField(options, gOptions_targetDensityFieldID);
            const int screenDensity = env->GetIntField(options, gOptions_screenDensityFieldID);
            if (density != 0 && targetDensity != 0 && density != screenDensity) {
                scale = (float) targetDensity / density;
            }
        }
```

doDecode 方法中只有这一段用到了 inDensity 与 inTargetDensity。用它们计算出了一个结果 scale。直接搜索哪里用到了这个值。

```c++
    if (scale != 1.0f) {
        willScale = true;
        scaledWidth = static_cast<int>(scaledWidth * scale + 0.5f);
        scaledHeight = static_cast<int>(scaledHeight * scale + 0.5f);
    }
```

对 scaledWidth 与 scaledHeight 进行缩放。

看看哪里用到了这个两个值。

```c++
    const float scaleX = scaledWidth / float(decodingBitmap.width());
    const float scaleY = scaledHeight / float(decodingBitmap.height());
```

看看哪里用到了 scaleX 与 scaleY。

```c++
        SkCanvas canvas(outputBitmap, SkCanvas::ColorBehavior::kLegacy);
        canvas.scale(scaleX, scaleY);
        canvas.drawBitmap(decodingBitmap, 0.0f, 0.0f, &paint);
```

这里有点意思，将 decodingBitmap 缩放了 scaleX，scaleY，然后画到 outputBitmap 上了。

看看 decodingBitmap 是怎么创建的。

```c++
    SkBitmap decodingBitmap;
    if (!decodingBitmap.setInfo(bitmapInfo) ||
            !decodingBitmap.tryAllocPixels(decodeAllocator)) {
        // SkAndroidCodec should recommend a valid SkImageInfo, so setInfo()
        // should only only fail if the calculated value for rowBytes is too
        // large.
        // tryAllocPixels() can fail due to OOM on the Java heap, OOM on the
        // native heap, or the recycled javaBitmap being too small to reuse.
        return nullptr;
    }
```

这里只有一个声明，真是奇怪，不太明白。

```c++
    codecOptions.fSampleSize = sampleSize;
    SkCodec::Result result = codec->getAndroidPixels(decodeInfo, decodingBitmap.getPixels(),
            decodingBitmap.rowBytes(), &codecOptions);
```

嗯，我没搜索到哪里创建的这个对象，但是发现了这个一个东西，应该是用 codecOptions 取获取图片的像素。注意这里只使用了 sampleSize。

给我的感觉是 Skia 使用 inSampleSize 的值去获取像素，然后将这个 Bitmap 的画布缩放 inTargetDensity / inDensity 倍，就得到了需要的大小。

嗯，《Android高性能编程》说 inSampleSize 比较快，所以需要两者结合，好像是那么回事。



### 参考文档

https://blog.csdn.net/ynztlxdeai/article/details/69956262

https://cloud.tencent.com/developer/article/1006307

https://cloud.tencent.com/developer/article/1006352

https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r21/core/jni/android/graphics/BitmapFactory.cpp