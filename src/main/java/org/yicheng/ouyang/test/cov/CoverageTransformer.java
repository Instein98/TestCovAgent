package org.yicheng.ouyang.test.cov;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * @author Yicheng Ouyang
 * @Date 6/30/22
 */

public class CoverageTransformer implements ClassFileTransformer {

    public static int ASM_VERSION = ASM9;
    private static boolean debug = false;
    private static boolean patchAnt = true;
    private static boolean d4jMode = false;
    private static boolean contCov = true;
    public static String logPath = "./test-cov.log";
    private static String verifyLogPath = "./class-verify.log";

    private static Set<String> PREFIX_BLACK_LIST = new HashSet<>();
    private static Set<String> PREFIX_WHITE_LIST = new HashSet<>();

    static {
        new File(logPath).delete();
        new File(verifyLogPath).delete();
        PREFIX_BLACK_LIST.add("org/yicheng/ouyang/test/cov");
        PREFIX_BLACK_LIST.add("org/springframework");
        PREFIX_BLACK_LIST.add("com/fasterxml");
        PREFIX_BLACK_LIST.add("java");
        PREFIX_BLACK_LIST.add("jdk");
        PREFIX_BLACK_LIST.add("sun");
        PREFIX_BLACK_LIST.add("com/sun");
        PREFIX_BLACK_LIST.add("org/apache/catalina");
        PREFIX_BLACK_LIST.add("org/apache");
        PREFIX_BLACK_LIST.add("org/hibernate/validator");
        PREFIX_BLACK_LIST.add("javax");
    }

    public static void setPrefixWhiteList(String d4jProjPid){
        if (d4jProjPid.equals("Chart")){
            PREFIX_WHITE_LIST.add("org/jfree");
        } else if (d4jProjPid.equals("Lang")) {
            PREFIX_WHITE_LIST.add("org/apache/commons/lang");
        } else if (d4jProjPid.equals("Time")) {
            PREFIX_WHITE_LIST.add("org/joda/");
        } else if (d4jProjPid.equals("Math")) {
            PREFIX_WHITE_LIST.add("org/apache/commons/math");
        } else if (d4jProjPid.equals("Mockito")){
            PREFIX_WHITE_LIST.add("org/mockito");
            PREFIX_WHITE_LIST.add("org/concurrentmockito");
        } else if (d4jProjPid.equals("Closure")){
            PREFIX_WHITE_LIST.add("com/google");
        } else {
            PREFIX_WHITE_LIST.add("org/jfree");  // Chart
            PREFIX_WHITE_LIST.add("org/apache/commons/lang");  // Lang
            PREFIX_WHITE_LIST.add("org/joda/");  // Time
            PREFIX_WHITE_LIST.add("org/mockito");
            PREFIX_WHITE_LIST.add("org/concurrentmockito");  // Mockito
            PREFIX_WHITE_LIST.add("com/google");  // Closure
        }
    }

//    public static void setInstClassPrefix(String prefixList){
//        String[] tmp = prefixList.trim().split(",");
//        PREFIX_WHITE_LIST.clear();
//        for (String prefix: tmp){
//            PREFIX_WHITE_LIST.add(prefix);
//        }
//    }

    public static void setDebugMode(){
        debug = true;
    }

    public static void setD4jMode(boolean value){
        d4jMode = value;
    }

    public static void setPatchAnt(boolean value){
        patchAnt = value;
    }

    public static void setContCov(boolean value){
        contCov = value;
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

//        log("PREFIX_WHITE_LIST.size(): " + PREFIX_WHITE_LIST.size());
//        for (String prefix: PREFIX_WHITE_LIST){
//            log("   " + prefix);
//        }
        if (PREFIX_WHITE_LIST == null){
            for (String prefix: PREFIX_BLACK_LIST){
                if (slashClassName.startsWith(prefix)){
                    return result;
                }
            }
        } else {
            if (patchAnt) PREFIX_WHITE_LIST.add("org/apache/tools/ant/taskdefs/ExecuteJava");
            if (d4jMode) PREFIX_WHITE_LIST.add("edu/washington/cs/mut/testrunner/Formatter");
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

            Map<String, Map<Integer, Integer>> methodLineNumMap = null;
            if (contCov){
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                methodLineNumMap = new HashMap<>();
                for (MethodNode mn: cn.methods){
                    Set<Integer> lines = new HashSet<>();
                    for (AbstractInsnNode node: mn.instructions){
                        if (node instanceof LineNumberNode){
                            LineNumberNode lineNode = (LineNumberNode) node;
                            int lineNum = lineNode.line;
                            lines.add(lineNum);
                        }
                    }
                    List<Integer> sortedLineList = new ArrayList<>(lines);
                    Collections.sort(sortedLineList);
                    // the key is a line number, the value is the next line number, they form a range.
                    Map<Integer, Integer> nextLineNumMap = new HashMap<>();
                    int lastLineNum = -1;
                    for (int line: sortedLineList){
                        if (lastLineNum < 0){
                            lastLineNum = line;
                            continue;
                        }
                        nextLineNumMap.put(lastLineNum, line);
                        lastLineNum = line;
                    }
                    methodLineNumMap.put(mn.name + "#" + mn.desc, nextLineNumMap);
                }
            }

            File f = new File("trace.log");FileWriter fw = null;BufferedWriter bw = null;
            try{
                 fw= new FileWriter(f);
                 bw= new BufferedWriter(fw);
                for (StackTraceElement element: Thread.currentThread().getStackTrace()){
                    bw.write(element.toString() + "\n");
                }
                bw.close(); fw.close();
            } catch (Throwable t){}


            ClassWriter cw = new ClassWriter(cr, 0);  // use no COMPUTE_FRAME or COMPUTE_MAX
            ClassVisitor cv;
            if (patchAnt && slashClassName.equals("org/apache/tools/ant/taskdefs/ExecuteJava")){
                cv = new AntPatchClassVisitor(cw);
            } else if (d4jMode && slashClassName.equals("edu/washington/cs/mut/testrunner/Formatter")){
                cv = new D4jFormatterClassVisitor(cw);
            } else {
                cv = new CoverageClassVisitor(cw, slashClassName, loader, getClassVersion(cr), methodLineNumMap, d4jMode);
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

    public static void log(String content){
        log(content, null);
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
