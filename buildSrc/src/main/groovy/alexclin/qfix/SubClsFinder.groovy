package alexclin.qfix

import org.objectweb.asm.ClassReader
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.lang.reflect.Modifier
import org.objectweb.asm.ClassVisitor
import static org.objectweb.asm.Opcodes.ASM4

public class SubClsFinder extends ClassVisitor{
    //不在补丁中的类
    private ArrayList<OutPatchClass> outPatchClasses;
    //补丁中的虚基类
    private HashSet<String> absPatchClasses;
    //补丁中的所有类
    private HashSet<String> allPatchClasses;

    private boolean strictMode;

    private final RefMethodVisitor methodVisitor = new RefMethodVisitor();
    private String currentEntry;
    private Set<String> currentRefs;
    private Map<String,Set<String>> entryRefMap;

    SubClsFinder(boolean strict) {
        super(ASM4);
        outPatchClasses = new HashMap<>();
        absPatchClasses = new HashSet<>();
        allPatchClasses = new HashSet<>();
        strictMode = strict;
        entryRefMap = new HashMap<>();
    }

    public void addOutPatchClass(byte[] bytes, String entryName){
        addOutPatchClass(new ClassReader(bytes),entryName);
    }

    public void addOutPatchClass(ClassReader cr, String entryName){
        def superName = cr.superName;
        if(superName){
            boolean isAbstract = Modifier.isAbstract(cr.access);
            OutPatchClass opc = new OutPatchClass(entryName,toPath(superName),isAbstract);
            outPatchClasses.add(opc);
        }
        visitClassReader(cr);
    }

    private static String toPath(String className){
        return className.replaceAll("\\.",File.separator)+".class";
    }

    public void addAbsPatchClass(byte[] bytes, String entryName){
        ClassReader cr = new ClassReader(bytes);
        addAbsPatchClass(cr,entryName)
    }

    public void addAbsPatchClass(ClassReader cr, String entryName){
        if(Modifier.isAbstract(cr.access)||strictMode){
            absPatchClasses.add(entryName);
        }
        allPatchClasses.add(entryName);
    }

    private Collection<String> getAbsPatchSubClasses(Set<String> absPatchClasses,boolean allowAll){
        HashSet<String> subClasses = new HashSet<>();
        for(OutPatchClass opc:outPatchClasses){
            if(absPatchClasses.contains(opc.superName)){
                subClasses.add(opc.entryName);
                if(opc.isAbstract||allowAll){
                    ArrayList<OutPatchClass> absSubs = getSubclasses(opc.entryName,subClasses);
                    //如果虚基类继承有很多层，这里会有stack overflow风险，
                    //但是连着很多层的虚基类继承，绝壁是代码写的有问题
                    getListSubs(absSubs,subClasses);
                }
            }
        }
        return subClasses;
    }

    private ArrayList<OutPatchClass> getSubclasses(String entryName,HashSet<String> outSet){
        ArrayList<OutPatchClass> absSubs = new ArrayList<>();
        for(OutPatchClass opc:outPatchClasses){
            if(opc.superName.equals(entryName)){
                outSet.add(opc.entryName);
                if(opc.isAbstract){
                    absSubs.add(opc);
                }
            }
        }
        return absSubs;
    }

    private void getListSubs(ArrayList<OutPatchClass> absSubs,HashSet<String> outSet){
        if(!absSubs.isEmpty()){
            for(OutPatchClass o:absSubs){
                ArrayList<OutPatchClass> absSubs2 = getSubclasses(o.entryName,outSet);
                getListSubs(absSubs2,outSet);
            }
        }
    }

    static class OutPatchClass {
        String entryName;
        String superName;
        boolean isAbstract;
        HashSet<String> refClasses;

        OutPatchClass(String entryName, String superName, boolean isAbstract) {
            this.entryName = entryName
            this.superName = superName
            this.isAbstract = isAbstract
        }

        public void addRefClasses(String className){
            if(refClasses==null){
                refClasses = new HashSet<>();
            }
            refClasses.add(className);
        }

        @Override
        public String toString() {
            return "OutPatchClass{" +
                    "entryName='" + entryName + '\'' +
                    ", superName='" + superName + '\'' +
                    ", isAbstract=" + isAbstract +
                    '}';
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return methodVisitor;
    }

    private static boolean isInvokeOpcode(int opcode){
        return (opcode==Opcodes.INVOKESTATIC)||
                (opcode==Opcodes.INVOKEDYNAMIC)||
                (opcode==Opcodes.INVOKEINTERFACE)||
                (opcode==Opcodes.INVOKEVIRTUAL)
    }

    public void saveInvokeClass(String clazzName){
        if(currentRefs!=null){
            currentRefs.add(clazzName)
        }
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentEntry = name;
        currentRefs = new HashSet<>();
        super.visit(version, access, name, signature, superName, interfaces)
    }

    @Override
    void visitEnd() {
        if(currentEntry!=null&&currentRefs!=null){
            entryRefMap.put(currentEntry,currentRefs);
        }
        currentEntry = null;
        currentRefs = null;
        super.visitEnd()
    }

    class RefMethodVisitor extends MethodVisitor{

        RefMethodVisitor() {
            super(ASM4)
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if(isInvokeOpcode(opcode)){
                saveInvokeClass(owner);
            }
        }
    }

    public void visitClassReader(ClassReader cr){
        cr.accept(this,0);
    }

    public Collection<String> searchRefPatchClass(Collection<String> patchClasses,Collection<String> subClasses){
        Set<String> set = new HashSet<>();
        set.addAll(patchClasses)
        set.addAll(subClasses)
        HashSet<String> refSet = new HashSet<>();
        for(Map.Entry<String,Set<String>> entry:entryRefMap.entrySet()){
            for(String clazz:entry.value){
                if(set.contains(clazz+".class")){
                    refSet.add(entry.key+".class");
                    break;
                }
            }
        }
        set.addAll(refSet);
        return set;
    }

    public Collection<String> getRefClasses(){
        Collection<String> collection = getAbsPatchSubClasses(absPatchClasses,false);
        if(!strictMode) return collection;
        return searchRefPatchClass(absPatchClasses,collection);
    }

    public Collection<String> getAllRefPatchClasses(){
        Collection<String> collection = getAbsPatchSubClasses(allPatchClasses,true);
        return searchRefPatchClass(allPatchClasses,collection);
    }
}