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
package net.robotmedia.acv.ui.settings;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.ichi2.anki.api.AddContentApi;

import java.util.Arrays;

public class AnkiModelListPreference extends ListPreference {

    public AnkiModelListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        AddContentApi anki = new AddContentApi(context);
        String[] models = anki.getModelList().values().toArray(new String[0]);
        Arrays.sort(models);
        setEntries(models);
        setEntryValues(models);
        setDefaultValue(anki.getModelName(anki.getCurrentModelId()));
    }

}
