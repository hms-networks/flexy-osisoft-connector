package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.ScheduledActionManager;
import com.hms_networks.americas.sc.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.json.JSONArray;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.json.JSONObject;
import com.hms_networks.americas.sc.json.JSONTokener;
import com.hms_networks.americas.sc.logging.Logger;
import com.hms_networks.americas.sc.taginfo.TagInfo;
import com.hms_networks.americas.sc.taginfo.TagInfoManager;
import java.io.File;
import java.io.IOException;

/**
 * Class object for an OSIsoft PI Server.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftServer {

  private static boolean connected = true;

  public static final int LINK_ERROR = 32601;
  public static final int SEND_ERROR = 32603;
  public static final int JSON_ERROR = -100;

  static final int NO_ERROR = 0;
  static final int EWON_ERROR = 1;
  static final int AUTH_ERROR = 2;
  static final int WEB_ID_ERROR = 2;
  static final int GENERIC_ERROR = 254;

  static final String WEB_ID_ERROR_STRING = "Unknown or invalid WebID format:";

  /** Constructs the OSIsoftServer object. */
  public OSIsoftServer() {}

  /**
   * Makes an HTTPS request against the OSIsoft server and validates the response code.
   *
   * @param CnxParam Request parameters
   * @param Method Request method
   * @param Headers Request headers
   * @param TextFields Request Payload
   * @param FileFields File location for response
   * @param FileName File name for response
   * @return The HTTP response code
   * @throws JSONException Throws when JSON is malformed
   */
  public static int RequestHTTPS(
      String CnxParam,
      String Method,
      String Headers,
      String TextFields,
      String FileFields,
      String FileName) {
    int res = NO_ERROR;

    try {
      Logger.LOG_DEBUG("HTTPS request is: " + CnxParam);
      res =
          ScheduledActionManager.RequestHttpX(
              CnxParam, Method, Headers, TextFields, FileFields, FileName);
    } catch (EWException e) {
      Logger.LOG_EXCEPTION(e);
      res = EWON_ERROR;
    }

    if (!FileName.equals("") && res == NO_ERROR) {
      String fileStr = null;
      try {
        fileStr = FileAccessManager.readFileToString(FileName);
      } catch (IOException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Unable to read HTTPS response file from previous request.");
      }

      if (fileStr.charAt(0) != '[' && fileStr.charAt(0) != '{') {
        // json has odd characters at the front, trim them to make valid json
        int i = fileStr.indexOf("{");
        fileStr = fileStr.substring(i);
      }
      JSONTokener JsonT = new JSONTokener(fileStr);
      JSONObject response = null;
      try {
        response = new JSONObject(JsonT);
      } catch (JSONException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Unable to generate new JSON object from request's response file.");
        Logger.LOG_SERIOUS("The request " + CnxParam + " has a bad response file.");
      }
      if (response.has("Message")) {
        res = AUTH_ERROR;
        Logger.LOG_SERIOUS("User Credentials are incorrect");
      }
      if (response.has("Errors")) {
        JSONArray errors = null;
        try {
          errors = response.getJSONArray("Errors");
        } catch (JSONException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_SERIOUS("Unable to parse JSON array from request's response file.");
          Logger.LOG_SERIOUS("The request " + CnxParam + " has a bad response file.");
        }
        for (int i = 0; i < errors.length(); i++) {
          String error = null;
          try {
            error = errors.getString(i);
          } catch (JSONException e) {
            Logger.LOG_EXCEPTION(e);
            Logger.LOG_SERIOUS("Unable to parse JSON string from request's response file.");
            Logger.LOG_SERIOUS("The request " + CnxParam + " has a bad response file.");
          }
          if (error.substring(0, WEB_ID_ERROR_STRING.length()).equals(WEB_ID_ERROR_STRING)) {
            res = WEB_ID_ERROR;
            Logger.LOG_SERIOUS("WEB ID: \"" + OSIsoftConfig.getServerWebID() + "\"");
            Logger.LOG_SERIOUS("WEB ID Error: Supplied Web ID does not exist on this server");
          } else {
            res = GENERIC_ERROR;
            Logger.LOG_SERIOUS(error);
          }
        }
      }
    } else if (res == LINK_ERROR) {
      if (connected == true) {
        Logger.LOG_SERIOUS("Could not connect to OSIsoft Server, link is down");
        connected = false;
      }
    } else if (res == SEND_ERROR) {
      if (connected == true) {
        Logger.LOG_SERIOUS("Could not connect to OSIsoft Server, server is offine or unreachable");
        connected = false;
      }
    } else if (res != NO_ERROR) {
      Logger.LOG_SERIOUS("Sending Failed. Error #" + res);
    }

    if (res == NO_ERROR && connected == false) {
      connected = true;
      Logger.LOG_SERIOUS("Connection restored");
    }

    return res;
  }

  /**
   * Attempts to initialize a new tag into OSIsoft and retrieve the relevant information.
   *
   * @param tag the tag to attempt to initialize in OSIsoft
   * @return returns the HTTP response code
   */
  public int setTagWebId(TagInfo tag) {

    String tagWebID = "not found";

    int res = NO_ERROR;

    // HTTPS responses are stored in this file
    String responseFilename = "/usr/response.txt";

    String tagName = tag.getName();

    // url for the dataserver's pi points
    String url =
        "https://"
            + OSIsoftConfig.getServerIP()
            + "/piwebapi/dataservers/"
            + OSIsoftConfig.getServerWebID()
            + "/points";

    // Check if the tag already exists in the dataserver
    res =
        RequestHTTPS(
            url + "?nameFilter=" + tagName,
            "Get",
            OSIsoftConfig.getPostHeaders(),
            "",
            "",
            responseFilename);
    if (res == NO_ERROR) {
      // Parse the JSON response and retrieve the JSON Array of items
      JSONTokener JsonT = null;
      try {
        JsonT = new JSONTokener(FileAccessManager.readFileToString(responseFilename));
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
        try {
          tagWebID = items.getJSONObject(0).getString("WebId");
        } catch (JSONException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_SERIOUS("Unable to parse JSON string from request's response file.");
        }
      } else {
        // tag does not exist and must be created
        String payload = PayloadBuilder.buildNewPointBody(tag);
        res =
            RequestHTTPS(
                url, "Post", OSIsoftConfig.getPostHeaders(), payload, "", responseFilename);

        if (res == NO_ERROR) {
          /* The WebID is sent back in the headers of the previous post
          however, there is no mechanism currently to retrieve it so
          another request must be issued.*/
          res =
              RequestHTTPS(
                  url + "?nameFilter=" + tagName,
                  "Get",
                  OSIsoftConfig.getPostHeaders(),
                  "",
                  "",
                  responseFilename);
          if (res == NO_ERROR) {
            // Parse the JSON response and retrieve the JSON Array of items
            try {
              JsonT = new JSONTokener(FileAccessManager.readFileToString(responseFilename));
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
              try {
                tagWebID = items.getJSONObject(0).getString("WebId");
              } catch (JSONException e) {
                Logger.LOG_EXCEPTION(e);
                Logger.LOG_SERIOUS("Unable to parse JSON string from request's response file.");
              }
              res =
                  RequestHTTPS(
                      OSIsoftConfig.getTargetURL()
                          + "points/"
                          + tagWebID
                          + "/attributes/pointsource",
                      "Put",
                      OSIsoftConfig.getPostHeaders(),
                      "\"HMS\"",
                      "",
                      "");
              if (res != NO_ERROR) {
                Logger.LOG_SERIOUS(
                    "Could not set point source of " + tag.getName() + ". Error: " + res);
              }
            } else {
              // tag does not exist, error
              Logger.LOG_SERIOUS("PI Point creation failed.  Error: " + res);
            }
          }
        } else {
          Logger.LOG_SERIOUS("Error in creating tag.  Error: " + res);
        }
      }

      // Delete the https response file
      File file = new File(responseFilename);
      if (!file.delete()) {
        Logger.LOG_SERIOUS("Failed to delete the HTTPS response file");
      }
    }

    // set tag web id to list for later lookup
    PayloadBuilder.setTagWebId(tag.getId(), tagWebID);

    return res;
  }

  /**
   * Initializes all of the tags into OSIsoft for future storage on the server.
   *
   * @return returns the HTTP response code
   * @throws JSONException Throws when JSON is malformed
   */
  public int initTags() throws JSONException {
    int retval = NO_ERROR;

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

  private void initOMF() {
    // setup type
    String messageTypeHeader = "&messagetype=type";
    final String responseFilename = "/usr/response.json";

    int res =
        RequestHTTPS(
            OSIsoftConfig.getOmfUrl(),
            "Post",
            OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
            PayloadBuilder.getStringTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOmfUrl(),
            "Post",
            OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
            PayloadBuilder.getNumberTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOmfUrl(),
            "Post",
            OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
            PayloadBuilder.getIntegerTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOmfUrl(),
            "Post",
            OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
            PayloadBuilder.getBooleanTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    // setup containers
    final int numTagsInList = TagInfoManager.getTagInfoList().size();
    final int numTagsInitializing = 100;

    messageTypeHeader = "&messagetype=container";

    for (int currentTagIndex = 0;
        currentTagIndex < numTagsInList;
        currentTagIndex += numTagsInitializing) {
      String payload = PayloadBuilder.getContainerSettingJson(currentTagIndex, numTagsInitializing);
      res =
          RequestHTTPS(
              OSIsoftConfig.getOmfUrl(),
              "Post",
              OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
              payload,
              "",
              responseFilename);
    }
  }

  private void initLegacyFormat() {
    // web id's are stored in the payload builder
    PayloadBuilder.initWebIdList();

    for (int i = 0; i < TagInfoManager.getTagInfoList().size(); i++) {
      TagInfo tag = (TagInfo) TagInfoManager.getTagInfoList().get(i);

      if (tag != null) {
        int res = setTagWebId(tag);
        if (res != NO_ERROR) {
          Logger.LOG_WARN("There was a problem obtaining the web ID for tag " + tag.getName());
          Logger.LOG_WARN("This tag will not be updated in OSIsoft.");
        }
      }
    }
  }

  private void initOCS() {
    final String responseFilename = "/usr/response.json";

    // setup type
    final String ocsMessageTypeHeader = "&messagetype=type";

    int res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
            PayloadBuilder.getStringTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
            PayloadBuilder.getStringTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
            PayloadBuilder.getIntegerTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
            PayloadBuilder.getNumberTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + ocsMessageTypeHeader,
            PayloadBuilder.getBooleanTypeBody(),
            "",
            responseFilename);
    checkInitResponseCode(res);

    // setup containers
    String messageTypeHeader = "&messagetype=container";

    // initialize tag containers
    final int numTagsInList = TagInfoManager.getTagInfoList().size();
    final int numTagsInitializing = 100;

    for (int currentTagIndex = 0;
        currentTagIndex < numTagsInList;
        currentTagIndex += numTagsInitializing) {
      String payload = PayloadBuilder.getContainerSettingJson(currentTagIndex, numTagsInitializing);
      res =
          RequestHTTPS(
              OSIsoftConfig.getOcsUrl(),
              "Post",
              OSIsoftConfig.getOcsPostHeaders() + messageTypeHeader,
              payload,
              "",
              responseFilename);
    }
  }

  /**
   * Helper function to print out errors when initializing OMF types or containers.
   *
   * @param res The response code of an HTTPS request
   */
  private void checkInitResponseCode(int res) {
    if (res != NO_ERROR) {
      Logger.LOG_WARN(
          "The HTTPS request to initialize OMF data points was not completed successfully");
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

    int res = NO_ERROR;

    postHeaderType = "&messagetype=data";
    String responseFilename = "/usr/response.txt";

    // posting OMF batch
    res =
        RequestHTTPS(
            OSIsoftConfig.getOmfUrl(),
            "Post",
            OSIsoftConfig.getOmfPostHeaders() + postHeaderType,
            payload,
            "",
            responseFilename); // data
    if (res != NO_ERROR) {
      return false;
    }
    return true;
  }

  /**
   * Posts the OMF batch to OSIsoft Cloud Services.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postOcsBatch(String payload) {

    String postHeaderType = "&messagetype=type";

    int res = NO_ERROR;
    boolean requestSuccessful = false;

    postHeaderType = "&messagetype=data";
    String responseFilename = "/usr/response.txt";

    // posting OMF batch
    res =
        RequestHTTPS(
            OSIsoftConfig.getOcsUrl(),
            "Post",
            OSIsoftConfig.getOcsPostHeaders() + postHeaderType,
            payload,
            "",
            responseFilename);
    if (res == NO_ERROR) {
      requestSuccessful = true;
    }
    return requestSuccessful;
  }

  /**
   * Posts the PIWebAPI batch to OSIsoft.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postBatch(String payload) {
    int res = NO_ERROR;
    res =
        RequestHTTPS(
            OSIsoftConfig.getTargetURL() + "batch/",
            "Post",
            OSIsoftConfig.getPostHeaders(),
            payload,
            "",
            "");

    if (res != NO_ERROR) {
      return false;
    }
    return true;
  }
}
