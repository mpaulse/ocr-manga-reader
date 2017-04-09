/*******************************************************************************
 * Copyright 2009 Robot Media SL
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
import android.webkit.WebView;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.HashSet;

@SuppressWarnings("deprecation")
public class ComicFrame extends FrameLayout
{
  private SuperImageView mImage;
  private HashSet<WebView> mContentViews = new HashSet<WebView>();
  private AbsoluteLayout mContentContainer;


  private void init(Context context)
  {
    // FIXME: Do this programatically
    final int defStyle = context.getResources().getIdentifier("scrollViewStyle", "attr", context.getPackageName());
    mImage = new SuperImageView(context, null, defStyle);
    mImage.setScaleType(ImageView.ScaleType.CENTER);
    mImage.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    this.addView(mImage);

    mContentContainer = new AbsoluteLayout(context);
    mContentContainer.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
    this.addView(mContentContainer);
  }


  public void removeContent()
  {
    for (WebView w : mContentViews)
    {
      mContentContainer.removeView(w);
    }
    
    mContentViews.clear();
  }


  public ComicFrame(Context context)
  {
    super(context);
    init(context);
  }


  public SuperImageView getImage()
  {
    return mImage;
  }
}
