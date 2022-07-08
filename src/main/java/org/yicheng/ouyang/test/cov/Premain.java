package org.yicheng.ouyang.test.cov;

import java.lang.instrument.Instrumentation;
import java.net.JarURLConnection;
import java.net.URL;

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
