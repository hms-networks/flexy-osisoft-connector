package com.hms.flexyosisoftconnector;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
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

   /* The highest rate is 1 point per second logged to historical tags.
    *
    * Worst case, after 1 minute there are 60 points of data per tag.
    *
    * We limit the number of lines read to a little over 3 minutes worth of data to match this time.
    */
   private int timeWindowMinutes = 3;
   private long timeWindowMilliseconds = timeWindowMinutes * 60 * 1000; // minutes * 60 seconds * 1000 milliseconds to convert minutes to milliseconds

   /* This tells us if we can grab the historical logs.
    *
    * It gets set to true if it is the start of the program,
    * or If the entire file has been parsed,
    * or if for some reason we have decided to stop parsing the file.
    *
    * More information on why we might stop parsing the file is in the parseBinary function.
    *
    */
   private boolean readEntireFile = true;

   /* The historical logs can store 1 million data points per tag.
    * We don't need to read that many into the internal queue at one time
    * for any reason, at most we can send at 5000 per batch composed of multiple tags.
    *
    * Assume we have about 3 minutes of data with the highest logging rate of 1 per second
    * there should be 180 lines generated. To be safe, only read in 200.
    *
    * If somehow we have 1000s of lines in this file the device will stay stuck on one tag until
    * it has read every line without this cap.
    */
   private static final int MAX_DATA_POINTS_PER_TAG = 200;

   //Previous posted datapoint
   private DataPoint lastDataPoint;

   // temp bool to turn off adding to queue
   private boolean addToQueue = true;


   public Tag(String tagName) {
      eWONTagName = tagName;
      tagDataFileName = tagQueueDirectory + tagName + "TagQueue";
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
   public void recordTagValue(long timeEpochSeconds, float floatValue)
   {
      long epochTimeMilliseconds = timeEpochSeconds * 1000;
      String timestamp = OSISoftDateFormat.format(new Date(epochTimeMilliseconds));

      String value = String.valueOf(floatValue);

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
    * exportBlockDescriptorHistoricalLogs
    * Send an EBD command with a start and end time and a tag name.
    * Start time is stored in an external file, end time is set to ("a start time" + "a timeWindow").
    * If either the start or end time is in the future, adjustments are made.
    */
   public void exportBlockDescriptorHistoricalLogs()
   {
      // Time trackers to keep track of start and end of queue
      String startTime = TIME_ERROR;
      String endTime = TIME_ERROR;

      // Get the time tracker to use in EBD call for the start of historical logs
      startTime = readTimeTrackerFile();

      // Check that the error value did not get returned. Log if it did, and do not make EBD call
      if (startTime.equalsIgnoreCase(TIME_ERROR)) {
         Logger.LOG_ERR( "EBD Error: Start time not set after reading time tracker file.");
         return;
      }

      long currentTimeMs = System.currentTimeMillis();
      long startTimeMs = Long.parseLong(startTime);
      int minutesConverter = 60;
      int millisecondConverter = 1000;

      // Get the time queue is behind in milliseconds and convert that to minutes
      long howFarBehindMinutes = ((currentTimeMs - startTimeMs) / millisecondConverter / minutesConverter);

      /* This comment is for the first if case
       *
       * There is a case we will change the time tracker to a time in the future.
       * If we have nothing in the queue and move the tracker up by 'time window' minutes,
       * it will make the time tracker ahead of the current time and break the EBD command.
       *
       * Ensure the above issue is fixed here instead of in point removal because the logic
       * is simpler than handling when removing points.
       *
       * If the time period we are looking at would become a negative number... fix it.
       */
      if ((currentTimeMs - startTimeMs) < 0) {
          Logger.LOG_DEBUG("tracker was negative, setting to current time");
          writeTimeTrackerFile(currentTimeMs);
          startTimeMs = currentTimeMs;
          // else if the time period is longer then the time window but also positive
      } else if ((currentTimeMs - startTimeMs) > timeWindowMilliseconds) {
          endTime = convertTimeIntHelper((Long.parseLong(startTime) + timeWindowMilliseconds));
          Logger.LOG_DEBUG(getTagName() + " grabbing " + (timeWindowMilliseconds / millisecondConverter / minutesConverter) + " minutes of data: EBD generated, ");
          Logger.LOG_DEBUG(getTagName() + " is " + howFarBehindMinutes + " minutes behind current time");
      } else {
          // Otherwise just set the end time to current time, because the end of the time period would of been into the future.
          // This is different from the first case where the beginning of the time period would of been in the future
          endTime = convertTimeIntHelper(currentTimeMs);
          Logger.LOG_DEBUG(getTagName() + "grabbing  " + ((currentTimeMs - startTimeMs) / millisecondConverter) + " seconds: EBD generating");
      }

      // format the time tracker to correct format after we have used it in the previous format
      startTime = convertTimeIntHelper(Long.parseLong(startTime));

      String tagName = getTagName();

      try {
          // https://ewonsupport.biz/ebd/ <- has a nice EBD string builder. :)
          // Command gets historical logs from start and end time for the tag and exports in binary.
          Exporter exporter = new Exporter("$dtHL$ftB$st" + startTime + "$et" + endTime + "$fnvalues$tn" + tagName);
          exporter.ExportTo("file://" + tagDataFileName);
          exporter.close();
      } catch (IOException ioe) {
          Logger.LOG_ERR(getTagName() + " Error in export block descriptor");
          Logger.LOG_ERR("$dtHL$ftT$st" + startTime + "$et" + endTime + "$fnvalues$tn" + tagName);
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

   public boolean getAddToQueue() {
       return addToQueue;
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
      String fileValueString = Long.toString(timeInt);


      File file = new File(tagTimeIntFileName);
      FileWriter fr = null;
      BufferedWriter br = null;
      try {
          fr = new FileWriter(file);
          br = new BufferedWriter(fr);

          // write the data to the file here
          br.write(fileValueString);

      } catch (IOException e) {
          Logger.LOG_ERR("Error writing time tracker file. Time tracker not set");
          e.printStackTrace();
      } finally {
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
             Logger.LOG_ERR("Something went wrong reading tag data queue time tracker from timeInt file. Check tag timeInt file: " + tagTimeIntFileName);
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
     * @param x Decimal value to transform to binary
     * @param len How many 1's and 0's should we have?
     * This adds in the missing leading 0's to the generated binary string
     * when given small inputs.
     *
     * Example, x input of 4 would be "100"
     * x output with len input of 8 would be "00000100"
     *
     * @return Returns the string of the decimal value converted to binary
     * with leading 0's added if needed.
     */
    public static String toBinaryString(int x, int len) {
        StringBuilderLite result = new StringBuilderLite(len);

        for (int i = len - 1; i >= 0 ; i--) {
            int mask = 1 << i;
            result.append(String.valueOf((x & mask) != 0 ? 1 : 0));
        }

        return result.toString();
    }

    /**
     * This function parses the output of the binary file that results from the EBD command
     * called earlier in this program.
     *
     * Parsed data gets put directly into internal data queue per tag. If any of the stop conditions
     * are met, parsing immediately ends.
     */
    public void parseBinary() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(tagDataFileName));

            /* We read in each word in the binary file and store that as a decimal integer
             * the decimal integer is kept in this variable, so we can transform it to binary
             */
            int singleCharInt;

            /* As we go through each word in the binary file this variable counts which word we are on
             * it is mostly used to keep track of when we are on the next line of the binary file
             * this is done by counting out the number of words we expect to see in a single line
             */
            int numWordRepresentedInDecimal = 0;

            // Store the full 32 bit binary in strings once we read the 4 different  8 bit words into them
            String fullBinaryTimeString = "";
            String fullBinaryValueString = "";

            /* A single line of binary file is made up of 16 Words
             * line 0 holds the header information and half of the first line, so we start on line 1
             * and 'reach backward' for the first half of the first line
             */
            int whichLine = 1; // start on line 1, line 0 holds the header info in first half and the first time in the second half
            final int lineWordLength = 16;
            final int startPositionOfTimeStamp = 8 - lineWordLength; // Start is on the line with the header info. Set to second half and subtract a line.
            final int endPositionOfTimeStamp = 12 - lineWordLength; // End is at the end of the line with the header info. Set to second half and subtract a line.
            final int startPostionOfFloatValue = 4; // This is the first half of the second line
            final int endPositionOfFloatValue = 8; // This is also in the first half of the second line
            final int numBitsInBinary = 8;

            /* This variable is used to find the last word of the file
             * The line 'ends' in the middle of the line because the binary data is offset by half the line length
             */
            final int endPositionOfWordsWeNeed = lineWordLength / 2;

           // Keep track of if we have read the entire binary file with this
           readEntireFile = false;

           while((singleCharInt = reader.read()) != -1) {

               // Increase the word count since we just read in a new word
               numWordRepresentedInDecimal++;

               /* WhichLine is which line of data are we on. A line means one line of historical logging data
                * MAX_DATA_POINTS_PER_TAG is for how many line should we read before giving up and going to the next tag
                *
                * This is one of the conditions to stop parsing the binary file and just move on to the next tag.
                */
               if(whichLine > MAX_DATA_POINTS_PER_TAG) {
                   // Lines parsed has reached the max, stop parsing.
                   Logger.LOG_DEBUG("Tag \"" + getTagName() + " no longer parsing file, max lin num hit. que size: " + dataPoints.size());
                   reader.close();
                   break;
               }

               /* addToQueue gets changed by data poster when removing points so we don't add and remove at the same time
                *
                * This is another condition to stop parsing and move on the the next tag.
                */
               if(!addToQueue) {
                   Logger.LOG_DEBUG("Tag \"" + getTagName() + " parsing file stopped from dataposter while removing points");
                   reader.close();
                   readEntireFile = true;
                   break;
               }

               // calculate start and end times of all binary data we want in units of word positions
               int startPosTime = startPositionOfTimeStamp + whichLine * lineWordLength;
               int endPosTime = endPositionOfTimeStamp + whichLine * lineWordLength;

               int startPosValue = startPostionOfFloatValue + whichLine * lineWordLength;
               int endPosValue = endPositionOfFloatValue + whichLine * lineWordLength;

               /* Grab the 4 words in decimal and convert to binary.
                * Once in binary, concatenate them all together to get the binary value
                *
                * Convert from binary back to decimal, and we have the epoch time in seconds.
                */
               if((numWordRepresentedInDecimal > startPosTime) && (numWordRepresentedInDecimal <= endPosTime)) {

                   // we need the binary of each of the 4 Words added together to get the epoch time in seconds in binary
                   fullBinaryTimeString += toBinaryString(singleCharInt, numBitsInBinary);

                   /* Again, grab the 4 words in decimal and convert to binary.
                    * Once in binary, concatenate them all together to get the binary value
                    *
                    * This time the final binary value is converted into floating point format.
                    */
               } else if ((numWordRepresentedInDecimal > startPosValue) && (numWordRepresentedInDecimal <= endPosValue)) {

                   fullBinaryValueString += toBinaryString(singleCharInt, numBitsInBinary);
               }

               /* Calculate the word position of the end of the line.
                * Note: binary is skewed, so the 'end' of one line is actually the middle of the file
                *
                * Example:  start is the middle of the first line, end is the middle of the second line
                */
               int lineEndCheck = (endPositionOfWordsWeNeed + (whichLine * lineWordLength));

               if(numWordRepresentedInDecimal == lineEndCheck) {

                   long epochTimeSeconds = Long.parseLong(fullBinaryTimeString, 2);

                   // convert to float from binary
                   int intBits = new BigInteger(fullBinaryValueString, 2).intValue();
                   float myFloat = Float.intBitsToFloat(intBits);

                   // record the data point now that we have it parsed

                   // If last data point is not null, check the new one is new
                   if (lastDataPoint == null) {
                       // There was no last point, start of program condition
                       recordTagValue(epochTimeSeconds, myFloat);
                   } else {
                       // Convert last point's epoch time to seconds, which is what the EBD "timeInt" is in
                       // Use the seconds to make sure we dont add the same timestamped point twice in a row
                       long lastPointSeconds = lastDataPoint.epochTime / 1000;

                       // if the time stamp of the last point is not the same as the current point, go ahead and add the point
                       if (lastPointSeconds != epochTimeSeconds) {
                           recordTagValue(epochTimeSeconds, myFloat);
                       }
                   }

                   // we finished parsing a line of binary data. move to the next one
                   whichLine++;

                   // empty the  binary string holders for next pass through
                   fullBinaryValueString = "";
                   fullBinaryTimeString = "";
               }
           }
           // we finished either from finishing or the loop got stopped
           readEntireFile = true;
           Logger.LOG_DEBUG("Ended binary parse of tag " + getTagName());
           reader.close();

       } catch (IOException e) {
           Logger.LOG_ERR("IO exception in Tag Class, parsing binary file.");
           e.printStackTrace();
       }
   }

   /**
    * This function generates a list of historical logs from
    * and EBD call and then reads the list into the internal queue
    *
    */
   public void readFromHistoricalLogQueue()
   {
       // If we are ready to read the file again
       // And if we are also not mid process on removing from queue (specifically changing the time tracker file)
       if(readEntireFile && addToQueue) {
           // EBD call to generate tag data point file
           exportBlockDescriptorHistoricalLogs();

           // Parse the file generated by the EBD call and add all info to array list
           parseBinary();
       }
   }

   /**
    * Takes in timeInt retrieved from EBD call to historical logs and converts it to the format
    * needed for OSIsoft.
    *
    * @param timeInt time from historical logs which is in epoch time in seconds
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
             // this starts the removal and stops adding to the internal queue
             setAddToQueue(false);

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

            if(readEntireFile) {
                // there were 0 tags removed in the time window given, move up queue by time window
                long startTime = Long.parseLong(readTimeTrackerFile());
                long newTime = timeWindowMilliseconds + startTime;

                writeTimeTrackerFile(newTime);
            }

            // removal is done, we can keep adding to queue
            setAddToQueue(true);
         }
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
