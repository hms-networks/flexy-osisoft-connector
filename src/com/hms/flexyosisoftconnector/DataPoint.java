package com.hms.flexyosisoftconnector;

/**
 * DataPoint class
 *
 * Abstract class object for a DataPoint.  This is a value and a time.
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */

public abstract class DataPoint {

   public static final byte TYPE_BOOLEAN = 0;
   public static final byte TYPE_FLOAT   = 1;
   public static final byte TYPE_INT     = 2;
   public static final byte TYPE_DWORD   = 3;

   protected String timestamp;

   public String getTimeStamp()
   {
      return timestamp;
   }

   public String toString()
   {
      return timestamp + " " + getValueString();
   }

   //Compares the datapoint to another datapoint
   //Returns true if the timestamp, type, and value are the same
   public abstract boolean equals(DataPoint p);

   //Compares the datapoint's value to another datapoint's value
   //Returns true if the type and value are the same
   public abstract boolean valueEquals(DataPoint p);

   //Returns the datapoint's type
   public abstract byte    getType();

   //Returns the datapoint's value as a string
   public abstract String  getValueString();
}
