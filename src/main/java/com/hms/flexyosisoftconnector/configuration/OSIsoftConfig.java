package com.hms.flexyosisoftconnector.configuration;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.SysControlBlock;
import com.ewon.ewonitf.TagControl;
import com.hms_networks.americas.sc.extensions.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.extensions.historicaldata.HistoricalDataQueueManager;
import com.hms_networks.americas.sc.extensions.json.JSONException;
import com.hms_networks.americas.sc.extensions.json.JSONObject;
import com.hms_networks.americas.sc.extensions.json.JSONTokener;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import java.io.IOException;

/**
 * This class contains the configuration for the Flexy to OSIsoft connection.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftConfig {

  /** Name of the OSIsoft connector application. */
  public static final String CONNECTOR_NAME =
      OSIsoftConfig.class.getPackage().getImplementationTitle();

  /** Version of the OSIsoft connector application. */
  public static final String CONNECTOR_VERSION =
      OSIsoftConfig.class.getPackage().getImplementationVersion();

  /** Number of milliseconds queue can fall beind before warnings are logged. */
  public static final int WARNING_LIMIT_QUEUE_BEHIND_MILLISECONDS = 15000;

  /** IP address of OSIsoft PI Server */
  private static String piServerUrl;

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

  /** Unique name of this flexy */
  private static String flexyName;

  /** OSIsoft tag name scheme configuration */
  private static String parsedOSIsoftTagNameOptions;

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

  /** Default logging level for the SC logger library is set to 3 for 'warning'. */
  private static final int LOGGING_LEVEL_DEFAULT = 3;

  /** Key to access http timeout from config file JSON. */
  private static final String HTTP_TIMEOUT_SECONDS_KEY = "httpTimeoutSeconds";

  /** Unique type for OMF messages from this flexy */
  private static String typeID;

  /** Access token for OCS OMF. The token expires every hour. */
  private static String ocsOmfToken;

  /** Key to access the auto restart configuration setting. */
  private static boolean autoRestart;

  /** Key to access auto restart setting from config file JSON. */
  private static final String AUTO_RESTART_KEY = "AutoRestart";

  /** Key to access the data poll rate (in milliseconds) setting from config file JSON. */
  private static final String DATA_POLL_RATE_MS_KEY = "DataPollRateMs";

  /**
   * The default value for the data poll rate (in milliseconds) setting. This value is used if the
   * setting is not present in the config file.
   */
  private static final int DATA_POLL_RATE_MS_DEFAULT = 5000;

  /**
   * The data poll rate (in milliseconds) setting. This is the rate at which the connector will poll
   * the Flexy historical data queue for data points.
   */
  private static int dataPollRateMs;

  /** Key to access the data post rate (in milliseconds) setting from config file JSON. */
  private static final String DATA_POST_RATE_MS_KEY = "DataPostRateMs";

  /**
   * The default value for the data post rate (in milliseconds) setting. This value is used if the
   * setting is not present in the config file.
   */
  private static final int DATA_POST_RATE_MS_DEFAULT = 5000;

  /**
   * The data post rate (in milliseconds) setting. This is the rate at which the connector will post
   * data points to the OSIsoft server.
   */
  private static int dataPostRateMs;

  /** Key for accessing the 'QueueDataPollMaxBehindTimeMins' object in the configuration file. */
  private static final String CONFIG_FILE_QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_KEY =
      "QueueDataPollMaxBehindTimeMins";

  /**
   * The default maximum time (in mins) which data polling may run behind. Changing this will modify
   * the amount of time which data polling may run behind by. By default, this functionality is
   * disabled. The value {@link HistoricalDataQueueManager#DISABLED_MAX_HIST_FIFO_GET_BEHIND_MINS}
   * indicates that the functionality is disabled.
   */
  private static final long QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_DEFAULT =
      HistoricalDataQueueManager.DISABLED_MAX_HIST_FIFO_GET_BEHIND_MINS;

  /**
   * The maximum time (in mins) which data polling may run behind. Is set to {@link
   * HistoricalDataQueueManager#DISABLED_MAX_HIST_FIFO_GET_BEHIND_MINS} as a default, but this can
   * be overwritten by the configuration file.
   */
  private static long historicalDataQueueMaxFallBehindMin =
      QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_DEFAULT;

  /** Key for accessing the 'QueueDiagnosticTagsEnabled' object in the configuration file. */
  private static final String CONFIG_FILE_QUEUE_DIAGNOSTIC_TAGS_ENABLED_KEY =
      "QueueDiagnosticTagsEnabled";

  /** The default value for the 'QueueDiagnosticTagsEnabled' object in the configuration file. */
  private static final boolean QUEUE_DIAGNOSTIC_TAGS_ENABLED_DEFAULT = false;

  /**
   * The value for the 'QueueDiagnosticTagsEnabled' object in the configuration file. This is the
   * value which determines whether diagnostic tags are enabled for the historical data queue.
   */
  private static boolean historicalDataQueueDiagnosticTagsEnabled =
      QUEUE_DIAGNOSTIC_TAGS_ENABLED_DEFAULT;

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
    piServerUrl = serverConfig.getString("URL");
    dataServerWebID = serverConfig.getString("WebID");
    piServerLogin = serverConfig.getString("Credentials");

    // Build a JSON Object containing the "eWONConfig"
    JSONObject ewonConfig = configJSON.getJSONObject("eWONConfig");
    ewonCertificatePath = ewonConfig.getString("CertificatePath");
    try {
      if (ewonConfig.has(CONFIG_FILE_QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_KEY)) {
        historicalDataQueueMaxFallBehindMin =
            ewonConfig.getLong(CONFIG_FILE_QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_KEY);
      }

    } catch (Exception e) {
      String defaultQueueDataPollMaxBehindTimeMinsStr =
          String.valueOf(QUEUE_DATA_POLL_MAX_BEHIND_TIME_MINS_DEFAULT);
      Logger.LOG_WARN(
          "The queue maximum data polling run behind time setting was not set. "
              + "Using default value of "
              + defaultQueueDataPollMaxBehindTimeMinsStr
              + " minutes.");
    }
    try {
      if (ewonConfig.has(CONFIG_FILE_QUEUE_DIAGNOSTIC_TAGS_ENABLED_KEY)) {
        historicalDataQueueDiagnosticTagsEnabled =
            ewonConfig.getBoolean(CONFIG_FILE_QUEUE_DIAGNOSTIC_TAGS_ENABLED_KEY);
      }
    } catch (Exception e) {
      historicalDataQueueDiagnosticTagsEnabled = QUEUE_DIAGNOSTIC_TAGS_ENABLED_DEFAULT;
      Logger.LOG_WARN(
          "The queue diagnostic tags enabled setting could not be read. "
              + "Using default value of "
              + QUEUE_DIAGNOSTIC_TAGS_ENABLED_DEFAULT
              + ".");
    }

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

    final String OSIsoftTagNameKey = "OSIsoftTagNamingScheme";
    if (appConfig.has(OSIsoftTagNameKey)) {
      parsedOSIsoftTagNameOptions =
          OSIsoftTagNamingScheme.tagNameFormatOptionParser(appConfig.getString(OSIsoftTagNameKey));
      Logger.LOG_INFO("OSIsoft tag name option set to: " + parsedOSIsoftTagNameOptions);
    } else {
      Logger.LOG_SERIOUS(
          "No \""
              + OSIsoftTagNameKey
              + "\" option was given. Please add one in to the AppConfig section of"
              + " the connector configuration file.");
    }

    boolean res;
    if (appConfig.has("LoggingLevel")) {
      res = Logger.SET_LOG_LEVEL(appConfig.getInt("LoggingLevel"));
    } else {
      res = Logger.SET_LOG_LEVEL(LOGGING_LEVEL_DEFAULT);
      Logger.LOG_WARN(
          "No logging level was set in the configuration file. Using default logging"
              + " level of 'warning'.");
    }
    if (!res) {
      Logger.LOG_SERIOUS("Invalid logging level specified");
    }

    if (appConfig.has(HTTP_TIMEOUT_SECONDS_KEY)) {
      httpTimeoutSeconds = appConfig.getInt("httpTimeoutSeconds");
      Logger.LOG_INFO(
          "HTTP timeout of " + httpTimeoutSeconds + " seconds retrieved from configuration file.");
    } else {
      httpTimeoutSeconds = HTTP_TIMEOUT_SECONDS_DEFAULT;
    }

    autoRestart = appConfig.getBoolean(AUTO_RESTART_KEY);

    // Load data poll rate (ms)
    if (appConfig.has(DATA_POLL_RATE_MS_KEY)) {
      dataPollRateMs = appConfig.getInt(DATA_POLL_RATE_MS_KEY);
      Logger.LOG_INFO(
          "Data poll rate of " + dataPollRateMs + " ms retrieved from configuration file.");
    } else {
      dataPollRateMs = DATA_POLL_RATE_MS_DEFAULT;
      Logger.LOG_INFO(
          "Data poll rate not set in configuration file. Using default value of "
              + dataPollRateMs
              + " ms.");
    }

    // Load data post rate (ms)
    if (appConfig.has(DATA_POST_RATE_MS_KEY)) {
      dataPostRateMs = appConfig.getInt(DATA_POST_RATE_MS_KEY);
      Logger.LOG_INFO(
          "Data post rate of " + dataPostRateMs + " ms retrieved from configuration file.");
    } else {
      dataPostRateMs = DATA_POST_RATE_MS_DEFAULT;
      Logger.LOG_INFO(
          "Data post rate not set in configuration file. Using default value of "
              + dataPostRateMs
              + " ms.");
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
    return getServerUrl();
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
  public static String getServerUrl() {
    return piServerUrl;
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
   * Get the auto restart configuration setting.
   *
   * @return the auto restart configuration setting
   */
  public static boolean getAutoRestart() {
    return autoRestart;
  }

  /**
   * Get the data poll rate (in milliseconds) setting. This is the rate at which the connector will
   * poll the Flexy historical data queue for data points.
   *
   * @return the data poll rate (in milliseconds) setting
   */
  public static int getDataPollRateMs() {
    return dataPollRateMs;
  }

  /**
   * Get the data post rate (in milliseconds) setting. This is the rate at which the connector will
   * post data points to the OSIsoft server.
   *
   * @return the data post rate (in milliseconds) setting
   */
  public static int getDataPostRateMs() {
    return dataPostRateMs;
  }

  /**
   * Get the OMF URL
   *
   * @return the OMF URL
   */
  public static String getOmfUrl() {
    return getServerUrl();
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

  /**
   * Get the max time in minutes to poll historical data.
   *
   * @return Queue data poll Max behind minutes.
   */
  public static long getQueueDataPollMaxBehindTimeMinutes() {
    return historicalDataQueueMaxFallBehindMin;
  }

  /**
   * Get the boolean value indicating whether historical data queue diagnostic tags should be
   * enabled.
   *
   * @return true if historical data queue diagnostic tags should be enabled, false otherwise.
   */
  public static boolean getQueueDiagnosticTagsEnabled() {
    return historicalDataQueueDiagnosticTagsEnabled;
  }
}
