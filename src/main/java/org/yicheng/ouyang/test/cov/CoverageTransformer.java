package org.yicheng.ouyang.test.cov;

import com.google.common.collect.Sets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * @author Yicheng Ouyang
 * @Date 6/30/22
 */

public class CoverageTransformer implements ClassFileTransformer {

    public static int ASM_VERSION = ASM9;
    private static boolean debug = false;
    private static boolean patchAnt = false;
    public static String logPath = "./test-cov.log";
    private static String verifyLogPath = "./class-verify.log";

    static {
        new File(logPath).delete();
        new File(verifyLogPath).delete();
    }

    Set<String> PREFIX_BLACK_LIST = Sets.newHashSet(
            "org/yicheng/ouyang/test/cov",
            "org/springframework",
            "com/fasterxml",
            "java",
            "jdk",
            "sun",
            "com/sun",
            "org/apache/catalina",
            "org/apache",
            "org/hibernate/validator",
            "javax"
    );

    private static Set<String> PREFIX_WHITE_LIST;

    public static void setPrefixWhiteList(String d4jProjPid){
        if (d4jProjPid.equals("Chart")){
            PREFIX_WHITE_LIST = Sets.newHashSet("org/jfree");
        } else if (d4jProjPid.equals("Lang")) {
            PREFIX_WHITE_LIST = Sets.newHashSet("org/apache/commons/lang");
        } else if (d4jProjPid.equals("Time")) {
            PREFIX_WHITE_LIST = Sets.newHashSet("org/joda/");
        } else if (d4jProjPid.equals("Math")) {
            PREFIX_WHITE_LIST = Sets.newHashSet("org/apache/commons/math");
        } else if (d4jProjPid.equals("Mockito")){
            PREFIX_WHITE_LIST = Sets.newHashSet("org/mockito", "org/concurrentmockito");
        } else if (d4jProjPid.equals("Closure")){
            PREFIX_WHITE_LIST = Sets.newHashSet("com/google");
        } else {
            PREFIX_WHITE_LIST = Sets.newHashSet(
                    "org/jfree",  // Chart
                    "org/apache/commons/lang",  // Lang
                    "org/joda/",  // Time
                    "org/mockito",
                    "org/concurrentmockito",  // Mockito
                    "com/google"  // Closure
            );
        }
    }

    public static void setInstClassPrefix(String prefixList){
        String[] tmp = prefixList.trim().split(",");
        PREFIX_WHITE_LIST = Sets.newHashSet(tmp);
    }

    public static void setDebugMode(){
        debug = true;
    }

    public static void setPatchAnt(){
        patchAnt = true;
    }

    @Override
    public byte[] transform(ClassLoader loader, String slashClassName, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        byte[] result = classfileBuffer;
//        if (slashClassName.startsWith("org/yicheng/ouyang"))
//            log(String.format("%s is loaded by %s", slashClassName, loader.getClass().getName()), null);
        if (slashClassName == null){
            return result;
        }

        if (PREFIX_WHITE_LIST == null){
            for (String prefix: PREFIX_BLACK_LIST){
                if (slashClassName.startsWith(prefix)){
                    return result;
                }
            }
        } else {
            if (patchAnt) PREFIX_WHITE_LIST.add("org/apache/tools/ant/taskdefs/ExecuteJava");
            boolean matched = false;
            for (String prefix: PREFIX_WHITE_LIST){
                if (slashClassName.startsWith(prefix)){
                    matched = true;
                    break;
                }
            }
            if (!matched){
                return result;
            }
        }

        try {
            if (debug) write(slashClassName + ".class.before", result);
            // Start instrumentation
//            log("Instrumenting " + slashClassName, null);
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 0);  // use no COMPUTE_FRAME or COMPUTE_MAX
            ClassVisitor cv = new CoverageClassVisitor(cw, slashClassName, loader, getClassVersion(cr));
            if (patchAnt && slashClassName.equals("org/apache/tools/ant/taskdefs/ExecuteJava")){
                cv = new AntPatchClassVisitor(cv);
            }
            cr.accept(cv, 0);
            result = cw.toByteArray();
            if (debug) {
                write(slashClassName + ".class", result);
//                try{
//                    CheckClassAdapter.verify(
//                            new ClassReader(result),
//                            loader,
//                            false,
//                            new PrintWriter(new FileOutputStream(verifyLogPath, true))
//                    );
//                } catch (Throwable t){
//                    logStackTrace(t, verifyLogPath);
//                }
            }
        } catch (Throwable t){
            logStackTrace(t, logPath);
            t.printStackTrace();
        }
        return result;
    }

    public static void write(String fileName, byte[] bytes) {
        String path = System.getProperty("user.home") + "/agentLogs/testCov/" + fileName;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    File file = new File(path);
                    if (!file.exists()) {
                        file.getParentFile().mkdirs();
                    }
                    Files.write(Paths.get(path), bytes);
                } catch (Throwable t){
                    t.printStackTrace();
                }
            }
        }).start();
    }

    public static int getClassVersion(ClassReader cr) {
        return cr.readUnsignedShort(6);
    }

    public static void err(String content){
        log(content, "ERROR");
    }

    public static void warn(String content){
        log(content, "WARNING");
    }

    public static void log(String content, String level){
        try(FileWriter fw = new FileWriter(logPath, true);
            BufferedWriter bw = new BufferedWriter(fw)){
            SimpleDateFormat formatter= new SimpleDateFormat("yy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            bw.write(String.format("%s(%s) %s\n", level==null ? "" : "["+level+"]",
                    formatter.format(date), content));
        } catch (Throwable t){
            t.printStackTrace();
        }
    }

    public static void logStackTrace(Throwable throwable, String path){
        try(FileOutputStream fos = new FileOutputStream(path, true);
            PrintStream ps = new PrintStream(fos)){
            throwable.printStackTrace(ps);
        } catch (Throwable t){
            t.printStackTrace();
        }
    }
}
