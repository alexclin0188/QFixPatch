package alexclin.qfix.util

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class Utils {

    public static File touchFile(File dir, String path) {
        def file = new File("${dir}/${path}")
        file.getParentFile().mkdirs()
        return file
    }

    public static touchFile(File file){
        if(!file.exists()){
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
    }

    public static copyBytesToFile(byte[] bytes, File file) {
        if (!file.exists()) {
            file.createNewFile()
        }
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(bytes);
        fos.close();
    }

    public static File getFileFromProperty(Project project, String property) {
        def file
        if (project.hasProperty(property)) {
            String path = project.getProperties()[property];
            if(path.startsWith("/")){
                file = new File(path);
            }else{
                file = new File(project.rootDir,path);
            }
            if (!file.exists()) {
                throw new InvalidUserDataException("${project.getProperties()[property]} does not exist")
            }
            if (!file.isDirectory()) {
                throw new InvalidUserDataException("${project.getProperties()[property]} is not directory")
            }
        }
        return file
    }

    public static File getVariantFile(File dir, def variant, String fileName) {
        return new File("${dir}/${variant.dirName}/${fileName}")
    }

    public static void unZipFile(File zipFile,File outputDir,String... extensions){
        if(!outputDir.exists()){
            outputDir.mkdirs();
        }
        ZipFile zip = new ZipFile(zipFile);
        for(Enumeration entries = zip.entries();entries.hasMoreElements();){
            ZipEntry entry = (ZipEntry)entries.nextElement();
            String zipEntryName = entry.getName();
            if(!hasExtension(zipEntryName,extensions)){
                continue;
            }
            InputStream is = zip.getInputStream(entry);
            String outPath = (outputDir.absolutePath+"/"+zipEntryName).replaceAll("\\*", "/");;
            //判断路径是否存在,不存在则创建文件路径
            File file = new File(outPath.substring(0, outPath.lastIndexOf('/')));
            if(!file.exists()){
                file.mkdirs();
            }
            //判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
            if(new File(outPath).isDirectory()){
                continue;
            }

            OutputStream out = new FileOutputStream(outPath);
            byte[] buf1 = new byte[1024];
            int len;
            while((len=is.read(buf1))>0){
                out.write(buf1,0,len);
            }
            is.close();
            out.close();
        }
    }

    public static boolean hasExtension(String name,String... extensions){
        if(!name) return false;
        if(!extensions||extensions.length==0) return true;
        for(String ext : extensions){
            if(name.endsWith(ext)) return true;
        }
        return false;
    }

    public static byte[] readAllBytesAndClose(InputStream is){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos << is;
        return bos.toByteArray();
    }

    public static byte[] readAllBytesAndClose(File file){
        InputStream is = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos << is;
        return bos.toByteArray();
    }

    public static void copyFile(File from,File to){
        touchFile(to);
        new FileOutputStream(to)<<new FileInputStream(from);
    }

    private static final String MAP_SEPARATOR = ":"

    public static boolean notSame(Map map, String name, String hash) {
        def notSame
        if (map) {
            def value = map.get(name)
            notSame = !value || !value.equals(hash);
        }else{
            notSame = false
        }
        return notSame
    }

    public static Map parseMap(File hashFile) {
        def hashMap = [:]
        if (hashFile.exists()) {
            hashFile.eachLine {
                List list = it.split(MAP_SEPARATOR)
                if (list.size() == 2) {
                    hashMap.put(list[0], list[1])
                }
            }
        } else {
            println "$hashFile does not exist"
        }
        return hashMap
    }

    public static format(String path, String hash) {
        return path + MAP_SEPARATOR + hash + "\n"
    }
}
