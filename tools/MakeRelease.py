#!/usr/bin/python

# Builds a release zip folder

import sys
import os
from subprocess import call
import zipfile

PROJECT_NAME = "flexy-osisoft-connector"

#Minify depenancies
JAVA_JS_COMPILER   = "closure-compiler-v20181028.jar"
JAVA_JS_COMP_LEVEL = "--compilation_level=SIMPLE"
JAVA_JS_WARN_LEVEL = "--warning_level=QUIET"

JAVA_CSS_COMPILER  = "yuicompressor-2.4.8.jar"

#Path that the release file gets saved to
RELEASE_PATH = "../releases/"

#Path in the release directory that functional files should be placed
ZIP_CONNECTOR_PATH = "osisoft_connector/"
ZIP_CONNECTOR_JS_PATH = ZIP_CONNECTOR_PATH + "js/"
ZIP_CONNECTOR_CSS_PATH = ZIP_CONNECTOR_PATH + "css/"

#File names for release files
README_FILENAME     = "README.md"
CHANGELOG_FILENAME  = "CHANGELOG.md"
JAR_FILENAME        = PROJECT_NAME + ".jar"
CONFIG_FILENAME     = "ConnectorConfig.json"
JVMRUN_FILENAME     = "jvmrun"
OCS_SCRIPT_FILENAME = "OcsBasicScript.bas";

#Paths for release files
README_PATH    = "../"
CHANGELOG_PATH = "../docs/"
JAR_PATH       = "../build/"
CONFIG_PATH    = "../config/"
SCRIPTS_PATH   = "../scripts/"

#Web file paths
WEB_PATH     = "../web/"
CSS_PATH     = WEB_PATH + "css/"
CSS_MIN_PATH = CSS_PATH + "min/"
JS_PATH      = WEB_PATH + "js/"
JS_MIN_PATH  = JS_PATH + "min/"

def MinifyWebFiles():
   #JS
   for filename in os.listdir(JS_PATH):
      if filename.endswith(".js"):
         minFilename = os.path.splitext(filename)[0] + ".min.js"
         retval = call(["java", "-jar", JAVA_JS_COMPILER,JAVA_JS_COMP_LEVEL, JAVA_JS_WARN_LEVEL,"--js_output_file=\"" + JS_MIN_PATH + minFilename + "\"", JS_PATH + filename])
         if (retval != 0):
            print("Something went wrong in minification of " + filename + " return value: " + retval)

   #CSS
   #create css/min directory, yuicomplier cannot do this
   if not os.path.exists(CSS_MIN_PATH):
      os.makedirs(CSS_MIN_PATH)

   for filename in os.listdir(CSS_PATH):
      if filename.endswith(".css"):
         minFilename = os.path.splitext(filename)[0] + ".min.css"
         retval = call(["java", "-jar", JAVA_CSS_COMPILER, "--type", "css", "-o", CSS_MIN_PATH + minFilename, CSS_PATH + filename])
         if (retval != 0):
            print("Something went wrong in minification of " + filename + " return value: " + retval)

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
   zf.write(os.path.abspath(os.path.join(SCRIPTS_PATH,JVMRUN_FILENAME)), ZIP_CONNECTOR_PATH + JVMRUN_FILENAME)
   zf.write(os.path.abspath(os.path.join(SCRIPTS_PATH,OCS_SCRIPT_FILENAME)), ZIP_CONNECTOR_PATH + OCS_SCRIPT_FILENAME)

   #web
   MinifyWebFiles()
   for root, dirs, files in os.walk(WEB_PATH, topdown=False):
      for filename in files:
         if filename.endswith("min.js"):
            zf.write(os.path.abspath(os.path.join(root, filename)), ZIP_CONNECTOR_JS_PATH + filename)
         elif filename.endswith("min.css"):
            zf.write(os.path.abspath(os.path.join(root, filename)), ZIP_CONNECTOR_CSS_PATH + filename)
         elif filename.endswith(".html"):
            zf.write(os.path.abspath(os.path.join(root, filename)), ZIP_CONNECTOR_PATH + filename)


   #Close the release zip folder
   zf.close()

   print("\nSuccessfully made release: " + releaseFilename + ".zip")

if __name__ == '__main__':

   if len(sys.argv) != 2:
      print("Usage: MakeRelease.py versionNumber")
      sys.exit(0)

   CreateRelease(sys.argv[1])
