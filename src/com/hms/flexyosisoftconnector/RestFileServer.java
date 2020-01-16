package com.hms.flexyosisoftconnector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import javax.microedition.io.StreamConnection;
import org.microemu.cldc.socket.*;

/**
 * RestFileServer class
 *
 * Class object for a RestFileServer. Listens on a socket for a file, if
 * received the file is saved.
 *
 * HMS Networks Inc. Solution Center
 *
 */

public class RestFileServer extends Thread {

   private static final int PORT_NUM = 22333;
   private static final String FILENAME = "usr/ConnectorConfig.json";

   public void run() {
      listen();
   }

   //Writes a string to a new file
   public void writeFile(String data) {
      Logger.LOG_DEBUG(data);
      try {
         BufferedWriter writer = new BufferedWriter(new FileWriter(FILENAME));
         writer.write(data);
         writer.close();
      } catch (IOException e) {
         Logger.LOG_EXCEPTION(e);
      }
   }

   //Listen for a POST on the configured port, trigger saving, and respond
   public void listen() {
      try {
         ServerSocketConnection svr = new ServerSocketConnection(PORT_NUM);

         //Server should always be listening for new files
         while (true) {
            StreamConnection client = svr.acceptAndOpen();
            Logger.LOG_DEBUG("Received new JSON file via webpage");
            InputStream inputStream = client.openInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            //Read the post payload data
            String headers = "";
            String payload = "";
            boolean headerReceived = false;

            //Read in the received file
            while (bufferedReader.ready()) {

               //Read the header information
               //Header has been fully received when an empty line is read
               if(!headerReceived)
               {
                  String readLine = bufferedReader.readLine();

                  //New empty line received, end of header information
                  if (readLine.length() == 0)
                  {
                     headerReceived = true;
                  }
                  //Header information received, append to headers.
                  else
                  {
                     headers += readLine;
                  }
               }
               else
               {
                  //Read the payload
                  //This must be done one char at a time due to buffered reader's
                  //ready() function only indicates if a char can be read, not a whole line
                  payload += (char)bufferedReader.read();
               }
            }
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();

            //Save the payload to the file
            writeFile(payload);

            //create a PrintWriter to post the http response
            PrintWriter outWriter = new PrintWriter(client.openOutputStream());

            //Hardcoded response header
            String responseHeaders = "HTTP/1.1 200\r\nContent-Type:  text/plain\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Request-Headers: content-type\r\nConnection: close\r\n";
            outWriter.println(responseHeaders);
            Logger.LOG_DEBUG("Response to HTTP POST sent");

            //Close writer
            outWriter.close();

            //Close stream
            client.close();

         }
      } catch (Exception e) {
         Logger.LOG_EXCEPTION(e);
      }
   }
}
