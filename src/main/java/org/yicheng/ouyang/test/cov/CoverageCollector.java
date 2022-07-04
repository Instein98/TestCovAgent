package org.yicheng.ouyang.test.cov;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
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

    public static String logPath = "./test-cov.log";
    public static String outputPath = "./coverage.txt";

    // bind each covered line to a unique id for the final output coverage file
    private static int nextLineId = 1;
    private static HashMap<String, Integer> lineToIdMap = new HashMap<>();
    private static HashMap<Integer, String> idToLineMap = new LinkedHashMap<>();

    // test -> set of id of covered lines
    private static HashMap<String, Set<Integer>> covMap = new HashMap<>();

    // indicate whether it is during the test execution
    public static String currentTestMethodId;

    public static synchronized void reportCoverage(String className, String methodName, int lineNum){
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

    public static void testStart(String testClassName, String testMethodName){
        String startingTest = testClassName + "#" + testMethodName;
        if (currentTestMethodId != null){
            warn(String.format("Test %s has not end when test %s starts! Its coverage collection is forced to end.", currentTestMethodId, startingTest));
        }
        log("testStart: " + startingTest, null);
        currentTestMethodId = startingTest;
        if (covMap.containsKey(currentTestMethodId)) {
            warn(currentTestMethodId + " is invoked more than once! Coverage of different execution will be collected separately.");
            int idx = 1;
            while (covMap.containsKey(String.format("%s[%d]", startingTest, idx))){
                idx++;
            }
            currentTestMethodId = String.format("%s[%d]", startingTest, idx);
        }
    }

    public static void testEnd(String testClassName, String testMethodName){
        log("testEnd: " + currentTestMethodId, null);
        currentTestMethodId = null;
    }

    public static void outputCoverage(){
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
            t.printStackTrace();
        }
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
    public static void logStackTrace(Throwable throwable){
        try(FileOutputStream fos = new FileOutputStream(logPath, true);
                PrintStream ps = new PrintStream(fos)){
            throwable.printStackTrace(ps);
        } catch (Throwable t){
            t.printStackTrace();
        }
    }
}