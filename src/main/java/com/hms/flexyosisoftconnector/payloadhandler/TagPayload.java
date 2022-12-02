package com.hms.flexyosisoftconnector.payloadhandler;

import com.hms_networks.americas.sc.extensions.datapoint.DataPoint;

/**
 * This class holds the incomplete payload as well as one of the datapoint objects.
 *
 * @author HMS Networks Inc. Solution Center
 */
public class TagPayload {

  /** Holds the JSON payload. */
  private final StringBuffer payload = new StringBuffer();

  /** Holds the tag information associated with the payload. */
  private DataPoint dataPoint;

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
