from datetime import datetime

errorFileList=set()
errorIDList=set()
def getErrorFilesInList():
    filepath = 'input/errorFiles'
    with open(filepath) as fp:
        line = fp.readline()
        while line:
            errorFileList.add(line[0:len(line)-1])
            line = fp.readline()


# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    print("Start time: "+str(datetime.now().strftime('%Y-%m-%d %H:%M:%S'))+"\n\n")
    getErrorFilesInList()
    count=0
    countFalsePositive = 0
    countFunctions=0
    functions=False
    clones=False
    falsePositive=False
    emptyInsert=False
    insertIntoPublicFunctions=False
    insertIntoPublicClones = False
    insertIntoPublicFalsePositive = False
    with open("newSQL.sqlp", "w") as outPutFile:
        with open("input/db-dump.sql") as inputFile:
            line = inputFile.readline()
            while line:
                if (functions and line.startswith('(\'')):
                    splitLine=line.split(',')
                    javaFile=splitLine[0][2:len(splitLine[0])-1]
                    subfolder=splitLine[1][2:len(splitLine[1])-1]
                    if(subfolder+','+javaFile in errorFileList):
                        id=splitLine[4][1:]
                        errorIDList.add(id)
                        countFunctions=countFunctions+1
                    else:
                        if (emptyInsert==False):
                            outPutFile.write(",\n")
                        l=line[0:len(line)-1].rstrip()
                        l=l[0:len(l)-1]
                        outPutFile.write(l)
                        emptyInsert=False
                elif (clones and line.startswith('(')):
                    splitLine=line.split(',')
                    id1 = splitLine[0][1:len(splitLine[0])]
                    id2 = splitLine[1][1:len(splitLine[1])]
                    if(id1 not in errorIDList and id2 not in errorIDList):
                        if (emptyInsert == False):
                            outPutFile.write(",\n")
                        l=line[0:len(line) - 1].rstrip()
                        l = l[0:len(l) - 1]
                        outPutFile.write(l)
                        emptyInsert=False
                    else:
                        count=count+1
                elif (falsePositive and line.startswith('(')):
                    splitLine=line.split(',')
                    id1 = splitLine[0][1:len(splitLine[0])]
                    id2 = splitLine[1][1:len(splitLine[1])]
                    if(id1 not in errorIDList and id2 not in errorIDList):
                        if (emptyInsert == False):
                            outPutFile.write(",\n")
                        l=line[0:len(line) - 1].rstrip()
                        l = l[0:len(l) - 1]
                        outPutFile.write(l)
                        emptyInsert=False
                    else:
                        countFalsePositive=countFalsePositive+1
                elif (line.startswith( 'INSERT INTO \"PUBLIC\".\"FUNCTIONS' )):
                    if (insertIntoPublicFunctions==True and emptyInsert==False):
                        outPutFile.write(";\n")
                    insertIntoPublicFunctions=True
                    functions=True
                    clones=False
                    falsePositive=False
                    if (emptyInsert==False):
                        outPutFile.write(line)
                    #else:
                    #    print("EMPTYINSERT")
                    emptyInsert=True
                elif (line.startswith( 'INSERT INTO \"PUBLIC\".\"CLONES' )):
                    if (insertIntoPublicClones == True and emptyInsert==False):
                        outPutFile.write(";\n")
                    insertIntoPublicClones = True
                    functions=False
                    clones=True
                    falsePositive=False
                    if (emptyInsert==False):
                        outPutFile.write(line)
                    emptyInsert=True
                elif (line.startswith('CREATE INDEX')):
                    if ((insertIntoPublicFalsePositive or insertIntoPublicClones or insertIntoPublicFunctions ) and emptyInsert==False):
                        outPutFile.write(";\n")
                    outPutFile.write(line)
                    insertIntoPublicFalsePositive = False
                    functions=False
                    clones=False
                    falsePositive=False
                    emptyInsert=False
                elif (line.startswith( 'INSERT INTO \"PUBLIC\".\"FALSE_POSITIVES' )):
                    if (insertIntoPublicFalsePositive == True and emptyInsert==False):
                        outPutFile.write(";\n")
                    insertIntoPublicFalsePositive = True
                    functions=False
                    clones=False
                    falsePositive=True
                    if (emptyInsert==False):
                        outPutFile.write(line)
                    emptyInsert=True
                elif (line.startswith('CREATE CACHED TABLE')):
                    emptyInsert = False
                    functions = False
                    clones = False
                    falsePositive = False
                    if (insertIntoPublicFalsePositive or insertIntoPublicClones or insertIntoPublicFunctions ):
                        outPutFile.write(';\n')
                    outPutFile.write(line)
                else:
                    emptyInsert = False
                    functions=False
                    clones=False
                    falsePositive=False
                    outPutFile.write(line)
                line = inputFile.readline()
    print("Anzahl gelöschter Funktionen " + str(countFunctions))
    print("Anzahl gelöschter Klone "+str(count))
    print("Anzahl gelöschter False Positives " + str(countFalsePositive))
    print("End time: "+str(datetime.now().strftime('%Y-%m-%d %H:%M:%S'))+"\n\n")
