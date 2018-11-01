package com.hms.flexyosisoftconnector;
import java.util.Date;

/**
 * DataPoint class
 *
 * Class object for a DataPoint.  This is a value and a time.
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */

public class DataPoint {

   private long valueLong;
   private String timestamp;

   public DataPoint(long value, String time)
   {
      valueLong = value;
      timestamp = time;
   }

   public long getValueLong()
   {
      return valueLong;
   }

   public String getTimeStamp()
   {
      return timestamp;
   }

   public String toString()
   {
      String s = "";
      s += valueLong + " " + timestamp;
      return s;
   }

   public boolean equals(DataPoint p)
   {
      if (p.getTimeStamp().equals(timestamp) && p.getValueLong()==valueLong)return true;
      return false;
   }

   public boolean valueEquals(DataPoint p)
   {
      if (p != null && p.getValueLong()==valueLong)return true;
      return false;
   }
}
