package alexclin.qfix

import alexclin.qfix.util.AndroidUtils
import alexclin.qfix.util.Utils
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.builder.Version

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


public class QFixPlugin implements Plugin<Project> {
    static final String CLASS_ID_TXT = "class-ids.txt"
    static final String SPLIT_PATCH_FORMAT = "%s_%s.apk";

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

    static void configTasks(Project project, BaseVariant variant, Creator creator) {
        Map hashMap

        def dexTask = project.tasks.findByName(AndroidUtils.getDexTaskName(project, variant))
        def proguardTask = project.tasks.findByName(AndroidUtils.getProGuardTaskName(project, variant))

        if (creator.patchTaskEnable()) {
            //保存所有改变的类到指定目录
            def diffClassBeforeDex = "diffClassBeforeDex${variant.name.capitalize()}"
            def diffClassBeforeDexTask = project.task(diffClassBeforeDex) << {
                //补丁准备工作
                if (!creator.baseInfoDir) {
                    throw new InvalidUserDataException("no PatchBase dir found, maybe you need run by: ./gradlew clean assembleXxxPatch -P PatchBase=...your patch base..");
                }
                def baseDexDir = creator.getDexOutDir(variant)
                File mappingFile = creator.getBaseMappingFile(variant)
                if (mappingFile && mappingFile.exists()) {
                    AndroidUtils.applyMapping((DefaultTask) proguardTask, variant, mappingFile)
                }
                def hashFile = creator.getBaseHashFile(variant)
                if (!hashFile || !hashFile.exists()) {
                    throw new InvalidUserDataException("No base hash.txt in:${creator.baseInfoDir}, cannot continue patch task");
                }
                hashMap = Utils.parseMap(hashFile)
                File patchOutDir = creator.getPatchOutDir(variant);
                //判断dex目录或apk是否存在,不存在就抛异常
                if (!baseDexDir.exists()) {
                    File apkFile = creator.getBaseApkFile(variant);
                    if (!apkFile || !apkFile.exists()) {
                        throw new InvalidUserDataException("No base apk in:${creator.baseInfoDir}");
                    }
                    Utils.unZipFile(apkFile, creator.getDexOutDir(variant), ".dex");
                }
                if (!patchOutDir.exists()) patchOutDir.mkdirs()

                //比较所有类与之前保存的sha值是否有差异，有差异则保存到patchClassDir
                Set<File> inputFiles = AndroidUtils.getDexTaskInputFiles(project, variant, dexTask)
                if (proguardTask) {
                    inputFiles.each {
                        inputFile ->
                            if (inputFile.path.endsWith(".jar")) {
                                diffJar(inputFile, hashMap, creator, variant)
                            }
                    }
                } else if (AndroidUtils.compareVersionName(Version.ANDROID_GRADLE_PLUGIN_VERSION, "2.2.3") > -1) {
                    //没有混淆在2.2.3及以后插件上inputFiles不包含当前模块类
                    //合并所有jar包
                    Set<File> jarAndDir = new HashSet<>(inputFiles)
                    jarAndDir.add(new File(project.buildDir, "intermediates/classes/${variant.dirName}"))
                    File combinedJar = combineJarAndDir(project, jarAndDir)
                    diffJar(combinedJar, hashMap, creator, variant)
                }
                //增加dexDump处理，dump patch class ids
                def dumpCmdPath = AndroidUtils.getDexDumpPath(project, creator.sdkDir);
                File patchClassDir = creator.getClassOutDir(variant);
                File classIdsFile = new File(patchClassDir, CLASS_ID_TXT);
                DexClassIdResolve.dumpDexClassIds(dumpCmdPath, baseDexDir, patchClassDir, classIdsFile)
            }
            diffClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)

            //将改变的类打成一个dex
            def hotfixPatch = "assemble${variant.name.capitalize()}Patch"
            def hotfixPatchTask = project.task(hotfixPatch) << {
                File patchOutDir = creator.getPatchOutDir(variant);
                File patchFile = new File(patchOutDir, PATCH_NAME + "_${project.name}-${variant.dirName}-unsigned.apk");
                String pathFilePath = patchFile.absolutePath;
                AndroidUtils.dex(project, creator.getClassOutDir(variant), creator.sdkDir, pathFilePath)
                //TODO 签名补丁
            }
            hotfixPatchTask.dependsOn diffClassBeforeDexTask
        }

        //在dexTask执行之前 保存所有类的sha值
        def shaClassBeforeDex = "shaClassBeforeDex${variant.name.capitalize()}"
        def shaClassBeforeDexTask = project.task(shaClassBeforeDex) << {
            def baseOutDir = creator.getBaseOutDir(variant);
            //补丁准备工作
            if (!baseOutDir.exists()) baseOutDir.mkdirs()
            def hashFile = creator.getHashOutFile(variant)
            if (!hashFile.exists()) {
                hashFile.createNewFile()
            }
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
        Closure saveAssembleClosure = {
            //解压apk得到dex文件并保存
            File apkFile = new File("${project.buildDir}/outputs/apk/${project.name}-${variant.dirName}.apk");
            if (!apkFile.exists()) {
                apkFile = new File("${project.buildDir}/outputs/apk/${project.name}-${variant.dirName}-unsigned.apk");
            }
            Utils.copyFile(apkFile, creator.getApkOutDir(variant, apkFile.getName()));
        }

        assembleTask.doLast(saveAssembleClosure)
        assembleTask.dependsOn shaClassBeforeDexTask
        assembleTask.dependsOn project.tasks["assemble${variant.name.capitalize()}"]
    }

    static void shaJarInfo(File jarFile, PatchSet set, File outFile) {
        if (jarFile && jarFile.isFile()) {
            def file = new JarFile(jarFile);
            Enumeration enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                InputStream inputStream = file.getInputStream(jarEntry);
                if (!set.isExcluded(entryName)) {
                    def bytes = Utils.readAllBytesAndClose(inputStream);
                    def hash = DigestUtils.shaHex(bytes)
                    outFile.append(Utils.format(entryName, hash))
                }
            }
            file.close();
        }
    }

    static void diffJar(File jarFile, HashMap hashMap, Creator builder, BaseVariant variant) {
        File basePatchClassDir = builder.getClassOutDir(variant);
        if (!basePatchClassDir.exists()) basePatchClassDir.mkdirs();
        if (jarFile && jarFile.isFile()) {
            def file = new JarFile(jarFile);
            builder.patchSetting.addApplicationAndSuper(file, variant);
            SubClsFinder finder = new SubClsFinder(builder.strictMode);
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

    static File combineJarAndDir(Project project, Set<File> files) {
        String[] extensions = ["class"];
        File combinedJarFile = new File(project.buildDir, 'qfix-combined.jar');
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(combinedJarFile));
        for (File file : files) {
            if (file.isFile()) {
                def jarFile = new JarFile(file);
                Enumeration enumeration = jarFile.entries();
                while (enumeration.hasMoreElements()) {
                    JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                    String entryName = jarEntry.getName();
                    if (entryName.endsWith(".class")) {
                        InputStream inputStream = jarFile.getInputStream(jarEntry);
                        jarOutputStream.putNextEntry(new ZipEntry(entryName));
                        jarOutputStream << inputStream
                        inputStream.close();
                        jarOutputStream.closeEntry();
                    }
                }
            } else {
                Collection<File> classFiles = FileUtils.listFiles(file, extensions, true);
                def dirName = file.absolutePath
                classFiles.each {
                    inputClassFile ->
                        def classPath = inputClassFile.path;
                        if (classPath.endsWith(".class")) {
                            if ("\\".equals(File.separator)) {
                                classPath = classPath.split("${dirName}\\\\")[1]
                            } else {
                                classPath = classPath.split("${dirName}/")[1]
                            }
                            jarOutputStream.putNextEntry(new ZipEntry(classPath));
                            FileInputStream fis = new FileInputStream(inputClassFile);
                            jarOutputStream << fis
                            fis.close();
                            jarOutputStream.closeEntry();
                        }
                }
            }
        }
        jarOutputStream.close();
        return combinedJarFile;
    }
}