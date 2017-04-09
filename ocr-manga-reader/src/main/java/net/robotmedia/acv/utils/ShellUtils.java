package net.robotmedia.acv.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ShellUtils
{
  
  /** Call chmod */
  public static void chmod(String file, String permissions)
  {    
    callExe(new String[] { "/system/bin/chmod", permissions, file });
  }
  
  
  /** Call the provided exe and get it's stdout. */
  public static String callExe(String[] exeArgs)
  {
    String outText = "";
    
    try
    {
      Process process = Runtime.getRuntime().exec(exeArgs);

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      int read;
      char[] buffer = new char[60000];
      StringBuffer output = new StringBuffer();
      
      while ((read = reader.read(buffer)) > 0)
      {
        output.append(buffer, 0, read);
      }
      
      reader.close();

      process.waitFor();
      
      outText = output.toString();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
    catch (InterruptedException e)
    {
      throw new RuntimeException(e);
    }
    
    return outText;
  }
}
