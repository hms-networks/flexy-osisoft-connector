package com.hms.flexyosisoftconnector;

import java.io.IOException;
import java.util.ArrayList;

import com.ewon.ewonitf.SysControlBlock;
import com.hms_networks.americas.sc.datapoint.DataPoint;
import com.hms_networks.americas.sc.historicaldata.HistoricalDataQueueManager;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.logging.Logger;
import com.hms_networks.americas.sc.taginfo.TagInfoManager;

/**
 * Ewon Flexy java demo for OSIsoft Server
 *
 * <p>This demo reads multiple tag values from an Ewon Flexy IO Server and POSTs them to an OSIsoft
 * PI Server.
 *
 * <p>HMS Networks Inc. Solution Center
 */
public class Main {

  /** Application Major Version Number */
  static final int MAJOR_VERSION = 2;

  /** Application Minor Version Number */
  static final int MINOR_VERSION = 0;

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
    Logger.LOG_CRITICAL("OSIsoft Connector v" + MAJOR_VERSION + "." + MINOR_VERSION + " starting");

    // Start the webserver to accept json file
    RestFileServer restServer = new RestFileServer();
    restServer.start();

    OSIsoftConfig.initConfig(CONNECTOR_CONFIG_FILENAME);

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
    setHttpTimeouts();

    // update tag info to use later
    TagInfoManager.refreshTagList();

    payloadMngr = new PayloadManager(OSIsoftConfig.getCommunicationType());

    piServer = new OSIsoftServer();

    int res;
    do {
      Logger.LOG_INFO("Initializing tags");
      res = piServer.initTags();

    } while (res != OSIsoftServer.NO_ERROR);

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
    }

    // if there are data points to send
    if (queuePoints.size() != 0) {
      for (int i = 0; i < queuePoints.size(); i++) {
        DataPoint data = ((DataPoint) queuePoints.get(i));

        // build a payload out of data points
        payloadMngr.appendDataPointToPayLoad(data);
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
  private static void setHttpTimeouts() {
    SysControlBlock SCB;
    boolean needsSave = false;
    final String HTTPS_TIMEOUT_S = "2";
    try {
      SCB = new SysControlBlock(SysControlBlock.SYS);

      if (!SCB.getItem("HTTPC_SDTO").equals(HTTPS_TIMEOUT_S)) {
        SCB.setItem("HTTPC_SDTO", HTTPS_TIMEOUT_S);
        needsSave = true;
      }

      if (!SCB.getItem("HTTPC_RDTO").equals(HTTPS_TIMEOUT_S)) {
        SCB.setItem("HTTPC_RDTO", HTTPS_TIMEOUT_S);
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
}
