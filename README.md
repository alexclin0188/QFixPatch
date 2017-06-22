# QFixPatch

项目fork自[QFix](https://github.com/lizhangqu/QFix)

原项目只是[QFix补丁方案](http://dev.qq.com/topic/57ff5832bb8fec206ce2185d)的一个简单示例demo，实际应用到项目中还是有不少工作要做，且原项目是Eclipse ant构建的，而实际应用中公司的项目是gradle/AS项目。

此项目即是按照[QFix](https://github.com/lizhangqu/QFix)方案和demo将补丁的构建生成过程利用gradle插件进行自动化处理，简化实际应用到项目时的接入流程和生成补丁流程。

和[QFix补丁方案](http://dev.qq.com/topic/57ff5832bb8fec206ce2185d)不同之处时，手Q原方案是在每个dex中预留一个空类作为加载补丁类的入口类，而此项目是使用asm分析每个dex找到第一个不会调用补丁中类的类作为加载入口类。

另外参考Tinker对在art模式下可能遇到一些问题增加了处理，增加strictMode，开启时打包时会把修改过的类以及所有调用了修改过的类的类class都打包到补丁中，解决高版本下[ART下dex激进内联问题](http://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=2649286426&idx=1&sn=eb75349c0c3663f10fbdd74ef87be338&chksm=8334c398b4434a8e6933ddb4fda4a4f06c729c7d2ffef37e4598cb90f4602f5310486b7f95ff&mpshare=1&scene=1&srcid=12018kiBIseVYptcp6BmhZmk#rd)

## 使用说明

* 接入QFixPatch

在项目根目录的build.gradle中

```
   ........
   
	buildscript {
	     repositories {
	        jcenter()
	     }
	     dependencies {
	        classpath 'com.android.tools.build:gradle:2.2.3'
	
	        classpath 'alexclin.qfix:qfix-gradle:1.0.0'
	     }
   }
   
   ........
```

在app模块的build.gradle中

```
   .......
   apply plugin: 'alexclin.qfix'
    
   qfix{
	    debugOn true //debug模式是否开启补丁构建task,可选
	    outputDir 'qfix_output'//补丁基础信息默认输入目录和构建输出目录，必须
	    excludeClass = ["App.class"]//需要排除的类，会自动排除Application及其父类，此处只是示例，可选
	    //includePackage = []//需要包含的package，可选
	    strictMode true //严格模式，用于解决ART下dex激进内联问题，
	}
   .......
   dependencies {
       ......
       compile 'alexclin.qfix:qfixlib:1.0.0'
       ......
   }
```

* 打包发布版本

```
//构建发布包apk并保存对应的基础信息
./gradlew clean buildBase
```

```
//使用当前代码与输出目录下的最新的基础信息对比构建生成补丁
./gradlew clean buildPatch
```

```
//使用当前代码与指定目录的基础信息对比构建生成补丁
./gradlew clean buildPatch -P PatchBase=/xxxx/xx/xxx/
```

* 命令说明

```
./gradlew clean buildBase //相当于同时执行assembleDebugBase和assembleReleaseBase

./gradlew clean assembleDebugBase //执行assembleDebug的同时保存基础debug apk信息

./gradlew clean assembleReleaseBase //执行assembleRelease的同时保存release apk基础信息

./gradlew clean buildPatch //相当于同时执行assembleDebugPatch + assembleReleasePatch

./gradlew clean assembleDebugPatch //构建debug补丁

./gradlew clean assembleReleasePatch //构建release补丁
```