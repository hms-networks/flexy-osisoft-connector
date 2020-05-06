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
            Logger.LOG_SERIOUS("WEB ID: \"" + dbWebID + "\"");
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
  public int setTagWebId(Tag tag) throws JSONException {

    int res = NO_ERROR;

    // HTTPS responses are stored in this file
    String responseFilename = "/usr/response.txt";

    String tagName = tag.getTagName() + "-" + flexyName;

    // url for the dataserver's pi points
    String url = "https://" + serverIP + "/piwebapi/dataservers/" + dbWebID + "/points";

    // Check if the tag already exists in the dataserver
    res =
        RequestHTTPS(url + "?nameFilter=" + tagName, "Get", postHeaders, "", "", responseFilename);
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
      if (requestResponse.has("Items")) {
        items = requestResponse.getJSONArray("Items");
      }

      if (items.length() > 0) {
        // tag exists
        tag.setWebID(items.getJSONObject(0).getString("WebId"));
      } else {
        // tag does not exist and must be created
        res =
            RequestHTTPS(
                url,
                "Post",
                postHeaders,
                buildNewPointBody(tagName, tag.getDataType()),
                "",
                responseFilename);

        if (res == NO_ERROR) {
          // The WebID is sent back in the headers of the previous post
          // however, there is no mechanism currently to retrieve it so
          // another request must be issued.
          res =
              RequestHTTPS(
                  url + "?nameFilter=" + tagName, "Get", postHeaders, "", "", responseFilename);
          if (res == NO_ERROR) {
            // Parse the JSON response and retrieve the JSON Array of items
            try {
              JsonT = new JSONTokener(FileAccessManager.readFileToString(responseFilename));
            } catch (IOException e) {
              Logger.LOG_EXCEPTION(e);
              Logger.LOG_SERIOUS("Unable to read response file from previous request.");
            }
            requestResponse = new JSONObject(JsonT);
            if (requestResponse.has("Items")) {
              items = requestResponse.getJSONArray("Items");
            }
            if (items.length() > 0) {
              // tag exists
              tag.setWebID(items.getJSONObject(0).getString("WebId"));
              res =
                  RequestHTTPS(
                      targetURL + "points/" + tag.getWebID() + "/attributes/pointsource",
                      "Put",
                      postHeaders,
                      "\"HMS\"",
                      "",
                      "");
              if (res != NO_ERROR) {
                Logger.LOG_SERIOUS(
                    "Could not set point source of " + tag.getTagName() + ". Error: " + res);
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

    return res;
  }

  // Initializes a list of tags
  public int initTags(String serverIp, ArrayList tagList, int communicationType)
      throws JSONException {
    int retval = NO_ERROR;
    int res;

    switch (communicationType) {
      case OSIsoftConfig.omf:
        // OMF setup

        // setup type
        String responseFilename = "/usr/response.txt";
        String messageTypeHeader = "&messagetype=type";
        setTypeBody();
        res =
            RequestHTTPS(
                omfUrl,
                "Post",
                omfPostHeaders + messageTypeHeader,
                batchBuffer.toString(),
                "",
                responseFilename);

        // setup containers
        messageTypeHeader = "&messagetype=container";

        // set batch buffer with list of tag containers to initialize for if they have not been
        // already
        setContainerJson(tagList);
        res =
            RequestHTTPS(
                omfUrl,
                "Post",
                omfPostHeaders + messageTypeHeader,
                batchBuffer.toString(),
                "",
                responseFilename);
        if (res != NO_ERROR) {
          retval = res;
        }
        break;
      case OSIsoftConfig.piwebapi:
        // old PIWEBAPI setup
        for (int i = 0; i < tagList.size(); i++) {
          res = setTagWebId((Tag) tagList.get(i));
          if (res != NO_ERROR) {
            return retval = res;
          }
        }
        break;
      default:
        Logger.LOG_SERIOUS(comsErrMsg);
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
  public static boolean postOMFBatch() {

    String postHeaderType = "&messagetype=type";

    int res = NO_ERROR;

    try {

      postHeaderType = "&messagetype=data";
      String responseFilename = "/usr/response.txt";

      // posting OMF batch
      res =
          RequestHTTPS(
              omfUrl,
              "Post",
              omfPostHeaders + postHeaderType,
              batchBuffer.toString(),
              "",
              responseFilename); // data

    } catch (JSONException e) {
      Logger.LOG_SERIOUS("Exception caught on posting omf data points");
      e.printStackTrace();
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
  public static boolean postBatch() {
    int res = NO_ERROR;
    try {
      res = RequestHTTPS(targetURL + "batch/", "Post", postHeaders, batchBuffer.toString(), "", "");
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
