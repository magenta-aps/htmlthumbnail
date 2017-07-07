__author__ = 'DarkStar1'

import fileinput, glob, os, re, shutil, sys, urllib
from bs4 import BeautifulSoup

def encodeImgSrc(file, encodedFiles):
    #Wanted to use the lxml lib but for some reason it was only finding 1 result within the test file.
    soup = BeautifulSoup(file.read(), "html5lib")
    for img in soup.find_all('img'):
        # print("Found an image: " + img['src'])
        #create a base64 link from a dictionary containing the encoded pngs
        img['src'] = base64Prefix + encodedFiles[img['src']]

    file.seek(0)
    file.write(str(soup))
    file.truncate()

#holds a map of the png file and it's base64 encoding in the form of {"xx.png":"hsbudbud..."}
encodedFiles = {}
#the string prefix to base64 encoded images
base64Prefix = "data:image/png;base64,"

#The pdf file to convert
SOURCE_FILE = os.path.abspath(sys.argv[1])

# change to the source file directory since we can't be sure alfresco would
os.chdir(os.path.split(SOURCE_FILE)[0])

# First call poppler's pdftohtml to convert the file from pdf to html
os.system("pdftohtml -s -c " + SOURCE_FILE )

#The pervious call adds a -html to the result of the conversion so we need to create an interim file name
# that takes this into account
INTERIM_FILE = os.path.splitext(SOURCE_FILE)[0]
INTERIM_FILE += "-html.html"

#The string is usually escaped so we need to remove the '\' from the string
INTERIM_FILE = INTERIM_FILE.replace("\ ", " ")
# print("\n\nThe INTERIM FILE name is now: " + INTERIM_FILE+"\n\n")

#Look in the current directory and base 64 encode all png files into a map with the original src values as keys
#(Extremely expensive. I know)
for file in glob.glob("*.png"):
    with open(file, 'rb') as fh:
        #add to the map
        encodedFiles[file] = fh.read().encode('base64').replace('\n', '')

# Look for the and replace the urlencoded string in the html file
HTMLFILE = open(os.path.abspath(INTERIM_FILE), 'r+')
encodeImgSrc(HTMLFILE, encodedFiles)
HTMLFILE.close()

#For alfresco, change the name of the interim file to the target filename *argv[2]
shutil.move(INTERIM_FILE, sys.argv[2])

#clear the dictionary/map for sake of memory issues
encodedFiles.clear()