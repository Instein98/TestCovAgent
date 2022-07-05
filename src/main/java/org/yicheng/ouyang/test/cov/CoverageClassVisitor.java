package org.yicheng.ouyang.test.cov;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
    private int classVersion;

    CoverageClassVisitor(ClassVisitor classVisitor, String className, ClassLoader loader, int classVersion) {
        super(ASM_VERSION, classVisitor);
        this.slashClassName = className;
        this.loader = loader;
        this.classVersion = classVersion;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superSlashName, String[] interfaces) {
        String originalSuperName = superSlashName;
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
        } catch (Exception e) {
            err("[ERROR] ClassLoader can not get resource: " + superSlashName + ".class");
            logStackTrace(e);
            e.printStackTrace();
        }
        super.visit(version, access, name, signature, originalSuperName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
//        log("Visiting method " + slashClassName + "#" + name, null);
        return new CoverageMethodVisitor(mv, slashClassName, name, isJUnit3TestClass, this.classVersion);
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

}

class CoverageMethodVisitor extends MethodVisitor {

    private String slashClassName;
    private String methodName;
    private int currentLine;
    private boolean isJUnit3TestClass;
    private boolean hasTestAnnotation;
    private boolean isTestMethod;
    private int classVersion;

    private boolean isRightAfterLabel;

    // insert a try catch block for the whole test method to capture the exception thrown
    private Label tryStart;
    private Label tryEndCatchStart;

    protected CoverageMethodVisitor(MethodVisitor methodVisitor, String className, String methodName, boolean isJUnit3TestClass, int classVersion) {
        super(ASM_VERSION, methodVisitor);
        this.slashClassName = className;
        this.methodName = methodName;
        this.isJUnit3TestClass = isJUnit3TestClass;
        this.classVersion = classVersion;
    }

    private void instrumentReportCoverageInvocation() {
        super.visitLdcInsn(slashClassName);
        super.visitLdcInsn(methodName);
        super.visitLdcInsn(currentLine);
        super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                "reportCoverage", "(Ljava/lang/String;Ljava/lang/String;I)V", false);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals("Lorg/junit/Test;") || descriptor.equals("Lorg/junit/jupiter/api/Test;")){
            this.hasTestAnnotation = true;
        }
        return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        this.isTestMethod = (this.isJUnit3TestClass && this.methodName.startsWith("test")) || hasTestAnnotation;
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

    @Override
    public void visitInsn(int opcode) {
//        instrumentReportCoverageInvocation();
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
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitVarInsn(opcode, varIndex);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitLdcInsn(value);
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitIincInsn(varIndex, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        if (isRightAfterLabel) instrumentReportCoverageInvocation(); isRightAfterLabel = false;
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
        super.visitMaxs(maxStack+3, maxLocals);
    }

    /**
     * Should not report line coverage immediately after the visitLineNumber. visitLineNumber is called right after
     * visitLabel, but it is very possible that a stack map frame is after the label, if insert instructions right
     * after the label, the original stack map frame will be messed up. So instead, insert instructions before the
     * first instruction after the label. */
    @Override
    public void visitLineNumber(int line, Label start) {
        super.visitLineNumber(line, start);
        currentLine = line;
        isRightAfterLabel = true;
//        instrumentReportCoverageInvocation();
    }
}
