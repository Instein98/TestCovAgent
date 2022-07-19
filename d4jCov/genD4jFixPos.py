"""
This script is used to generate the buggy position of defects4j buggy projects by comparing the source code 
of buggy projects and fixed projects.
"""

import os
import re
from pathlib import Path
import subprocess as sp
import utils

d4jBuggyProjDir = '/home/yicheng/apr/d4jProj'
d4jFixProjDir = '/home/yicheng/apr/d4jFixedProj'
diffLog = 'd4j120fixDiff.txt'


def genFixPos(posLog):
    res = ''
    if os.path.isfile(posLog):
        os.remove(posLog)
    with open(posLog, 'a') as output:
        with open(diffLog, 'r') as file:
            curPid = None
            curBid = None
            curJavaFile = None
            for line in file:
                if line.startswith('================'):
                    m = re.match(r'================ (\w+)-(\d+) buggy-fixed ================', line)
                    curPid = m.group(1)
                    curBid = m.group(2)
                    buggyPath = os.path.join(d4jBuggyProjDir, curPid, curBid)
                elif line.startswith('diff -r'):
                    line = line.split()[-1]
                    startIdx = line.index('/{}/'.format(curBid)) + len(curBid) + 2
                    curJavaFile = line[startIdx:]
                elif re.match(r'(\d+)(,\d+)?(a|d|c)(\d+)(,\d+)?', line):
                    m = re.match(r'(\d+)(,\d+)?(a|d|c)(\d+)(,\d+)?', line)
                    if m[3] == 'd' or m[3] == 'c' or m[3] == 'a':
                        if m[2]:
                            start = int(m[1])
                            end = int(m[2][1:])  # ',29'
                            for i in range(start, end+1):
                                output.write('{}_{}@{}@{}\n'.format(curPid, curBid, curJavaFile, str(i)))
                                # output.write(curDotClassName + '@' + str(i) + '\n')
                        else:
                            lineNum = m[1]
                            output.write('{}_{}@{}@{}\n'.format(curPid, curBid, curJavaFile, lineNum))
                            # output.write(curDotClassName + '@' + lineNum + '\n')
    sp.run("sort -o {0} {0}".format(posLog), shell=True)


def genDiffLog():
    if os.path.isfile(diffLog):
        os.remove(diffLog)
    # for pid in os.listdir(d4jBuggyProjDir):
    for pid in ['Lang', 'Closure', 'Chart', 'Math', 'Mockito', 'Time']:
        pidPath = os.path.join(d4jBuggyProjDir, pid)
        if not os.path.isdir(pidPath):
            continue
        for bid in os.listdir(pidPath):
            buggyPath = os.path.join(pidPath, bid)
            if not os.path.isdir(buggyPath):
                continue

            fixedPath = Path(os.path.join(d4jFixProjDir, pid, bid))
            fixedPath.parent.absolute().mkdir(parents=True, exist_ok=True)

            if not fixedPath.exists():
                utils.log("Checking out fixed version of {}-{}".format(pid, bid))
                sp.run("defects4j checkout -p {} -v {}f -w {} 2>/dev/null".format(pid, bid, fixedPath.absolute()), shell=True, check=True)
            
            # start comparison
            utils.log('Comparing {}-{}'.format(pid, bid))
            sourceDir = sp.check_output("defects4j export -p dir.src.classes 2>/dev/null", shell=True, cwd=buggyPath, text=True)
            diffOutput = sp.check_output("diff -r {} {}; exit 0".format(os.path.join(buggyPath, sourceDir), os.path.join(str(fixedPath.resolve()), sourceDir)), shell=True, text=True, encoding='utf-8', errors='ignore')
            with open(diffLog, 'a') as file:
                file.write('================ {}-{} buggy-fixed ================\n'.format(pid, bid))
                file.write(diffOutput + '\n')

if __name__ == '__main__':
    # genDiffLog()
    genFixPos('d4j120fixPos.txt')
