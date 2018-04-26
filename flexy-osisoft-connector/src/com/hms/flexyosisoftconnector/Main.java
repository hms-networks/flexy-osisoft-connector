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
   
   //Filename of connector config file
   static String connectorConfigFilename= "/usr/ConnectorConfig.json";

   public static void main(String[] args) {

      // Time keeping variables
      long lastUpdateTimeMs = 0;
      long currentTimeMs;

      try {
         piConfig = new OSIsoftConfig(connectorConfigFilename);
      } catch (JSONException e1) {
         System.out.println("Error: " + connectorConfigFilename + " is malformed");
         e1.printStackTrace();
         System.exit(0);
      }
      
      // Set the path to the directory holding the certificate for the server
      // Only needed if the certificate is self signed
      setCertificatePath(piConfig.getCertificatePath());
      
      piServer = new OSIsoftServer(piConfig.getServerIP(), piConfig.getServerLogin(), piConfig.getServerWebID());
      
      try {
         piServer.initTags(piConfig.getTags());
      } catch (JSONException e) {
         System.out.println("Error: Linking eWON tags to OSIsoft PI server failed");
         e.printStackTrace();
      }
      // Infinite loop
      while (true) {

         currentTimeMs = System.currentTimeMillis();

         if ((currentTimeMs - lastUpdateTimeMs) >= piConfig.getCycleTimeMs()) {

            // Update the last update time
            lastUpdateTimeMs = currentTimeMs;

            // Post all tags in Tags
            for (int i = 0; i < piConfig.getTags().size(); i++) {
               piServer.postTag((Tag) piConfig.getTags().get(i));
            }
         }
      }
   }

  

   // Sets the directory that the eWON uses to check for SSL Certificates
   public static void setCertificatePath(String path) {

      SysControlBlock SCB;

      try {
         SCB = new SysControlBlock(SysControlBlock.SYS);
         SCB.setItem("HttpCertDir", path);
         SCB.saveBlock(true);
      } catch (Exception e) {
         System.out.println("Error: Setting certificate directory failed");
         System.exit(0);
      }
   }   
}
