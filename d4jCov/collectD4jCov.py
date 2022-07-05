import os
import time
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

def checkFailingTests(projPath: str, pid: str, bid: str):
    print('checking failing tests for {}-{}'.format(pid, bid))
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
            
            # check failing tests before the coverage collection
            succeed = checkFailingTests(projPath, pid, bid)
            if not succeed:
                warn('skipping coverage collection for {}-{} due to mismatched failing tests'.format(pid, bid))
                continue

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
                    print('Finished coverage collection for {}-{}'.format(process_pid, process_bid))
                    del processPool[idx]
                    # check the failing tests again, it will warn if not consistent
                    succeed = checkFailingTests(os.path.join(d4jProjPath, process_pid, process_bid), process_pid, process_bid)
                    continue  # counter should not change
            
            # create a subprocess once get a chance
            process = sp.run("_JAVA_OPTIONS='-Xbootclasspath/a:{0} -javaagent:{0}=d4jPid={1}' time defects4j test > {2} 2>&1; cp coverage.txt test-cov.log {3}".format(javaagentJarPath, pid, os.path.join(coverageOutputDir, pid, bid, 'd4jTest.log'), os.path.join(coverageOutputDir, pid, bid)), shell=True, cwd=projPath)
            print('Starting coverage collection for {}-{}'.format(pid, bid))
            processPool.append((process, pid, bid))

if __name__ == '__main__':
    main()
        