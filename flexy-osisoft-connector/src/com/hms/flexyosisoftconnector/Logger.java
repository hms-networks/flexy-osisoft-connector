package com.hms.flexyosisoftconnector;


/**
 * Logger.java
 *
 * Provides an interface to selectively log output
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */
public class Logger {

   //Supported Logging levels
   //These are the same as spdlog
   public static final int LOG_LEVEL_TRACE    = 0;
   public static final int LOG_LEVEL_DEBUG    = 1;
   public static final int LOG_LEVEL_INFO     = 2;
   public static final int LOG_LEVEL_WARN     = 3;
   public static final int LOG_LEVEL_ERR      = 4;
   public static final int LOG_LEVEL_CRITICAL = 5;
   public static final int LOG_LEVEL_NONE     = 6;

   private static int loggingLevel = LOG_LEVEL_DEBUG;

   public static boolean SET_LOG_LEVEL(int level)
   {
      boolean retval = false;
      if(level <= LOG_LEVEL_NONE && level >= LOG_LEVEL_TRACE)
      {
         loggingLevel = level;
         retval = true;
         Logger.LOG_INFO("Set logging level to " + level);
      }
      return retval;
   }

   public static void LOG_EXCEPTION(Exception e)
   {
      if(loggingLevel == LOG_LEVEL_TRACE)
      {
         e.printStackTrace();
      }
   }

   public static void LOG(int level, String logString)
   {
      if(level >= loggingLevel)
      {
         System.out.println(logString);
      }
   }

   public static void LOG_DEBUG(String logString)
   {
      LOG(LOG_LEVEL_DEBUG, logString);
   }

   public static void LOG_INFO(String logString)
   {
      LOG(LOG_LEVEL_INFO, logString);
   }

   public static void LOG_WARN(String logString)
   {
      LOG(LOG_LEVEL_WARN, logString);
   }

   public static void LOG_ERR(String logString)
   {
      LOG(LOG_LEVEL_ERR, ("ERROR: " + logString));
   }

   public static void LOG_CRITICAL(String logString)
   {
      LOG(LOG_LEVEL_CRITICAL, logString);
   }
}
