package com.hms.flexyosisoftconnector;

/**
 * DataPointLong class
 *
 * Class object for a DataPoint with a long value
 *
 * HMS Networks Inc. Solution Center
 *
 */

public class DataPointLong extends DataPoint {
   private long valueLong;

   public DataPointLong(long value, String time)
   {
      valueLong = value;
      timestamp = time;
   }

   public long getValueLong()
   {
      return valueLong;
   }

   //Compares the datapoint to another datapoint
   //Returns true if the timestamp, type, and value are the same
   public boolean equals(DataPoint p)
   {
      if (p instanceof DataPointLong)
      {
         if (p.getTimeStamp().equals(timestamp) && ((DataPointLong) p).getValueLong()==valueLong)
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
      if (p instanceof DataPointLong)
      {
         if (p != null && ((DataPointLong) p).getValueLong()==valueLong)
         {
            return true;
         }
      }
      return false;
   }

   //Returns the datapoint's type
   public byte getType() {
      return TYPE_DWORD;
   }

   //Returns the datapoint's value as a string
   public String getValueString() {
      return Long.toString(valueLong);
   }
}
