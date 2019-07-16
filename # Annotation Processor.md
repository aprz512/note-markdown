# Annotation Processor

### 创建一个注解处理器



#### 新建两个module

一个用于创建注解，一个用于创建注解处理器。

为啥需要新建两个module呢？

因为 processor 需要 annatation 的引用，所以 annotation 需要提出来作为一个 module。

那么可不可以将所有代码都放到 app 里面呢？是可以的，但是由于我们不需要 processor 的代码，只需要它在编译的时候处理我们的代码然后生成新的文件就好了，更不就不需要将  processor 的代码打包到 apk 里面，所以新建 module 是最好的选择。

![](F:\note-markdown\Annotation Processor\1_4JbENCbEx_g2KNvBLWdAeg.png)

piri-pricessor 的 build.gradle 需要配置一下：

```groovy
implementation project(':piri-annatation')
```



app 的 build.gradle 需要配置一下：

```groovy
implementation project(':piri-annatation')
annotationProcessor project(':piri-processor')
```



#### 创建注解

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface NewIntent {
}
```

ElementType.TYPE 表示这个注解可以修饰 类，接口，枚举 等等。



#### 创建注解处理器

自定义的注解处理器需要继承至一个指定的父类（`AbstractProcessor `）：

```java
public class NewIntentProcessor extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {}

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {}

    @Override
    public SourceVersion getSupportedSourceVersion() {}
}
```



#### 开始处理注解

##### 首先找到所有的被指定注解修饰元素

```java
for (Element element : roundEnvironment.getElementsAnnotatedWith(NewIntent.class)) {

    if (element.getKind() != ElementKind.CLASS) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Can be applied to class.");
        return true;
    }

    TypeElement typeElement = (TypeElement) element;
    activitiesWithPackage.put(
        typeElement.getSimpleName().toString(),
        elements.getPackageOf(typeElement).getQualifiedName().toString());
}
```

我们利用 `roundEnvironment.getElementsAnnotatedWith()` 这个方法就可以找出所以被指定注解修饰的元素，这个方法返回了一个集合，集合类型是 Element，Element 是所有元素的一个父接口。

然后我们判断一下，注解是否被正确使用了，因为我们在创建注解的时候就指定了该注解只能修饰类，接口，枚举...

如果注解被错误使用了，我们可以使用 message 打印错误信息，反之，被正确使用了，那么我们就可以将它强制转换为 TypeElement。关于这个 TypeElement ，它是 Element 的一个子接口。它通常可以用于类和方法参数。还有一些其他类型的元素：

```java
package com.example;	// PackageElement

public class Foo {		// TypeElement

	private int a;		// VariableElement
	private Foo other; 	// VariableElement

	public Foo () {} 	// ExecuteableElement

	public void setA ( 	// ExecuteableElement
	                 int newA	// TypeElement
	                 ) {}
}
```

之所以要强制转换成 TypeElement，是因为转换之后，我们可以获取到更多的信息。



##### 生成代码

```java
TypeSpec.Builder navigatorClass = TypeSpec
                    .classBuilder("Navigator")
    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

for (Map.Entry<String, String> element : activitiesWithPackage.entrySet()) {
    String activityName = element.getKey();
    String packageName = element.getValue();
    ClassName activityClass = ClassName.get(packageName, activityName);
    MethodSpec intentMethod = MethodSpec
        .methodBuilder(METHOD_PREFIX + activityName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(classIntent)
        .addParameter(classContext, "context")
        .addStatement("return new $T($L, $L)", classIntent, "context", activityClass + ".class")
        .build();
    navigatorClass.addMethod(intentMethod);
}
```

这个是 JavaPoet 的使用方法，就不多说了，可以查看[ 文档](https://github.com/square/javapoet) 。



##### 最后，将代码写入文件

```java
JavaFile.builder("com.annotationsample", navigatorClass.build())
  .build()
  .writeTo(filer);
```

生成的文件大概内容如下：

```java
public final class Navigator {
  public static Intent startMainActivity(Context context) {
    return new Intent(context, com.annotationsample.MainActivity.class);
  }
}
```

然后，我们就可以在代码中使用生成的代码了：

```java
@NewIntent
public class MainActivity extends AppCompatActivity {}

----------------------------------------------------------------------------------------

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Navigator.startMainActivity(this); //generated class, method
    }
}
```



#### 实例工程

坑

1. 创建 java module
2. 没有自动生成文件
3. 与gradle版本有关系