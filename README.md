# TestCovAgent

A javaagent to collect the statement coverage for JUnit tests (for Java 8).

## Usage

Build the javaagent jar file with `mvn package` or `mvn install`.

### Javaagent Parameters

| Parameter Key | Values | Default Value | Description |
| --- | --- |--- | --- |
| d4jPid | Ids of D4J Project | null | e.g., "Chart"
| patchAnt | "true" or "false" | "true" | Add this jar to the class path of the ant class loader. Leave it "true" when collecting coverage for `defects4j test`.
| d4jMode | "true" or "false" | "false" | Use defects4j's API to determine the start/end of tests. Set it to "true" when collecting coverage for `defects4j test`.
| contCov | "true" or "false" | "true" | Record continuous line numbers between the line numbers in the class LineNumberTable. See [Line Numbers in LineNumberTable](#line-numbers-in-linenumbertable).
| debug | "true" or "false" | "false" | Dump the class files before and after instrumentation to `~/agentLogs/testCov/`.

### Normal Java Program Coverage

Add the following JVM option to the JVM you started.

```
-javaagent:"$Path_To_Jar"=parameter1=value1;parameter2=value2;...
```

For examples, 

```
java -javaagent:"$Path_To_Jar"=contCov=true;debug=true -cp .:junit-4.12.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore TestClassName
```

```
mvn test -DargLine='-javaagent:"$Path_To_Jar"=contCov=true;debug=true'
```

### Defects4j Project Coverage

When collecting test coverage for `defects4j test`, use `_JAVA_OPTIONS` to add the javaagent VM option. `_JAVA_OPTIONS` is used to pass options to any JVM process started on your system.

For example,

```
cd $d4jProjDir/Mockito/12
export _JAVA_OPTIONS="-javaagent:$Path_To_Jar=d4jPid=Mockito;d4jMode=true;debug=true"
defects4j test
```

Or one line command:
```
cd $d4jProjDir/Mockito/12
_JAVA_OPTIONS="-javaagent:$Path_To_Jar=d4jPid=Mockito;d4jMode=true;debug=true" defects4j test
```

### Bug Shooting

If the JVM complains `NoClassDefFoundError`, it might because the javaagent failed to add its jar to the search path of the bootstrap class loader. In that case, please add an extra JVM option `-Xbootclasspath/a:"$Path_To_Jar"`.

### Javaagent Output

`coverage.txt` and `test-cov.log` will be generated at the project directory if the javaagent successfully records coverage of some tests.

`coverage.txt`: Each line of the first part of `coverage.txt` follows the format 'index -> code element', the second part follows the format 'testName, index1, index2, ...'

`test-cov.log`: Log file recording the exceptions/errors, the start event of tests, suspicious behavior such as the nested tests execution.

## Basic Idea

### Test Method Duration

#### When `d4jMode` is set to "false"
To collect coverage for different test methods, it is important to identify 
- what are the test methods 
- when do the test methods start and end

For the first question, one can refer to the JUnit documents. For the second question, currently the javaagent will insert `testStart` method invocation before the first instruction of the **test method** and insert `testEnd` method invocation before all kinds of `return` instructions. Also, in case the test method throw `Throwable`, a big try-catch block is inserted to catch the `Throwable`, call `testEnd` and re-throw the `Throwable`.

Consequently, the javaagent can not record the coverage before and after the test method execution, such as @Before, @After. It can be a problem for projects like Mockito-12 in defects4j because all its failing tests are failed before executing the test method and the javaagent can collect no coverage.

#### When `d4jMode` is set to "true"
The javaagent will instrument the class of defects4j `edu/washington/cs/mut/testrunner/Formatter`, whose methods `startTest`/`endTest` directly tell when a test starts/ends, to precisely track the test start/end events.

### Statement Coverage Collection

The javaagent insert invocation of method `reportCoverage` to collect the covered lines. Instead of inserting such invocation before each instruction executed, it will insert only when the line number is changed. However, such approach can be vulnerable because **Some line numbers of source code may not appear in the class LineNumberTable**. See [Line Numbers in LineNumberTable](#line-numbers-in-linenumbertable).

## Pitfalls

### Nested Tests

Some tests will run other tests. The coverage of the inner tests will be counted as the coverage of the outer tests.

### Test Class Inheritance

Let's say test class B extends test class A, and test method m is located only in A. When executing B#m, it is actually executing A#m. But the javaagent can not distinguish B#m and A#m (when `d4jMode` is false), it only knows A#m is executed twice. This can cause problems because the test identification can not be found in the collected coverage sometimes (e.g., no B#m found in the collected coverage). Such issue can be avoided if using `d4jMode`.

### Parameterized Tests

The javaagent will only know that the test method is executed several times. Currently, the javaagent will add an index to distinguish different executions and collect coverage for them separately. Such issue can be avoided if using `d4jMode`.

### @Before, @After, etc

The javaagent can not collect the coverage for the methods executed before or after the test methods for now. Such issue can be avoided if using `d4jMode`.

### Defects4j Ant Class Loader

When defects4j is compiling the tests, it will use ant to execute some java tasks sometimes. But the ant class loader used to execute that java task is not a typical class loader, it will not consult parent class loaders for the classes unknown. It will throw `ClassNotFoundException` for the class `CoverageCollector`. 

See https://github.com/apache/ant/blob/master/src/main/org/apache/tools/ant/taskdefs/ExecuteJava.java#L135 and https://ant.apache.org/manual/api/org/apache/tools/ant/AntClassLoader.html#setIsolated(boolean). 

The javaagent will instrument the `ExecuteJava` class to add its jar to the classpath of the ant class loader when `patchAnt` is set to true.

### Line Numbers in LineNumberTable

The line numbers shown in the LineNumberTable is not continuous. For example, if a statement in source code cross several lines, only the first line may appear in the LineNumberTable. If only recording the line number appears in the LineNumberTable, some executed lines may be missing. In order to mitigate such issue, `contCov` option can record all the continuous line numbers between the current line number and the next line number appearing in the LineNumberTable. However, it can not solve all problems. For example, if the last statement in a method cross several lines, the javaagent can only record it's first line in coverage. Also, using `contCov` may cause empty lines and comment lines appearing in the coverage results.

See https://stackoverflow.com/a/41142251/11495796.

### StackMapTable Format Error (Instrumentation)

See https://stackoverflow.com/q/72877465/11495796