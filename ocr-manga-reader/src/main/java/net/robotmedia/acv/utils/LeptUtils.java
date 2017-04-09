/*******************************************************************************
 * Copyright 2013-2016 Christopher Brochtrup
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package net.robotmedia.acv.utils;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.WriteFile;

public class LeptUtils
{
  /** Returns 0 if black and 1 if white. */
  public static int getBinaryPixelColor(Pix pixs, int x, int y)
  {
    int pixelColor = pixs.getPixel(x, y);
    
    // If black (yes, it's the opposite of what you might expect)
    if(pixelColor == 0xFFFFFFFF)
    {
      return 0;
    }
    else
    {
      return 1;
    }
  }
  
  
  /** Returns true if pixel is black */
  public static boolean isBlack(Pix pixs, int x, int y)
  {
    if(inRangeX(pixs, x) && inRangeY(pixs, y))
    {
      return (getBinaryPixelColor(pixs, x, y) == 0);
    }
    else
    {
      return false;
    }
  }

  
  /** Returns true if x-coordinate is inside of pixs */
  public static boolean inRangeX(Pix pixs, int x)
  {
    return (x >= 0 && x < pixs.getWidth());
  }
  
  
  /** Returns true if y-coordinate is inside of pixs */
  public static boolean inRangeY(Pix pixs, int y)
  {
    return (y >= 0 && y < pixs.getHeight());
  }
  
  
  /** pixs must be 1bpp. Returns average brightness of border colors. Range [0.0-1.0, where 1.0 is pure white] */ 
  public static float getAveBorderBrightness(Pix pixs)
  {
    int width = pixs.getWidth();
    int height = pixs.getHeight();
    int accum = 0;
    int numBorderPix = (2 * width) + (2 * height) - 4;
    
    // Top and bottom
    for(int x = 0; x < width; x++)
    {
      accum += LeptUtils.getBinaryPixelColor(pixs, x, 0); // Top
      accum += LeptUtils.getBinaryPixelColor(pixs, x, height - 1); // Bottom
    }
    
    // Left and right
    for(int y = 1; y < height - 1; y++)
    {
      accum += LeptUtils.getBinaryPixelColor(pixs, 0, y); // Left
      accum += LeptUtils.getBinaryPixelColor(pixs, width - 1, y); // Right
    }

    return (accum / (float)numBorderPix);
  }
  
  
  /* Clear/erase a left-to-right section of the provided binary Pix. */
  public static void pixEraseAreaLeftToRight(Pix pixs, int startX, int width)
  {
    for(int y = 0; y < pixs.getHeight(); y++)
    {
      for(int x = startX; x < startX + width; x++)
      {
        pixs.setPixel(x, y, 0xFF000000);
      }
    }
  }
  
  
  /* Clear/erase a top-to-bottom section of the provided binary Pix. */
  public static void pixEraseAreaTopToBottom(Pix pixs, int startY, int height)
  {
    for(int y = startY; y < startY + height; y++)
    {
      for(int x = 0; x < pixs.getWidth(); x++)
      {
        pixs.setPixel(x, y, 0xFF000000);
      }
    }
  }
  

  /** Search for the nearest black pixel in a spiral search pattern */
  public static Point findNearestBlackPixel(Pix pixs, int startX, int startY, int maxDist)
  {
    Point pt = new Point(startX, startY);

    for(int dist = 1; dist < maxDist; dist++)
    {
      // Check right one pixel
      pt.x++;
      
      if(LeptUtils.isBlack(pixs, pt.x, pt.y))
      {
        return pt;
      }
      
      // Check down
      for(int i = 0; i < dist * 2 - 1; i++)
      {
        pt.y++;
        
        if(LeptUtils.isBlack(pixs, pt.x, pt.y))
        {
          return pt;
        }
      }
      
      // Check left
      for(int i = 0; i < dist * 2; i++)
      {
        pt.x--;
        
        if(LeptUtils.isBlack(pixs, pt.x, pt.y))
        {
          return pt;
        }
      }
      
      // Check up
      for(int i = 0; i < dist * 2; i++)
      {
        pt.y--;
        
        if(LeptUtils.isBlack(pixs, pt.x, pt.y))
        {
          return pt;
        }
      }

      // Check right
      for(int i = 0; i < dist * 2; i++)
      {
        pt.x++;
        
        if(LeptUtils.isBlack(pixs, pt.x, pt.y))
        {
          return pt;
        }
      }
    }
    
    return new Point(-1, -1);
  }
  
  
  /** Does horizontal line contain black pixels? */
  public static boolean lineContainBlackH(Pix pixs, Point startPt, int width)
  {
    for (int x = startPt.x; x <= startPt.x + width && LeptUtils.inRangeX(pixs, x); x++)
    {
      if (LeptUtils.isBlack(pixs, x, startPt.y))
        return true;
    }

    return false;
  }


  /** Does Vertical line contain black pixels? */
  public static boolean lineContainBlackV(Pix pixs, Point startPt, int height)
  {
    for (int y = startPt.y; y <= startPt.y + height && LeptUtils.inRangeY(pixs, y); y++)
    {
      if (LeptUtils.isBlack(pixs, startPt.x, y))
        return true;
    }

    return false;
  }
  
  
  /** Return a point that represents the top-left point of the foreground clip */
  public static Point getForegroundClipOffset(Pix pixs)
  {
    Point pt = new Point(0, 0);

    // Scan Left
    for(int x = 0; x < pixs.getWidth(); x++)
    {
      if(lineContainBlackV(pixs, new Point(x, 0), pixs.getHeight()))
        break;
      else
        pt.x++;
    }
        
    // Scan Top
    for(int y = 0; y < pixs.getHeight(); y++)
    {
      if(lineContainBlackH(pixs, new Point(0, y), pixs.getWidth()))
        break;
      else
        pt.y++;
    }
    
    return pt;
  }
  
  
  /** Debug. Save pixs to file. 
      Example: "/sdcard/Temp/img/myfile.png" */
  public static void writePixToFile(Pix pixs, String filename)
  {
    // We must make a copy to avoid corrupting pixs
    Pix copyPixs = pixs.copy();
    Bitmap bmp = WriteFile.writeBitmap(copyPixs);
    FileUtils.writeBitmapToFile(bmp, filename);
    bmp.recycle();
    copyPixs.recycle();
  }
  
  
}
