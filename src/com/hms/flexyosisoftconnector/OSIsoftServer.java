package com.hms.flexyosisoftconnector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.ArrayList;

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
      String FileName)
      throws JSONException {
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
      JSONObject response = new JSONObject(JsonT);
      if (response.has("Message")) {
        res = AUTH_ERROR;
        Logger.LOG_SERIOUS("User Credentials are incorrect");
      }
      if (response.has("Errors")) {
        JSONArray errors = response.getJSONArray("Errors");
        for (int i = 0; i < errors.length(); i++) {
          String error = errors.getString(i);
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
   * @throws JSONException Throws when JSON is malformed
   */
  public int setTagWebId(TagInfo tag) throws JSONException {

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
      JSONObject requestResponse = new JSONObject(JsonT);
      JSONArray items = new JSONArray();
      if (requestResponse.has("Items")) items = requestResponse.getJSONArray("Items");

      if (items.length() > 0) {
        // tag exists
        tagWebID = items.getJSONObject(0).getString("WebId");
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
            requestResponse = new JSONObject(JsonT);
            if (requestResponse.has("Items")) items = requestResponse.getJSONArray("Items");
            if (items.length() > 0) {
              // tag exists
              tagWebID = items.getJSONObject(0).getString("WebId");
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
    int res;

    switch (OSIsoftConfig.getCommunicationType()) {
        // OMF setup
      case OSIsoftConfig.omf:
        // setup type
        String responseFilename = "/usr/response.txt";
        String messageTypeHeader = "&messagetype=type";

        String payload = PayloadBuilder.getTypeBody();
        res =
            RequestHTTPS(
                OSIsoftConfig.getOmfUrl(),
                "Post",
                OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
                payload,
                "",
                responseFilename);

        // setup containers
        messageTypeHeader = "&messagetype=container";

        // set batch buffer with list of tag containers to initialize for if they have not been
        // already
        final int numTagsInList = TagInfoManager.getTagInfoList().size();
        final int numTagsInitializing = 100;

        for (int currentTagIndex = 0; currentTagIndex < numTagsInList; currentTagIndex += numTagsInitializing) {
          payload = PayloadBuilder.getContainerSettingJson(currentTagIndex, numTagsInitializing);
          res =
              RequestHTTPS(
                  OSIsoftConfig.getOmfUrl(),
                  "Post",
                  OSIsoftConfig.getOmfPostHeaders() + messageTypeHeader,
                  payload,
                  "",
                  responseFilename);
          if (res != NO_ERROR) retval = res;
        }
        break;
        // legacy PIWEBAPI setup
      case OSIsoftConfig.piwebapi:
        // web id's are stored in the payload builder
        PayloadBuilder.initWebIdList();

        for (int i = 0; i < TagInfoManager.getTagInfoList().size(); i++) {
          res = setTagWebId((TagInfo) TagInfoManager.getTagInfoList().get(i));
          if (res != NO_ERROR) return retval = res;
        }

        break;
      default:
        Logger.LOG_SERIOUS(OSIsoftConfig.COM_ERR_MSG);
        break;
    }

    return retval;
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

    try {
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

    } catch (JSONException e) {
      Logger.LOG_SERIOUS("Exception caught on posting omf data points");
      Logger.LOG_EXCEPTION(e);
    }
    if (res != NO_ERROR) {
      return false;
    }
    return true;
  }

  /**
   * Posts the PIWebAPI batch to OSIsoft.
   *
   * @param payload the payload to post
   * @return returns the http response code
   */
  public static boolean postBatch(String payload) {
    int res = NO_ERROR;
    try {
      res =
          RequestHTTPS(
              OSIsoftConfig.getTargetURL() + "batch/",
              "Post",
              OSIsoftConfig.getPostHeaders(),
              payload,
              "",
              "");
    } catch (JSONException e) {
      Logger.LOG_SERIOUS("Failed to post tags due to malformed JSON response");
      Logger.LOG_EXCEPTION(e);
      res = JSON_ERROR;
    }

    if (res != NO_ERROR) {
      return false;
    }
    return true;
  }
}
