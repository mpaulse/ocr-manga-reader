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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.GestureDetector.OnGestureListener;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cb4960.dic.Dic;
import com.cb4960.dic.DicEdict;
import com.cb4960.dic.DicEpwing;
import com.cb4960.dic.DicEpwingRaw;
import com.cb4960.dic.DicKanji;
import com.cb4960.dic.DicNames;
import com.cb4960.dic.Entry;
import com.cb4960.dic.Example;
import com.cb4960.dic.FineTune;
import com.cb4960.dic.Frequency;
import com.cb4960.dic.UtilsCommon;
import com.cb4960.dic.UtilsFormatting;
import com.cb4960.dic.UtilsLang;
import com.cb4960.dic.WordSet;
import com.cb4960.ocrmr.R;
import com.googlecode.leptonica.android.Binarize;
import com.googlecode.leptonica.android.Clip;
import com.googlecode.leptonica.android.Convert;
import com.googlecode.leptonica.android.Convolve;
import com.googlecode.leptonica.android.Enhance;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Scale;
import com.googlecode.leptonica.android.Seedfill;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.ichi2.anki.api.AddContentApi;

import net.robotmedia.acv.Constants;
import net.robotmedia.acv.logic.PreferencesController;
import net.robotmedia.acv.ui.ComicViewerActivity;
import net.robotmedia.acv.utils.AnkiUtils;
import net.robotmedia.acv.utils.BoundingTextRect;
import net.robotmedia.acv.utils.FileUtils;
import net.robotmedia.acv.utils.Furigana;
import net.robotmedia.acv.utils.IntentUtils;
import net.robotmedia.acv.utils.LeptUtils;
import net.robotmedia.acv.utils.MathUtils;
import net.robotmedia.acv.utils.ShellUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

/** Container for all OCR related views. */
public class OcrLayout extends RelativeLayout implements OnGestureListener
{ 
  /** Direction to nudge the capture box. */
  public enum NudgeDirection
  {
    UP,
    DOWN,
    LEFT,
    RIGHT
  }
  
  /** Part of capture box that is being dragged. */
  private enum DragRegion
  {
    NONE,
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    MIDDLE,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT
  }
  
  /** Screen quadrant. */
  private enum Quadrant
  {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
  }
  
  /** Location of the dictionary view. */
  private enum DicViewLocation
  {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT
  }
  
  /** State that described current capture box manipulation. */
  private enum CaptureState
  {
    SET_TOP_LEFT,
    SET_BOTTOM_RIGHT,
    DRAG
  }
  
  /** Amount to scale the capture before passing it to the OCR engine. */
  final public static float SCALE_FACTOR = 3.5f;
  
  /** Tag to use in log. */
  final private String LOG_TAG = "OcrLayout";
  
  // Orientation of the text that is being captured
  final private int TEXT_ORIENTATION_VERTICAL = 0;
  final private int TEXT_ORIENTATION_HORIZONTAL = 1;
  final private int TEXT_ORIENTATION_AUTO = 2;
  
  // Corner of capture box that nudge buttons will adjust
  final private int NUDGE_CORNER_TOP_LEFT = 0;
  final private int NUDGE_CORNER_BOTTOM_RIGHT = 1;

  public static final int ANKI_RW_PERM_REQ_CODE = 4261;
         
  /** View that contains the comic image. */
  private ComicView comicView = null;
  
  /** View that hosts anything that needs to be drawn, such as the capture box. */
  private OcrView ocrView = null;
  
  /** Capture box. */
  private Rect captureBox = null;
  
  /** Represents the top-left point of the foreground clip that is generated during pre-processing. */
  private Point clipOffset = new Point(0, 0);
  
  /** Previous capture box. Set in the capture timer.
   *  Compared against captureBox to determine if screen capture and OCR should take place. */
  private Rect lastCaptureBox = new Rect(0, 0, 1, 1);
  
  /** State of the capture. */
  private CaptureState captureState = CaptureState.SET_TOP_LEFT;
  
  /** Which region is being dragged by the user? */
  private DragRegion dragRegion = DragRegion.NONE;
  
  /** Last capture of the _entire_ comicView screen. */
  private Bitmap lastScreenshot = null;

  /** Canvas that will draw the ComicView onto lastScreenshot. */
  private Canvas captureCanvas = null;
  
  /** Last captured screen area used as input into the OCR engine. */
  private Pix lastCapture = null;
  
  /** Usable screen width. */
  private int screenWidth = 0;
  
  /** Usable screen height (excludes the navigation bar if < Android 4.4). */
  private int screenHeight = 0;
  
  /** Tesseract OCR library. */
  private static TessBaseAPI tess = null;
    
  /** OCR'd text from the latest capture. */
  private String lastOcrText = ""; 
  
  /** Used for next/previous navigation. Stores the indices of words in lastOcrText. */
  private Stack<Integer> lookupWordIdxStack = new Stack<Integer>();
  
  /** Bounding boxes around the OCR'd text */
  private List<Rect> boundingBoxes;
  
  /** Used to schedule screen captures and OCR. */
  private Timer captureTimer;
  
  /** The task that is run by captureTimer. */
  private UpdateCaptureTask updateCaptureTask;
  
  /** The last time that updateCaptureTask checked to see if it should capture and OCR. */
  private Calendar lastCaptureTime = null;
  
  /** The last time that the capture box was adjusted */
  private Calendar lastAdjustment = null;
    
  /** EDICT dictionary */
  private static DicEdict dicEdict = new DicEdict();
  
  /** Names dictionary */
  private static DicNames dicNames = new DicNames();
  
  /** Kanji dictionary */
  private static DicKanji dicKanji = new DicKanji();
  
  /** List of dictionary entries */
  private List<Entry> lastEntryList = null;
  
  /** WebView that will contain the dictionary lookup. */
  private WebView dicView = null;
  
  /** Set to force a capture and OCR by the capture timer task. */
  private boolean forceUpdate = false;
    
  /** Corner of capture box that the nudge buttons will adjust. */
  private int nudgeCorner = this.NUDGE_CORNER_BOTTOM_RIGHT;
  
  /** True = GUI is currently hidden (except for the Show/Hide button). */
  private boolean hideGui = false;
  
  /** Used to get/store settings */
  private PreferencesController preferencesController = null;
  
  /** Context passed in by the constructor. */
  private Context context = null;
  
  /** Orientation of the text. */
  private int textOrientation = this.TEXT_ORIENTATION_VERTICAL;
  
  /** Progress dialog used when copying database assets.*/
  private ProgressDialog assetCopyProgressDialog = null;
  
  /** Tesseract database file */
  private File tesseractDbFile = null;
  
  /** Directory that contains the Tesseract database */
  private File tesseractDbDir = null;
  
  /** Edict database file */
  private File edictDbFile = null;
  
  /** Names database file */
  private File namesDbFile = null;
  
  /** Kanji database file */
  private File kanjiDbFile = null;
  
  /** Kanji definition format file */
  private File kanjiDefFormatFile = null;
  
  /** De-inflection rules file */
  private File deinflectionDbFile = null;
  
  /** Substitutions file */
  private File substitutionsDbFile = null;
  
  /** From->To substitutions found in the substitutions file */
  private List<Pair<String, String>> substitutionsList = null;
  
  /** Frequency database file */
  private File freqDbFile = null;
  
  /** Frequency database */
  private static Frequency freqDb = new Frequency();
  
  /** Word set for known words */
  private WordSet wordSetKnown = null;

  /** Word set to todo words */
  private WordSet wordSetTodo = null;
  
  /** Reference to the comic viewer activity */
  private ComicViewerActivity comicViewerActivity = null;
  
  /** Has the OCR view been started? */
  private boolean ocrStarted = false;
  
  /** Original width of nudge buttons */
  private int origNudgeButtonsWidth = 40;
  
  /** Original height of nudge buttons */
  private int origNudgeButtonsHeight = 40;
  
  /** True = Trigger capture in progress */
  private boolean isTriggerCapture = false;

  
  // GUI references
  private Button btnSwapNudgeCorner = null;
  private Button btnUp = null;
  private Button btnDown = null;
  private Button btnLeft = null;
  private Button btnRight = null;
  private Button btnTextOrientation = null;
  private Button btnSend = null;
  private Button btnLookupNext = null;
  private Button btnLookupPrev = null;
  private Button btnMenu = null;
  private Button btnExit = null;
  private TextView textViewStartMsg = null;
  
  
  // Settings
  private int ocrSettingsDictBackgroundColor = Constants.DEFAULT_OCR_SETTINGS_DICT_BACKGROUND_COLOR;
  private int ocrSettingsDictExpressionColor = Constants.DEFAULT_OCR_SETTINGS_DICT_EXPRESSION_COLOR;
  private int ocrSettingsDictReadingColor = Constants.DEFAULT_OCR_SETTINGS_DICT_READING_COLOR;
  private int ocrSettingsDictDefinitionColor = Constants.DEFAULT_OCR_SETTINGS_DICT_DEFINITION_COLOR;
  private int ocrSettingsDictConjugationColor = Constants.DEFAULT_OCR_SETTINGS_DICT_CONJUGATION_COLOR;
  private int ocrSettingsDictSubDefColor = Constants.DEFAULT_OCR_SETTINGS_DICT_SUB_DEF_COLOR;
  private int ocrSettingsDictExamplePrependColor = Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_PREPEND_COLOR;
  private int ocrSettingsDictExampleJapColor = Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_JAP_COLOR;
  private int ocrSettingsDictExampleEngColor = Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_ENG_COLOR;
  private int ocrSettingsDictNameColor = Constants.DEFAULT_OCR_SETTINGS_DICT_NAME_COLOR;
  private int ocrSettingsDictSeparatorColor = Constants.DEFAULT_OCR_SETTINGS_DICT_SEPARATOR_COLOR;
  private int ocrSettingsDictOcrTextColor = Constants.DEFAULT_OCR_SETTINGS_DICT_OCR_TEXT_COLOR;
  private int ocrSettingsCaptureBoxColor = Constants.DEFAULT_OCR_SETTINGS_CAPTURE_BOX_COLOR;
  private int ocrSettingsBoundingBoxColor = Constants.DEFAULT_OCR_SETTINGS_BOUNDING_BOX_COLOR;
  private int ocrSettingsFreqVeryCommonColor = Constants.DEFAULT_OCR_SETTINGS_FREQ_VERY_COMMON_COLOR;
  private int ocrSettingsFreqCommonColor = Constants.DEFAULT_OCR_SETTINGS_FREQ_COMMON_COLOR;
  private int ocrSettingsFreqUncommonColor = Constants.DEFAULT_OCR_SETTINGS_FREQ_UNCOMMON_COLOR;
  private int ocrSettingsFreqRareColor = Constants.DEFAULT_OCR_SETTINGS_FREQ_RARE_COLOR;
  private int ocrSettingsWordHighlightColor = Constants.DEFAULT_OCR_SETTINGS_WORD_HIGHLIGHT_COLOR;
  private int ocrSettingsKnownWordColor = Constants.DEFAULT_OCR_SETTINGS_KNOWN_WORD_COLOR;

  
  private boolean ocrSettingsShowBoundingBoxes = Constants.DEFAULT_OCR_SETTINGS_SHOW_BOUNDING_BOXES;
  private boolean ocrSettingsSimplifiedLayoutPortrait = Constants.DEFAULT_OCR_SETTINGS_SIMPLIFIED_LAYOUT_PORTRAIT;
  private boolean ocrSettingsSimplifiedLayoutLandscape = Constants.DEFAULT_OCR_SETTINGS_SIMPLIFIED_LAYOUT_LANDSCAPE;
  private boolean ocrSettingsLargeNudgeButtons = Constants.DEFAULT_OCR_SETTINGS_LARGE_NUDGE_BUTTONS;
  private boolean ocrSettingsShowTextOrientationButton = Constants.DEFAULT_OCR_SETTINGS_SHOW_TEXT_ORIENTATION_BUTTON;
  private boolean ocrSettingsShowNudgeButtons = Constants.DEFAULT_OCR_SETTINGS_SHOW_NUDGE_BUTTONS;
  private boolean ocrSettingsShowSendButton = Constants.DEFAULT_OCR_SETTINGS_SHOW_SEND_BUTTON;
  private boolean ocrSettingsShowLookupNextButton = Constants.DEFAULT_OCR_SETTINGS_SHOW_LOOKUP_NEXT_BUTTON;
  private boolean ocrSettingsShowLookupPrevButton = Constants.DEFAULT_OCR_SETTINGS_SHOW_LOOKUP_PREV_BUTTON;

  private boolean ocrSettingsEdictCompactDefinitions = Constants.DEFAULT_OCR_SETTINGS_EDICT_COMPACT_DEFINITIONS;
  
  private String ocrSettingsEpwingDic1 = "";
  private String ocrSettingsEpwingDic2 = "";
  private String ocrSettingsEpwingDic3 = "";
  private String ocrSettingsEpwingDic4 = "";
  
  private boolean ocrSettingsEpwingCompactDefinitions = Constants.DEFAULT_OCR_SETTINGS_EPWING_COMPACT_DEFINITIONS;
  private int     ocrSettingsEpwingMaxDefLines        = Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_DEF_LINES;
  private boolean ocrSettingsEpwingParse              = Constants.DEFAULT_OCR_SETTINGS_EPWING_PARSE;          
  private boolean ocrSettingsEpwingShowExamples       = Constants.DEFAULT_OCR_SETTINGS_EPWING_SHOW_EXAMPLES;
  private int     ocrSettingsEpwingMaxExamples        = Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_EXAMPLES;
  private boolean ocrSettingsEpwingCompactExamples    = Constants.DEFAULT_OCR_SETTINGS_EPWING_COMPACT_EXAMPLES;
  private boolean ocrSettingsEpwingStripExamplesFromDefs = Constants.DEFAULT_OCR_SETTINGS_EPWING_STRIP_EXAMPLES_FROM_DEFS;

  private boolean ocrSettingsForceBorder = Constants.DEFAULT_OCR_SETTINGS_FORCE_BORDER;
  private boolean ocrSettingsShowFrequency = Constants.DEFAULT_OCR_SETTINGS_SHOW_FREQUENCY;
  private String ocrSettingsWordListSaveFilePath = Constants.DEFAULT_OCR_SETTINGS_WORD_LIST_SAVE_FILE_PATH;
  private String ocrSettingsWordListSaveFileFormat = Constants.DEFAULT_OCR_SETTINGS_WORD_LIST_SAVE_FILE_FORMAT;


  public OcrLayout(Context context)
  {
    super(context);
    this.init(context);
  }
  

  /** Constructor called by Android system at initialization. Also called when changing screen orientation. */
  public OcrLayout(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    this.init(context);
  }
  
  
  /** Initialization routine called by the constructor. */
  private void init(Context context)
  {
    // Exit if in the Eclipse graphical editor
    if(this.isInEditMode())
    {
      return;
    }
        
    this.context = context;
    
    this.readOcrSettings(context);

    this.determineScreenDimensions(context);
    
    File externalDir = this.context.getExternalFilesDir(null);
    this.tesseractDbDir = new File(externalDir.toString() + Constants.TESSERACT_ROOT_DIR);
    this.tesseractDbFile = new File(externalDir.toString() + Constants.TESSDATA_DIR, Constants.TESSERACT_DB_FILENAME);
    this.edictDbFile = new File(externalDir, Constants.EDICT_DB_FILENAME);
    this.namesDbFile = new File(externalDir, Constants.NAMES_DB_FILENAME);
    this.kanjiDbFile = new File(externalDir, Constants.KANJI_DB_FILENAME);
    this.kanjiDefFormatFile = new File(externalDir, Constants.KANJI_DEF_FORMAT_FILENAME);
    this.deinflectionDbFile = new File(externalDir, Constants.DEINFLECTION_DB_FILENAME);
    this.substitutionsDbFile = new File(externalDir, Constants.SUBSTITUTIONS_DB_FILENAME);
    this.freqDbFile = new File(externalDir, Constants.FREQ_DB_FILENAME);
    
    this.wordSetKnown = new WordSet(new File(externalDir, Constants.KNOWN_WORDS_FILENAME));
    this.wordSetTodo = new WordSet(new File(externalDir, Constants.TODO_WORDS_FILENAME));
    
    // Add OCR view
    this.ocrView = new OcrView(context);    
    this.addView(this.ocrView);
    
    // Add dictionary view
    this.dicView = new WebView(context);
    this.dicView.setBackgroundColor(this.ocrSettingsDictBackgroundColor); // Make the background translucent.
    this.dicView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null);
    this.dicView.setVisibility(GONE);
    this.addView(this.dicView);
    
    // Set the path to eplkup
    ContextWrapper cw = new ContextWrapper(this.context);
    
    // Android 5.0 requires executables to be compiled with PIE (Position Independent Executable) support for security
    // reasons. However, PIE is not supported before Android 4.1, which is why we have 2 executables, one with
    // PIE and one without PIE.
    if (android.os.Build.VERSION.SDK_INT >= 20)
    {
      DicEpwing.setEplkupExe(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_FILENAME);
    }
    else
    {
      DicEpwing.setEplkupExe(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_NON_PIE_FILENAME);
    }
  }

  
  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    
    this.btnSwapNudgeCorner = (Button)findViewById(R.id.btn_ocr_swap_nudge_corner);
    this.btnUp = (Button)findViewById(R.id.btn_ocr_up);
    this.btnDown = (Button)findViewById(R.id.btn_ocr_down);
    this.btnLeft = (Button)findViewById(R.id.btn_ocr_left);
    this.btnRight = (Button)findViewById(R.id.btn_ocr_right);
    this.btnTextOrientation = (Button)findViewById(R.id.btn_ocr_text_orientation);
    this.btnSend =  (Button)findViewById(R.id.btn_ocr_send);
    this.btnLookupNext = (Button)findViewById(R.id.btn_ocr_lookup_next);
    this.btnLookupPrev = (Button)findViewById(R.id.btn_ocr_lookup_prev);
    this.btnMenu = (Button)findViewById(R.id.btn_ocr_menu);
    this.btnExit = (Button)findViewById(R.id.btn_ocr_exit);
    this.textViewStartMsg = (TextView)findViewById(R.id.btn_ocr_start_msg);
    
    // Store the original size of the nudge buttons
    LayoutParams params = (LayoutParams)btnSwapNudgeCorner.getLayoutParams(); 
    this.origNudgeButtonsWidth = params.width;
    this.origNudgeButtonsHeight = params.height;
  }
  
  
  /** Set size of nudge buttons based on user settings. */
  private void setNudgeButtonSize()
  {
    final float LARGE_SIZE_FACTOR = 1.3f;
    
    LayoutParams params;
    
    int newWidth = this.origNudgeButtonsWidth;
    int newHeight = this.origNudgeButtonsHeight;
    
    if(this.ocrSettingsLargeNudgeButtons)
    {
      newWidth *= LARGE_SIZE_FACTOR;
      newHeight *= LARGE_SIZE_FACTOR;
    }
    
    params = (LayoutParams)btnSwapNudgeCorner.getLayoutParams(); 
    params.width = newWidth;
    params.height = newHeight;
    this.btnSwapNudgeCorner.setLayoutParams(params);
    
    params = (LayoutParams)btnUp.getLayoutParams(); 
    params.width = newWidth;
    params.height = newHeight;
    this.btnUp.setLayoutParams(params);
    
    params = (LayoutParams)btnDown.getLayoutParams(); 
    params.width = newWidth;
    params.height = newHeight;
    this.btnDown.setLayoutParams(params);
    
    params = (LayoutParams)btnLeft.getLayoutParams(); 
    params.width = newWidth;
    params.height = newHeight;
    this.btnLeft.setLayoutParams(params);
    
    params = (LayoutParams)btnRight.getLayoutParams(); 
    params.width = newWidth;
    params.height = newHeight;
    this.btnRight.setLayoutParams(params);
  }
  

  /** Update properties for the text orientation menu item */
  public void updateMenuItemTextOrientation(MenuItem menuItem)
  {    
    if(this.textOrientation == this.TEXT_ORIENTATION_HORIZONTAL)
    { 
      menuItem.setTitle(getResources().getString(R.string.menu_item_ocr_text_orientation_horizontal));
    }
    else if(this.textOrientation == this.TEXT_ORIENTATION_VERTICAL)
    {
      menuItem.setTitle(getResources().getString(R.string.menu_item_ocr_text_orientation_vertical));
    }
    else // TEXT_ORIENTATION_AUTO
    {
      menuItem.setTitle(getResources().getString(R.string.menu_item_ocr_text_orientation_auto));
    }
  }

  
  /** Update properties for the send to menu item */
  public void updateMenuItemSend(MenuItem menuItem)
  {
    // No properties set at present
  }
  
  
  /** Show the OCR interface that appears after user has started to draw a box around text */
  public void showOcrInterface()
  {
    // Turn on GUI elements that depend on the existence of the capture box
    if(this.showSimplifiedInterface())
    {
      this.btnMenu.setVisibility(VISIBLE);
      this.btnLookupNext.setVisibility(GONE);
      this.btnLookupPrev.setVisibility(GONE);
    }
    else // Full interface
    {
      this.btnMenu.setVisibility(GONE);
      
      if(this.ocrSettingsShowLookupNextButton)
      {
        this.btnLookupNext.setVisibility(VISIBLE);
      }
      else
      {
        this.btnLookupNext.setVisibility(INVISIBLE);
      }
      
      if(this.ocrSettingsShowLookupPrevButton)
      {
        this.btnLookupPrev.setVisibility(VISIBLE);
      }
      else
      {
        this.btnLookupPrev.setVisibility(INVISIBLE);
      }
    }
    
    this.btnExit.setVisibility(VISIBLE);
    this.setGuiButtonVisi(VISIBLE);
    this.dicView.setVisibility(VISIBLE);
   
    // Turn off initial message
    this.textViewStartMsg.setVisibility(GONE);
  }
  
  
  // For OnGestureListener. Called when user first touches the screen (but not as they are moving their finger)
  @Override
  public boolean onDown(MotionEvent e)
  {
    int x = (int)e.getX();
    int y = (int)e.getY();
    
    if(this.captureState == CaptureState.SET_TOP_LEFT)
    {
      // Set top-left corner of capture box to appear where the user touched.
      this.captureBox = new Rect(x, y, x + 1, y + 1);
      this.ocrView.setCaptureBox(this.captureBox);
      
      this.showOcrInterface();
      
      this.captureState = CaptureState.SET_BOTTOM_RIGHT;
      
      return true;
    }
    else if((captureState == CaptureState.SET_BOTTOM_RIGHT) || (captureState == CaptureState.DRAG))
    {
      this.dragRegion = this.determineDragRegion(x, y);
      this.captureState = CaptureState.DRAG;
      
      return true;
    }
    
    return false;
  }

  

  // For OnGestureListener.
  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
  {
    return false;
  }


  // For OnGestureListener.
  @Override
  public void onLongPress(MotionEvent e)
  {

  }

  
  // For OnGestureListener. Called after onDown() when the user moves finger on screen. 
  @Override
  public boolean onScroll(MotionEvent downEvent, MotionEvent dragEvent, float distanceX, float distanceY)
  {
    boolean handled = false;
    
    if(this.captureState == CaptureState.SET_BOTTOM_RIGHT)
    {
      this.captureBox.right -= (int)distanceX;
      this.captureBox.bottom -= (int)distanceY;
      this.ocrView.setCaptureBox(this.captureBox);
      
      handled = true;
    }
    else if(this.captureState == CaptureState.DRAG)
    {
      this.adjustCaptureBox(this.dragRegion, distanceX, distanceY);
      
      handled = true;
    }
    
    if(handled)
    {
      this.updateDicViewLocation();
    }
    
    return handled;
  }

  
  // For OnGestureListener.
  @Override
  public void onShowPress(MotionEvent e)
  {
    
  }

  
  // For OnGestureListener.
  @Override
  public boolean onSingleTapUp(MotionEvent e)
  {
    // If the user has decided to tap the text instead of drawing a box around it, perform a trigger capture
    if((this.captureState == CaptureState.SET_BOTTOM_RIGHT || captureState == CaptureState.DRAG) && (this.captureTimer != null))
    {
      this.createTriggerCaptureBox(new Point((int)e.getX(), (int)e.getY()));
      this.updateDicViewLocation();
      this.ocrView.setCaptureBox(this.captureBox);
      this.forceCaptureUpdate();
           
      this.showOcrInterface();
      
      this.captureState = CaptureState.DRAG;
    } 
    
    return true;
  }

    
  /** Provide the ComicView object to get screenshot from. */
  public void setComicView(ComicView comicView)
  {
    this.comicView = comicView;
  }
  
  
  /** Updates screenWidth and screenHeight. */
  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  private void determineScreenDimensions(Context context)
  {
    WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    
    if (android.os.Build.VERSION.SDK_INT >= 19) 
    {
      // Get the total size of the screen (because on android 4.4 and
      // above we use immersive mode to hide the navigation bar).
      DisplayMetrics metrics = new DisplayMetrics();
      wm.getDefaultDisplay().getRealMetrics(metrics);
      
      this.screenWidth = metrics.widthPixels;
      this.screenHeight = metrics.heightPixels;
    }
    else if (android.os.Build.VERSION.SDK_INT >= 13)
    {
      // Get the size of the screen minus the height of the navigation bar
      Point size = new Point();
      display.getSize(size);
      
      this.screenWidth = size.x;
      this.screenHeight = size.y;
    }
    else
    {
      // Get the size of the screen minus the height of the navigation bar (old method)
      this.screenWidth = display.getWidth();  // deprecated
      this.screenHeight = display.getHeight();  // deprecated
    }
  }
  
  
  /** Should the simplified interface be shown? */
  private boolean showSimplifiedInterface()
  {
    boolean simplified = false;
    int screenOrientation = this.context.getResources().getConfiguration().orientation;
    
    if(screenOrientation == Configuration.ORIENTATION_PORTRAIT)
    {
      if(this.ocrSettingsSimplifiedLayoutPortrait)
      {
        simplified = true;
      }
    }
    else // ORIENTATION_LANDSCAPE
    {
      if(this.ocrSettingsSimplifiedLayoutLandscape)
      {
        simplified = true;
      }
    }
    
    return simplified;
  }
  
  
  /** Returns true is text capture box orientation is vertical. */
  private boolean isOrientationVertical(Rect captureBox)
  {
    if(this.textOrientation == this.TEXT_ORIENTATION_AUTO)
    {
      double aspectRatio = captureBox.width() / (double)captureBox.height();

      if(aspectRatio < 2.0)
      {
        return true;
      }
      else
      {
        return false;
      }
    }
    else
    {
      return (this.textOrientation == this.TEXT_ORIENTATION_VERTICAL);
    }
  }
  
  
  /** Set Tesseract text orientation */
  private void setOcrOrientation(Rect captureBox)
  {
    int orientation = this.textOrientation;
    
    if(orientation == this.TEXT_ORIENTATION_AUTO)
    {
      if(this.isOrientationVertical(captureBox))
      {
        orientation = this.TEXT_ORIENTATION_VERTICAL;
      }
      else
      {
        orientation = this.TEXT_ORIENTATION_HORIZONTAL;
      }
    }
    
    if(orientation == this.TEXT_ORIENTATION_HORIZONTAL)
    {
      OcrLayout.tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
    }
    else
    {
      OcrLayout.tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT);
    }
  }
  
  
  /** OCR lastCapture and update lastOcrText. The curCaptureBox argument is just used so that we can
      exit early if the user adjusts the capture box. */
  private boolean ocrCapture(Rect curCaptureBox)
  {
    if(this.lastCapture != null)
    {
      try
      {
        setOcrOrientation(curCaptureBox);
        
        // Give the captured bitmap to Tesseract
        OcrLayout.tess.setImage(this.lastCapture);
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          OcrLayout.tess.clear();
          return false;
        }
        
        // Get the bounding rectangles
        this.boundingBoxes = OcrLayout.tess.getTextlines().getBoxRects();
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          OcrLayout.tess.clear();
          return false;
        }

        this.lastOcrText = OcrLayout.tess.getUTF8Text().replaceAll("\n", "");
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          OcrLayout.tess.clear();
          return false;
        }
        
        // Clears Tesseract's image and result data, but not the recognition data
        OcrLayout.tess.clear();
              
        // Correct common OCR mistakes
        this.lastOcrText = this.performOcrTextSubstitutions(this.lastOcrText);
        
        // Reset the prev/next history
        this.lookupWordIdxStack = new Stack<Integer>();
        this.lookupWordIdxStack.push(0);
      }
      catch(Exception e)
      {
        Log.e(LOG_TAG, "Exception in ocrCapture()! " + e);
        return false;
      }
    }
    
    return true;
  }
  

  /** Capture screen to the lastCapture bitmap. */
  private boolean captureScreen(Rect curCaptureBox)
  {
    View v = this.comicView;
    
    if((v != null) && (curCaptureBox != null) 
        && (curCaptureBox.width() > 0) && (curCaptureBox.height() > 0))
    {
      this.clipOffset = new Point(0, 0);
      
      try
      {
        // Only create the bitmap that contains the entire screen once
        if(this.lastScreenshot == null)
        {
          this.lastScreenshot = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
          this.captureCanvas = new Canvas(this.lastScreenshot);
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
        
        v.layout(0, 0, v.getWidth(), v.getHeight());
        
        // Draw the comic view to the lastScreenshot bitmap
        v.draw(this.captureCanvas);
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
      
        // Get bitmap that contains only the capture box area
        Bitmap captureBmp = Bitmap.createBitmap(this.lastScreenshot, 
            curCaptureBox.left, curCaptureBox.top, 
            curCaptureBox.width(), curCaptureBox.height());
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }

        // Create a Leptonica Pix object from the captured image
        this.lastCapture = ReadFile.readBitmap(captureBmp);
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
                
        // Convert to grayscale
        this.lastCapture = Convert.convertTo8(this.lastCapture);
                  
        if(this.lastCapture == null)
        {
          return false;
        }
                  
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
              
        Pix tempOtsu = Binarize.otsuAdaptiveThreshold(this.lastCapture, 2000, 2000, 0, 0, 0.0f);
      
        if(tempOtsu == null)
        {
          return false;
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          tempOtsu.recycle();
          return false;
        }
        
        /* Get the average intensity of the border pixels */
        float aveBorderBrightness = LeptUtils.getAveBorderBrightness(tempOtsu);
        tempOtsu.recycle();
        
        // If background is dark
        if(aveBorderBrightness <= 0.5f)
        {
          // Negate image
          boolean invertStatus = this.lastCapture.invert();
          
          if(!invertStatus)
          {
            return false;
          }
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
        
        // Scale the image
        this.lastCapture = Scale.scale(this.lastCapture, this.getIdealPreProcessingScaleFactor());

        if(this.lastCapture == null)
        {
          return false;
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
        
        // Apply unsharp mask
        this.lastCapture = Enhance.unsharpMasking(this.lastCapture, 5, 2.5f);
        
        if(this.lastCapture == null)
        {
          return false;
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
        
        // Binarize
        this.lastCapture = Binarize.otsuAdaptiveThreshold(this.lastCapture, 2000, 2000, 0, 0, 0.0f);
        
        if(this.lastCapture == null)
        {
          return false;
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
        
        // Remove furigana
        if(this.isOrientationVertical(this.captureBox))
        {
          Furigana.eraseFuriganaVerticalText(this.lastCapture, this.getIdealPreProcessingScaleFactor());
        }
        else
        {
          Furigana.eraseFuriganaHorizontalText(this.lastCapture, this.getIdealPreProcessingScaleFactor());
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
         
        // Clip foreground and add border
        if(this.isTriggerCapture || this.ocrSettingsForceBorder)
        {
          // Are there any foreground pixels to clip to?
          boolean canClip = Clip.testClipToForeground(this.lastCapture);
          
          if(canClip)
          {
            // Get the top-left point of the foreground clip (will be used to offset bounding boxes)
            this.clipOffset = LeptUtils.getForegroundClipOffset(this.lastCapture);
            
            // If user adjusted capture box since this routine was called, stop here
            if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
            {
              return false;
            }
            
            // Remove all existing white border pixels
            this.lastCapture = Clip.clipToForeground(this.lastCapture);
            
            if(this.lastCapture == null)
            {
              return false;
            }
          }
          
          // If user adjusted capture box since this routine was called, stop here
          if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
          {
            return false;
          }
          
          // Add an external white border
          this.lastCapture = Pix.addBlackOrWhiteBorder(this.lastCapture, 2, 2, 2, 2, false);
          this.clipOffset.x -= 2;
          this.clipOffset.y -= 2;
          
          this.isTriggerCapture = false;
        }
        
        // If user adjusted capture box since this routine was called, stop here
        if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(this.captureBox, curCaptureBox))
        {
          return false;
        }
      }
      catch(Exception e)
      {
        // If we're here, it's probably an out-of-memory exception
        Log.e(LOG_TAG, "Exception in captureScreen()! " + e);
        return false;
      }
    }
    else
    {
      return false;
    }
    
    return true;
  }
    
 
  /** Get the scale factor to scale the captured image such that the
   *  dimensions are equal to the un-scaled image size * SCALE_FACTOR. */
  public float getIdealPreProcessingScaleFactor()
  {
    ComicView v = this.comicView;
    float curZoom = v.getZoomFactor();
    float idealScaleFactor = OcrLayout.SCALE_FACTOR / curZoom;
        
    return idealScaleFactor;
  }

  
  /** Substitute common OCR mistakes with the correct text. */
  private String performOcrTextSubstitutions(String ocrText)
  {
    if(!this.substitutionsDbFile.exists())
    {
      return ocrText;
    }
    
    // Populate the substitutions list if needed
    if(this.substitutionsList == null)
    {
      try
      {
        this.substitutionsList = new ArrayList<Pair<String, String>>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.substitutionsDbFile), "UTF8"));
        
        String line = "";
        
        // Skip header line
        reader.readLine();
    
        while ((line = reader.readLine()) != null)
        {
          // Skip comment lines and blank lines
          if(line.startsWith("#") || (line.length() == 0))
          {
            continue;
          }
          
          String[] fields = line.split("=");
          
          String from = "";
          String to = "";
                  
          if(fields.length == 1)
          {
            from = fields[0];
            to = "";
          }
          else if(fields.length == 2)
          {
            from = fields[0];
            to = fields[1];
          }
          else
          {
            continue;
          }
          
          // Replace tokens
          from = from.replaceAll("%eq%", "="); 
          from = from.replaceAll("%perc%", "%");
          to = to.replaceAll("%eq%", "="); 
          to = to.replaceAll("%perc%", "%");
          
          Pair<String, String> fromTo = new Pair<String, String>(from, to);
          this.substitutionsList.add(fromTo);
        }
        
        reader.close();
      }
      catch(Exception e)
      {
        // Don't care
      }
    }
    
    String correctedText = ocrText;
    
    // Make substitutions from substitutions file
    for(Pair<String, String> fromTo : this.substitutionsList)
    {
      correctedText = correctedText.replaceAll(fromTo.first, fromTo.second);
    }
    
    // Force a few substitutions
    correctedText = correctedText.replaceAll("[′丶●'` 　〓]", "").trim();

    if(correctedText.length() >= 1)
    {
      String firstChar = correctedText.substring(0, 1);
      String newFirstChar = firstChar;
      
      // Convert small hiragana to large hiragana
      if(newFirstChar.equals("ぁ")) newFirstChar = "あ";
      else if(newFirstChar.equals("ぃ")) newFirstChar = "い";
      else if(newFirstChar.equals("ぅ")) newFirstChar = "う";
      else if(newFirstChar.equals("ぇ")) newFirstChar = "え";
      else if(newFirstChar.equals("ぉ")) newFirstChar = "お"; 
      
      else if(newFirstChar.equals("っ")) newFirstChar = "つ";
      
      else if(newFirstChar.equals("ゃ")) newFirstChar = "や";
      else if(newFirstChar.equals("ゅ")) newFirstChar = "ゆ";
      else if(newFirstChar.equals("ょ")) newFirstChar = "よ";
      
      else if(newFirstChar.equals("ゎ")) newFirstChar = "わ";
      
      // Convert small katakana to large katakana
      else if(newFirstChar.equals("ァ")) newFirstChar = "ア";
      else if(newFirstChar.equals("ィ")) newFirstChar = "イ";
      else if(newFirstChar.equals("ゥ")) newFirstChar = "ウ";
      else if(newFirstChar.equals("ェ")) newFirstChar = "エ";
      else if(newFirstChar.equals("ォ")) newFirstChar = "オ";
      
      else if(newFirstChar.equals("ヵ")) newFirstChar = "カ";
      else if(newFirstChar.equals("ケ")) newFirstChar = "ケ";
      
      else if(newFirstChar.equals("ッ")) newFirstChar = "ツ";
      
      else if(newFirstChar.equals("ャ")) newFirstChar = "ヤ";
      else if(newFirstChar.equals("ュ")) newFirstChar = "ユ";
      else if(newFirstChar.equals("ョ")) newFirstChar = "ヨ";
      
      else if(newFirstChar.equals("ヮ")) newFirstChar = "ワ";

      // Japanese words don't start with ん
      else if(newFirstChar.equals("ん")) newFirstChar = "";
      
      // Convert prolonged sound mark to "ichi"
      else if(newFirstChar.equals("ー")) newFirstChar = "一";
      
      
      correctedText = newFirstChar + correctedText.substring(1);
    }
           
    return correctedText;
  }
  
  
  /** Determine region that the provided coordinates are in relative to the capture box. */
  private DragRegion determineDragRegion(final int x, final int y)
  {
    final Rect r = this.captureBox;
    DragRegion dragRegion = DragRegion.NONE;
    
    if((x < r.left) && (y < r.top))
    {
      dragRegion = DragRegion.TOP_LEFT;
    }
    else if((x > r.left) && (x < r.right) && (y < r.top))
    {
      dragRegion = DragRegion.TOP;
    }
    else if((x > r.right) && (y < r.top))
    {
      dragRegion = DragRegion.TOP_RIGHT;
    }
    else if((x < r.left) && (y > r.top) && (y < r.bottom))
    {
      dragRegion = DragRegion.LEFT;
    }
    else if((x > r.left) && (x < r.right) && (y > r.top) && (y < r.bottom))
    {
      dragRegion = DragRegion.MIDDLE;
    }
    else if((x > r.right) && (y > r.top) && (y < r.bottom))
    {
      dragRegion = DragRegion.RIGHT;
    }
    else if((x < r.left) && (y > r.bottom))
    {
      dragRegion = DragRegion.BOTTOM_LEFT;
    }
    else if((x > r.left) && (x < r.right) && (y > r.bottom))
    {
      dragRegion = DragRegion.BOTTOM;
    }
    else if((x > r.right) && (y > r.bottom))
    {
      dragRegion = DragRegion.BOTTOM_RIGHT;
    }
    
    return dragRegion;
  }

  
  /** Adjust the specified region of the captureBox by the provided deltas. */
  public void adjustCaptureBox(final DragRegion dr, final float dx, final float dy)
  {        
    switch(dr)
    {
      case TOP_LEFT:
        this.captureBox.left -= dx;
        this.captureBox.top -= dy;
        break;
      case TOP:
        this.captureBox.top -= dy;
        break;
      case TOP_RIGHT:
        this.captureBox.right -= dx;
        this.captureBox.top -= dy;
        break;
      case LEFT:
        this.captureBox.left -= dx;
        break;
      case MIDDLE:
        this.captureBox.left -= dx;
        this.captureBox.top -= dy;
        this.captureBox.right -= dx;
        this.captureBox.bottom -= dy;
        break;
      case RIGHT:
        this.captureBox.right -= dx;
        break;
      case BOTTOM_LEFT:
        this.captureBox.left -= dx;
        this.captureBox.bottom -= dy;
        break;
      case BOTTOM:
        this.captureBox.bottom -= dy;
        break;
      case BOTTOM_RIGHT:
        this.captureBox.right -= dx;
        this.captureBox.bottom -= dy;
        break;
      default:
        break;
    }
    
    // Bound the capture box to the edges of the screen and ensure that  
    // the bottom-right corner cannot be less than the top-right corner
    this.captureBox.left = MathUtils.bound(this.captureBox.left, 0, this.screenWidth - 1);
    this.captureBox.top = MathUtils.bound(this.captureBox.top, 0, this.screenHeight - 1);
    this.captureBox.right = MathUtils.bound(this.captureBox.right, this.captureBox.left + 1,  this.screenWidth);
    this.captureBox.bottom = MathUtils.bound(this.captureBox.bottom, this.captureBox.top + 1, this.screenHeight);
    
    this.lastAdjustment = Calendar.getInstance();
    
    this.isTriggerCapture = false;
    
    // Remove bounding boxes from the OCR view until they are re calculated
    ocrView.setBoundingBoxes(null, 1.0f);
    
    // Pass the capture box to the OCR view to be drawn immediately
    this.ocrView.setCaptureBox(this.captureBox);
  }
  
  
  /** Nudge the capture box in the provided direction. */
  public void nudgeCaptureBox(NudgeDirection dir)
  {
    // Number of pixels to nudge by
    final int NUDGE_AMOUNT = 2;
    
    if(this.nudgeCorner == this.NUDGE_CORNER_BOTTOM_RIGHT)
    {
      if(dir == NudgeDirection.UP)
      {
        this.adjustCaptureBox(DragRegion.BOTTOM, 0, NUDGE_AMOUNT);
      }
      else if(dir == NudgeDirection.DOWN)
      {
        this.adjustCaptureBox(DragRegion.BOTTOM, 0, -NUDGE_AMOUNT);
      }
      else if(dir == NudgeDirection.LEFT)
      {
        this.adjustCaptureBox(DragRegion.RIGHT, NUDGE_AMOUNT, 0);
      }
      else // RIGHT
      {
        this.adjustCaptureBox(DragRegion.RIGHT, -NUDGE_AMOUNT, 0);
      }
    }
    else // TOP_LEFT
    {
      if(dir == NudgeDirection.UP)
      {
        this.adjustCaptureBox(DragRegion.TOP, 0, NUDGE_AMOUNT);
      }
      else if(dir == NudgeDirection.DOWN)
      {
        this.adjustCaptureBox(DragRegion.TOP, 0, -NUDGE_AMOUNT);
      }
      else if(dir == NudgeDirection.LEFT)
      {
        this.adjustCaptureBox(DragRegion.LEFT, NUDGE_AMOUNT, 0);
      }
      else // RIGHT
      {
        this.adjustCaptureBox(DragRegion.LEFT, -NUDGE_AMOUNT, 0);
      }
    }
  }
  
  
  /** Show the OCR Engine Load Failure dialog. */
  private void showOcrEngineLoadFailureDialog()
  {
    new AlertDialog.Builder(this.context)
        .setIcon(android.R.drawable.ic_menu_info_details)
        .setTitle(R.string.ocr_engine_load_failed_dialog_title)
        .setMessage(R.string.ocr_engine_load_failed_dialog_msg)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface dialog, int whichButton)
            {
            }
          })
       .show();
  }
  
  
  /** Show the OCR Dictionary Load Failure dialog. */
  private void showOcrDictLoadFailureDialog()
  { 
    new AlertDialog.Builder(this.context)
        .setIcon(android.R.drawable.ic_menu_info_details)
        .setTitle(R.string.ocr_dict_load_failed_dialog_title)
        .setMessage(R.string.ocr_dict_load_failed_dialog_msg)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface dialog, int whichButton)
            {
            }
          })
       .show();
  }
  
  
  /** Show the OCR Assets Missing dialog. */
  private void showOcrAssetsMissingDialog()
  { 
    new AlertDialog.Builder(this.context)
        .setIcon(android.R.drawable.ic_menu_info_details)
        .setTitle(R.string.ocr_assets_missing_dialog_title)
        .setMessage(R.string.ocr_assets_missing_dialog_msg)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface dialog, int whichButton)
            {
            }
          })
       .show();
  }

  /** Show the OCR Assets Missing dialog. */
  public void showErrorDialog(@StringRes int msgResId) {
    showErrorDialog(context.getString(msgResId));
  }

  public void showErrorDialog(String msg) {
    new AlertDialog.Builder(this.context)
            .setIcon(android.R.drawable.ic_menu_info_details)
            .setTitle(R.string.ocr_error_dialog_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
            {
              public void onClick(DialogInterface dialog, int whichButton)
              {
              }
            })
            .show();
  }
            
       
  /** Initialize the OCR engine. */
  private boolean initOcrEngine()
  {
    if(OcrLayout.tess == null)
    {
      OcrLayout.tess = new TessBaseAPI();  
      
      try
      {
        boolean loaded = OcrLayout.tess.init(this.tesseractDbDir.toString(), "jpn");
        
        boolean status = true;
        status = status && OcrLayout.tess.setVariable("tessedit_enable_dict_correction",  "1"     );
        status = status && OcrLayout.tess.setVariable("textord_really_old_xheight",       "1"     );
        status = status && OcrLayout.tess.setVariable("tosp_threshold_bias2",             "1"     );
        status = status && OcrLayout.tess.setVariable("classify_norm_adj_midpoint",       "96"    );
        status = status && OcrLayout.tess.setVariable("tessedit_class_miss_scale",        "0.002" );
        status = status && OcrLayout.tess.setVariable("textord_initialx_ile",             "1.0"   );
        status = status && OcrLayout.tess.setVariable("textord_min_linesize",             "2.5"   );
        
        if(!status)
        {
          Log.e(LOG_TAG, "initOcrEngine() setVariable failed!");
          this.showOcrEngineLoadFailureDialog();
          return false;
        }
         
        if(!loaded)
        {
          Log.e(LOG_TAG, "initOcrEngine() load failed!");
          this.showOcrEngineLoadFailureDialog();
          return false;
        }
      }
      catch(Exception e)
      {
        Log.e(LOG_TAG, "initOcrEngine() exception!" + e);
        this.showOcrEngineLoadFailureDialog();
        return false;
      }
        
      OcrLayout.tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT);
    }
    
    return true;
  }
  
  
  /** Initialize the Japanese dictionary. */
  private boolean initJapaneseDictionary()
  {
    if(!OcrLayout.dicEdict.isDatabaseLoaded())
    {
      // Setup the dictionary with paths to databases
      boolean loaded = OcrLayout.dicEdict.openDatabase(this.edictDbFile.getPath(), this.deinflectionDbFile.getPath());
      
      if(!loaded)
      {
        Log.e(LOG_TAG, "initJapaneseDictionary() load failed!");
        this.showOcrDictLoadFailureDialog();
        return false;
      }
    }
    
    return true;
  }
   
  
  /** Initialize the Names dictionary. */
  private boolean initNamesDictionary()
  {
    if(!this.namesDbFile.exists())
    {
      return false;
    }
    
    if(!OcrLayout.dicNames.isDatabaseLoaded())
    {
      // Setup the dictionary with paths to databases
      boolean loaded = OcrLayout.dicNames.openDatabase(this.namesDbFile.getPath());
      
      if(!loaded)
      {
        Log.e(LOG_TAG, "initNamesDictionary() load failed!");
        this.showOcrDictLoadFailureDialog();
        return false;
      }
    }
    
    return true;
  }
  
  
  /** Initialize the Kanji dictionary. */
  private boolean initKanjiDictionary()
  {
    if(!this.kanjiDbFile.exists())
    {
      return false;
    }
    
    if(!OcrLayout.dicKanji.isDatabaseLoaded())
    {
      // Setup the dictionary with paths to databases
      boolean loaded = OcrLayout.dicKanji.openDatabase(this.kanjiDbFile.getPath());
      
      if(!loaded)
      {
        Log.e(LOG_TAG, "initKanjiDictionary() load failed!");
        this.showOcrDictLoadFailureDialog();
        return false;
      }
    }
    
    if(this.kanjiDefFormatFile.exists())
    {
      try
      {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.kanjiDefFormatFile), "UTF8"));
        
        String line = "";
        String defFormat = "";
        
        while ((line = reader.readLine()) != null)
        {
          defFormat += line;
        }
        
        OcrLayout.dicKanji.setDefFormat(defFormat);
        
        reader.close();
      }
      catch(Exception e)
      {
        // Don't care
      }
    }
    
    return true;
  }
  
  
  /** Initialize the frequency database. */
  private boolean initFreqDatabase()
  {
    if(!OcrLayout.freqDb.isDatabaseLoaded())
    {
      boolean loaded = OcrLayout.freqDb.openDatabase(this.freqDbFile.getPath());
      
      if(!loaded)
      {
        Log.e(LOG_TAG, "initFreqDatabase() load failed!");
        return false;
      }
    }
    
    return true;
  }
  
  
  /** Copy OCR assets to the application folder in external storage. 
   *  http://stackoverflow.com/questions/4447477/android-how-to-copy-files-in-assets-to-sdcard
   *  */
  private boolean copyOcrAssets()
  {
    AssetManager assetManager = this.context.getAssets();
    ContextWrapper cw = new ContextWrapper(this.context);
    String[] files = null;
    File externalDir = this.context.getExternalFilesDir(null);
    File tessdataDir = new File(externalDir.getPath() + Constants.TESSDATA_DIR);
        
    // Create the Tesseract directory structure if it doesn't exist
    if(!tessdataDir.exists())
    {
      tessdataDir.mkdirs();
    }
    
    try
    {
      files = assetManager.list("");
    }
    catch (IOException e)
    {
      Log.e(LOG_TAG, "copyOcrAssets() failed to get asset file list!" + e);
      return false;
    }
    
    for (String filename : files)
    {
      InputStream in = null;
      OutputStream out = null;
      
      // Only store expected files
      if(filename.equals(Constants.EDICT_DB_FILENAME) 
          || filename.equals(Constants.TESSERACT_DB_FILENAME)
          || filename.equals(Constants.DEINFLECTION_DB_FILENAME)
          || filename.equals(Constants.SUBSTITUTIONS_DB_FILENAME)
          || filename.equals(Constants.EPLKUP_FILENAME)
          || filename.equals(Constants.EPLKUP_NON_PIE_FILENAME)
          || filename.equals(Constants.FREQ_DB_FILENAME)
          )
      {
        try
        {
          in = assetManager.open(filename);
          
          File outFile;
          
          if(filename.equals(Constants.TESSERACT_DB_FILENAME))
          {
            outFile = new File(tessdataDir, filename);
          }
          else if(filename.equals(Constants.EPLKUP_FILENAME))
          {
            outFile =  new File(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_FILENAME);
          }
          else if(filename.equals(Constants.EPLKUP_NON_PIE_FILENAME))
          {
            outFile =  new File(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_NON_PIE_FILENAME);
          }
          else
          {
            outFile = new File(externalDir, filename);
          }
          
          out = new FileOutputStream(outFile);
          FileUtils.copyFile(in, out);
          in.close();
          in = null;
          out.flush();
          out.close();
          out = null;
          
          
          // Give eplkup execute permissions
          if(filename.equals(Constants.EPLKUP_FILENAME))
          {
            ShellUtils.chmod(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_FILENAME, "744");
          }
          else if(filename.equals(Constants.EPLKUP_NON_PIE_FILENAME))
          {
            ShellUtils.chmod(cw.getApplicationInfo().dataDir + "/" + Constants.EPLKUP_NON_PIE_FILENAME, "744");
          }
        }
        catch (IOException e)
        {
          Log.e(LOG_TAG, "copyOcrAssets() copy failed!" + e);
          return false;
        }
      }
    }
    
    return true;
  }

  
  /** Is this version of the app different than the stored version? */
  private boolean isVersionDifferent()
  {   
    boolean versionDifferent = false;
    this.preferencesController = new PreferencesController(context);
    
    try
    {
      // Get the version of this app
      PackageInfo info = this.context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      String versionName = info.versionName;
      
      // Get the stored version preference
      String storedVersionName = this.preferencesController.getPreferences().getString("pref_stored_version", "NONE");
      
      // If versions are different, store current app version
      if(!versionName.equals(storedVersionName))
      {
        this.preferencesController.getPreferences().edit().putString("pref_stored_version", versionName).commit();
        versionDifferent = true;
      }
    }
    catch (NameNotFoundException e)
    {
      versionDifferent = true;
    }

    return versionDifferent;
  }
  
  
  /** Have the OCR assets been copied to external storage? */
  private boolean ocrAssetsExist()
  {
    return (this.tesseractDbFile.exists()
        && this.edictDbFile.exists()
        && this.deinflectionDbFile.exists()
        && this.freqDbFile.exists());
  }
  
  
  /** Check that the OCR database assets exist and are up to date. */
  private boolean needToUpdateOcrAssets()
  {
    boolean update = false; 
    boolean versionDifferent = this.isVersionDifferent();
    
    if(!this.ocrAssetsExist() || versionDifferent)
    {
      update = true;
    }
    
    return update;
  }
  

  /** Start task that will show a progress dialog, copy OCR assets to external storage and prepare for capture. */  
  private void runCopyAssetsTask()
  {
    /** Task responsible for showing a progress dialog and copying the OCR assets to external storage. */
    AsyncTask<Void, Void, Void> copyAssetsTask = new AsyncTask<Void, Void, Void>()
    {
      
      final Handler prepareCaptureHandler = new Handler(); 
      
      /** Runnable that will start the capture process */
      final Runnable prepareCaptureRunnable = new Runnable()
      {
        public void run()
        {
          prepareCapture();
        }
      };
      
      
      @Override
      protected void onPreExecute()
      {
        assetCopyProgressDialog = new ProgressDialog(context);
        assetCopyProgressDialog.setTitle(getResources().getString(R.string.ocr_load_assets_dialog_title));
        assetCopyProgressDialog.setMessage(getResources().getString(R.string.ocr_load_assets_dialog_msg));
        assetCopyProgressDialog.setCancelable(false);
        assetCopyProgressDialog.setIndeterminate(true);
        assetCopyProgressDialog.show();
      }

      
      @Override
      protected Void doInBackground(Void... arg0)
      {
        copyOcrAssets();      
        prepareCaptureHandler.post(prepareCaptureRunnable);
        return null;
      }


      @Override
      protected void onPostExecute(Void result)
      {
        if (assetCopyProgressDialog != null)
        {
          assetCopyProgressDialog.dismiss();
        }
      }
    };
    
    copyAssetsTask.execute((Void[]) null);
  }

  
  public boolean createTriggerCaptureBox(Point pt)
  {
    final int IDEAL_CAP_WIDTH = 100;
    final int IDEAL_CAP_HALF_WIDTH = IDEAL_CAP_WIDTH / 2;
    final int IDEAL_CAP_HEIGHT = 450;
    final int IDEAL_CAP_ABOVE_PT = 35;
    final int IDEAL_LOOKAHEAD = 30;
    final int FALLBACK_WIDTH = 70;
    final int FALLBACK_HEIGHT = 200;
    final int FALLBACK_ABOVE_PT = 10;
    
    View v = this.comicView;
    
    if(v != null)
    {
      try
      {
        // Only create the bitmap that contains the entire screen once
        if(this.lastScreenshot == null)
        {
          this.lastScreenshot = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
          this.captureCanvas = new Canvas(this.lastScreenshot);
        }
                
        v.layout(0, 0, v.getWidth(), v.getHeight());
        
        // Draw the comic view to the lastScreenshot bitmap
        v.draw(this.captureCanvas);
           
        Rect cropRect = new Rect();
        
        if(this.textOrientation == TEXT_ORIENTATION_HORIZONTAL)
        {
          cropRect.left = Math.max(0, pt.x - IDEAL_CAP_ABOVE_PT);
          cropRect.top = Math.max(0, pt.y - IDEAL_CAP_HALF_WIDTH);
          cropRect.right = Math.min(v.getWidth(), pt.x + IDEAL_CAP_HEIGHT);
          cropRect.bottom = Math.min(v.getHeight(), pt.y + IDEAL_CAP_HALF_WIDTH);
        }
        else // Vertical or Auto
        {
          cropRect.left = Math.max(0, pt.x - IDEAL_CAP_HALF_WIDTH);
          cropRect.top = Math.max(0, pt.y - IDEAL_CAP_ABOVE_PT);
          cropRect.right = Math.min(v.getWidth(), pt.x + IDEAL_CAP_HALF_WIDTH);
          cropRect.bottom = Math.min(v.getHeight(), pt.y + IDEAL_CAP_HEIGHT);
        }
        
        // Get bitmap that contains the area to crop
        Bitmap cropBmp = Bitmap.createBitmap(this.lastScreenshot, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
        
        // Get the click point relative to the cropped area
        Point ptInCropRect = new Point(pt.x - cropRect.left, pt.y - cropRect.top);
             
        // Crop
        Pix pixs = ReadFile.readBitmap(cropBmp);
        
        if(pixs == null)
        {
          return false;
        }
        
        cropBmp.recycle();
                
        // Convert to grayscale
        pixs = Convert.convertTo8(pixs);
                
        if(pixs == null)
        {
          return false;
        }
        
        Pix tempOtsu = Binarize.otsuAdaptiveThreshold(pixs, 2000, 2000, 0, 0, 0.0f);
        
        if(tempOtsu == null)
        {
          return false;
        }
        
        float ave = 0.0f;
        
        // Get the average intensity of the pixels around the click point
        if(this.textOrientation == TEXT_ORIENTATION_HORIZONTAL)
        {  
          ave = Pix.averageInRect(tempOtsu,
              (int)(ptInCropRect.x * 0.9), 
              (int)(ptInCropRect.y - (tempOtsu.getHeight() * 0.95) / 2.0), 
              (int)(tempOtsu.getWidth() * 0.25), 
              (int)(tempOtsu.getHeight() * 0.95));
        }
        else // Vertical or Auto
        {
          ave = Pix.averageInRect(tempOtsu,
              (int)(ptInCropRect.x - (tempOtsu.getWidth() * 0.95) / 2.0), 
              (int)(ptInCropRect.y * 0.9), 
              (int)(tempOtsu.getWidth() * 0.95), 
              (int)(tempOtsu.getHeight() * 0.25));
        }
        
        tempOtsu.recycle();
        
        // If background is dark
        if(ave >= 0.51f)
        {
          // Negate image
          boolean invertStatus = pixs.invert();
          
          if(!invertStatus)
          {
            return false;
          }
        }
        
        // Blur to reduce noise
        pixs = Convolve.blockconvGray(pixs, 1, 1);
        
        if(pixs == null)
        {
          return false;
        }
                        
        // Apply unsharp mask
        pixs = Enhance.unsharpMasking(pixs, 5, 2.5f);
        
        if(pixs == null)
        {
          return false;
        }
         
        // Binarize
        pixs = Binarize.otsuAdaptiveThreshold(pixs, 2000, 2000, 0, 0, 0.0f);
        
        if(pixs == null)
        {
          return false;
        }
             
        // Remove black pixels connected to the border.
        // This eliminates annoying things like text bubbles.
        pixs = Seedfill.removeBorderConnComps(pixs, 8);
        
        if(pixs == null)
        {
          return false;
        }
                          
        // Find the black pixel closest to the click point
        Point nearestPixel = LeptUtils.findNearestBlackPixel(pixs, ptInCropRect.x, ptInCropRect.y, 40);

        // Get a bounding box surrounding the clicked text
        Rect boundingBox = BoundingTextRect.getBoundingRect(pixs, nearestPixel.x, nearestPixel.y, 
            (this.textOrientation != TEXT_ORIENTATION_HORIZONTAL), IDEAL_LOOKAHEAD);
        
        // Form the capture box size and position based on click point and bounding box
        this.captureBox = new Rect();
        this.captureBox.left = pt.x - ptInCropRect.x + boundingBox.left;
        this.captureBox.top = pt.y - ptInCropRect.y + boundingBox.top;
        this.captureBox.right = this.captureBox.left + boundingBox.width();
        this.captureBox.bottom = this.captureBox.top + boundingBox.height();
                 
         // If could not find adequate bounding rectangle, fallback to a default size
        if(this.captureBox.width() <= 2 || this.captureBox.height() <= 2)
        {
          if(this.textOrientation == TEXT_ORIENTATION_HORIZONTAL)
          {  
            this.captureBox = new Rect();
            this.captureBox.left = Math.max(0, pt.x - FALLBACK_ABOVE_PT);
            this.captureBox.top = Math.max(0, pt.y - FALLBACK_WIDTH / 2);
            this.captureBox.right = Math.min(v.getWidth(), pt.x + FALLBACK_HEIGHT);
            this.captureBox.bottom = Math.min(v.getHeight(), pt.y + FALLBACK_WIDTH / 2);
          }
          else // Vertical or Auto
          {
            this.captureBox = new Rect();
            this.captureBox.left = Math.max(0, pt.x - FALLBACK_WIDTH / 2);
            this.captureBox.top = Math.max(0, pt.y - FALLBACK_ABOVE_PT);
            this.captureBox.right = Math.min(v.getWidth(), pt.x + FALLBACK_WIDTH / 2);
            this.captureBox.bottom = Math.min(v.getHeight(), pt.y + FALLBACK_HEIGHT);
          }
        }
        
        pixs.recycle();
      }
      catch(Exception e)
      {
        // If we're here, it's probably an out-of-memory exception
        Log.e(LOG_TAG, "Exception in processTriggerCapture()! " + e);
        return false;
      }
    }
      
    this.isTriggerCapture = true;
    
    return true;
  }
  
  
  /** Perform a trigger capture. May be called from OcrLayout instead of start()
      to avoid being prompted to draw a box or to perform a trigger capture
      when already in OCR mode. */
  public void startTriggerCapture(ComicViewerActivity cva, Point pt)
  {    
    /* If already in OCR mode, update the capture box location */
    if(this.captureTimer != null)
    {
      this.createTriggerCaptureBox(pt);
      this.updateDicViewLocation();
      this.ocrView.setCaptureBox(this.captureBox);
      this.forceCaptureUpdate();
    }
    else /* Start OCR mode */
    {
      this.start(cva);
      this.createTriggerCaptureBox(pt);
      this.updateDicViewLocation();
      this.ocrView.setCaptureBox(this.captureBox);
      this.forceCaptureUpdate();
  
      this.showOcrInterface();
      
      this.captureState = CaptureState.DRAG;
    }
  }
  
  
  /** Called when this view is shown. */
  public void start(ComicViewerActivity cva)
  {
    this.ocrStarted = false; // Will be set in prepareCapture()
    
    this.comicViewerActivity = cva;
    
    // Check if OCR assets need updating
    if(this.needToUpdateOcrAssets())
    {
      this.runCopyAssetsTask();
    }
    else // Assets did not need to be updated
    {
      this.prepareCapture();
    }
  }

  
  /** Setup for the start of the capture. */
  private void prepareCapture()
  {
    if(!this.ocrAssetsExist())
    {
      this.showOcrAssetsMissingDialog();
      this.comicViewerActivity.hideOcr();
      return; 
    }
    
    if(!this.initOcrEngine())
    {
      this.comicViewerActivity.hideOcr();
      return;
    }
    
    if(!this.initJapaneseDictionary())
    {
      this.comicViewerActivity.hideOcr();
      return;
    }
    
    if(!this.initNamesDictionary())
    {
      // Don't care
    }
    
    if(!this.initKanjiDictionary())
    {
      // Don't care
    }
    
    if(!this.initFreqDatabase())
    {
      // Don't care
    }
        
    this.readOcrSettings(this.context);
        
    this.setHideGui(false);
    
    this.textViewStartMsg.setVisibility(VISIBLE);
    
    // Remove GUI elements that depend on the existence of the capture box
    this.btnMenu.setVisibility(GONE);
    this.btnExit.setVisibility(GONE);
    this.btnLookupNext.setVisibility(GONE);
    this.btnLookupPrev.setVisibility(GONE);
    this.dicView.setVisibility(GONE);
    this.setGuiButtonVisi(GONE);
    
    // Set size of the nudge buttons
    this.setNudgeButtonSize();
    
    // Reset capture info
    this.captureState = CaptureState.SET_TOP_LEFT;
    this.captureBox = null;
    this.ocrView.setCaptureBox(null);
    this.boundingBoxes = null;
    this.ocrView.setBoundingBoxes(null, 1.0f);
    
    this.lastOcrText = "";
    this.lastEntryList = null;
    
    // Reset the prev/next history
    this.lookupWordIdxStack = new Stack<Integer>();
    this.lookupWordIdxStack.push(0);
    
    this.lastCaptureTime = Calendar.getInstance();
    this.lastAdjustment = Calendar.getInstance();
    
    // Start the timer that will schedule the capture and OCR
    this.captureTimer = new Timer("CaptureTimer", true);
    this.updateCaptureTask = new UpdateCaptureTask();
    this.captureTimer.scheduleAtFixedRate(this.updateCaptureTask, 10, 10);
    
    this.ocrStarted = true;
  }
  
  
  /** Called when this view is hidden. */
  public void stop()
  {
    try
    {
      if(!this.ocrStarted)
      {
        return;
      }
      
      // Stop the capture timer
      if(this.captureTimer != null)
      {
        this.captureTimer.cancel();
        this.captureTimer = null;
      }
      
      this.storeOcrSettings();
    }
    catch(Exception e)
    {
      Log.e(LOG_TAG, "Exception in stop()! " + e);
    }
  }
  
  
  /** Read in OCR-related settings. */
  private void readOcrSettings(Context context)
  {
    this.preferencesController = new PreferencesController(context);
    
    // Settings that can be set in the preferences activity
    this.ocrSettingsDictBackgroundColor  = preferencesController.getPreferences().getInt("ocr_settings_dict_background_color",  Constants.DEFAULT_OCR_SETTINGS_DICT_BACKGROUND_COLOR);
    this.ocrSettingsDictExpressionColor  = preferencesController.getPreferences().getInt("ocr_settings_dict_expression_color",  Constants.DEFAULT_OCR_SETTINGS_DICT_EXPRESSION_COLOR);
    this.ocrSettingsDictReadingColor     = preferencesController.getPreferences().getInt("ocr_settings_dict_reading_color",     Constants.DEFAULT_OCR_SETTINGS_DICT_READING_COLOR);
    this.ocrSettingsDictDefinitionColor  = preferencesController.getPreferences().getInt("ocr_settings_dict_definition_color",  Constants.DEFAULT_OCR_SETTINGS_DICT_DEFINITION_COLOR);
    this.ocrSettingsDictConjugationColor = preferencesController.getPreferences().getInt("ocr_settings_dict_conjugation_color", Constants.DEFAULT_OCR_SETTINGS_DICT_CONJUGATION_COLOR);
    this.ocrSettingsDictSubDefColor      = preferencesController.getPreferences().getInt("ocr_settings_sub_def_color",          Constants.DEFAULT_OCR_SETTINGS_DICT_SUB_DEF_COLOR);
    this.ocrSettingsDictExamplePrependColor = preferencesController.getPreferences().getInt("ocr_settings_example_prepend_color", Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_PREPEND_COLOR);
    this.ocrSettingsDictExampleJapColor  = preferencesController.getPreferences().getInt("ocr_settings_example_jap_color",      Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_JAP_COLOR);
    this.ocrSettingsDictExampleEngColor  = preferencesController.getPreferences().getInt("ocr_settings_example_eng_color",      Constants.DEFAULT_OCR_SETTINGS_DICT_EXAMPLE_ENG_COLOR);
    this.ocrSettingsDictNameColor        = preferencesController.getPreferences().getInt("ocr_settings_dict_name_color",        Constants.DEFAULT_OCR_SETTINGS_DICT_NAME_COLOR);
    this.ocrSettingsDictSeparatorColor   = preferencesController.getPreferences().getInt("ocr_settings_dict_separator_color",   Constants.DEFAULT_OCR_SETTINGS_DICT_SEPARATOR_COLOR);
    this.ocrSettingsDictOcrTextColor     = preferencesController.getPreferences().getInt("ocr_settings_dict_ocr_text_color",    Constants.DEFAULT_OCR_SETTINGS_DICT_OCR_TEXT_COLOR);
    this.ocrSettingsCaptureBoxColor      = preferencesController.getPreferences().getInt("ocr_settings_capture_box_color",      Constants.DEFAULT_OCR_SETTINGS_CAPTURE_BOX_COLOR);
    this.ocrSettingsBoundingBoxColor     = preferencesController.getPreferences().getInt("ocr_settings_bounding_box_color",     Constants.DEFAULT_OCR_SETTINGS_BOUNDING_BOX_COLOR);
    this.ocrSettingsFreqVeryCommonColor  = preferencesController.getPreferences().getInt("ocr_settings_freq_very_common_color", Constants.DEFAULT_OCR_SETTINGS_FREQ_VERY_COMMON_COLOR);
    this.ocrSettingsFreqCommonColor      = preferencesController.getPreferences().getInt("ocr_settings_freq_common_color",      Constants.DEFAULT_OCR_SETTINGS_FREQ_COMMON_COLOR);
    this.ocrSettingsFreqUncommonColor    = preferencesController.getPreferences().getInt("ocr_settings_freq_uncommon_color",    Constants.DEFAULT_OCR_SETTINGS_FREQ_UNCOMMON_COLOR);
    this.ocrSettingsFreqRareColor        = preferencesController.getPreferences().getInt("ocr_settings_freq_rare_color",        Constants.DEFAULT_OCR_SETTINGS_FREQ_RARE_COLOR);
    this.ocrSettingsWordHighlightColor   = preferencesController.getPreferences().getInt("ocr_settings_word_highlight_color",   Constants.DEFAULT_OCR_SETTINGS_WORD_HIGHLIGHT_COLOR);  
    this.ocrSettingsKnownWordColor       = preferencesController.getPreferences().getInt("ocr_settings_known_word_color",       Constants.DEFAULT_OCR_SETTINGS_KNOWN_WORD_COLOR);  
    this.ocrSettingsShowBoundingBoxes    = preferencesController.getPreferences().getBoolean("ocr_settings_show_bounding_boxes", Constants.DEFAULT_OCR_SETTINGS_SHOW_BOUNDING_BOXES);
    
    if(this.ocrView != null)
    {
      this.ocrView.setCaptureBoxColor(this.ocrSettingsCaptureBoxColor);
      this.ocrView.setBoundingBoxColor(this.ocrSettingsBoundingBoxColor);
      this.ocrView.setBoundingBoxVisi(this.ocrSettingsShowBoundingBoxes);
    }
   
    this.ocrSettingsSimplifiedLayoutPortrait  = preferencesController.getPreferences().getBoolean("ocr_settings_simplified_layout_portrait", Constants.DEFAULT_OCR_SETTINGS_SIMPLIFIED_LAYOUT_PORTRAIT);
    this.ocrSettingsSimplifiedLayoutLandscape = preferencesController.getPreferences().getBoolean("ocr_settings_simplified_layout_landscape", Constants.DEFAULT_OCR_SETTINGS_SIMPLIFIED_LAYOUT_LANDSCAPE);
    this.ocrSettingsLargeNudgeButtons         = preferencesController.getPreferences().getBoolean("ocr_settings_large_nudge_buttons", Constants.DEFAULT_OCR_SETTINGS_LARGE_NUDGE_BUTTONS);
    this.ocrSettingsShowTextOrientationButton = preferencesController.getPreferences().getBoolean("ocr_settings_show_text_orientation_button", Constants.DEFAULT_OCR_SETTINGS_SHOW_TEXT_ORIENTATION_BUTTON);
    this.ocrSettingsShowNudgeButtons          = preferencesController.getPreferences().getBoolean("ocr_settings_show_nudge_buttons",   Constants.DEFAULT_OCR_SETTINGS_SHOW_NUDGE_BUTTONS);
    this.ocrSettingsShowSendButton            = preferencesController.getPreferences().getBoolean("ocr_settings_show_send_button",     Constants.DEFAULT_OCR_SETTINGS_SHOW_SEND_BUTTON);
    this.ocrSettingsShowLookupNextButton      = preferencesController.getPreferences().getBoolean("ocr_settings_show_lookup_next_button", Constants.DEFAULT_OCR_SETTINGS_SHOW_LOOKUP_NEXT_BUTTON);
    this.ocrSettingsShowLookupPrevButton      = preferencesController.getPreferences().getBoolean("ocr_settings_show_lookup_prev_button", Constants.DEFAULT_OCR_SETTINGS_SHOW_LOOKUP_PREV_BUTTON);
   
    this.ocrSettingsEdictCompactDefinitions   = preferencesController.getPreferences().getBoolean("ocr_settings_edict_compact_definitions", Constants.DEFAULT_OCR_SETTINGS_EDICT_COMPACT_DEFINITIONS);
    
    try
    {
      this.ocrSettingsEpwingMaxDefLines = preferencesController.getPreferences().getInt("ocr_settings_epwing_max_def_lines", Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_DEF_LINES);
    }
    catch(Exception e)
    {
      try
      {
        this.ocrSettingsEpwingMaxDefLines = Integer.parseInt(preferencesController.getPreferences().getString("ocr_settings_epwing_max_def_lines", String.valueOf(Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_DEF_LINES)));
      }
      catch(Exception e1)
      {
        this.ocrSettingsEpwingMaxDefLines = Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_DEF_LINES;
      }
    }   
    
    this.ocrSettingsEpwingDic1 = preferencesController.getPreferences().getString("ocr_settings_epwing_path_1", "");
    this.ocrSettingsEpwingDic2 = preferencesController.getPreferences().getString("ocr_settings_epwing_path_2", "");
    this.ocrSettingsEpwingDic3 = preferencesController.getPreferences().getString("ocr_settings_epwing_path_3", "");
    this.ocrSettingsEpwingDic4 = preferencesController.getPreferences().getString("ocr_settings_epwing_path_4", "");
    
    this.ocrSettingsEpwingCompactDefinitions = preferencesController.getPreferences().getBoolean("ocr_settings_epwing_compact_definitions", Constants.DEFAULT_OCR_SETTINGS_EPWING_COMPACT_DEFINITIONS);
    this.ocrSettingsEpwingParse = preferencesController.getPreferences().getBoolean("ocr_settings_epwing_parse", Constants.DEFAULT_OCR_SETTINGS_EPWING_PARSE);
       
    this.ocrSettingsEpwingShowExamples = preferencesController.getPreferences().getBoolean("ocr_settings_epwing_show_examples", Constants.DEFAULT_OCR_SETTINGS_EPWING_SHOW_EXAMPLES);
    
    try
    {
      this.ocrSettingsEpwingMaxExamples = preferencesController.getPreferences().getInt("ocr_settings_epwing_max_examples", Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_EXAMPLES);
    }
    catch(Exception e)
    {
      try
      {
        this.ocrSettingsEpwingMaxExamples = Integer.parseInt(preferencesController.getPreferences().getString("ocr_settings_epwing_max_examples", String.valueOf(Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_EXAMPLES)));
      }
      catch(Exception e1)
      {
        this.ocrSettingsEpwingMaxExamples = Constants.DEFAULT_OCR_SETTINGS_EPWING_MAX_EXAMPLES;
      }
    }
    
    this.ocrSettingsEpwingCompactExamples = preferencesController.getPreferences().getBoolean("ocr_settings_epwing_compact_examples", Constants.DEFAULT_OCR_SETTINGS_EPWING_COMPACT_EXAMPLES);
    this.ocrSettingsEpwingStripExamplesFromDefs = preferencesController.getPreferences().getBoolean("ocr_settings_epwing_strip_examples_from_defs", Constants.DEFAULT_OCR_SETTINGS_EPWING_STRIP_EXAMPLES_FROM_DEFS);
    
    this.ocrSettingsForceBorder = preferencesController.getPreferences().getBoolean("ocr_settings_force_border", Constants.DEFAULT_OCR_SETTINGS_FORCE_BORDER);
    this.ocrSettingsShowFrequency = preferencesController.getPreferences().getBoolean("ocr_settings_show_frequency", Constants.DEFAULT_OCR_SETTINGS_SHOW_FREQUENCY);
    this.ocrSettingsWordListSaveFilePath = preferencesController.getPreferences().getString("ocr_settings_misc_word_list_save_file_path", "");
    this.ocrSettingsWordListSaveFileFormat = preferencesController.getPreferences().getString("ocr_settings_misc_word_list_save_file_format", "Expression [tab] Reading [tab] Definition");
    
    // Settings that can't be set in the preferences activity
    this.setNudgeCorner(preferencesController.getPreferences().getInt("ocr_settings_nudge_corner", this.NUDGE_CORNER_BOTTOM_RIGHT));
    this.setTextOrientation(preferencesController.getPreferences().getInt("ocr_settings_text_orientation", this.TEXT_ORIENTATION_VERTICAL));
  }
  
  
  /** Store OCR-related settings. */
  private void storeOcrSettings()
  {
    this.preferencesController.getPreferences().edit().putInt("ocr_settings_nudge_corner", this.nudgeCorner).commit();
    this.preferencesController.getPreferences().edit().putInt("ocr_settings_text_orientation", this.textOrientation).commit();
  }
  
  
  /** Set visibility of all buttons (existing for the Hide/Show button) */
  private void setGuiButtonVisi(int visi)
  {
    if(this.ocrSettingsShowNudgeButtons)
    {
      this.btnSwapNudgeCorner.setVisibility(visi);
      this.btnUp.setVisibility(visi);
      this.btnDown.setVisibility(visi);
      this.btnLeft.setVisibility(visi);
      this.btnRight.setVisibility(visi);
    }
    else
    {
      this.btnSwapNudgeCorner.setVisibility(INVISIBLE);
      this.btnUp.setVisibility(INVISIBLE);
      this.btnDown.setVisibility(INVISIBLE);
      this.btnLeft.setVisibility(INVISIBLE);
      this.btnRight.setVisibility(INVISIBLE);
    }

    if(this.showSimplifiedInterface())
    {
      // Remove button visibility for the other buttons
      visi = GONE;
    }
    
    if(this.ocrSettingsShowTextOrientationButton)
    {
      this.btnTextOrientation.setVisibility(visi);
    }
    else
    {
      this.btnTextOrientation.setVisibility(INVISIBLE);
    }
    
    if(this.ocrSettingsShowSendButton)
    {
      this.btnSend.setVisibility(visi);
    }
    else
    {
      this.btnSend.setVisibility(INVISIBLE);
    }
    
  }


  /** Toggle visibility of all OCR view GUI elements, with the exception of the Show/Hide button. */
  public void toggleHideGui()
  {
    this.setHideGui(!this.hideGui);
  }
  
  
  /** Hide/Show all OCR view GUI elements, with the exception of the Show/Hide button. */
  public void setHideGui(boolean hide)
  {
    this.hideGui = hide;
    int visi = GONE;
    
    if(hide)
    {
      visi = GONE;
    }
    else // Show GUI
    {
      visi = VISIBLE;
    }

    this.btnLookupNext.setVisibility(visi);
    this.btnLookupPrev.setVisibility(visi);
    this.ocrView.setVisibility(visi);
    this.dicView.setVisibility(visi);
    this.setGuiButtonVisi(visi);
  }
  
  
  /** Swap nudge corner between top-left and bottom-right. */
  public void swapNudgeCorner()
  {
    int newNudgeCorner = this.NUDGE_CORNER_BOTTOM_RIGHT;
    
    if(this.nudgeCorner == this.NUDGE_CORNER_BOTTOM_RIGHT)
    {
      newNudgeCorner =  this.NUDGE_CORNER_TOP_LEFT;
    }
    else // Top-left
    {
      newNudgeCorner =  this.NUDGE_CORNER_BOTTOM_RIGHT;
    }
    
    this.setNudgeCorner(newNudgeCorner);
  }
  
  
  /** Set the corner that the nudge buttons will adjust. */
  private void setNudgeCorner(int newNudgeCorner)
  {
    this.nudgeCorner = newNudgeCorner;
    
    if(this.btnSwapNudgeCorner == null)
    {
      return;
    }
    
    if(this.nudgeCorner == this.NUDGE_CORNER_BOTTOM_RIGHT)
    {
      this.btnSwapNudgeCorner.setText(R.string.btn_ocr_diag_bottom_right);
    }
    else // Top-left
    {
      this.btnSwapNudgeCorner.setText(R.string.btn_ocr_diag_top_left);
    }
  }
  
  
  /** Switch between vertical/horizontal/auto text orientation for OCR. */
  public void switchTextOrientation()
  {
    if(this.textOrientation == this.TEXT_ORIENTATION_HORIZONTAL)
    {
      this.setTextOrientation(this.TEXT_ORIENTATION_AUTO);
    }
    else if(this.textOrientation == this.TEXT_ORIENTATION_VERTICAL)
    {
      this.setTextOrientation(this.TEXT_ORIENTATION_HORIZONTAL);
    }
    else // TEXT_ORIENTATION_AUTO
    {
      this.setTextOrientation(this.TEXT_ORIENTATION_VERTICAL);
    }
  }
  
  
  /** Set text orientation. */
  private void setTextOrientation(int val)
  {
    this.textOrientation = val;
    
    if(this.btnTextOrientation == null)
    {
      return;
    }
    
    if(this.textOrientation == this.TEXT_ORIENTATION_HORIZONTAL)
    {
      this.btnTextOrientation.setText(getResources().getString(R.string.btn_ocr_text_orientation_horizontal));
    }
    else if(this.textOrientation == this.TEXT_ORIENTATION_VERTICAL)
    {
      this.btnTextOrientation.setText(getResources().getString(R.string.btn_ocr_text_orientation_vertical));
    }
    else // TEXT_ORIENTATION_AUTO
    {
      this.btnTextOrientation.setText(getResources().getString(R.string.btn_ocr_text_orientation_auto));
    }
    
    this.forceCaptureUpdate();
  }
 

  /** Get list of options to use with the OCR Send Dialog, */
  public List<String> createOcrSendList()
  {
    List<String> optList = new ArrayList<String>();

    // Add the options to the list
    optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_clipboard));
    optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_error_correction_editor));
    
    File wordListSaveFile = new File(this.ocrSettingsWordListSaveFilePath);
    
    if((this.ocrSettingsWordListSaveFilePath.length() > 0) 
        && wordListSaveFile.exists() 
        && !wordListSaveFile.isDirectory())
    {
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_word_list_save_file));
    }
    
    if (IntentUtils.isIntentAvailable(context, "sk.baka.aedict.action.ACTION_SEARCH_EDICT"))
    {
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_aedict));
    }
    
    if (IntentUtils.isIntentAvailable(context, "org.openintents.action.CREATE_FLASHCARD"))
    {
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_ankidroid));
    }
    
    if (IntentUtils.isIntentAvailable(context, "android.intent.action.VIEW"))
    {
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_eijiro_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_goo_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_sanseido_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_yahoo_je_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_yahoo_jj_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_google_web));
      optList.add(this.context.getResources().getString(R.string.ocr_send_dialog_opt_google_images_web));
    }
    
    return optList;
  }
  
  
  /** Show the OCR Send Dialog. */
  public void showOcrSendDialog()
  {    
    List<String> optList = createOcrSendList();  
    CharSequence[] optCharSeq = optList.toArray(new CharSequence[optList.size()]);
 
    new AlertDialog.Builder(this.context)
        .setIcon(android.R.drawable.ic_menu_info_details)
        .setTitle(R.string.ocr_send_dialog_title)
        .setItems(optCharSeq, new DialogInterface.OnClickListener()
           {
             public void onClick(DialogInterface dialog, int which) 
             {
               sendOcrText(which);
             }
           })
        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface dialog, int whichButton)
            {
            }
          })
       .show();
  }
  
   
  /** Send the OCR'd text to the provided destination. */
  public void sendOcrText(int sendDest)
  {
    List<String> optList = this.createOcrSendList(); 
    
    if(sendDest < optList.size())
    {
      String sendDestStr = optList.get(sendDest);
      this.performSendAction(sendDestStr);
    }
  }
  
  
  public void performSendAction(String sendDestStr)
  {
    String textToSend = this.lastOcrText;
    Entry entry = null;
    
    /** If a word was found, use it */
    if((this.lastEntryList != null) && (this.lastEntryList.size() > 0))
    {
      entry = this.lastEntryList.get(0);
      textToSend = entry.Expression;
    }
          
    if((entry != null) 
        && sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_word_list_save_file)))
    {
      this.sendToWordListSaveFile(entry);
    }
    else if(textToSend.length() > 0)
    {      
      // Determine the option that was selected
      if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_clipboard)))
      {
        this.sendToClipboard(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_error_correction_editor)))
      {
        this.sendToErrorCorrectionDialog(this.lastOcrText); // Always edit the full OCR text
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_aedict)))
      {
        this.sendToAedict(textToSend);    
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_ankidroid)))
      {
        if(entry != null)
        {
          this.sendToAnkiDroid(entry);
        }
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_eijiro_web)))
      {
        this.sendToEijiroWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_goo_web)))
      {
        this.sendToGooWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_sanseido_web)))
      {
        this.sendToSanseidoWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_yahoo_je_web)))
      {
        this.sendToYahooJeWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_yahoo_jj_web)))
      {
        this.sendToYahooJjWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_google_web)))
      {
        this.sendToGoogleWeb(textToSend);
      }
      else if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_google_images_web)))
      {
        this.sendToGoogleImagesWeb(textToSend);
      }
    }
    else if(textToSend.length() == 0)
    {
      if(sendDestStr.equals(this.context.getResources().getString(R.string.ocr_send_dialog_opt_error_correction_editor)))
      {
        this.sendToErrorCorrectionDialog(this.lastOcrText); // Always edit the full OCR text
      }
    }
  }
  
  
  /** Send the provided text to the clipboard. */
  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  private void sendToClipboard(String text)
  {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
    {
      android.text.ClipboardManager clipboard = (android.text.ClipboardManager)this.context.getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setText(text);
    }
    else
    {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager)this.context.getSystemService(Context.CLIPBOARD_SERVICE);
      android.content.ClipData clip = android.content.ClipData.newPlainText("OCR Manga Reader Text", text);
      clipboard.setPrimaryClip(clip);
    }
  }  
  
  
  /** Send the provided text to the OCR Error Correction dialog. */
  private void sendToErrorCorrectionDialog(String text)
  {
    final EditText input = new EditText(this.context);
    input.setText(text);

    new AlertDialog.Builder(this.context)
      .setTitle(this.context.getResources().getString(R.string.ocr_error_correction_dialog_title))
      .setMessage(this.context.getResources().getString(R.string.ocr_error_correction_dialog_msg))
      .setView(input)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int whichButton)
        {          
          lastOcrText = input.getText().toString();
          
          // Reset the prev/next history
          lookupWordIdxStack = new Stack<Integer>();
          lookupWordIdxStack.push(0);
          
          updateDicViewText();
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int whichButton)
        {

        }
      })
      .show();
  }

  /** Save the entry to the word list save file. */
  private void sendToWordListSaveFile(Entry entry)
  {
    String entryText = "";
    
    if(this.ocrSettingsWordListSaveFileFormat.contains("Expression"))
    {
      entryText += entry.Expression + "\t";
    }
    
    if(this.ocrSettingsWordListSaveFileFormat.contains("Reading"))
    {
      entryText += entry.Reading + "\t";
    }
    
    if(this.ocrSettingsWordListSaveFileFormat.contains("Definition"))
    {
      entryText += entry.Definition + "\t";
    }
    
    if(this.ocrSettingsWordListSaveFileFormat.contains("Frequency"))
    {
      String freqStr = this.formatGlossFrequency(entry);
      freqStr = freqStr.replaceFirst("<span.*?>", "").replaceFirst("</span>", "").trim();
      
      entryText += freqStr;
    }
    
    entryText = entryText.trim();

    try 
    {
      PrintWriter out = new PrintWriter(new BufferedWriter(
          new FileWriter(this.ocrSettingsWordListSaveFilePath, true)));
      out.println(entryText);
      out.close();
    
      Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show(); 
    }
    catch(IOException e) 
    {
      Toast.makeText(context, "Save failed!", Toast.LENGTH_SHORT).show();  
    }
  }
  
  /** Send the provided text to Aedict. */
  private void sendToAedict(String text)
  {
    if (IntentUtils.isIntentAvailable(context, "sk.baka.aedict.action.ACTION_SEARCH_EDICT"))
    {
      Intent intent = new Intent("sk.baka.aedict.action.ACTION_SEARCH_EDICT");
      intent.putExtra("kanjis", text);    
      this.context.startActivity(intent);
    }
  }


  /**
   * @param entry
   * @author Marlon Paulse
   */
  private void sendToAnkiDroid(final Entry entry) {
    if (AnkiUtils.isApiAvailable(context)) {
      if (AnkiUtils.haveApiPermissions(context)) {
        ActivityCompat.requestPermissions(comicViewerActivity, new String[]{AddContentApi.READ_WRITE_PERMISSION}, ANKI_RW_PERM_REQ_CODE);
        return;
      }

      String deckName =
              preferencesController.getPreferences().getString(
                      PreferencesController.PREFERENCE_ANKI_DECK,
                      AnkiUtils.getDefaultDeck());
      long deckId = AnkiUtils.getDeckID(deckName, context);
      if (deckId < 0) {
        showErrorDialog(context.getString(R.string.ocr_send_anki_deck_not_found) + " " + deckName);
        return;
      }

      final String modelName =
              preferencesController.getPreferences().getString(
                      PreferencesController.PREFERENCE_ANKI_MODEL,
                      AnkiUtils.getDefaultModel());
      long modelId = AnkiUtils.getModelID(modelName, context);
      if (modelId < 0) {
        showErrorDialog(context.getString(R.string.ocr_send_anki_model_not_found) + " " + modelName);
        return;
      }

      final String[] fields = AnkiUtils.getModelFields(modelId, context);
      if (fields == null) {
        showErrorDialog(context.getString(R.string.ocr_send_anki_model_fields_not_found) + " " + modelName);
        return;
      }

      String egSentence = "";
      if (entry.ExampleList != null && entry.ExampleList.size() > 0) {
        egSentence = entry.ExampleList.get(0).Text;
      }

      String[] info = { entry.Expression, entry.Reading, entry.Definition, "", egSentence };
      final String[] values = new String[fields.length];
      for (int i = 0; i < values.length; i++) {
        int fieldType = PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_UNUSED_INT;
        try {
          fieldType =
                  Integer.parseInt(
                          preferencesController.getPreferences().getString(
                                  PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_PREFIX + modelName + "_" + fields[i],
                                  String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_UNUSED_INT)));
        } catch (NumberFormatException e) {
        }
        values[i] = (fieldType >= 0 && fieldType < info.length) ? info[fieldType] : "";
      }

      if (preferencesController.getPreferences().getBoolean(PreferencesController.PREFERENCE_ANKI_CONFIRM_SEND, true)) {
        AnkiSendDialogFragment dialog = AnkiSendDialogFragment.newInstance(deckId, deckName, modelId, modelName, entry.Expression, fields, values);
        dialog.show(comicViewerActivity.getFragmentManager(), "ankiSend");
      } else {
        addAnkiCard(deckId, modelId, entry.Expression, values);
      }
    } else if (IntentUtils.isIntentAvailable(context, "org.openintents.action.CREATE_FLASHCARD")) {
      Intent intent = new Intent("org.openintents.action.CREATE_FLASHCARD");

      // String, language code of the first side
      intent.putExtra("SOURCE_LANGUAGE", "ja");

      //  String, language code of the second side
      intent.putExtra("TARGET_LANGUAGE", "en");

      // Text of the first side

      String sourceText = entry.Expression;
      String reading = entry.Reading;

      if (reading.length() > 0) {
        sourceText += " [" + reading + "]";
      }

      intent.putExtra("SOURCE_TEXT", sourceText);

      // Text of the second side
      intent.putExtra("TARGET_TEXT", entry.Definition);

      this.context.startActivity(intent);
    }
  }

  public void addAnkiCard(long deckId, long modelId, String modelKey, String[] fieldValues) {
    if (AnkiUtils.addCard(deckId, modelId, modelKey, fieldValues, context)) {
      Toast.makeText(context, R.string.ocr_send_anki_success, Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(context, R.string.ocr_send_anki_card_already_exists, Toast.LENGTH_SHORT).show();
    }
  }
  
  /** Send the provided text to Eijiro J-E Web. */
  private void sendToEijiroWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("http://eow.alc.co.jp/" + text));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to goo jisho J-E/J-J Web. */
  private void sendToGooWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://dictionary.goo.ne.jp/srch/all/" + text + "/m1u/"));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to Sanseido J-J Web. */
  private void sendToSanseidoWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://www.sanseido.net/User/Dic/Index.aspx?TWords=" + text + "&st=0&DailyJJ=checkbox"));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to Yahoo Jisho J-E Web. */
  private void sendToYahooJeWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://dic.search.yahoo.co.jp/dsearch?p=" + text + "&dic_id=ejje&stype=prefix"));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to Yahoo Jisho J-J Web */
  private void sendToYahooJjWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://dic.search.yahoo.co.jp/dsearch?p=" + text + "&dic_id=jj&stype=prefix&b=1"));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to Google Web */
  private void sendToGoogleWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://www.google.com/search?q=" + text + "&hl=en&lr=lang_ja"));
    this.context.startActivity(intent);
  }
  
  
  /** Send the provided text to Google Images J-J Web */
  private void sendToGoogleImagesWeb(String text)
  {
    Intent intent = new Intent("android.intent.action.VIEW", 
        Uri.parse("http://images.google.com/images?q=" + text + "&hl=en"));
    this.context.startActivity(intent);
  }
  
  
  /** Force the timer task to perform a capture and OCR. */
  private void forceCaptureUpdate()
  {
    if(this.captureBox != null)
    {
      this.forceUpdate = true; // Note: will be unset by the timer task
    }
  }
  

  /** Determine the quadrant of the screen that the provided coordinates are in. */
  private Quadrant determineQuadrant(int x, int y)
  {
    Quadrant quadrant = Quadrant.BOTTOM_LEFT;
    
    int centerX = this.screenWidth / 2;
    int centerY = this.screenHeight / 2;
    
    // If left half
    if(x <= centerX)
    {
      if(y <= centerY) // Top half
      {
        quadrant = Quadrant.TOP_LEFT;
      }
      else // Bottom half
      {
        quadrant = Quadrant.BOTTOM_LEFT;
      }
    }
    else // Right half
    {
      if(y <= centerY) // Top half
      {
        quadrant = Quadrant.TOP_RIGHT;
      }
      else // Bottom half
      {
        quadrant = Quadrant.BOTTOM_RIGHT;
      }
    }
    
    return quadrant;
  }
    
 
  /** Determine dictionary view location based on the capture box location and size. */
  private DicViewLocation determineDicViewLocation()
  {
    Rect r = this.captureBox;
    
    DicViewLocation loc = DicViewLocation.BOTTOM_LEFT;
    
    Quadrant qP1 = this.determineQuadrant(r.left, r.top);
    Quadrant qP2 = this.determineQuadrant(r.right, r.bottom);
    
    //    (qP1 == Quadrant.TOP_RIGHT)   
    
    if(qP1 == Quadrant.TOP_LEFT)
    {
      if(qP2 == Quadrant.TOP_LEFT)
      {
        loc = DicViewLocation.BOTTOM;
      }
      else if(qP2 == Quadrant.TOP_RIGHT)
      {
        loc = DicViewLocation.BOTTOM;
      }
      else if(qP2 == Quadrant.BOTTOM_LEFT)
      {
        loc = DicViewLocation.RIGHT;
      }
      else // BOTTOM_RIGHT
      {
        // Capture box spans all quadrants, so choose location least likely to interfere.
        loc = DicViewLocation.TOP;
      }
    }
    else if(qP1 == Quadrant.TOP_RIGHT)
    {
      loc = DicViewLocation.LEFT;
    }
    else if(qP1 == Quadrant.BOTTOM_LEFT)
    {
      loc = DicViewLocation.TOP;
    }
    else // BOTTOM_RIGHT
    {
      loc = DicViewLocation.TOP;
    }
    
    return loc;
  }
  
  
  /** Convert a DicViewLocation to a Rect. */
  private Rect dicViewLocationToRect(DicViewLocation loc)
  {
    Rect r;
    
    int sw = this.screenWidth;
    int sh = this.screenHeight;
    int cx = sw / 2;
    int cy = sh / 2;
    
    switch(loc)
    {
      case TOP_LEFT:
        r = new Rect(0, 0, cx, cy);
        break;
      case TOP:
        r = new Rect(0, 0, sw, cy);
        break;
      case TOP_RIGHT:
        r = new Rect(cx, 0, sw, cy);
        break;
      case LEFT:
        r = new Rect(0, 0, cx, sh);
        break;
      case RIGHT:
        r = new Rect(cx, 0, sw, sh);
        break;
      case BOTTOM_LEFT:
        r = new Rect(0, cy, cx, sh);
        break;
      case BOTTOM:
        r = new Rect(0, cy, sw, sh);
        break;
      case BOTTOM_RIGHT:
        r = new Rect(cx, cy, sw, sh);
        break;
      default:
        r = new Rect(0, cy, cx, sh);
        break;
    }
    
    return r;
  }
  
  
  /** Update the location of the dictionary view based on the location of the capture box. */
  @SuppressLint("NewApi")
  public void updateDicViewLocation()
  {
    if(this.captureBox != null)
    {
      DicViewLocation loc = this.determineDicViewLocation();
      Rect rect = this.dicViewLocationToRect(loc);
      
      int width = rect.width();
      int height = rect.height();
      
      LayoutParams layoutParams = new LayoutParams(width, height);
      
      if(android.os.Build.VERSION.SDK_INT >= 11)
      {
        this.dicView.setLayoutParams(layoutParams);
        this.dicView.setX(rect.left);
        this.dicView.setY(rect.top);
      }
      else
      {
        layoutParams.leftMargin = rect.left;
        layoutParams.topMargin = rect.top;
        this.dicView.setLayoutParams(layoutParams);
      }
    }
  }
  
  
  /** Add EPWING dictionary to the provided list based on the provided CATALOGS file */
  private void addEpwingDicToListIfValid(List<Dic> dicList, String catalogsFile)
  {
    if(catalogsFile.length() > 0)
    {
      File file = new File(catalogsFile);
      
      if(file.exists())
      {
        Dic tempDic = null;
        
        try
        {
          if(ocrSettingsEpwingParse)
          {
            tempDic = DicEpwing.createEpwingDic(catalogsFile);
          }
          else
          {
            tempDic = new DicEpwingRaw(catalogsFile, 0);
          }
        }
        catch(Exception e)
        {
          Log.e(LOG_TAG, "Exception in addEpwingDicToListIfValid()! " + e);
        }
        
        if(tempDic != null)
        {
          dicList.add(tempDic);
        }
      }
    }
  }
   
  
  /** Get list containing EPWING dictionaries */
  private List<Dic> getEpwingDicList()
  {
    List<Dic> dicList = new LinkedList<Dic>();
    
    this.addEpwingDicToListIfValid(dicList, this.ocrSettingsEpwingDic1);
    this.addEpwingDicToListIfValid(dicList, this.ocrSettingsEpwingDic2);
    this.addEpwingDicToListIfValid(dicList, this.ocrSettingsEpwingDic3);
    this.addEpwingDicToListIfValid(dicList, this.ocrSettingsEpwingDic4);
        
    return dicList;
  }
  
  
  /** After the text has been OCR'd, this may be invoked to advance to the next word. */
  public void lookupNextWord()
  {
    try
    {
      // If the current word is in the dictionary, advance to the next word
      if((this.lastEntryList != null) && (this.lastEntryList.size() > 0))
      {
        String curWord = this.lastEntryList.get(0).Inflected;
        Integer newIdx = this.lookupWordIdxStack.peek() + curWord.length();
        
        if(newIdx < this.lastOcrText.length())
        {
          this.lookupWordIdxStack.push(newIdx);
          this.updateDicViewText();
        }
      }
      else // Current word is not in the dictionary, advance one character
      {
        Integer newIdx = this.lookupWordIdxStack.peek() + 1;
        
        if(newIdx < this.lastOcrText.length())
        {
          this.lookupWordIdxStack.push(newIdx);
          this.updateDicViewText();
        }
      }
    }
    catch (Exception e)
    {
      Log.e(LOG_TAG, "Exception in lookupPrevWord()! " + e);
    }
  }
  
  
  /** After the text has been OCR'd, this may be invoked to go back to the previous word. */
  public void lookupPrevWord()
  {
    try
    {
      // If we can go back, then go back
      if(this.lookupWordIdxStack.size() > 1)
      {
        this.lookupWordIdxStack.pop();
        this.updateDicViewText();
      }
    }
    catch(Exception e)
    {
      Log.e(LOG_TAG, "Exception in lookupPrevWord()! " + e);
    }
  }
  
  
  /** Update dictionary view with a lookup of lastOcrText. */
  private void updateDicViewText()
  {
    try
    {
      final String ocrPrefix = getResources().getString(R.string.ocr_prefix); 
      final String noResultsMsg = getResources().getString(R.string.ocr_no_results); 
      final String noEntriesMsg = getResources().getString(R.string.ocr_no_dict_entries); 
      final String ocrTextHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictOcrTextColor));
      final String separatorHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictSeparatorColor));
      final String highlightColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsWordHighlightColor));
      
      String htmlText = String.format("<html><body><span lang='ja' style='color: %s'>%s ", ocrTextHtmlColor, ocrPrefix);
      
      // If no OCR results were found
      if(this.lastOcrText.length() == 0)
      {
        htmlText += noResultsMsg + "</span></body></html>";
      }
      else // OCR results were found
      {
        int curRawLookupIdx = this.lookupWordIdxStack.peek();
        String curRawLookup = this.lastOcrText.substring(curRawLookupIdx);
        
        this.lastEntryList = new ArrayList<Entry>();

        // Lookup first word in dictionary
        List<Entry> edictEntryList = OcrLayout.dicEdict.searchWord(curRawLookup, 0);
        
        if(edictEntryList == null)
        {
          edictEntryList = new ArrayList<Entry>(); 
        }
         
        // Add entries from each EPWING dictionary to epwingEntryList
        List<Entry> epwingEntryList = new ArrayList<Entry>();       
 
        if(edictEntryList.size() > 0)
        {
          String firstWord = "";
          
          // If the first captured word is all kana, don't lookup the kanjified form of the word.
          // Example1: if の is highlighted, use の rather than the kanji equivalent (乃)
          // Example2: if された is highlighted, use される rather then 為れる  
          if(!UtilsLang.containsIdeograph(edictEntryList.get(0).Inflected))
          {
            firstWord = edictEntryList.get(0).Reading;
          }
          else
          {
            firstWord = edictEntryList.get(0).Expression;
          }
          
          List<Dic> epwingDicList = this.getEpwingDicList();
                  
          FineTune fineTune = new FineTune();
          fineTune.JjKeepExamplesInDef =  !this.ocrSettingsEpwingStripExamplesFromDefs;
                 
          // For each EPWING dictionary
          for(Dic dic : epwingDicList)
          {
            if(dic instanceof DicEpwing)
            {
              try
              {
                List<Entry> entryList = dic.lookup(firstWord, true, fineTune);
  
                if(entryList != null)
                {
                  epwingEntryList.addAll(entryList); 
                }
              }
              catch(Exception e)
              {
                Log.e(LOG_TAG, "Exception in updateDicViewText() when searching EPWING dics! " + e);
              }
            }
          }
          
          if(epwingEntryList.size() > 0)
          {
            // Save Inflected from the first Edict entry into the first EPWING entry. 
            // It's used for next/previous word functionality.
            epwingEntryList.get(0).Inflected = edictEntryList.get(0).Inflected;
          }
        }
        
        this.lastEntryList.addAll(epwingEntryList);
        this.lastEntryList.addAll(edictEntryList);
        
        List<Entry> namesList = null;
        
        // Lookup names and add them to lastEntryList 
        if(OcrLayout.dicNames.isDatabaseLoaded())
        {
          namesList = OcrLayout.dicNames.searchWord(curRawLookup, 10);
          
          if((namesList != null) && (namesList.size() != 0))
          {
            if(this.lastEntryList.size() == 0)
            {
              this.lastEntryList = namesList;
            }
            else
            {
              this.lastEntryList.addAll(namesList);
            }
          }
        }
        
        List<Entry> kanjiList = null;
        
        // Lookup kanji and add them to lastEntryList 
        if(OcrLayout.dicKanji.isDatabaseLoaded())
        {
          kanjiList = OcrLayout.dicKanji.lookup(curRawLookup, false, null);
          
          if((kanjiList != null) && (kanjiList.size() != 0))
          {
            if(this.lastEntryList.size() == 0)
            {
              this.lastEntryList = kanjiList;
            }
            else
            {
              this.lastEntryList.addAll(kanjiList);
            }
          }
        }  
        
        // If no dictionary entries were found
        if(this.lastEntryList.size() == 0)
        {
          String leadingText = this.lastOcrText.substring(0, curRawLookupIdx);
          String highlightedWord = this.lastOcrText.substring(curRawLookupIdx, curRawLookupIdx + 1);
          String trailingText = this.lastOcrText.substring(curRawLookupIdx + 1);
          
          htmlText += String.format("%s<span lang='ja' style='background-color: %s'>%s</span>%s</span><br />",
              leadingText, highlightColor, highlightedWord, trailingText);
          
          htmlText += String.format("<span style='color:LightGray'>%s</ span>", noEntriesMsg);
        }
        else // One or more dictionary entries were found
        {        
          String leadingText = this.lastOcrText.substring(0, curRawLookupIdx);
          String highlightedWord = this.lastOcrText.substring(curRawLookupIdx, curRawLookupIdx + this.lastEntryList.get(0).Inflected.length());
          String trailingText = this.lastOcrText.substring(curRawLookupIdx + this.lastEntryList.get(0).Inflected.length());
          
          htmlText += String.format("%s<span lang='ja' style='background-color: %s'>%s</span>%s</span><br />",
            leadingText, highlightColor, highlightedWord, trailingText);
            
          // Add each entry to the HTML
          for(int i = 0; i < this.lastEntryList.size(); i++)
          {
            String entryText = this.entryToHtml(this.lastEntryList.get(i));
            htmlText += entryText;
            
            // If this is not the final entry, add a line
            if(i != (this.lastEntryList.size() - 1))
            {
              htmlText += String.format("<hr style='border-color: %s'>", separatorHtmlColor);
            }
          }
        }
        
        htmlText += "</body></html>";
      }
  
      this.dicView.setBackgroundColor(this.ocrSettingsDictBackgroundColor);
      this.dicView.loadDataWithBaseURL(null, htmlText, "text/html", "utf-8", null);
    }
    catch(Exception e)
    {
      Log.e(LOG_TAG, "Exception in updateDicViewText()! " + e);
    }
  }
  
  
  /** Format marker for known/todo words */
  private String formatKnownWordMarker(Entry entry)
  {
    String knownWordColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsKnownWordColor));
    
    String markerText = "";
    
    if(this.wordSetKnown.isWordInSet(entry.Expression))
    {
      markerText = "*"; 
    }
    else if(this.wordSetTodo.isWordInSet(entry.Expression))
    {
      markerText = "*t"; 
    }
    else if(this.wordSetKnown.isWordInSet(entry.Reading))
    {
      markerText = "*r"; 
    }
    else if(this.wordSetTodo.isWordInSet(entry.Reading))
    {
      markerText = "*tr";
    }
    
    String html = String.format(Locale.US, " <span style='color: %s'>%s</span>", knownWordColor, markerText);
     
    return html;
  }
    
  
  /** Format a frequency for the gloss. */
  private String formatGlossFrequency(Entry entry)
  {
    String freq = "";
    boolean freqBasedOnReading = false;
    int freqNum = -1;

    // Get all of the expression/reading pairs
    List<Entry> combos = UtilsCommon.getExpressionReadingCombinations(entry.Expression, entry.Reading);

    // Use the frequency of the first expression that has frequency information
    for (Entry comboEntry : combos)
    {
      int readingFreqNum = OcrLayout.freqDb.getFrequency(comboEntry.Reading);
      boolean readingSameAsExpression = comboEntry.Expression.equals(comboEntry.Reading);
      int expressionFreqNum = readingFreqNum;
      
      if(!readingSameAsExpression)
      {
        expressionFreqNum = OcrLayout.freqDb.getFrequency(comboEntry.Expression);
      }
      
      // If neither the reading nor the expression is in the freq db
      if((expressionFreqNum == -1) && (readingFreqNum == -1))
      {
        continue;
      }
      
      // If the highlighted word does contain kanji
      if(!readingSameAsExpression
          && !UtilsLang.containsIdeograph(entry.Inflected) 
          && (readingFreqNum != -1)
          && (OcrLayout.dicEdict.getReadingCount(comboEntry.Reading) == 1))
      {
          freqNum = readingFreqNum;
          freqBasedOnReading = true;
          break;
      }
      
      if(readingSameAsExpression
          && (readingFreqNum != -1))
      {
        freqNum = readingFreqNum;
        break;
      }
      
      // If the expression is in the freq db
      if(expressionFreqNum != -1)
      {
        freqNum = expressionFreqNum;
        break;
      }
      
      // If the reading is in the freq db
      if(readingFreqNum != -1)
      {
        freqNum = readingFreqNum;
        freqBasedOnReading = true;
        break;
      }
    }

    // If frequency was found
    if (freqNum != -1)
    {
      String veryCommonColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsFreqVeryCommonColor));
      String commonColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsFreqCommonColor));
      String uncommonColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsFreqUncommonColor));
      String rareColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsFreqRareColor));
      
      // Determine style to use
      String freqColor = rareColor;

      if (freqNum <= 5000)
      {
        freqColor = veryCommonColor;
      }
      else if (freqNum <= 10000)
      {
        freqColor = commonColor;
      }
      else if (freqNum <= 20000)
      {
        freqColor = uncommonColor;
      }

      // If frequency was based on the reading
      if (freqBasedOnReading)
      {
        // Indicate that frequency was based on the reading by adding "_r" to the end of the frequency
        freq = String.format(Locale.US, " <span style='color: %s'>%d_r</span>", freqColor, freqNum); 
      }
      else
      {
        freq = String.format(Locale.US, " <span style='color: %s'>%s</span>", freqColor, freqNum);  
      }
    }

    return freq;
  }
  

  /** Convert dictionary entry to HTML. */
  private String entryToHtml(Entry entry)
  {
    String html = "";
    String expressionHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictExpressionColor));
    String readingHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictReadingColor));
    String conjugationHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictConjugationColor));
    String definitionHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictDefinitionColor));
    String subdefHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictSubDefColor));
    String exPrependHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictExamplePrependColor));
    String exJapHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictExampleJapColor));
    String exEngHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictExampleEngColor));
    String dicNameHtmlColor = String.format("#%06X", (0xFFFFFF & this.ocrSettingsDictNameColor));
    
    boolean isEpwing = (entry.SourceDic instanceof DicEpwing);
    boolean isEpwingRaw = (entry.SourceDic instanceof DicEpwingRaw);
 
    if(isEpwingRaw)
    {
      if ((entry.SourceDic != null) && (entry.SourceDic.ShortName.length() != 0))
      {
        html += String.format(" <span style='color: %s'>『%s』</span>", dicNameHtmlColor, entry.SourceDic.ShortName);
        html += "<br />";
      }
    }
    else // EDICT or names or parsed EPWING dictionary
    {
      html += String.format("<span lang='ja' style='color: %s'>%s</span>", expressionHtmlColor, entry.Expression);
      
      if (entry.Reading.length() != 0)
      {
        html += String.format(" <span lang='ja' style='color: %s'>%s</span>", readingHtmlColor,  entry.Reading);
      }
         
      html += this.formatKnownWordMarker(entry);
      
      if(this.ocrSettingsShowFrequency)
      {
        html += this.formatGlossFrequency(entry);
      }
      
      if (entry.DeinflectionRule.length() != 0)
      {
        html += String.format(" <span style='color: %s'>(%s)</span>", conjugationHtmlColor, entry.DeinflectionRule);
      }
      
      if ((entry.SourceDic != null) && (entry.SourceDic.ShortName.length() != 0))
      {
        html += String.format(" <span style='color: %s'>『%s』</span>", dicNameHtmlColor, entry.SourceDic.ShortName);
      }
      
      html += "<br />";
    }
        
    // Format definition
    String definition = entry.Definition;
    
    if(isEpwing)
    {
      // If the definition is blank (for example 直接 in Ken5), set the definition to the first example sentence
      if(definition.trim().length() == 0)
      {
        if(entry.ExampleList.size() > 0)
        {
          definition = this.formatSingleExample(entry.ExampleList.get(0), exJapHtmlColor, exEngHtmlColor, exPrependHtmlColor);
          
          // Remove this example so that it won't be used again
          entry.ExampleList.remove(0);
        }
      }
     
      String[] defLines = definition.split("<br />");

      if (defLines.length > ocrSettingsEpwingMaxDefLines)
      {
        definition = "";

        for (int lineIdx = 0; lineIdx < ocrSettingsEpwingMaxDefLines; lineIdx++)
        {
          definition += defLines[lineIdx];

          if (lineIdx != ocrSettingsEpwingMaxDefLines - 1)
          {
            definition += "<br />";
          }
        }
      }
      
      if(ocrSettingsEpwingCompactDefinitions)
      {
        definition = definition.replaceAll("<br />", " ");
      }
    }
    else
    {
      if(!this.ocrSettingsEdictCompactDefinitions)
      {
        definition = definition.replaceAll("([①-⑳㉑-㉟㊱-㊿])", "<br />$1");
      }
    }
    
    definition = definition.replaceAll("([①-⑳㉑-㉟㊱-㊿])",
        String.format("<span style='color: %s'>%s</span>", subdefHtmlColor, "$1"));
    
    html += String.format("<span style='color: %s'>%s</span>", definitionHtmlColor, definition);
    
    // Format examples
    if(isEpwing && !isEpwingRaw)
    {
      if(ocrSettingsEpwingShowExamples)
      {
        List<Example> exampleList = Example.getBestExamples(entry.Expression, entry.ExampleList, ocrSettingsEpwingMaxExamples);
        String exampleStr = "<br />";
        
        for (int lineIdx = 0; lineIdx < exampleList.size(); lineIdx++)
        {
          exampleStr += this.formatSingleExample(exampleList.get(lineIdx), exJapHtmlColor, exEngHtmlColor, exPrependHtmlColor);

          if (lineIdx != exampleList.size() - 1)
          {
            exampleStr += "<br />";
          }
        }
        
        if(ocrSettingsEpwingCompactExamples)
        {
          exampleStr = "<br />" + exampleStr.replaceAll("<br />", " ");
        }
        
        html += exampleStr;
      }
    }
    
    return html;
  }
  

  /** Format the string for a single example. */
  private String formatSingleExample(Example example, String japColor, String engColor, String prependColor)
  {
    String exampleText = example.Text; 
 
    String[] fields = exampleText.split("\t");

    String japText = "";
    String engText = "";
 
    japText = String.format("<span style='color: %s'>%s</span>", 
        japColor, UtilsFormatting.addPunctuationToJapText(fields[0].trim()));
    
    if (fields.length == 2)
    {
      engText = String.format("<span style='color: %s'>%s</span>", 
          engColor, UtilsFormatting.addPunctuationToEngText(fields[1].trim()));
    }

    exampleText = String.format("%s %s", japText, engText).trim();

    // Get rid of any left over tabs
    exampleText = exampleText.replaceAll("\t", "");

    exampleText = String.format("<span style='color: %s'>▲</span>%s", prependColor, exampleText);

    return exampleText;
  }
  
  
  /** Is the capture box currently being adjusted? */
  private boolean isCaptureBoxBeingAdjusted()
  {
    return ((Calendar.getInstance().getTimeInMillis() - this.lastAdjustment.getTimeInMillis()) < 200);
  }
  
  
  /** Returns true is the provided capture boxes have equal dimensions. */
  public boolean isCaptureBoxEqual(Rect captureBoxOld, Rect captureBoxNew)
  {
    if((captureBoxOld == null) || (captureBoxNew == null))
    {
      return false;
    }
    
    return ((captureBoxOld.bottom == captureBoxNew.bottom) 
        && (captureBoxOld.right == captureBoxNew.right) 
        && (captureBoxOld.top == captureBoxNew.top) 
        && (captureBoxOld.left == captureBoxNew.left));
  }
  
  
  
  /** Task run by the capture timer that is responsible for periodically performing a capture and OCR. */
  class UpdateCaptureTask extends TimerTask
  {
    /** Number of milliseconds to wait in between captures. */ 
    final private long CAPTURE_UPDATE_RATE = 20;
    
    final Handler redrawOcrViewHandler = new Handler();
    final Handler updateDicViewHandler = new Handler(); 
    
    
    /** Runnable that will redraw ocrView */
    final Runnable redrawOcrViewRunnable = new Runnable()
    {
      public void run()
      {
        ocrView.invalidate();
      }
    };
    
    
    /** Runnable that will update dicView */
    final Runnable updateDicViewRunnable = new Runnable()
    {
      public void run()
      {
        try
        {
          updateDicViewText();
        }
        catch(Exception e)
        {
          Log.e(LOG_TAG, "Exception in updateDicViewRunnable()! " + e);
        }
      }
    };
    
    
    // Capture and OCR, then send the results to the appropriate views.
    public void run()
    {
      try
      {
        if(captureBox != null)
        {
          // If user is still adjusting capture box
          if(isCaptureBoxBeingAdjusted())
          {
            return;
          }
          
          if((Calendar.getInstance().getTimeInMillis() - lastCaptureTime.getTimeInMillis()) >= CAPTURE_UPDATE_RATE)
          {
            Rect curCaptureBox = new Rect(captureBox);
            
            // Make sure box is big enough to matter
            if((curCaptureBox.width() <= 2) || (curCaptureBox.height() <= 2))
            {
              return;
            }
            
            if(forceUpdate || !isCaptureBoxEqual(lastCaptureBox, curCaptureBox))
            {         
              forceUpdate = false;
              
              // Capture area under the capture box
             
              boolean captureResult = captureScreen(curCaptureBox);
              
              if(!captureResult)
              {
                return;
              }
                          
              // Did the user adjust the capture box while capture took place?
              if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(captureBox, curCaptureBox))
              {
                return;
              }
              
              // OCR the captured area
              boolean ocrResult = ocrCapture(curCaptureBox);
              
              if(!ocrResult)
              {
                return;
              }
              
              // Did the user adjust the capture box while OCR took place?
              if(isCaptureBoxBeingAdjusted() || !isCaptureBoxEqual(captureBox, curCaptureBox))
              {
                return;
              }
              
              // Offset the bounding boxes
              for(Rect bbox : boundingBoxes)
              {
                bbox.offset(clipOffset.x, clipOffset.y);
              }
              
              // Pass capture and OCR info to the OCR view
              ocrView.setBoundingBoxes(boundingBoxes, getIdealPreProcessingScaleFactor());
              ocrView.setLastCapture(lastCapture);
                          
              // Update the dictionary view 
              updateDicViewHandler.post(updateDicViewRunnable);
              
              // Redraw the OCR view 
              redrawOcrViewHandler.post(redrawOcrViewRunnable);
              
              // Save the capture box for comparison later
              lastCaptureBox = new Rect(curCaptureBox);
            }
            
            // Save the time for comparison later
            lastCaptureTime.setTimeInMillis(Calendar.getInstance().getTimeInMillis());
          }
          
        }
      }
      catch(Exception e)
      {
        Log.e(LOG_TAG, "Exception in UpdateCaptureTask.run()! " + e);
      }
    }
  }

}
