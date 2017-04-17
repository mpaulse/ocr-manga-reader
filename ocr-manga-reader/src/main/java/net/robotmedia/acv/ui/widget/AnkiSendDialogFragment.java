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
package net.robotmedia.acv.ui.widget;

import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AnkiSendDialogLayout extends LinearLayout {

    public AnkiSendDialogLayout(Context context, String deckName, String modelName, String[] fields, String[] values) {
        super(context);
        init(context, deckName, modelName, fields, values);
    }

    private void init(Context context, String deckName, String modelName, String[] fields, String[] values) {
        setOrientation(VERTICAL);

        TextView deckText = new TextView(context);
        deckText.setText("Deck:");
        addView(deckText);

        TextView deckNameText = new TextView(context);
        deckNameText.setText(deckName);
        addView(deckNameText);

        TextView modelText = new TextView(context);
        modelText.setText("Model:");
        addView(modelText);

        TextView modelNameText = new TextView(context);
        modelNameText.setText(modelName);
        addView(modelNameText);

        for (int i = 0; i < fields.length; i++) {
            TextView fieldText = new TextView(context);
            fieldText.setText(fields[i]);
            addView(fieldText);
            EditText valueText = new EditText(context);
            valueText.setText(values[i]);
            addView(valueText);
        }
    }

}
