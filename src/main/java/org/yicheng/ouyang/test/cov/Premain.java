package org.yicheng.ouyang.test.cov;

import sun.tools.jar.resources.jar;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.concurrent.TimeUnit;

import static org.yicheng.ouyang.test.cov.CoverageTransformer.log;

/**
 * @author Yicheng Ouyang
 * @Date 6/30/22
 */

public class Premain {

    public static String agentJarPath;

    public static void premain(String options, Instrumentation ins) throws Throwable {
        JarURLConnection connection =
                (JarURLConnection) Premain.class.getResource("Premain.class").openConnection();
        ins.appendToBootstrapClassLoaderSearch(connection.getJarFile());

        URL classUrl = Premain.class.getResource("Premain.class");
        agentJarPath = classUrl.getPath().substring(5, classUrl.getPath().indexOf('!'));

        parseArgs(options);
        ins.addTransformer(new CoverageTransformer());
//        log("Premain: " + System.getProperty("sun.boot.class.path"), null);
    }

    public static void test(){
        File f = new File("xxx");
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
            } else if (key.equals("patchAnt")){
                if (value.equals("true") || value.equals("True")){
                    CoverageTransformer.setPatchAnt();
                }
            }
        }
    }
}
