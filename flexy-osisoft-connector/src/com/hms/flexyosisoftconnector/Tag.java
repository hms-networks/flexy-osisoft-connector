package com.hms.flexyosisoftconnector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import com.ewon.ewonitf.SysControlBlock;
import com.ewon.ewonitf.TagControl;

/**
 * Tag class
 *
 * Class object for a Tag. Stores the eWON tag name and the OSIsoft PI webId of
 * attribute.
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */

public class Tag {

   private String eWONTagName;
   private String webID;
   private boolean validTag = true;
   private List dataPoints;
   private TagControl tagControl;
   private byte dataType;

   //Flag to indicate if duplicate values should be logged
   //true  - Always log datapoint
   //false - Only log on value change
   private boolean logDuplicateValues = true;
   
   //Previous posted datapoint
   private DataPoint lastDataPoint;


   public Tag(String tagName, boolean logDuplicates) {
      eWONTagName = tagName;
      logDuplicateValues = logDuplicates;
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

   public void recordTagValue(String time)
   {
      if(validTag)
      {
         DataPoint point = null;
         switch (dataType) {
            case DataPoint.TYPE_BOOLEAN:
               boolean val = (tagControl.getTagValueAsLong() != 0);
               point = new DataPointBoolean(val, time);
               break;
            case DataPoint.TYPE_FLOAT:
               point = new DataPointDouble(tagControl.getTagValueAsDouble(), time);
               break;
            case DataPoint.TYPE_INT:
               point = new DataPointInt((int)tagControl.getTagValueAsLong(), time);
               break;
            case DataPoint.TYPE_DWORD:
               point = new DataPointLong(tagControl.getTagValueAsLong(), time);
               break;
            default:
               return;
         }
         if(logDuplicateValues || !point.valueEquals(lastDataPoint))
         {
            synchronized (dataPoints)
            {
               lastDataPoint = point;
               dataPoints.add(point);
            }
         }
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
         int dataPointsSize = dataPoints.size();
         if(dataPointsSize > 0  && pointsToRemove.size() > 0)
         {
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
