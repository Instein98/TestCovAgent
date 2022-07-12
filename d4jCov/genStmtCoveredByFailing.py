import os
import subprocess as sp
from pathlib import Path
from datetime import datetime

pwd = '/home/yicheng/research/apr/testCovAgent/d4jCov/'
d4jProjDir = '/home/yicheng/research/apr/d4jProj/'
covResultDir = os.path.join(pwd, 'covResult/')
tbarPosDir = os.path.join(pwd, 'SuspiciousCodePositions/')

def getCwd(pid, bid):
    return os.path.join(d4jProjDir, pid, bid)

def getExpectedFailingTestsByCommand(pid, bid):
    res = set()
    process = sp.Popen("defects4j export -p tests.trigger", shell=True, stderr=sp.PIPE, stdout=sp.PIPE, cwd=getCwd(pid, bid), universal_newlines=True)
    stdout, _ = process.communicate()
    for string in stdout.strip().split('\n'):
        tmp = string.split('::')
        dotClassName = tmp[0]
        testMethod = tmp[1]
        res.add("{}#{}".format(dotClassName, testMethod))
    if not res:
        err("Failed to get the failed tests list for {}-{}".format(pid, bid))
    return res

def getExpectedFailingTests(pid, bid):
    res = set()
    exFailFile = os.path.join(covResultDir, pid, bid, 'expected_failing_tests')
    if not os.path.isfile(exFailFile):
        return getExpectedFailingTestsByCommand(pid, bid)
    with open(exFailFile, 'r') as file:
        for line in file:
            tmp = line.strip().split('::')
            dotClassName = tmp[0]
            testMethod = tmp[1]
            res.add("{}#{}".format(dotClassName, testMethod))
    if not res:
        err("Failed to ge the failed tests list for {}-{}".format(pid, bid))
    return res

def getTestSourceDirPath(pid, bid):
    projPath = getCwd(pid, bid)
    testDir = sp.check_output("defects4j export -p dir.src.tests 2>/dev/null", shell=True, universal_newlines=True, cwd=projPath)
    return "{}/{}".format(projPath, testDir)

def readCovFile(filePath):
    idxDict = {}
    testDict = {}  # testClass -> {testMethod -> list of idx}
    with open(filePath, 'r') as file:
        for line in file:
            if '->' in line:
                tmp = line.strip().split(' -> ')
                idx = tmp[0]
                element = tmp[1]
                firstColonIdx = element.index(':')
                lastColonIdx = element.rindex(':')
                dotClassName = element[:firstColonIdx].replace("/", ".")
                lineNum = element[lastColonIdx+1:]
                newEleStr = dotClassName + ":" + lineNum
                idxDict[idx] = newEleStr
            elif ',' in line:
                tmp = line.strip().split(', ')
                testName = tmp[0]
                testDotClassName = testName[:testName.index('#')].replace("/", ".")
                testMethodName = testName[testName.index('#')+1:]
                idxList = []
                for idx in tmp[1:]:
                    idxList.append(idx)
                if testDotClassName not in testDict:
                    testDict[testDotClassName] = {}
                    testDict[testDotClassName][testMethodName] = idxList
                else:
                    testDict[testDotClassName][testMethodName] = idxList
    return idxDict, testDict

def isTestClass(testSourcePath, dotClassName):
    slashClassName = dotClassName.replace('.', '/')
    if os.path.exists("{}/{}.java".format(testSourcePath, slashClassName)):
        return True
    else:
        return False

def patchFailingTestSet(pid: str, bid: str, tests: set):
    """
    Need to make some fixes because of test class inheritance, etc
    """
    def replaceClass(oldClName: str, newClName: str):
        removeList = []
        addList = []
        for test in tests:
            delIdx = test.index('#')
            className = test[:delIdx]
            methodName = test[delIdx+1:]
            if className == oldClName:
                removeList.append(test)
                addList.append(newClName + '#' + methodName)
        for tmp in removeList:
            tests.remove(tmp)
        for tmp in addList:
            tests.add(tmp)
    def replaceMethod(oldMethod: str, newMethod: str):
        removeList = []
        addList = []
        for test in tests:
            if test == oldMethod:
                removeList.append(test)
                addList.append(newMethod)
        for tmp in removeList:
            tests.remove(tmp)
        for tmp in addList:
            tests.add(tmp)

    if pid == 'Math':
        if bid == '102':
            replaceClass('org.apache.commons.math.stat.inference.ChiSquareFactoryTest', 'org.apache.commons.math.stat.inference.ChiSquareTestTest')
        elif bid == '101':
            replaceClass('org.apache.commons.math.complex.FrenchComplexFormatTest', 'org.apache.commons.math.complex.ComplexFormatAbstractTest')
            replaceClass('org.apache.commons.math.complex.ComplexFormatTest', 'org.apache.commons.math.complex.ComplexFormatAbstractTest')
        elif bid == '12':
            replaceMethod('org.apache.commons.math3.distribution.GammaDistributionTest#testDistributionClone', 
            'org.apache.commons.math3.distribution.RealDistributionAbstractTest#testDistributionClone')
            replaceMethod('org.apache.commons.math3.distribution.NormalDistributionTest#testDistributionClone', 
            'org.apache.commons.math3.distribution.RealDistributionAbstractTest#testDistributionClone')
            replaceMethod('org.apache.commons.math3.distribution.LogNormalDistributionTest#testDistributionClone', 
            'org.apache.commons.math3.distribution.RealDistributionAbstractTest#testDistributionClone')
        elif bid == '29':
            replaceMethod('org.apache.commons.math3.linear.SparseRealVectorTest#testEbeMultiplySameType', 'org.apache.commons.math3.linear.RealVectorAbstractTest#testEbeMultiplySameType')
            replaceMethod('org.apache.commons.math3.linear.SparseRealVectorTest#testEbeMultiplyMixedTypes', 'org.apache.commons.math3.linear.RealVectorAbstractTest#testEbeMultiplyMixedTypes')
            replaceMethod('org.apache.commons.math3.linear.SparseRealVectorTest#testEbeDivideMixedTypes', 'org.apache.commons.math3.linear.RealVectorAbstractTest#testEbeDivideMixedTypes')
        elif bid == '6':
            replaceMethod('org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizerTest#testGetIterations', 'org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizerAbstractTest#testGetIterations')
            replaceMethod('org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizerTest#testGetIterations', 'org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizerAbstractTest#testGetIterations')
        elif bid == '41':
            replaceMethod('org.apache.commons.math.stat.descriptive.moment.VarianceTest#testEvaluateArraySegmentWeighted', 'org.apache.commons.math.stat.descriptive.UnivariateStatisticAbstractTest#testEvaluateArraySegmentWeighted')
        elif bid == '43':
            replaceClass('org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatisticsTest', 'org.apache.commons.math.stat.descriptive.SummaryStatisticsTest')
        elif bid == '69':
            replaceMethod('org.apache.commons.math.stat.correlation.SpearmansRankCorrelationTest#testPValueNearZero', 'org.apache.commons.math.stat.correlation.PearsonsCorrelationTest#testPValueNearZero')
        elif bid == '22':
            replaceMethod('org.apache.commons.math3.distribution.UniformRealDistributionTest#testIsSupportUpperBoundInclusive', 'org.apache.commons.math3.distribution.RealDistributionAbstractTest#testIsSupportUpperBoundInclusive')
            replaceMethod('org.apache.commons.math3.distribution.FDistributionTest#testIsSupportLowerBoundInclusive', 'org.apache.commons.math3.distribution.RealDistributionAbstractTest#testIsSupportLowerBoundInclusive')
    elif pid == 'Mockito':
        if bid == '12':
            pass  # need rerun???
    elif pid == 'Chart':
        if bid == '4':
            pass  # need rerun???
    elif pid == 'Lang':
        if bid == '9' or bid == '10':
            replaceClass('org.apache.commons.lang3.time.FastDateFormat_ParserTest', 'org.apache.commons.lang3.time.FastDateParserTest')
        elif bid == '57':
            pass  # need rerun???
        elif bid == '8':
            replaceClass('org.apache.commons.lang3.time.FastDateFormat_PrinterTest', 'org.apache.commons.lang3.time.FastDatePrinterTest')
    return tests

def generateTbarCovPos(pid: str, bid: str):
    targetFile = os.path.join(tbarPosDir, pid+"_"+bid, 'Covered.txt')
    if os.path.exists(targetFile):
        print("{} already exists, skipping".format(targetFile))
        return
    print("Generating {}".format(targetFile))
    res = set()
    failedTestSet = getExpectedFailingTests(pid, bid)

    # make some manual patches
    failedTestSet = patchFailingTestSet(pid, bid, failedTestSet)

    covLogPath = os.path.join(covResultDir, pid, bid, 'coverage.txt')
    if not os.path.exists(covLogPath):
        err("{} does not exist, skipping".format(covLogPath))
        return
    testSourcePath = getTestSourceDirPath(pid, bid)
    idxDict, testDict = readCovFile(covLogPath)

    hasProblem = False
    for failingTest in failedTestSet:
        tmp = failingTest.index('#')
        testClass = failingTest[:tmp]
        testMethod = failingTest[tmp+1:]
        if testClass not in testDict:
            err("Test class {} is not in testDict for {}-{}".format(testClass, pid, bid))
            # return
            hasProblem = True
            continue
        if testMethod not in testDict[testClass]:
            err("Test method {} is not in testDict[{}] for {}-{}".format(testMethod, testClass, pid, bid))
            # return
            hasProblem = True
            continue
        for idx in testDict[testClass][testMethod]:
            ele = idxDict[idx]
            dotClassName = ele[:ele.index(':')]
            if isTestClass(testSourcePath, dotClassName):
                continue
            res.add(ele.replace(':', '@'))
    if hasProblem:
        return
    Path(targetFile).parent.absolute().mkdir(parents=True, exist_ok=True)
    with open(targetFile, 'w') as file:
        for location in res:
            file.write(location + "\n")
    sp.run("sort -o {0} {0}".format(targetFile), shell=True, check=True)

def main():
    for pid in os.listdir(covResultDir):
        pidPath = os.path.join(covResultDir, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            bidPath = os.path.join(pidPath, bid)
            if not os.path.isdir(bidPath):
                continue
            generateTbarCovPos(pid, bid)

def err(msg: str):
    print('[ERROR]({}) {}'.format(datetime.now().strftime('%Y/%m/%d %H:%M:%S'), msg))

def warn(msg: str):
    print('[WARNING]({}) {}'.format(datetime.now().strftime('%Y/%m/%d %H:%M:%S'), msg))

def log(msg: str):
    print('[INFO]({}) {}'.format(datetime.now().strftime('%Y/%m/%d %H:%M:%S'), msg))

if __name__ == '__main__':
    main()
    # generateTbarCovPos('Math', '102')
