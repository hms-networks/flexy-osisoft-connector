package com.hms.flexyosisoftconnector;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/**
 * FileReader class
 * 
 * Converts file to String form
 * 
 * HMS Industrial Networks Inc. Solution Center
 * 
 * @author thk
 *
 */

public class FileReader {
   
   // Reads a file and returns it as a string
   public static String readFile(String path) {

      FileConnection fconn = null;
      InputStream is       = null;
      int bufferSize       = 10000;

      try {
         fconn = (FileConnection) Connector.open(path);
         // If no exception is thrown, then the URI is valid, but the file may or may not
         // exist.

         is = fconn.openInputStream();
         int Read;
         byte[] Buffer = new byte[bufferSize];

         Read = is.read(Buffer);
         fconn.close();
         return new String(Buffer, 0, Read);

      } catch (IOException ioe) {
         System.out.println("Error: " + ioe.toString());
         //Returns an empty string in error case
         return "";
      }
   }
}
