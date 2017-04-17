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
package net.robotmedia.acv.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.Constants;
import net.robotmedia.acv.adapter.RecentListBaseAdapter;
import net.robotmedia.acv.comic.Comic;
import net.robotmedia.acv.logic.PreferencesController;
import net.robotmedia.acv.logic.SetComicScreenAsTask;
import net.robotmedia.acv.provider.HistoryManager;
import net.robotmedia.acv.ui.settings.mobile.SettingsActivityMobile;
import net.robotmedia.acv.ui.settings.tablet.SettingsActivityTablet;
import net.robotmedia.acv.ui.widget.AnkiSendDialogFragment;
import net.robotmedia.acv.ui.widget.ComicView;
import net.robotmedia.acv.ui.widget.ComicViewListener;
import net.robotmedia.acv.ui.widget.OcrLayout;
import net.robotmedia.acv.ui.widget.OcrLayout.NudgeDirection;
import net.robotmedia.acv.utils.FileUtils;
import net.robotmedia.acv.utils.MathUtils;
import net.robotmedia.acv.utils.Reflect;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;

public class ComicViewerActivity extends ExtendedActivity implements OnGestureListener,
    GestureDetector.OnDoubleTapListener, ComicViewListener, AnkiSendDialogFragment.ClickListener
{

  public final static String POSITION_EXTRA = "position";

  private class LoadComicTask extends AsyncTask<String, Object, Comic>
  {

    public int initialIndex = 0;

    private ProgressDialog progressDialog;


    @Override
    protected Comic doInBackground(String... params)
    {
      final String path = params[0];
      Comic result = Comic.createComic(path);
      
      if (result != null)
      {
        HistoryManager.getInstance(ComicViewerActivity.this).remember(new File(path));
      }
      
      return result;
    }


    protected void onPostExecute(Comic result)
    {
      if (progressDialog != null)
      {
        progressDialog.dismiss();
      }

      if (result != null && !result.isError())
      {
        comic = result;

        mScreen.setVisibility(View.VISIBLE);
        hideRecentItems();
        preferencesController.savePreference(Constants.COMIC_PATH_KEY, comic.getPath());

        mScreen.setComic(comic);
        mScreen.goToScreen(initialIndex);

        if (isHoneyComb())
        {
          new MenuHelper().invalidateOptionsMenu();
        }
        
        hideActionBar();
      }
      else
      {
        mScreen.setVisibility(View.GONE);
        showRecentItems();
        showDialog(Constants.DIALOG_LOAD_ERROR);
      }
    }


    @Override
    protected void onPreExecute()
    {
      progressDialog = new ACVDialogFactory(ComicViewerActivity.this).createLoadProgressDialog();
      progressDialog.show();
      removePreviousComic(true, false);
    }
  }

  private GestureDetector mGestureDetector;
  private ImageButton mCornerTopLeft;
  private ImageButton mCornerTopRight;
  private ImageButton mCornerBottomLeft;
  private ImageButton mCornerBottomRight;
  private ImageButton mEdgeLeft;
  private ImageButton mEdgeRight;
  private ImageButton mEdgeTop;
  private ImageButton mEdgeBottom;

  protected Comic comic;
  protected boolean destroyed = false;
  protected ACVDialogFactory dialogFactory;
  protected LoadComicTask loadComicTask = null;

  protected boolean markCleanExitPending = false;
  protected ViewGroup mRecentItems = null;
  protected ListView mRecentItemsList = null;
  protected RecentListBaseAdapter mRecentItemsListAdapter = null;
  protected View mButtonsContainer;
  protected OcrLayout ocrLayout;
  protected View mMain;
  protected ComicView mScreen;
  protected PreferencesController preferencesController;
  protected SharedPreferences preferences;
  protected boolean requestedRotation = false;
  protected boolean ocrShown = false;
  protected File photoFile = null;
  protected boolean takingPhoto = false;
  private String mComicPath; // Used for testing


  public String getComicPath()
  {
    return mComicPath;
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == Constants.SCREEN_BROWSER_CODE && resultCode == RESULT_OK)
    {
      int index = data.getIntExtra(BrowseActivity.POSITION_EXTRA, mScreen.getIndex());
      
      if (isComicLoaded())
      {
        mScreen.goToScreen(index);
      }
    }
    else if (requestCode == Constants.SD_BROWSER_CODE && resultCode == RESULT_OK)
    {
      String absolutePath = data.getStringExtra(Constants.COMIC_PATH_KEY);
      this.loadComic(absolutePath);
    }
    else if (requestCode == Constants.SETTINGS_CODE)
    {
      boolean sensor = preferences.getBoolean(Constants.AUTO_ROTATE_KEY, false);
      
      if (sensor)
      {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
      }
      else
      {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
      }

      if (preferencesController.isLeftToRight())
      {
        if (preferencesController.isUsedForPreviousNext(Constants.TRACKBALL_RIGHT_KEY, Constants.TRACKBALL_LEFT_KEY)
            || preferencesController.isUsedForPreviousNext(Constants.INPUT_FLING_LEFT, Constants.INPUT_FLING_RIGHT)
            || preferencesController.isUsedForPreviousNext(Constants.INPUT_CORNER_BOTTOM_RIGHT,
                Constants.INPUT_CORNER_BOTTOM_LEFT))
        {
          showDialog(Constants.DIALOG_FLIP_CONTROLS);
        }
      }
      else
      {
        if (preferencesController.isUsedForPreviousNext(Constants.TRACKBALL_LEFT_KEY, Constants.TRACKBALL_RIGHT_KEY)
            || preferencesController.isUsedForPreviousNext(Constants.INPUT_FLING_RIGHT, Constants.INPUT_FLING_LEFT)
            || preferencesController.isUsedForPreviousNext(Constants.INPUT_CORNER_BOTTOM_LEFT,
                Constants.INPUT_CORNER_BOTTOM_RIGHT))
        {
          showDialog(Constants.DIALOG_FLIP_CONTROLS);
        }
      }
      this.adjustCornersVisibility(true); // Actions assigned to corners might have changed

      adjustBrightness();
      adjustLowMemoryMode();

      if (isComicLoaded())
      {
        mScreen.goToScreen(mScreen.getIndex());
      }
    }
    else if(requestCode == Constants.REQUEST_TAKE_PHOTO)
    {
      mScreen.setTwoPageLayout(false);
      takingPhoto = true;
      loadComic(photoFile.toString(), 0);
    }
  }

  @Override
  public void onAnkiSendDialogOK(long deckId, long modelId, String modelKey, String[] fieldValues) {
    ocrLayout.addAnkiCard(deckId, modelId, modelKey, fieldValues);
  }

  @Override
  public void onAnkiSendDialogCancel() {
      Toast.makeText(this, R.string.ocr_send_anki_cancelled, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus)
  {
    super.onWindowFocusChanged(hasFocus);
    
    if (hasFocus)
    {
      // Remove navigation bar when a comic is being shown.
      // https://developer.android.com/training/system-ui/immersive.html
      if (isKitKat())
      {
        if(mScreen.getVisibility() == View.VISIBLE)
        {
          getWindow().getDecorView().setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE 
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN 
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        else
        {
          int flags = getWindow().getDecorView().getSystemUiVisibility();
          
          flags &= (~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
          flags &= (~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
          flags &= (~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
          getWindow().getDecorView().setSystemUiVisibility(flags);
        }
      }
    }
  }

  /**
   * @param requestCode
   * @param permissions
   * @param grantResults
   * @author Marlon Paulse
   */
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == OcrLayout.ANKI_RW_PERM_REQ_CODE) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        ocrLayout.performSendAction(getString(R.string.ocr_send_dialog_opt_ankidroid));
      } else {
        ocrLayout.showErrorDialog(R.string.ocr_send_anki_permission_denied);
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (!isHoneyComb())
    {
      requestWindowFeature(Window.FEATURE_NO_TITLE);
    }
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    
    setContentView(R.layout.main);

    dialogFactory = new ACVDialogFactory(this);
    mRecentItems = (ViewGroup) findViewById(R.id.main_recent);
    mRecentItemsList = (ListView) findViewById(R.id.main_recent_list);
    mRecentItemsList.setEmptyView(findViewById(R.id.main_recent_list_no_items));
    mRecentItemsListAdapter = new RecentListBaseAdapter(this, R.layout.list_item_recent);
    mRecentItemsListAdapter.setMaxNumItems(2);
    mRecentItemsList.setAdapter(mRecentItemsListAdapter);
    
    mRecentItemsList.setOnItemClickListener(new OnItemClickListener()
    {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id)
      {
        String path = (String) parent.getItemAtPosition(position);
        loadComic(path);
      }
    });

    mGestureDetector = new GestureDetector(this);
    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    mButtonsContainer = findViewById(R.id.main_buttons_container);
    
    ocrLayout = (OcrLayout)findViewById(R.id.ocr_layout);
  

    mMain = findViewById(R.id.main_layout);

    ImageView logo = (ImageView) findViewById(R.id.main_logo);
    
    logo.setOnClickListener(new OnClickListener()
    {
      public void onClick(View view)
      {
        showMenu();
      }
    });

    mScreen = (ComicView) findViewById(R.id.screen);
    mScreen.setListener(this);

    mCornerTopLeft = (ImageButton) findViewById(R.id.corner_top_left);
    mCornerTopRight = (ImageButton) findViewById(R.id.corner_top_right);
    mCornerBottomLeft = (ImageButton) findViewById(R.id.corner_bottom_left);
    mCornerBottomRight = (ImageButton) findViewById(R.id.corner_bottom_right);
    mEdgeLeft = (ImageButton) findViewById(R.id.edge_left);
    mEdgeRight = (ImageButton) findViewById(R.id.edge_right);
    mEdgeTop = (ImageButton) findViewById(R.id.edge_top);
    mEdgeBottom = (ImageButton) findViewById(R.id.edge_bottom);
    
    adjustCornersVisibility(true);

    preferencesController = new PreferencesController(this);

    adjustBrightness();
    adjustLowMemoryMode();
    
    mScreen.setTwoPageLayout(preferences.getBoolean(Constants.TWO_PAGE_LAYOUT_KEY, false));

    // TODO: Shouldn't this be first?
    if (startupOrientation(savedInstanceState))
    { // If no orientation change was requested
      preferencesController.checkCleanExit();
      markCleanExitPending = true;
    }

    String comicPath = null;
    
    if (savedInstanceState != null && savedInstanceState.containsKey(Constants.COMIC_PATH_KEY))
    {
      comicPath = savedInstanceState.getString(Constants.COMIC_PATH_KEY);
    }

    if (comicPath == null)
    {
      Intent intent = getIntent();
      boolean loaded = false;

      if (intent != null)
      {
        loaded = attemptToLoadComicFromViewIntent(intent);
      }
      
      if (!loaded)
      {
        showRecentItems();
      }
    }
    else
    {
      loadComic(comicPath);
    }
  }


  @Override
  public void onResume()
  {
    mRecentItemsListAdapter.refresh();
    super.onResume();
  }


  private void adjustLowMemoryMode()
  {
    boolean lowMemory = preferences.getBoolean(PreferencesController.PREFERENCE_LOW_MEMORY, false);
    
    // If heap size is small (<= 32MB), force low memory mode
    if((Runtime.getRuntime().maxMemory() / 1048576L) <= 32)
    {
      lowMemory = true;
    }
    
    mScreen.setPreload(!lowMemory);
    mScreen.setLowMemoryTransitions(lowMemory);
  }


  private void adjustBrightness()
  {
    WindowManager.LayoutParams lp = getWindow().getAttributes();
    float brightness = (float) preferences.getInt(Constants.BRIGHTNESS_KEY, Math.round(lp.screenBrightness * 100)) / 100f;
    
    if (brightness == 0)
    { 
      // 0 renders the phone unusable
      brightness = 1f / 100f;
    }
    
    lp.screenBrightness = brightness;
    getWindow().setAttributes(lp);
  }


  private void adjustCornerVisibility(ImageButton corner, String key, String defaultAction, boolean allInvisible)
  {
    final String action = preferences.getString(key, defaultAction);
    final boolean visible = !allInvisible && !Constants.ACTION_VALUE_NONE.equals(action);
   
    if (visible)
    {
      corner.setImageResource(R.drawable.corner_button);
    }
    else
    {
      corner.setImageDrawable(null);
    }
  }


  private void adjustCornersVisibility(final boolean visible)
  {
    final boolean allInvisible = !visible || preferences.getBoolean(Constants.PREFERENCE_INVISIBLE_CORNERS, false);
    adjustCornerVisibility(mCornerTopLeft, Constants.INPUT_CORNER_TOP_LEFT, Constants.ACTION_VALUE_NONE, allInvisible);
    adjustCornerVisibility(mCornerTopRight, Constants.INPUT_CORNER_TOP_RIGHT, Constants.ACTION_VALUE_NONE, allInvisible);
    adjustCornerVisibility(mCornerBottomLeft, Constants.INPUT_CORNER_BOTTOM_LEFT, Constants.ACTION_VALUE_PREVIOUS, allInvisible);
    adjustCornerVisibility(mCornerBottomRight, Constants.INPUT_CORNER_BOTTOM_RIGHT, Constants.ACTION_VALUE_NEXT, allInvisible);
    adjustCornerVisibility(mEdgeLeft, Constants.INPUT_EDGE_LEFT, Constants.ACTION_VALUE_NONE, allInvisible);
    adjustCornerVisibility(mEdgeRight, Constants.INPUT_EDGE_RIGHT, Constants.ACTION_VALUE_NONE, allInvisible);
    adjustCornerVisibility(mEdgeTop, Constants.INPUT_EDGE_TOP, Constants.ACTION_VALUE_NONE, allInvisible);
    adjustCornerVisibility(mEdgeBottom, Constants.INPUT_EDGE_BOTTOM, Constants.ACTION_VALUE_NONE, allInvisible);
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    super.onCreateOptionsMenu(menu);
    
    // cb4960: Moved to onPrepareOptionsMenu() so that we can choose which menu to display 
    //MenuInflater inflater = getMenuInflater();

    return true;
  }


  public boolean onDoubleTap(MotionEvent e)
  {
    if(ocrShown)
    {
      return true;
    }
    
    String action = preferences.getString(Constants.INPUT_DOUBLE_TAP, Constants.ACTION_VALUE_ZOOM_IN);
    Point p = new Point(Math.round(e.getX()), Math.round(e.getY()));
    
    if (Constants.ACTION_VALUE_ZOOM_IN.equals(action) && isComicLoaded() && mScreen.isMaxZoom())
    {
      // Zoom back out
      
      String scaleMode = preferences.getString(Constants.SCALE_MODE_KEY, Constants.SCALE_MODE_WIDTH_VALUE);
      
      if (Constants.SCALE_MODE_BEST_VALUE.equals(scaleMode))
      {
        mScreen.fitScreen();
      }
      else if (Constants.SCALE_MODE_WIDTH_VALUE.equals(scaleMode))
      {
        mScreen.fitWidth();
      }
      else if (Constants.SCALE_MODE_HEIGHT_VALUE.equals(scaleMode))
      {
        mScreen.fitHeight();
      }
      else // None
      {
        return mScreen.zoom(-1, p);
      }
      
      return true;
    }
    else
    {
      return action(Constants.INPUT_DOUBLE_TAP, Constants.ACTION_VALUE_ZOOM_IN, p);
    }
  }


  public boolean onDoubleTapEvent(MotionEvent e)
  {
    return false;
  }


  public boolean onDown(MotionEvent e)
  {
    if(ocrShown)
    {
      return this.ocrLayout.onDown(e);
    }
    
    return false;
  }


  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
  {
    if(ocrShown)
    {
      return this.ocrLayout.onFling(e1, e2, velocityX, velocityY);
    }
    else
    {
      if (mPinch)
      {
        return false;
      }
      
      final double angle = MathUtils.getAngle(e1.getX(), e1.getY(), e2.getX(), e2.getY());
      final int minFlingDifference = MathUtils.dipToPixel(this, Constants.MIN_FLING_DIFFERENCE_DIP);
      final float distance = MathUtils.distance(e1.getX(), e1.getY(), e2.getX(), e2.getY());
  
      boolean comicLoaded = isComicLoaded();
     
      if (distance > minFlingDifference)
      {
        if ((angle < Constants.MAX_FLING_ANGLE || Math.abs(angle - 180) < Constants.MAX_FLING_ANGLE))
        {
          if (e1.getX() > e2.getX())
          { // Fling left
            if (!comicLoaded || mScreen.isRightMost())
            {
              return action(Constants.INPUT_FLING_LEFT, Constants.ACTION_VALUE_NEXT);
            }
          }
          else
          { // Fling right
            if (!comicLoaded || mScreen.isLeftMost())
            {
              return action(Constants.INPUT_FLING_RIGHT, Constants.ACTION_VALUE_PREVIOUS);
            }
          }
        }
        else if (angle - 90 < Constants.MAX_FLING_ANGLE)
        {
          if (e1.getY() > e2.getY())
          { // Fling up
            if (!comicLoaded || mScreen.isBottomMost())
            {
              return action(Constants.INPUT_FLING_UP, Constants.ACTION_MENU);
            }
          }
          else
          { // Fling down
            if (!comicLoaded || mScreen.isTopMost())
            {
              return action(Constants.INPUT_FLING_DOWN, Constants.ACTION_VALUE_NONE);
            }
          }
        }
      }
      
      return false;
    }
  }


  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event)
  {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
    {
      String action = preferences.getString(Constants.INPUT_VOLUME_UP, Constants.ACTION_VALUE_PREVIOUS);
      
      // The actual action is handled in OnKeyDown().
      // If volume up is set to NONE in the settings, let system change the volume.
      // Otherwise, block the system from changing the volume.
      if (Constants.ACTION_VALUE_NONE.equals(action))
      {
        return super.onKeyUp(keyCode, event);
      }
      else
      {
        return true;
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
    {
      String action = preferences.getString(Constants.INPUT_VOLUME_DOWN, Constants.ACTION_VALUE_NEXT);
      
      // The actual action is handled in OnKeyDown().
      // If volume up is set to NONE in the settings, let system change the volume.
      // Otherwise, block the system from changing the volume.
      if (Constants.ACTION_VALUE_NONE.equals(action))
      {
        return super.onKeyUp(keyCode, event);
      }
      else
      {
        return true;
      }
    }
    else if (this.isComicLoaded() && ((keyCode == KeyEvent.KEYCODE_O) 
        || (keyCode == KeyEvent.KEYCODE_ENTER)))
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      else
      {
        this.showOcr();
      }
      
      return true;
    }
    else if (this.isComicLoaded() && ((keyCode == KeyEvent.KEYCODE_EQUALS)
        || (keyCode == KeyEvent.KEYCODE_PLUS)))
    {
      return action("", Constants.ACTION_VALUE_ZOOM_IN);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_MINUS))
    {
      return action("", Constants.ACTION_VALUE_ZOOM_OUT);
    }
    else if (this.isComicLoaded() && ((keyCode == KeyEvent.KEYCODE_1)
        || (keyCode == KeyEvent.KEYCODE_DEL))) // Backspace
    {
      return action("", Constants.ACTION_VALUE_FIT_WIDTH);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_2))
    {
      return action("", Constants.ACTION_VALUE_FIT_HEIGHT);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_3))
    {
      return action("", Constants.ACTION_VALUE_FIT_SCREEN);
    } 
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_4))
    {
      return action("", Constants.ACTION_VALUE_ACTUAL_SIZE);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_MOVE_END))
    {
      return action("", Constants.ACTION_VALUE_LAST);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_MOVE_HOME))
    {
      return action("", Constants.ACTION_VALUE_FIRST);
    }
    else if((keyCode == KeyEvent.KEYCODE_R) 
        || (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET) 
        || (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET))
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }

      return action("", Constants.ACTION_VALUE_ROTATE);
    }
    else if (this.isComicLoaded() && (keyCode == KeyEvent.KEYCODE_B))
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_VALUE_SCREEN_BROWSER);
    }
    else if (keyCode == KeyEvent.KEYCODE_F)
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_VALUE_SD_BROWSER);
    }
    else if (keyCode == KeyEvent.KEYCODE_M)
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_MENU);
    }
    else if (keyCode == KeyEvent.KEYCODE_G)
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_VALUE_SETTINGS);
    }
    else if (keyCode == KeyEvent.KEYCODE_X)
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_CLOSE);
    }
    else if (keyCode == KeyEvent.KEYCODE_P)
    {
      if(this.ocrShown)
      {
        this.hideOcr();
      }
      
      return action("", Constants.ACTION_VALUE_TOGGLE_PAGE_LAYOUT);
    }
    else if (this.ocrShown && (keyCode == KeyEvent.KEYCODE_N))
    {
      this.ocrLayout.showOcrSendDialog();
      return true;
    }
    else if (this.ocrShown && (keyCode == KeyEvent.KEYCODE_T))
    {
      this.ocrLayout.switchTextOrientation();
      return true;
    }
    else if (this.ocrShown && (keyCode == KeyEvent.KEYCODE_E))
    {
      this.ocrLayout.swapNudgeCorner();
      return true;
    }
    else
    {
      return super.onKeyUp(keyCode, event);
    }
  }


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
    {
      final String action = preferences.getString(Constants.INPUT_VOLUME_UP, Constants.ACTION_VALUE_PREVIOUS);
      
      if (Constants.ACTION_VALUE_NONE.equals(action))
      {
        return super.onKeyDown(keyCode, event);
      }
      else
      {
        action(Constants.INPUT_VOLUME_UP, Constants.ACTION_VALUE_PREVIOUS);
        return true;
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
    {
      final String action = preferences.getString(Constants.INPUT_VOLUME_DOWN, Constants.ACTION_VALUE_NEXT);
      
      if (Constants.ACTION_VALUE_NONE.equals(action))
      {
        return super.onKeyDown(keyCode, event);
      }
      else
      {
        action(Constants.INPUT_VOLUME_DOWN, Constants.ACTION_VALUE_NEXT);
        return true;
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.lookupNextWord();
        return true;
      }
      else
      {
        return action(Constants.TRACKBALL_RIGHT_KEY, Constants.ACTION_VALUE_NEXT);
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.lookupPrevWord();
        return true;
      }
      else
      {
        return action(Constants.TRACKBALL_LEFT_KEY, Constants.ACTION_VALUE_PREVIOUS);
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
    {
      return action(Constants.TRACKBALL_UP_KEY, Constants.ACTION_VALUE_ZOOM_IN);
    }
    else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
    {
      return action(Constants.TRACKBALL_DOWN_KEY, Constants.ACTION_VALUE_ZOOM_OUT);
    }
    else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
    {
      return action(Constants.TRACKBALL_CENTER_KEY, Constants.ACTION_VALUE_NEXT);
    }
    else if (keyCode == KeyEvent.KEYCODE_BACK)
    {
      if(ocrShown)
      {
        this.hideOcr();
        return true;
      }
      else
      {
        final String action = preferences.getString(Constants.BACK_KEY, Constants.ACTION_VALUE_NONE);
        
        if (Constants.ACTION_VALUE_NONE.equals(action))
        {
          return super.onKeyDown(keyCode, event);
        }
        else if (Constants.ACTION_VALUE_PREVIOUS.equals(action) && mScreen.getIndex() == 0)
        {
          return super.onKeyDown(keyCode, event);
        }
        else
        {
          action(Constants.BACK_KEY, Constants.ACTION_VALUE_NONE);
          return true;
        }
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_SPACE)
    {
      action("", Constants.ACTION_VALUE_NEXT);
    }
    else if (keyCode == KeyEvent.KEYCODE_W)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.nudgeCaptureBox(NudgeDirection.UP);
        return true;
      }
      else
      {
        action("", Constants.ACTION_VALUE_SCROLL_UP);
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_A)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.nudgeCaptureBox(NudgeDirection.LEFT);
        return true;
      }
      else
      {
        action("", Constants.ACTION_VALUE_SCROLL_LEFT);
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_S)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.nudgeCaptureBox(NudgeDirection.DOWN);
        return true;
      }
      else
      {
        action("", Constants.ACTION_VALUE_SCROLL_DOWN);
      }
    }
    else if (keyCode == KeyEvent.KEYCODE_D)
    {
      if(this.ocrShown)
      {
        this.ocrLayout.nudgeCaptureBox(NudgeDirection.RIGHT);
        return true;
      }
      else
      {
        action("", Constants.ACTION_VALUE_SCROLL_RIGHT);
      }
    }
    
    return super.onKeyDown(keyCode, event);
  }


  public void onLongPress(MotionEvent arg0)
  {
    if(ocrShown)
    {
      ocrLayout.onLongPress(arg0);
    }
    else
    {
      if (!mPinch)
      {
        action(Constants.LONG_TAP_KEY, Constants.ACTION_VALUE_SCREEN_BROWSER);
      }
    }
  }


  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item)
  {
    return super.onMenuItemSelected(featureId, item);
  }


  @Override
  public void onPanelClosed(int featureId, Menu menu)
  {
    super.onPanelClosed(featureId, menu);
    hideActionBarDelayed();
  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    String actionValue = null;
    
    switch (item.getItemId())
    {
      case R.id.item_zoom_in:
        actionValue = Constants.ACTION_VALUE_ZOOM_IN;
        break;
      case R.id.item_zoom_out:
        actionValue = Constants.ACTION_VALUE_ZOOM_OUT;
        break;
      case R.id.item_fit_width:
        actionValue = Constants.ACTION_VALUE_FIT_WIDTH;
        break;
      case R.id.item_fit_height:
        actionValue = Constants.ACTION_VALUE_FIT_HEIGHT;
        break;
      case R.id.item_actual_size:
        actionValue = Constants.ACTION_VALUE_ACTUAL_SIZE;
        break;
      case R.id.item_first:
        actionValue = Constants.ACTION_VALUE_FIRST;
        break;
      case R.id.item_previous:
        actionValue = Constants.ACTION_VALUE_PREVIOUS;
        break;
      case R.id.item_next:
        actionValue = Constants.ACTION_VALUE_NEXT;
        break;
      case R.id.item_previous_screen:
        actionValue = Constants.ACTION_VALUE_PREVIOUS_SCREEN;
        break;
      case R.id.item_next_screen:
        actionValue = Constants.ACTION_VALUE_NEXT_SCREEN;
        break;
      case R.id.item_last:
        actionValue = Constants.ACTION_VALUE_LAST;
        break;
      case R.id.item_browse:
        actionValue = Constants.ACTION_VALUE_SCREEN_BROWSER;
        break;
      case R.id.item_toggle_page_layout:
        actionValue = Constants.ACTION_VALUE_TOGGLE_PAGE_LAYOUT;
        break;
      case R.id.item_take_photo:
        actionValue = Constants.ACTION_VALUE_TAKE_PHOTO;
        break;
      case R.id.item_rotate:
        actionValue = Constants.ACTION_VALUE_ROTATE;
        break;
      case R.id.item_settings:
        actionValue = Constants.ACTION_VALUE_SETTINGS;
        break;
      case R.id.item_open:
        actionValue = Constants.ACTION_VALUE_SD_BROWSER;
        break;
      case R.id.menu_close:
        actionValue = Constants.ACTION_CLOSE;
        break;
    }
    
    if (actionValue != null)
    {
      return this.actionWithValue(actionValue, Constants.EVENT_VALUE_MENU, null);
    }
    else
    {
      return false;
    }
  }


  @Override
  public boolean onPrepareOptionsMenu(Menu menu)
  {
    if (!isHoneyComb() && mScreen.isLoading())
    {
      return false;
    }
    
    // Remove current menu. It will be re-created below depending on the mode.
    menu.clear(); 
    
    if(this.ocrShown)
    {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.ocr_menu, menu);
      
      // Get references to the menu items and pass them to the ocr layout view
      // so they can have their text and visibility updated
      this.ocrLayout.updateMenuItemTextOrientation(menu.findItem(R.id.menu_item_ocr_text_orientation));
      this.ocrLayout.updateMenuItemSend(menu.findItem(R.id.menu_item_ocr_send));
    }
    else // OCR not shown
    {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main, menu);
      
      boolean comicLoaded = isComicLoaded();
      
      menu.findItem(R.id.item_navigate).setVisible(comicLoaded);
      menu.findItem(R.id.item_zoom).setVisible(comicLoaded);
      menu.findItem(R.id.item_browse).setVisible(comicLoaded);
      menu.findItem(R.id.item_rotate).setVisible(comicLoaded);
      menu.findItem(R.id.item_toggle_page_layout).setVisible(comicLoaded);
  
      if (comicLoaded)
      {
        boolean considerFrames = comic.hasFrames(mScreen.getIndex());
        
        menu.findItem(R.id.item_next_screen).setVisible(considerFrames);
        menu.findItem(R.id.item_previous_screen).setVisible(considerFrames);
      }
    }
    
    return true;
  }


  public void onScreenLoadFailed()
  {
    mScreen.setVisibility(View.INVISIBLE);
    showRecentItems();

    // Remove the comic path in case the comic is defective.
    // If the page load failed because of an orientation change, the comic path is saved in the
    // instance state anyway.
    preferencesController.savePreference(Constants.COMIC_PATH_KEY, null);

    removePreviousComic(true, true);

    // Don't want to show an error if the activity was destroyed
    if (!destroyed)
    {
      showDialog(Constants.DIALOG_PAGE_ERROR);
    }
  }

  private boolean mScrolling;


  public boolean onScroll(MotionEvent downEvent, MotionEvent dragEvent, float distanceX, float distanceY)
  {
    if(ocrShown)
    {
      this.ocrLayout.onScroll(downEvent, dragEvent, distanceX, distanceY);
      return true;
    }
    else
    {
      mScrolling = true;
      
      if (isComicLoaded() && !mPinch)
      {
        return mScreen.scroll(Math.round(distanceX), Math.round(distanceY));
      }
      else
      {
        return false;
      }
    }
  }


  public void onShowPress(MotionEvent arg0)
  {
    if(this.ocrShown)
    {
      this.ocrLayout.onShowPress(arg0);
    }
  }
  
  
  private enum CornerRegion
  {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
  }
  
   
  /** Get corner region that provided coords are in */
  private CornerRegion determineCornerRegion(final float x, final float y)
  {
    final int width = mMain.getWidth();
    final int height = mMain.getHeight();
    final int cornerWidth = MathUtils.dipToPixel(this, Constants.CORNER_WIDTH_DIP);
    
    CornerRegion cornerRegion = CornerRegion.NONE;
    
    if ((x <= cornerWidth) && (y <= cornerWidth))
    {
      cornerRegion = CornerRegion.TOP_LEFT;
    }
    else if ((x <= cornerWidth) && (y >= (height - cornerWidth)))
    {
      cornerRegion = CornerRegion.BOTTOM_LEFT;
    }
    else if ((x >= (width - cornerWidth)) && (y <= cornerWidth))
    {
      cornerRegion = CornerRegion.TOP_RIGHT;
    }
    else if ((x >= (width - cornerWidth)) && (y >= (height - cornerWidth)))
    {
      cornerRegion = CornerRegion.BOTTOM_RIGHT;
    }
    else if ((x <= cornerWidth)
        && (y >= (height / 2) - (cornerWidth / 2)) && (y <= (height / 2) + (cornerWidth / 2)))
    {
      cornerRegion = CornerRegion.LEFT;
    }
    else if ((x >= (width - cornerWidth))
        && (y >= (height / 2) - (cornerWidth / 2)) && (y <= (height / 2) + (cornerWidth / 2)))
    {
      cornerRegion = CornerRegion.RIGHT;
    }
    else if ((x >= ((width / 2) - (cornerWidth / 2))) && (x <= ((width / 2) + (cornerWidth / 2)))
        && (y <= cornerWidth))
    {
      cornerRegion = CornerRegion.TOP;
    }
    else if ((x >= ((width / 2) - (cornerWidth / 2))) && (x <= ((width / 2) + (cornerWidth / 2)))
        && (y >= (height - cornerWidth)))
    {
      cornerRegion = CornerRegion.BOTTOM;
    }

    return cornerRegion;
  }


  private boolean detectCornerButton(MotionEvent e, boolean pressed, boolean action)
  {
    CornerRegion cornerRegion = this.determineCornerRegion(e.getX(), e.getY());
    String inputKey = null;
    String defaultAction = null;
    View button = null;
    boolean actionPerformed = false;
    
    if (cornerRegion == CornerRegion.TOP_LEFT)
    {
      button = mCornerTopLeft;
      inputKey = Constants.INPUT_CORNER_TOP_LEFT;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }
    else if (cornerRegion == CornerRegion.BOTTOM_LEFT)
    {
      button = mCornerBottomLeft;
      inputKey = Constants.INPUT_CORNER_BOTTOM_LEFT;
      defaultAction = Constants.ACTION_VALUE_PREVIOUS;
    }
    else if (cornerRegion == CornerRegion.TOP_RIGHT)
    {
      button = mCornerTopRight;
      inputKey = Constants.INPUT_CORNER_TOP_RIGHT;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }
    else if (cornerRegion == CornerRegion.BOTTOM_RIGHT)
    {
      button = mCornerBottomRight;
      inputKey = Constants.INPUT_CORNER_BOTTOM_RIGHT;
      defaultAction = Constants.ACTION_VALUE_NEXT;
    }
    else if (cornerRegion == CornerRegion.LEFT)
    {
      button = mEdgeLeft;
      inputKey = Constants.INPUT_EDGE_LEFT;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }
    else if (cornerRegion == CornerRegion.RIGHT)
    {
      button = mEdgeRight;
      inputKey = Constants.INPUT_EDGE_RIGHT;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }
    else if (cornerRegion == CornerRegion.TOP)
    {
      button = mEdgeTop;
      inputKey = Constants.INPUT_EDGE_TOP;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }
    else if (cornerRegion == CornerRegion.BOTTOM)
    {
      button = mEdgeBottom;
      inputKey = Constants.INPUT_EDGE_BOTTOM;
      defaultAction = Constants.ACTION_VALUE_NONE;
    }

    if (action && (inputKey != null))
    {
      actionPerformed = action(inputKey, defaultAction);
      
      final String buttonAction = preferences.getString(inputKey, defaultAction);
      
      // If corner is mapped to an action
      if(!buttonAction.equals(Constants.ACTION_VALUE_NONE))
      {
        // Make sure that onSingleTapConfirmed() doesn't act on this press
        this.cornerButtonConsumed = true;
      }
    }
  
    if (button != null)
    {
      button.setPressed(pressed);
    }
    else
    {
      unpressCornerButtons();
    }
    
    return actionPerformed;
  }


  public boolean onSingleTapConfirmed(MotionEvent e)
  {
    if (mPinch || cornerButtonConsumed)
    {
      return false; // TODO is pinch necessary?
    }
    
    final Point p = new Point(Math.round(e.getX()), Math.round(e.getY()));

    boolean processed = action(Constants.SINGLE_TAP_KEY, Constants.ACTION_VALUE_NONE, p);

    return processed;
  }

  /** True if corner button press detected */
  private boolean cornerButtonConsumed = false;

  /** Corner that was pressed in onActionDown() */
  private CornerRegion initialCornerPressed = CornerRegion.NONE;
  

  public boolean onSingleTapUp(MotionEvent e)
  {
    if(this.ocrShown)
    {
      return this.ocrLayout.onSingleTapUp(e);
    }
    else
    {
      return false;
    }
  }


  public void onStop()
  {
    super.onStop();
    
    if (markCleanExitPending)
    {
      preferencesController.markCleanExit();
    }
  }


  private void onActionDown(MotionEvent e)
  {
    mPinch = false;
    
    detectCornerButton(e, true, false);
    
    if(this.ocrShown)
    {
      return;
    }
    
    this.initialCornerPressed = this.determineCornerRegion(e.getX(), e.getY());
  }


  private void onActionMove(MotionEvent e)
  {
    if (mPinching && Reflect.getPointerCount(e) == 2)
    {
      final float x0 = Reflect.getX(e, 0);
      final float y0 = Reflect.getY(e, 0);
      final float x1 = Reflect.getX(e, 1);
      final float y1 = Reflect.getY(e, 1);
      final float newDistance = MathUtils.distance(x0, y0, x1, y1);
      float ratio = newDistance / pinchDistance;
      ratio = 1 + (ratio - 1) * 0.5f;
      mScreen.zoom(ratio, pinchCenter);
      pinchDistance = newDistance;
    }
    
    detectCornerButton(e, true, false);
  }


  private void unpressCornerButtons()
  {
    mCornerBottomLeft.setPressed(false);
    mCornerBottomRight.setPressed(false);
    mCornerTopLeft.setPressed(false);
    mCornerTopRight.setPressed(false);
    mEdgeLeft.setPressed(false);
    mEdgeRight.setPressed(false);
    mEdgeTop.setPressed(false);
    mEdgeBottom.setPressed(false);
  }


  private void onActionUp(MotionEvent e)
  {
    mPinching = false;
    mScrolling = false;
    
    if(this.ocrShown)
    {
      return;
    }
    
    this.cornerButtonConsumed = false;
    
    // If user had originally pressed down in a corner region
    if(this.initialCornerPressed != CornerRegion.NONE)
    {
      // Get region where the user just released finger
      CornerRegion currentCornerPressed = this.determineCornerRegion(e.getX(), e.getY());
      
      // If user released finger in the same a corner region
      if(currentCornerPressed == this.initialCornerPressed)
      {
        this.detectCornerButton(e, false, true);
      }
      
      this.initialCornerPressed = CornerRegion.NONE;
    }
    
    unpressCornerButtons();
  }


  private void onActionPointerDown(MotionEvent e)
  {
    if (!mScrolling && isComicLoaded())
    {
      mPinch = true;
      mPinching = true;
      final float x0 = Reflect.getX(e, 0);
      final float y0 = Reflect.getY(e, 0);
      final float x1 = Reflect.getX(e, 1);
      final float y1 = Reflect.getY(e, 1);
      pinchDistance = MathUtils.distance(x0, y0, x1, y1);
      final int centerX = Math.round(x0 + x1) / 2;
      final int centerY = Math.round(y0 + y1) / 2;
      Point center = new Point(centerX, centerY);
      pinchCenter = mScreen.toImagePoint(center);
    }
  }


  private void onActionPointerUp(MotionEvent e)
  {
  }

  private boolean mPinch = false;
  private boolean mPinching = false;
  private Point pinchCenter;
  private float pinchDistance;


  // TODO: Move pinch logic to SuperImageView.
  @Override
  public boolean onTouchEvent(MotionEvent e)
  {
    final boolean wasScrolling = mScrolling;
    final int action = e.getAction() & Reflect.ACTION_MASK();
    
    switch (action)
    {
      case MotionEvent.ACTION_DOWN:
        this.onActionDown(e);
        break;
      case MotionEvent.ACTION_MOVE:
        this.onActionMove(e);
        break;
      case MotionEvent.ACTION_UP:
        this.onActionUp(e);
        break;
    }

    if (action == Reflect.ACTION_POINTER_DOWN())
    {
      this.onActionPointerDown(e);
    }
    else if (action == Reflect.ACTION_POINTER_UP())
    {
      this.onActionPointerUp(e);
    }

    if (mPinching)
    {
      return true;
    }
    else
    {
      if (isComicLoaded() && (mScrolling || wasScrolling))
      {
        mScreen.scroll(e);
      }
      
      return mGestureDetector.onTouchEvent(e);
    }
  }


  private boolean action(String preferenceKey, String defaultValue)
  {
    return action(preferenceKey, defaultValue, null);
  }


  private boolean action(String preferenceKey, String defaultValue, Point p)
  {
    final String actionValue = preferences.getString(preferenceKey, defaultValue);
    return actionWithValue(actionValue, preferenceKey, p);
  }


  private boolean actionWithValue(String actionValue, String preferenceKey, Point p)
  {
    boolean action = false;

    // Actions that require a comic
    if (isComicLoaded())
    {
      final int scrollIncrement = (int)Float.parseFloat(preferences.getString("scroll_step",  Float.toString(Constants.DEFAULT_SCROLL_STEP)));

      if (Constants.ACTION_VALUE_PREVIOUS.equals(actionValue))
      {
        action = previous();
      }
      else if (Constants.ACTION_VALUE_PREVIOUS_SCREEN.equals(actionValue))
      {
        action = previousScreen();
      }
      else if (Constants.ACTION_VALUE_ZOOM_IN.equals(actionValue))
      {
        action = mScreen.zoom(1, p);
      }
      else if (Constants.ACTION_VALUE_ZOOM_OUT.equals(actionValue))
      {
        action = mScreen.zoom(-1, p);
      }
      else if (Constants.ACTION_VALUE_SCROLL_UP.equals(actionValue))
      {
        action = mScreen.scroll(0, -scrollIncrement);
      }
      else if (Constants.ACTION_VALUE_SCROLL_DOWN.equals(actionValue))
      {
        action = mScreen.scroll(0, scrollIncrement);
      }
      else if (Constants.ACTION_VALUE_SCROLL_LEFT.equals(actionValue))
      {
        action = mScreen.scroll(-scrollIncrement, 0);
      }
      else if (Constants.ACTION_VALUE_SCROLL_RIGHT.equals(actionValue))
      {
        action = mScreen.scroll(scrollIncrement, 0);
      }
      else if (Constants.ACTION_VALUE_FIRST.equals(actionValue))
      {
        action = first();
      }
      else if (Constants.ACTION_VALUE_LAST.equals(actionValue))
      {
        action = last();
      }
      else if (Constants.ACTION_VALUE_SCREEN_BROWSER.equals(actionValue))
      {
        startBrowseActivity();
        action = true;
      }
      else if (Constants.ACTION_VALUE_NEXT.equals(actionValue))
      {
        action = next();
      }
      else if (Constants.ACTION_VALUE_NEXT_SCREEN.equals(actionValue))
      {
        action = nextScreen();
      }
      else if (Constants.ACTION_VALUE_FIT_WIDTH.equals(actionValue))
      {
        action = mScreen.fitWidth();
      }
      else if (Constants.ACTION_VALUE_FIT_HEIGHT.equals(actionValue))
      {
        action = mScreen.fitHeight();
      }
      else if (Constants.ACTION_VALUE_FIT_SCREEN.equals(actionValue))
      {
        action = mScreen.fitScreen();
      }
      else if (Constants.ACTION_VALUE_ACTUAL_SIZE.equals(actionValue))
      {
        action = mScreen.actualSize();
      }
      else if (Constants.ACTION_VALUE_OCR.equals(actionValue))
      {
        this.showOcr();
        action = true;
      }
      else if (Constants.ACTION_VALUE_TOGGLE_PAGE_LAYOUT.equals(actionValue))
      {        
        this.mScreen.toggleTwoPageLayout();
        this.mScreen.goToCurrent();
        Editor editor = preferences.edit();
        editor.putBoolean(Constants.TWO_PAGE_LAYOUT_KEY, this.mScreen.getTwoPageLayout());
        editor.commit();
        action = true;
      }
    }

    // Actions that do not require a comic
    if (Constants.ACTION_VALUE_SETTINGS.equals(actionValue))
    {
      startSettingsActivity();
      action = true;
    }
    else if (Constants.ACTION_CLOSE.equals(actionValue))
    {
      close();
      action = true;
    }
    else if (Constants.ACTION_VALUE_SD_BROWSER.equals(actionValue))
    {
      startSDBrowserActivity();
      action = true;
    }
    else if (Constants.ACTION_VALUE_ROTATE.equals(actionValue))
    {
      rotate();
      action = true;
    }
    else if (Constants.ACTION_MENU.equals(actionValue))
    {
      showMenu();
      return true;
    }
    else if(Constants.ACTION_VALUE_TAKE_PHOTO.equals(actionValue))
    {
      takePhoto();
      action = true;
    }
    
    return action;
  }
  
  
  protected String getRelativePath()
  {
    return Constants.TEMP_PATH;
  }


  protected File createTempCameraFile()
  {
    File dir = new File(Environment.getExternalStorageDirectory(), Constants.CAMERA_PATH);
    dir.mkdirs();
    
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    String imageFileName = "camera_" + timeStamp + ".jpg";

    return new File(dir, imageFileName);
  }

  
  protected void takePhoto()
  {
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    
    // Ensure that there's a camera activity to handle the intent
    if (takePictureIntent.resolveActivity(getPackageManager()) != null)
    {
      // Create the File where the photo should go
      photoFile = null;
      
      try
      {
        photoFile = createTempCameraFile();
      }
      catch (Exception ex)
      {
        // Error occurred while creating the File
      }
      
      // Continue only if the File was successfully created
      if (photoFile != null)
      {
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
        startActivityForResult(takePictureIntent, Constants.REQUEST_TAKE_PHOTO);
      }
    }
  }


  private void setAs()
  {
    SetComicScreenAsTask task = new SetComicScreenAsTask(this, comic);
    task.execute(mScreen.getIndex());
  }


  private void close()
  {
    if (isComicLoaded())
    {
      removePreviousComic(true, true);
      mScreen.setVisibility(View.GONE);
      showRecentItems();
      preferencesController.savePreference(Constants.COMIC_PATH_KEY, null);
    }
    else
    {
      finish();
    }
  }


  private String describeOrientation(int orientation)
  {
    switch (orientation)
    {
      case Configuration.ORIENTATION_LANDSCAPE:
        return Constants.EVENT_VALUE_LANDSCAPE;
      case Configuration.ORIENTATION_PORTRAIT:
        return Constants.EVENT_VALUE_PORTRAIT;
      case Configuration.ORIENTATION_SQUARE:
        return Constants.EVENT_VALUE_SQUARE;
      default:
        return Constants.EVENT_VALUE_UNDEFINED;
    }
  }


  private ArrayList<File> findCandidates(File file)
  {
    File parent = file.getParentFile();

    String[] allContents = parent.list();
    TreeMap<String, File> aux = new TreeMap<String, File>();
    HashMap<String, Integer> supportedExtensions = Constants.getSupportedExtensions(this);
    
    if (allContents != null)
    {
      String path = parent.getPath();
      
      for (int i = 0; i < allContents.length; i++)
      {
        String contentName = allContents[i];
        String extension = FileUtils.getFileExtension(contentName);
        
        if (!net.robotmedia.acv.utils.FileUtils.isHidden(contentName)
            && supportedExtensions.containsKey(extension.toLowerCase()))
        {
          File contentFile = new File(path, contentName);
          aux.put(contentFile.getName().toLowerCase(), contentFile);
        }
      }
    }
    
    ArrayList<File> candidates = new ArrayList<File>();
    candidates.addAll(aux.values());
    return candidates;
  }


  private File findNextComic()
  {
    String comicPath = comic.getPath();
    File file = new File(comicPath);
    ArrayList<File> candidates = findCandidates(file);
    String fileName = file.getName().toLowerCase();
    boolean next = false;
    File nextComic = null;
    
    for (File candidate : candidates)
    {
      if (next)
      {
        nextComic = candidate;
        break;
      }
      else if (fileName.equals(candidate.getName().toLowerCase()))
      {
        next = true;
      }
    }
    
    return nextComic;
  }


  private File findPreviousComic()
  {
    String comicPath = comic.getPath();
    File file = new File(comicPath);
    ArrayList<File> candidates = findCandidates(file);
    String fileName = file.getName().toLowerCase();
    File previousComic = null;
    boolean previous = false;
    
    for (File candidate : candidates)
    {
      if (fileName.equals(candidate.getName().toLowerCase()))
      {
        previous = true;
        break;
      }
      else
      {
        previousComic = candidate;
      }
    }
    
    if (previous)
    {
      return previousComic;
    }
    else
    {
      return null;
    }
  }


  private boolean first()
  {
    return mScreen.goToScreen(0);
  }


  private boolean isComicLoaded()
  {
    return (comic != null && comic.getLength() > 0 && !comic.isError());
  }


  private boolean last()
  {
    return mScreen.goToScreen(comic.getLength() - 1);
  }


  private void loadComic(final String comicPath, final Intent intent)
  {
    final File file = new File(comicPath);
    int initialIndex = intent.getIntExtra(POSITION_EXTRA, 0);
    
    if (initialIndex == 0)
    {
      initialIndex = HistoryManager.getInstance(this).getBookmark(file);
    }
    
    loadComic(comicPath, initialIndex);
  }


  private void loadComic(final String comicPath)
  {
    final File file = new File(comicPath);
    final int initialIndex = HistoryManager.getInstance(this).getBookmark(file);
    loadComic(comicPath, initialIndex);
  }


  private void loadComic(final String comicPath, final int initialIndex)
  {
    final File file = new File(comicPath);
    
    if (file.exists())
    {
      mComicPath = comicPath;
      loadComicTask = new LoadComicTask();
      loadComicTask.initialIndex = initialIndex;
      loadComicTask.execute(comicPath);
    }
  }


  private boolean attemptToLoadComicFromViewIntent(Intent intent)
  {
    if (intent.getAction().equals(Intent.ACTION_VIEW))
    {
      final Uri uri = intent.getData();
      
      try
      {
        final File file = new File(new URI(uri.toString()));
        String comicPath = file.getAbsolutePath();
        
        if (comicPath != null)
        {
          loadComic(comicPath, intent);
          return true;
        }
      }
      catch (URISyntaxException e)
      {
        Log.w("attemptToLoadComicFromViewIntent", "Invalid intent data");
      }
    }
    
    return false;
  }


  private boolean next()
  {
    int index = mScreen.getIndex();
    int frameIndex = mScreen.getFrameIndex();
    
    if (comic.hasFrames(index) && ((frameIndex + 1) < comic.getFramesSize(index)))
    {
      return mScreen.next();
    }
    else
    {
      return nextScreen();
    }
  }


  private boolean nextScreen()
  {
    int index = mScreen.getIndex();
    
    if (index + 1 >= comic.getLength())
    { // Load next comic
      File next = findNextComic();
      
      if (next != null)
      {
        this.loadComic(next.getPath(), 0);
        return true;
      }
    }
    else
    {
      return mScreen.nextScreen();
    }
    
    return false;
  }


  private boolean previous()
  {
    int index = mScreen.getIndex();
    int frameIndex = mScreen.getFrameIndex();
    
    if (comic.hasFrames(index) && frameIndex > 0)
    {
      return mScreen.previous();
    }
    else
    {
      return previousScreen();
    }
  }


  private boolean previousScreen()
  {
    int index = mScreen.getIndex();
    
    if (index - 1 < 0)
    { // Load previous comic
      File previous = findPreviousComic();
      if (previous != null)
      {
        this.loadComic(previous.getPath(), 0);
        return true;
      }
    }
    else
    {
      return mScreen.previousScreen();
    }
    
    return false;
  }

  
  private void rotate()
  {
    int orientation = getResources().getConfiguration().orientation;
    int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    
    switch (orientation)
    {
      case Configuration.ORIENTATION_LANDSCAPE:
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        orientation = Configuration.ORIENTATION_PORTRAIT;
        break;
      case Configuration.ORIENTATION_PORTRAIT:
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        orientation = Configuration.ORIENTATION_LANDSCAPE;
        break;
    }
    
    Editor editor = preferences.edit();
    editor.putInt(Constants.ORIENTATION_KEY, orientation);
    editor.commit();
    requestedRotation = true;
    setRequestedOrientation(requestedOrientation);
  }


  /**
   * Shows the menu.
   */
  private void showMenu()
  {
    // Make sure that onPrepareOptionsMenu() will be called
    invalidateOptionsMenu();
    
    if (isHoneyComb() && !isIcecream())
    {
      showActionBar();
    }
    
    openOptionsMenu();
  }


  private void startBrowseActivity()
  {
    if (isComicLoaded())
    {
      final Intent intent = new Intent(this, BrowseActivity.class);
      intent.putExtra(BrowseActivity.POSITION_EXTRA, mScreen.getIndex());
      final String comicID = comic.getID();
      intent.putExtra(BrowseActivity.EXTRA_COMIC_ID, comicID);
      startActivityForResult(intent, Constants.SCREEN_BROWSER_CODE);
    }
  }


  private void startSDBrowserActivity()
  {
    Intent myIntent = new Intent(this, SDBrowserActivity.class);
    String comicsPath = preferences.getString(Constants.COMICS_PATH_KEY, Environment.getExternalStorageDirectory()
        .getAbsolutePath());
    myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
    startActivityForResult(myIntent, Constants.SD_BROWSER_CODE);
  }


  private void startSettingsActivity()
  {
    if (!isHoneyComb())
    {
      startActivityForResult(new Intent(this, SettingsActivityMobile.class), Constants.SETTINGS_CODE);
    }
    else
    {
      startActivityForResult(new Intent(this, SettingsActivityTablet.class), Constants.SETTINGS_CODE);
    }
  }


  private boolean startupOrientation(Bundle savedInstanceState)
  {
    boolean wasRequestedRotation = savedInstanceState != null ? savedInstanceState
        .getBoolean(Constants.REQUESTED_ROTATION_KEY) : false;
    if (!wasRequestedRotation)
    { // If the activity was not created because of a rotation request
      boolean sensor = preferences.getBoolean(Constants.AUTO_ROTATE_KEY, false);
      
      if (sensor)
      {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
      }
      else
      {
        int currentOrientation = getResources().getConfiguration().orientation;
        int lastOrientation = preferences.getInt(Constants.ORIENTATION_KEY, currentOrientation);
        int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        
        switch (lastOrientation)
        {
          case Configuration.ORIENTATION_LANDSCAPE:
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            break;
          case Configuration.ORIENTATION_PORTRAIT:
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            break;
        }
        setRequestedOrientation(requestedOrientation);
        return currentOrientation == lastOrientation;
      }
    }
    
    return true;
  }


  @Override
  protected Dialog onCreateDialog(int id)
  {
    switch (id)
    {
      case Constants.DIALOG_LOAD_ERROR:
        return dialogFactory.createLoadErrorDialog();
      case Constants.DIALOG_PAGE_ERROR:
        return dialogFactory.createPageErrorDialog();
      case Constants.DIALOG_FLIP_CONTROLS:
        return dialogFactory.createFlipControlsDialog();
    }
    return null;
  }


  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    destroyed = true;
        
    removePreviousComic(true, !requestedRotation);
    
    if (loadComicTask != null)
    {
      loadComicTask.cancel(true);
    }
    
    mScreen.destroy();
  }


  @Override
  protected void onSaveInstanceState(Bundle outState)
  {
    super.onSaveInstanceState(outState);
    
    if (isComicLoaded())
    {
      outState.putString(Constants.COMIC_PATH_KEY, comic.getPath());
    }
    
    outState.putBoolean(Constants.REQUESTED_ROTATION_KEY, requestedRotation);
  }


  protected void removePreviousComic(boolean emptyTemp, boolean emptyCamera)
  {
    // Free the memory of the current comic
    mScreen.recycleBitmaps();

    if (emptyTemp)
    {
      File tempDirectory = new File(Environment.getExternalStorageDirectory(), Constants.TEMP_PATH);
      
      if (tempDirectory.exists())
      {
        FileUtils.deleteDirectory(tempDirectory);
      }
    }
        
    if (emptyCamera)
    {
      File tempDirectory = new File(Environment.getExternalStorageDirectory(), Constants.CAMERA_PATH);
      
      if (tempDirectory.exists())
      {
        FileUtils.deleteDirectory(tempDirectory);
      }
    }
    
    if (comic != null)
    {
      comic.destroy();
      comic = null;
    }
  }


  public void onAnimationEnd(ComicView comicView)
  {
    this.adjustCornersVisibility(true);
  }


  public void onAnimationStart(ComicView comicView)
  {
    this.adjustCornersVisibility(false);
  }


  @Override
  public void onScreenChanged(int index)
  {
    if (comic != null)
    {
      final String path = comic.getPath();
      HistoryManager.getInstance(this).setBookmark(new File(path), index);
    }
    
    if(takingPhoto)
    {
      takingPhoto = false;
      showOcr();
    }
  }


  @Override
  protected boolean toggleControls()
  {
    boolean shown = super.toggleControls();
    
    if (shown)
    {
      showMenu();
    }
    
    return shown;
  }

  @Override
  @SuppressLint("NewApi")
  public boolean onGenericMotionEvent(MotionEvent event) 
  {
    if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER))
    {
      switch (event.getAction()) 
      {
        // Handle mouse wheel event to scroll comic up/down.
        case MotionEvent.ACTION_SCROLL:
        {
          final int scrollIncrement = (int)Float.parseFloat(preferences.getString("scroll_step",  Float.toString(Constants.DEFAULT_SCROLL_STEP)));
 
          if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f)
          {
            // If the left mouse button is held when the scroll is started
            if(event.getButtonState() == MotionEvent.BUTTON_PRIMARY)
            {
              Point mousePos = new Point(Math.round(event.getX()), Math.round(event.getY()));
              mScreen.zoom(-1, mousePos); // Zoom out
            }
            else
            {
              mScreen.scroll(0, scrollIncrement); // Scroll down
            }
          }
          else
          {
            // If the left mouse button is held when the scroll is started
            if(event.getButtonState() == MotionEvent.BUTTON_PRIMARY)
            {
              Point mousePos = new Point(Math.round(event.getX()), Math.round(event.getY()));
              mScreen.zoom(1, mousePos); // Zoom in
            }
            else
            { 
              if(this.isExperimentalMode())
              {
                Point mousePos = new Point(Math.round(event.getX()), Math.round(event.getY()));
                this.startTriggerCapture(mousePos);
              }
              else
              {
                mScreen.scroll(0, -scrollIncrement); // Scroll up  
              }
            }
          }

          return true;
        } 

        case MotionEvent.ACTION_HOVER_EXIT:
        {
          switch(event.getButtonState())
          {
            // Handle right mouse click
            case MotionEvent.BUTTON_SECONDARY:
            {
              if(this.ocrShown)
              {
                this.hideOcr();
              }
              else
              {
                this.showOcr();
              }
              
              return true;
            }
            
            // Handle middle mouse click
            case MotionEvent.BUTTON_TERTIARY:
            {
              this.nextScreen();
              return true;
            } 
          }
        }
      }
    }
    
    return super.onGenericMotionEvent(event);
  }
  
  
  /** If experimental mode is enabled */
  private boolean isExperimentalMode()
  {
    float scrollIncrement = Float.parseFloat(preferences.getString("scroll_step",  Float.toString(Constants.DEFAULT_SCROLL_STEP)));
    return (Math.abs((scrollIncrement - (int)scrollIncrement) - 0.496) < 0.0001);
  }


  private void showRecentItems()
  {
    mRecentItemsListAdapter.refresh();
    mRecentItems.setVisibility(View.VISIBLE);
    mButtonsContainer.setVisibility(View.INVISIBLE);
  }


  private void hideRecentItems()
  {
    mRecentItems.setVisibility(View.GONE);
    mButtonsContainer.setVisibility(View.VISIBLE);
  }
  
  
  /** Start a trigger OCR capture */
  private void startTriggerCapture(Point pt)
  {    
    this.ocrLayout.setComicView(mScreen);
    this.ocrLayout.startTriggerCapture(this, pt);
    
    this.ocrLayout.setVisibility(View.VISIBLE);
    this.mButtonsContainer.setVisibility(View.INVISIBLE);
    this.ocrShown = true;
  }
  
  /** Show the OCR view. */
  private void showOcr()
  {    
    this.ocrLayout.setComicView(mScreen);
    this.ocrLayout.start(this);

    this.ocrLayout.setVisibility(View.VISIBLE);
    this.mButtonsContainer.setVisibility(View.INVISIBLE);
    this.ocrShown = true;
  }
  
  
  /** Hide the OCR view. */
  public void hideOcr()
  {
    this.ocrLayout.stop();
    this.ocrLayout.setVisibility(View.GONE);
    this.mButtonsContainer.setVisibility(View.VISIBLE);
    this.ocrShown = false;
  }
    
  /** Handle the OCR nudge up button click event. */
  public void btnOcrUp_Clicked(View view)
  {
    this.ocrLayout.nudgeCaptureBox(NudgeDirection.UP);
  }
  

  /** Handle the OCR nudge down button click event. */
  public void btnOcrDown_Clicked(View view)
  {
    this.ocrLayout.nudgeCaptureBox(NudgeDirection.DOWN);
  }

  
  /** Handle the OCR nudge left button click event. */
  public void btnOcrLeft_Clicked(View view)
  {
    this.ocrLayout.nudgeCaptureBox(NudgeDirection.LEFT);
  }
  
  
  /** Handle the OCR nudge right button click event. */
  public void btnOcrRight_Clicked(View view)
  {
    this.ocrLayout.nudgeCaptureBox(NudgeDirection.RIGHT);
  }
 
  
  /** Handle the OCR lookup Next click event. */
  public void btnOcrLookupNext_Clicked(View view)
  {
    this.ocrLayout.lookupNextWord();
  }
  
  /** Handle the OCR lookup Prev click event. */
  public void btnOcrLookupPrev_Clicked(View view)
  {
    this.ocrLayout.lookupPrevWord();
  }
  
  
  /** Handle the OCR hide/show menu item click event. */
  public boolean menuItemOcrToggleVisi_Clicked(MenuItem menuItem)
  {
    this.ocrLayout.toggleHideGui();
    
    return true;
  }

  
  /** Handle the OCR text orientation button click event. */
  public void btnOcrTextOrientation_Clicked(View view)
  {
    this.ocrLayout.switchTextOrientation();
  }
  
  
  /** Handle the OCR text orientation menu item click event. */
  public boolean menuItemOcrTextOrientation_Clicked(MenuItem menuItem)
  {
    this.ocrLayout.switchTextOrientation();
    
    return true;
  }
  
  
  /** Handle the OCR swap nudge corner button click event. */
  public void btnOcrSwapNudgeCorner_Clicked(View view)
  {
    this.ocrLayout.swapNudgeCorner();
  }
  
  
  /** Handle the OCR Send button click event. */
  public void btnOcrSend_Clicked(View view)
  {
    String sendAction = preferences.getString(Constants.OCR_SETTING_SEND_ACTION_KEY, Constants.OCR_SETTING_SEND_ACTION_SHOW_LIST_VALUE);
    
    if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_SHOW_LIST_VALUE))
    {
      this.ocrLayout.showOcrSendDialog();
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_CLIPBOARD_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_clipboard));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_OCR_CORRECTION_EDITOR_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_error_correction_editor));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_AEDICT_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_word_list_save_file));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_AEDICT_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_aedict));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_ANKI_DROID_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_ankidroid));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_EIJIRO_JE_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_eijiro_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_GOO_JE_JJ_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_goo_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_SANSEIDO_JJ_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_sanseido_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_YAHOO_JE_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_yahoo_je_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_YAHOO_JJ_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_yahoo_jj_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_GOOGLE_WEB_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_google_web));
    }
    else if(sendAction.equals(Constants.OCR_SETTING_SEND_ACTION_GOOGLE_IMAGES_VALUE))
    {
      this.ocrLayout.performSendAction(getResources().getString(R.string.ocr_send_dialog_opt_google_images_web));
    }
  }
  
  
  /** Handle the OCR Send menu item click event. */
  public boolean menuItemOcrSend_Clicked(MenuItem menuItem)
  {
    this.ocrLayout.showOcrSendDialog();
    
    return true;
  }
  
  /** Handle the OCR Lookup Next menu item click event. */
  public boolean menuItemOcrLookupNext_Clicked(MenuItem menuItem)
  {
    this.ocrLayout.lookupNextWord();
    
    return true;
  }
  
  /** Handle the OCR Lookup Previous menu item click event. */
  public boolean menuItemOcrLookupPrev_Clicked(MenuItem menuItem)
  {
    this.ocrLayout.lookupPrevWord();
    
    return true;
  }
  
  
  /** Handle the OCR Menu button click event. */
  public void btnOcrMenu_Clicked(View view)
  {
    this.showMenu();
  }
  
  
  /** Handle the OCR Exit button click event. */
  public void btnOcrExit_Clicked(View view)
  {
    this.hideOcr();
  }
  
}
