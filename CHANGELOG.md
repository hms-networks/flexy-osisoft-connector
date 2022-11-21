# Flexy OSIsoft Changelog

## Version 3.0.6
### Major Changes
+ Updated project structure to better match sc-java-maven-starter-project template
  + Moved CHANGELOG.md to root of repository
  + Moved images folder to root of repository
+ Updated now-deprecated PreAllocatedStringBuilder.java usage to StringBuffer.java
  + Resolved bugs related to hard-coded buffer sizes
### Minor Changes
+ Updated GitHub actions to use the latest versions from sc-java-maven-starter-project
+ Updated com.hms_networks.americas.sc:extensions library to v1.12.1
+ Cleaned up remnants of previous libraries

## Version 3.0.5
### Major Changes
+ None
### Minor Changes
+ Increased tag name length limit from 60 characters to 128 characters

## Version 3.0.4
### Major Changes
+ None
### Minor Changes
+ Added support for application argument(s) to disable auto-restart

## Version 3.0.3
### Major Changes
+ None
### Minor Changes
+ Fixed bug when no tags are added to queue
+ Fixed bug when invalid response body received

## Version 3.0.2
### Major Changes
+ None
### Minor Changes
+ Simplified URL options
+ Fixed bug in time tracker logic

## Version 3.0.1
### Major Changes
+ None
### Minor Changes
+ Fixed bug in naming scheme options

## Version 3.0.0
### Major Changes
+ Removed FTP user for local time
+ Added Naming scheme options to config file
### Minor Changes
+ Switched build tool to Maven
+ Refactored classes into new packages
+ Added LICENSE file
+ Added CONTRIBUTING.md
+ Added CODE_OF_CONDUCT.md

## Version 2.9.0
### Major Changes
+ Fixed OMF issue with successful HTTPS requests shown as failed
+ Added FTP user to set Java local time offset
### Minor Changes
+ Updated libraries
+ Refactored 'IP' to 'URL' in config file and README
+ Added default logging level

## Version 2.8.0
### Major Changes
+ None
### Minor Changes
+ Fixed malformed HTTPS requests for piwebapi calls
+ Updated libraries

## Version 2.7.0
### Major Changes
+ None
### Minor Changes
+ Fixed OMF bug in tag initialization where containers referenced old type ID
+ Made HTTPS timeout value an optional configuration parameter
+ OMF request responses are now saved separately by request type
+ Fixed malformed post request using "Post" instead of "POST"
+ Fixed OMF logical error in setting type format field

## Version 2.6.0
### Major Changes
+ None
### Minor Changes
+ Add support for proxy URL
+ Updated libraries

## Version 2.5.0
### Major Changes
+ None
### Minor Changes
+ OMF container ID's are unique across devices

## Version 2.4.0
### Major Changes
+ Fixed integer type storage bug in OSIsoft AF server
### Minor Changes
+ Specified type format for OMF instead of using default type

## Version 2.3.0
### Major Changes
+ Supported storing tags as different data types in OSIsoft with OMF
### Minor Changes
+ Improved performance during initial connection establishment
+ Adding logging for data queue status
+ Fixed tag indexing error
+ Fixed issue with webpage not saving configurations
+ Accounted for the new tag indexing that includes null points on tag gaps
+ Updated libraries

## Version 2.2.0
### Major Changes
+ Updated Solution Center libraries
### Minor Changes
+ Readme updated

## Version 2.1.0
### Major Changes
+ Added Support for OCS with OMF.
### Minor Changes
+ Connector Configuration file has new OCS specific additions

## Version 2.0.0
### Major Changes
+ Added new logic to add data points to a payload
+ Refactored mechanism to send data points to OSIsoft
+ Refactored to make use of SC data point library
+ Refactored to make use of SC file util library
+ Refactored to make use of SC historical data queue library
+ Refactored to make use of SC JSON library
+ Refactored to make use of SC logger library
+ Refactored to make use of SC string library
+ Refactored to make use of SC tag info library
### Minor Changes
+ Various adjustments to log messages.


## Version 1.0.0
### Major Changes
+ Added in support for OMF while retaining support for old versions of piwebapi
+ Communication type can be changed in json file through new config setting
### Minor Changes
+ None

## Version 0.4.0
### Major Changes
+ None
### Minor Changes
+ X-Requested-With header added to webapi calls
+ eWON Boolean tags now map to Digital types in OSIsoft

## Version 0.3.0
### Major Changes
#### Configuration Webpage
Connector configuration can now be done through a webpage hosted at **FLEXY-IP-ADDRESS**/usr/config.html

### Minor Changes
+ eWON's unique name is appended to the osisoft tag name to designate the flexy the data is generated from
+ OSIsoft point source is now "HMS" instead of "l"

## Version 0.2.0
### Major Changes
#### Offline Data Storage
Volatile offline data storage has been added.  This will buffer ~250K datapoints with their timestamp when connection to the OSIsoft server has been interrupted.  If the application gets low on memory the oldest datapoints buffered will be removed to free space in memory.  Upon reconnection the application will post current data with a higher priority than buffered data.  It may take a significant amount of time to transfer all buffered data if the device has been offline for an extended period.

#### Timestamping
Timestamps are now generated on the Flexy rather than using the OSIsoft server timestamp.

Note: Please read the new section in README.md about setting the time on the Flexy.

#### Additional Datatype Support
The datatypes "Integer", "Floating point", "Boolean", and "DWORD" are now supported.  On startup the Flexy will read the Tag's configuration and automatically determine the datatype of the tag.  PI Point's created automatically by the application will be created with the appropriate datatype.

#### Data Change Based Logging
The "PostDuplicateTagValues" has been added to the configuration.  This option controls when datapoints are logged. If set to true, datapoints will always be cyclically logged. If set to false, datapoints will only be logged on change of value.

### Minor Changes
+ Improved application logging
+ Improved logging performance when https connections fail or time out
+ Batch based datapoint posting

## Version 0.1.0
Initial Release
