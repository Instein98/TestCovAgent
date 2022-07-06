import os
import time
import shutil
import subprocess as sp
from datetime import datetime

d4jHome = '/home/yicheng/research/apr/experiments/defects4j/'
covAgentProjPath = os.path.abspath('..')
d4jProjPath = '/home/yicheng/research/apr/d4jProj/'
coverageOutputDir = os.path.abspath('covResult/')
javaagentJarPath = '/home/yicheng/research/apr/testCovAgent/target/test-cov-1.0-SNAPSHOT.jar'

processPool = []  # store (process, pid, bid)
maxProcessNum = 32

# print(os.path.abspath('..'))

# sp.run("mvn clean package", shell=True, check=True, cwd=covAgentProjPath)

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
    with open(covFile, 'r') as cov:
        if ',' not in cov.read():
            warn("Invalid cov result of {}-{}: coverage.txt has no ','".format(pid, bid))
            return False


def cleanUpInvalidResults():
    for pid in os.listdir(coverageOutputDir):
        pidPath = os.path.join(coverageOutputDir, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            bidPath = os.path.join(pidPath, bid)
            if not os.path.isdir(bidPath):
                continue

            # actual failing tests should be consistent with the expected failing tests
            actualFail = os.path.join(d4jProjPath, pid, bid, 'failing_tests')
            expectedFail = os.path.join(d4jProjPath, pid, bid, 'expected_failing_tests')
            if not os.path.isfile(actualFail) or not os.path.isfile(expectedFail):
                print("Remove result of {}-{}: actual or expected failing test file not found".format(pid, bid))
                shutil.rmtree(bidPath)
                continue
            else:
                shutil.copy(actualFail, bidPath)
                shutil.copy(expectedFail, bidPath)
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
                print("Remove result of {}-{}: actual_failing != expected_failing".format(pid, bid))
                shutil.rmtree(bidPath)
                continue

            # coverage file should exist and contain valid coverage information
            covFile = os.path.join(bidPath, "coverage.txt")
            if not os.path.isfile(covFile):
                print("Remove result of {}-{}: coverage.txt not found".format(pid, bid))
                shutil.rmtree(bidPath)
                continue
            with open(covFile, 'r') as cov:
                if ',' not in cov.read():
                    print("Remove result of {}-{}: coverage.txt has no ','".format(pid, bid))
                    shutil.rmtree(bidPath)
                    continue


def main():
    for pid in os.listdir(d4jProjPath):
        pidPath = os.path.join(d4jProjPath, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            projPath = os.path.join(pidPath, bid)
            if not os.path.isdir(projPath):
                continue
            if not os.path.isfile(os.path.join(projPath, 'failing_tests')):
                warn("failing_tests file not found in {}-{}".format(pid, bid))
                warn('skipping coverage collection for {}-{} due to failing_tests file not found'.format(pid, bid))
                continue
            os.makedirs(os.path.join(coverageOutputDir, pid, bid), exist_ok=True)
            
            if os.path.isfile(os.path.join(coverageOutputDir, pid, bid, "coverage.txt")):
                log("Cov file of {}-{} already exists, skipping...".format(pid, bid))
                continue

            # # check failing tests before the coverage collection
            # succeed = checkFailingTests(projPath, pid, bid)
            # if not succeed:
            #     warn('skipping coverage collection for {}-{} due to mismatched failing tests'.format(pid, bid))
            #     continue

            # start coverage collection

            # poll if the process pool is full
            counter = 0
            while (len(processPool) >= maxProcessNum):
                time.sleep(2)
                idx = counter % len(processPool)
                process, process_pid, process_bid = processPool[idx]
                exitCode = process.poll()
                if exitCode is None:
                    counter += 1
                    continue
                else:
                    log('Finished coverage collection for {}-{}'.format(process_pid, process_bid))
                    del processPool[idx]

                    generatedCovFile = os.path.join(coverageOutputDir, process_pid, process_bid, 'coverage.txt')
                    if exitCode != 0:
                        warn('d4j test with coverage FAIL for {}-{}'.format(process_pid, process_bid))
                        if os.path.isfile(generatedCovFile):
                            os.remove(generatedCovFile)
                    else:
                        # check the failing tests again, it will warn if not consistent
                        succeed = checkResultValid(process_pid, process_bid)
                        if not succeed and os.path.isfile(generatedCovFile):
                            os.remove(generatedCovFile)
                        continue  # counter should not change
            
            # create a subprocess once get a chance
            process = sp.Popen("rm test-cov.log; _JAVA_OPTIONS='-Xbootclasspath/a:{0} -javaagent:{0}=d4jPid={1};patchAnt=true' time defects4j test > {2} 2>&1; cp coverage.txt test-cov.log failing_tests expected_failing_tests all_tests {3}".format(javaagentJarPath, pid, os.path.join(coverageOutputDir, pid, bid, 'd4jTest.log'), os.path.join(coverageOutputDir, pid, bid)), shell=True, cwd=projPath)
            log('Starting coverage collection for {}-{}'.format(pid, bid))
            processPool.append((process, pid, bid))

if __name__ == '__main__':
    main()
    # cleanUpInvalidResults()
        