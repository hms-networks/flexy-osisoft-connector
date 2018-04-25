import java.util.ArrayList;

/**
 * 
 * This class contains the configuration 
 * for the Flexy <-> OSIsoft connection
 * 
 * HMS Industrial Networks Inc. Solution Center
 * 
 * @author thk
 *
 */
public class OSIsoftConfig {
 
    // IP address of OSIsoft PI Server
    String piServerIP;
    
    // WebID of the OSIsoft PI Dataserver
    String dataServerWebID;
    
    // Your BASE64 encoded BASIC authentication credentials
    // Visit https://www.base64encode.org/
    // Encode: "username:password"
    String piServerLogin;

    // Path to the directory containing your OSIsoft server's certificate
    String eWONCertificatePath;

    // Update rate for all tags in milliseconds
    int cycleTimeMs;
    
    // List of tags 
    ArrayList tags = new ArrayList();


    // Build the OSIsoft config from a configuration json file
    public OSIsoftConfig(String configFile) throws JSONException {
       
       //Read in the JSON file to a string 
       JSONTokener JsonT = new JSONTokener(FileReader.readFile("file://" + configFile));
       
       //Build a JSON Object containing the whole file
       JSONObject configJSON = new JSONObject(JsonT);
       
       //Build a JSON Object containing the "ServerConfig"
       JSONObject serverConfig = configJSON.getJSONObject("ServerConfig");
       
       //Set the server config parameters
       piServerIP = serverConfig.getString("IP");
       dataServerWebID = serverConfig.getString("WebID");
       piServerLogin = serverConfig.getString("Credentials");
       
       //Build a JSON Object containing the "eWONConfig"
       JSONObject eWONConfig = configJSON.getJSONObject("eWONConfig");
       eWONCertificatePath = eWONConfig.getString("CertificatePath");
       
       //Build a JSON Object containing the "AppConfig"
       JSONObject appConfig = configJSON.getJSONObject("AppConfig");
       cycleTimeMs = appConfig.getInt("CycleTimeMs");
     
       //Build a JSON Array containing the tag names
       JSONArray tagNames = configJSON.getJSONArray("TagList");
       
       //For each tagname in the config file create a tag and add it to 
       //the arraylist of tags
       for(int i = 0; i < tagNames.length(); i++) {
          tags.add(new Tag(tagNames.getString(i)));
       }
    }
    
    // Returns the IP Address of the OSIsoft Server
    public String getServerIP() {
       return piServerIP;
    }
    
    // Returns the BASE64 encoded authentication credentials
    public String getServerLogin() {
       return piServerLogin;
    }
    
    // Returns the Web ID of the dataserver
    public String getServerWebID() {
       return dataServerWebID;
    }
    
    //Returns the list of tags
    public ArrayList getTags() {
       return tags;
    }
    
    // Returns the path to the OSIsoft server's certificate
    public String getCertificatePath() {
       return eWONCertificatePath;
    }
    
    // Returns the cycle time in milliseconds
    public int getCycleTimeMs() {
       return cycleTimeMs;
    }

}
