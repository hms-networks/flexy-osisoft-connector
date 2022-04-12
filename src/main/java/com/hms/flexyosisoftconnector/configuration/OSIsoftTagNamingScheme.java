package com.hms.flexyosisoftconnector.configuration;

import com.hms_networks.americas.sc.extensions.logging.Logger;
import com.hms_networks.americas.sc.extensions.string.StringUtils;
import java.util.List;

/**
 * This class contains the tag naming scheme parsing for the tag name on OSIsoft to be set by the
 * connector.
 *
 * @author HMS Networks, MU Americas Solution Center
 */
public class OSIsoftTagNamingScheme {

  /** The configuration value associated with the Ewon Flexy serial number option. */
  public static final String serialNumberOptionName = "sn";

  /** The configuration value associated with the Ewon Flexy tag type option. */
  public static final String tagTypeOptionName = "tt";

  /** The configuration value associated with the Ewon Flexy tag name option. */
  public static final String tagNameOptionName = "tn";

  /** The configuration value associated with the default option. */
  public static final String defaultOptionName = "default";

  /**
   * The default value assigned when the configuration file is set to a default naming scheme.
   * OSIsoft tag names would consist of the following: (Ewon tag name)-(Ewon serial number)-(Ewon
   * tag type).
   */
  public static final String defaultOptionValue = "tnsntt";

  /** The deliminator used in the configuration file to separate options. */
  public static final String optionNameDeliminator = "-";

  /** The parsed shorthand name option selected in the configuration file. */
  private static String shortHandOSIsoftNameOptionSelected = "";

  /** Option name delimiter error message. */
  private static String optionNameDeliminatorErrorMessage =
      "Please separate your configuration option selection with a " + optionNameDeliminator + ".";

  /**
   * Boolean representing if the OSIsoft tag name scheme is valid or not due to the presence of the
   * tag name component within the name. *
   */
  private static boolean hasTagName = false;

  /** Help string shown when an invalid tag name scheme is detected. */
  private static final String tagOptionsHelpNotice =
      "The valid tag name options are "
          + tagNameOptionName
          + " for tag name, "
          + serialNumberOptionName
          + " for serial number, and "
          + tagTypeOptionName
          + "for tag type."
          + optionNameDeliminatorErrorMessage;

  /**
   * Function that parses the configuration file's OSIsoft tag name scheme options.
   *
   * @param chosenNameTagNameFormat This can be set to default, which will name the tags with the
   *     default tag name scheme. It can also specify a combination of tag name, tag type, and
   *     serial number in any order as long as tag name is included.
   * @return The parsed and valid tag name scheme.
   */
  public static String tagNameFormatOptionParser(String chosenNameTagNameFormat) {
    chosenNameTagNameFormat = chosenNameTagNameFormat.trim();
    String parsedOSIsoftTagNameFormat = "";

    if (chosenNameTagNameFormat.compareToIgnoreCase(defaultOptionName) == 0) {
      // Default option has been chosen
      parsedOSIsoftTagNameFormat = defaultOptionValue;
    } else {
      // Check options are valid
      if (chosenNameTagNameFormat.compareToIgnoreCase(tagNameOptionName) == 0) {
        // Tag name will be used without appending any additional information
        parsedOSIsoftTagNameFormat = tagNameOptionName;
      } else {
        // Check dash separators are present
        if (chosenNameTagNameFormat.indexOf('-') >= 0) {
          List optionsList = StringUtils.split(chosenNameTagNameFormat, "-");

          final int minListSize = 1;
          final int maxListSize = 3;

          // Check tag name plus at least one of the 3 options are present
          if (optionsList.size() > minListSize && optionsList.size() <= maxListSize) {
            for (int i = 0; i < optionsList.size(); i++) {
              parsedOSIsoftTagNameFormat += checkNameOptionIsValid((String) optionsList.get(i));
            }
            if (!hasTagName) {
              parsedOSIsoftTagNameFormat = defaultOptionName;
              Logger.LOG_SERIOUS(
                  "No OSIsoft tag name option was used in the selected OSIsoft tag name scheme."
                      + " Using default tag name scheme.");
              Logger.LOG_INFO(tagOptionsHelpNotice);
            }
          } else {
            // Wrong amount of options was supplied
            parsedOSIsoftTagNameFormat = defaultOptionValue;
            Logger.LOG_INFO(tagOptionsHelpNotice);
          }
        } else {
          // No delimiter present in passed in configuration options
          parsedOSIsoftTagNameFormat = defaultOptionValue;
          Logger.LOG_WARN(
              "No delimiter found. Unable to parse OSIsoft tag name configuration options.");
          Logger.LOG_INFO(optionNameDeliminatorErrorMessage);
        }
      }
    }

    // Store the valid tag name scheme with no dashes for faster parsing
    shortHandOSIsoftNameOptionSelected = parsedOSIsoftTagNameFormat;
    // Return valid options list with no dashes
    return parsedOSIsoftTagNameFormat;
  }

  /**
   * Extracts an OSIsoft tag name from the stored tag name scheme.
   *
   * @param tagName Name of the tag to insert into the OSIsoft tag name
   * @param tagType Type of the tag to insert into the OSIsoft tag name
   * @return the OSIsoft tag name for the current Flexy tag
   */
  public static String extractTagNameFromComponents(String tagName, String tagType) {
    String parsedTagName = "";
    final int lengthOfSingleOption = 2;

    if (shortHandOSIsoftNameOptionSelected.length() >= lengthOfSingleOption) {
      // loop through every option present
      for (int index = 0;
          index < shortHandOSIsoftNameOptionSelected.length();
          index += lengthOfSingleOption) {

        // add a optionNameDeliminator between name components in the OSIsoft tag name
        if(index > 0){
          parsedTagName += optionNameDeliminator;
        }
        String shortHandOption = shortHandOSIsoftNameOptionSelected.substring(index, index + 2);
        if (shortHandOption.compareToIgnoreCase(serialNumberOptionName) == 0) {
          parsedTagName += OSIsoftConfig.getFlexySerial();
        } else if (shortHandOption.compareToIgnoreCase(tagTypeOptionName) == 0) {
          parsedTagName += tagType;
        } else if (shortHandOption.compareToIgnoreCase(tagNameOptionName) == 0) {
          parsedTagName += tagName;
        } else {
          Logger.LOG_WARN(
              "Invalid option was given. Unable to parse tag name configuration options.");
          Logger.LOG_INFO(tagOptionsHelpNotice);
        }
      }
    } else {
      Logger.LOG_SERIOUS("Invalid number of tag name scheme options given.");
      Logger.LOG_INFO(tagOptionsHelpNotice);
    }

    return parsedTagName;
  }

  /**
   * Takes in a single name option and checks if it is one of the offered options.
   *
   * @param option the name format option
   */
  private static String checkNameOptionIsValid(String option) {
    String selectedOption = "";
    if (option.compareToIgnoreCase(serialNumberOptionName) == 0) {
      selectedOption = serialNumberOptionName;
    } else if (option.compareToIgnoreCase(tagTypeOptionName) == 0) {
      selectedOption = tagTypeOptionName;
    } else if (option.compareToIgnoreCase(tagNameOptionName) == 0) {
      selectedOption = tagNameOptionName;
      hasTagName = true;
    } else {
      Logger.LOG_WARN("Invalid option was given. Unable to parse tag name configuration options.");
      Logger.LOG_INFO(tagOptionsHelpNotice);
    }
    return selectedOption;
  }
}
