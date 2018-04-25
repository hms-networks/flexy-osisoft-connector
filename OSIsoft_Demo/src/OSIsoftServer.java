import java.io.File;

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
   
   // WebID for the database being used
   private String dbWebID;
   
   // URL for the OSIsoft Server
   private String targetURL;

   // Post Headers
   private String postHeaders;
   
   public OSIsoftServer(String ip, String login, String webID) {
      serverIP = ip;
      authCredentials = login;
      dbWebID = webID;
      targetURL = "https://" + serverIP + "/piwebapi/streams/";
      postHeaders = "Authorization=Basic " + authCredentials + "&Content-Type=application/json";
   }
   
   // Looks up and sets a tags PI Point web id.  If the tag does not exist
   // in the data server a PI Point is created for that tag.
   public void setTagWebId(Tag tag) throws JSONException {
      
      //HTTPS responses are stored in this file
      String responseFilename = "/usr/response.txt";
      
      //url for the dataserver's pi points
      String url = "https://" + serverIP +"/piwebapi/dataservers/" + dbWebID + "/points";

      //Check if the tag already exists in the dataserver
      try {
         //RequestHttpX stores the response in a file
         ScheduledActionManager.RequestHttpX(url + "?nameFilter=" + tag.getTagName(), "Get", postHeaders,"", "", responseFilename);
      } catch (EWException e) {
         e.printStackTrace();
      }
      
      //Parse the JSON response and retrieve the JSON Array of items
      JSONTokener JsonT = new JSONTokener(FileReader.readFile("file://" +responseFilename));
      JSONObject requestResponse = new JSONObject(JsonT);
      JSONArray items = requestResponse.getJSONArray("Items");
      
      if (items.length() > 0) {
         //tag exists
         tag.setWebID(items.getJSONObject(0).getString("WebId"));
      } else {
         //tag does not exist and must be created
         try {
            //RequestHttpX stores the response in a file
            ScheduledActionManager.RequestHttpX(url, "Post", postHeaders,buildNewPointBody(tag.getTagName()), "", responseFilename);
         } catch (EWException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
         
         //The WebID is sent back in the headers of the previous post
         //however, there is no mechanism currently to retrieve it so 
         //another request must be issued.
         try {
            //RequestHttpX stores the response in a file
            ScheduledActionManager.RequestHttpX(url + "?nameFilter=" + tag.getTagName(), "Get", postHeaders,"", "", responseFilename);
         } catch (EWException e) {
            e.printStackTrace();
         }
         
         //Parse the JSON response and retrieve the JSON Array of items
         JsonT = new JSONTokener(FileReader.readFile("file://" +responseFilename));
         requestResponse = new JSONObject(JsonT);
         items = requestResponse.getJSONArray("Items");
         if (items.length() > 0) {
            //tag exists
            tag.setWebID(items.getJSONObject(0).getString("WebId"));
         } else {
            //tag does not exist, error
            System.out.println("Error: PI Point creation failed");
         } 
      }
   
      //Delete the https response file
      File file = new File(responseFilename);
      if(!file.delete())
      {
         System.out.println("Error: Failed to delete the HTTPS response file");
      }         
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
   
   private static String buildNewPointBody(String tagName) {
      String jsonBody = "{\r\n" + 
            "  \"Name\": \""+ tagName +"\",\r\n" + 
            "  \"Descriptor\": \""+tagName+"\",\r\n" + 
            "  \"PointClass\": \"classic\",\r\n" + 
            "  \"PointType\": \"Int32\",\r\n" +  
            "  \"EngineeringUnits\": \"\",\r\n" + 
            "  \"Step\": false,\r\n" + 
            "  \"Future\": false\r\n" + 
            "}";
      return jsonBody;
   }
}
