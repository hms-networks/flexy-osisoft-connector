package com.hms.flexyosisoftconnector;

import java.util.ArrayList;

public class DataPoster extends Thread{

   private int maxDataPointsPerPost;
   private static final int MAX_DATA_POINTS_PER_POST_API = 200;
   private static final int MAX_DATA_POINTS_PER_POST_OMF = 5000;
   private boolean shouldRun;
   private static final String comsErrMsg = "Internal Error DataPoster.java: communication type is not valid";
   private int communicationType;

   public DataPoster (int com) {
      communicationType = com;
      switch(communicationType) {
         case OSIsoftConfig.omf:
            Logger.LOG_INFO("Using connection settings for piwebapi 2019 and later");
            maxDataPointsPerPost = MAX_DATA_POINTS_PER_POST_OMF;
            break;
         case OSIsoftConfig.piwebapi:
            Logger.LOG_INFO("Using connection settings for piwebapi 2018 and earlier");
            maxDataPointsPerPost = MAX_DATA_POINTS_PER_POST_API;
            break;
         default:
            Logger.LOG_ERR(comsErrMsg);
            break;
       }
   }

   public void run()
   {
      shouldRun = true;

      long lastUpdateTimeMs = 0;

      while(shouldRun)
      {
          long currentTimeMs = System.currentTimeMillis();

          /*
           *  The queue is more efficient if it send out after a large amount of data has been added
           *  With this logic of waiting 6 seconds per check, the internal queues of each tag has time
           *  to build up before we look at those internal to send out all the data in them.
           */
          final int dataSendRateSeconds = 6;
          final int convertToMilliseconds = 1000;
          int waitTime = dataSendRateSeconds * convertToMilliseconds;
          // If the elapsed time since the last check is greater than waitTime seconds, run all of the data poster logic
          if ((currentTimeMs - lastUpdateTimeMs) >= waitTime) {
          // Update the last update time
          lastUpdateTimeMs = currentTimeMs;
          switch(communicationType) {
             case OSIsoftConfig.omf:
                 // start building OMF data message body
                 OSIsoftServer.startOMFDataMessage();
                 break;
             case OSIsoftConfig.piwebapi:
                 OSIsoftServer.startBatch();
                 break;
             default:
                 Logger.LOG_ERR(comsErrMsg);
                 break;
          }

         ArrayList dataPoints = new ArrayList();

         int pointsPerTag = (maxDataPointsPerPost/OSIsoftConfig.tags.size());
         int pointsAdded = 0;
         //Build the JSON payload containing the most recent data points
         for (int tagIndex = 0; tagIndex < OSIsoftConfig.tags.size(); tagIndex++) {
            dataPoints.add(((Tag) OSIsoftConfig.tags.get(tagIndex)).getNewestDataPoints(pointsPerTag));

            String tagName = ((Tag) OSIsoftConfig.tags.get(tagIndex)).getTagName();

            switch(communicationType) {
               case OSIsoftConfig.omf:
                   if (tagIndex > 0) {
                      OSIsoftServer.separateDataMessage();
                   }

                   OSIsoftServer.addContainerStartToOMFDataMessage(tagName);
                   break;
               case OSIsoftConfig.piwebapi:
                   // do nothing
                   break;
               default:
                   Logger.LOG_ERR(comsErrMsg);
                   break;
            }

            for(int dataPointIndex = 0; dataPointIndex<((ArrayList) dataPoints.get(tagIndex)).size(); dataPointIndex++)
            {
               if(((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex) != null)
               {
                  switch(communicationType) {
                     case OSIsoftConfig.omf:
                         String tagValue = ((DataPoint)((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex)).getValueString();
                         String timeStamp = ((DataPoint)((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex)).timestamp;

                         // if there is more than one data point we need to comma separate them
                         if (dataPointIndex > 0) {
                             OSIsoftServer.separateDataMessage();
                         }

                          // add a data point to the OMF message body
                          OSIsoftServer.addPointToOMFDataMessage(tagValue, timeStamp);
                          break;
                      case OSIsoftConfig.piwebapi:
                          OSIsoftServer.addPointToBatch(((Tag) OSIsoftConfig.tags.get(tagIndex)),(DataPoint)((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex));

                          break;
                      default:
                          Logger.LOG_ERR(comsErrMsg);
                          break;
                   }

                  pointsAdded++;
               }
               else Logger.LOG_ERR("Null data point encountered");
            }
            switch(communicationType) {
               case OSIsoftConfig.omf:
                   OSIsoftServer.addContainerEndToOMFDataMessage();
                   break;
               case OSIsoftConfig.piwebapi:
                   OSIsoftServer.startBatch();
                   break;
               default:
                   Logger.LOG_ERR(comsErrMsg);
                   break;
            }
         }

         switch(communicationType) {
            case OSIsoftConfig.omf:
                OSIsoftServer.endOMFDataMessage();
                break;
            case OSIsoftConfig.piwebapi:
                OSIsoftServer.endBatch();
                break;
            default:
                Logger.LOG_ERR(comsErrMsg);
                break;
         }


         if(pointsAdded>0)
         {
            Logger.LOG_DEBUG("Sending " + pointsAdded + " points to server");
            //Post the tags to the server
            boolean retval = false;

            switch(communicationType) {
               case OSIsoftConfig.omf:
                   retval = OSIsoftServer.postOMFBatch();
                   break;
               case OSIsoftConfig.piwebapi:
                   retval = OSIsoftServer.postBatch();
                   break;
               default:
                   Logger.LOG_ERR(comsErrMsg);
                   break;
            }

            if(retval)
            {
               //Remove all the points that were posted
               for (int tagIndex = 0; tagIndex < OSIsoftConfig.tags.size(); tagIndex++)
               {
                   // For every tag, even if there are 0 points to remove, go into removal function
                   ((Tag) OSIsoftConfig.tags.get(tagIndex)).removeDataPoints((ArrayList)dataPoints.get(tagIndex));
               }
            }
         }

         //Tell the JVM that it should garbage collect soon
         System.gc();
         }
      }
   }
}
