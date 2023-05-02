package com.hms.flexyosisoftconnector.payloadhandler;

import com.hms_networks.americas.sc.extensions.datapoint.DataPoint;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class constructs payloads to send to OSISoft.
 *
 * <p>The payloads will be stored as an array of strings, where each string is a complete payload.
 *
 * @author HMS Networks Inc. Solution Center
 */
public class PayloadManager {

  /** List containing completed payloads */
  private final List payloads;

  /** Maximum number of payloads */
  static final int MAX_PAYLOADS = 8;

  /**
   * When the payload queue is full, wait this long before checking if there is room for a new
   * payload.
   */
  static final int WAIT_FOR_PAYLOAD_SEND_MILLISECONDS = 1000;

  /** Holds the not fully constructed payload information as it gets built. */
  OsisoftJsonPayload osisoftPayload;

  /** Constant for status okay */
  static final int STATUS_OKAY = 4;

  /** Constant for status stop */
  static final int STATUS_STOP = 5;

  /** Current payload manager status */
  private int status = STATUS_OKAY;

  /** Index of the top of the payload queue */
  static final int TOP_OF_QUEUE = 0;

  /**
   * Constructor for the Payload Manager.
   *
   * @param communicationType The communication type can be OMF or PIWebApi format.
   */
  public PayloadManager(int communicationType) {

    // The unfinished payload information is stored in this.
    osisoftPayload = new OsisoftJsonPayload(communicationType);

    // The complete payload strings are stored in this.
    payloads = Collections.synchronizedList(new ArrayList());
  }

  /**
   * Append a new datapoint to a payload with the specified information.
   *
   * @param dataPoint holds all the information for a new datapoint.
   */
  public void appendDataPointToPayLoad(DataPoint dataPoint) {

    osisoftPayload.appendDataPointToPayLoad(dataPoint);
    checkPayloadStatus();
  }

  /**
   * Check the status of the payload at the specified index. If it is a complete payload, add it to
   * the list of start new payload.
   */
  private void checkPayloadStatus() {
    if (osisoftPayload.statusIsComplete()) {
      addToQueue(osisoftPayload.getPayload());
      osisoftPayload.startPayload();
    }
  }

  /**
   * Add the specified payload string to the payload queue list.
   *
   * @param payload payload to add to queue
   */
  private void addToQueue(String payload) {
    waitForPayloadSend();

    synchronized (payloads) {
      payloads.add(payload);
    }

    Logger.LOG_DEBUG("Payload completed count is: " + payloads.size());
    if (payloads.size() >= MAX_PAYLOADS) {
      status = STATUS_STOP;
    }
  }

  /**
   * Gets the next payload from the top of the payload queue.
   *
   * @return next payload
   */
  public String getNextPayload() {
    String payload = "";

    synchronized (payloads) {
      if (!payloads.isEmpty()) {
        payload = (String) payloads.remove(TOP_OF_QUEUE);
        if (status == STATUS_STOP) {
          status = STATUS_OKAY;
        }
      }
    }

    return payload;
  }

  /** Clear the contents of the payload queue. */
  public void clearQueue() {
    synchronized (payloads) {
      payloads.clear();
    }
  }

  /**
   * Only store up to {@link #MAX_PAYLOADS} payloads for OSIsoft to send out. If there is a full
   * queue, wait for payloads to be sent out before adding more.
   */
  private void waitForPayloadSend() {

    // Wait until status is not STOP
    while (status == STATUS_STOP) {
      if (payloads.size() != MAX_PAYLOADS) {
        status = STATUS_OKAY;
      }
      try {
        Thread.sleep(WAIT_FOR_PAYLOAD_SEND_MILLISECONDS);
      } catch (InterruptedException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Payload Manager was unable to thread sleep.");
      }
    }
  }
}
