/*******************************************************************************
 * Copyright 2013-2014 Christopher Brochtrup
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

package net.robotmedia.acv.ui.settings.tablet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.Constants;
import net.robotmedia.acv.logic.PreferencesController;
import net.robotmedia.acv.ui.SDEpwingBrowerActivity;
import net.robotmedia.acv.ui.SDTextBrowserActivity;

import java.util.Locale;

public class OcrSettingsFragment extends ExtendedPreferenceFragment
{
  int dicToSet = 1;


  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.ocr_settings);

    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_1);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_2);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_3);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_4);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_MAX_EXAMPLES);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_MAX_LINES);
    this.showValueOnSummary(PreferencesController.PREFERENCE_MISC_WORD_LIST_SAVE_FILE_PATH);
    this.showValueOnSummary(PreferencesController.PREFERENCE_MISC_WORD_LIST_SAVE_FILE_FORMAT);

    Preference epwingDic1 = findPreference(Constants.OCR_SETTING_EPWING_1_KEY);
    epwingDic1.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingDic2 = findPreference(Constants.OCR_SETTING_EPWING_2_KEY);
    epwingDic2.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingDic3 = findPreference(Constants.OCR_SETTING_EPWING_3_KEY);
    epwingDic3.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingDic4 = findPreference(Constants.OCR_SETTING_EPWING_4_KEY);
    epwingDic4.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    
    Preference epwingResetDic1 = findPreference(Constants.OCR_SETTING_EPWING_RESET_1_KEY);
    epwingResetDic1.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingResetDic2 = findPreference(Constants.OCR_SETTING_EPWING_RESET_2_KEY);
    epwingResetDic2.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingResetDic3 = findPreference(Constants.OCR_SETTING_EPWING_RESET_3_KEY);
    epwingResetDic3.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    Preference epwingResetDic4 = findPreference(Constants.OCR_SETTING_EPWING_RESET_4_KEY);
    epwingResetDic4.setOnPreferenceClickListener(new SelectEpwingFileHandler());
    
    Preference wordListSaveFilePath = findPreference(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_PATH_KEY);
    wordListSaveFilePath.setOnPreferenceClickListener(new SelectWordListSaveFileHandler());
    Preference wordListSaveFileReset = findPreference(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_RESET_KEY);
    wordListSaveFileReset.setOnPreferenceClickListener(new SelectWordListSaveFileHandler());
  }


  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
  {
    super.onSharedPreferenceChanged(sharedPreferences, key);
    final PreferencesController preferences = new PreferencesController(this.getActivity());
    preferences.setMaxImageResolution();
  }
  
  
  public class SelectWordListSaveFileHandler implements OnPreferenceClickListener
  {
    @Override
    public boolean onPreferenceClick(Preference preference)
    {
      String key = preference.getKey();
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

      if (key.equals(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_PATH_KEY))
      {
        Intent myIntent = new Intent(getActivity(), SDTextBrowserActivity.class);
        String comicsPath = preferences.getString(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_PATH_KEY, Environment
            .getExternalStorageDirectory().getAbsolutePath());
        myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
        startActivityForResult(myIntent, Constants.SD_BROWSER_TEXT_CODE);
      }
      else if (key.equals(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_RESET_KEY))
      {
        preferences.edit().putString(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_PATH_KEY, "").commit();
      }
      else
      {
        return false;
      }

      return true;
    }
  }
  

  public class SelectEpwingFileHandler implements OnPreferenceClickListener
  {
    @Override
    public boolean onPreferenceClick(Preference preference)
    {
      String key = preference.getKey();
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

      if (key.equals(Constants.OCR_SETTING_EPWING_1_KEY))
      {
        Intent myIntent = new Intent(getActivity(), SDEpwingBrowerActivity.class);
        String comicsPath = preferences.getString(Constants.OCR_SETTING_EPWING_1_KEY, Environment
            .getExternalStorageDirectory().getAbsolutePath());
        myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
        startActivityForResult(myIntent, Constants.SD_BROWSER_CODE);
        dicToSet = 1;
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_2_KEY))
      {
        Intent myIntent = new Intent(getActivity(), SDEpwingBrowerActivity.class);
        String comicsPath = preferences.getString(Constants.OCR_SETTING_EPWING_2_KEY, Environment
            .getExternalStorageDirectory().getAbsolutePath());
        myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
        startActivityForResult(myIntent, Constants.SD_BROWSER_CODE);
        dicToSet = 2;
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_3_KEY))
      {
        Intent myIntent = new Intent(getActivity(), SDEpwingBrowerActivity.class);
        String comicsPath = preferences.getString(Constants.OCR_SETTING_EPWING_3_KEY, Environment
            .getExternalStorageDirectory().getAbsolutePath());
        myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
        startActivityForResult(myIntent, Constants.SD_BROWSER_CODE);
        dicToSet = 3;
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_4_KEY))
      {
        Intent myIntent = new Intent(getActivity(), SDEpwingBrowerActivity.class);
        String comicsPath = preferences.getString(Constants.OCR_SETTING_EPWING_4_KEY, Environment
            .getExternalStorageDirectory().getAbsolutePath());
        myIntent.putExtra(Constants.COMICS_PATH_KEY, comicsPath);
        startActivityForResult(myIntent, Constants.SD_BROWSER_CODE);
        dicToSet = 4;
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_RESET_1_KEY))
      {
        preferences.edit().putString(Constants.OCR_SETTING_EPWING_1_KEY, "").commit();
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_RESET_2_KEY))
      {
        preferences.edit().putString(Constants.OCR_SETTING_EPWING_2_KEY, "").commit();
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_RESET_3_KEY))
      {
        preferences.edit().putString(Constants.OCR_SETTING_EPWING_3_KEY, "").commit();
      }
      else if (key.equals(Constants.OCR_SETTING_EPWING_RESET_4_KEY))
      {
        preferences.edit().putString(Constants.OCR_SETTING_EPWING_4_KEY, "").commit();
      }
      else
      {
        return false;
      }

      return true;
    }
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);

    if ((requestCode == Constants.SD_BROWSER_CODE) && (resultCode == Activity.RESULT_OK))
    {
      String absolutePath = data.getStringExtra(Constants.COMIC_PATH_KEY);

      if (absolutePath.endsWith("CATALOGS") || absolutePath.endsWith("CATALOG"))
      {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (dicToSet == 1)
        {
          preferences.edit().putString(Constants.OCR_SETTING_EPWING_1_KEY, absolutePath).commit();
        }
        else if (dicToSet == 2)
        {
          preferences.edit().putString(Constants.OCR_SETTING_EPWING_2_KEY, absolutePath).commit();
        }
        else if (dicToSet == 3)
        {
          preferences.edit().putString(Constants.OCR_SETTING_EPWING_3_KEY, absolutePath).commit();
        }
        else if (dicToSet == 4)
        {
          preferences.edit().putString(Constants.OCR_SETTING_EPWING_4_KEY, absolutePath).commit();
        }
      }
    }
    else if ((requestCode == Constants.SD_BROWSER_TEXT_CODE) && (resultCode == Activity.RESULT_OK))
    {
      String absolutePath = data.getStringExtra(Constants.COMIC_PATH_KEY);

      if (absolutePath.toLowerCase(Locale.US).endsWith(".txt")
          || absolutePath.toLowerCase(Locale.US).endsWith(".tsv"))
      {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.edit().putString(Constants.OCR_SETTING_MISC_WORD_LIST_SAVE_FILE_PATH_KEY, absolutePath).commit();
      }
    }
  }
}
