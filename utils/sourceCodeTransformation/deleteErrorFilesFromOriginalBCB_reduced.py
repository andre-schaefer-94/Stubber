import os
import sys

if __name__ == '__main__':
    errorFiles=set()
    with open(sys.argv[1]) as inputFile:
        line = inputFile.readline()
        while line:
            line=line.rstrip()
            errorFiles.add(line)
            line = inputFile.readline()
    count=0
    filesToDelete=[]
    for root, subdirs, files in os.walk(sys.argv[2]):
        splittedRoot=root.split("/")
        subdir=splittedRoot[len(splittedRoot)-1]
        for f in files:
            print(f)
            if (subdir+","+f in errorFiles):
                print(f)
                count=count+1
                filesToDelete.append(root+"/"+f)
    print(str(count))
    for file in filesToDelete:
        os.remove(file)
