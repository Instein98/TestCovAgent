package org.yicheng.ouyang.test.cov;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/**
 * @author Yicheng Ouyang
 * @Date 6/30/22
 */

public class Premain {

    public static void premain(String options, Instrumentation ins) {
//        System.out.println("******** ToolAgent Premain Start ********\n");
//        try(FileWriter fw = new FileWriter("Premain.log", true);
//            BufferedWriter bw = new BufferedWriter(fw)){
//            bw.write("******** ToolAgent Premain Start ********\n");
//        } catch (Throwable t){
//            t.printStackTrace();
//        }
//        try {
//            ins.appendToBootstrapClassLoaderSearch(
//                    new JarFile("/Users/yicheng/instein/workspace/intellij/TestCoverageAgent/target/test-cov-1.0-SNAPSHOT.jar")
//            );
//            TimeUnit.SECONDS.sleep(20);
//        } catch (Throwable t){
//            t.printStackTrace();
//        }
        parseArgs(options);
        ins.addTransformer(new CoverageTransformer());
        Thread hook = new Thread(CoverageCollector::outputCoverage);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public static void parseArgs(String args){
        if (args == null || args.equals(""))
            return;
        for (String argPair: args.split(";")){
            String[] kv = argPair.split("=");
            String key = kv[0];
            String value = kv[1];
            if (key.equals("d4jPid")){
                CoverageTransformer.setPrefixWhiteList(value);
            } else if (key.equals("instPrefix")){
                // value expected to be slash class name prefix with "," as delimiter
                CoverageTransformer.setInstClassPrefix(value);
            } else if (key.equals("debug")){
                if (value.equals("true") || value.equals("True")){
                    CoverageTransformer.setDebugMode();
                }
            }
        }
    }
}
