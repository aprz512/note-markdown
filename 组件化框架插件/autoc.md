### 目的
- 该插件基于三方开源库编写而来，主要用于简化组件化工程的框架搭建。
- 该插件不包含组件之间的通信方案，目前组件通信方案有多种选择，该插件不会与任何通讯方案冲突，可以自由组合。



### 优点
1. 避免组件在Application与Library之间切换导致的重复编译。

   ![](F:/note-markdown/%E7%BB%84%E4%BB%B6%E5%8C%96%E6%A1%86%E6%9E%B6%E6%8F%92%E4%BB%B6/1.PNG)

   在上图中，app是主工程，componenta是组件。点击comonenta或者app均可以单独运行。

   componenta是组件。点击comonenta或者app均可以单独运行。

   但是与普通项目不同的是**点击app运行的时候，会将componenta当作一个library打到app里面去**，此时的componenta会被当作依赖库运行，与在app的build.gradle文件中添加 

   ```groovy
      implementation project(':componenta')
   ```

   的作用是一样的。

   组件可以单独运行，运行App又可以将其他组件打包进来。




2. **统一debug文件的位置**，一目了然，如下：

   <img src="F:/note-markdown/%E7%BB%84%E4%BB%B6%E5%8C%96%E6%A1%86%E6%9E%B6%E6%8F%92%E4%BB%B6/2.PNG" style="zoom:50%" />

   以后就不用在src下又维护一个debug目录。



3. 避免每个组件都要配置sourceSet，**使用该插件之后，build.gradle中不用设置任何额外的东西，插件会自动管理**。

   以前需要在每个组件中维护一份自己的 sourceSet：

   ```groovy
   sourceSets {
           main {
               if (rootProject.ext.isComponent) {
                   manifest.srcFile 'src/main/debug/AndroidManifest.xml'
                   java.srcDirs = ['src/debug/java', 'src/main/java']
                   res.srcDirs = ['src/debug/res', 'src/main/res']
               } else {
                   manifest.srcFile 'src/main/AndroidManifest.xml'
                   //release 时 debug 目录下文件不需要合并到主工程
                   java {
                       exclude 'src/debug/**'
                   }
                   res {
                       exclude 'src/debug/**'
                   }
               }
           }
       }
   ```

   但是现在不用了，不用对build.gradle文件做任何的修改。

4. **严格的代码隔离，主工程编译期间无法访问组件代码**。避免在主工程中直接使用组件中的类，为以后的组件分离埋下bug。

5. 清晰的输出日志，可以看到工程编译时插件的工作情况。



### 快速使用

- 新建一个项目

- 在Project的build.gradle中配置：

    ```gradle
    
        repositories {
            maven {
                url  "https://dl.bintray.com/aprz512/gradle_plugins" 
            }
        }
    
        dependencies {
            classpath 'com.aprz.module.manager:autoc:1.5.0'
        }
    
    ```

- 在主工程的build.gradle中配置：
    ```gradle
    ext.mainApp = true
    apply plugin: 'aprz.manager'
    
    // 删除下面的语句，已经不需要了，插件会自动判断是否应该添加
    // apply plugin: 'com.android.application'
    
    // 添加组件使用如下语句：addComponent '组件名'
    // 注意这行代码非常重要，否则无法识别组件的debug目录，就无法新建并且识别java文件
    addComponent 'componenta'
    
    ```

- 新建一个library

- 在library的build.gradle中配置：

    ```gradle
    apply plugin: 'aprz.manager'
    // 删除下面的语句，已经不需要了，插件会自动判断是否应该添加
    // apply plugin: 'com.android.library'
    ```

- 建立debug目录，注意配置Application与Mainfest文件

- 如果有需要，在local.properties中设置

    ```cmd
    // 表示当前组件不参与打包
    componenta = true
    ```



现在组件化工程就已经搭建好了，当然你还需要选择组件通信框架。



### 注意事项

- 该插件基于gradle4.6编写，使用时注意gradle版本不得小于4.6
- 注意组件的前缀设置，建议所有的module都加上前缀，不管是否组件（注意前缀不是严格要求完全一致，比如前缀声明为`ca_`，使用可以可以用 `c_a_`或者`CA`，灵活使用，避免风格看着奇怪）
- 注意去掉组件的applicationId，默认会使用包名，否则编译主工程时会报异常。