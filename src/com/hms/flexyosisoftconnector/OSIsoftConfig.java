package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.SysControlBlock;
import com.ewon.ewonitf.TagControl;
import com.hms_networks.americas.sc.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.json.JSONObject;
import com.hms_networks.americas.sc.json.JSONTokener;
import com.hms_networks.americas.sc.logging.Logger;
import java.io.IOException;

/**
 * This class contains the configuration for the Flexy to OSIsoft connection.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftConfig {

  /** Number of seconds queue can fall beind before warnings are logged. */
  public static final int WARNING_LIMIT_QUEUE_BEHIND_SECONDS = 15;

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

  /**
   * String converted to int for use in switch statement. Either (0) pre2019 for piwebapi or (1)
   * post2019 for OMF
   */
  private static int communicationType;

  /** The string stored in the config file when a user sets it to PIWebAPI */
  private static String pre2019 = "piwebapi";

  /** The string stored in the config file when a user sets it to OMF */
  private static String post2019 = "omf";

  /** The string stored in the config file when a user sets it to OMF via OCS */
  private static String omfOcs = "omfOcs";

  /** The string stored in the config file when chosing OMF in the web config page */
  private static String secondPre2019 = "PI Web API 2018 and older";

  /** The string stored in the config file when chosing PIWebAPI in the web config page */
  private static String secondPost2019 = "PI Web API 2019+";

  /** PIWebAPI comminucation type */
  public static final int PI_WEB_API = 0;
  /** OMF communication type */
  public static final int OMF = 1;
  /** OCS communication type */
  public static final int OCS = 2;

  /** Error message to be displayed on invalid communication type */
  public static final String COM_ERR_MSG = "Error: Communication type is not valid.";

  /** URL for the OSIsoft Server */
  private static String targetURL;

  /** URL for the OMF endpoint */
  private static String omfUrl;

  /** Unique name of this flexy */
  private static String flexyName;

  /** Flexy's serial number */
  private static String flexySerialNum;

  /** Post headers for PIWebAPI */
  private static String postHeaders;

  /** Post headers for OMF */
  private static String omfPostHeaders;

  /** Post headers for OCS */
  private static String ocsPostHeaders;

  /** Optional configuration to set the http timeout in seconds. */
  private static int httpTimeoutSeconds;

  /** default http timeout in seconds for if it is not set through configuration file. */
  private static final int HTTP_TIMEOUT_SECONDS_DEFAULT = 2;

  /** Key to access http timeout from config file JSON. */
  private static final String HTTP_TIMEOUT_SECONDS_KEY = "httpTimeoutSeconds";

  /** Unique type for OMF messages from this flexy */
  private static String typeID;

  /** Access token for OCS OMF. The token expires every hour. */
  private static String ocsOmfToken;

  /** OCS OMF URL for making requests to OCS. */
  private static String ocsUrl;

  /** Proxy URL to replace pi endpoint when needed */
  private static String proxyURL;

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

    String tmpCommunicationType = appConfig.getString("CommunicationType");
    if (tmpCommunicationType.equalsIgnoreCase(pre2019)
        || tmpCommunicationType.equalsIgnoreCase(secondPre2019)) {
      communicationType = PI_WEB_API;
      Logger.LOG_DEBUG(
          "The Flexy configuration file has been set to use PIWEBAPI as the communication type.");
    } else if (tmpCommunicationType.equalsIgnoreCase(post2019)
        || tmpCommunicationType.equalsIgnoreCase(secondPost2019)) {
      communicationType = OMF;
      Logger.LOG_DEBUG("The Flexy configuration file has been set to use OMF with PIWEBAPI.");
    } else if (tmpCommunicationType.equalsIgnoreCase(omfOcs)) {
      communicationType = OCS;
      setOcsToken();
      Logger.LOG_DEBUG("The Flexy configuration file has been set to use OMF OCS.");
    } else {
      // can't do anything without a valid communication type..
      Logger.LOG_SERIOUS("Invalid communication type in config json");
      Logger.LOG_SERIOUS(
          "Change the communication Type in config json to one of the valid options in the readme"
              + " and restart the connector.");
      Logger.LOG_SERIOUS("OSIsoft connector Shutting down.");
      System.exit(1);
    }

    if (appConfig.has("LoggingLevel")) {
      boolean res = Logger.SET_LOG_LEVEL(appConfig.getInt("LoggingLevel"));
      if (!res) {
        Logger.LOG_SERIOUS("Invalid logging level specified");
      }
    }

    if (appConfig.has(HTTP_TIMEOUT_SECONDS_KEY)) {
      httpTimeoutSeconds = appConfig.getInt("httpTimeoutSeconds");
      Logger.LOG_INFO(
          "HTTP timeout of " + httpTimeoutSeconds + " seconds retrieved from configuration file.");
    } else {
      httpTimeoutSeconds = HTTP_TIMEOUT_SECONDS_DEFAULT;
    }

    if (communicationType == OCS) {
      // grab OCS specific information from config file
      String namespace = "";
      String tenantId = "";

      namespace = serverConfig.getString("Namespace");
      tenantId = serverConfig.getString("TenantId");

      ocsUrl =
          "https://dat-b.osisoft.com/api/v1/Tenants/"
              + tenantId
              + "/Namespaces/"
              + namespace
              + "/omf";
    }

    if (serverConfig.has("ProxyURL")) {
      proxyURL = serverConfig.getString("ProxyURL");
      // if a proxy URL is present user defines endpoints
      targetURL = "https://" + piServerIP + "/" + proxyURL;
      omfUrl = targetURL;
    } else {
      // there is not proxy in use
      targetURL = "https://" + piServerIP + "/piwebapi/";
      omfUrl = targetURL + "omf";
    }

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
            + "&messageformat=json&omfversion=1.1"
            + "&action=Create";
    ocsPostHeaders =
        "Authorization=Bearer "
            + OSIsoftConfig.getOcsToken()
            + "&Content-Type=application/json"
            + "&messageformat=json&omfversion=1.1"
            + "&Producertoken="
            + OSIsoftConfig.getOcsToken()
            + "&action=Create";

    setFlexyName();
    readFlexySerial();
  }

  /**
   * The token will be retrieved in a BASIC script due to issues with the request when made in Java.
   * Assume the response file exists and read from it.
   */
  private static void setOcsToken() {
    // file name hardcoded in basic script and should never change.
    String tokenFile = "/usr/token.json";
    JSONTokener JsonT = null;
    String token = "";
    try {
      JsonT = new JSONTokener(FileAccessManager.readFileToString(tokenFile));
      // Build a JSON Object containing the whole file
      JSONObject tokenJSON = new JSONObject(JsonT);

      token = tokenJSON.getString("access_token");
    } catch (IOException e) {
      Logger.LOG_SERIOUS("Unable to read OCS token from https request response file.");
      Logger.LOG_EXCEPTION(e);
    } catch (JSONException e) {
      Logger.LOG_SERIOUS(
          "Unable to parse JSON in OCS token response file from OCS new token https request.");
      Logger.LOG_EXCEPTION(e);
    }

    // Set the access token
    ocsOmfToken = token;
  }

  /**
   * This function need to have a basic script running that is specified in the README. Updates the
   * OCS token if the OCS token tag is not set to 0. This tag will then be reset to 0.
   */
  public static void updateOcsToken() {

    /* Ensure the JVM and basic script are working together by checking the value of a tag is 0.
     * The basic script will increment the tag value by 1 every time a new request is sent.
     * The Java program should reset that tag to 0 after the request's response file is handled.
     */
    final int tagInitialValue = 0;

    // check tag to see if it is {@link TAG_INITIAL_VALUE}
    TagControl tc = null;
    try {
      tc = new TagControl("tokenReq");

    } catch (EWException e) {
      Logger.LOG_SERIOUS("Unable to read OCS token requester tag.");
      Logger.LOG_SERIOUS(
          "Please make sure the tag 'tokenReq' exists and the tag datatype is set to integer.");
      Logger.LOG_EXCEPTION(e);
    }

    int tagValue = tc.getTagValueAsInt();

    if (tagValue > tagInitialValue) {

      // if tag is not TAG_INITIAL_VALUE
      setOcsToken();

      // reset tag to TAG_INITIAL_VALUE
      try {
        tc.setTagValueAsInt(tagInitialValue);
      } catch (EWException e) {
        Logger.LOG_SERIOUS("Unable to change OCS token requester tag value.");
        Logger.LOG_EXCEPTION(e);
      }
    }
  }

  /**
   * Get the OCS URL.
   *
   * @return the OCS URL
   */
  public static String getOcsUrl() {
    return ocsUrl;
  }

  /**
   * Get the HTTP timeout in seconds.
   *
   * @return the HTTP timeout in seconds
   */
  public static int getHttpTimeoutSeconds() {
    return httpTimeoutSeconds;
  }

  /**
   * Get the OCS OMF access token.
   *
   * @return the OCS OMF access token as a string.
   */
  public static String getOcsToken() {
    return ocsOmfToken;
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

  /** Set the flexy device name */
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

  /** Read the Flexy serial number and store it. */
  public static void readFlexySerial() {
    try {
      SysControlBlock sysControlBlock = new SysControlBlock(SysControlBlock.INF);
      flexySerialNum = sysControlBlock.getItem("SerNum");
    } catch (EWException e) {
      Logger.LOG_SERIOUS(
          "An error occurred while getting the serial number of the host Ewon Flexy.");
      Logger.LOG_EXCEPTION(e);
    }
  }

  /**
   * Get the Flexy serial number.
   *
   * @return the Flexy serial number
   */
  public static String getFlexySerial() {
    return flexySerialNum;
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

  /**
   * Get the headers specific to OCS.
   *
   * @return the OCS headers
   */
  public static String getOcsPostHeaders() {
    return ocsPostHeaders;
  }
}
