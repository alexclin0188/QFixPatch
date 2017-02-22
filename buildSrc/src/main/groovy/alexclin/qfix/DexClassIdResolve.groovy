package alexclin.qfix

import org.apache.commons.io.FileUtils
import org.objectweb.asm.ClassReader


class DexClassIdResolve {
    /**
     * 输出补丁类的classIds
     *
     * @param dexDumpPath dexDump工具的路径
     * @param dexFilePath dex文件路径(包含classX.dex)
     * @param patchClassPath 补丁类的路径(包含补丁 xxx.class)
     * @param outputPath 输出路径
     * @throws Exception dexDump的异常
     */
    public static void dumpDexClassIds(String dexDumpPath,File dexDir,File patchClassDir,File outputFile,String application,Set<String> patchRefSet) throws Exception{
        HashSet<String> patchClassSet = new HashSet<String>();
        String[] extensions = ["class"];
        Collection<File> classFiles = FileUtils.listFiles(patchClassDir,extensions, true);
        for (File file : classFiles) {
            ClassReader reader = new ClassReader(new FileInputStream(file));
            String ctClassName = reader.className;
            if (!patchClassSet.contains(ctClassName)) {
                patchClassSet.add(ctClassName);
            }
        }
        println "ResolveDexClassId patchClass size=" + patchClassSet.size();
        if(patchClassSet.size()==0){
            println "No change class was found, so no patch will be generated!!!!!!"
            return
        }
        File[] dexFileList = dexDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().startsWith("classes") && file.getName().endsWith(".dex");
            }
        });
        if (dexFileList == null||dexFileList.length==0) {
            throw new IllegalStateException("dexFileList in dexFilePath[" + dexDir + "] is null/empty");
        }
        System.out.println("ResolveDexClassId dexFileList size=" + dexFileList.length);
        StringBuilder stringBuilder = new StringBuilder("");
        List<List<ClassIdMap>> classIdMapList = new ArrayList<>();
        application = trimClassName(application);
        for (File file : dexFileList) {
            String command = dexDumpPath + " -h " + file.getAbsolutePath();
            int dexIndex = getDexIndex(file.getName());
            Process process = Runtime.getRuntime().exec(command);
            ArrayList<ClassIdMap> resultList = new ArrayList<>();
            String entrance = readClassIdMap(process.getInputStream(), dexIndex, patchClassSet,patchRefSet,resultList);
            readErrorInfo(process.getErrorStream());
            if(entrance==null){
                throw new IllegalStateException("readClassIdMap error for dex:"+dexIndex);
            }
            classIdMapList.add(resultList);
            if(dexIndex<2){
                stringBuilder.append("L").append(application).append(";:")
            }else{
                stringBuilder.append("L").append(trimClassName(entrance)).append(";:")
            }
        }
        stringBuilder.append("\n")

        for(List<ClassIdMap> list:classIdMapList){
            for (ClassIdMap item : list) {
                System.out.println("ResolveDexClassId Item=" + item.toString());
                stringBuilder.append(item.toString()).append("\n");
            }
        }

        if (outputFile.exists()) {
            outputFile.delete();
        }
        FileWriter writer = null;
        BufferedWriter bw = null;
        try {
            writer = new FileWriter(outputFile);
            bw = new BufferedWriter(writer);
            bw.write(stringBuilder.toString());
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (Exception e) {
                    System.out.println("ResolveDexClassId close BufferedWriter exception=" + e);
                    e.printStackTrace();
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    System.out.println("ResolveDexClassId close FileWriter exception=" + e);
                    e.printStackTrace();
                }
            }
        }
    }

    private static String trimClassName(String name){
        if(name!=null&&name.endsWith(".class")){
            name = name.substring(0,name.indexOf(".class"))
        }
        return name;
    }

    private static int getDexIndex(String dexName) {
        if ("classes.dex".equals(dexName)) {
            return 1;
        } else if (dexName != null && dexName.startsWith("classes") && dexName.endsWith(".dex")) {
            String str = dexName.substring(7);
            str = str.substring(0, str.indexOf(".dex"));
            return Integer.parseInt(str);
        }
        return 0;
    }

    static class ClassIdMap {
        String className;
        int dexId;
        long classId;

        public ClassIdMap(String classname, int dexid, long classid) {
            className = classname;
            dexId = dexid;
            classId = classid;
        }

        @Override
        public String toString() {
            return new StringBuilder(className).append("-").append(dexId).append("-").append(classId).toString();
        }
    }

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
                    }else if(classIndex>1&&!patchRefSet.contains(className)&&entrance==null){
                        entrance = className;
                    }
                    findHead = false;
                    findClass = false;
                    classIndex = -1;
                    classIdx = -1;
                }
            }
        } catch (Exception e) {
            System.out.println("ProcessRunnable run exception=" + e);
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    System.out.println("ProcessRunnable close BufferedReader exception=" + e);
                    e.printStackTrace();
                }
            }
            if (isReader != null) {
                try {
                    isReader.close();
                } catch (Exception e) {
                    System.out.println("ProcessRunnable close InputStreamReader exception=" + e);
                    e.printStackTrace();
                }
            }
        }
        return entrance;
    }


    private static void readErrorInfo(InputStream inputStream){
        InputStreamReader isReader = null;
        BufferedReader reader = null;
        try {
            isReader = new InputStreamReader(inputStream);
            reader = new BufferedReader(isReader);
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            System.out.println("LogErrorRunnable run exception=" + e);
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    System.out.println("LogErrorRunnable close BufferedReader exception=" + e);
                    e.printStackTrace();
                }
            }
            if (isReader != null) {
                try {
                    isReader.close();
                } catch (Exception e) {
                    System.out.println("LogErrorRunnable close InputStreamReader exception=" + e);
                    e.printStackTrace();
                }
            }
        }
    }
}
