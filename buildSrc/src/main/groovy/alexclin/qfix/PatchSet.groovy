package alexclin.qfix

import com.android.build.gradle.api.BaseVariant
import alexclin.qfix.util.AndroidUtils
import org.objectweb.asm.ClassReader

import java.util.jar.JarFile
import java.util.zip.ZipEntry;

/**
 * 用于判断需要排除哪些类
 */

public class PatchSet {
    private HashSet<String> includePackage;
    private HashSet<String> excludeClass;

    public PatchSet(QFixExtension extension) {
        this.includePackage = extension.includePackage;
        this.excludeClass = extension.excludeClass;
    }

    public boolean isExcluded(String entryName){
        for(String exclude:excludeClass){
            if (entryName.equals(exclude)||entryName.endsWith(exclude)) {
                return true;
            }
        }
        for(String include:includePackage){
            if (entryName.contains(include)) {
                return false;
            }
        }
        return !entryName.endsWith(".class") || isAndroidSupport(entryName) ||
                isQFixLib(entryName) || isBuildConfigOrR(entryName);
    }

    private static boolean isBuildConfigOrR(String entryName){
        return entryName.contains("/R\$") ||
                entryName.endsWith("/R.class") || entryName.endsWith("/BuildConfig.class");
    }

    private static boolean isQFixLib(String entryName){
        return entryName.startsWith("alexclin/patch/qfix") ;
    }

    private static boolean isAndroidSupport(String entryName){
        return entryName.contains("android/support/");
    }

    public void addExcludeClass(String name){
        excludeClass.add(name);
    }

    public void addApplicationAndSuper(JarFile jarFile, BaseVariant variant){
        //查找manifest里的Application及其父类
        def manifestFile = variant.outputs.processManifest.manifestOutputFile[0]
        def applicationName = AndroidUtils.getApplication(manifestFile)
        if (applicationName != null) {
            addExcludeClass(applicationName)
            while ((applicationName= getSuperEntry(jarFile,applicationName))!=null){
                addExcludeClass(applicationName);
            }
        }
    }

    public static String getSuperEntry(JarFile file, String entryName){
        ZipEntry entry = file.getEntry(entryName);
        if(entry==null) return null;
        ClassReader cr = new ClassReader(file.getInputStream(entry));
        return cr.superName+".class";
    }
}
