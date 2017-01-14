package alexclin.qfix.util

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import org.apache.commons.io.FileUtils
import com.google.common.collect.Sets
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import proguard.gradle.ProGuardTask

public class AndroidUtils {

    public static String getApplication(File manifestFile) {
        def manifest = new XmlParser().parse(manifestFile)
        def androidTag = new groovy.xml.Namespace("http://schemas.android.com/apk/res/android", 'android')
        def applicationName = manifest.application[0].attribute(androidTag.name)

        if (applicationName != null) {
            return applicationName.replace(".", "/") + ".class"
        }
        return null;
    }

    public static dex(Project project, File classDir,String sdkDir,String outFilePath) {
        if (classDir.listFiles().size()) {
            if (sdkDir) {
                def cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? '.bat' : ''
                def stdout = new ByteArrayOutputStream()
                project.exec {
                    commandLine "${sdkDir}/build-tools/${project.android.buildToolsVersion}/dx${cmdExt}",
                            '--dex',
                            "--output=${outFilePath}",
                            "${classDir.absolutePath}"
                    standardOutput = stdout
                }
                def error = stdout.toString().trim()
                if (error) {
                    println "dex error:" + error
                }
            } else {
                throw new InvalidUserDataException('$ANDROID_HOME is not defined')
            }
        }
    }

    public static applyMapping(DefaultTask proguardTask,BaseVariant variant, File mappingFile) {
        if(!proguardTask) return;
        if (proguardTask instanceof ProGuardTask) {
            if (mappingFile.exists()) {
                proguardTask.applymapping(mappingFile)
            }
        } else if(variant.variantData.getScope().hasProperty('transformManager')){
            //兼容gradle1.4 增加了transformapi
            def manager = variant.variantData.getScope().transformManager;
            def proguardTransform = manager.transforms.find {
                it.class.name == com.android.build.gradle.internal.transforms.ProGuardTransform.class.name
            };
            if (proguardTransform) {
                proguardTransform.configuration.applyMapping = mappingFile
            }
        }
    }

    static String getDexTaskName(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return "transformClassesWithDexFor${variant.name.capitalize()}"
        } else {
            return "dex${variant.name.capitalize()}"
        }
    }

    static String getProcessManifestTaskName(Project project, BaseVariant variant) {
        return "process${variant.name.capitalize()}Manifest"
    }

    static String getProGuardTaskName(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
        } else {
            return "proguard${variant.name.capitalize()}"
        }
    }

    public static boolean isUseTransformAPI(Project project) {
//        return compareVersionName(project.gradle.gradleVersion, "1.4.0") >= 0;
        return compareVersionName(project.gradle.gradleVersion, "2.10") >= 0;
    }

    private static int compareVersionName(String str1, String str2) {
        String[] thisParts = str1.split("-")[0].split("\\.");
        String[] thatParts = str2.split("-")[0].split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart)
                return -1;
            if (thisPart > thatPart)
                return 1;
        }
        return 0;
    }

    static Set<File> getDexTaskInputFiles(Project project, BaseVariant variant, Task dexTask) {
        if (dexTask == null) {
            dexTask = project.tasks.findByName(getDexTaskName(project, variant));
        }

        if (isUseTransformAPI(project)) {
            Set<File> files = Sets.newHashSet();
            dexTask.inputs.files.files.each {
                def extensions = [SdkConstants.EXT_JAR] as String[]
                if (it.exists()) {
                    if (it.isDirectory()) {
                        Collection<File> jars = FileUtils.listFiles(it, extensions, true);
                        files.addAll(jars)
                    } else if (it.name.endsWith(SdkConstants.DOT_JAR)) {
                        files.add(it)
                    }
                }
            }
            return files
        } else {
            return dexTask.inputs.files.files;
        }
    }

    static String getDexDumpPath(Project project,String sdkDir){
        if(sdkDir){
            def cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? '.bat' : ''
            return "${sdkDir}/build-tools/${project.android.buildToolsVersion}/dexdump${cmdExt}";
        } else {
            throw new InvalidUserDataException('$ANDROID_HOME is not defined')
        }
    }

    static String getDexFilePath(Project project, BaseVariant variant) {
        if (isUseTransformAPI(project)) {
            return "${project.buildDir}/intermediates/transforms/dex/${variant.name}/folders/1000/1f/main"
        } else {
            return "${project.buildDir}/intermediates/${variant.name}"
        }
    }
}
