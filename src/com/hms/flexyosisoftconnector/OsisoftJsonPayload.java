package com.hms.flexyosisoftconnector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.hms.flexyosisoftconnector.PayloadBuilder;
import com.hms_networks.americas.sc.datapoint.DataPoint;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.logging.Logger;
import com.hms_networks.americas.sc.string.PreAllocatedStringBuilder;
import com.hms_networks.americas.sc.taginfo.TagConstants;
import com.hms_networks.americas.sc.taginfo.TagInfoManager;

/**
 * This class will hold a payload string and a status
 *
 * <p>The payload string will be appended to until the status has been set to done
 *
 * @author HMS Networks Inc. Solution Center
 */
public class OsisoftJsonPayload {

  /** Current payload status */
  int status;

  /** String builder for payload */
  final PreAllocatedStringBuilder payload;

  /** Constant representing payload not started */
  static final int PAYLOAD_NOT_STARTED = 0;

  /** Constant representing payload header complete */
  static final int PAYLOAD_HEADER_COMPLETE = 1;

  /** Constant representing payload values in progress */
  static final int PAYLOAD_VALUES_IN_PROGRESS = 2;

  /** Constant representing payload values complete */
  static final int PAYLOAD_VALUES_COMPLETE = 3;

  /** Constant representing payload completion */
  static final int PAYLOAD_COMPLETE = 4;

  /** Constant representing how many datapoints can be added to a payload */
  static final int MAX_DATA_POINTS = 200;

  /**
   * The unfinished payload will have an array of strings. Each tag has it's own array that gets
   * added to when the tag is encountered.
   */
  TagPayload[] tagPayloadArr;

  /** Array of boolean flags for tags encountered (by ID) */
  boolean[] tagNamesEncountered;

  /** Count of tags encountered */
  int dataPointsEncounteredCount = 0;

  /**
   * Offset for indexing tags in {@link #tagNamesEncountered}
   *
   * <p>This offset will be equal to the lowest valued tag ID found.
   */
  int tagIndexOffset;

  /** Size of encountered tags array */
  int tagsEncounteredArraySize;

  /**
   * Timestamp for the start of payload
   *
   * <p>This timestamp means that payload datapoint can come from any time from the timestamp listed
   * up to an additional {@link #PAY_LOAD_TIME_PERIOD_SECONDS} seconds. Payloads are from a time
   * period, not a single time stamp.
   */
  long payloadStartTimestamp = 0;

  /** This is the number of seconds to wait before cutting off the payload and completing it. */
  static final long PAY_LOAD_TIME_PERIOD_SECONDS = 10;

  /**
   * represents either OMF or the legacy data format. The valid values are piwebapi which is 1, or
   * omf which is 0
   */
  int communicationType;

  /** Error message to display on unknown communication type. */
  String comsErrMsg = "Unknown communication type given. Unable to build payload.";

  /**
   * Constructor for the OsisoftJsonPayload.
   *
   * @param comType The payload will be constructed based on either the OMF format or the legacy
   *     format.
   */
  public OsisoftJsonPayload(int comType) {

    if (comType != OSIsoftConfig.piwebapi && comType != OSIsoftConfig.omf) {
      comType = OSIsoftConfig.omf;
      Logger.LOG_SERIOUS("Invalid communication type set. Please check the config file settings.");
    }

    final int byteSizePerDataPoint = 80;
    final int byteSizeForStartAndEnd = 200;
    final int maxByteSize = (MAX_DATA_POINTS * byteSizePerDataPoint) + byteSizeForStartAndEnd;
    payload = new PreAllocatedStringBuilder(maxByteSize);
    status = PAYLOAD_NOT_STARTED;
    communicationType = comType;

    startPayload();

    // set up array for tags encountered
    int lowestSeen = TagInfoManager.getLowestTagIdSeen();
    int highestSeen = TagInfoManager.getHighestTagIdSeen();

    if (highestSeen != TagConstants.UNINIT_INT_VAL) {
      tagIndexOffset = lowestSeen;
      tagsEncounteredArraySize = highestSeen - lowestSeen + 1;
      tagNamesEncountered = new boolean[tagsEncounteredArraySize];
      tagPayloadArr = new TagPayload[tagsEncounteredArraySize];
    } else {
      Logger.LOG_SERIOUS(
          "Unable to initialize tags list."
              + " It is possible no tags have been added to this device.");
      tagNamesEncountered = new boolean[0];
    }
  }

  /**
   * Set the value of the tagId's index to true if it is being 'added' to this array
   *
   * @param tagId Id of the tag
   */
  private void addToTagsEncounteredArray(int tagId) {
    int index = tagId - tagIndexOffset;
    tagNamesEncountered[index] = true;
  }

  /**
   * Return if the specified tag ID has been encountered
   *
   * @param tagId tag ID
   * @return true if tag ID has been encountered
   */
  private boolean checkIfTagIdEncountered(int tagId) {
    int index = tagId - tagIndexOffset;
    return tagNamesEncountered[index];
  }

  /**
   * Get the payload as a string
   *
   * @return payload string
   */
  public String getPayload() {
    return payload.toString();
  }

  /**
   * This function starts a new payload with the objects payload settings.
   *
   * <p>The status of the payload will be updated to show that the header information has been
   * added.
   */
  public void startPayload() {

    payload.clearString();
    tagNamesEncountered = new boolean[tagsEncounteredArraySize];
    tagPayloadArr = new TagPayload[tagsEncounteredArraySize];
    payloadStartTimestamp = 0;
    dataPointsEncounteredCount = 0;
    String startOfPayload;

    switch (communicationType) {
      case OsisoftConfig.omf:
        startOfPayload = PayloadBuilder.startOMFDataMessage();
        break;
      case OsisoftConfig.piwebapi:
        startOfPayload = PayloadBuilder.startBatchOldFormat();
        break;
      default:
        Logger.LOG_SERIOUS(comsErrMsg);
        startOfPayload = "no communication type";
        break;
    }

    boolean didAppendToPayload = payload.append(startOfPayload);
    if (didAppendToPayload) {
      status = PAYLOAD_HEADER_COMPLETE;
    } else {
      Logger.LOG_CRITICAL("An error occurred while appending the start of a payload.");
    }
  }

  /**
   * Append a data point to the payload
   *
   * @param dataPoint the data point to append
   */
  public void appendDataPointToPayLoad(DataPoint dataPoint) {

    addDataPoint(dataPoint);

    didReachMaxDataPoints();

    didTakeTooLong(dataPoint);

    completePayloadAttempt();
  }

  /**
   * Ensures that we do not wait endlessly for a payload to fill up completely. Cuts off the payload
   * after {@link #PAY_LOAD_TIME_PERIOD_SECONDS}
   *
   * @param data the data point to check
   */
  private void didTakeTooLong(DataPoint data) {
    long time = Long.parseLong(data.getTimeStamp());

    /*
     * The initial value of the start time is 0.
     * set the value to 0 whenever the payload is complete.
     *
     * In either case, start a new time period and set the time.
     */
    if (payloadStartTimestamp == 0) {
      payloadStartTimestamp = time;

    } else if (payloadStartTimestamp < (time - PAY_LOAD_TIME_PERIOD_SECONDS)) {
      // start a new payload due to reaching time period limit
      status = PAYLOAD_VALUES_COMPLETE;
    }
  }

  /**
   * Adds a datapoint to the payload
   *
   * @param dataPoint The data point to add
   */
  private void addDataPoint(DataPoint dataPoint) {
    String newPayloadPart;
    int index = tagIdToIndex(dataPoint.getTagId());

    String timestamp = getFormattedStartTime(Long.parseLong(dataPoint.getTimeStamp()));
    boolean isFirstPoint;

    // if we have not seen that tag before, init it
    if (checkIfTagIdEncountered(dataPoint.getTagId())) {
      isFirstPoint = false;
    } else {
      // the tag has not been seen it yet
      addToTagsEncounteredArray(dataPoint.getTagId());
      tagPayloadArr[index] = new TagPayload();
      ((TagPayload) tagPayloadArr[index]).setDataPoint(dataPoint);

      isFirstPoint = true;
    }

    switch (communicationType) {
      case OsisoftConfig.omf:
        newPayloadPart =
            PayloadBuilder.addPointToOMFDataMessage(
                dataPoint.getValueString(), timestamp, isFirstPoint);
        break;
      case OsisoftConfig.piwebapi:
        newPayloadPart = PayloadBuilder.addPointOldFormat(dataPoint, dataPointsEncounteredCount);
        break;
      default:
        Logger.LOG_SERIOUS(comsErrMsg);
        break;
    }

    ((TagPayload) tagPayloadArr[index]).add(newPayloadPart);

    // might remove all this if statement
    if (status == PAYLOAD_HEADER_COMPLETE) {

      status = PAYLOAD_VALUES_IN_PROGRESS;

      long time = Long.parseLong(dataPoint.getTimeStamp());
      payloadStartTimestamp = time;
    }

    // add to the count of data points
    dataPointsEncounteredCount++;
  }

  /** Checks to see if the payload has added up to the max number of points. */
  private void didReachMaxDataPoints() {

    if (dataPointsEncounteredCount >= MAX_DATA_POINTS) {
      status = PAYLOAD_VALUES_COMPLETE;
    }
  }

  /**
   * Convert payload start timestamp from epoch seconds to ISO 8601 date format in UTC.
   *
   * @param time the epoch time to get converted
   * @return formatted payload start timestamp
   */
  private String getFormattedStartTime(long time) {
    final int timeMillisPerSec = 1000;
    Date d = new Date(time * timeMillisPerSec);
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String timestamp = dateFormat.format(d);
    return timestamp;
  }

  /**
   * Get the index of the {@link #tagNamesEncountered} from the tag ID.
   *
   * @param tagId ID of the tag
   * @return returns the index
   */
  private int tagIdToIndex(int tagId) {
    int index = tagId - tagIndexOffset;
    return index;
  }

  /** Completes the payload if it has values */
  public void completePayloadAttempt() {
    if (status == PAYLOAD_VALUES_COMPLETE) {
      // Payload has values, end the payload
      String batchTimeStamp = getFormattedStartTime(payloadStartTimestamp);

      boolean firstPayload = true;

      // add all the tag's info to the payload
      for (int i = 0; i < tagPayloadArr.length; i++) {

        if (tagPayloadArr[i] != null && tagPayloadArr[i].payload.length() > 1) {

          TagPayload tagPayload = (TagPayload) tagPayloadArr[i];

          String tagName = tagPayload.getDataPoint().getTagName();

          if (!firstPayload) {
            payload.append(",");
          } else {
            firstPayload = false;
          }

          payload.append(PayloadBuilder.addContainerStartToOMFDataMessage(tagName));

          payload.append(tagPayload.payload);

          payload.append(PayloadBuilder.addContainerEndToOMFDataMessage());
        }
      }

      boolean allAppended = payload.append(PayloadBuilder.endOMFDataMessage());

      if (!allAppended) {
        Logger.LOG_SERIOUS(
            "An error occurred while appending a payload to a message. Datapoints may be lost.");

      } else {
        status = PAYLOAD_COMPLETE;
      }
    }
  }

  /**
   * Checks if the payload status is set to {@link #PAYLOAD_COMPLETE}
   *
   * @return returns true if it is
   */
  public boolean statusIsComplete() {
    return (status == PAYLOAD_COMPLETE);
  }
}
