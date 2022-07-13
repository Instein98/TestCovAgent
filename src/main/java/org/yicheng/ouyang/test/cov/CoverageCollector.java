package org.yicheng.ouyang.test.cov;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * @author Yicheng Ouyang
 * @Date 7/1/22
 */

public class CoverageCollector {

    private static String logPath = "./test-cov.log";
    private static String outputPath = "./coverage.txt";

    // bind each covered line to a unique id for the final output coverage file
    private static int nextLineId = 1;
    private static HashMap<String, Integer> lineToIdMap = new HashMap<>();
    private static HashMap<Integer, String> idToLineMap = new LinkedHashMap<>();

    // test -> set of id of covered lines
    private static HashMap<String, Set<Integer>> covMap = new HashMap<>();

    // indicate whether it is during the test execution
    private static String currentTestMethodId;

    static {
        Thread hook = new Thread(CoverageCollector::outputCoverage);
        Runtime.getRuntime().addShutdownHook(hook);
//        log("CoverageCollector: " + System.getProperty("sun.boot.class.path"), null);
        try{
            Files.deleteIfExists(new File(outputPath).toPath());
        } catch (Throwable t){
            logStackTrace(t);
        }
    }

    public static void reportCoverageRange(String className, String methodName, int start, int end){
        for (int i = start; i < end; i++){
            reportCoverage(className, methodName, i);
        }
    }

    public static void reportCoverage(String className, String methodName, int lineNum){
        synchronized (covMap) {
            // record coverage only during test execution
            if (currentTestMethodId != null){
                String line = className + ":" + methodName + ":" + lineNum;
                int lineId;
                if (! lineToIdMap.containsKey(line)){
                    lineId = nextLineId++;
                    lineToIdMap.put(line, lineId);
                    idToLineMap.put(lineId, line);
                } else {
                    lineId = lineToIdMap.get(line);
                }

                Set<Integer> coveredLineIds;
                if (! covMap.containsKey(currentTestMethodId)){
                    coveredLineIds = new HashSet<>();
                    covMap.put(currentTestMethodId, coveredLineIds);
                } else {
                    coveredLineIds = covMap.get(currentTestMethodId);
                }
                coveredLineIds.add(lineId);
            }
        }
    }

    public static void d4jStartTest(String testId){
        int lbIdx = testId.indexOf('(');
        int rbIdx = testId.indexOf(')');
        String testMethodName = testId.substring(0, lbIdx);
        String testClassName = testId.substring(lbIdx+1, rbIdx);
        testStart(testClassName, testMethodName);
    }

    public static void d4jEndTest(String testId){
        int lbIdx = testId.indexOf('(');
        int rbIdx = testId.indexOf(')');
        String testMethodName = testId.substring(0, lbIdx);
        String testClassName = testId.substring(lbIdx+1, rbIdx);
        testEnd(testClassName, testMethodName);
    }

    public static void testStart(String testClassName, String testMethodName){
        synchronized (covMap) {
            String startingTest = testClassName + "#" + testMethodName;
            // for nested tests, only the coverage of the outer test will be recorded
            if (currentTestMethodId != null) {
                warn(String.format("Test %s seems to invoke another test %s", currentTestMethodId, startingTest));
                return;
            } else {
                log("testStart: " + startingTest, null);
                currentTestMethodId = startingTest;
                if (covMap.containsKey(currentTestMethodId)) {
                    warn(currentTestMethodId + " is invoked more than once! Coverage of different execution will be collected separately.");
                    int idx = 1;
                    while (covMap.containsKey(String.format("%s[%d]", startingTest, idx))) {
                        idx++;
                    }
                    currentTestMethodId = String.format("%s[%d]", startingTest, idx);
                }
            }
        }
    }

    public static void testEnd(String testClassName, String testMethodName){
        synchronized (covMap) {
//        log("testEnd: " + currentTestMethodId, null);
            String endingTest = testClassName + "#" + testMethodName;
            if (currentTestMethodId.equals(endingTest)) {
                currentTestMethodId = null;
            } else if (currentTestMethodId.contains("[") && currentTestMethodId.startsWith(endingTest)) {
                String startingTest = currentTestMethodId.substring(0, currentTestMethodId.indexOf('['));
                if (startingTest.equals(endingTest)) {
                    currentTestMethodId = null;
                }
            } else {
                warn(String.format("Test %s's inner test %s ends", currentTestMethodId, endingTest));
            }
        }
    }

    private static void outputCoverage(){
        try(FileWriter fw = new FileWriter(outputPath, true);
            BufferedWriter bw = new BufferedWriter(fw)){
            // print the ids and the covered code elements (statements)
            for (int id: idToLineMap.keySet()){
                bw.write(String.format("%d -> %s\n", id, idToLineMap.get(id)));
            }
            // print the tests and their covered statement ids
            for (String test: covMap.keySet()){
                bw.write(test);
                for (int id: covMap.get(test)){
                    bw.write(", " + id);
                }
                bw.write("\n");
            }
        } catch (Throwable t){
            logStackTrace(t);
        }
    }

    private static void err(String content){
        log(content, "ERROR");
    }
    private static void warn(String content){
        log(content, "WARNING");
    }
    private static void log(String content, String level){
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
    private static void logStackTrace(Throwable throwable){
        try(FileOutputStream fos = new FileOutputStream(logPath, true);
                PrintStream ps = new PrintStream(fos)){
            throwable.printStackTrace(ps);
        } catch (Throwable t){
            t.printStackTrace();
        }
    }
}
