# 理解 Lambda 表达式



## Comparator.comparing();

有一个类 N 定义如下：

```java
public class N {

    private int n;

    public N(int n) {
        this.n = n;
    }

    public int getN() {
        return n;
    }


    public void setN(int n) {
        this.n = n;
    }

}
```

有这样一个需求，将许多 N 的实例对象，放入 HashSet 中。

如果我们想指定自定义的排序方式(按照n的值排序)，可以使用接口的默认实现方法：

```java
    public static <T, U extends Comparable<? super U>> Comparator<T> comparing(
            Function<? super T, ? extends U> keyExtractor)
    {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> keyExtractor.apply(c1).compareTo(keyExtractor.apply(c2));
    }
```

调用如下：

```java
Comparator.comparing(N::getN);
```

当时看到这样的使用方式的时候我是比较懵逼的，经过了自己的探索，我还原了中间的变化过程。

首先，将这个方法，拷出来，把它还原成匿名内部类的写法：

```java
    public static <T, U extends Comparable<? super U>> Comparator<T> comparing(
            Function<? super T, ? extends U> keyExtractor)
    {
        Objects.requireNonNull(keyExtractor);
        return new Comparator<T>() {
            @Override
            public int compare(T c1, T c2) {
                return keyExtractor.apply(c1).compareTo(keyExtractor.apply(c2));
            }
        };
    }
```

这下就清晰了很多，然后使用这个函数，传递一个匿名类进去：

```java
comparing(new Function<N, Integer>() {
    @Override
    public Integer apply(N n) {
        return n.getN();
    }
});
```

所以，apply 方法其实是调用了我们自己写的方法。

编译器提示可以转成 lambda 表达式：

```java
comparing((Function<N, Integer>) n -> n.getN());
```

再次提示，可以使用方法引用：

```java
comparing((Function<N, Integer>) N::getN);
```

继续提示，编译器可以自己进行类型推断：

```java
comparing(N::getN);
```

大功告成！！！