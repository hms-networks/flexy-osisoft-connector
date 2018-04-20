import java.util.ArrayList;
import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.ScheduledActionManager;
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

   /*--------------------------------------------------------------------------------
   **********************************************************************************
   ** Server Parameters
   **********************************************************************************
   */

   // IP address of OSIsoft Server
   static String targetIP = "192.168.0.124";

   // Your BASE64 encoded BASIC authentication credentials
   // Visit https://www.base64encode.org/
   // Encode: "username:password"
   static String authCredentials = "UEktU2VydmVyOk1hbmNoZXN0ZXIxMjMh";

   // Path to the directory containing your OSIsoft server's certificate
   static String eWONCertificatePath = "/usr/Certificates";

   // URL for the OSIsoft Server
   // Do not change
   static String targetURL = "https://" + targetIP + "/piwebapi/streams/";

   // Post Headers
   // Do not change
   static String postHeaders = "Authorization=Basic " + authCredentials + "&Content-Type=application/json";

   // End Server Parameters
   // --------------------------------------------------------------------------------

   // Update rate for all tags in milliseconds
   static int cycleTimeMs = 1000;

   public static void main(String[] args) {

      /*--------------------------------------------------------------------------------
      **********************************************************************************
      ** Tag Parameters
      **********************************************************************************
      */

      ArrayList tags = new ArrayList();

      Tag exampleTag1 = new Tag("ExampleTag1",
            "A0E_6AQvaWb6Emua0jP1uU4Mg3_I9yFg_6BGrQwgAJwekvw-RujgJKJ2kyYeSxDRtIAQAUEktU0VSVkVSLVBDXERBVEFCQVNFMVxURVNUfFRFU1QgVkFMVUU");
      tags.add(exampleTag1);

      Tag exampleTag2 = new Tag("ExampleTag2",
            "A0E_6AQvaWb6Emua0jP1uU4Mg3_I9yFg_6BGrQwgAJwekvw6xirSCtPFkKs-ZfpFM_78wUEktU0VSVkVSLVBDXERBVEFCQVNFMVxURVNUfEVYQU1QTEVUQUcy");
      tags.add(exampleTag2);

      Tag exampleTag3 = new Tag("ExampleTag3",
            "A0E_6AQvaWb6Emua0jP1uU4Mg3_I9yFg_6BGrQwgAJwekvwwG6znx7wwkyYapia14gTxAUEktU0VSVkVSLVBDXERBVEFCQVNFMVxURVNUfEVYQU1QTEVUQUcz");
      tags.add(exampleTag3);

      Tag exampleTag4 = new Tag("ExampleTag4",
            "A0E_6AQvaWb6Emua0jP1uU4Mg3_I9yFg_6BGrQwgAJwekvw8wyi8vpTeEeFRR7WdeC_FwUEktU0VSVkVSLVBDXERBVEFCQVNFMVxURVNUfEVYQU1QTEVUQUc0");
      tags.add(exampleTag4);

      // End Tag Parameters
      // --------------------------------------------------------------------------------

      // Time keeping variables
      long lastUpdateTimeMs = 0;
      long currentTimeMs;

      // Set the path to the directory holding the certificate for the server
      // Only needed if the certificate is self signed
      setCertificatePath(eWONCertificatePath);

      // Infinite loop
      while (true) {

         currentTimeMs = System.currentTimeMillis();

         if ((currentTimeMs - lastUpdateTimeMs) >= cycleTimeMs) {

            // Update the last update time
            lastUpdateTimeMs = currentTimeMs;

            // Post all tags in Tags
            for (int i = 0; i < tags.size(); i++) {
               postTag((Tag) tags.get(i));
            }
         }
      }
   }

   // Posts a tag value to the OSIsoft server
   public static void postTag(Tag tag) {
      try {
         ScheduledActionManager.RequestHttpX(targetURL + tag.getWebID() + "/Value", "Post", postHeaders,
               buildBody(tag.getTagValue()), "", "");
      } catch (EWException e) {
         e.printStackTrace();
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

   // Builds and returns the body content
   // Hardcoded JSON payload
   public static String buildBody(String value) {
      String jsonBody = "{\r\n" + "    \"Timestamp\": \"1970-01-01T00:00:00Z\",\r\n" + "    \"Value\": " + value
            + ",\r\n" + "    \"UnitsAbbreviation\": \"\",\r\n" + "    \"Good\": true,\r\n"
            + "    \"Questionable\": false,\r\n" + "}";
      return jsonBody;
   }
}
