package com.hms.flexyosisoftconnector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

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

   public Tag(String tagName) {
      eWONTagName = tagName;
      dataPoints = Collections.synchronizedList(new ArrayList());
      try {
         tagControl = new TagControl(tagName);
      } catch (Exception e) {
         Logger.LOG_DEBUG("Tag \"" + tagName + "\" does not exist on this eWON" );
         validTag = false;
      }
   }

   public Tag(String tagName, String id) {
      eWONTagName = tagName;
      webID = id;
      dataPoints =  Collections.synchronizedList(new ArrayList());
      try {
         tagControl = new TagControl(tagName);
      } catch (Exception e) {
         Logger.LOG_DEBUG("Tag \"" + tagName + "\" does not exist on this eWON" );
         validTag = false;
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

   // Returns a string representation of the eWON's long value of the tag
   public String getTagValue() {
      return Long.toString(tagControl.getTagValueAsLong());
   }

   public void recordTagValue(String time)
   {
      if(validTag)
      {
         DataPoint point = new DataPoint(tagControl.getTagValueAsLong(), time);
         synchronized (dataPoints)
         {
            dataPoints.add(point);
         }
      }
   }

   public ArrayList getDataPoints(int num)
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
}
