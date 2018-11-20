package com.hms.flexyosisoftconnector;

/**
 * DataPointBoolean class
 *
 * Class object for a DataPoint with a boolean value
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */

public class DataPointBoolean extends DataPoint {
   private boolean valueBoolean;

   public DataPointBoolean(boolean value, String time)
   {
      valueBoolean = value;
      timestamp = time;
   }

   public boolean getValueBoolean()
   {
      return valueBoolean;
   }

   //Compares the datapoint to another datapoint
   //Returns true if the timestamp, type, and value are the same
   public boolean equals(DataPoint p)
   {
      if (p instanceof DataPointBoolean)
      {
         if (p.getTimeStamp().equals(timestamp) && ((DataPointBoolean) p).getValueBoolean()==valueBoolean)
         {
            return true;
         }
      }
      return false;
   }

   //Compares the datapoint's value to another datapoint's value
   //Returns true if the type and value are the same
   public boolean valueEquals(DataPoint p)
   {
      if (p instanceof DataPointBoolean)
      {
         if (p != null && ((DataPointBoolean) p).getValueBoolean()==valueBoolean)
         {
            return true;
         }
      }
      return false;
   }

   //Returns the datapoint's type
   public byte getType() {
      return TYPE_BOOLEAN;
   }

   //Returns the datapoint's value as a string
   public String getValueString() {
      return (""+valueBoolean);
   }
}
