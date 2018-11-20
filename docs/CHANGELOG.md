# Flexy OSIsoft Changelog

## v0.3
### Major Changes
#### Configuration Webpage
Connector configuration can now be done through a webpage hosted at **FLEXY-IP-ADDRESS**/usr/config.html

### Minor Changes
+ eWON's unique name is appended to the osisoft tag name to designate the flexy the data is generated from
+ OSIsoft point source is now "HMS" instead of "l"

## v0.2
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

## v0.1
Initial Release
