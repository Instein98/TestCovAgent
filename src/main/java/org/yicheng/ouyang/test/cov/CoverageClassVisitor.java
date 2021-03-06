package org.yicheng.ouyang.test.cov;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;
import static org.yicheng.ouyang.test.cov.CoverageTransformer.*;

/**
 * @author Yicheng Ouyang
 * @Date 6/30/22
 */

public class CoverageClassVisitor extends ClassVisitor{

    private String slashClassName;
    private ClassLoader loader;
    private boolean isJUnit3TestClass;
    private boolean isParameterizedTestClass;
    private int classVersion;

    private boolean d4jMode;
    private Map<String, Map<Integer, Integer>> methodLineNumMap;

    CoverageClassVisitor(ClassVisitor classVisitor, String className, ClassLoader loader,
                         int classVersion, Map<String, Map<Integer, Integer>> methodLineNumMap, boolean d4jMode) {
        super(ASM_VERSION, classVisitor);
        this.slashClassName = className;
        this.loader = loader;
        this.classVersion = classVersion;
        this.methodLineNumMap = methodLineNumMap;
        this.d4jMode = d4jMode;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superSlashName, String[] interfaces) {
        String originalSuperName = superSlashName;
        if (!d4jMode){
            // check if this class is a subclass of TestCase.class
            try{
                while (superSlashName != null){
                    if (superSlashName.equals("junit/framework/TestCase")){
                        this.isJUnit3TestClass = true;
                        break;
                    }else{
                        InputStream is;
                        if (this.loader != null){
                            is = this.loader.getResourceAsStream(superSlashName + ".class");
                        } else {
                            is = ClassLoader.getSystemResourceAsStream(superSlashName + ".class");
                        }
                        byte[] superBytes = loadByteCode(is);
                        ClassReader parentCr = new ClassReader(superBytes);
                        superSlashName = parentCr.getSuperName();
                    }
                }
//            log(String.format("%s is Junit 3 test class? %s", slashClassName, isJUnit3TestClass?"true":"false"));
            } catch (Exception e) {
                err("[ERROR] ClassLoader can not get resource: " + superSlashName + ".class");
                logStackTrace(e, logPath);
                e.printStackTrace();
            }
        }
        super.visit(version, access, name, signature, originalSuperName, interfaces);
    }

//    @Override
//    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
//        if (visible && descriptor.equals("Lorg/junit/runner/RunWith;")){
//            AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
//            return new CoverageAnnotationVisitor(av);
//        } else {
//            return super.visitAnnotation(descriptor, visible);
//        }
//    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
//        log("Visiting method " + slashClassName + "#" + name, null);
        return new CoverageMethodVisitor(mv, slashClassName, name, descriptor, isJUnit3TestClass,
                isParameterizedTestClass, this.classVersion,
                this.methodLineNumMap == null ? null : this.methodLineNumMap.get(name+"#"+descriptor), d4jMode);
    }

    private byte[] loadByteCode(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(1000);
        byte[] b = new byte[1000];
        while(inStream.read(b) != -1) {
            outStream.write(b, 0, b.length);
        }
        inStream.close();
        outStream.close();
        return outStream.toByteArray();
    }

//    class CoverageAnnotationVisitor extends AnnotationVisitor{
//        protected CoverageAnnotationVisitor(AnnotationVisitor annotationVisitor) {
//            super(ASM_VERSION, annotationVisitor);
//        }
//        @Override
//        public void visit(String name, Object value) {
//            super.visit(name, value);
//            if (name.equals("value") && Type.getType("Lorg/junit/runners/Parameterized;").equals(value)){
//                isParameterizedTestClass = true;
//            }
//        }
//    }
}

class CoverageMethodVisitor extends MethodVisitor {

    private String slashClassName;
    private String methodName;
    private String methodDesc;
    private int lineNumber = -1;
    private boolean isJUnit3TestClass;
    private boolean hasTestAnnotation;
    private boolean isParameterizedTestClass;
    private boolean hasPrameterizedTestAnnotation;
    private boolean hasNoParameters;
    private boolean isTestMethod;
    private int classVersion;
    private Map<Integer, Integer> lineNumMap;
    private boolean d4jMode;

    private Label lastLabel;
    // See https://stackoverflow.com/a/72901516/11495796
    private Map<Label, Label> translateForUninitialized = new HashMap<>();

    // insert a try catch block for the whole test method to capture the exception thrown
    private Label tryStart;
    private Label tryEndCatchStart;

    protected CoverageMethodVisitor(MethodVisitor methodVisitor, String className, String methodName,
                                    String desc, boolean isJUnit3TestClass, boolean isParameterizedTestClass,
                                    int classVersion, Map<Integer, Integer> lineNumMap, boolean d4jMode) {
        super(ASM_VERSION, methodVisitor);
        this.slashClassName = className;
        this.methodName = methodName;
        this.methodDesc = desc;
        this.isJUnit3TestClass = isJUnit3TestClass;
        this.isParameterizedTestClass = isParameterizedTestClass;
        this.hasNoParameters = desc.contains("()");
        this.classVersion = classVersion;
        this.lineNumMap = lineNumMap;
        this.d4jMode = d4jMode;
    }

    private boolean instrumentReportCoverageInvocation() {
        if (lineNumber < 0)
            return false;
        if (this.lineNumMap != null && this.lineNumMap.containsKey(lineNumber)){
            super.visitLdcInsn(slashClassName);
            super.visitLdcInsn(methodName);
            super.visitLdcInsn(lineNumber);
            super.visitLdcInsn(this.lineNumMap.get(lineNumber));
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "reportCoverageRange", "(Ljava/lang/String;Ljava/lang/String;II)V", false);
        } else {
            super.visitLdcInsn(slashClassName);
            super.visitLdcInsn(methodName);
            super.visitLdcInsn(lineNumber);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "reportCoverage", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
        }
        lineNumber = -1;
        return true;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals("Lorg/junit/Test;") || descriptor.equals("Lorg/junit/jupiter/api/Test;")){
            this.hasTestAnnotation = true;
        } else if (descriptor.equals("Lorg/junit/jupiter/params/ParameterizedTest;")){
            this.hasPrameterizedTestAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        this.isTestMethod = !d4jMode && ((isJUnit3TestClass && methodName.startsWith("test") && hasNoParameters)
                || hasTestAnnotation
                || hasPrameterizedTestAnnotation);
//        log(String.format("%s is test method? %s", slashClassName+"#"+methodName, isTestMethod?"yes":"no"), null);
        if (isTestMethod){
            super.visitLdcInsn(this.slashClassName);
            super.visitLdcInsn(this.methodName);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "testStart", "(Ljava/lang/String;Ljava/lang/String;)V", false);
            tryStart = new Label();
            super.visitLabel(tryStart);
        }
    }

    private Object[] replaceLabels(int num, Object[] array) {
        Object[] result = array;
        for(int ix = 0; ix < num; ix++) {
            Label repl = translateForUninitialized.get(result[ix]);
            if(repl == null) continue;
            if(result == array) result = array.clone();
            result[ix] = repl;
        }
        return result;
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        switch(type) {
            case F_NEW:
            case F_FULL:
                local = replaceLabels(numLocal, local);
                stack = replaceLabels(numStack, stack);
                break;
            case F_APPEND:
                local = replaceLabels(numLocal, local);
                break;
            case F_CHOP:
            case F_SAME:
                break;
            case F_SAME1:
                stack = replaceLabels(1, stack);
                break;
            default:
                throw new AssertionError();
        }
        super.visitFrame(type, numLocal, local, numStack, stack);
    }

    @Override
    public void visitInsn(int opcode) {
        instrumentReportCoverageInvocation();
        // reporting the test end event when return normally (add big try-catch block to handle exception throwing)
        if (isTestMethod && opcode >= IRETURN && opcode <= RETURN){
            super.visitLdcInsn(this.slashClassName);
            super.visitLdcInsn(this.methodName);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "testEnd", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        instrumentReportCoverageInvocation();
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        instrumentReportCoverageInvocation();
        super.visitVarInsn(opcode, varIndex);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (instrumentReportCoverageInvocation() && opcode == NEW){
            if (lastLabel != null){
                Label newLabel = new Label();
                super.visitLabel(newLabel);
                // used to replace the original label with the new label for the stack map frames later
                translateForUninitialized.put(lastLabel, newLabel);
            }
        }
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        instrumentReportCoverageInvocation();
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        instrumentReportCoverageInvocation();
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        instrumentReportCoverageInvocation();
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        instrumentReportCoverageInvocation();
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLdcInsn(Object value) {
        instrumentReportCoverageInvocation();
        super.visitLdcInsn(value);
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        instrumentReportCoverageInvocation();
        super.visitIincInsn(varIndex, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        instrumentReportCoverageInvocation();
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        instrumentReportCoverageInvocation();
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        instrumentReportCoverageInvocation();
        super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (isTestMethod){
            tryEndCatchStart = new Label();
            super.visitTryCatchBlock(tryStart, tryEndCatchStart, tryEndCatchStart, "java/lang/Throwable");
            super.visitLabel(tryEndCatchStart);
            // if the class is compiled with Java 6 or higher, stack map frames are required
//            log(String.format("Class version of %s: %d", slashClassName, classVersion), null);
            if (this.classVersion >= 50){
                super.visitFrame(F_FULL, 0, null, 1, new Object[]{"java/lang/Throwable"});
            }
            // report test method end
            super.visitLdcInsn(this.slashClassName);
            super.visitLdcInsn(this.methodName);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "testEnd", "(Ljava/lang/String;Ljava/lang/String;)V", false);
            // rethrow the caught exception
            mv.visitInsn(ATHROW);
        }
        super.visitMaxs(maxStack+4, maxLocals);
    }

    /**
     * Should not report line coverage immediately after the visitLineNumber. visitLineNumber is called right after
     * visitLabel, but it is very possible that a stack map frame is after the label, if insert instructions right
     * after the label, the original stack map frame will be messed up. So instead, insert instructions before the
     * first instruction after the label. */
    @Override
    public void visitLineNumber(int line, Label start) {
        super.visitLineNumber(line, start);
        lineNumber = line;
    }

    @Override
    public void visitLabel(Label label) {
        lastLabel = label;
        super.visitLabel(label);
    }
}
