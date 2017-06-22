# Android热修复:Qfix方案的gradle实践

## 一、Android热修复方案的发展

Android热修复技术现在主流分为NativeHook,ClassLoader以及新出现的Instant-Run方案，在工作中因为团队需要为团队引入了[QZone的ClassLoader插桩方案](https://zhuanlan.zhihu.com/p/20308548?columnSlug=magilu),当初开发的时候(15年底)市面上还只有AndFix和ClassLoader两种方案，而那时的AndFix兼容性也还比较差，就选择了ClassLoader方案，基于网上开源的Nuwa方案修改而来。

2016年的时候Android热修复方案如雨后春笋，ClassLoader方案新起之秀有Tinker,QFix等。对比几种新出的方案，调研之后认为QFix与团队之前使用的ClassLoader方案最为接近，同时无需插桩，避免了插桩性能损失问题，在16年下半年时候将团队的热修复方案升级到了QFix方案。

由于团队的热修复方案需要对原来的方案做不少兼容，所以会有不少雍余的代码。17年年初就想着自己写个QFix的开源gradle实现，现项目已完成发布到Github,地址：[https://github.com/alexclin0188/QFixPatch](https://github.com/alexclin0188/QFixPatch)。 这篇文章主要是对开发过程的一个记录，发出来供有兴趣的同学参考和自己以后复习使用。

## 二、QFix方案原理

[QFix方案原理介绍原文](http://mp.weixin.qq.com/s?__biz=MzA3NTYzODYzMg==&mid=2653577964&idx=1&sn=bac5c8883b7aaaf7d7d9ea227f200412&chksm=84b3b0ebb3c439fd56a502a27e1adc18f600b875718e537191ef109e2d18dae1c52e5e36f2d9&mpshare=1&scene=1&srcid=1013Ii2YbutUPmTXVJjMfYwf#rd)。有兴趣的同学可以仔细研读下这篇原理介绍，这里就不再详细讲解，补丁的基本原来和ClassLoader类同，反射在DexPahList类中dex数组前插入补丁dex，区别在于QFix使用在native层调用dvmResolveClass方法来解决pre-verify的问题。

GitHub上有一个开源的简单的QFix方案Eclipse工程demo:[https://github.com/lizhangqu/QFix](https://github.com/lizhangqu/QFix), QFixPatch项目即是基于这个demo扩展而来的一个gradle实践。

## 三、Gradle插件开发

gradle插件生成补丁逻辑和Nuwa类似，只是增加了dexdump分析补丁类class-id的操作，简单的流程示意图如下：

![image](https://github.com/alexclin0188/QFixPatch/blob/master/qfix_gradle_proto.png)

首先是buildBase，构建基础apk和保存基础信息，在dexTask之前增加hook，将原始apk中的类的sha值保存到hash.txt中，以便生成补丁时使用。然后在buildPatch执行时比较找出修改的类(补丁类)，并通过dexdump分析这些补丁类在基础apk的dex中的class-id，将分析得到的class-id信息和补丁类通过dex工具打包成一个补丁apk。

### 3.1 创建插件工程

在一个AS工程内，如果有buildSrc目录，这个目录就会被当做gradle插件源码目录编译，并可以直接在工程模块中直接使用编写的gradle插件，下面是QFixPatch工程gradle插件源码目录截图

![image](https://github.com/alexclin0188/QFixPatch/blob/qfix-gradle-source-code.png)


其中特殊的是resources目录，下面有个META-INF.gradle-plugins文件夹，内有propertites文件，而这个文件是用来注册插件入口类的，下面是alexclin.qfix.properties的内容

```
//alexclin.qfix.properties文件
implementation-class=alexclin.qfix.QFixPlugin
```

我们gradle插件的使用方式是在模块的build.gradle中增加apply，如下

```
//build.gradle文件

......
apply plugin: 'alexclin.qfix'
......

```

而这里的apply plugin的作用最终就是让项目在执行gradle编译时调用我们的实现类alexclin.qfix.QFixPlugin的apply函数，下面让我们看QFixPlugin类的代码

```
//QFixPlugin类
public class QFixPlugin implements Plugin<Project> {
    static final String CLASS_ID_TXT = "class-ids.txt"

    static final String PATCH_NAME = "patch"
    static final String DEBUG = "debug"
    static final String PLUGIN_NAME = "qfix"

    def debugOn

    @Override
    void apply(Project project) {
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            project.extensions.create(PLUGIN_NAME, QFixExtension, project)
            applyPlugin(project)
        }
    }

    //真正的applyplugin逻辑代码
    private void applyPlugin(Project project) {
        project.afterEvaluate {
            def extension = project.extensions.findByName(PLUGIN_NAME) as QFixExtension

            Creator creator = new Creator(project, extension);
            debugOn = extension.debugOn

            project.android.applicationVariants.each { variant ->

                if (variant.name.contains(DEBUG) && !debugOn)
                    return;
                configTasks(project, variant, creator);
            }

            //添加总的buildPatch Task
            def releasePatch = project.tasks.findByName("assembleReleasePatch")
            def debugPatch = project.tasks.findByName("assembleDebugPatch")
            if (releasePatch != null || debugPatch != null) {
                def buildPatchTask = project.task("buildPatch")
                if (debugPatch) buildPatchTask.dependsOn debugPatch
                if (releasePatch) buildPatchTask.dependsOn releasePatch
            }

            def assembleDebugBase = project.tasks.findByName("assembleDebugBase")
            def assembleReleaseBase = project.tasks.findByName("assembleReleaseBase")
            if (assembleDebugBase != null || assembleReleaseBase != null) {
                def assembleBase = project.task("buildBase");
                if (assembleDebugBase) assembleBase.dependsOn assembleDebugBase
                if (assembleReleaseBase) assembleBase.dependsOn assembleReleaseBase
            }
        }
    }
    
    ....
 }
```

可以看到上面这段代码里面，主要是在真正的applyplugin函数对项目进行了Task设置，并对buildBase,buildPatch,assembleDebugBase,assembleDebugPacth,assembleReleaseBase,assembleReleasePacth的依赖关系做了设置。

实际上只有assemble\<Viriant\>Base,assemble\<Viriant\>Patch的区别,前者是构建基础宝和保存基础信息，后者是构建补丁

### 3.2 插件设置QFixExtension

gradle插件支持属性设置的，如我们的QFix插件在build.gradle中属性设置，如下

```
//qfix插件在app模块build.gradle中的设置
qfix{  
    debugOn true //debug模式是否开启补丁构建task,可选
    outputDir 'qfix_output'//补丁基础信息默认输入目录和构建输出目录，必须
    excludeClass = ["App.class"]//需要排除的类，会自动排除Application及其父类，此处只是示例，可选
    //includePackage = []//需要包含的package，可选
    strictMode true //严格模式，用于解决ART下dex激进内联问题，详情参看README,可选
}
```

而上面这个设置实际对应的是一个Extension类，在qfix-gradle插件中对应的就是QFixExtension类

```
package alexclin.qfix

import org.gradle.api.Project

class QFixExtension {
    HashSet<String> includePackage = []
    HashSet<String> excludeClass = []
    //补丁构建在debug版是否开启，默认开启
    boolean debugOn = false
    //补丁基础信息保存目录和补丁输出目录
    String outputDir
    //严格模式,启用则打包所有引用补丁类的class到补丁中，应对ART激进内联引起的问题
    boolean strictMode = false;

    QFixExtension(Project project) {
    }
}
```

### 3.3 设置Task和hook dexTask

设置Task和hook dexTask的逻辑主要都在QFixPlugin的configTasks函数中，以下是简化的函数代码。

```
    static void configTasks(Project project, BaseVariant variant, Creator creator) {
        Map hashMap
        //获取dexTask和proguardTask
        def dexTask = ...
        def proguardTask = ...

        if (creator.patchTaskEnable()) {
            //创建Task，寻找被修改的类，只有assemble<Virant>Patch时才会被调用
            def diffClassBeforeDex = "diffClassBeforeDex${variant.name.capitalize()}"
            def diffClassBeforeDexTask = ...
            diffClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)

            //创建Task，将改变的类打成一个dex
            def hotfixPatch = "assemble${variant.name.capitalize()}Patch"
            def hotfixPatchTask = ...
            hotfixPatchTask.dependsOn diffClassBeforeDexTask
        }

        //创建Hook Task, 在dexTask执行之前 保存所有类的sha值到对应目录的hash.txt
        def shaClassBeforeDex = "shaClassBeforeDex${variant.name.capitalize()}"
        def shaClassBeforeDexTask = ...

            //备份构建过程中的mapping.txt，如果有
            if (proguardTask) {
                def mapFile = new File("${project.buildDir}/outputs/mapping/${variant.dirName}/mapping.txt")
                def newMapFile = creator.getMappingOutFile(variant);
                Utils.copyFile(mapFile, newMapFile)
            }
        }
        shaClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)

        //对assembleRelease或assembleDebug添加Hook，保存apk
        def assembleTaskName = "assemble${variant.name.capitalize()}Base";
        def assembleTask = project.task(assembleTaskName);
        Closure saveAssembleClosure = ...
        
        //设置依赖关系，保证assembleXXXBase在android自身的assembleDebug/assembleRelease之后执行
        assembleTask.doLast(saveAssembleClosure)
        assembleTask.dependsOn shaClassBeforeDexTask
        assembleTask.dependsOn project.tasks["assemble${variant.name.capitalize()}"]
    }
```

上面创建的几个task中间最重要的是shaClassBeforeDexTask和diffClassBeforeDexTask，前者是保存基础apk中所有类的sha信息，后者则是对比基础apk和修改后的代码类的信息，找出改变的类。先来看shaClassBeforeDexTask

```
//shaClassBeforeDexTask 保存基础apk中所有类的sha信息
def shaClassBeforeDexTask = project.task(shaClassBeforeDex) << {
            //准备工作...
            ...
            
            //保存所有类的sha值到hashFile中
            Set<File> inputFiles = AndroidUtils.getDexTaskInputFiles(project, variant, dexTask)

            if (proguardTask) {
                inputFiles.each {
                    inputFile ->
                        if (inputFile.path.endsWith(".jar")) {
                            shaJarInfo(inputFile, creator.patchSetting, hashFile)
                        }
                }
            } else if (AndroidUtils.compareVersionName(Version.ANDROID_GRADLE_PLUGIN_VERSION, "2.2.3") > -1) {
                //没有混淆在2.2.3及以后插件上inputFiles不包含当前模块类
                //合并所有jar包
                Set<File> jarAndDir = new HashSet<>(inputFiles)
                jarAndDir.add(new File(project.buildDir, "intermediates/classes/${variant.dirName}"))
                File combinedJar = combineJarAndDir(project, jarAndDir)
                shaJarInfo(combinedJar, creator.patchSetting, hashFile)
            }

            //备份构建过程中的mapping.txt
            ......
        }
```

diffClassBeforeDexTask的作用是找出改变的类并dump分析改变的类在基础apk的dex中的class-id，简化的代码如下：

```
//diffClassBeforeDexTask 找出改变的类并dump分析改变的类在基础apk的dex中的class-id
def diffClassBeforeDexTask = project.task(diffClassBeforeDex) << {
                //补丁准备工作, 读取原来apk的dex目录，proguard-mapping,hash.txt,以及初始化补丁输出目录
                def baseDexDir = ...
                File mappingFile = ...
                hashMap = ...
                File patchOutDir = ...
                //获取dexTask的输入jar报参数
                //比较所有类与之前保存的sha值是否有差异，有差异则保存到patchClassDir
                Set<File> inputFiles = AndroidUtils.getDexTaskInputFiles(project, variant, dexTask)
                if (proguardTask) {
                    inputFiles.each {
                        inputFile ->
                            if (inputFile.path.endsWith(".jar")) {
                                diffJar(inputFile, hashMap, creator, variant, finder)
                            }
                    }
                } else if (AndroidUtils.compareVersionName(Version.ANDROID_GRADLE_PLUGIN_VERSION, "2.2.3") > -1) {
                    //没有混淆在2.2.3及以后插件上inputFiles不包含当前模块类
                    //合并所有jar包
                    Set<File> jarAndDir = new HashSet<>(inputFiles)
                    jarAndDir.add(new File(project.buildDir, "intermediates/classes/${variant.dirName}"))
                    File combinedJar = combineJarAndDir(project, jarAndDir)
                    diffJar(combinedJar, hashMap, creator, variant, finder)
                }
                def allRefPatchClasses = ...
                def appName = ...
                //增加dexDump处理，分析补丁类在基础apk的dex中class-id并保存
                def dumpCmdPath = AndroidUtils.getDexDumpPath(project, creator.sdkDir);
                File patchClassDir = creator.getClassOutDir(variant);
                File classIdsFile = new File(patchClassDir, CLASS_ID_TXT);
                DexClassIdResolve.dumpDexClassIds(dumpCmdPath, baseDexDir, patchClassDir, classIdsFile,appName,allRefPatchClasses)
            }
```

可以看到重要的逻辑就是比较sha值和dexDump分析class-id, diffJar函数主要是读取jar包中的类并保存sha信息，代码如下

```
static void diffJar(File jarFile, HashMap hashMap, Creator builder, BaseVariant variant, SubClsFinder finder) {
        File basePatchClassDir = builder.getClassOutDir(variant);
        if (!basePatchClassDir.exists()) basePatchClassDir.mkdirs();
        if (jarFile && jarFile.isFile()) {
            def file = new JarFile(jarFile);
            builder.patchSetting.addApplicationAndSuper(file, variant);
            Enumeration enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                InputStream inputStream = file.getInputStream(jarEntry);
                if (!builder.patchSetting.isExcluded(entryName)) {
                    def bytes = Utils.readAllBytesAndClose(inputStream);
                    def hash = DigestUtils.shaHex(bytes)
                    if (Utils.notSame(hashMap, entryName, hash)) {
                        finder.addAbsPatchClass(bytes, entryName)
                        Utils.copyBytesToFile(bytes, Utils.touchFile(basePatchClassDir, entryName))
                    } else {
                        finder.addOutPatchClass(bytes, entryName)
                    }
                }
            }
            Collection<String> collections = finder.getRefClasses();
            for (String entryName : collections) {
                ZipEntry zipEntry = file.getJarEntry(entryName);
                InputStream inputStream = file.getInputStream(zipEntry);
                def bytes = Utils.readAllBytesAndClose(inputStream);
                Utils.copyBytesToFile(bytes, Utils.touchFile(basePatchClassDir, entryName))
                inputStream.close();
            }
            file.close();
        }
    }
```

### 3.4 调用dexDump分析补丁类在原始apk的dex中class-id

dexDump的分析逻辑在alexclin.qfix.DexClassIdResolve类中，主要代码是readClassIdMap函数，该函数根据传入的dex和补丁类信息，结合dexdump工具的输出，将补丁类class-id保存下来

```
private static String readClassIdMap(InputStream mInputStream, int mDexIndex, HashSet<String> mPatchSet,Set<String> patchRefSet,ArrayList<ClassIdMap> mClassIdMapList) {
        InputStreamReader isReader = null;
        BufferedReader reader = null;
        String entrance = null;
        try {
            isReader = new InputStreamReader(mInputStream);
            reader = new BufferedReader(isReader);
            boolean findHead = false;
            boolean findClass = false;
            int classIndex = -1;
            long classIdx = -1;
            def line
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Class #") && line.endsWith(" header:") && !findHead && classIndex < 0) {
                    findHead = true;
                    classIndex = Integer.parseInt(line.substring("Class #".length(), line.indexOf(" header:")));
                } else if (line.startsWith("class_idx") && findHead && classIndex >= 0 && classIdx < 0) {
                    classIdx = Integer.parseInt(line.substring(line.indexOf(": ") + 2));
                } else if (line.startsWith("Class #") && findHead && classIndex >= 0
                        && line.contains(String.valueOf(classIndex)) && classIdx > 0) {
                    findClass = true;
                } else if (line.startsWith("  Class descriptor") && findHead && findClass && classIndex >= 0 && classIdx > 0) {
                    String className = line.substring(line.indexOf("'L") + 2, line.indexOf(";'"));
                    if (mPatchSet.contains(className)) {
                        ClassIdMap item = new ClassIdMap(className, mDexIndex, classIdx);
                        mClassIdMapList.add(item);
                    }else if(!patchRefSet.contains(className)&&entrance==null){
                        entrance = className;
                    }
                    System.out.println("className:"+className)
                    findHead = false;
                    findClass = false;
                    classIndex = -1;
                    classIdx = -1;
                }
            }
        } catch (Exception e) {
            //异常处理和关闭流
            ......
        }
        return entrance;
    }
```

因为在APP注入补丁时调用dvmResolveClass需要传入一个引用类，这里使用当前dex中和补丁类无调用关系的一个类作为该dex中补丁类的入口类

### 3.5 生成补丁

经过上面的流程，已经有所有改变的类(补丁类)和补丁类在基础apk的Dex中的class-ids信息(class-ids.txt)，生成补丁就直接调用dx工具类即可

```
def hotfixPatchTask = project.task(hotfixPatch) << {
					//调用dx工具生成apk
                ......
                AndroidUtils.dex(project, creator.getClassOutDir(variant), creator.sdkDir, pathFilePath)
                //签名补丁
                SigningConfig signingConfig = variant.signingConfig;
                if(signingConfig!=null){
                    ......
                    if (AndroidUtils.signApk(patchFile, patchSignedFile, signingConfig,compatible))
                        patchFile.delete();
                }
            }
```

## 四、补丁客户端应用代码开发

### 4.1 使用Application代理方式

因为Application类被调起之后我们才有机会去加载我们自己的补丁，而如果将Application调用了某些其他类，这些类的class也可能在我们加载补丁前就已经被加载到ClassLoader中,这样这些类就没有机会被替换了。

为了解决如上这个问题，参考了[Tinker](https://mp.weixin.qq.com/s/GOqPLO28wk1TZzBrISnZFg)的方式，也才用Application代理来解决，将真正的Application逻辑写在代理类中。demo中的Application类如下

```
public class App extends PatchApplication {
    private static final String DELEGATE_NAME = "alexclin.qfix.qfixgradle.AppDelegate";

    public App() {
        super(DELEGATE_NAME);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
        File debugPatch = new File(Environment.getExternalStorageDirectory(),"debugPatch.apk");
        PatchTool.installPatch(this,debugPatch);
    }
}
```

下面是PatchApplication的代码，主要是反射调起Application代理类的对应方法

```
public abstract class PatchApplication extends Application {
    private String delegateClassName;
    private ApplicationLifeCycle delegate;

    public PatchApplication(String delegateClassName) {
        this.delegateClassName = delegateClassName;
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        ensureDelegate();
        delegate.onCreate();
    }

    @Override
    public final void onTerminate() {
        super.onTerminate();
        if(delegate!=null) delegate.onTerminate();
    }

    @Override
    public final void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(delegate!=null) delegate.onConfigurationChanged(newConfig);
    }

    @Override
    public final void onLowMemory() {
        super.onLowMemory();
        if(delegate!=null) delegate.onLowMemory();
    }

    @Override
    public final void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(delegate!=null) delegate.onTrimMemory(level);
    }

    private void ensureDelegate(){
        if(delegate==null){
            try {
                Class<?> delegateClass = Class.forName(delegateClassName,false,getClassLoader());
                Constructor<?> constructor = delegateClass.getConstructor(Application.class);
                delegate = (ApplicationLifeCycle) constructor.newInstance(this);
            } catch (Exception e) {
                throw new IllegalArgumentException("create app delegate failed",e);
            }
        }
    }
}
```

### 4.1 反射注入补丁dex到ClassLoader中的数组前面

反射注入补丁dex到ClassLoader中的数组前面的方式和其它ClassLoader方案的注入方式差别不大，入口函数是PatchTool.installPatch(Application application,File patchFile)，在此函数中先通过InjectUtil提供的函数注入补丁dex，再使用解析出来的class-id信息调用native函数

注入函数这里只简单列出

```
   //InjectUtil类代码
   //注入补丁Dex
	static boolean injectDex(Application context, File patchFile) {
        ArrayList<File> files = new ArrayList<File>();
        files.add(patchFile);
        try {
			checkApkFiles(files);
            if(isAliyunOs()){   //阿里云OS的注入
                for(File file:files){
                    injectLexFile(context,file);
                }
                return true;
            }else if(isAndroid()){  //普通android-os的注入
                installDex(context,InjectUtil.class.getClassLoader(),context.getDir("dex", 0),files);
                return true;
            }
            throw new IllegalStateException("Current system is not support");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
```

### 4.2 解析补丁apk中的补丁class-id信息

解析补丁apk中class-id信息的逻辑在PatchTool.readPatchClassIds函数中，代码如下

```
//class-ids.txt中的格式如下 第一行是入口类信息，以：分割，格式：Dex1入口类：Dex2入口类
//之后是class-id信息，格式：classname-dexIndex-classId
private static List<Pair<String,Long>> readPatchClassIds(File patchFile, String defaultEntranceClass){
		List<Pair<String,Long>> classIds = new ArrayList<Pair<String,Long>>();
		InputStream inputStream = null;
		BufferedReader reader = null;
		SparseArray<String> dexEntrances = new SparseArray<String>();
		try {
			JarFile jarFile = new JarFile(patchFile);
			ZipEntry entry = jarFile.getEntry(CLASS_ID_TXT);
			inputStream = jarFile.getInputStream(entry);
			reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			boolean isFirst = true;
			while ((line=reader.readLine())!=null){
				if(isFirst){
					isFirst = false;
					if(line.contains(":")){ //解析入口
						String[] entrances = line.split(":");
						for(int i=0;i<entrances.length;i++){
							String entrance = entrances[i];
							if(TextUtils.isEmpty(entrance))
								continue;
							dexEntrances.put(i+1,entrance);
						}
						boolean loadEntrance = loadEntranceClasses(dexEntrances);
						if(!loadEntrance){
							return null;
						}
						continue;
					}
				}
				if (!TextUtils.isEmpty(line)) {
					String[] infos = line.split("-");
					if (infos.length == 3) {
						long classId = Long.valueOf(infos[2]);
						int dexIndex = Integer.valueOf(infos[1]);
						String entrance = getEntranceClass(dexEntrances,dexIndex,defaultEntranceClass);
						classIds.add(Pair.create(entrance,classId));
					}
				}
			}
		} catch (Exception e) {
			//.....异常处理，关闭流
		}

		return classIds;
	}
```

然后使用解析出来的class-id信息调用PatchTool.resolvePatchClass函数

```
private static boolean resolvePatchClass(Application app, String[] referrerClassList, long[] classIdxList, int size) {
    	if (!sIsLibLoaded) {
			sIsLibLoaded = loadPatchToolLib();
		}
    	if (!sIsLibLoaded) {
			boolean unloadResult = InjectUtil.unloadPatchElement(app, 0);
			Log.e(TAG, "load lib failed, unload patch result=" + unloadResult);
			return false;
		} else {
			int resolveResult = nativeResolvePatchClass(referrerClassList, classIdxList, size);
			if (resolveResult != CODE_RESOLVE_PATCH_ALL_SUCCESS) {
			    //resolve不成功从dex数组中卸载对应dex
				boolean unloadResult = InjectUtil.unloadPatchElement(app, 0);
				Log.e(TAG, String.format(Locale.ENGLISH,"resolve patch class failed, unload patch result= %b,refClass1:%s",
						unloadResult,referrerClassList[0]));
				return false;
			} else {
				Log.d(TAG, "resolve patch class success");
				return true;
			}
		}
    }
```

### 4.3 调用nativeResolveClass函数

上一节中最终会调用到nativeResolvePatchClass方法，这个方法是native方法，实现在qfixlib/src/main/jni/ResolvePatch.c中,这个C文件也是直接使用[https://github.com/lizhangqu/QFix](https://github.com/lizhangqu/QFix)的中的C文件

```
jint Java_alexclin_patch_qfix_tool_PatchTool_nativeResolvePatchClass(JNIEnv* env,
		jobject thiz, jobjectArray referrerClassList, jlongArray classIdxList, jint size) {
	LOGI("enter nativeResolvePatchClass");
	int referrerClassSize = (*env)->GetArrayLength(env, referrerClassList);
	int classIdxSize = (*env)->GetArrayLength(env, classIdxList);
	if (size <= 0 || referrerClassSize != size || classIdxSize != size) {
		LOGE("CODE_NATIVE_INIT_PARAMETER_ERROR");
		return CODE_NATIVE_INIT_PARAMETER_ERROR;
	}
	jlong* jClassIdxArray = (*env)->GetLongArrayElements(env, classIdxList, 0);
	if (jClassIdxArray == 0) {
		LOGE("CODE_NATIVE_INIT_PARAMETER_ERROR");
		return CODE_NATIVE_INIT_PARAMETER_ERROR;
	}

	void* handle = 0;
	handle = dlopen("/system/lib/libdvm.so", RTLD_LAZY);
	if (handle) {
		void* findFunc = 0;
		int i = 0;
		while(i < ARRAY_SIZE_FIND_CLASS) {
			findFunc = dlsym(handle, ARRAY_SYMBOL_FIND_LOADED_CLASS[i]);
			if (findFunc) {
				break;
			}
			i++;
		}
		if (findFunc) {
			g_pDvmFindLoadedClass_Addr = findFunc;
			void* resolveFunc = 0;
			i = 0;
			while(i < ARRAY_SIZE_RESOLVE_CLASS) {
				resolveFunc = dlsym(handle, ARRAY_SYMBOL_RESOLVE_CLASS[i]);
				if (resolveFunc) {
					break;
				}
				i++;
			}
			if (resolveFunc) {
				g_pDvmResolveClass_Addr = resolveFunc;
				i = 0;
				while(i < size) {
					jstring jClassItem = (jstring)((*env)->GetObjectArrayElement(env, referrerClassList, i));
					const char* classItem = (*env)->GetStringUTFChars(env, jClassItem, 0);
					if (classItem == 0) {
						(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
						LOGE("CODE_NATIVE_ITEM_PARAMETER_ERROR=%d", i);
						return NUM_FACTOR_PATCH * i + CODE_NATIVE_ITEM_PARAMETER_ERROR;
					}
					if (strlen(classItem) < 5 || jClassIdxArray[i] < 0) {
						(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
						(*env)->ReleaseStringUTFChars(env, jClassItem, classItem);
						LOGE("CODE_NATIVE_ITEM_PARAMETER_ERROR=%d", i);
						return NUM_FACTOR_PATCH * i + CODE_NATIVE_ITEM_PARAMETER_ERROR;
					}
					void* referrerClassObj = g_pDvmFindLoadedClass_Addr(classItem);
					if (referrerClassObj) {
						void* resClassObj = g_pDvmResolveClass_Addr(referrerClassObj, (unsigned int)jClassIdxArray[i], 1);
						if (!resClassObj) {
							(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
							(*env)->ReleaseStringUTFChars(env, jClassItem, classItem);
							LOGE("CODE_PATCH_CLASS_OBJECT_ERROR=%d", i);
							return NUM_FACTOR_PATCH * i + CODE_PATCH_CLASS_OBJECT_ERROR;
						}
					} else {
						(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
						(*env)->ReleaseStringUTFChars(env, jClassItem, classItem);
						LOGE("CODE_REFERRER_CLASS_OBJECT_ERROR=%d", i);
						return NUM_FACTOR_PATCH * i + CODE_REFERRER_CLASS_OBJECT_ERROR;
					}
					(*env)->ReleaseStringUTFChars(env, jClassItem, classItem);
					i++;
				}
			} else {
				(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
				LOGE("CODE_RESOLVE_CLASS_ERROR");
				return CODE_RESOLVE_CLASS_ERROR;
			}
		} else {
			(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
			LOGE("CODE_FIND_LOADED_CLASS_ERROR");
			return CODE_FIND_LOADED_CLASS_ERROR;
		}
	} else {
		(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
		LOGE("CODE_LOAD_DALVIK_SO_ERROR");
		return CODE_LOAD_DALVIK_SO_ERROR;
	}
	(*env)->ReleaseLongArrayElements(env, classIdxList, jClassIdxArray, 0);
	LOGI("CODE_RESOLVE_PATCH_ALL_SUCCESS");
	return CODE_RESOLVE_PATCH_ALL_SUCCESS;
}
```

到这里加载流程就结束了。

## 五、总结

[QFix方案原理]()和实现相对[Tinker方案](https://mp.weixin.qq.com/s/GOqPLO28wk1TZzBrISnZFg)来说都简单不少，比较轻量级，不过Tinker实现比较重的同时可以实现功能级更新，而QFix方案更多是应对bug的修复。

[QFixPatch](https://github.com/alexclin0188/QFixPatch)这个项目算是QFix方案的一个gradle实践，实际开发过程中原创东西不多，主要工作还是在gradle插件。对于Android热修复我也是因为工作中有需求所以了解多一些。项目中有不完善的地方也欢迎各位同学来github提issue.

热修复目前主流是NativeHook,ClassLoader,InstantRun三种方案，NativeHook,ClassLoader在android7.0版本上都会有一些兼容性问题，相对来说InstantRun方案兼容性会更好，目前我也在研究学习[Robust](http://tech.meituan.com/android_robust.html)和[Aceso](https://mp.weixin.qq.com/s/GuzbU1M1LY1VKmN7PyVbHQ)的路上，欢迎有兴趣的同学一起来探讨。

本人Github:[https://github.com/alexclin0188](https://github.com/alexclin0188) 欢迎关注
>>>>>>> Stashed changes
