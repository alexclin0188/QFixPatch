package alexclin.qfix

import com.android.build.gradle.api.BaseVariant
import alexclin.qfix.util.Utils
import org.gradle.api.Project
import org.apache.commons.io.FileUtils

public class Creator {
    private static final String HOT_FIX_DIR = "HotFixDir"
    private static final String MAPPING_TXT = "mapping.txt"
    private static final String HASH_TXT = "hash.txt"

    //上次构建保存的信息目录
    public File baseInfoDir;
    //补丁输出目录
    public File patchOutDir;

    public String sdkDir;

    public PatchSet excluder;

    public boolean strictMode;

    Creator(Project project, QFixExtension extension) {
        excluder = new PatchSet(extension);
        Properties properties = new Properties()
        File localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            properties.load(localProps.newDataInputStream())
            sdkDir = properties.getProperty("sdk.dir")
        } else {
            sdkDir = System.getenv("ANDROID_HOME")
        }
        baseInfoDir = Utils.getFileFromProperty(project, HOT_FIX_DIR);
        patchOutDir = new File("${project.buildDir}/outputs/" + QFixPlugin.PLUGIN_NAME)
        //删除旧文件夹
        FileUtils.deleteDirectory(patchOutDir);
        strictMode = extension.strictMode;
    }

    public boolean patchTaskEnable(){
        return baseInfoDir != null;
    }

    /**
     *  返回补丁构建时的Class输出目录
     *
     * @param variant
     * @return
     */
    public File getClassOutDir(BaseVariant variant){
        return new File(patchOutDir,"${variant.dirName}/patch")
    }

    /**
     * 返回补丁构建时的补丁输出目录
     *
     * @param variant
     * @return
     */
    public File getPatchOutDir(BaseVariant variant){
        return new File(patchOutDir,"${variant.dirName}")
    }

    /**
     * 返回基础构建时dex输出路径
     *
     * @param variant
     * @return
     */
    public File getDexOutDir(BaseVariant variant){
        return new File(patchOutDir,"${variant.dirName}/dex")
    }

    /**
     * 返回基础构建时mapping输出路径
     *
     * @param variant
     * @return
     */
    public File getMappingOutFile(BaseVariant variant){
        return new File(patchOutDir,"${variant.dirName}/${MAPPING_TXT}")
    }

    /**
     * 返回基础构建时hash输出路径
     *
     * @param variant
     * @return
     */
    public File getHashOutFile(BaseVariant variant){
        return new File(patchOutDir,"${variant.dirName}/${HASH_TXT}")
    }

    public File[] getPatchClassDirs(BaseVariant variant){
        File classOutDir = getClassOutDir(variant);
        return [classOutDir];
    }

    /**
     * 返回基础信息的hash文件路径
     *
     * @param variant
     * @return
     */
    public File getBaseHashFile(BaseVariant variant){
        return new File(baseInfoDir,"${variant.dirName}/${HASH_TXT}")
    }

    /**
     * 返回基础信息的mapping文件路径
     *
     * @param variant
     * @return
     */
    public File getBaseMappingFile(BaseVariant variant){
        return new File(baseInfoDir,"${variant.dirName}/${MAPPING_TXT}")
    }

    public File getBaseDexDir(BaseVariant variant){
        return new File(baseInfoDir,"${variant.dirName}/dex")
    }
}

