package com.hms.flexyosisoftconnector;

import java.util.ArrayList;
import com.hms.flexyosisoftconnector.JSON.*;

/**
 * This class contains the configuration for the Flexy <-> OSIsoft connection
 *
 * <p>HMS Networks Inc. Solution Center
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

  // String converted to int for use in switch statement
  // Either (0) pre2019 for piwebapi or (1) post2019 for OMF
  int communicationType;

  // Strings you can get off of the json file
  String pre2019 = "piwebapi";
  String post2019 = "omf";
  // Strings set by web page config tool
  String secondPre2019 = "PI Web API 2018 and older";
  String secondPost2019 = "PI Web API 2019+";

  // Expose these for switch statement conditionals
  public static final int piwebapi = 0;
  public static final int omf = 1;

  // List of tags

  public static ArrayList tags = new ArrayList();

  // Build the OSIsoft config from a configuration json file
  public OSIsoftConfig(String configFile) throws JSONException {

    // Read in the JSON file to a string
    JSONTokener JsonT = new JSONTokener(FileReader.readFile("file://" + configFile));

    // Build a JSON Object containing the whole file
    JSONObject configJSON = new JSONObject(JsonT);

    // Build a JSON Object containing the "ServerConfig"
    JSONObject serverConfig = configJSON.getJSONObject("ServerConfig");

    // Set the server config parameters
    piServerIP = serverConfig.getString("IP");
    dataServerWebID = serverConfig.getString("WebID");
    piServerLogin = serverConfig.getString("Credentials");

    // Build a JSON Object containing the "eWONConfig"
    JSONObject eWONConfig = configJSON.getJSONObject("eWONConfig");
    eWONCertificatePath = eWONConfig.getString("CertificatePath");

    // Build a JSON Object containing the "AppConfig"
    JSONObject appConfig = configJSON.getJSONObject("AppConfig");
    cycleTimeMs = appConfig.getInt("CycleTimeMs");

    String tmpCommunicationType = appConfig.getString("CommunicationType");
    if (tmpCommunicationType.equalsIgnoreCase(pre2019)
        || tmpCommunicationType.equalsIgnoreCase(secondPre2019)) {
      communicationType = piwebapi;
    } else if (tmpCommunicationType.equalsIgnoreCase(post2019)
        || tmpCommunicationType.equalsIgnoreCase(secondPost2019)) {
      communicationType = omf;
    } else {
      // can't do anything without a valid communication type..
      Logger.LOG_ERR("Invalid communication type in config json");
      Logger.LOG_ERR(
          "Change the communication Type in config json to one of the valid options in the readme and restart the connector.");
      Logger.LOG_ERR("OSIsoft connector Shutting down.");
      System.exit(1);
    }

    boolean shouldLogDuplicateValues = appConfig.getBoolean("PostDuplicateTagValues");

    if (appConfig.has("LoggingLevel")) {
      boolean res = Logger.SET_LOG_LEVEL(appConfig.getInt("LoggingLevel"));
      if (!res) {
        Logger.LOG_ERR("Invalid logging level specified");
      }
    }

    // Build a JSON Array containing the tag names
    JSONArray tagNames = configJSON.getJSONArray("TagList");

    // For each tagname in the config file create a tag and add it to
    // the arraylist of tags
    for (int i = 0; i < tagNames.length(); i++) {
      Tag tag = new Tag(tagNames.getString(i), shouldLogDuplicateValues);
      if (tag.isValidTag()) {
        tags.add(tag);
      } else {
        Logger.LOG_ERR("Tag \"" + tag.getTagName() + "\" does not exist on this eWON");
      }
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

  // Returns the list of tags
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

  public int getCommunicationType() {
    return communicationType;
  }
}
