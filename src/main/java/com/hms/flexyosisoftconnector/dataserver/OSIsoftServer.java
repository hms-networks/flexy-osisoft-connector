package com.hms.flexyosisoftconnector.dataserver;

import com.ewon.ewonitf.EWException;
import com.hms.flexyosisoftconnector.configuration.OSIsoftConfig;
import com.hms.flexyosisoftconnector.payloadhandler.OsisoftJsonPayload;
import com.hms.flexyosisoftconnector.payloadhandler.PayloadBuilder;
import com.hms_networks.americas.sc.extensions.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.extensions.json.JSONArray;
import com.hms_networks.americas.sc.extensions.json.JSONException;
import com.hms_networks.americas.sc.extensions.json.JSONObject;
import com.hms_networks.americas.sc.extensions.json.JSONTokener;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpAuthException;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpConnectionException;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpEwonException;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpUnknownException;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpUtility;
import com.hms_networks.americas.sc.extensions.taginfo.TagInfo;
import com.hms_networks.americas.sc.extensions.taginfo.TagInfoManager;
import java.io.File;
import java.io.IOException;

/**
 * Class object for an OSIsoft PI Server.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftServer {

  /** Boolean to represent when the last sent message was succesfully sent. */
  private static boolean connected = true;

  /** Ewon error code representing a send error */
  public static final int SEND_ERROR = 32603;

  /** Ewon error code representing a JSON error */
  public static final int JSON_ERROR = -100;

  /** Ewon error code representing a web ID error */
  public static final int WEB_ID_ERROR = SCHttpUtility.HTTPX_CODE_AUTH_ERROR;

  /** Ewon error code representing a generic error */
  public static final int GENERIC_ERROR = 254;

  /** String to display wheb the web ID error occurs */
  static final String WEB_ID_ERROR_STRING = "Unknown or invalid WebID format:";

  /** The directory where http(s) responses are stored. */
  static final String RESPONSE_DIRECTORY = "/usr/responses/";

  /** The name appended to the end of all http(s) response files. */
  static final String RESPONSE_FILE_NAME = "Response.json";

  /** Save the response for initializing string OMF types. */
  static final String STRING_RESPONSE_FILE_NAME =
      OsisoftJsonPayload.STRING_TAG_TYPE + RESPONSE_FILE_NAME;

  /** Save the response for initializing number OMF types. */
  static final String NUMBER_RESPONSE_FILE_NAME =
      OsisoftJsonPayload.NUMBER_TAG_TYPE + RESPONSE_FILE_NAME;

  /** Save the response for initializing integer OMF types. */
  static final String INTEGER_RESPONSE_FILE_NAME =
      OsisoftJsonPayload.INTEGER_TAG_TYPE + RESPONSE_FILE_NAME;

  /** Save the response for initializing boolean OMF types. */
  static final String BOOLEAN_RESPONSE_FILE_NAME =
      OsisoftJsonPayload.BOOLEAN_TAG_TYPE + RESPONSE_FILE_NAME;

  /** Save the response for initializing boolean OMF types. */
  static final String DATA_RESPONSE_FILE_NAME = "dataMessage" + RESPONSE_FILE_NAME;

  /** Save the response for sending PI Web API batch messages. */
  static final String PI_WEB_API_RESPONSE_FILE_NAME = "piWeb" + RESPONSE_FILE_NAME;

  /** Save the response for sending OCS data messages. */
  static final String OCS_RESPONSE_FILE_NAME = "OcsDataMessage" + RESPONSE_FILE_NAME;

  /** Constructs the OSIsoftServer object. */
  public OSIsoftServer() {}

  /** HTTP Method string for Post requests */
  static final String HTTP_METHOD_POST = "POST";

  /** HTTP Method string for Get requests */
  static final String HTTP_METHOD_GET = "GET";

  /**
   * Repeated Exception handling code for HTTP requests
   *
   * @param e Exception
   * @param ErrorMessage Specific message to log
   */
  private static void RequestHttpsError(Exception e, String ErrorMessage) {
    connected = false;
    Logger.LOG_CRITICAL(ErrorMessage);
    Logger.LOG_EXCEPTION(e);
  }

  /**
   * Makes an HTTPS request against the OSIsoft server and validates the response code.
   *
   * @param CnxParam Request parameters: GET or POST
   * @param Method Request method
   * @param Headers Request headers
   * @param TextFields Request Payload
   * @param FileName File name for response
   * @return True/False message was successfully sent
   * @throws IllegalArgumentException if the specified parameter, {@code CnxParam}, is not an
   *     allowed value
   */
  public static boolean RequestHttps(
      String CnxParam, String Method, String Headers, String TextFields, String FileName) {
    String responseBodyString = null;
    if (FileName.length() > 0) {
      FileName = RESPONSE_DIRECTORY + FileName;
    }
    try {
      Logger.LOG_DEBUG("HTTPS request is: " + CnxParam);
      if (Method == HTTP_METHOD_GET) {
        responseBodyString = SCHttpUtility.httpGet(CnxParam, Headers, TextFields, FileName);
      } else if (Method == HTTP_METHOD_POST) {
        responseBodyString = SCHttpUtility.httpPost(CnxParam, Headers, TextFields, FileName);
      } else {
        throw new IllegalArgumentException("CnxParam method not supported");
      }

      connected = true;
    } catch (EWException e) {
      RequestHttpsError(e, "Ewon exception during HTTP request to" + CnxParam + ".");
    } catch (IOException e) {
      RequestHttpsError(e, "IO exception during HTTP request to" + CnxParam + ".");
    } catch (SCHttpEwonException e) {
      RequestHttpsError(e, "Ewon exception during the HTTP request to" + CnxParam + ".");
    } catch (SCHttpAuthException e) {
      RequestHttpsError(e, "Auth exception during the HTTP request to" + CnxParam + ".");
    } catch (SCHttpConnectionException e) {
      RequestHttpsError(e, "Connection error during the HTTP request to" + CnxParam + ".");
    } catch (SCHttpUnknownException e) {
      RequestHttpsError(e, "Unknown exception during the HTTP request to" + CnxParam + ".");
    }

    // There is no point trying to parse or check
    if (!connected) {
      return false;
    }

    // Remove random characters before start of JSON in response file
    final int indexNotFound = -1;
    final String jsonStart = "{";
    if (responseBodyString != null
        && !responseBodyString.startsWith(jsonStart)
        && responseBodyString.indexOf(jsonStart) != indexNotFound) {
      responseBodyString = responseBodyString.substring(responseBodyString.indexOf(jsonStart));
    }

    // Parse response file to JSON object
    JSONTokener jsonTokener = new JSONTokener(responseBodyString);
    JSONObject response = null;
    try {
      response = new JSONObject(jsonTokener);
    } catch (JSONException e) {
      Logger.LOG_EXCEPTION(e);
      Logger.LOG_SERIOUS("Unable to generate new JSON object from request's response file.");
      Logger.LOG_SERIOUS("The request " + CnxParam + " has an improperly formatted response.");
    }

    boolean success = false;
    if (connected) {
      success = OSIsoftServerResponseUtil.checkForMessages(response, CnxParam, FileName);
    }
    return success;
  }

  /**
   * Attempts to initialize a new tag into OSIsoft and retrieve the relevant information.
   *
   * @param tag the tag to attempt to initialize in OSIsoft
   * @return returns the HTTP response code
   */
  public boolean setTagWebId(TagInfo tag) {

    String tagWebID = "not found";

    boolean requestSuccess = false;
    boolean webIDSet = false;

    // HTTPS responses are stored in this file
    String responseFilename = "response.txt";

    String tagName = tag.getName();

    // url for the dataserver's pi points
    String url =
        "https://"
            + OSIsoftConfig.getServerUrl()
            + "/piwebapi/dataservers/"
            + OSIsoftConfig.getServerWebID()
            + "/points";

    // Check if the tag already exists in the dataserver
    requestSuccess =
        RequestHttps(
            url + "?nameFilter=" + tagName,
            "GET",
            OSIsoftConfig.getPostHeaders(),
            "",
            responseFilename);
    if (requestSuccess) {
      // Parse the JSON response and retrieve the JSON Array of items
      JSONTokener JsonT = null;
      try {
        JsonT =
            new JSONTokener(
                FileAccessManager.readFileToString(RESPONSE_DIRECTORY + responseFilename));
      } catch (IOException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Unable to read malformed HTTPS response JSON from previous request.");
      }
      JSONObject requestResponse = null;
      try {
        requestResponse = new JSONObject(JsonT);
      } catch (JSONException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Unable to parse JSON object from request's response file.");
      }
      JSONArray items = new JSONArray();
      if (requestResponse.has("Items"))
        try {
          items = requestResponse.getJSONArray("Items");
        } catch (JSONException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_SERIOUS("Unable to parse JSON array from request's response file.");
        }

      if (items.length() > 0) {
        // tag exists
        webIDSet = true;
        try {
          tagWebID = items.getJSONObject(0).getString("WebId");
        } catch (JSONException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_SERIOUS("Unable to parse JSON string from request's response file.");
        }
      } else {
        // tag does not exist and must be created
        String payload = PayloadBuilder.buildNewPointBody(tag);
        requestSuccess =
            RequestHttps(url, "POST", OSIsoftConfig.getPostHeaders(), payload, responseFilename);

        if (requestSuccess) {
          /* The WebID is sent back in the headers of the previous post
          however, there is no mechanism currently to retrieve it so
          another request must be issued.*/
          requestSuccess =
              RequestHttps(
                  url + "?nameFilter=" + tagName,
                  "GET",
                  OSIsoftConfig.getPostHeaders(),
                  "",
                  responseFilename);
          if (requestSuccess) {
            // Parse the JSON response and retrieve the JSON Array of items
            try {
              JsonT =
                  new JSONTokener(
                      FileAccessManager.readFileToString(RESPONSE_DIRECTORY + responseFilename));
            } catch (IOException e) {
              Logger.LOG_EXCEPTION(e);
              Logger.LOG_SERIOUS("Unable to read response file from previous request.");
            }
            try {
              requestResponse = new JSONObject(JsonT);
            } catch (JSONException e) {
              Logger.LOG_EXCEPTION(e);
              Logger.LOG_SERIOUS("Unable to parse JSON object from request's response file.");
            }
            if (requestResponse.has("Items"))
              try {
                items = requestResponse.getJSONArray("Items");
              } catch (JSONException e) {
                Logger.LOG_EXCEPTION(e);
                Logger.LOG_SERIOUS("Unable to parse JSON array from request's response file.");
              }
            if (items.length() > 0) {
              // tag exists
              webIDSet = true;
              try {
                tagWebID = items.getJSONObject(0).getString("WebId");
              } catch (JSONException e) {
                Logger.LOG_EXCEPTION(e);
                Logger.LOG_SERIOUS("Unable to parse JSON string from request's response file.");
              }
              requestSuccess =
                  RequestHttps(
                      OSIsoftConfig.getTargetURL()
                          + "points/"
                          + tagWebID
                          + "/attributes/pointsource",
                      "PUT",
                      OSIsoftConfig.getPostHeaders(),
                      "\"HMS\"",
                      "");
              if (!requestSuccess) {
                Logger.LOG_SERIOUS("Could not set point source of " + tag.getName() + ".");
              }
            } else {
              // tag does not exist, error
              Logger.LOG_SERIOUS("PI Point creation failed.");
            }
          }
        } else {
          Logger.LOG_SERIOUS("Error in creating tag.");
        }
      }

      // Delete the https response file
      File file = new File(RESPONSE_DIRECTORY + responseFilename);
      if (!file.delete()) {
        Logger.LOG_SERIOUS("Failed to delete the HTTPS response file");
      }
    }

    // set tag web id to list for later lookup
    PayloadBuilder.setTagWebId(tag.getId(), tagWebID);

    return webIDSet;
  }

  /**
   * Initializes all of the tags into OSIsoft for future storage on the server.
   *
   * @return returns the HTTP response code
   */
  public int initTags() {
    int retval = SCHttpUtility.HTTPX_CODE_NO_ERROR;

    makeResponseDirectory();

    switch (OSIsoftConfig.getCommunicationType()) {
        // OMF setup
      case OSIsoftConfig.OMF:
        initOMF();
        break;
        // OMF OCS setup
      case OSIsoftConfig.OCS:
        initOCS();
        break;
        // legacy PIWEBAPI setup
      case OSIsoftConfig.PI_WEB_API:
        initLegacyFormat();
        break;
      default:
        Logger.LOG_SERIOUS(OSIsoftConfig.COM_ERR_MSG);
        break;
    }

    return retval;
  }

  /** If there is not a directory to hold responses, create one. */
  private void makeResponseDirectory() {
    File directory = new File(RESPONSE_DIRECTORY);
    if (!directory.exists()) {
      directory.mkdir();
    }
  }

  /** Initialize the types and containers needed by OMF data messages into OSIsoft. */
  private void initOMF() {
    // setup type
    String messageTypeHeader = "&messagetype=type";

    RequestHttps(
        OSIsoftConfig.getOmfUrl(),
        "POST",
        OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
        PayloadBuilder.getStringTypeBody(),
        STRING_RESPONSE_FILE_NAME);

    RequestHttps(
        OSIsoftConfig.getOmfUrl(),
        "POST",
        OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
        PayloadBuilder.getNumberTypeBody(),
        NUMBER_RESPONSE_FILE_NAME);

    RequestHttps(
        OSIsoftConfig.getOmfUrl(),
        "POST",
        OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
        PayloadBuilder.getIntegerTypeBody(),
        INTEGER_RESPONSE_FILE_NAME);

    RequestHttps(
        OSIsoftConfig.getOmfUrl(),
        "POST",
        OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
        PayloadBuilder.getBooleanTypeBody(),
        BOOLEAN_RESPONSE_FILE_NAME);

    // setup containers
    final int numTagsInList = TagInfoManager.getTagInfoList().size();
    final int numTagsInitializing = 100;

    messageTypeHeader = "&messagetype=container";

    for (int currentTagIndex = 0;
        currentTagIndex < numTagsInList;
        currentTagIndex += numTagsInitializing) {
      String payload = PayloadBuilder.getContainerSettingJson(currentTagIndex, numTagsInitializing);

      RequestHttps(
          OSIsoftConfig.getOmfUrl(),
          "POST",
          OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
          payload,
          "containers" + currentTagIndex + RESPONSE_FILE_NAME);
    }
  }

  /** Initialize the Flexy tags in OSIsoft for use by PI Web API's legacy data message format. */
  private void initLegacyFormat() {
    // web id's are stored in the payload builder
    PayloadBuilder.initWebIdList();

    for (int i = 0; i < TagInfoManager.getTagInfoList().size(); i++) {
      TagInfo tag = (TagInfo) TagInfoManager.getTagInfoList().get(i);

      if (tag != null) {
        boolean success = setTagWebId(tag);
        if (!success) {
          Logger.LOG_WARN("There was a problem obtaining the web ID for tag " + tag.getName());
          Logger.LOG_WARN("This tag will not be updated in OSIsoft.");
        }
      }
    }
  }

  /**
   * Initialize the types and containers needed by OMF data messages into OSIsoft cloud services.
   */
  private void initOCS() {
    final String responseFilename = "OcsResponse.json";

    // setup type
    final String ocsMessageTypeHeader = "&messagetype=type";

    RequestHttps(
        OSIsoftConfig.getOcsUrl(),
        "POST",
        OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
        PayloadBuilder.getStringTypeBody(),
        responseFilename);

    RequestHttps(
        OSIsoftConfig.getOcsUrl(),
        "POST",
        OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
        PayloadBuilder.getStringTypeBody(),
        responseFilename);

    RequestHttps(
        OSIsoftConfig.getOcsUrl(),
        "POST",
        OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
        PayloadBuilder.getIntegerTypeBody(),
        responseFilename);

    RequestHttps(
        OSIsoftConfig.getOcsUrl(),
        "POST",
        OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
        PayloadBuilder.getNumberTypeBody(),
        responseFilename);

    RequestHttps(
        OSIsoftConfig.getOcsUrl(),
        "POST",
        OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
        PayloadBuilder.getBooleanTypeBody(),
        responseFilename);

    // setup containers
    String messageTypeHeader = "&messagetype=container";

    // initialize tag containers
    final int numTagsInList = TagInfoManager.getTagInfoList().size();
    final int numTagsInitializing = 100;

    for (int currentTagIndex = 0;
        currentTagIndex < numTagsInList;
        currentTagIndex += numTagsInitializing) {
      String payload = PayloadBuilder.getContainerSettingJson(currentTagIndex, numTagsInitializing);

      RequestHttps(
          OSIsoftConfig.getOcsUrl(),
          "POST",
          OSIsoftConfig.getOcsPostHeaders() + messageTypeHeader,
          payload,
          responseFilename);
    }
  }

  /**
   * Posts the OMF batch to OSIsoft.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postOMFBatch(String payload) {

    String postHeaderType = "&messagetype=type";

    postHeaderType = "&messagetype=data";

    // posting OMF batch
    boolean requestSuccess =
        RequestHttps(
            OSIsoftConfig.getOmfUrl(),
            "POST",
            OSIsoftConfig.getOmfPostHeaders() + postHeaderType,
            payload,
            DATA_RESPONSE_FILE_NAME);
    if (!requestSuccess) {
      Logger.LOG_SERIOUS("Could not post batch of OMF data points to OSIsoft.");
    }
    return requestSuccess;
  }

  /**
   * Posts the OMF batch to OSIsoft Cloud Services.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postOcsBatch(String payload) {

    String postHeaderType = "&messagetype=type";

    postHeaderType = "&messagetype=data";

    // posting OMF batch
    boolean requestSuccess =
        RequestHttps(
            OSIsoftConfig.getOcsUrl(),
            "POST",
            OSIsoftConfig.getOcsPostHeaders() + postHeaderType,
            payload,
            OCS_RESPONSE_FILE_NAME);
    if (!requestSuccess) {
      Logger.LOG_SERIOUS("Could not post batch of data point to OCS.");
    }
    return requestSuccess;
  }

  /**
   * Posts the PIWebAPI batch to OSIsoft.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postBatch(String payload) {
    boolean requestSuccess =
        RequestHttps(
            OSIsoftConfig.getTargetURL() + "batch/",
            "POST",
            OSIsoftConfig.getPostHeaders(),
            payload,
            PI_WEB_API_RESPONSE_FILE_NAME);

    if (!requestSuccess) {
      Logger.LOG_SERIOUS("Could not post batch of data points to OSIsoft.");
    }
    return requestSuccess;
  }
}
