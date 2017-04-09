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

package net.robotmedia.acv.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.WriteFile;

import net.robotmedia.acv.Constants;

import java.util.List;

/** View used to draw the capture box on. */
public class OcrView extends View 
{      
  /** Used to paint the capture box. */
  private Paint captureBoxPaint = new Paint();
  
  /** Paint used for drawing bounding boxes. */
  private Paint boundingBoxPaint = new Paint();
  
  /** Capture box. */
  private Rect captureBox = null;
    
  /** Last captured screen area. Used for debug. */
  private Bitmap lastCaptureBitmap;
  
  /** Bounding boxes around the OCR'd text. */
  private List<Rect> boundingBoxes;
  
  /** Factor to scale bounding boxes for display */
  private float boundingBoxesScaleFactor = 1.0f;
  
  /** Set this flag to draw the bounding boxes. */
  private boolean showBoundingBoxes = Constants.DEFAULT_OCR_SETTINGS_SHOW_BOUNDING_BOXES;
    
  /** For debug. Set this flag to draw lastCapture in the top-left corner. */
  private boolean dbgDrawCapture = false;
  
  
  public OcrView(Context context)
  {
    super(context);
    this.init();
  }
  
  
  public OcrView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    this.init();
  }

  
  /** Initialization routine called by the constructor. */
  private void init()
  {               
    this.captureBoxPaint.setColor(Constants.DEFAULT_OCR_SETTINGS_CAPTURE_BOX_COLOR);
    
    this.boundingBoxPaint.setColor(Constants.DEFAULT_OCR_SETTINGS_BOUNDING_BOX_COLOR);
    this.boundingBoxPaint.setStyle(Style.STROKE);
    this.boundingBoxPaint.setStrokeWidth(1);
  }
  
  
  @Override
  protected void onDraw(final Canvas canvas)
  {
    if((this.captureBox != null))
    {
      this.drawCaptureBox(canvas);
      this.drawBoundingBoxes(canvas);
      this.drawLastCapture(canvas);
    }
  }

  
  /** Draw the capture box. */
  private void drawCaptureBox(final Canvas canvas)
  {
    canvas.drawRect(this.captureBox, this.captureBoxPaint);
  }
  
  
  /** Draw the bounding boxes over the capture box. */
  private void drawBoundingBoxes(final Canvas canvas)
  {
    if(this.showBoundingBoxes && (this.boundingBoxes != null))
    {
      for (int i = 0; i < this.boundingBoxes.size(); i++)
      {
        Rect boundingBox = new Rect(this.boundingBoxes.get(i));
  
        // Scale bounding box to the capture box area
        boundingBox.left = (int)(boundingBox.left / this.boundingBoxesScaleFactor) + this.captureBox.left;
        boundingBox.top = (int)(boundingBox.top / this.boundingBoxesScaleFactor) + this.captureBox.top;
        boundingBox.right = (int)(boundingBox.right / this.boundingBoxesScaleFactor) + this.captureBox.left;
        boundingBox.bottom = (int)(boundingBox.bottom / this.boundingBoxesScaleFactor) + this.captureBox.top;        
       
        canvas.drawRect(boundingBox, this.boundingBoxPaint);
      }
    }
  }
  

  /** For debug. Draw the captured bitmap to the top-left of the screen. */
  private void drawLastCapture(final Canvas canvas)
  {
    // Draw the captured bitmap
    if(this.dbgDrawCapture && (this.lastCaptureBitmap != null))
    {
      canvas.drawBitmap(this.lastCaptureBitmap, 0, 0, null);
      
      // Draw the bounding boxes if desired
      if(this.showBoundingBoxes && (this.boundingBoxes != null))
      {
        for (int i = 0; i < this.boundingBoxes.size(); i++)
        {
          Rect boundingBox = this.boundingBoxes.get(i);
          canvas.drawRect(boundingBox, this.boundingBoxPaint);
        }
      }
    }
  }
  
  
  /** Set the capture box. */
  public void setCaptureBox(Rect captureBox)
  {
    this.captureBox = captureBox;
    
    // Force a redraw
    this.invalidate();
  }
  

  /** Set the last captured area. */
  public void setLastCapture(final Pix lastCapture)
  {
    if(lastCapture != null)
    {
      if(this.dbgDrawCapture)
      {
        this.lastCaptureBitmap = WriteFile.writeBitmap(lastCapture);
      }
    }
  }
  
  
  /** Set the bounding boxes around the OCR'd text. */
  public void setBoundingBoxes(List<Rect> boundingBoxes, float scaleFactor)
  {
    this.boundingBoxes = boundingBoxes;
    this.boundingBoxesScaleFactor = scaleFactor;
  }
  
  
  /** Set the capture box color. */
  public void setCaptureBoxColor(int color)
  {
    if(this.captureBoxPaint != null)
    {
      this.captureBoxPaint.setColor(color);
    }
  }
  
  
  /** Set the bounding box color. */
  public void setBoundingBoxColor(int color)
  {
    if(this.boundingBoxPaint != null)
    {
      this.boundingBoxPaint.setColor(color);
    }
  }
  
  
  /** Set the visibility of the bounding boxes. */
  public void setBoundingBoxVisi(boolean isVisi)
  {
    this.showBoundingBoxes = isVisi;
  }
  
  
  
}
