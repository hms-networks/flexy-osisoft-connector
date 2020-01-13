package com.hms.flexyosisoftconnector;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ewon.ewonitf.Exporter;
import com.ewon.ewonitf.SysControlBlock;
import com.ewon.ewonitf.TagControl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Tag class
 *
 * Class object for a Tag. Stores the eWON tag name and the OSIsoft PI webId of
 * attribute.
 *
 * HMS Networks Inc. Solution Center
 *
 */

public class Tag {

   private String eWONTagName;
   private String webID;
   private boolean validTag = true;
   private List dataPoints;
   private TagControl tagControl;
   private byte dataType;
   private String tagDataFileName;
   private String tagTimeIntFileName;
   private final String tagQueueDirectory = "/usr/internalTagQueue/";
   private static SimpleDateFormat OSISoftDateFormat;

   private final String TIME_ERROR = "-1";

   /* The historical logs can store 1 million data points per tag.
    * We don't need to read that many into the internal queue at one time
    * for any reason, at most we can send at 5000 per batch composed of multiple tags.
    *
    * A limit of 1000 seems reasonable because 5 max count tags of 1000 data points will be sent over
    * OMF at a time in a fully loaded situation.
    */
   private static final int MAX_DATA_POINTS_PER_TAG = 200;

   //Previous posted datapoint
   private DataPoint lastDataPoint;

   // temp bool to turn off adding to queue
   private boolean addToQueue = true;


   public Tag(String tagName) {
      eWONTagName = tagName;
      tagDataFileName = tagQueueDirectory + tagName + "TagQueue.txt";
      tagTimeIntFileName = tagQueueDirectory + tagName + "TimeInt.txt";
      OSISoftDateFormat = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss");
      dataPoints = Collections.synchronizedList(new ArrayList());
      try {
         tagControl = new TagControl(tagName);
      } catch (Exception e) {
         Logger.LOG_DEBUG("Tag \"" + tagName + "\" does not exist on this eWON" );
         validTag = false;
      }

      if(validTag)
      {
         dataType = getTagType(tagName);
         if(dataType == -1)
         {
            Logger.LOG_DEBUG("Could not find datatype for \"" + tagName + "\"");
            validTag = false;
         }
      }
   }

   // Returns the eWON tag name
   public String getTagName() {
      return eWONTagName;
   }

   public boolean isValidTag()
   {
      return validTag;
   }

   // Returns the OSIsoft webId
   public String getWebID() {
      return webID;
   }

   // Sets the OSIsoft webID
   public void setWebID(String newWebID) {
      webID = newWebID;
   }

   //Returns the data type of this tag
   public byte getDataType()
   {
      return dataType;
   }

   // Returns a string representation of the eWON's long value of the tag
   public String getTagValue() {
      return Long.toString(tagControl.getTagValueAsLong());
   }

   /**
    * Record tag value will be called after parsing the historical log values.
    * Historical logs are added to internal queue through this function.
    *
    * @param time is the time in historical logs in epoch time seconds
    * (Referred to as timeInt in the EBD generated file)
    * @param value is the value in historical logs
    */
   public void recordTagValue(String time, String value)
   {
      // Convert time string holding a epoch time in seconds to timestamp
      long epochSeconds = Long.parseLong(time);
      long epochTimeMilliseconds = epochSeconds * 1000;
      String timestamp = OSISoftDateFormat.format(new Date(epochTimeMilliseconds));

      // Parse value into correct type from string and put it in a datapoint
      if(validTag)
      {
         DataPoint point = null;
         switch (dataType) {
            case DataPoint.TYPE_BOOLEAN:
               boolean val = (Long.parseLong(value) != 0);
               point = new DataPointBoolean(val, timestamp);
               break;
            case DataPoint.TYPE_FLOAT:
               point = new DataPointDouble(Double.parseDouble(value), timestamp);
               break;
            case DataPoint.TYPE_INT:
               point = new DataPointInt(Integer.parseInt(value), timestamp);
               break;
            case DataPoint.TYPE_DWORD:
               point = new DataPointLong(Long.parseLong(value), timestamp);
               break;
            default:
               return;
         }

         // Store the time in epoch time format as well so we don't need to convert it back later when popping off queue
         point.setEpochTime(epochTimeMilliseconds);

         if(!point.equals(lastDataPoint))
         {
            synchronized (dataPoints)
            {
               lastDataPoint = point;
               dataPoints.add(point);
            }
         }
      }
   }

   /**
    * exportBlockDescriptor
    * Send an EBD command with a start and end time and a tag name.
    * Start time is stored in an external file, end time is current time.
    */
   public void exportBlockDescriptor()
   {
      // Time trackers to keep track of start and end of queue
      String startTime = TIME_ERROR;
      String endTime = TIME_ERROR;

      // Get the time tracker to use in EBD call for the start of historical logs
      startTime = readTimeTrackerFile();

      // Check that the error value did not get returned. Log if it did, and do not make EBD call
      if (startTime.equalsIgnoreCase(TIME_ERROR)) {
         Logger.LOG_ERR("EBD Error: Start time not set after reading time tracker file.");
         return;
      }

      // End time should be the current time to always keep it up to date
      endTime = convertTimeIntHelper(System.currentTimeMillis());

      String tagName = getTagName();

      try {
          // https://ewonsupport.biz/ebd/ <- has a nice EBD string builder. :)
          Exporter exporter = new Exporter("$dtHL$ftT$st" + startTime + "$et" + endTime + "$fnvalues$tn" + tagName);
          exporter.ExportTo("file://" + tagDataFileName);
          exporter.close();
      }catch (IOException ioe){
          Logger.LOG_ERR("Error in export block descriptor");
      }
   }

   /**
    * addToQueue is used to check if we should be reading in from the historical logs at this time.
    * Without it, we run into issues when removing from the list in dataposter while adding to the list
    * from the main method
    *
    * @param newValue, new value to set
    * True - keep adding to queue
    * False = stop adding to queue
    */
   public void setAddToQueue(boolean newValue) {
       addToQueue = newValue;
   }

   /**
    * Parse a semi colon seperated file for the 'timeInt' and 'value' fields
    */
   public void parseTagFile()
   {

       BufferedReader reader;
       try {
           reader = new BufferedReader(new FileReader(tagDataFileName));
           // Read the first line and do nothing with it
           String line = reader.readLine();
           if (line != null) {
               line = reader.readLine(); // get first line of data
           }

           // Counter to keep track of number of lines read in from historical logs
           int count = 0;
           while (line != null) {

               // Limit the lines read in to the max queue size to avoid massive queues
               if(count > MAX_DATA_POINTS_PER_TAG) {
                   // Lines parsed has reached the max, stop parsing.
                   // remove commented code before new code review
                   // long mem = Runtime.getRuntime().freeMemory();
                   // Logger.LOG_DEBUG("Tag \"" + getTagName() + " no longer parsing file, max lin num hit. que size: "+dataPoints.size()+ " free mem is: "+mem);
                   break;
               }

               if(!addToQueue) {
                   // remove commented code before new code review
                   // long mem = Runtime.getRuntime().freeMemory();
                   // Logger.LOG_DEBUG("Tag \"" + getTagName() + " parsing file stopped from dp, que size: "+dataPoints.size()+ " free mem is: "+mem);
                   break;
               }
               count++;

               // Extract the timeInt, which is first in the file
               String timeInt = line.substring(0, line.indexOf(';'));

               // Extract value, which is after 2 semi colons
               String value = "";

               /* This code finds the index of the substring where the tag's value is in the current line.
                * It looks at the number of occurrences of semi colons, and marks when the correct number has been
                * seen. Then it records those indices to use in the substring call.
                */
               // Main index for the loop
               int indexMain = 0;
               // Recorded start index of new substring
               int indexStart = 0;
               // Recorded end index of new substring
               int indexEnd = 0;

               final int firstSemiColonOfInterest = 2;
               final int secondSemiColonOfInterest = 3;
               final int lastInterestingSemiColon = 4;
               for(int i = 0; i < lastInterestingSemiColon; i++) {

                   // We need to add 1 to index main so indexOf function can work
                   // or it will just find the same thing again
                   indexMain = 1 + line.indexOf(';', indexMain);
                   if(i == firstSemiColonOfInterest) {
                       indexStart = indexMain;
                   } else if(i == secondSemiColonOfInterest) {
                       // Subtract one here to exclude the semicolon from the substring
                       indexEnd = indexMain - 1;
                   }
               }
               value = line.substring(indexStart, indexEnd);

               // Below checks are to only record into internal queue if it is a new tag in historical logs

               // If last data point is not null, check the new one is new
               if (lastDataPoint == null) {
                   // There was no last point, start of program condition
                   recordTagValue(timeInt, value);
               } else {
                   // Convert last point's epoch time to seconds, which is what the EBD "timeInt" is in
                   long lastPointSeconds = lastDataPoint.epochTime/1000;
                   if (lastPointSeconds != Long.parseLong(timeInt)) {
                       recordTagValue(timeInt, value);
                   }
               }

               // Read next line, repeat
               line = reader.readLine();
           }
           reader.close();
       } catch (IOException e) {
           Logger.LOG_ERR("IO exception in Tag Class, reading in historical log file.");
           e.printStackTrace();
       }
   }


   /**
    * Converts given long into date string of expected format
    * and writes it to a file
    *
    * @param timeInt epoch time in seconds to get converted and stored in file
    * @return just return what string it set
    */
   public String writeTimeTrackerFile(long timeInt)
   {
      // convert from epoch time long to string
      String fileValueString = convertTimeIntHelper(timeInt);


      File file = new File(tagTimeIntFileName);
      FileWriter fr = null;
      BufferedWriter br = null;
      try{
          fr = new FileWriter(file);
          br = new BufferedWriter(fr);

          // write the data to the file here
          br.write(fileValueString);

      } catch (IOException e) {
          Logger.LOG_ERR("Error writing time tracker file. Time tracker not set");
          e.printStackTrace();
      }finally{
          try {
              br.close();
              fr.close();
          } catch (IOException e) {
              Logger.LOG_ERR("Error closing time tracker file");
              e.printStackTrace();
          }
      }
      return fileValueString;
   }

   /**
    * This function is only called when retrieving value for call to export block descriptor.
    *
    * The tagTimeIntFileName file holds a time tracker for every tag. One file exists per tag.
    * This file will hold a string which will be the epoch time in seconds for where/when
    * in the Flexy historical logs has the data queue left off on sending data to OSIsoft
    *
    * The file can only contain numbers because it is converted to a long
    *
    * The below function makes sure the file exists, if not add in the current time to that
    * otherwise read off the epoch time in that file
    *
    * @return Returns a string of the file contents. it will be the epoch time tracker for the data queue.
    * The value is called a timeInt in the return file from the EBD commands, so the name will be timeInt throughout code.
    */
   public String readTimeTrackerFile()
   {
      String timeInt = TIME_ERROR;
      // check if file exists
      File tempFile = new File(tagTimeIntFileName);
      boolean timeTrackerFileExists = tempFile.exists();

      if(timeTrackerFileExists) {
         BufferedReader reader;
         try {
            reader = new BufferedReader(new FileReader(tagTimeIntFileName));
            String line = reader.readLine();
            while (line != null) {

               // There will be just one line, but just save the last line.
               timeInt = line;
               line = reader.readLine();
            }
            reader.close();
         } catch (IOException e) {
             e.printStackTrace();
             Logger.LOG_ERR("Something went wrong reading tag data queue time tracker from timeInt file. Check tag timeInt file: "+tagTimeIntFileName);
         }

      } else { // When the connector has not run before, these files will not exist.

          // If file does not exist, set timeInt to the current time to start a new queue
          Logger.LOG_INFO(getTagName() + ": time int was null, setting back of tag data queue current time");
          long tmpTimeIntMilliSeconds = System.currentTimeMillis();
          // Current time (when the list was null) will be the new back of queue
          String writtenValue = writeTimeTrackerFile(tmpTimeIntMilliSeconds);
          // Return what got written in the writeTimeTrackerFile function
          return writtenValue;
      }

      // Return the string read in this function
      return timeInt;
   }

   /**
    * This function generates a list of historical logs from
    * and EBD call and then reads the list into the internal queue
    *
    */
   public void readFromHistoricalLogQueue()
   {
       // EBD call to generate tag data point file
       exportBlockDescriptor();

       // Parse the file generated by the EBD call and add all info to array list
       parseTagFile();
   }

   /**
    * Takes in timeInt retrieved from EBD call to historical logs and converts it to the format
    * needed for OSIsoft.
    *
    * @param timeInt time from historical logs which is in epoch time
    * @return returns the new string to send to OSIsoft
    */
   private String convertTimeIntHelper(long timeInt)
   {

      String formattedTime = "";
      formattedTime = new SimpleDateFormat("ddMMyyyy_HHmmss")
               .format(new Date(timeInt));
      return formattedTime;
   }

   /**
    *  Remove from the queue by moving the time tracker forward to a later time
    * @param numTags number of places to move forward in the queue
    */
   public void popNumTagsFromQueue(int numTags)
   {
      // There will be a second or so when the value passed in from the other thread is always 0. Do nothing when that happens
      if (numTags > 0) {
         // Convert tag position to tag index
         numTags = numTags - 1;

         // Get timestamp for the point in the list that will be the new start
         long timestamp = ((DataPoint)(dataPoints.get(numTags))).getEpochTime();

         writeTimeTrackerFile(timestamp);
      }
   }

   public ArrayList getNewestDataPoints(int num)
   {
      ArrayList points = new ArrayList();
      synchronized (dataPoints)
      {
         int dataPointsSize = dataPoints.size();
         if(num > dataPointsSize) num = dataPointsSize;
         for(int i = 0; i < num; i++)
         {
            points.add(dataPoints.get(dataPointsSize - i - 1));
         }
      }
      return points;
   }

   public int getNumDataPoints()
   {
      return dataPoints.size();
   }

   public void trimOldestEntries(int num)
   {
      //Always keep at least one element in the datapoint set
      //to make sure that the current value is always updated
      if(dataPoints.size() <= num) num = dataPoints.size() - 1;

      for(int i = 0; i < num; i++)
      {
         try
         {
            dataPoints.remove(0);
         } catch (IndexOutOfBoundsException e)
         {
            //The datapoint list is empty
            Logger.LOG_DEBUG("Attemped to remove a datapoint from an empty list");
         }
      }
   }

   public void removeDataPoints(ArrayList pointsToRemove)
   {
      //Lock Access to the arraylist for thread saftey
      synchronized (dataPoints)
      {
        int numTags = pointsToRemove.size();

         int dataPointsSize = dataPoints.size();
         if(dataPointsSize > 0  && pointsToRemove.size() > 0)
         {
             Logger.LOG_DEBUG("Tag " + getTagName() + ": removing "  + numTags + " data points from queue.");
             popNumTagsFromQueue(numTags);

            DataPoint p = (DataPoint) pointsToRemove.get(0);

            //Iterate on the Arraylist from the end to the beginning
            for(int i = (dataPointsSize-1); i >=0; i--)
            {
               if(pointsToRemove.size() == 0) break;

               if(dataPoints.get(i).equals(p))
               {
                  dataPoints.remove(i);
                  pointsToRemove.remove(0);
                  if(pointsToRemove.size() > 0)
                  {
                     p = (DataPoint) pointsToRemove.get(0);
                  }
                  else
                  {
                     break;
                  }
               }
            }
         }

         // Update queue now that the queue time tracker has been updated
         readFromHistoricalLogQueue();
      }
   }

   //Gets the datatype of a tag on the Flexy
   //in numeric form
   private static byte getTagType(String tagName)
   {
      byte retval = -1;
      SysControlBlock SCB;
      try {
         SCB = new SysControlBlock(SysControlBlock.TAG, tagName);
         retval = (byte)Integer.parseInt(SCB.getItem("Type"));
      } catch (Exception e) {
         Logger.LOG_ERR("Error reading tag type for " + tagName);
      }
      return retval;
   }
}
