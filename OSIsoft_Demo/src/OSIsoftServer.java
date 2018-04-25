import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.ScheduledActionManager;

/**
 * 
 * Class object for an OSIsoft PI Server.
 * 
 * HMS Industrial Networks Inc. Solution Center
 * 
 * @author thk
 *
 */

public class OSIsoftServer {

   // IP address of OSIsoft Server
   private String serverIP;

   // BASE64 encoded BASIC authentication credentials
   private String authCredentials;
   
   // URL for the OSIsoft Server
   private String targetURL;

   // Post Headers
   private String postHeaders;
   
   public OSIsoftServer(String ip, String login) {
      serverIP = ip;
      authCredentials = login;
      targetURL = "https://" + serverIP + "/piwebapi/streams/";
      postHeaders = "Authorization=Basic " + authCredentials + "&Content-Type=application/json";
   }
   
   
   // Posts a tag value to the OSIsoft server
   public void postTag(Tag tag) {
      try {
         ScheduledActionManager.RequestHttpX(targetURL + tag.getWebID() + "/Value", "Post", postHeaders,
               buildBody(tag.getTagValue()), "", "");
      } catch (EWException e) {
         e.printStackTrace();
      }
   }
   
   // Builds and returns the body content
   // Hardcoded JSON payload
   private static String buildBody(String value) {
      String jsonBody = "{\r\n" + "    \"Timestamp\": \"1970-01-01T00:00:00Z\",\r\n" + "    \"Value\": " + value
            + ",\r\n" + "    \"UnitsAbbreviation\": \"\",\r\n" + "    \"Good\": true,\r\n"
            + "    \"Questionable\": false,\r\n" + "}";
      return jsonBody;
   }
   
   // Reads a file and returns it as a string
   public static String readFile(String path) {

      FileConnection fconn = null;
      InputStream is       = null;
      int bufferSize       = 10000;

      try {
         fconn = (FileConnection) Connector.open(path);
         // If no exception is thrown, then the URI is valid, but the file may or may not
         // exist.

         is = fconn.openInputStream();
         int Read;
         byte[] Buffer = new byte[bufferSize];

         Read = is.read(Buffer);
         fconn.close();
         return new String(Buffer, 0, Read);

      } catch (IOException ioe) {
         System.out.println("Error: " + ioe.toString());
         //Returns an empty string in error case
         return "";
      }
   }
}
