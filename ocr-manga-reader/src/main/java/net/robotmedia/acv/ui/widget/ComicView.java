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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.Constants;
import net.robotmedia.acv.comic.Comic;
import net.robotmedia.acv.logic.PreferencesController;

import java.io.File;

public class ComicView extends RelativeLayout implements OnCompletionListener, OnErrorListener
{

  protected void initializeWithResources(Context context)
  {
    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.comic_view, this);

    animationFadeIn = R.anim.fade_in;
    animationFadeOut = R.anim.fade_out;
    animationKeep = R.anim.keep;
    animationPushDownIn = R.anim.push_down_in;
    animationPushDownOut = R.anim.push_down_out;
    animationPushLeftIn = R.anim.push_left_in;
    animationPushLeftOut = R.anim.push_left_out;
    animationPushRightIn = R.anim.push_right_in;
    animationPushRightOut = R.anim.push_right_out;
    animationPushUpIn = R.anim.push_up_in;
    animationPushUpOut = R.anim.push_up_out;
    mSwitcher = (ImageSwitcher) findViewById(R.id.switcher);
    stringScreenProgressMessage = R.string.dialog_page_progress_text;
    stringScreenProgressTitle = R.string.dialog_page_progress_title;
    stringUnavailableText = R.string.dialog_unavailable_text;
    stringUnavailableTitle = R.string.dialog_unavailable_title;
  }


  protected boolean isLeftToRight()
  {
    return new PreferencesController(getContext()).isLeftToRight();
  }

  private class SwitchImageTask extends AsyncTask<Object, Object, Drawable>
  {
    private boolean forward;
    private boolean sequential;

    private ProgressDialog progressDialog;
    
    final Runnable mNotifyLoadScreenRunning = new Runnable()
    {
      public void run()
      {
        if (progressDialogRequired && !mDestroyed)
        {
          progressDialog = new ProgressDialog(getContext());
          progressDialog.setTitle(stringScreenProgressTitle);
          progressDialog.setIcon(android.R.drawable.ic_menu_info_details);
          progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
          
          if (SwitchImageTask.this.isImageSwitch())
          {
            progressDialog.setMessage(getContext().getString(stringScreenProgressMessage).replace("@number",
                String.valueOf(imageIndex + 1)));
          }
          else
          {
            // TODO: Localize text
            progressDialog.setMessage("Loading...");
          }
          
          progressDialog.show();
        }
      }
    };

    private final static int IMAGE_SWITCH = -1;

    private int imageIndex;
    private int frameIndex = IMAGE_SWITCH; // Screen change by default

    boolean progressDialogRequired;


    private boolean isImageSwitch()
    {
      return frameIndex == IMAGE_SWITCH;
    }


    private void postScreenChangedActions()
    {
      showScreenNumber();

      boolean stopAnimation = true;

      if (stopAnimation)
      {
        stopAnimating();
      }

      if (mListener != null)
      {
        mListener.onScreenChanged(position);
      }

      comicWasJustSet = false;
    }


    @Override
    protected Drawable doInBackground(Object... params)
    {
      System.gc();
      
      try
      {
        if (twoPageLayout)
        {
          if (forward)
          {
            return comic.getDualScreen(imageIndex + 1, imageIndex);
          }
          else
          {
            return comic.getDualScreen(imageIndex, imageIndex - 1);
          }
        }
        else
        {
          return comic.getScreen(imageIndex);
        }
      }
      catch (OutOfMemoryError e)
      {
        e.printStackTrace();
        return null;
      }
    }


    @Override
    protected void onPostExecute(Drawable result)
    {
      if (result != null && result instanceof BitmapDrawable)
      {
        Log.d("Image width", String.valueOf(((BitmapDrawable) result).getBitmap().getWidth()));
        Log.d("Image height", String.valueOf(((BitmapDrawable) result).getBitmap().getHeight()));
      }
      
      loadingPage = false;
      progressDialogRequired = false;
      
      if (progressDialog != null)
      {
        progressDialog.dismiss();
      }
      
      // if (!viewer.destroyed) {
      if (result != null)
      {

        previousPosition = position;
        position = imageIndex;
        framePosition = -1;

        final ComicFrame current = (ComicFrame) mSwitcher.getNextView();
        current.getImage().setImageDrawable(result);
        mSwitcher.showNext();
        nextImageUp = !nextImageUp; // HACK

        Integer backgroundColor = comic.getBackgroundColor(position);
        
        if (backgroundColor == null)
        {
          backgroundColor = Color.BLACK;
        }

        setBackgroundColor(backgroundColor);
        mSwitcher.setBackgroundColor(backgroundColor);
        // TODO: Check if it's necessary to set both background colors
        scale(current.getImage());

        Animation animation = mSwitcher.getInAnimation();
        
        if (animation == null)
        {
          animation = mSwitcher.getOutAnimation();
        };

        if (animation != null)
        {
          animation.setAnimationListener(new AnimationListener()
          {
            @Override
            public void onAnimationEnd(Animation arg0)
            {
              if (SwitchImageTask.this.isImageSwitch())
              {
                postScreenChangedActions();
              }
            }

            @Override
            public void onAnimationRepeat(Animation arg0)
            {
            }

            @Override
            public void onAnimationStart(Animation arg0)
            {
              startAnimating();
            }
          });
        }
        else
        {
          postScreenChangedActions();
        }

        if (isImageSwitch())
        {
          int cachePosition;
          
          if (previousPosition == position + 1)
          {
            // The next screen is already prepared, so we attempt to prepare the previous screen.
            cachePosition = position - 1;
          }
          else
          {
            cachePosition = position + 1;
          }
          
          if (preload)
          {
            mPrepareScreenTask = new PrepareScreenTask(comic);
            mPrepareScreenTask.execute(cachePosition);
          }
        }
      }
      else
      {
        if (mListener != null)
        {
          mListener.onScreenLoadFailed();
        }
      }
    }


    /**
     * Scales the current image based on (in order of priority): the comic scale mode, the zoom of
     * the previous image or the scale mode preference.
     * 
     * @param current
     */
    private void scale(SuperImageView current)
    {
      String scaleMode = comic.getScaleMode();

      if (scaleMode == null)
      {
        scaleMode = preferences.getString(Constants.SCALE_MODE_KEY, Constants.SCALE_MODE_WIDTH_VALUE);
      }

      current.scale(scaleMode, false);
    }


    @Override
    protected void onPreExecute()
    {
      System.gc();
      loadingPage = true;
      progressDialogRequired = true;
      ComicView.this.postDelayed(mNotifyLoadScreenRunning, 1000);
      final ComicFrame next = (ComicFrame) mSwitcher.getNextView();

      if (next != null)
      {
        next.getImage().recycleBitmap();
      }

      if (lowMemoryTransitions)
      {
        final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
        
        if (current != null)
        {
          if (position != imageIndex)
          { // No need to recycle the bitmap if the image is the same.
            current.getImage().recycleBitmap();
          }
        }
      }
    }

  }


  public void setPreload(boolean preload)
  {
    this.preload = preload;
  }


  public void setLowMemoryTransitions(boolean lowMemoryTransitions)
  {
    this.lowMemoryTransitions = lowMemoryTransitions;
  }

  private boolean preload = true;
  private boolean lowMemoryTransitions = false;

  private class PrepareScreenTask extends AsyncTask<Integer, Object, Object>
  {
    private Comic comic;


    public PrepareScreenTask(Comic comic)
    {
      this.comic = comic;
    }


    @Override
    protected Object doInBackground(Integer... params)
    {
      try
      {
        comic.prepareScreen(params[0]);
      }
      catch (OutOfMemoryError e)
      {
        e.printStackTrace();
      }
      
      return null;
    }

  }

  private PrepareScreenTask mPrepareScreenTask;
  private SwitchImageTask mImageSwitchTask;

  private Comic comic;
  private int framePosition;
  private boolean isBottomMost;
  private boolean isLeftMost;
  private boolean isRightMost;
  private boolean isTopMost;
  private boolean loadingPage = false;
  private boolean mAnimating = false;
  private boolean mDestroyed = false;
  private ComicViewListener mListener;
  private boolean twoPageLayout = false;
  private boolean moveForwardAfterVideo;
  private boolean nextImageUp; // HACK
  private int position;
  private SharedPreferences preferences;
  private int previousPosition;
  private Toast toast = null;
  protected int animationFadeIn;
  protected int animationFadeOut;
  protected int animationKeep;
  protected int animationPushDownIn;
  protected int animationPushDownOut;
  protected int animationPushLeftIn;
  protected int animationPushLeftOut;
  protected int animationPushRightIn;
  protected int animationPushRightOut;
  protected int animationPushUpIn;
  protected int animationPushUpOut;
  protected ViewSwitcher mSwitcher;
  protected int stringScreenProgressMessage;
  protected int stringScreenProgressTitle;
  protected int stringUnavailableText;
  protected int stringUnavailableTitle;
  boolean comicWasJustSet = false;


  public ComicView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    nextImageUp = true;
    initializeWithResources(context);

    preferences = PreferenceManager.getDefaultSharedPreferences(context);

    mSwitcher.setFactory(new ViewSwitcher.ViewFactory()
    {

      public View makeView()
      {
        final ComicFrame view = new ComicFrame(getContext());
        view.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        // SuperImageView i = new SuperImageView(getContext(), null, attributeScrollViewStyle);
        // i.setScaleType(ImageView.ScaleType.CENTER);
        // FIXME: Why does the following show the image vertically
        // centered? See onLoadPageSuccess for workaround.
        // i.setLayoutParams(new
        // FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        // ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
      }

    });
  }


  public boolean actualSize()
  {
    return fit(Constants.SCALE_MODE_NONE_VALUE);
  }


  public void destroy()
  {
    this.recycleBitmaps();
    mDestroyed = true;
    
    if (mImageSwitchTask != null)
    {
      mImageSwitchTask.cancel(true);
      mImageSwitchTask = null;
    }
    
    if (mPrepareScreenTask != null)
    {
      mPrepareScreenTask.cancel(true);
      mPrepareScreenTask = null;
    }
  }


  public boolean fitHeight()
  {
    return fit(Constants.SCALE_MODE_HEIGHT_VALUE);
  }


  public boolean fitScreen()
  {
    return fit(Constants.SCALE_MODE_BEST_VALUE);
  }


  public boolean fitWidth()
  {
    return fit(Constants.SCALE_MODE_WIDTH_VALUE);
  }


  public int getFrameIndex()
  {
    return framePosition;
  }


  public int getIndex()
  {
    return position;
  }


  public boolean goToScreen(int index)
  {
    if (!isAnimating())
    {
      if (index < 0 || index >= comic.getLength())
      {
        index = 0;
      }
      
      moveForwardAfterVideo = true;
      setPosition(index, index > position, false);
      return true;
    }
    
    return false;
  }


  public boolean isBottomMost()
  {
    return isBottomMost;
  }


  public boolean isLeftMost()
  {
    return isLeftMost;
  }


  public boolean isLoading()
  {
    return loadingPage;
  }


  public boolean isMaxZoom()
  {
    float max_zoom = Float.parseFloat(preferences.getString("max_zoom",
        Float.toString(Constants.DEFAULT_MAX_ZOOM_FACTOR)));
    final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();

    return current.getImage().getZoomFactor() >= max_zoom;
  }


  public float getZoomFactor()
  {
    final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
    return current.getImage().getZoomFactor();
  }


  public ComicFrame getComicFrame()
  {
    return (ComicFrame) mSwitcher.getCurrentView();
  }


  public boolean isRightMost()
  {
    return isRightMost;
  }


  public boolean isTopMost()
  {
    return isTopMost;
  }


  public boolean goToCurrent()
  {
    if (!isAnimating())
    {
      setPosition(position, true, false);
      return true;
    }
    else
    {
      return false;
    }
  }


  /** Shows the next screen. */
  public boolean next()
  {
    if (!isAnimating())
    {
      return nextScreen();
    }

    return false;
  }


  public boolean nextScreen()
  {
    if (!isAnimating())
    {
      return forceNextScreen();
    }

    return false;
  }


  public void onCompletion(MediaPlayer mp)
  {
    mSwitcher.setVisibility(View.VISIBLE);

    if (moveForwardAfterVideo)
    {
      forceNextScreen();
    }
    else
    {
      forcePreviousScreen();
    }
  }


  public boolean onError(MediaPlayer mp, int what, int extra)
  {
    stopAnimating();
    mSwitcher.setVisibility(View.VISIBLE);
    return true;
  }


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    super.onKeyDown(keyCode, event);
    ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
    current.getImage().abortScrollerAnimation();
    return false;
  }


  @Override
  public boolean onTouchEvent(MotionEvent motionEvent)
  {
    super.onTouchEvent(motionEvent);
    
    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN)
    {
      ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (current != null)
      {
        isLeftMost = current.getImage().isLeftMost();
        isRightMost = current.getImage().isRightMost();
        isTopMost = current.getImage().isTopMost();
        isBottomMost = current.getImage().isBottomMost();
      }
    }
    
    // TODO: Abort scroller animation here?
    return false;
  }


  /** Shows the previous screen. */
  public boolean previous()
  {
    if (!isAnimating())
    {
      return previousScreen();
    }

    return false;
  }


  public boolean previousScreen()
  {
    if (!isAnimating())
    {
      return this.forcePreviousScreen();
    }
    
    return false;
  }


  public void recycleBitmaps()
  {
    final ComicFrame next = (ComicFrame) mSwitcher.getNextView();
    
    if (next != null)
    {
      next.getImage().recycleBitmap();
    }
    
    final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
    
    if (current != null)
    {
      current.getImage().recycleBitmap();
    }
  }


  public boolean scroll(int distanceX, int distanceY)
  {
    if (!isAnimating())
    {
      ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      current.removeContent();
      current.getImage().safeScrollBy(distanceX, distanceY);
      return true;
    }
    
    return false;

  }


  public void scroll(MotionEvent event)
  {
    if (!isAnimating())
    {
      ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (!current.getImage().isSmallerThanRootView())
      {
        current.removeContent();
        current.getImage().smoothScroll(event);
      }
    }
  }


  public void setComic(Comic comic)
  {
    this.comic = comic;
    position = -1; // TODO: Why -1?
    framePosition = 0;
    comicWasJustSet = true;
  }


  public void setListener(ComicViewListener listener)
  {
    this.mListener = listener;
  }


  public void setTwoPageLayout(boolean value)
  {
    twoPageLayout = value;
  }


  public boolean getTwoPageLayout()
  {
    return twoPageLayout;
  }


  public void toggleTwoPageLayout()
  {
    twoPageLayout = !twoPageLayout;
  }


  public Point toImagePoint(Point viewPoint)
  {
    final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
    return current.getImage().toImagePoint(viewPoint);
  }


  public Rect getOriginalSize()
  {
    final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
    return current.getImage().getOriginalSize();
  }


  // TODO: Combine in a single zoom public method
  public boolean zoom(int increment, Point viewPoint)
  {
    if (!isAnimating())
    {
      final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (current != null)
      {
        current.getImage().zoom(increment, viewPoint);
        current.removeContent();
        return true;
      }
    }
    
    return false;
  }


  public boolean zoom(float factor, Point imagePoint)
  {
    if (!isAnimating())
    {
      final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (current != null)
      {
        current.removeContent();
        current.getImage().zoom(factor, imagePoint);
        return true;
      }
    }
    
    return false;
  }


  private boolean fit(String scaleMode)
  {
    if (!isAnimating())
    {
      final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (current != null)
      {
        Editor editor = preferences.edit();
        editor.putString(Constants.SCALE_MODE_KEY, scaleMode);
        editor.commit();
        current.getImage().scale(scaleMode, true);
        return true;
      }
    }
    
    return false;
  }


  private boolean forceNextScreen()
  {
    int newPosition = position + 1;

    if (newPosition >= comic.getLength())
    { // Load next comic
      return false;
    }
    else
    {
      moveForwardAfterVideo = true;

      if (twoPageLayout)
      {
        setPosition(newPosition + 1, true, true);
      }
      else
      {
        setPosition(newPosition, true, true);
      }
      
      return true;
    }
  }


  private boolean forcePreviousScreen()
  {
    int newPosition = position - 1;

    if (newPosition < 0)
    { // Load next comic
      return false;
    }
    else
    {
      moveForwardAfterVideo = false;

      if (twoPageLayout)
      {
        setPosition(newPosition - 1, true, true);
      }
      else
      {
        setPosition(newPosition, true, true);
      }

      return true;
    }
  }


  private boolean isAnimating()
  {
    if (mAnimating)
    {
      return true;
    }
    else
    {
      final ComicFrame current = (ComicFrame) mSwitcher.getCurrentView();
      
      if (current != null)
      {
        return current.getImage().isAnimating();
      }
    }
    
    return false;
  }


  private void startChangeScreenTask(final int value, final boolean forward, final boolean sequential)
  {
    mImageSwitchTask = new SwitchImageTask();
    mImageSwitchTask.forward = forward;
    mImageSwitchTask.sequential = sequential;
    mImageSwitchTask.imageIndex = value;
    mImageSwitchTask.execute();
  }


  private void setPosition(final int value, final boolean forward, final boolean sequential)
  {
    if (!loadingPage)
    {
      if (sequential)
      {
        setSequentialTransition(value, forward);
      }
      else
      {
        mSwitcher.setInAnimation(AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in));
        mSwitcher.setOutAnimation(AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_out));
      }
      
      if (lowMemoryTransitions)
      {
        final Integer backgroundColor = comic.getBackgroundColor(value);
        final Animation animation;
        final Animation inAnimation = mSwitcher.getInAnimation();
        final Animation outAnimation = mSwitcher.getOutAnimation();
        
        if (inAnimation != null)
        {
          animation = inAnimation;
          inAnimation.setDuration(inAnimation.getDuration() / 2);
        }
        else
        {
          animation = outAnimation;
        }
        
        if (outAnimation != null)
        {
          outAnimation.setDuration(outAnimation.getDuration() / 2);
        }
        
        if (animation != null)
        {
          animation.setAnimationListener(new AnimationListener()
          {

            @Override
            public void onAnimationEnd(Animation a)
            {
              a.setAnimationListener(null);
              ComicView.this.stopAnimating();
              startChangeScreenTask(value, forward, sequential);
            }


            @Override
            public void onAnimationRepeat(Animation arg0)
            {
            }


            @Override
            public void onAnimationStart(Animation arg0)
            {
            }
          });
          this.startAnimating();
          final ComicFrame current = (ComicFrame) mSwitcher.getNextView();
          current.getImage().setImageDrawable(new ColorDrawable(backgroundColor));
          mSwitcher.showNext();
        }
        else
        {
          startChangeScreenTask(value, forward, sequential);
        }
      }
      else
      {
        startChangeScreenTask(value, forward, sequential);
      }
    }
  }


  private void setSequentialTransition(int index, boolean forward)
  {
    String transitionMode = null;
    final long transitionDuration;

    transitionDuration = -1;

    if (transitionMode == null)
    {
      transitionMode = preferences.getString(Constants.TRANSITION_MODE_KEY, Constants.TRANSITION_MODE_NONE_VALUE);
    }
    
    setTransition(forward, transitionMode, transitionDuration);
  }


  private void setTransition(boolean forward, String transitionString, long duration)
  {
    final Animation inAnimation;
    final Animation outAnimation;
    
    if (Constants.TRANSITION_MODE_FADE_VALUE.equals(transitionString))
    {
      if (nextImageUp)
      {
        inAnimation = AnimationUtils.loadAnimation(getContext(), animationFadeIn);
        outAnimation = AnimationUtils.loadAnimation(getContext(), animationKeep);
      }
      else
      {
        inAnimation = AnimationUtils.loadAnimation(getContext(), animationKeep);
        outAnimation = AnimationUtils.loadAnimation(getContext(), animationFadeOut);
      }
    }
    else if (Constants.TRANSITION_MODE_NONE_VALUE.equals(transitionString))
    {
      inAnimation = null;
      outAnimation = null;
    }
    else if (Constants.TRANSITION_MODE_PUSH_UP_VALUE.equals(transitionString))
    {
      inAnimation = AnimationUtils.loadAnimation(getContext(), forward ? animationPushUpIn : animationPushDownIn);
      outAnimation = AnimationUtils.loadAnimation(getContext(), forward ? animationPushUpOut : animationPushDownOut);
    }
    else if (Constants.TRANSITION_MODE_PUSH_DOWN_VALUE.equals(transitionString))
    {
      inAnimation = AnimationUtils.loadAnimation(getContext(), forward ? animationPushDownIn : animationPushUpIn);
      outAnimation = AnimationUtils.loadAnimation(getContext(), forward ? animationPushDownOut : animationPushUpOut);
    }
    else
    {
      boolean leftToRight = isLeftToRight();
      if ((forward && leftToRight) || (forward && !leftToRight))
      {
        inAnimation = AnimationUtils.loadAnimation(getContext(), animationPushLeftIn);
        outAnimation = AnimationUtils.loadAnimation(getContext(), animationPushLeftOut);
      }
      else
      {
        inAnimation = AnimationUtils.loadAnimation(getContext(), animationPushRightIn);
        outAnimation = AnimationUtils.loadAnimation(getContext(), animationPushRightOut);
      }
    }
    
    if (duration >= 0)
    {
      if (inAnimation != null)
      {
        inAnimation.setDuration(duration);
      }
      if (outAnimation != null)
      {
        outAnimation.setDuration(duration);
      }
    }
    
    mSwitcher.setInAnimation(inAnimation);
    mSwitcher.setOutAnimation(outAnimation);
  }


  private void showScreenNumber()
  {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
    boolean showNumber = preferences.getBoolean(Constants.SHOW_NUMBER_KEY, true);
    
    if (showNumber)
    {
      if (toast != null)
      {
        toast.cancel();
      }
      
      String message;

      int numPages = comic.getLength();
      int curPage = Math.min(numPages, Math.max(1, position + 1));

      if (comicWasJustSet)
      {
        String path = comic.getPath();
        File file = new File(path);
        message = String.format("%d/%d\n%s", curPage, numPages, file.getName());
      }
      else
      {
        message = String.format("%d/%d", curPage, numPages);
      }

      toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
      toast.setGravity(Gravity.BOTTOM | Gravity.RIGHT, 0, 0);
      toast.show();
    }
  }


  private void startAnimating()
  {
    mAnimating = true;
    
    if (mListener != null)
    {
      mListener.onAnimationStart(this);
    }
  }


  private void stopAnimating()
  {
    mAnimating = false;
    
    if (mListener != null)
    {
      mListener.onAnimationEnd(this);
    }
  }

}
