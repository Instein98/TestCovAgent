package org.yicheng.ouyang.test.cov;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.yicheng.ouyang.test.cov.CoverageTransformer.ASM_VERSION;

/**
 * @author Yicheng Ouyang
 * @Date 7/12/22
 */

public class D4jFormatterClassVisitor extends ClassVisitor {

    D4jFormatterClassVisitor(ClassVisitor classVisitor) {
        super(ASM_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("endTest") || name.equals("startTest")){
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new D4jFormatterMethodVisitor(mv, name);
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}

class D4jFormatterMethodVisitor extends MethodVisitor {

    String methodName;

    D4jFormatterMethodVisitor(MethodVisitor methodVisitor, String methodName) {
        super(ASM_VERSION, methodVisitor);
        this.methodName = methodName;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        if (methodName.equals("startTest")){
            super.visitVarInsn(ALOAD, 1);
            super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString",
                    "()Ljava/lang/String;", false);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "d4jStartTest", "(Ljava/lang/String;)V", false);
        } else if (methodName.equals("endTest")){
            super.visitVarInsn(ALOAD, 1);
            super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString",
                    "()Ljava/lang/String;", false);
            super.visitMethodInsn(INVOKESTATIC, "org/yicheng/ouyang/test/cov/CoverageCollector",
                    "d4jEndTest", "(Ljava/lang/String;)V", false);
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack+1, maxLocals);
    }
}