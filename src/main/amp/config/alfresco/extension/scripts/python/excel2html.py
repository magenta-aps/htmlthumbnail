__author__ = 'lanre'

import fileinput, glob, os, sys

#holds a map of the png file and it's base64 encoding in the form of {"xx.png":"hsbudbud..."}
encodedFiles = {}
base64Prefix = "data:image/png;base64,"
#The excel file to convert
SOURCE_FILE = os.path.basename(sys.argv[2])

# change to the source file directory since we can't be sure alfresco would
os.chdir(os.path.split(sys.argv[2])[0])

def replaceAll(file, searchExp, replaceExp):
    for line in fileinput.input(file, inplace=1, backup='.bak'):
        if searchExp in line:
            line = line.replace(searchExp, replaceExp)
        sys.stdout.write(line)


# First call open office with to convert the file
os.system(sys.argv[1] + " --headless --convert-to html:\"HTML (StarCalc)\" " + SOURCE_FILE + "")

#The interim file is the result of the soffice conversion
INTERIM_FILE = os.path.splitext(SOURCE_FILE)[0]
INTERIM_FILE += ".html"

#Look in the current directory and base 64 encode all png files
for file in glob.glob("*.png"):
    with open(file, 'rb') as fh:
        #add to the map
        encodedFiles[file] = fh.read().encode('base64').replace('\n', '')

# Look for the and replace the string in the html file
for key in encodedFiles:
    print("\nThe key being searched: " + key + "\n")
    replaceAll(INTERIM_FILE, key, base64Prefix + encodedFiles[key])

#For alfresco, change the name of the interim file to the target filename *argv[2]
os.system("mv " + INTERIM_FILE + " " + sys.argv[3])

