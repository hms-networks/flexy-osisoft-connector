package com.hms.flexyosisoftconnector;

/**
 * DataPointInt class
 *
 * Class object for a DataPoint with a int value
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 */

public class DataPointInt extends DataPoint {
   private int valueInt;

   public DataPointInt(int value, String time)
   {
      valueInt = value;
      timestamp = time;
   }

   public int getValueInt()
   {
      return valueInt;
   }

   //Compares the datapoint to another datapoint
   //Returns true if the timestamp, type, and value are the same
   public boolean equals(DataPoint p)
   {
      if (p instanceof DataPointInt)
      {
         if (p.getTimeStamp().equals(timestamp) && ((DataPointInt) p).getValueInt()==valueInt)
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
      if (p instanceof DataPointInt)
      {
         if (p != null && ((DataPointInt) p).getValueInt()==valueInt)
         {
            return true;
         }
      }
      return false;
   }

   //Returns the datapoint's type
   public byte getType() {
      return TYPE_INT;
   }

   //Returns the datapoint's value as a string
   public String getValueString() {
      return (""+valueInt);
   }
}
