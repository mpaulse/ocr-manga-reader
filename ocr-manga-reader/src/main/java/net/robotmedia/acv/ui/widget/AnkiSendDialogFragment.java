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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cb4960.ocrmr.R;

import net.robotmedia.acv.utils.AnkiUtils;

public class AnkiSendDialogFragment extends DialogFragment {

    private ClickListener listener;

    public interface ClickListener {
        public void onAnkiSendDialogOK(long deckId, long modelId, String modelKey, String[] fieldValues);
        public void onAnkiSendDialogCancel();
    }

    public static AnkiSendDialogFragment newInstance(
            long deckId,
            String deckName,
            long modelId,
            String modelName,
            String modelKey,
            String[] fields,
            String[] values) {
        AnkiSendDialogFragment f = new AnkiSendDialogFragment();
        Bundle args = new Bundle();
        args.putLong("deckId", deckId);
        args.putString("deckName", deckName);
        args.putLong("modelId", modelId);
        args.putString("modelName", modelName);
        args.putString("modelKey", modelKey);
        args.putStringArray("fields", fields);
        args.putStringArray("values", values);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof ClickListener) {
            listener = (ClickListener) activity;
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle bundle = getArguments();
        Context context = getActivity();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        TextView deckText = new TextView(context);
        deckText.setText(R.string.ocr_send_anki_deck);
        deckText.setTypeface(Typeface.DEFAULT_BOLD);
        layout.addView(deckText);

        TextView deckNameText = new TextView(context);
        deckNameText.setText(bundle.getString("deckName"));
        layout.addView(deckNameText);

        TextView modelText = new TextView(context);
        modelText.setText(R.string.ocr_send_anki_model);
        modelText.setTypeface(Typeface.DEFAULT_BOLD);
        modelText.setPadding(0, 40, 0, 0);
        layout.addView(modelText);

        TextView modelNameText = new TextView(context);
        modelNameText.setText(bundle.getString("modelName"));
        layout.addView(modelNameText);

        final long modelId = bundle.getLong("modelId");
        final String modelKey = bundle.getString("modelKey");

        String[] fields = bundle.getStringArray("fields");
        String[] values = bundle.getStringArray("values");

        int k = 0;
        for (; k < values.length && !values[k].equals(modelKey); k++);
        final int modelKeyIndex = k;

        final EditText[] valueTexts = new EditText[values.length];
        for (int i = 0; i < fields.length; i++) {
            TextView fieldText = new TextView(context);
            fieldText.setText(fields[i]);
            fieldText.setTypeface(Typeface.DEFAULT_BOLD);
            fieldText.setPadding(0, 40, 0, 0);
            layout.addView(fieldText);
            valueTexts[i] = new EditText(context);
            valueTexts[i].setText(values[i]);
            if (values[i].equals(modelKey) && AnkiUtils.cardExists(modelId, modelKey, context)) {
                valueTexts[i].setBackgroundColor(Color.rgb(255, 136, 136));
                valueTexts[i].setTextColor(Color.BLACK);
            }
            layout.addView(valueTexts[i]);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ocr_send_anki_title);
        builder.setView(layout);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (listener != null) {
                    String[] values = new String[valueTexts.length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = valueTexts[i].getText().toString();
                    }
                    listener.onAnkiSendDialogOK(
                            bundle.getLong("deckId"),
                            modelId,
                            values[modelKeyIndex],
                            values);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (listener != null) {
                    listener.onAnkiSendDialogCancel();
                }
            }
        });
        return builder.create();
    }

}
