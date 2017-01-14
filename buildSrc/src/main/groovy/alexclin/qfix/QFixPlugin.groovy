package alexclin.qfix

import alexclin.qfix.util.AndroidUtils
import alexclin.qfix.util.Utils
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
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

    private void applyPlugin(Project project){
        project.afterEvaluate {
            def extension = project.extensions.findByName(PLUGIN_NAME) as QFixExtension

            Creator dirBuilder = new Creator(project,extension);
            debugOn = extension.debugOn

            project.android.applicationVariants.each { variant ->

                if(variant.name.contains(DEBUG)&&!debugOn)
                    return;
                configTasks(project,variant,dirBuilder);
            }

            //添加总的buildPatch Task
            def releasePatch = project.tasks.findByName("assembleReleasePatch")
            def debugPatch = project.tasks.findByName("assembleDebugPatch")
            if (releasePatch != null || debugPatch != null) {
                def buildPatchTask = project.task("buildPatches")
                if (debugPatch) buildPatchTask.dependsOn debugPatch
                if (releasePatch) buildPatchTask.dependsOn releasePatch
            }
        }
    }

    static void configTasks(Project project, BaseVariant variant, Creator dirBuilder) {
        Map hashMap

        def dexTask = project.tasks.findByName(AndroidUtils.getDexTaskName(project, variant))
        def proguardTask = project.tasks.findByName(AndroidUtils.getProGuardTaskName(project, variant))

        if (dirBuilder.patchTaskEnable()) {
            File mappingFile = dirBuilder.getBaseMappingFile(variant)
            if (mappingFile.exists()) {
                AndroidUtils.applyMapping((DefaultTask) proguardTask, variant, mappingFile)
            }
            def hashFile = dirBuilder.getBaseHashFile(variant)
            hashMap = Utils.parseMap(hashFile)
        }

        //对assembleRelease或assembleDebug添加Hook，保存apk
        def assembleTaskName = "assemble${variant.name.capitalize()}";
        def assembleTask = project.tasks[assembleTaskName];
        Closure saveAssembleClosure = {
            //解压apk得到dex文件并保存
            File apkFile = new File("${project.buildDir}/outputs/apk/${project.name}-${variant.dirName}.apk");
            if (!apkFile.exists()) {
                apkFile = new File("${project.buildDir}/outputs/apk/${project.name}-${variant.dirName}-unsigned.apk");
            }
            Utils.unZipFile(apkFile, dirBuilder.getDexOutDir(variant), ".dex");
        }
        assembleTask.doLast(saveAssembleClosure)

        File patchOutDir = dirBuilder.getPatchOutDir(variant);

        if (dirBuilder.patchTaskEnable()) {

            //保存所有改变的类到指定目录
            def diffClassBeforeDex = "diffClassBeforeDex${variant.name.capitalize()}"
            def diffClassBeforeDexTask = project.task(diffClassBeforeDex) << {
                //补丁准备工作
                def baseDexDir = dirBuilder.getBaseDexDir(variant);
                //判断dex目录或apk是否存在,不存在就抛异常
                if (!baseDexDir.exists()) {
                    throw new InvalidUserDataException("Not dex dir in:${dirBuilder.baseInfoDir}");
                }
                if (!patchOutDir.exists()) patchOutDir.mkdirs()

                dirBuilder.scanModuleInfo(project,variant);

                //比较所有类与之前保存的sha值是否有差异，有差异则保存到patchClassDir
                Set<File> inputFiles = AndroidUtils.getDexTaskInputFiles(project, variant, dexTask)
                inputFiles.each {
                    inputFile ->
                        if (inputFile.path.endsWith(".jar")) {
                            diffJar(inputFile, hashMap,dirBuilder,variant)
                        }
                }
                //增加dexDump处理，dump patch class ids
                def dumpCmdPath = AndroidUtils.getDexDumpPath(project, dirBuilder.sdkDir);
                if(dirBuilder.isSplit()){
                    File[] patchClassDirs = dirBuilder.getPatchClassDirs(variant);
                    for(File patchClassDir:patchClassDirs){
                        File classIdsFile = new File(patchClassDir, CLASS_ID_TXT);
                        DexClassIdResolve.dumpDexClassIds(dumpCmdPath, baseDexDir, patchClassDir, classIdsFile)
                    }
                }else{
                    File patchClassDir = dirBuilder.getClassOutDir(variant);
                    File classIdsFile = new File(patchClassDir, CLASS_ID_TXT);
                    DexClassIdResolve.dumpDexClassIds(dumpCmdPath, baseDexDir, patchClassDir, classIdsFile)
                }
            }
            diffClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)

            //将改变的类打成一个dex
            def hotfixPatch = "assemble${variant.name.capitalize()}Patch"
            def hotfixPatchTask = project.task(hotfixPatch) << {
                if (dirBuilder.isSplit()) {
                    File[] patchClassDirs = dirBuilder.getPatchClassDirs(variant);
                    for (File patchClassDir : patchClassDirs) {
                        File patchFile = new File(patchOutDir, String.format(Locale.ENGLISH, SPLIT_PATCH_FORMAT, HotFixPlugin.PATCH_NAME, patchClassDir.name));
                        String pathFilePath = patchFile.absolutePath;
                        AndroidUtils.dex(project, patchClassDir, dirBuilder.sdkDir, pathFilePath)
                    }
                } else {
                    File patchFile = new File(patchOutDir, HotFixPlugin.PATCH_NAME + ".apk");
                    String pathFilePath = patchFile.absolutePath;
                    AndroidUtils.dex(project, dirBuilder.getClassOutDir(variant), dirBuilder.sdkDir, pathFilePath)
                }
            }
            hotfixPatchTask.dependsOn diffClassBeforeDexTask
        }

        //在dexTask执行之前 保存所有类的sha值
        def shaClassBeforeDex = "shaClassBeforeDex${variant.name.capitalize()}"
        def shaClassBeforeDexTask = project.task(shaClassBeforeDex) << {
            //补丁准备工作
            if (!patchOutDir.exists()) patchOutDir.mkdirs()
            def hashFile = dirBuilder.getHashOutFile(variant)
            if (!hashFile.exists()) {
                hashFile.createNewFile()
            }
            //保存所有类的sha值到hashFile中
            Set<File> inputFiles = AndroidUtils.getDexTaskInputFiles(project, variant, dexTask)
            inputFiles.each {
                inputFile ->
                    if (inputFile.path.endsWith(".jar")) {
                        shaJarInfo(inputFile, dirBuilder.excluder, hashFile)
                    }
            }
            //备份构建过程中的mapping.txt
            if (proguardTask) {
                def mapFile = new File("${project.buildDir}/outputs/mapping/${variant.dirName}/mapping.txt")
                def newMapFile = dirBuilder.getMappingOutFile(variant);
                Utils.copyFile(mapFile, newMapFile)
            }
        }
        shaClassBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
        //放在diffClassBeforeDexTask之后设置，否则diffClassBeforeDexTask会依赖shaClassBeforeDexTask
        dexTask.dependsOn shaClassBeforeDexTask
    }

    static void shaJarInfo(File jarFile, PatchSet excluder, File outFile) {
        if (jarFile && jarFile.isFile()) {
            def file = new JarFile(jarFile);
            Enumeration enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                InputStream inputStream = file.getInputStream(jarEntry);
                if (!excluder.isExcluded(entryName)) {
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
        if(!basePatchClassDir.exists()) basePatchClassDir.mkdirs();
        if (jarFile && jarFile.isFile()) {
            def file = new JarFile(jarFile);
            builder.excluder.addApplicationAndSuper(file,variant);
            SubClsFinder finder = new SubClsFinder(builder.strictMode);
            Enumeration enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                InputStream inputStream = file.getInputStream(jarEntry);
                if (!builder.excluder.isExcluded(entryName)) {
                    def bytes = Utils.readAllBytesAndClose(inputStream);
                    def hash = DigestUtils.shaHex(bytes)
                    if (Utils.notSame(hashMap, entryName, hash)) {
                        finder.addAbsPatchClass(bytes, entryName)
                        if(builder.isSplit()){
                            File patchClassDir = new File(basePatchClassDir,builder.getClassModule(entryName,variant));
                            Utils.copyBytesToFile(bytes, Utils.touchFile(patchClassDir, entryName))
                        }else{
                            Utils.copyBytesToFile(bytes, Utils.touchFile(basePatchClassDir, entryName))
                        }
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
                if(builder.isSplit()){
                    File patchClassDir = new File(basePatchClassDir,builder.getClassModule(entryName,variant));
                    Utils.copyBytesToFile(bytes, Utils.touchFile(patchClassDir, entryName))
                }else{
                    Utils.copyBytesToFile(bytes, Utils.touchFile(basePatchClassDir, entryName))
                }
                inputStream.close();
            }
            file.close();
        }
    }
}