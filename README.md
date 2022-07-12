# TestCovAgent
A javaagent to collect the statement coverage for JUnit tests

## Basic Idea

### Test Method Duration

To collect coverage for different test methods, it is important to identify 
- what are the test methods 
- when do the test methods start and end

For the first question, one can refer to the JUnit documents. For the second question, currently the javaagent will insert `testStart` method invocation before the first instruction of the **test method** and insert `testEnd` method invocation before all kinds of `return` instructions. Also, in case the test method throw `Throwable`, a big try-catch block is inserted to catch the `Throwable`, call `testEnd` and re-throw the `Throwable`.

Consequently, the javaagent can not record the coverage before and after the test method execution, such as @Before, @After. It can be a problem for projects like Mockito-12 in defects4j because all its failing tests are failed before executing the test method and the javaagent can collect no coverage.

### Statement Coverage Collection

The javaagent insert invocation of method `reportCoverage` to collect the covered lines. Instead of inserting such invocation before each instruction executed, it will insert only when the line number is changed.

## Pitfalls

### Nested Tests

Some tests will run other tests. The coverage of the inner tests will be counted as the coverage of the outer tests.

### Test Class Inheritance

Let's say test class B extends test class A, and test method m is located only in A. When executing B#m, it is actually executing A#m. But the javaagent can not distinguish B#m and A#m, it only knows A#m is executed twice. This can cause problems because the test identification can not be found in the collected coverage sometimes (e.g., no B#m found in the collected coverage).

### Parameterized Tests

The javaagent will only know that the test method is executed several times. Currently, the javaagent will add an index to distinguish different executions and collect coverage for them separately.

### @Before, @After, etc

The javaagent can not collect the coverage for the methods executed before or after the test methods for now.

### Defects4j Ant Class Loader

When defects4j is compiling the tests, it will use ant to execute some java tasks sometimes. But the ant class loader used to execute that java task is not a typical class loader, it will not consult parent class loaders for the classes unknown. It will throw `ClassNotFoundException` for the class `CoverageCollector`. See https://github.com/apache/ant/blob/master/src/main/org/apache/tools/ant/taskdefs/ExecuteJava.java#L135 and https://ant.apache.org/manual/api/org/apache/tools/ant/AntClassLoader.html#setIsolated(boolean). So the javaagent instrument the `ExecuteJava` class to add its jar to the classpath of the ant class loader.

### StackMapTable Format Error (Instrumentation)

See https://stackoverflow.com/q/72877465/11495796