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

import com.googlecode.leptonica.android.Pix;

import java.util.ArrayList;
import java.util.List;

public class Furigana
{

  final private static int FURIGANA_MIN_FG_PIX_PER_LINE = 2;
  final private static int FURIGANA_MIN_WIDTH = 5;
  
    
  /* Erase the furigana from the provided binary PIX. Works by finding spans of foreground
     text and removing the spans that are too narrow and are likely furigana.
     Use this version for vertical text. */
  public static void eraseFuriganaVerticalText(Pix pixs, float scaleFactor)
  {
    final int NO_VALUE = -1;
    int minFgPixPerLine = (int)(FURIGANA_MIN_FG_PIX_PER_LINE * scaleFactor);
    int minSpanWidth = (int)Math.ceil(FURIGANA_MIN_WIDTH * scaleFactor);
    int x = 0;
    int numFgPixelsOnLine = 0;
    boolean goodLine = false;
    int numGoodLinesInCurSpan = 0;
    int totalGoodLines = 0;
    int pixelValue = 0;
    FuriganaSpan span = new FuriganaSpan(NO_VALUE, NO_VALUE);
    List<FuriganaSpan> spanList = new ArrayList<FuriganaSpan>();
    int aveSpanWidth = 0;
    
    /* Get list of spans that contain fg pixels */
    for(x = 0; x < pixs.getWidth(); x++)
    {
      numFgPixelsOnLine = 0;
      goodLine = false;
      
      for(int y = 0; y < pixs.getHeight(); y++)
      {
        pixelValue = LeptUtils.getBinaryPixelColor(pixs, x, y);
        
        /* If this is a foreground pixel */
        if(pixelValue == 0)
        {
          numFgPixelsOnLine++;

          /* If this line has already meet the minimum number of fg pixels, stop scanning it */
          if(numFgPixelsOnLine >= minFgPixPerLine)
          {
            goodLine = true;
            break;
          }
        }
      }
      
      /* If last line is good, set it bad in order to close the span */
      if (goodLine && (x == pixs.getWidth() - 1))
      {
        goodLine = false;
        numGoodLinesInCurSpan++;
      }
      
      /* If this line has the minimum number of fg pixels */
      if(goodLine)
      {
        /* Start a new span */
        if(span.start == NO_VALUE)
        {
          span.start = x;
        }

        numGoodLinesInCurSpan++;
      }
      else /* Line doesn't have enough fg pixels to consider as part of a span */
      {
        /* If a span has already been started, then end it */
        if(span.start != NO_VALUE)
        {
          /* If this span isn't too small (needed so that the average isn't skewed) */
          if(numGoodLinesInCurSpan >= minSpanWidth)
          {
            span.end = x;

            totalGoodLines += numGoodLinesInCurSpan;

            /* Add span to the list */
            spanList.add(new FuriganaSpan(span.start, span.end));
          }
        }
        
        /* Reset span */
        span.start = NO_VALUE;
        span.end = NO_VALUE;
        numGoodLinesInCurSpan = 0;
      }
    }
    
    if(spanList.size() == 0)
    {
      return;
    }
    
    /* Get average width of the spans */
    aveSpanWidth = totalGoodLines / spanList.size();
    
    x = 0;
    
    /* Erase areas of the PIX where either no span exists or where a span is too narrow */
    for(int spanIdx = 0; spanIdx < spanList.size(); spanIdx++)
    {
      span = spanList.get(spanIdx);
      
      /* If span is at least of average width, erase area between the previous span and this span */
      if((span.end - span.start + 1) >= (int)(aveSpanWidth * 0.90))
      {
        LeptUtils.pixEraseAreaLeftToRight(pixs, x, span.start - x);
        
        x = span.end + 1;
      }
    }
    
    /* Clear area between the end of the right-most span and the right edge of the PIX */
    if((x != 0) && (x < (pixs.getWidth() - 1)))
    {
      LeptUtils.pixEraseAreaLeftToRight(pixs, x, pixs.getWidth() - x);
    }
    
    return;
  }
  
  
  /* Erase the furigana from the provided binary PIX. Works by finding spans of foreground
     text and removing the spans that are too narrow and are likely furigana.
     Use this version for horizontal text. */
  public static void eraseFuriganaHorizontalText(Pix pixs, float scaleFactor)
  {
    final int NO_VALUE = -1;
    int minFgPixPerLine = (int) (FURIGANA_MIN_FG_PIX_PER_LINE * scaleFactor);
    int minSpanWidth = (int) Math.ceil(FURIGANA_MIN_WIDTH * scaleFactor);
    int y = 0;
    int numFgPixelsOnLine = 0;
    boolean goodLine = false;
    int numGoodLinesInCurSpan = 0;
    int totalGoodLines = 0;
    int pixelValue = 0;
    FuriganaSpan span = new FuriganaSpan(NO_VALUE, NO_VALUE);
    List<FuriganaSpan> spanList = new ArrayList<FuriganaSpan>();
    int aveSpanWidth = 0;

    /* Get list of spans that contain fg pixels */
    for (y = 0; y < pixs.getHeight(); y++)
    {
      numFgPixelsOnLine = 0;
      goodLine = false;

      for (int x = 0; x < pixs.getWidth(); x++)
      {
        pixelValue = LeptUtils.getBinaryPixelColor(pixs, x, y);

        /* If this is a foreground pixel */
        if (pixelValue == 0)
        {
          numFgPixelsOnLine++;

          /* If this line has already meet the minimum number of fg pixels, stop scanning it */
          if (numFgPixelsOnLine >= minFgPixPerLine)
          {
            goodLine = true;
            break;
          }
        }
      }

      /* If last line is good, set it bad in order to close the span */
      if (goodLine && (y == pixs.getHeight() - 1))
      {
        goodLine = false;
        numGoodLinesInCurSpan++;
      }

      /* If this line has the minimum number of fg pixels */
      if (goodLine)
      {
        /* Start a new span */
        if (span.start == NO_VALUE)
        {
          span.start = y;
        }

        numGoodLinesInCurSpan++;
      }
      else
      /* Line doesn't have enough fg pixels to consider as part of a span */
      {
        /* If a span has already been started, then end it */
        if (span.start != NO_VALUE)
        {
          /* If this span isn't too small (needed so that the average isn't skewed) */
          if (numGoodLinesInCurSpan >= minSpanWidth)
          {
            span.end = y;

            totalGoodLines += numGoodLinesInCurSpan;

            /* Add span to the list */
            spanList.add(new FuriganaSpan(span.start, span.end));
          }
        }

        /* Reset span */
        span.start = NO_VALUE;
        span.end = NO_VALUE;
        numGoodLinesInCurSpan = 0;
      }
    }

    if (spanList.size() == 0)
    {
      return;
    }

    /* Get average width of the spans */
    aveSpanWidth = totalGoodLines / spanList.size();

    y = 0;

    /* Erase areas of the PIX where either no span exists or where a span is too narrow */
    for (int spanIdx = 0; spanIdx < spanList.size(); spanIdx++)
    {
      span = spanList.get(spanIdx);

      /* If span is at least of average width, erase area between the previous span and this span */
      if ((span.end - span.start + 1) >= (int) (aveSpanWidth * 0.90))
      {
        LeptUtils.pixEraseAreaTopToBottom(pixs, y, span.start - y);

        y = span.end + 1;
      }
    }

    /* Clear area between the end of the right-most span and the right edge of the PIX */
    if ((y != 0) && (y < (pixs.getHeight() - 1)))
    {
      LeptUtils.pixEraseAreaTopToBottom(pixs, y, pixs.getHeight() - y);
    }

    return;
  }
  
  
}
