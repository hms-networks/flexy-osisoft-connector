package com.hms.flexyosisoftconnector;

import com.ewon.ewonitf.SysControlBlock;
import com.hms.flexyosisoftconnector.configuration.OSIsoftConfig;
import com.hms.flexyosisoftconnector.dataserver.DataPoster;
import com.hms.flexyosisoftconnector.dataserver.OSIsoftServer;
import com.hms.flexyosisoftconnector.payloadhandler.PayloadManager;
import com.hms_networks.americas.sc.extensions.datapoint.DataPoint;
import com.hms_networks.americas.sc.extensions.historicaldata.CircularizedFileException;
import com.hms_networks.americas.sc.extensions.historicaldata.CorruptedTimeTrackerException;
import com.hms_networks.americas.sc.extensions.historicaldata.EbdTimeoutException;
import com.hms_networks.americas.sc.extensions.historicaldata.HistoricalDataQueueManager;
import com.hms_networks.americas.sc.extensions.historicaldata.TimeTrackerUnrecoverableException;
import com.hms_networks.americas.sc.extensions.json.JSONException;
import com.hms_networks.americas.sc.extensions.logging.Logger;
import com.hms_networks.americas.sc.extensions.system.application.SCAppArgsParser;
import com.hms_networks.americas.sc.extensions.system.application.SCAppManagement;
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

  /** The minimum memory (in bytes) required to perform a poll of the data queue. */
  public static final int QUEUE_DATA_POLL_MIN_MEMORY_BYTES = 5000000;

  /** Boolean flag indicating if the application is running out of memory */
  private static boolean isMemoryCurrentlyLow;

  /** PI Server management object */
  static OSIsoftServer piServer;

  /** Constructs payloads out of data points and holds them for sending. */
  static PayloadManager payloadMngr;

  /** Track current number of consecutive times the payload queue is full. */
  static long payloadQueueFullCount = 0;

  /** Threshold for debug warning for payload queue is full. */
  static long payloadQueueFullWarnThresh = 1;

  /** Thread sleep time for delay when payload queue is full. */
  static final int FULL_QUEUE_DELAY_MS = 500;

  public static void main(String[] args) throws IOException, JSONException {
    // Indicate the version number and that the application is starting
    Logger.LOG_CRITICAL(
        OSIsoftConfig.CONNECTOR_NAME + " v" + OSIsoftConfig.CONNECTOR_VERSION + " starting");

    OSIsoftConfig.initConfig(CONNECTOR_CONFIG_FILENAME);

    // Parse arguments
    SCAppArgsParser argsParser = new SCAppArgsParser(args);

    // Configure auto-restart if not started by multi-loader
    if (!argsParser.getStartedByMultiLoader()) {
      if (OSIsoftConfig.getAutoRestart()) {
        SCAppManagement.enableAppAutoRestart();
      } else {
        SCAppManagement.disableAppAutoRestart();
      }
    } else {
      Logger.LOG_DEBUG(
          OSIsoftConfig.CONNECTOR_NAME
              + " was started by the multi-loader application. Skipping auto-restart "
              + "configuration.");
    }

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

    if (TagInfoManager.getTagInfoList().size() == 0) {
      Logger.LOG_SERIOUS(
          "There are currently no tags configured to be monitored for OSIsoft telemetry data.");
      Logger.LOG_SERIOUS(
          "Add 1 or more tags to telemetry data and restart the connector. Connector application"
              + " exiting.");
      System.exit(1);
    }

    payloadMngr = new PayloadManager(OSIsoftConfig.getCommunicationType());

    piServer = new OSIsoftServer();

    Logger.LOG_INFO("Initializing tags");
    int res = piServer.initTags();
    while (res != SCHttpUtility.HTTPX_CODE_NO_ERROR) {
      // Sleep between initialization attempts to increase Flexy performance.
      Thread.yield();
      try {
        final int threadWaitMS = 2000;
        Thread.sleep(threadWaitMS);
      } catch (InterruptedException e) {
        Logger.LOG_EXCEPTION(e);
        Logger.LOG_SERIOUS("Exception thrown while sleeping thread.");
      }

      Logger.LOG_INFO("Retrying initializing tags");
      res = piServer.initTags();
    }

    Logger.LOG_INFO("Finished initializing tags");

    DataPoster dataThread = new DataPoster(payloadMngr);
    dataThread.start();

    while (true) {
      getNewPayloadToSend();

      // pause to help the flexy with the infinite loop
      Thread.yield();
      try {
        final int threadWaitMS = OSIsoftConfig.getDataPollRateMs();
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
    // Update available memory variable
    long availableMemoryBytes = Runtime.getRuntime().freeMemory();

    // Check if memory is within permissible range to poll data queue
    if (availableMemoryBytes < QUEUE_DATA_POLL_MIN_MEMORY_BYTES) {
      // Show low memory warning
      Logger.LOG_WARN(
          "Low memory on device, "
              + (availableMemoryBytes / 1000)
              + " MB left! Data polling paused to prevent out of memory error.");

      // If low memory flag not set, set it and request garbage collection
      if (!isMemoryCurrentlyLow) {
        // Set low memory flag
        isMemoryCurrentlyLow = true;

        // Tell the JVM that it should garbage collect soon
        System.gc();
      }
    } else {
      // There is enough memory to run, reset memory state variable.
      if (isMemoryCurrentlyLow) {
        isMemoryCurrentlyLow = false;
      }

      // If the payload queue is not full, read from historical data
      if (!payloadMngr.isQueueFull()) {

        payloadQueueFullCount = 0;
        payloadQueueFullWarnThresh = 1;

        // Process datapoints from queue
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
        } catch (IOException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN(
              "Exception thrown when obtaining new batch of datapoints. The queue will retry.");
        } catch (CircularizedFileException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("Data in the historical log has been overwritten with newer data.");
        } catch (EbdTimeoutException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("EBD call has timed out while fetching data.");
        } catch (JSONException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("Invalid JSON received while fetching data.");
        } catch (TimeTrackerUnrecoverableException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("Time tracker file is not recoverable.");
        } catch (CorruptedTimeTrackerException e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("Time tracker file has been corrupted.");
        } catch (Exception e) {
          Logger.LOG_EXCEPTION(e);
          Logger.LOG_WARN("There was an unknown error collecting data points!");
        }

        // if there are data points to send
        if (queuePoints.size() != 0) {
          // Check if queue is behind
          try {
            long queueBehindMillis = HistoricalDataQueueManager.getQueueTimeBehindMillis();
            if (queueBehindMillis >= OSIsoftConfig.WARNING_LIMIT_QUEUE_BEHIND_MILLISECONDS) {
              Logger.LOG_WARN(
                  "The historical data queue is running behind by "
                      + SCTimeUtils.getDayHourMinSecsForMillis(queueBehindMillis));
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
      } else {
        payloadQueueFullCount += 1;

        if (payloadQueueFullCount >= payloadQueueFullWarnThresh) {
          payloadQueueFullCount += 0;
          Logger.LOG_DEBUG("Reading historical data skipped due to full payload queue.");

          // after warning increase the threshold, but avoid roll over
          if (payloadQueueFullWarnThresh < Long.MAX_VALUE / 100) {
            payloadQueueFullWarnThresh *= 10;
          }
        }
        // This thread should yield and give time for publisher thread to decrease queue size
        Thread.yield();
        try {
          Thread.sleep(FULL_QUEUE_DELAY_MS);
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
