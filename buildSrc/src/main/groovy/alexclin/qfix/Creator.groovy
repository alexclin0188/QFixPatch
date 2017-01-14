package alexclin.qfix

import com.android.build.gradle.api.BaseVariant
import alexclin.qfix.util.Utils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.apache.commons.io.FileUtils
import org.gradle.util.TextUtil

import java.text.ParsePosition
import java.text.SimpleDateFormat

public class Creator {
    private static final String PATCH_BASE = "PatchBase"
    private static final String MAPPING_TXT = "mapping.txt"
    private static final String HASH_TXT = "hash.txt"

    private static final String DIR_BASE = "base_";
    private static final String DIR_PATCH = "patch_%s_%s"
    private static final SimpleDateFormat TIME_NAME_FORMAT = new SimpleDateFormat("YYYYMMddHHmmss");
    //补丁基础信息默认输入目录和构建输出目录
    private File outputDir;

    //上次构建保存的信息目录
    public File baseInfoDir;
    //补丁输出目录
    public File patchOutDir;

    public File baseOutDir;

    public String sdkDir;

    public PatchSet patchSetting;

    public boolean strictMode;

    Creator(Project project, QFixExtension extension) {
        patchSetting = new PatchSet(extension);
        Properties properties = new Properties()
        File localProps = project.rootProject.file("local.properties")
        if (localProps.exists()) {
            properties.load(localProps.newDataInputStream())
            sdkDir = properties.getProperty("sdk.dir")
        } else {
            sdkDir = System.getenv("ANDROID_HOME")
        }
        String outDirPath = extension.outputDir;
        if(!outDirPath||outDirPath.empty){
            throw new InvalidUserDataException("You must set outputDir in qfix-extension for patch build");
        }
        outputDir = new File(outDirPath);
        if(outputDir.exists()&&!outputDir.isDirectory()){
            throw new InvalidUserDataException("outputDir config in qfix-extension must be a directory");
        }
        if(!outputDir.exists()){
            outputDir.mkdirs();
        }
        baseInfoDir = Utils.getFileFromProperty(project, PATCH_BASE);
        if(!baseInfoDir){
            baseInfoDir = searchBaseFromOutputDir(outputDir);
        }
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
        ensurePatchOutDir();
        return new File(patchOutDir,"${variant.dirName}/patch")
    }

    /**
     * 返回补丁构建时的补丁输出目录
     *
     * @param variant
     * @return
     */
    public File getPatchOutDir(BaseVariant variant){
        ensurePatchOutDir();
        return new File(patchOutDir,"${variant.dirName}")
    }

    /**
     * 返回基础构建时dex输出路径 TODO 删除
     *
     * @param variant
     * @return
     */
    public File getDexOutDir(BaseVariant variant){
        return new File(baseInfoDir,"${variant.dirName}/dex")
    }

    public File getBaseApkFile(BaseVariant variant){
        File baseVariantDir = new File(baseInfoDir,"${variant.dirName}");
        File[] apkFiles = baseVariantDir.listFiles(new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.isFile()&&file.name.endsWith(".apk");
            }
        })
        return apkFiles.length>0?apkFiles[0]:null;
    }

    public File getApkOutDir(BaseVariant variant,String fileName){
        ensureBaseOutDir();
        return new File(baseOutDir,"${variant.dirName}/${fileName}");
    }

    /**
     * 返回基础构建时mapping输出路径
     *
     * @param variant
     * @return
     */
    public File getMappingOutFile(BaseVariant variant){
        ensureBaseOutDir();
        return new File(baseOutDir,"${variant.dirName}/${MAPPING_TXT}")
    }

    /**
     * 返回基础构建时hash输出路径
     *
     * @param variant
     * @return
     */
    public File getHashOutFile(BaseVariant variant){
        ensureBaseOutDir();
        return new File(baseOutDir,"${variant.dirName}/${HASH_TXT}")
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

    private void ensurePatchOutDir(){
        if(!patchOutDir){
            String timeName = TIME_NAME_FORMAT.format(new Date());
            String baseName = baseInfoDir.getName();
            if(baseName.startsWith(DIR_BASE)){
                baseName = baseName.substring(6);
            }else{
                baseName = baseInfoDir.absolutePath.replace(File.separator,"-");
            }
            String patchDirName = String.format(Locale.ENGLISH,DIR_PATCH,baseName,timeName);
            patchOutDir = new File(outputDir,patchDirName);
            patchOutDir.mkdirs();
        }
    }

    public static File searchBaseFromOutputDir(File outputDir){
        if(!outputDir.exists()) return null;
        File[] baseDirs = outputDir.listFiles(new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.isDirectory()&&file.name.startsWith(DIR_BASE);
            }
        });
        if(baseDirs.length==0) return null;
        long maxTime = -1;
        int index = -1;
        for(int i=0;i<baseDirs.length;i++){
            String timeStr = baseDirs[i].name.substring(6);
            try {
                long time = TIME_NAME_FORMAT.parse(timeStr).time;
                if(time>maxTime){
                    maxTime = time;
                    index = i;
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
        if(index>-1){
            return baseDirs[index];
        }else{
            return null;
        }
    }

    public File getBaseOutDir(BaseVariant variant){
        ensureBaseOutDir();
        return new File(baseOutDir,"${variant.dirName}");
    }

    private void ensureBaseOutDir() {
        if (!baseOutDir) {
            String baseName = TIME_NAME_FORMAT.format(new Date());
            baseOutDir = new File(outputDir, DIR_BASE + baseName);
        }
    }
}

