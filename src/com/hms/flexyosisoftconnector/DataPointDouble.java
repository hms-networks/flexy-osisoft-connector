package com.hms.flexyosisoftconnector;

/**
 * DataPointDouble class
 *
 * Class object for a DataPoint with a double value
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */

public class DataPointDouble extends DataPoint {
   private double valueDouble;

   public DataPointDouble(double value, String time)
   {
      valueDouble = value;
      timestamp = time;
   }

   public double getValueDouble()
   {
      return valueDouble;
   }

   //Compares the datapoint to another datapoint
   //Returns true if the timestamp, type, and value are the same
   public boolean equals(DataPoint p)
   {
      if (p instanceof DataPointDouble)
      {
         if (p.getTimeStamp().equals(timestamp) && ((DataPointDouble) p).getValueDouble()==valueDouble)
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
      if (p instanceof DataPointDouble)
      {
         if (p != null && ((DataPointDouble) p).getValueDouble()==valueDouble)
         {
            return true;
         }
      }
      return false;
   }

   //Returns the datapoint's type
   public byte getType() {
      return TYPE_FLOAT;
   }

   //Returns the datapoint's value as a string
   public String getValueString() {
      return (""+valueDouble);
   }
}
