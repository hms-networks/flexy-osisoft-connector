package com.hms.flexyosisoftconnector;

import java.util.ArrayList;

public class DataPoster extends Thread{

   private static final int maxDataPointsPerPost = 200;
   private boolean shouldRun;

   public void run()
   {
      shouldRun = true;

      while(shouldRun)
      {

         OSIsoftServer.startBatch();
         ArrayList dataPoints = new ArrayList();

         int pointsPerTag = (maxDataPointsPerPost/OSIsoftConfig.tags.size());
         int pointsAdded = 0;
         //Build the JSON payload containing the most recent data points
         for (int tagIndex = 0; tagIndex < OSIsoftConfig.tags.size(); tagIndex++) {
            dataPoints.add(((Tag) OSIsoftConfig.tags.get(tagIndex)).getNewestDataPoints(pointsPerTag));

            for(int dataPointIndex = 0; dataPointIndex<((ArrayList) dataPoints.get(tagIndex)).size(); dataPointIndex++)
            {
               if(((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex) != null)
               {
                  OSIsoftServer.addPointToBatch(((Tag) OSIsoftConfig.tags.get(tagIndex)),(DataPoint)((ArrayList)dataPoints.get(tagIndex)).get(dataPointIndex));
                  pointsAdded++;
               }
               else Logger.LOG_ERR("Null data point encountered");
            }

         }

         OSIsoftServer.endBatch();

         if(pointsAdded>0)
         {
            Logger.LOG_DEBUG("Sending " + pointsAdded + " points to server");
            //Post the tags to the server
            boolean retval = OSIsoftServer.postBatch();

            if(retval)
            {
               //Remove all the points that were posted
               for (int tagIndex = 0; tagIndex < OSIsoftConfig.tags.size(); tagIndex++)
               {
                  ((Tag) OSIsoftConfig.tags.get(tagIndex)).removeDataPoints((ArrayList)dataPoints.get(tagIndex));
               }
            }
         }

         //Tell the JVM that it should garbage collect soon
         System.gc();
      }
   }
}
