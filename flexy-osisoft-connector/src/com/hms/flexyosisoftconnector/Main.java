package com.hms.flexyosisoftconnector;
import com.ewon.ewonitf.SysControlBlock;
import com.hms.flexyosisoftconnector.JSON.JSONException;

/**
 * eWON Flexy java demo for OSIsoft Server
 *
 * This demo reads multiple tag values from an eWON Flexy IO Server and POSTs
 * them to an OSIsoft PI Server.
 *
 * HMS Industrial Networks Inc. Solution Center
 *
 * @author thk
 *
 */
public class Main {

   static OSIsoftConfig piConfig;

   static OSIsoftServer piServer;

   //Application Version Numbers
   static final int MajorVersion = 0;
   static final int MinorVersion = 1;

   //Filename of connector config file
   static String connectorConfigFilename= "/usr/ConnectorConfig.json";

   public static void main(String[] args) {

      // Time keeping variables
      long lastUpdateTimeMs = 0;
      long currentTimeMs;

      // Indicate the version number and that the application is starting
      Logger.LOG_CRITICAL("OSIsoft Connector v" + MajorVersion + "." + MinorVersion + " starting");

      try {
         piConfig = new OSIsoftConfig(connectorConfigFilename);
      } catch (JSONException e) {
         Logger.LOG_ERR(connectorConfigFilename + " is malformed");
         Logger.LOG_EXCEPTION(e);
         System.exit(0);
      }

      // Set the path to the directory holding the certificate for the server
      // Only needed if the certificate is self signed
      setCertificatePath(piConfig.getCertificatePath());
      setHttpTimeouts();

      int res = OSIsoftServer.NO_ERROR;
      piServer = new OSIsoftServer(piConfig.getServerIP(), piConfig.getServerLogin(), piConfig.getServerWebID());

      do {
         try {
            res = piServer.initTags(piConfig.getTags());
         } catch (JSONException e) {
            Logger.LOG_ERR("Linking eWON tags to OSIsoft PI server failed");
            Logger.LOG_EXCEPTION(e);
         }
      } while (res != OSIsoftServer.NO_ERROR);

      // Infinite loop
      while (true) {

         currentTimeMs = System.currentTimeMillis();

         if ((currentTimeMs - lastUpdateTimeMs) >= piConfig.getCycleTimeMs()) {

            // Update the last update time
            lastUpdateTimeMs = currentTimeMs;

            // Post all tags in Tags
            piServer.postTagsLive(piConfig.getTags());

         }
      }
   }



   // Sets the directory that the eWON uses to check for SSL Certificates
   private static void setCertificatePath(String path) {

      SysControlBlock SCB;

      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);
         SCB.setItem("HttpCertDir", path);
         SCB.saveBlock(true);
      } catch (Exception e) {
         Logger.LOG_ERR("Setting certificate directory failed");
         System.exit(0);
      }
   }

   // Sets the http timeouts
   // Note: This changes the eWON's global HTTP timeouts and stores
   //       these values in NV memory.
   private static void setHttpTimeouts() {
      SysControlBlock SCB;
      final String HTTPS_TIMEOUT_S = "2";
      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);
         SCB.setItem("HTTPC_SDTO", HTTPS_TIMEOUT_S);
         SCB.setItem("HTTPC_RDTO", HTTPS_TIMEOUT_S);
         SCB.saveBlock(true);
      } catch (Exception e) {
         Logger.LOG_ERR("Setting timeouts failed. Application ending");
         System.exit(0);
      }
   }
}
