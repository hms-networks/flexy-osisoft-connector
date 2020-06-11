package com.hms.flexyosisoftconnector;

import com.hms_networks.americas.sc.logging.Logger;

public class DataPoster extends Thread {


  /**
   * The PayloadManager to retrieve finished payloads from.
   */
  private PayloadManager payloadHolder;

  /**
   * Constructor for DataPoster class.
   *
   * @param payloadManager passes in the payload manager previously initialized
   */
  public DataPoster(PayloadManager payloadManager) {
    payloadHolder = payloadManager;
  }

  /**
   * Run function used to continuously send payloads to OSIsoft.
   */
  public void run() {

    while (true) {

      String payload = payloadHolder.getNextPayload();

      // If there are any payloads to send
      if (payload.length() > 0) {
        Logger.LOG_DEBUG("Sending completed payload to OSIsoft");

        boolean retval = false;
        switch (OSIsoftConfig.getCommunicationType()) {
          case OSIsoftConfig.OMF:
            retval = OSIsoftServer.postOMFBatch(payload);
            break;
          case OSIsoftConfig.PI_WEB_API:
            retval = OSIsoftServer.postBatch(payload);
            break;
          case OSIsoftConfig.OCS:
            // see if we need to update the OCS token
            OSIsoftConfig.updateOcsToken();
            // send the data after updating
            retval = OSIsoftServer.postOcsBatch(payload);
            break;
          default:
            Logger.LOG_SERIOUS(OSIsoftConfig.COM_ERR_MSG);
            break;
        }

        if (!retval) {
          Logger.LOG_WARN("Unable to send payload to OSIsoft");
        }
      }

      // thread finished
      Thread.yield();
      try {
        final int sleepTimeMs = 100;
        Thread.sleep(sleepTimeMs);
      } catch (InterruptedException e) {
        Logger.LOG_SERIOUS("Unable to sleep connected DataPoster thread.");
        Logger.LOG_EXCEPTION(e);
      }
    }
  }
}
