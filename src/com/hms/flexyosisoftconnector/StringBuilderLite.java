package com.hms.flexyosisoftconnector;

/**
 * StringBuilderLite class
 *
 * Class for building large preallocated strings
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 */

public class StringBuilderLite {
   private int size = 0;
   private int index = 0;
   private char[] strValue;

   public StringBuilderLite(int numChars)
   {
      size = numChars;
      strValue = new char[size];
   }

   public void clearString()
   {
      index = 0;
   }

   public String toString()
   {
      return String.valueOf(strValue, 0, index);
   }

   public boolean append(String s)
   {
      boolean retval = true;
      if (s == null) s = "";
      int len = s.length();
      try
      {
         s.getChars(0, len, strValue, index);
         index += len;
      } catch (IndexOutOfBoundsException e)
      {
         Logger.LOG_EXCEPTION(e);
         retval = false;
      }
      return retval;
   }
}
