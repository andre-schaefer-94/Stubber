import sys

if __name__ == '__main__':
    dictionary=dict()
    if (len(sys.argv)!=4):
        exit()
    with open(sys.argv[1]) as inputFile:
        line = inputFile.readline()
        while line:
            line=line.rstrip()
            splittedLine=line.split(",")
            key=",".join(splittedLine[1:5])
            if (key in dictionary):
                ss=dictionary[key]
                ss=""
            dictionary[key]=",".join(splittedLine[5:7])
            line=inputFile.readline()
    with open(sys.argv[3], "w") as outPutFile:
        with open(sys.argv[2]) as inputFile:
            line = inputFile.readline()
            while line:
                line=line.rstrip()
                splittedLine=line.split(",")
                key1=",".join(splittedLine[0:4])
                key2=",".join(splittedLine[4:8])
                try:
                    splittedLine[2]=str(int(splittedLine[2])-1)
                    splittedLine[6] = str(int(splittedLine[6]) - 1)
                except:
                    line=inputFile.readline()
                    continue
                key11 = ",".join(splittedLine[0:4])
                key22 = ",".join(splittedLine[4:8])
                part1=None
                part2=None
                if (key1 in dictionary):
                    part1=dictionary[key1]
                if part1==None:
                    part1=dictionary[key11]
                if (key2 in dictionary):
                    part2=dictionary[key2]
                if part2==None:
                    part2=dictionary[key22]
                if (part1==None or part2==None):
                    s=""
                write1=",".join(splittedLine[0:2])+","+part1
                write2 = ",".join(splittedLine[4:6]) + "," + part2
                outPutFile.write(write1+","+write2+"\n")
                line=inputFile.readline()