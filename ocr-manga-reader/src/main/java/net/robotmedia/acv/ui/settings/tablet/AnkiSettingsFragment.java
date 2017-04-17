/*******************************************************************************
 * Copyright 2017 Marlon Paulse
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

import android.content.Context;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.logic.PreferencesController;
import net.robotmedia.acv.utils.AnkiUtils;

public class AnkiSettingsFragment extends ExtendedPreferenceFragment  {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity();

        ListPreference deckPref = new ListPreference(context);
        deckPref.setKey(PreferencesController.PREFERENCE_ANKI_DECK);
        deckPref.setTitle(R.string.preference_anki_deck_title);
        deckPref.setDialogTitle(R.string.preference_anki_deck_title);
        deckPref.setSummary(R.string.preference_anki_deck_summary);
        String[] entries = AnkiUtils.getDecks();
        deckPref.setEntries(entries);
        deckPref.setEntryValues(entries);
        deckPref.setDefaultValue(AnkiUtils.getDefaultDeck());

        ListPreference modelPref = new ListPreference(context);
        modelPref.setKey(PreferencesController.PREFERENCE_ANKI_MODEL);
        modelPref.setTitle(R.string.preference_anki_model_title);
        modelPref.setDialogTitle(R.string.preference_anki_model_title);
        modelPref.setSummary(R.string.preference_anki_model_summary);
        entries = AnkiUtils.getModels();
        modelPref.setEntries(entries);
        modelPref.setEntryValues(entries);
        modelPref.setDefaultValue(AnkiUtils.getDefaultModel());

        PreferenceScreen fieldPrefScreen = getPreferenceManager().createPreferenceScreen(context);
        fieldPrefScreen.setTitle(R.string.preference_anki_fields_title);
        fieldPrefScreen.setSummary(R.string.preference_anki_fields_summary);
        fieldPrefScreen.setEnabled(false);
        PreferencesController prefCtrl = new PreferencesController(context);
        String modelName = prefCtrl.getPreferences().getString(PreferencesController.PREFERENCE_ANKI_MODEL, AnkiUtils.getDefaultModel());
        long modelId = AnkiUtils.getModelID(modelName, context);
        if (modelId >= 0) {
            String[] fields = AnkiUtils.getModelFields(modelId, context);
            if (fields != null) {
                String[] fieldTypesDesc = {
                    context.getString(R.string.preference_anki_fields_expression),
                        context.getString(R.string.preference_anki_fields_reading),
                        context.getString(R.string.preference_anki_fields_definition),
                        context.getString(R.string.preference_anki_fields_unused)
                };
                String[] fieldTypesInt = {
                        String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_EXPRESSION_INT),
                        String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_READING_INT),
                        String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_DEFINITION_INT),
                        String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_UNUSED_INT)
                };
                fieldPrefScreen.setEnabled(true);
                for (int i = 0; i < fields.length; i++) {
                    ListPreference listPref = new ListPreference(context);
                    listPref.setKey(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_PREFIX + modelName + "_" + fields[i]);
                    listPref.setTitle(fields[i]);
                    listPref.setDialogTitle(fields[i]);
                    listPref.setEntries(fieldTypesDesc);
                    listPref.setEntryValues(fieldTypesInt);
                    listPref.setDefaultValue((i < fieldTypesInt.length) ? fieldTypesInt[i] : String.valueOf(PreferencesController.PREFERENCE_ANKI_MODEL_FIELD_UNUSED_INT));
                    fieldPrefScreen.addPreference(listPref);
                }
            }
        }

        CheckBoxPreference confirmPref = new CheckBoxPreference(context);
        confirmPref.setKey(PreferencesController.PREFERENCE_ANKI_CONFIRM_SEND);
        confirmPref.setTitle(R.string.preference_anki_confirm_send_title);
        confirmPref.setSummary(R.string.preference_anki_confirm_send_summary);
        confirmPref.setDefaultValue(true);

        PreferenceScreen prefScreen = getPreferenceManager().createPreferenceScreen(context);
        prefScreen.addPreference(deckPref);
        prefScreen.addPreference(modelPref);
        prefScreen.addPreference(fieldPrefScreen);
        prefScreen.addPreference(confirmPref);
        setPreferenceScreen(prefScreen);
    }

}
