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
    String piServerIP    = "192.168.0.124";
    
    // WebID of the OSIsoft PI Dataserver
    String dataServerWebID = "s0U1IjG6kMOEW7mxyHCuX2mAUEktU0VSVkVSLVBD";
    
    // Your BASE64 encoded BASIC authentication credentials
    // Visit https://www.base64encode.org/
    // Encode: "username:password"
    String piServerLogin = "UEktU2VydmVyOk1hbmNoZXN0ZXIxMjMh";

    // Path to the directory containing your OSIsoft server's certificate
    String eWONCertificatePath = "/usr/Certificates";

    // Update rate for all tags in milliseconds
    int cycleTimeMs = 1000;
    
    // List of tags 
    ArrayList tags = new ArrayList();


    public OSIsoftConfig() {
       
       Tag exampleTag1 = new Tag("ExampleTag1");
       tags.add(exampleTag1);

       Tag exampleTag2 = new Tag("ExampleTag2");
       tags.add(exampleTag2);

       Tag exampleTag3 = new Tag("ExampleTag3");
       tags.add(exampleTag3);

       Tag exampleTag4 = new Tag("ExampleTag4");
       tags.add(exampleTag4);
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
