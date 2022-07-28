"""
When collecting coverage for some projects of Lang, it will fail more tests because some of its tests are time sensitive.
This script tries to 
"""

import os
import subprocess as sp

pid = 'Lang'
bidList = [1, 3, 4, 5, 6, 7]
d4jProjPath = '/home/yicheng/research/apr/d4jProj/'

testTimeBound = "2109585377"
poolAwaitTimeBound = "1200"

def showSource(projPath):
    sp.run('find . -name FastDateFormatTest.java -exec grep ", TimeUnit.SECONDS)" {} +', shell=True, cwd=projPath)
    sp.run('find . -name HashSetvBitSetTest.java -exec grep "timeDiff <= " {} +', shell=True, cwd=projPath)

def backupSource():
    for bid in bidList:
        projPath = os.path.join(d4jProjPath, pid, str(bid))
        sp.run('path=`find . -name HashSetvBitSetTest.java`; if [ ! -f "$path".bak ]; then cp "$path" "$path".bak; fi ;', shell=True, cwd=projPath)
        sp.run('path=`find . -name FastDateFormatTest.java`; if [ ! -f "$path".bak ]; then cp "$path" "$path".bak; fi ;', shell=True, cwd=projPath)
        # showSource(projPath)

def patchSource():
    for bid in bidList:
        projPath = os.path.join(d4jProjPath, pid, str(bid))
        sp.run("find . -name HashSetvBitSetTest.java -exec sed -i 's/timeDiff <= 0/timeDiff <= %s/' {} +" % (testTimeBound), shell=True, cwd=projPath)
        sp.run("find . -name FastDateFormatTest.java -exec sed -i 's/(20, TimeUnit.SECONDS)/(%s, TimeUnit.SECONDS)/' {} +" % (poolAwaitTimeBound), shell=True, cwd=projPath)
        showSource(projPath)

def revertSource():
    for bid in bidList:
        projPath = os.path.join(d4jProjPath, pid, str(bid))
        sp.run('path=`find . -name HashSetvBitSetTest.java`; cp "$path".bak "$path" ;', shell=True, cwd=projPath)
        sp.run('path=`find . -name FastDateFormatTest.java`; cp "$path".bak "$path" ;', shell=True, cwd=projPath)
        showSource(projPath)

if __name__ == '__main__':
    # backupSource()
    # patchSource()
    revertSource()