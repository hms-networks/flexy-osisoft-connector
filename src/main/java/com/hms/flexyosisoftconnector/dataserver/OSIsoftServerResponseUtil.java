package com.hms.flexyosisoftconnector.dataserver;

import com.hms.flexyosisoftconnector.configuration.OSIsoftConfig;
import com.hms_networks.americas.sc.extensions.json.JSONArray;
import com.hms_networks.americas.sc.extensions.json.JSONObject;
import com.hms_networks.americas.sc.extensions.logging.Logger;

/**
 * Utility class for parsing OSIsoft server responses and checking the contents.
 *
 * @author HMS Networks, MU Americas Solution Center
 * @since 2.0.10
 */
public class OSIsoftServerResponseUtil {

  /** The key used to retrieve the "Message" field from an OSIsoft server response. */
  private static final String MESSAGE_FIELD_KEY = "Message";

  /** The key used to retrieve the "Errors" field from an OSIsoft server response. */
  private static final String ERRORS_FIELD_KEY = "Errors";

  /** The key used to retrieve the "Messages" field from an OSIsoft server response. */
  private static final String MESSAGES_FIELD_KEY = "Messages";

  /**
   * The key used to retrieve the "MessageIndex" field from a message in an OSIsoft server response
   * "Messages" field.
   */
  private static final String MESSAGE_MESSAGE_INDEX_FIELD_KEY = "MessageIndex";

  /**
   * The key used to retrieve the "Events" field from a message in an OSIsoft server response
   * "Messages" field.
   */
  private static final String MESSAGE_EVENTS_FIELD_KEY = "Events";

  /**
   * The key used to retrieve the "Status" field from a message in an OSIsoft server response
   * "Messages" field.
   */
  private static final String MESSAGE_STATUS_FIELD_KEY = "Status";

  /**
   * The key used to retrieve the "Code" field from a message in an OSIsoft server response
   * "Messages" field's inner "Status" field.
   */
  private static final String MESSAGE_STATUS_CODE_FIELD_KEY = "Code";

  /**
   * The key used to retrieve the "HighestSeverity" field from a message in an OSIsoft server
   * response "Messages" field's inner "Status" field.
   */
  private static final String MESSAGE_STATUS_HIGHEST_SEVERITY_FIELD_KEY = "HighestSeverity";

  /**
   * The key uses to retrieve the "EventInfo" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_FIELD_KEY = "EventInfo";

  /**
   * The key used to retrieve the "Message" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_MESSAGE_FIELD_KEY = "Message";

  /**
   * The key used to retrieve the "Reason" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_REASON_FIELD_KEY = "Reason";

  /**
   * The key used to retrieve the "Suggestions" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_SUGGESTIONS_FIELD_KEY = "Suggestions";

  /**
   * The key used to retrieve the "EventCode" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_EVENT_CODE_FIELD_KEY = "EventCode";

  /**
   * The key used to retrieve the "Parameters" field from an event in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_PARAMETERS_FIELD_KEY = "Parameters";

  /**
   * The key used to retrieve the "Name" field from a parameter in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field's inner "Parameters" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_PARAMETER_NAME_FIELD_KEY = "Name";

  /**
   * The key used to retrieve the "Value" field from a parameter in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "EventInfo" field's inner "Parameters" field.
   */
  private static final String MESSAGE_EVENT_EVENT_INFO_PARAMETER_VALUE_FIELD_KEY = "Value";

  /**
   * The key used to retrieve the "ExceptionInfo" field from a parameter in an OSIsoft server
   * response "Messages" field's inner "Events" field's inner "Parameters" field.
   */
  private static final String MESSAGE_EVENT_EXCEPTION_INFO_FIELD_KEY = "ExceptionInfo";

  /**
   * The key used to retrieve the "Severity" field from a parameter in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "Parameters" field.
   */
  private static final String MESSAGE_EVENT_SEVERITY_FIELD_KEY = "Severity";

  /**
   * The key used to retrieve the "InnerEvents" field from a parameter in an OSIsoft server response
   * "Messages" field's inner "Events" field's inner "Parameters" field.
   */
  private static final String MESSAGE_EVENT_INNER_EVENTS_FIELD_KEY = "InnerEvents";

  /** The severity value used to indicate a warning message from the OSIsoft server. */
  private static final String SEVERITY_WARNING_KEY = "Warning";

  /** The severity value used to indicate an error message from the OSIsoft server. */
  private static final String SEVERITY_ERROR_KEY = "Error";

  /**
   * Checks an HTTPS response for messages from the OSIsoft server.
   *
   * @param response The response from the OSIsoft server to check for messages
   * @param connectionUrl The URL of the OSIsoft server request that was made
   * @param responseFileName The name of the file which the response was saved to
   * @return the highest severity type of message received from the OSIsoft server
   */
  public static boolean checkForMessages(
      JSONObject response, String connectionUrl, String responseFileName) {
    // Create boolean to track message success/failure and whether a message was found
    boolean messageSuccessful = true;
    boolean foundMessage = false;

    // Add try/catch for JSON exceptions when retrieving fields
    try {
      // Check for single "Message" field (usually indicates authentication failure)
      if (response.has(MESSAGE_FIELD_KEY)) {
        foundMessage = true;
        messageSuccessful = false;
        handleMessage(response, connectionUrl);
      }

      // Check for "Errors" field
      if (response.has(ERRORS_FIELD_KEY)) {
        foundMessage = true;
        messageSuccessful = false;
        handleErrors(response, connectionUrl);
      }

      // Check for "Messages" field
      if (response.has(MESSAGES_FIELD_KEY)) {
        foundMessage = true;
        messageSuccessful = handleMessages(response, connectionUrl, responseFileName);
      }
    } catch (Exception e) {
      messageSuccessful = false;
      Logger.LOG_SERIOUS(
          "An error occurred while parsing the response for request: " + connectionUrl);
      Logger.LOG_EXCEPTION(e);
      if (foundMessage) {
        Logger.LOG_SERIOUS(
            "Message(s) such as a log, warning, or error, were received from the server, "
                + "but the response is invalid.");
      }
    }

    // Return boolean indicating message success (or failure)
    return messageSuccessful;
  }

  /**
   * Handles a single message in an HTTPS response from the OSIsoft server.
   *
   * @param response The response from the OSIsoft server to handle
   * @param connectionUrl The URL of the OSIsoft server request that was made
   * @throws Exception if an error occurs while parsing the response
   */
  public static void handleMessage(JSONObject response, String connectionUrl) throws Exception {
    String message = response.getString(MESSAGE_FIELD_KEY);
    Logger.LOG_SERIOUS(
        "The following response message was received for request "
            + connectionUrl
            + ": "
            + message);
    Logger.LOG_SERIOUS(
        "Check that the configured credentials/authentication information is correct.");
  }

  /**
   * Handles an errors array in an HTTPS response from the OSIsoft server.
   *
   * @param response The response from the OSIsoft server to handle
   * @param connectionUrl The URL of the OSIsoft server request that was made
   * @throws Exception if an error occurs while parsing the response
   */
  public static void handleErrors(JSONObject response, String connectionUrl) throws Exception {
    // Loop through each error and log it
    JSONArray errors = response.getJSONArray(ERRORS_FIELD_KEY);
    for (int i = 0; i < errors.length(); i++) {
      String error = errors.getString(i);
      Logger.LOG_SERIOUS(
          "The following response error message was received for request "
              + connectionUrl
              + ": "
              + error);

      // Check for Web ID error
      if (error.startsWith(OSIsoftServer.WEB_ID_ERROR_STRING)) {
        Logger.LOG_SERIOUS("WEB ID: \"" + OSIsoftConfig.getServerWebID() + "\"");
        Logger.LOG_SERIOUS("WEB ID Error: Supplied Web ID does not exist on this server");
      }
    }
  }

  /**
   * Handles a messages array in an HTTPS response from the OSIsoft server.
   *
   * @param response The response from the OSIsoft server to handle
   * @param connectionUrl The URL of the OSIsoft server request that was made
   * @param responseFileName The name of the file which the response was saved to
   * @return boolean indicating if no messages with error severity level were found. If no
   *     message(s) with error severity level were found, then true is returned.
   * @throws Exception if an error occurs while parsing the response
   */
  public static boolean handleMessages(
      JSONObject response, String connectionUrl, String responseFileName) throws Exception {
    // Create boolean to track if an error severity message was found
    boolean foundMessageWithErrorSeverity = false;

    // Loop through each message and log it
    JSONArray messages = response.getJSONArray(MESSAGES_FIELD_KEY);
    for (int messagesLoopIndex = 0; messagesLoopIndex < messages.length(); messagesLoopIndex++) {
      JSONObject message = messages.getJSONObject(messagesLoopIndex);

      // Get message index
      int messageIndex = message.getInt(MESSAGE_MESSAGE_INDEX_FIELD_KEY);

      // Get events
      JSONArray events = message.getJSONArray(MESSAGE_EVENTS_FIELD_KEY);

      // Get status code and highest severity then log message
      JSONObject status = message.getJSONObject(MESSAGE_STATUS_FIELD_KEY);
      int statusCode = status.getInt(MESSAGE_STATUS_CODE_FIELD_KEY);
      String highestSeverity = status.getString(MESSAGE_STATUS_HIGHEST_SEVERITY_FIELD_KEY);
      logWithHighestSeverity(
          highestSeverity,
          "The request to "
              + connectionUrl
              + " returned a message (index "
              + messageIndex
              + ") with "
              + events.length()
              + " event(s), a status code of "
              + statusCode
              + " and the highest severity of "
              + highestSeverity
              + ".");
      if (highestSeverity.equalsIgnoreCase(SEVERITY_ERROR_KEY)) {
        foundMessageWithErrorSeverity = true;
      }

      // Loop through each event and log it
      logWithHighestSeverity(
          highestSeverity,
          "Message index "
              + messageIndex
              + " for response from request to "
              + connectionUrl
              + " contained the following event(s): ");
      for (int eventsLoopIndex = 0; eventsLoopIndex < events.length(); eventsLoopIndex++) {
        JSONObject event = events.getJSONObject(eventsLoopIndex);
        int eventNumber = eventsLoopIndex + 1;

        // Get severity
        String severity = event.getString(MESSAGE_EVENT_SEVERITY_FIELD_KEY);

        // Get event info object
        JSONObject eventInfo = event.getJSONObject(MESSAGE_EVENT_EVENT_INFO_FIELD_KEY);

        // Get event info code
        int eventInfoCode = eventInfo.getInt(MESSAGE_EVENT_EVENT_INFO_EVENT_CODE_FIELD_KEY);

        // Get event info message
        String eventInfoMessage = eventInfo.getString(MESSAGE_EVENT_EVENT_INFO_MESSAGE_FIELD_KEY);
        logWithHighestSeverity(
            highestSeverity,
            "  Event "
                + eventNumber
                + " (Code: "
                + eventInfoCode
                + ", Severity: "
                + severity
                + ") Message: "
                + eventInfoMessage);

        // Get event info reason
        String eventInfoReason = eventInfo.getString(MESSAGE_EVENT_EVENT_INFO_REASON_FIELD_KEY);
        logWithHighestSeverity(
            highestSeverity,
            "  Event "
                + eventNumber
                + " (Code: "
                + eventInfoCode
                + ", Severity: "
                + severity
                + ") Reason: "
                + eventInfoReason);

        // Get event info suggestions
        JSONArray eventInfoSuggestions =
            eventInfo.getJSONArray(MESSAGE_EVENT_EVENT_INFO_SUGGESTIONS_FIELD_KEY);
        for (int eventInfoSuggestionsLoopIndex = 0;
            eventInfoSuggestionsLoopIndex < eventInfoSuggestions.length();
            eventInfoSuggestionsLoopIndex++) {
          int suggestionNumber = eventInfoSuggestionsLoopIndex + 1;
          logWithHighestSeverity(
              highestSeverity,
              "  Event "
                  + eventNumber
                  + " (Code: "
                  + eventInfoCode
                  + ", Severity: "
                  + severity
                  + ") Suggestion #"
                  + suggestionNumber
                  + ": "
                  + eventInfoSuggestions.getString(eventInfoSuggestionsLoopIndex));
        }

        // Get event info parameters
        JSONArray eventInfoParameters =
            eventInfo.getJSONArray(MESSAGE_EVENT_EVENT_INFO_PARAMETERS_FIELD_KEY);
        for (int eventInfoParametersLoopIndex = 0;
            eventInfoParametersLoopIndex < eventInfoParameters.length();
            eventInfoParametersLoopIndex++) {
          JSONObject parameter = eventInfoParameters.getJSONObject(eventInfoParametersLoopIndex);
          int parameterNumber = eventInfoParametersLoopIndex + 1;
          logWithHighestSeverity(
              highestSeverity,
              "  Event "
                  + eventNumber
                  + " (Code: "
                  + eventInfoCode
                  + ", Severity: "
                  + severity
                  + ") Parameter #"
                  + parameterNumber
                  + " Name: "
                  + parameter.getString(MESSAGE_EVENT_EVENT_INFO_PARAMETER_NAME_FIELD_KEY));
          logWithHighestSeverity(
              highestSeverity,
              "  Event "
                  + eventNumber
                  + " (Code: "
                  + eventInfoCode
                  + ", Severity: "
                  + severity
                  + ") Parameter #"
                  + parameterNumber
                  + " Value: "
                  + parameter.getString(MESSAGE_EVENT_EVENT_INFO_PARAMETER_VALUE_FIELD_KEY));
        }

        // Get exception info
        String exceptionInfo = event.getString(MESSAGE_EVENT_EXCEPTION_INFO_FIELD_KEY);
        if (exceptionInfo != null) {
          logWithHighestSeverity(
              highestSeverity,
              "  Event "
                  + eventNumber
                  + " (Code: "
                  + eventInfoCode
                  + ", Severity: "
                  + severity
                  + ") Exception Info: "
                  + exceptionInfo);
        }

        // Get inner events
        JSONArray innerEvents = event.getJSONArray(MESSAGE_EVENT_INNER_EVENTS_FIELD_KEY);
        int innerEventsCount = innerEvents.length();
        if (innerEventsCount > 0) {
          logWithHighestSeverity(
              highestSeverity,
              "  Event "
                  + eventNumber
                  + " (Code: "
                  + eventInfoCode
                  + ", Severity: "
                  + severity
                  + ") contained "
                  + innerEventsCount
                  + " inner events. These events can be found in the raw response file at "
                  + responseFileName
                  + ".");
        }
      }
    }

    // Return boolean indicating if no message with error severity level was found
    return !foundMessageWithErrorSeverity;
  }

  /**
   * Utility method to log a message to the appropriate level corresponding with the highest
   * severity.
   *
   * @param highestSeverity The highest severity of the message
   * @param message The message to log
   */
  private static void logWithHighestSeverity(String highestSeverity, String message) {
    if (highestSeverity.equalsIgnoreCase(SEVERITY_WARNING_KEY)) {
      Logger.LOG_WARN(message);
    } else if (highestSeverity.equalsIgnoreCase(SEVERITY_ERROR_KEY)) {
      Logger.LOG_CRITICAL(message);
    } else {
      Logger.LOG_INFO(message);
    }
  }
}
