import java.util.ArrayList;
import com.ewon.ewonitf.SysControlBlock;

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
public class OSIsoft_DemoMain {

   static OSIsoftConfig piConfig;

   static OSIsoftServer piServer;

   public static void main(String[] args) {

      // Time keeping variables
      long lastUpdateTimeMs = 0;
      long currentTimeMs;

      piConfig = new OSIsoftConfig();
      
      // Set the path to the directory holding the certificate for the server
      // Only needed if the certificate is self signed
      setCertificatePath(piConfig.getCertificatePath());
      
      piServer = new OSIsoftServer(piConfig.getServerIP(), piConfig.getServerLogin(), piConfig.getServerWebID());
      
      for (int i = 0; i < piConfig.getTags().size(); i++) {
         try {
            piServer.setTagWebId((Tag) piConfig.getTags().get(i));
         } catch (JSONException e) {
            e.printStackTrace();
         }
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
