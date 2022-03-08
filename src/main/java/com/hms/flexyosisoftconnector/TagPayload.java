package com.hms.flexyosisoftconnector;

import com.hms_networks.americas.sc.extensions.datapoint.DataPoint;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import com.hms_networks.americas.sc.extensions.string.PreAllocatedStringBuilder;

/**
 * This class holds the incomplete payload as well as one of the datapoint objects.
 *
 * @author HMS Networks Inc. Solution Center
 */
public class TagPayload {

  /** Holds the JSON payload. */
  private PreAllocatedStringBuilder payload;

  /** Holds the tag information associated with the payload. */
  private DataPoint dataPoint;

  /** Constructs a new empty payload. */
  public TagPayload() {
    if (OsisoftJsonPayload.MAX_PAYLOAD_NUM_CHARACTERS == OsisoftJsonPayload.UNINITIALIZED_INTEGER) {
      Logger.LOG_SERIOUS("The OSIsoftJSONPayload class has not been initialized.");
      Logger.LOG_SERIOUS("Unable to allocate space for a new PreAllocatedStringBuilder object.");
    } else {
      payload = new PreAllocatedStringBuilder(OsisoftJsonPayload.MAX_PAYLOAD_NUM_CHARACTERS);
    }
  }

  /**
   * Appends payloadPart to the already constructed payload.
   *
   * @param payloadPart a JSON segment to append to the payload
   */
  public void add(String payloadPart) {
    payload.append(payloadPart);
  }

  /**
   * Get the JSON payload.
   *
   * @return the JSON payload
   */
  public String getPayload() {
    return payload.toString();
  }

  /**
   * Get the datapoint.
   *
   * @return the datapoint
   */
  public DataPoint getDataPoint() {
    return dataPoint;
  }

  /**
   * Set the datapoint
   *
   * @param dataPoint holds the datapoint to set.
   */
  public void setDataPoint(DataPoint dataPoint) {
    this.dataPoint = dataPoint;
  }
}
