package org.yicheng.ouyang.test.cov;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.yicheng.ouyang.test.cov.CoverageTransformer.ASM_VERSION;
import static org.objectweb.asm.Opcodes.*;

/**
 * @author Yicheng Ouyang
 * @Date 7/5/22
 */

public class AntPatchClassVisitor extends ClassVisitor {

    AntPatchClassVisitor(ClassVisitor classVisitor) {
        super(ASM_VERSION, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("execute") && descriptor.equals("(Lorg/apache/tools/ant/Project;)V")){
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new AntPatchMethodVisitor(mv);
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}

class AntPatchMethodVisitor extends MethodVisitor {
    protected AntPatchMethodVisitor(MethodVisitor methodVisitor) {
        super(ASM_VERSION, methodVisitor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == INVOKEVIRTUAL && owner.equals("org/apache/tools/ant/AntClassLoader")
                && name.equals("setThreadContextLoader") && descriptor.equals("()V")){
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            super.visitVarInsn(ALOAD, 3);
            super.visitTypeInsn(NEW, "java/io/File");
            super.visitInsn(DUP);
            super.visitLdcInsn(Premain.agentJarPath);
            super.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
            super.visitMethodInsn(INVOKEVIRTUAL, "org/apache/tools/ant/AntClassLoader",
                    "addPathComponent", "(Ljava/io/File;)V", false);
        } else{
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
