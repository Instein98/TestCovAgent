import os
import time
import shutil
import subprocess as sp
from datetime import datetime
import traceback

covAgentProjPath = os.path.abspath('..')
d4jProjPath = '/home/yicheng/research/apr/d4jProj/'
coverageOutputDir = os.path.abspath('covResult/')
javaagentJarPath = os.path.join(covAgentProjPath, 'target', 'test-cov-1.0-SNAPSHOT.jar')

processPool = []  # store (process, pid, bid)
maxProcessNum = 16

# print(os.path.abspath('..'))

# sp.run("mvn clean package", shell=True, check=True, cwd=covAgentProjPath)

def strInFile(string: str, path: str):
    with open(path) as file:
        for line in file:
            if string in line:
                return True
    return False

def getSetOfExpectedFailingTest(pid, bid):
    process = sp.Popen('defects4j info -p {} -b {}'.format(pid, bid),
                       shell=True, stderr=sp.PIPE, stdout=sp.PIPE, universal_newlines=True)
    stdout, _ = process.communicate()
    lines = stdout.strip().split('\n')
    start = False
    res = set()
    for line in lines:
        if 'Root cause in triggering tests:' in line:
            start = True
        elif '------------------------------------------------------' in line and start == True:
            start = False
            break
        elif line.startswith(' - ') and start == True:
            tmp = line[3:]
            res.add(tmp)
    return res

def warn(msg: str):
    print('[WARNING]({}) {}'.format(datetime.now().strftime('%Y/%m/%d %H:%M:%S'), msg))

def log(msg: str):
    print('[INFO]({}) {}'.format(datetime.now().strftime('%Y/%m/%d %H:%M:%S'), msg))

def checkFailingTests(projPath: str, pid: str, bid: str):
    log('checking failing tests for {}-{}'.format(pid, bid))
    expectedFailTestFile = os.path.join(projPath, 'expected_failing_tests')
    # if expected_failing_tests file not found, generate one; otherwise read it
    if not os.path.isfile(expectedFailTestFile):
        setOfExpectedFailing = getSetOfExpectedFailingTest(pid, bid)
        with open(expectedFailTestFile, 'w') as file:
            for test in setOfExpectedFailing:
                file.write(test + "\n")
    else:
        setOfExpectedFailing = set()
        with open(expectedFailTestFile, 'r') as file:
            for line in file:
                setOfExpectedFailing.add(line.strip())
    # get the actual failing test set
    actualFailing = set()
    failingTestFile = os.path.join(projPath, 'failing_tests')
    if not os.path.isfile(failingTestFile):
        warn("failing_tests file not found in {}-{}".format(pid, bid))
        return False
    with open(failingTestFile, 'r') as file:
        for line in file:
            if line.startswith('--- '):
                actualFailing.add(line.strip()[4:])
    if actualFailing != setOfExpectedFailing:
        warn('`defects4j test` fails unexpected tests!')
        return False
    else:
        return True

def checkResultValid(pid: str, bid: str):
     # actual failing tests should be consistent with the expected failing tests
    actualFail = os.path.join(coverageOutputDir, pid, bid, 'failing_tests')
    expectedFail = os.path.join(coverageOutputDir, pid, bid, 'expected_failing_tests')
    if not os.path.isfile(actualFail) or not os.path.isfile(expectedFail):
        warn("Invalid cov result of {}-{}: actual or expected failing test file not found".format(pid, bid))
        return False
    actualSet = set()
    with open(actualFail, 'r') as actual:
        for line in actual:
            if line.startswith('--- '):
                actualSet.add(line.strip()[4:])
    expectedSet = set()
    with open(expectedFail, 'r') as expected:
        for line in expected:
            expectedSet.add(line.strip())
    if actualSet != expectedSet:
        warn("Invalid cov result of {}-{}: actual_failing != expected_failing".format(pid, bid))
        return False

    # coverage file should exist and contain valid coverage information
    covFile = os.path.join(coverageOutputDir, pid, bid, "coverage.txt")
    if not os.path.isfile(covFile):
        warn("Invalid cov result of {}-{}: coverage.txt not found".format(pid, bid))
        return False
    if not strInFile(",", covFile):
        warn("Invalid cov result of {}-{}: coverage.txt has no ','".format(pid, bid))
        return False
    if strInFile(":-1", covFile):
        warn("Invalid cov result of {}-{}: coverage.txt has ':-1'".format(pid, bid))
        return False
    if isCovFileRepeatedlyAppend(covFile):
        warn("Invalid cov result of {}-{}: coverage.txt has multiple '1 -> ' lines".format(pid, bid))
        return False
    
    # defects4j test process should not be killed
    d4jLogPath = os.path.join(coverageOutputDir, pid, bid, "d4jTest.log")
    if strInFile("Killed", d4jLogPath):
        warn("Invalid cov result of {}-{}: defects4j test process is 'Killed'!".format(pid, bid))
        return False

    covLogPath = os.path.join(coverageOutputDir, pid, bid, "test-cov.log")
    if not strInFile("testStart:", covLogPath):
        warn("Invalid cov result of {}-{}: No testStart event is captured!".format(pid, bid))
        return False
    if strInFile("has not end when test", covLogPath):
        warn("Invalid cov result of {}-{}: Wrongly handle the nested tests (used the old javaagent)".format(pid, bid))
        return False
        
    return True

def cleanUpInvalidResults():
    for pid in os.listdir(coverageOutputDir):
        pidPath = os.path.join(coverageOutputDir, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            bidPath = os.path.join(pidPath, bid)
            if not os.path.isdir(bidPath):
                continue
            if not checkResultValid(pid, bid):
                print("Removing result of {}-{}.".format(pid, bid))
                shutil.rmtree(bidPath)


def isCovFileRepeatedlyAppend(covFile: str):
    res = sp.check_output('grep "^1 \->" {} | wc -l'.format(covFile), shell=True, universal_newlines=True)
    num = int(res.strip())
    if num == 1:
        return False  # it is not repeatedly appended
    return True  # it is repeatedly appended


def collectCov(projPath: str, pid: str, bid: str):
    # if not os.path.isfile(os.path.join(projPath, 'failing_tests')):
    #     warn("failing_tests file not found in {}-{}".format(pid, bid))
    #     warn('skipping coverage collection for {}-{} due to failing_tests file not found'.format(pid, bid))
    #     return False
    
    if os.path.isfile(os.path.join(coverageOutputDir, pid, bid, "coverage.txt")):
        log("Cov file of {}-{} already exists, skipping...".format(pid, bid))
        return False

    outputDirPath = os.path.join(coverageOutputDir, pid, bid)
    if os.path.isdir(outputDirPath): 
        shutil.rmtree(outputDirPath)
    os.makedirs(outputDirPath, exist_ok=True)
    # # check failing tests before the coverage collection
    # succeed = checkFailingTests(projPath, pid, bid)
    # if not succeed:
    #     warn('skipping coverage collection for {}-{} due to mismatched failing tests'.format(pid, bid))
    #     continue

    # start coverage collection

    # poll if the process pool is full
    counter = 0
    while (len(processPool) >= maxProcessNum):
        time.sleep(1)
        idx = counter % len(processPool)
        process, process_pid, process_bid = processPool[idx]
        processFinished = handleProcess(process, idx, process_pid, process_bid)
        # if the process is finished, the process at idx will be replaced by another process, so counter does not need to change
        if processFinished:
            continue
        else:
            counter += 1
    
    d4jTestLogPath = os.path.join(coverageOutputDir, pid, bid, 'd4jTest.log')
    # if os.path.isfile(d4jTestLogPath):
    #     os.remove(d4jTestLogPath)
    # create a subprocess once get a chance
    process = sp.Popen("_JAVA_OPTIONS='-Xbootclasspath/a:{0} -javaagent:{0}=d4jPid={1};patchAnt=true' time defects4j test > {2} 2>&1".format(javaagentJarPath, pid, d4jTestLogPath), shell=True, cwd=projPath)
    log('Starting coverage collection for {}-{}'.format(pid, bid))
    processPool.append((process, pid, bid))
    return True


def handleProcess(process, idx: int, process_pid: str, process_bid: str):
    exitCode = process.poll()
    if exitCode is None:
        return False  # returning False means process is not finished
    else:
        outputDir = os.path.join(coverageOutputDir, process_pid, process_bid)
        projDir = os.path.join(d4jProjPath, process_pid, process_bid)
        try:
            shutil.copy(os.path.join(projDir, 'coverage.txt'), outputDir)
            shutil.copy(os.path.join(projDir, 'test-cov.log'), outputDir)
            shutil.copy(os.path.join(projDir, 'failing_tests'), outputDir)
            shutil.copy(os.path.join(projDir, 'expected_failing_tests'), outputDir)
            shutil.copy(os.path.join(projDir, 'all_tests'), outputDir)
        except:
            traceback.print_exc()

        log('Finished coverage collection for {}-{}, exitCode: {}'.format(process_pid, process_bid, exitCode))
        del processPool[idx]

        generatedCovFile = os.path.join(coverageOutputDir, process_pid, process_bid, 'coverage.txt')
        if exitCode != 0:
            warn('d4j test with coverage FAIL for {}-{}'.format(process_pid, process_bid))
            if os.path.isfile(generatedCovFile):
                os.remove(generatedCovFile)
            return True  # returning True means process is finished and the output file is checked and handled.
        else:
            # check the failing tests again, it will warn if not consistent
            succeed = checkResultValid(process_pid, process_bid)
            if not succeed and os.path.isfile(generatedCovFile):
                os.remove(generatedCovFile)
            return True  # returning True means process is finished and the output file is checked and handled.


def waitProcessPoolFinish():
    counter = 0
    while (len(processPool) > 0):
        time.sleep(1)
        idx = counter % len(processPool)
        process, process_pid, process_bid = processPool[idx]
        processFinished = handleProcess(process, idx, process_pid, process_bid)
        # if the process is finished, the process at idx will be replaced by another process, so counter does not need to change
        if processFinished:
            continue
        else:
            counter += 1


def main():
    # for pid in os.listdir(d4jProjPath):
    for pid in [ 'Chart', 'Mockito', 'Time', 'Lang', 'Math', 'Closure' ]:
        pidPath = os.path.join(d4jProjPath, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            projPath = os.path.join(pidPath, bid)
            if not os.path.isdir(projPath):
                continue
            collectCov(projPath, pid, bid)
    waitProcessPoolFinish()
   

if __name__ == '__main__':
    cleanUpInvalidResults()
    main()
    # collectCov('/home/yicheng/research/apr/d4jProj/Math/78', 'Math', '78')
    # waitProcessPoolFinish()
        