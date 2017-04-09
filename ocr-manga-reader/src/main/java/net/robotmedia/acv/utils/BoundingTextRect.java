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

import android.graphics.Point;
import android.graphics.Rect;

import com.googlecode.leptonica.android.Pix;

import net.robotmedia.acv.utils.DirDist.D8;

import java.util.ArrayList;
import java.util.List;

public class BoundingTextRect
{

  /** Expand rect in specified direction if that direction contains a black pixel.
      Otherwise, don't expand and return false. */
  private static boolean tryExpandRect(Pix pixs, Rect rect, D8 dir, int dist)
  {    
    if (dir == D8.Top)
    {
      if (LeptUtils.lineContainBlackH(pixs, new Point(rect.left, rect.top - dist), rect.width()))
      {
        rect.top -= dist;
        return true;
      }
    }
    else if (dir == D8.TopRight)
    {
      if (LeptUtils.isBlack(pixs, rect.right + dist, rect.top - dist))
      {
        rect.top -= dist;
        rect.right += dist;
        return true;
      }
    }
    else if (dir == D8.Right)
    {
      if (LeptUtils.lineContainBlackV(pixs, new Point(rect.right + dist, rect.top), rect.height()))
      {
        rect.right += dist;
        return true;
      }
    }
    else if (dir == D8.BottomRight)
    {
      if (LeptUtils.isBlack(pixs, rect.right + dist, rect.bottom + dist))
      {
        rect.bottom += dist;
        rect.right += dist;
        return true;
      }
    }
    else if (dir == D8.Bottom)
    {
      if (LeptUtils.lineContainBlackH(pixs, new Point(rect.left, rect.bottom + dist), rect.width()))
      {
        rect.bottom += dist;
        return true;
      }
    }
    else if (dir == D8.BottomLeft)
    {
      if (LeptUtils.isBlack(pixs, rect.left - dist, rect.bottom + dist))
      {
        rect.left -= dist;
        rect.bottom += dist;
        return true;
      }
    }
    else if (dir == D8.Left)
    {
      if (LeptUtils.lineContainBlackV(pixs, new Point(rect.left - dist, rect.top), rect.height()))
      {
        rect.left -= dist;
        return true;
      }
    }
    else if (dir == D8.TopLeft)
    {
      if (LeptUtils.isBlack(pixs, rect.left - dist, rect.top + dist))
      {
        rect.top -= dist;
        rect.left -= dist;
        return true;
      }
    }

    return false;
  }
  
  
  /** Expand rect using provided list of directions and distances. */
  private static void expandRect(Pix pixs, List<DirDist> dirDistList, Rect bRect, boolean keepGoing)
  {
    int i = 0;

    while (true)
    {
      DirDist dirDist = dirDistList.get(i);

      // Try to expand rect in current direction
      boolean hasBlack = tryExpandRect(pixs, bRect, dirDist.dir, dirDist.dist);

      // If could not expand (ie no black pixel found in current direction)
      if (!hasBlack)
      {
        i++;
      }
      // If caller wants to exit upon first successful expansion
      else if (!keepGoing)
      {
        return;
      }

      // If we went through the entire list, return
      if (i >= dirDistList.size())
      {
        return;
      }
    }
  }
  
  
  /** Get bounding rectangle for vertical or horizontal text. */
  public static Rect getBoundingRect(Pix pixs, int startX, int startY, boolean vertical, int lookahead)
  {
    Rect bRect = new Rect(startX, startY, startX, startY);
    Rect bRectLast = new Rect(startX, startY, startX, startY);

    List<DirDist> listD4 = new ArrayList<DirDist>();

    if (vertical)
    {
      listD4.add(new DirDist(D8.Top, 1));
      listD4.add(new DirDist(D8.Right, 1));
      listD4.add(new DirDist(D8.Left, 1));

      for (int i = 1; i < lookahead + 1; i++)
      {
        listD4.add(new DirDist(D8.Bottom, i));
      }
    }
    else
    {
      listD4.add(new DirDist(D8.Top, 1));
      listD4.add(new DirDist(D8.Left, 1));
      listD4.add(new DirDist(D8.Bottom, 1));

      for (int i = 1; i < lookahead + 1; i++)
      {
        listD4.add(new DirDist(D8.Right, i));
      }
    }

    List<DirDist> listCorners = new ArrayList<DirDist>();
    listCorners.add(new DirDist(D8.TopRight, 1));
    listCorners.add(new DirDist(D8.BottomRight, 1));
    listCorners.add(new DirDist(D8.BottomLeft, 1));
    listCorners.add(new DirDist(D8.TopLeft, 1));
    
    // Try a few iterations to form the best rect
    for (int i = 0; i < 10; i++)
    {
      expandRect(pixs, listD4, bRect, true);
      expandRect(pixs, listCorners, bRect, false);

      // No change this iteration, no need to continue
      if (bRect.left == bRectLast.left
          && bRect.right == bRectLast.right
          && bRect.top == bRectLast.top
          && bRect.bottom == bRectLast.bottom)
      {
        break;
      }

      bRectLast = new Rect(bRect);
    }

    return bRect;
  }
  
  
}
