package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.SysControlBlock;
import com.hms_networks.americas.sc.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.json.JSONObject;
import com.hms_networks.americas.sc.json.JSONTokener;
import com.hms_networks.americas.sc.logging.Logger;
import java.io.IOException;

/**
 * This class contains the configuration for the Flexy <-> OSIsoft connection
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftConfig {

  /** IP address of OSIsoft PI Server */
  private static String piServerIP;

  /** WebID of the OSIsoft PI Dataserver */
  private static String dataServerWebID;

  /**
   * Your BASE64 encoded BASIC authentication credentials. Visit https://www.base64encode.org/
   * Encode: "username:password"
   */
  private static String piServerLogin;

  /** Path to the directory containing your OSIsoft server's certificate */
  private static String ewonCertificatePath;

  /** Update rate for all tags in milliseconds */
  private static int cycleTimeMs;

  /**
   * String converted to int for use in switch statement. Either (0) pre2019 for piwebapi or (1)
   * post2019 for OMF
   */
  private static int communicationType;

  /** The string stored in the config file when a user sets it to PIWebAPI */
  private static String pre2019 = "piwebapi";

  /** The string stored in the config file when a user sets it to OMF */
  private static String post2019 = "omf";

  /** The string stored in the config file when chosing OMF in the web config page */
  private static String secondPre2019 = "PI Web API 2018 and older";

  /** The string stored in the config file when chosing PIWebAPI in the web config page */
  private static String secondPost2019 = "PI Web API 2019+";

  /** PIWebAPI comminucation type */
  public static final int piwebapi = 0;
  /** OMF communication type */
  public static final int omf = 1;

  /** Error message to be displayed on invalid communication type */
  public static final String COM_ERR_MSG = "Error: Communication type is not valid.";

  /** URL for the OSIsoft Server */
  private static String targetURL;

  /** URL for the OMF endpoint */
  private static String omfUrl;

  /** Unique name of this flexy */
  private static String flexyName;

  /** Post headers for PIWebAPI */
  private static String postHeaders;

  /** Post headers for OMF */
  private static String omfPostHeaders;

  /** Unique type for OMF messages from this flexy */
  private static String typeID;

  /**
   * Initializes the configuration class and all required information.
   *
   * @param configFile The path to where the configuration file is stored on the device.
   * @throws JSONException Throws when JSON is malformed
   * @throws IOException Throws when unable to read the configuration file
   */
  public static void initConfig(String configFile) throws JSONException, IOException {

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

    targetURL = "https://" + piServerIP + "/piwebapi/";
    omfUrl = targetURL + "omf";
    typeID = "HMS-type-" + flexyName;
    postHeaders =
        "Authorization=Basic "
            + OSIsoftConfig.getServerLogin()
            + "&Content-Type=application/json&X-Requested-With=JSONHttpRequest";
    omfPostHeaders =
        "Authorization=Basic "
            + OSIsoftConfig.getServerLogin()
            + "&Content-Type=application/json"
            + "&X-Requested-With=JSONHttpRequest"
            + "&messageformat=json&omfversion=1.1";
    setFlexyName();
  }

  /**
   * Get the server ip address.
   *
   * @return the IP Address of the OSIsoft Server
   */
  public static String getServerIP() {
    return piServerIP;
  }

  /**
   * Get the authentication credentials.
   *
   * @return the BASE64 encoded authentication credentials
   */
  public static String getServerLogin() {
    return piServerLogin;
  }

  /**
   * Get the server web ID
   *
   * @return the Web ID of the dataserver
   */
  public static String getServerWebID() {
    return dataServerWebID;
  }

  /**
   * Get the certificate path
   *
   * @return the path to the OSIsoft server's certificate
   */
  public static String getCertificatePath() {
    return ewonCertificatePath;
  }

  /**
   * Get the cycle time in milliseconds.
   *
   * @return the cycle time in milliseconds
   */
  public static int getCycleTimeMs() {
    return cycleTimeMs;
  }

  /**
   * Get the target URL
   *
   * @return the target URL
   */
  public static String getTargetURL() {
    return targetURL;
  }

  /**
   * Get the type ID
   *
   * @return the type ID
   */
  public static String getTypeID() {
    return typeID;
  }

  /**
   * Get the flexy name
   *
   * @return the flexy name
   */
  public static String getFlexyName() {
    return flexyName;
  }

  /**
   * Set the flexy device name
   */
  public static void setFlexyName() {
    String res = "";
    SysControlBlock SCB;
    try {
      SCB = new SysControlBlock(SysControlBlock.SYS);
      res = SCB.getItem("Identification");
    } catch (Exception e) {
      Logger.LOG_SERIOUS("Error reading eWON's name");
    }
    flexyName = res;
  }


  /**
   * Get the OMF post Headers
   *
   * @return the OMF post Headers
   */
  public static String getOmfPostHeaders() {
    return omfPostHeaders;
  }

  /**
   * Get the legacy PIWebAPI post headers
   *
   * @return the legacy PIWebAPI post headers
   */
  public static String getPostHeaders() {
    return postHeaders;
  }

  /**
   * Get the OMF URL
   *
   * @return the OMF URL
   */
  public static String getOmfUrl() {
    return omfUrl;
  }

  /**
   * Get the communication type.
   *
   * @return the communication type
   */
  public static int getCommunicationType() {
    return communicationType;
  }
}
