package com.hms.flexyosisoftconnector;
import java.util.Date;

import com.ewon.ewonitf.SysControlBlock;
import com.hms.flexyosisoftconnector.JSON.JSONException;

/**
 * eWON Flexy java demo for OSIsoft Server
 *
 * This demo reads multiple tag values from an eWON Flexy IO Server and POSTs
 * them to an OSIsoft PI Server.
 *
 * HMS Networks Inc. Solution Center
 *
 */
public class Main {

   /**
    * Application Version Numbers
    */
   static final int MAJOR_VERSION = 0;
   static final int MINOR_VERSION = 4;

   /**
    * Minimum amount of free memory before data trimming occurs
    */
   static final long MINIMUM_MEMORY = 5000000;

   /**
    * Number of tags to trim when memory is low
    */
   static final int TAGS_TO_TRIM = 2;

   /**
    * Default name of a new Ewon
    */
   static final String DEFAULT_EWON_NAME = "eWON";

   /**
    * Filename of connector config file
    */
    static final String CONNECTOR_CONFIG_FILENAME = "/usr/ConnectorConfig.json";

   /**
    * Configuration of PI connector
    */
   static OSIsoftConfig piConfig;

   /**
    * PI Server management object
    */
   static OSIsoftServer piServer;

   /**
    * Current available JVM memory
    */
   static long AvailableMemory;


   public static void main(String[] args) {

      // Time keeping variables
      long lastUpdateTimeMs = 0;
      long currentTimeMs;

      //Start the webserver to accept json file
      RestFileServer restServer = new RestFileServer();
      restServer.start();

      //Unique name of the flexy
      String flexyName = getFlexyName();

      // Indicate the version number and that the application is starting
      Logger.LOG_CRITICAL("OSIsoft Connector v" + MAJOR_VERSION + "." + MINOR_VERSION + " starting");

      //Check that the flexy has a non-default name, stop the application if not
      if (flexyName.equals(DEFAULT_EWON_NAME))
      {
         Logger.LOG_ERR("Device name is set to \"eWON\" which is the default name");
         Logger.LOG_ERR("This device's name must be changed from default");
         Logger.LOG_ERR("Application aborting due to default name use");
         System.exit(0);
      }

      try {
         piConfig = new OSIsoftConfig(CONNECTOR_CONFIG_FILENAME);
      } catch (JSONException e) {
         Logger.LOG_ERR(CONNECTOR_CONFIG_FILENAME + " is malformed");
         Logger.LOG_EXCEPTION(e);
         System.exit(0);
      }

      // Set the path to the directory holding the certificate for the server
      // Only needed if the certificate is self signed
      setCertificatePath(piConfig.getCertificatePath());
      setHttpTimeouts();

      int res = OSIsoftServer.NO_ERROR;
      piServer = new OSIsoftServer(piConfig.getServerIP(), piConfig.getServerLogin(), piConfig.getServerWebID(), flexyName);

      do {
         try {
            res = piServer.initTags(piConfig.getTags());
         } catch (JSONException e) {
            Logger.LOG_ERR("Linking eWON tags to OSIsoft PI server failed");
            Logger.LOG_EXCEPTION(e);
         }
      } while (res != OSIsoftServer.NO_ERROR);

      DataPoster datathread = new DataPoster();
      datathread.start();

      // Infinite loop
      while (true) {

         AvailableMemory = Runtime.getRuntime().freeMemory();
         currentTimeMs = System.currentTimeMillis();

         if ((currentTimeMs - lastUpdateTimeMs) >= piConfig.getCycleTimeMs()) {

            // Update the last update time
            lastUpdateTimeMs = currentTimeMs;

            String time = piServer.convertTimeString(new Date());
            for (int i = 0; i < piConfig.getTags().size(); i++) {
               if(AvailableMemory < MINIMUM_MEMORY)
               {
                  ((Tag) piConfig.getTags().get(i)).trimOldestEntries(TAGS_TO_TRIM);
               }
               ((Tag) piConfig.getTags().get(i)).recordTagValue(time);
            }

            Thread.yield();
         }
      }
   }

   /**
    * Reads the unique name given to the flexy
    * @return Name of the Flexy
    */
   private static String getFlexyName()
   {
      String res = "";
      SysControlBlock SCB;
      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);
         res = SCB.getItem("Identification");
      } catch (Exception e) {
         Logger.LOG_ERR("Error reading eWON's name");
      }
      return res;
   }

   /**
    * Sets the directory that the eWON uses to check for SSL Certificates
    * @param path directory path where certificate is stored
    */
   private static void setCertificatePath(String path) {

      SysControlBlock SCB;
      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);
         if(!SCB.getItem("HttpCertDir").equals(path))
         {
            SCB.setItem("HttpCertDir", path);
            SCB.saveBlock(true);
         }
      } catch (Exception e) {
         Logger.LOG_ERR("Setting certificate directory failed");
         System.exit(0);
      }
   }


   /**
    * Sets the http timeouts
    * Note: This changes the eWON's global HTTP timeouts and stores
    *       these values in NV memory.
    */
   private static void setHttpTimeouts() {
      SysControlBlock SCB;
      boolean needsSave = false;
      final String HTTPS_TIMEOUT_S = "2";
      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);

         if (!SCB.getItem("HTTPC_SDTO").equals(HTTPS_TIMEOUT_S))
         {
            SCB.setItem("HTTPC_SDTO", HTTPS_TIMEOUT_S);
            needsSave = true;
         }

         if (!SCB.getItem("HTTPC_RDTO").equals(HTTPS_TIMEOUT_S))
         {
            SCB.setItem("HTTPC_RDTO", HTTPS_TIMEOUT_S);
            needsSave = true;
         }

         if (needsSave) SCB.saveBlock(true);

      } catch (Exception e) {
         Logger.LOG_ERR("Setting timeouts failed. Application ending");
         System.exit(0);
      }
   }
}
