package com.hms.flexyosisoftconnector;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.ArrayList;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.ScheduledActionManager;
import com.hms.flexyosisoftconnector.JSON.*;

/**
 * Class object for an OSIsoft PI Server.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftServer {

  // IP address of OSIsoft Server
  private String serverIP;

  // BASE64 encoded BASIC authentication credentials
  private static String authCredentials;

  // WebID for the database being used
  private static String dbWebID;

  // URL for the OSIsoft Server
  private static String targetURL;

  // Post Headers
  private static String postHeaders;
  private static String omfPostHeaders;

  private static String omfUrl;

  // Unique name of this flexy
  private static String flexyName;

  // Unique type for OMF messages from this flexy
  private static String typeID;

  private static boolean connected = true;

  public static final int LINK_ERROR = 32601;
  public static final int SEND_ERROR = 32603;
  public static final int JSON_ERROR = -100;

  static final int NO_ERROR = 0;
  static final int EWON_ERROR = 1;
  static final int AUTH_ERROR = 2;
  static final int WEB_ID_ERROR = 2;
  static final int GENERIC_ERROR = 254;

  static final String AUTH_ERROR_STRING = "Authorization has been denied for this request.";
  static final String WEB_ID_ERROR_STRING = "Unknown or invalid WebID format:";

  // to be used in switch statements on which comms type to use
  static final String communicationTypePre2019 = "piwebapi";
  static final String communicationTypePost2019 = "omf";

  // Error message to be displayed for internal comminication errors in this file
  private static final String comsErrMsg =
      "Internal Error OSIsoftServer.java: communication type is not valid";

  private static StringBuilderLite batchBuffer;
  private static final int STRING_BUILDER_NUM_CHARS = 500000;
  private static int batchCount;

  private static SimpleDateFormat dateFormat;

  private String omfTypeJson;

  public OSIsoftServer(String ip, String login, String webID, String name) {
    serverIP = ip;
    authCredentials = login;
    dbWebID = webID;
    flexyName = name;
    targetURL = "https://" + serverIP + "/piwebapi/";
    postHeaders =
        "Authorization=Basic "
            + authCredentials
            + "&Content-Type=application/json&X-Requested-With=JSONHttpRequest";
    omfPostHeaders =
        "Authorization=Basic "
            + authCredentials
            + "&Content-Type=application/json"
            + "&X-Requested-With=JSONHttpRequest"
            + "&messageformat=json&omfversion=1.1";
    omfUrl = targetURL + "omf";
    batchBuffer = new StringBuilderLite(STRING_BUILDER_NUM_CHARS);
    dateFormat = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss");
    typeID = "HMS-type-" + flexyName;
  }

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

  // Looks up and sets a tags PI Point web id.  If the tag does not exist
  // in the data server a PI Point is created for that tag.
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

  public static void startBatch() {
    batchCount = 0;
    batchBuffer.clearString();
    batchBuffer.append("{\n");
  }

  public static void addPointToBatch(Tag t, DataPoint d) {
    batchCount++;

    batchBuffer.append("  \"" + Integer.toString(batchCount) + "\": {\n");
    batchBuffer.append("    \"Method\": \"POST\",\n");
    batchBuffer.append(
        "    \"Resource\": \"" + targetURL + "streams/" + t.getWebID() + "/Value\",\n");
    batchBuffer.append(
        "    \"Content\": \"" + buildBody(d.getValueString(), d.getTimeStamp(), true) + "\",\n");
    batchBuffer.append(
        "    \"Headers\": {\"Authorization\": \"Basic " + authCredentials + "\"" + "}\n");
    batchBuffer.append("  },\n");
  }

  public static void endBatch() {
    batchBuffer.append("}");
  }

  // omf versions of above functions

  private static void setTypeBody() {
    batchCount = 0;
    batchBuffer.clearString();
    batchBuffer.append("[{");
    batchBuffer.append("\"id\": \"" + typeID + "\",");
    batchBuffer.append("\"classification\": \"dynamic\",");
    batchBuffer.append("\"type\": \"object\",");
    batchBuffer.append("\"properties\": {");
    batchBuffer.append("\"timestamp\": {");
    batchBuffer.append("\"type\": \"string\",");
    batchBuffer.append("\"format\": \"date-time\",");
    batchBuffer.append("\"isindex\": true");
    batchBuffer.append("},");
    batchBuffer.append("\"tagValue\": {");
    batchBuffer.append("\"type\": \"string\",");
    batchBuffer.append("\"description\": \"Ewon Flexy's tag value stored as a string\"");
    batchBuffer.append("}");
    batchBuffer.append("}");
    batchBuffer.append("}]");
  }

  /** call this first when constructing OMF message */
  public static void startOMFDataMessage() {
    batchCount = 0;
    batchBuffer.clearString();
    batchBuffer.append("[");
  }

  /** call this for each new data point to omf message */
  public static void addPointToOMFDataMessage(String tagValue, String timestamp) {
    batchCount++;

    batchBuffer.append("{");
    batchBuffer.append(
        " \"timestamp\": \""
            + timestamp
            + ".000Z\","); // should move the timezone part into the timestamp string
    batchBuffer.append(" \"tagValue\": \"" + tagValue + "\"");
    batchBuffer.append("}");
  }

  /**
   * call when adding new tag to omf message to reference container id
   *
   * @param tagName
   */
  public static void addContainerStartToOMFDataMessage(String tagName) {
    batchBuffer.append("{");
    // each tag has a container id that is set to the tag name
    batchBuffer.append("\"containerid\": \"" + tagName + "\"");
    batchBuffer.append(",");
    batchBuffer.append("\"values\": [");
  }

  /** call after finished adding all data points to close values array in json */
  public static void addContainerEndToOMFDataMessage() {
    batchBuffer.append("]");
    batchBuffer.append(
        "}"); // closes off the end of the container specific portion of the data message
  }

  /** call this before each call to addPointToOMFDataMessage but not before the first one */
  public static void separateDataMessage() {
    batchBuffer.append(",");
  }

  /** call this first when finished building up the OMF message */
  public static void endOMFDataMessage() {
    batchBuffer.append("]");
  }

  /** should be called for every tag in config list at constructor */
  private static void setContainerJson(ArrayList tagList) {
    startOMFDataMessage();

    for (int i = 0; i < tagList.size(); i++) {

      String tagName = ((Tag) tagList.get(i)).getTagName();

      // after first tag, separate by comma
      if (i > 0) {
        separateDataMessage();
      }

      batchBuffer.append("{");
      batchBuffer.append("\"id\": \"" + tagName + "\",");
      batchBuffer.append("\"typeid\": \"" + typeID + "\"");
      batchBuffer.append("}");
    }

    endOMFDataMessage();
  }

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
  // end of omf batch buffer functions

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

  public boolean postTagsLive(ArrayList tags) {
    String body = "{\n";
    for (int tagIndex = 0; tagIndex < tags.size(); tagIndex++) {
      body += "  \"" + Integer.toString(tagIndex - 1) + "\": {\n";
      body += "    \"Method\": \"POST\",\n";
      body +=
          "    \"Resource\": \""
              + targetURL
              + "streams/"
              + ((Tag) tags.get(tagIndex)).getWebID()
              + "/Value\",\n";
      body +=
          "    \"Content\": \""
              + buildBody(((Tag) tags.get(tagIndex)).getTagValue(), getCurrentTimeString(), true)
              + "\",\n";
      body += "    \"Headers\": {\"Authorization\": \"Basic " + authCredentials + "\"" + "}\n";
      if (tagIndex < tags.size()) {
        body += "  },\n";
      } else {
        body += "  }\n";
      }
    }
    body += "}";

    int res = NO_ERROR;
    try {
      res = RequestHTTPS(targetURL + "batch/", "Post", postHeaders, body, "", "");
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

  public static boolean postDataPoint(Tag tag, DataPoint dataPoint) {
    int res = NO_ERROR;
    try {
      res =
          RequestHTTPS(
              targetURL + "streams/" + tag.getWebID() + "/Value",
              "Post",
              postHeaders,
              buildBody(dataPoint.getValueString(), dataPoint.getTimeStamp(), false),
              "",
              "");
    } catch (JSONException e) {
      Logger.LOG_SERIOUS(
          "Failed to post value of " + tag.getTagName() + "due to malformed JSON response");
      Logger.LOG_EXCEPTION(e);
    }

    if (res != NO_ERROR) {
      Logger.LOG_SERIOUS("Failed to post value of " + tag.getTagName());
      return false;
    }
    return true;
  }

  // Posts a tag value to the OSIsoft server
  public void postTag(Tag tag) {
    int res = NO_ERROR;
    try {
      res =
          RequestHTTPS(
              targetURL + "streams/" + tag.getWebID() + "/Value",
              "Post",
              postHeaders,
              buildBody(tag.getTagValue(), getCurrentTimeString(), false),
              "",
              "");
    } catch (JSONException e) {
      Logger.LOG_SERIOUS(
          "Failed to post value of " + tag.getTagName() + "due to malformed JSON response");
      Logger.LOG_EXCEPTION(e);
    }

    if (res != NO_ERROR) {
      Logger.LOG_SERIOUS("Failed to post value of " + tag.getTagName());
    }
  }

  // Builds and returns the body content
  // Hardcoded JSON payload
  private static String buildBody(String value, String timestamp, boolean escapeQuotes) {
    String quote;
    if (escapeQuotes) {
      quote = "\\\"";
    } else {
      quote = "\"";
    }

    String jsonBody =
        "{"
            + quote
            + "Timestamp"
            + quote
            + ": "
            + quote
            + timestamp
            + quote
            + ","
            + quote
            + "Value"
            + quote
            + ": "
            + value
            + ","
            + quote
            + "UnitsAbbreviation"
            + quote
            + ": "
            + quote
            + quote
            + ","
            + quote
            + "Good"
            + quote
            + ": true,"
            + quote
            + "Questionable"
            + quote
            + ": false"
            + "}";
    return jsonBody;
  }

  // Creates an OSIsoft compatible timestamp string
  private static String getCurrentTimeString() {
    Date d = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String timestamp = dateFormat.format(d);
    return timestamp;
  }

  public String convertTimeString(Date d) {
    String timestamp = dateFormat.format(d);
    return timestamp;
  }

  private static String buildNewPointBody(String tagName, byte tagType) {
    String type;

    switch (tagType) {
      case DataPoint.TYPE_BOOLEAN:
        type = "Digital";
        break;
      case DataPoint.TYPE_FLOAT:
        type = "Float64";
        break;
      case DataPoint.TYPE_INT:
        type = "Int32";
        break;
      case DataPoint.TYPE_DWORD:
        type = "Float64";
        break;
      default:
        Logger.LOG_SERIOUS("Invalid datatype of " + tagType + " for new PI Point");
        return "";
    }

    String jsonBody =
        "{\r\n"
            + "  \"Name\": \""
            + tagName
            + "\",\r\n"
            + "  \"Descriptor\": \""
            + tagName
            + "\",\r\n"
            + "  \"PointClass\": \"classic\",\r\n"
            + "  \"PointType\": \""
            + type
            + "\",\r\n"
            + "  \"EngineeringUnits\": \"\",\r\n"
            + "  \"Step\": false,\r\n"
            + "  \"Future\": false\r\n"
            + "}";
    return jsonBody;
  }
}
