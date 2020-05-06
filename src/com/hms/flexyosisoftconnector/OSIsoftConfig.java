package com.hms.flexyosisoftconnector;

import java.util.ArrayList;
import com.hms.flexyosisoftconnector.JSON.*;

/**
 * This class contains the configuration for the Flexy <-> OSIsoft connection
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftConfig {

  /** IP address of OSIsoft PI Server */
  String piServerIP;

  /** WebID of the OSIsoft PI Dataserver */
  String dataServerWebID;

  /**
   * Your BASE64 encoded BASIC authentication credentials. Visit https://www.base64encode.org/
   * Encode: "username:password"
   */
  String piServerLogin;

  /** Path to the directory containing your OSIsoft server's certificate */
  String ewonCertificatePath;

  /** Update rate for all tags in milliseconds */
  int cycleTimeMs;

  /**
   * String converted to int for use in switch statement. Either (0) pre2019 for piwebapi or (1)
   * post2019 for OMF
   */
  int communicationType;

  /** The string stored in the config file when a user sets it to PIWebAPI */
  String pre2019 = "piwebapi";

  /** The string stored in the config file when a user sets it to OMF */
  String post2019 = "omf";

  /** The string stored in the config file when chosing OMF in the web config page */
  String secondPre2019 = "PI Web API 2018 and older";

  /** The string stored in the config file when chosing PIWebAPI in the web config page */
  String secondPost2019 = "PI Web API 2019+";

  /** PIWebAPI comminucation type */
  public static final int piwebapi = 0;
  /** OMF communication type */
  public static final int omf = 1;

  public static ArrayList tags = new ArrayList();

  // Build the OSIsoft config from a configuration json file
  public OSIsoftConfig(String configFile) throws JSONException {

    // Read in the JSON file to a string
    JSONTokener JsonT = new JSONTokener(FileAccessManager.readFileToString(configFile));

    // Build a JSON Object containing the whole file
    JSONObject configJSON = new JSONObject(JsonT);

    // Build a JSON Object containing the "ServerConfig"
    JSONObject serverConfig = configJSON.getJSONObject("ServerConfig");

    // Set the server config parameters
    piServerIP = serverConfig.getString("IP");
    dataServerWebID = serverConfig.getString("WebID");
    piServerLogin = serverConfig.getString("Credentials");

    // Build a JSON Object containing the "eWONConfig"
    JSONObject ewonConfig = configJSON.getJSONObject("eWONConfig");
    ewonCertificatePath = ewonConfig.getString("CertificatePath");

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
      Logger.LOG_SERIOUS("Invalid communication type in config json");
      Logger.LOG_SERIOUS(
          "Change the communication Type in config json to one of the valid options in the readme and restart the connector.");
      Logger.LOG_SERIOUS("OSIsoft connector Shutting down.");
      System.exit(1);
    }

    boolean shouldLogDuplicateValues = appConfig.getBoolean("PostDuplicateTagValues");

    if (appConfig.has("LoggingLevel")) {
      boolean res = Logger.SET_LOG_LEVEL(appConfig.getInt("LoggingLevel"));
      if (!res) {
        Logger.LOG_SERIOUS("Invalid logging level specified");
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

  /**
   * Get the server ip address.
   *
   * @return the IP Address of the OSIsoft Server
   */
  public String getServerIP() {
    return piServerIP;
  }

  /**
   * Get the authentication credentials.
   *
   * @return the BASE64 encoded authentication credentials
   */
  public String getServerLogin() {
    return piServerLogin;
  }

  /**
   * Get the server web ID
   *
   * @return the Web ID of the dataserver
   */
  public String getServerWebID() {
    return dataServerWebID;
  }

  // Returns the list of tags
  public ArrayList getTags() {
    return tags;
  }

  /**
   * Get the certificate path
   *
   * @return the path to the OSIsoft server's certificate
   */
  public String getCertificatePath() {
    return ewonCertificatePath;
  }

  /**
   * Get the cycle time in milliseconds.
   *
   * @return the cycle time in milliseconds
   */
  public int getCycleTimeMs() {
    return cycleTimeMs;
  }

  public int getCommunicationType() {
    return communicationType;
  }
}
