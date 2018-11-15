#!/usr/bin/python

# Builds a release zip folder

import sys
import os
import zipfile

PROJECT_NAME = "flexy-osisoft-connector"

#Path that the release file gets saved to
RELEASE_PATH = "../releases/"

#Path in the release directory that functional files should be placed
ZIP_CONNECTOR_PATH = "osisoft_connector/"
#File names for release files
README_FILENAME    = "README.md"
CHANGELOG_FILENAME = "CHANGELOG.md"
JAR_FILENAME       = PROJECT_NAME + ".jar"
CONFIG_FILENAME    = "ConnectorConfig.json"
JVMRUN_FILENAME    = "jvmrun"

#Paths for release files
README_PATH    = "../../"
CHANGELOG_PATH = "../docs/"
JAR_PATH       = "../build/"
CONFIG_PATH    = "../config/"
JVMRUN_PATH    = "../scripts/"

def CreateRelease(version):

   #Create releases directory if it does not already exist
   if not os.path.exists(RELEASE_PATH):
      os.makedirs(RELEASE_PATH)

   releaseFilename = RELEASE_PATH + PROJECT_NAME + "-" + version

   #Create the release zip folder
   zf = zipfile.ZipFile("%s.zip" % releaseFilename, "w", zipfile.ZIP_DEFLATED)

   #Add all "release" files to the zip
   zf.write(os.path.abspath(os.path.join(README_PATH,README_FILENAME)), README_FILENAME)
   zf.write(os.path.abspath(os.path.join(CHANGELOG_PATH,CHANGELOG_FILENAME)), CHANGELOG_FILENAME)
   zf.write(os.path.abspath(os.path.join(JAR_PATH,JAR_FILENAME)), ZIP_CONNECTOR_PATH + JAR_FILENAME)
   zf.write(os.path.abspath(os.path.join(CONFIG_PATH,CONFIG_FILENAME)), ZIP_CONNECTOR_PATH + CONFIG_FILENAME)
   zf.write(os.path.abspath(os.path.join(JVMRUN_PATH,JVMRUN_FILENAME)), ZIP_CONNECTOR_PATH + JVMRUN_FILENAME)

   #Close the release zip folder
   zf.close()

   print "\nSuccessfully made release: " + releaseFilename + ".zip"

if __name__ == '__main__':

   if len(sys.argv) != 2:
      print "Usage: MakeRelease.py versionNumber"
      sys.exit(0)

   CreateRelease(sys.argv[1])
