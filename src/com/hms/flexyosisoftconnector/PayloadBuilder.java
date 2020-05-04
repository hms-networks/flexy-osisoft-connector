package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.SysControlBlock;
import com.hms_networks.americas.sc.datapoint.DataPoint;
import com.hms_networks.americas.sc.datapoint.DataType;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.logging.Logger;
import com.hms_networks.americas.sc.taginfo.TagInfo;
import com.hms_networks.americas.sc.taginfo.TagInfoManager;
import com.hms_networks.americas.sc.taginfo.TagType;

import java.util.ArrayList;

/**
 * Class to build up a JSON payload in segments of strings.
 *
 * @author HMS Networks Inc. Solution Center
 */
public class PayloadBuilder {

  /** array of tag web id's */
  private static String tagWebIdList[];

  /** Index offset used to find the tagWebIDList index from the tag ID. */
  private static int tagWebIdListIndexOffset;

  /** Initialize an array of tag web ID's for legacy function web ID lookup. */
  public static void initWebIdList() {
    // setup an indicted array of web Id's
    int lowestSeen = TagInfoManager.getLowestTagIdSeen();
    int highestSeen = TagInfoManager.getHighestTagIdSeen();
    tagWebIdListIndexOffset = lowestSeen;
    int tagListArraySize = highestSeen - lowestSeen + 1;

    if (tagListArraySize == -1) {
      tagListArraySize = 1;
    }
    tagWebIdList = new String[tagListArraySize];
  }

  /**
   * Set the web idea to the retrieved web id from initializing a tag.
   *
   * @param tagId tag's ID
   * @param webID tag's webID
   */
  public static void setTagWebId(int tagId, String webID) {
    int index = tagId - tagWebIdListIndexOffset;
    tagWebIdList[index] = webID;
  }

  /**
   * Retrieve the start of an OMF payload.
   *
   * @return the payload segment as a string
   */
  public static String startBatchOldFormat() {
    String payload = "{\n";
    return payload;
  }

  /**
   * Retrieve the end of an OMF payload.
   *
   * @return the payload segment as a string
   */
  public static String endBatchOldFormat() {
    String payload = "}";
    return payload;
  }

  /**
   * Builds the JSON contents for a new data point in the payload.
   *
   * <p>The payload needs a comma before adding the next point, which is added when {@link
   * #isFirstPoint} is set to false;
   *
   * @param tagName the name of the tag
   * @param tagValue the value of the tag
   * @param isFirstPoint is this the first datapoint in the payload
   * @return the payload segment as a string
   */
  public static String addPoint(String tagName, String tagValue, boolean isFirstPoint) {
    String payload = "";
    if (!isFirstPoint) {
      payload += ",";
    }
    payload += "{\"name\": \"" + tagName + "\",\"result\": " + tagValue + "}";
    return payload;
  }

  /**
   * Adds a data point to the payload via the legacy format.
   *
   * @param dataPoint the data point to add
   * @param batchCount which data point we are adding
   * @return returns a JSON segment containing the added data point.
   */
  public static String addPointOldFormat(DataPoint dataPoint, int batchCount) {
    int index = dataPoint.getTagId() - tagWebIdListIndexOffset;
    String webId = tagWebIdList[index];
    String targetURL = "https://" + OSIsoftConfig.getServerIP() + "/piwebapi/";
    String authCredentials = OSIsoftConfig.getServerLogin();

    String payload =
        "  \""
            + Integer.toString(batchCount)
            + "\": {\n"
            + "    \"Method\": \"POST\",\n"
            + "    \"Resource\": \""
            + targetURL
            + "streams/"
            + webId
            + "/Value\",\n"
            + "    \"Content\": \""
            + buildBody(dataPoint.getValueString(), dataPoint.getTimeStamp(), true)
            + "\",\n"
            + "    \"Headers\": {\"Authorization\": \"Basic "
            + authCredentials
            + "\""
            + "}\n"
            + "  },\n";
    return payload;
  }

  /**
   * BuildBody is used to add a data point to the legacy format of PIWebApi.
   *
   * @param value Data point value
   * @param timestamp Data point time stamp
   * @param escapeQuotes Indicator to include escape characters before quotes
   * @return the payload segment as a string
   */
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

  /**
   * Builds the JSON contents for the end of the payload.
   *
   * @param timestamp The time should be in format yyyy-mm-ddThh:mm:ssZ.
   * @return the payload segment as a string
   */
  public static String endPayload(String timestamp) {
    String payload = "],\"lastSynchroDate\": \"" + timestamp + "\"}";
    return payload;
  }

  /**
   * Builds the start of the JSON data message.
   *
   * @return the payload segment as a string
   */
  public static String startOMFDataMessage() {
    String payload = "[";
    return payload;
  }

  /**
   * This constructs the start of a new container.
   *
   * @param the tag's name
   * @return the payload segment as a string
   */
  public static String addContainerStartToOMFDataMessage(String tagName) {
    // each tag's container id is set to the tag's name
    String payload = "{" + "\"containerid\": \"" + tagName + "\"" + "," + "\"values\": [";

    return payload;
  }

  /**
   * This constructs a new data point omf message.
   *
   * @param tagValue data point's value
   * @param timestamp data point's time stamp
   * @param isFirstPoint marks if this is the first data point in the message
   * @return return the JSON payload for a new OMF data point
   */
  public static String addPointToOMFDataMessage(
      String tagValue, String timestamp, boolean isFirstPoint) {
    String payload = "";

    if (!isFirstPoint) {
      payload += ",";
    }

    payload +=
        "{"
            + " \"timestamp\": \""
            + timestamp
            + ".000Z\","
            + " \"tagValue\": \""
            + tagValue
            + "\""
            + "}";

    return payload;
  }

  /**
   * Get the end of a container message.
   *
   * @return return the JSON payload for the end of an OMF container message.
   */
  public static String addContainerEndToOMFDataMessage() {
    // closes off the end of the container specific portion of the data message
    String payload = "]}";
    return payload;
  }

  /**
   * Get the end of an OMF message.
   *
   * @return return the JSON payload for the end of an OMF data message.
   */
  public static String endOMFDataMessage() {

    String payload = "]";
    return payload;
  }

  /**
   * Setup the format for how data from this device will be stored with omf
   *
   * @return returns the JSON payload to construct a new Type message.
   */
  public static String getTypeBody() {
    String payload =
        "[{"
            + "\"id\": \"HMS-type-"
            + getFlexyName()
            + "\","
            + "\"classification\": \"dynamic\","
            + "\"type\": \"object\","
            + "\"properties\": {"
            + "\"timestamp\": {"
            + "\"type\": \"string\","
            + "\"format\": \"date-time\","
            + "\"isindex\": true"
            + "},"
            + "\"tagValue\": {"
            + "\"type\": \"string\","
            + "\"description\": \"Ewon Flexy's tag value stored as a string\""
            + "}"
            + "}"
            + "}]";

    return payload;
  }

  /**
   * This sets up OMF to have the containers
   *
   * @return returns the JSON segment to construct a container for each of the tags.
   */
  public static String getContainerSettingJson() {
    String typeID = "HMS-type-" + getFlexyName();
    ;
    String payload = startOMFDataMessage();

    for (int i = 0; i < TagInfoManager.getTagInfoList().size(); i++) {

      String tagName = ((TagInfo) TagInfoManager.getTagInfoList().get(i)).getName();

      // after the first tag, separate by comma
      if (i > 0) {
        payload += ",";
      }

      payload += "{" + "\"id\": \"" + tagName + "\"," + "\"typeid\": \"" + typeID + "\"" + "}";
    }

    payload += endOMFDataMessage();
    return payload;
  }

  /**
   * Reads the unique name given to the Flexy
   *
   * @return Name of the Flexy
   */
  private static String getFlexyName() {
    String res = "";
    SysControlBlock SCB;
    try {
      SCB = new SysControlBlock(SysControlBlock.SYS);
      res = SCB.getItem("Identification");
    } catch (Exception e) {
      Logger.LOG_EXCEPTION(e);
      Logger.LOG_SERIOUS("Error reading Ewon's name");
    }
    return res;
  }

  /**
   * The old PiWebApi format uses this function to construct a payload body for initial data point
   * creation.
   *
   * @param tag The TagInfo object that holds required information.
   * @return Returns a string representation of the constructed json body.
   */
  public static String buildNewPointBody(TagInfo tag) {
    String type;
    String jsonBody;

    if (tag.getType().equals(TagType.BOOLEAN)) {
      type = "Digital";
    } else if (tag.getType().equals(TagType.FLOAT)) {
      type = "Float64";
    } else if (tag.getType().equals(TagType.INTEGER)) {
      type = "Int32";
    } else if (tag.getType().equals(TagType.DWORD)) {
      type = "Float64";
    } else {
      Logger.LOG_SERIOUS("Invalid datatype of " + tag.getType() + " for new PI Point");
      jsonBody = "";
    }

    jsonBody =
        "{\r\n"
            + "  \"Name\": \""
            + tag.getName()
            + "\",\r\n"
            + "  \"Descriptor\": \""
            + tag.getName()
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
