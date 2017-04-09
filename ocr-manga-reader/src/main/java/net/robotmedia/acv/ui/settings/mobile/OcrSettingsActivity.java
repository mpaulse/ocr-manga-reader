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

package net.robotmedia.acv.ui.settings.mobile;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.logic.PreferencesController;

public class OcrSettingsActivity extends ExtendedPreferenceActivity
{
  @Override
  protected int getPreferencesResource()
  {
    return R.xml.ocr_settings;
  }
  
  
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_1);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_2);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_3);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_DIC_4);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_MAX_SUB_DEFS);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_MAX_EXAMPLES);
    this.showValueOnSummary(PreferencesController.PREFERENCE_EPWING_MAX_LINES);
  }
  
  
  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
  {
    super.onSharedPreferenceChanged(sharedPreferences, key);
    final PreferencesController preferences = new PreferencesController(this);
    preferences.setMaxImageResolution();
  }
}
