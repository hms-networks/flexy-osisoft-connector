package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.SysControlBlock;
import com.hms.flexyosisoftconnector.configuration.OSIsoftConfig;
import com.hms.flexyosisoftconnector.dataserver.DataPoster;
import com.hms.flexyosisoftconnector.dataserver.OSIsoftServer;
import com.hms.flexyosisoftconnector.dataserver.RestFileServer;
import com.hms.flexyosisoftconnector.payloadhandler.PayloadManager;
import com.hms_networks.americas.sc.extensions.datapoint.DataPoint;
import com.hms_networks.americas.sc.extensions.historicaldata.CircularizedFileException;
import com.hms_networks.americas.sc.extensions.historicaldata.EbdTimeoutException;
import com.hms_networks.americas.sc.extensions.historicaldata.HistoricalDataQueueManager;
import com.hms_networks.americas.sc.extensions.historicaldata.TimeTrackerUnrecoverableException;
import com.hms_networks.americas.sc.extensions.json.JSONException;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import com.hms_networks.americas.sc.extensions.system.http.SCHttpUtility;
import com.hms_networks.americas.sc.extensions.system.time.SCTimeUtils;
import com.hms_networks.americas.sc.extensions.taginfo.TagInfoManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Ewon Flexy java demo for OSIsoft Server
 *
 * <p>This demo reads multiple tag values from an Ewon Flexy IO Server and POSTs them to an OSIsoft
 * PI Server.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class OSIsoftConnectorMain {

  /** Default name of a new Ewon */
  static final String DEFAULT_EWON_NAME = "eWON";

  /** Filename of connector config file */
  static final String CONNECTOR_CONFIG_FILENAME = "/usr/ConnectorConfig.json";

  /** PI Server management object */
  static OSIsoftServer piServer;

  /** Constructs payloads out of data points and holds them for sending. */
  static PayloadManager payloadMngr;

  public static void main(String[] args) throws IOException, JSONException {
    // Indicate the version number and that the application is starting
    Logger.LOG_CRITICAL(
        OSIsoftConfig.CONNECTOR_NAME + " v" + OSIsoftConfig.CONNECTOR_VERSION + " starting");

    // Start the webserver to accept json file
    RestFileServer restServer = new RestFileServer();
    restServer.start();

    OSIsoftConfig.initConfig(CONNECTOR_CONFIG_FILENAME);

    setLocalTimeZone();

    // Check that the flexy has a non-default name, stop the application if not
    if (OSIsoftConfig.getFlexyName().equals(DEFAULT_EWON_NAME)) {
      Logger.LOG_SERIOUS("Device name is set to \"eWON\" which is the default name");
      Logger.LOG_SERIOUS("This device's name must be changed from default");
      Logger.LOG_SERIOUS("Application aborting due to default name use");
      System.exit(0);
    }

    // Set the path to the directory holding the certificate for the server
    // Only needed if the certificate is self signed
    setCertificatePath(OSIsoftConfig.getCertificatePath());
    setHttpTimeouts(OSIsoftConfig.getHttpTimeoutSeconds());

    // update tag info to use later
    TagInfoManager.refreshTagList();

    payloadMngr = new PayloadManager(OSIsoftConfig.getCommunicationType());

    piServer = new OSIsoftServer();

    int res;
    do {
      // Sleep between initialization attempts to increase Flexy performance.
      Thread.yield();
      try {
        final int threadWaitMS = 5;
        Thread.sleep(threadWaitMS);
      } catch (InterruptedException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Exception thrown while sleeping thread.");
      }

      Logger.LOG_INFO("Initializing tags");
      res = piServer.initTags();

    } while (res != SCHttpUtility.HTTPX_CODE_NO_ERROR);

    Logger.LOG_INFO("Finished initializing tags");

    DataPoster dataThread = new DataPoster(payloadMngr);
    dataThread.start();

    while (true) {
      getNewPayloadToSend();

      // pause to help the flexy with the infinite loop
      Thread.yield();
      try {
        final int threadWaitMS = 500;
        Thread.sleep(threadWaitMS);
      } catch (InterruptedException e) {
        Logger.LOG_EXCEPTION(e);
      }
    }
  }

  /**
   * Grabs a new set of data points from the top of the queue and feeds each on to payload manager.
   */
  private static void getNewPayloadToSend() {
    Logger.LOG_DEBUG("Getting next section of datapoints");
    ArrayList queuePoints = new ArrayList();
    try {
      if (!HistoricalDataQueueManager.doesTimeTrackerExist()) {
        // Start a new time tracker file at current time
        HistoricalDataQueueManager.getFifoNextSpanDataAllGroups(true);
      } else {
        // Get the data points for current time period in time tracker
        queuePoints = HistoricalDataQueueManager.getFifoNextSpanDataAllGroups(false);
      }
    } catch (Exception e) {
      String errorMessage = "There was an unknown error collecting data points!";
      if (e instanceof IOException) {
        errorMessage =
            "Exception thrown when obtaining new batch of datapoints. The queue will retry.";
      } else if (e instanceof CircularizedFileException) {
        errorMessage = "Data in the historical log has been overwritten with newer data.";
      } else if (e instanceof EbdTimeoutException) {
        errorMessage = "EBD call has timed out while fetching data.";
      } else if (e instanceof JSONException) {
        errorMessage = "Invalid JSON received while fetching data.";
      } else if (e instanceof TimeTrackerUnrecoverableException) {
        errorMessage = "Time tracker file has been corrupted.";
      }
      Logger.LOG_WARN(errorMessage);
      Logger.LOG_EXCEPTION(e);
    }

    // if there are data points to send
    if (queuePoints.size() != 0) {
      // Check if queue is behind
      try {
        long queueBehindMillis = HistoricalDataQueueManager.getQueueTimeBehindMillis();
        long queueBehindSeconds = queueBehindMillis / 1000;
        if (queueBehindSeconds >= OSIsoftConfig.WARNING_LIMIT_QUEUE_BEHIND_SECONDS) {
          Logger.LOG_WARN(
              "The historical data queue is running behind by " + queueBehindSeconds + " seconds.");
        }
      } catch (IOException e) {
        Logger.LOG_SERIOUS("Unable to detect if historical data queue is running behind.");
        Logger.LOG_EXCEPTION(e);
      } catch (TimeTrackerUnrecoverableException e) {
        Logger.LOG_SERIOUS("Time tracker file has been corrupted.");
        Logger.LOG_EXCEPTION(e);
      }

      Logger.LOG_DEBUG("Grabbed " + queuePoints.size() + " datapoints for proccessing.");
      for (int i = 0; i < queuePoints.size(); i++) {
        DataPoint data = ((DataPoint) queuePoints.get(i));

        // build a payload out of data points
        if (data != null) {
          payloadMngr.appendDataPointToPayLoad(data);
        }

        // Sleep between datapoints to significantly increase Flexy performance.
        Thread.yield();
        try {
          final int threadWaitMS = 5;
          Thread.sleep(threadWaitMS);
        } catch (InterruptedException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_SERIOUS("Exception thrown while sleeping thread.");
        }
      }
    }
  }

  /**
   * Sets the directory that the Ewon uses to check for SSL Certificates
   *
   * @param path directory path where certificate is stored
   */
  private static void setCertificatePath(String path) {

    SysControlBlock SCB;
    try {
      SCB = new SysControlBlock(SysControlBlock.SYS);
      if (!SCB.getItem("HttpCertDir").equals(path)) {
        SCB.setItem("HttpCertDir", path);
        SCB.saveBlock(true);
      }
    } catch (Exception e) {
      Logger.LOG_SERIOUS("Setting certificate directory failed");
      System.exit(0);
    }
  }

  /**
   * Sets the http timeouts Note: This changes the Ewon's global HTTP timeouts and stores these
   * values in NV memory.
   */
  private static void setHttpTimeouts(int timeoutSeconds) {
    SysControlBlock SCB;
    boolean needsSave = false;
    final String httpTimeoutSec = String.valueOf(timeoutSeconds);
    Logger.LOG_INFO("HTTP timeout set to " + httpTimeoutSec);
    try {
      SCB = new SysControlBlock(SysControlBlock.SYS);

      if (!SCB.getItem("HTTPC_SDTO").equals(httpTimeoutSec)) {
        SCB.setItem("HTTPC_SDTO", httpTimeoutSec);
        needsSave = true;
      }

      if (!SCB.getItem("HTTPC_RDTO").equals(httpTimeoutSec)) {
        SCB.setItem("HTTPC_RDTO", httpTimeoutSec);
        needsSave = true;
      }

      if (needsSave) {
        SCB.saveBlock(true);
      }

    } catch (Exception e) {
      Logger.LOG_SERIOUS("Setting timeouts failed. Application ending");
      System.exit(0);
    }
  }

  /** Set the local time zone for use in all export block descriptor calls. */
  private static void setLocalTimeZone() {
    // Inject local time in to the JVM
    try {
      SCTimeUtils.injectJvmLocalTime();

      final Date currentTime = new Date();
      final String currentLocalTime = SCTimeUtils.getIso8601LocalTimeFormat().format(currentTime);
      final String currentUtcTime = SCTimeUtils.getIso8601UtcTimeFormat().format(currentTime);
      Logger.LOG_DEBUG(
          "The local time zone is "
              + SCTimeUtils.getTimeZoneName()
              + " with an identifier of "
              + SCTimeUtils.getLocalTimeZoneDesignator()
              + ". The current local time is "
              + currentLocalTime
              + ", and the current UTC time is "
              + currentUtcTime
              + ".");
    } catch (Exception e) {
      Logger.LOG_CRITICAL("Unable to inject local time in to the JVM!");
    }
  }
}
