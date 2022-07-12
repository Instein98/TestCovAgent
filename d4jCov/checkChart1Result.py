import os
import subprocess as sp
import xml.etree.ElementTree as et


myCovResultDir = 'covResult/'
d4jCovResultDir = '/home/jun/APR_FL/coverage/Chart/1/'
outputMyCovDir = '/tmp/myCov/'
outputD4jCovDir = '/tmp/d4jCov/'

pid = 'Chart'
bid = '1'

# generate a coverage file for each test class according to my result
def generateCovFileForEachTestClass():
    covFile = os.path.join(myCovResultDir, pid, bid, 'coverage.txt')
    idxDict, testDict = readCovFile(covFile)
    outputMyCov(idxDict, testDict)

def outputMyCov(idxDict, testDict):
    for testDotClassName in testDict:
        classDir = os.path.join(outputMyCovDir, testDotClassName)
        os.makedirs(classDir, exist_ok=True)
        for testMethodName in testDict[testDotClassName]:
            targetFile = os.path.join(classDir, testMethodName)
            if os.path.isfile(targetFile):
                os.remove(targetFile)
            print('Generating ' + targetFile)
            with open(targetFile, 'a') as file:
                for idx in testDict[testDotClassName][testMethodName]:
                    element = idxDict[idx]
                    file.write(element + '\n')

            # sort the file
            sp.run("sort -o {0} {0}".format(targetFile), shell=True, check=True)

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

def generateD4jCovFileForEachTestClass():
    for dotClassName in os.listdir(d4jCovResultDir):
        classDir = os.path.join(d4jCovResultDir, dotClassName)
        for covXML in os.listdir(classDir):
            elementList = []
            methodName = covXML[:-8]
            xmlPath = os.path.join(classDir, covXML)
            tree = et.parse(xmlPath)
            root = tree.getroot()
            classes = root.findall(".//*classes/class")
            for cl in classes:
                clName = cl.get('name')
                lines = cl.findall(".//*lines/line")
                for line in lines:
                    hitNum = int(line.get('hits'))
                    if hitNum <= 0:
                        continue
                    lineNum = line.get('number')
                    elementList.append(clName + ':' + lineNum)
            
            # write to file
            targetDir = os.path.join(outputD4jCovDir, dotClassName)
            os.makedirs(targetDir, exist_ok=True)
            covFilePath = os.path.join(targetDir, methodName)
            if os.path.isfile(covFilePath):
                os.remove(covFilePath)
            print('D4jCov generating ' + covFilePath)
            with open(covFilePath, 'w') as file:
                for ele in elementList:
                    file.write(ele + "\n")

            sp.run("sort -o {0} {0}".format(covFilePath), shell=True, check=True)

def generateDiffLog(log: str):
    if os.path.isfile(log):
        os.remove(log)
    for dotClassName in os.listdir(d4jCovResultDir):
        classDir = os.path.join(d4jCovResultDir, dotClassName)
        for covXML in os.listdir(classDir):
            elementList = []
            methodName = covXML[:-8]

            myCovFilePath = os.path.join(outputMyCovDir, dotClassName, methodName)
            d4jCovFilePath = os.path.join(outputD4jCovDir, dotClassName, methodName)

            if not os.path.isfile(myCovFilePath) or not os.path.isfile(d4jCovFilePath):
                print("coverage file not found. Please execute method generateCovFileForEachTestClass() and generateD4jCovFileForEachTestClass() first!")

            sp.run('echo "\n\n======= {}#{} =======" >> {}'.format(dotClassName, methodName, log), shell=True)
            sp.run("diff -s {} {} >> {}".format(d4jCovFilePath, myCovFilePath, log), shell=True)


if __name__ == '__main__':
    # generateCovFileForEachTestClass()
    # generateD4jCovFileForEachTestClass()
    generateDiffLog('covDiff.log')
