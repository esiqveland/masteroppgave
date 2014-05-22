#!/usr/bin/python

import os
import glob
import sys
import re
import getopt


def save_histogram(myhist, myoutfile):
    myfile = open(myoutfile, "w")
    myfile.write("responsetime, count\n")
    for key, value in myhist.iteritems():
        myfile.write("{},{}\n".format(key,value))
        #for x in xrange(value):
        #    myfile.write("{},1\n".format(key))

def usage():
    print "usage: "+sys.argv[0]+" -i inputdir -b benchmarks -p pattern -o outputfile" 

def main():
    try:
        opts, args = getopt.getopt(sys.argv[1:], "i:b:p:o:", ["help", "output="])
    except getopt.GetoptError as err:
        # print help information and exit:
        print str(err) # will print something like "option -a not recognized"
        usage()
        sys.exit(2)

    inputdir = "eivindmacbook"

    #filestoparse = "nozk/*r99-w1*"
    filestoparse = "zookeeper/*r99-w1*"

    benchmarks = -1

    outputfile = inputdir+"/summary.csv"

    for o, a in opts:
        if o == "-i":
            inputdir = a
            print o, a
        elif o in ("-h", "--help"):
            usage()
            sys.exit()
        elif o in ("-o", "--output"):
            outputfile = a
        elif o in ("-b", "--benchmarks"):
            benchmarks = int(a)
        elif o in ("-p", "--filepattern"):
            filestoparse = a
        else:
            assert False, "unhandled option"


    print "settings:"
    print "inputdir:", inputdir
    print "benchmarks:", benchmarks
    print "filestoparse:", filestoparse
    print "output:", outputfile
    paths = []

    if benchmarks > -1:
        for x in xrange(benchmarks):
            mypath = inputdir+"/"+str(x)+"/"+filestoparse
            paths += glob.glob(mypath)
    else:
        mypath = inputdir+"/"+filestoparse
        paths += glob.glob(mypath)

    print paths

    if len(paths) == 0:
        print "no input files found"
        sys.exit(-1)
    
    pattern = re.compile(r'\[(?P<operation>\w+)\]\:\s+(?P<responsetime>\d+)\s+(?P<count>\d+)')

    histogram = dict()
    for datafile in paths:
        with open(datafile, 'r') as myfile:
            # skip warmup of data file
            for line in myfile:
                if "iteration = 0" not in line:
#                if "[benchmark]" not in line:
                    continue
                break


            for line in myfile:
                # break when we reach the next iteration
                if "iteration = 2" in line:
                    break
                result = pattern.match(line)
                if result:
                    result = result.groupdict()
                    operation, responsetime, count = result["operation"], int(result["responsetime"]), int(result["count"])
                    if responsetime not in histogram:
                        histogram[responsetime] = 0
                    histogram[responsetime] = histogram[responsetime] + count

    print histogram

    save_histogram(histogram, outputfile)

if __name__ == "__main__":
    main()
