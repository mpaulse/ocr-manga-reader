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
package net.robotmedia.acv.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;

import com.ichi2.anki.api.AddContentApi;
import com.ichi2.anki.api.NoteInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AnkiUtils {

    private static boolean initialized = false;
    private static String defaultDeck = null;
    private static String defaultModel = null;
    private static String[] decks = null;
    private static String[] models = null;

    public static void initCachedInfo(Context context) {
        if (!isApiAvailable(context)) {
            return;
        }

        AddContentApi anki = new AddContentApi(context);

        try {
            decks = anki.getDeckList().values().toArray(new String[0]);
            Arrays.sort(decks);
            defaultDeck = anki.getSelectedDeckName();

            models = anki.getModelList().values().toArray(new String[0]);
            Arrays.sort(models);
            defaultModel = anki.getModelName(anki.getCurrentModelId());

            initialized = true;
        } catch (IllegalStateException e){
            // AnkiDroid database inaccessible
        }
    }

    public static String[] getDecks() {
        return decks;
    }

    public static long getDeckID(String name, Context context) {
        if (initialized) {
            AddContentApi anki = new AddContentApi(context);
            Map<Long, String> decks = anki.getDeckList();
            for (Map.Entry<Long, String> deck : decks.entrySet()) {
                if (deck.getValue().equals(name)) {
                    return deck.getKey();
                }
            }
        }
        return -1;
    }

    public static String getDefaultDeck() {
        return defaultDeck;
    }

    public static String[] getModels() {
        return models;
    }

    public static String getDefaultModel() {
        return defaultModel;
    }

    public static long getModelID(String name, Context context) {
        if (initialized) {
            AddContentApi anki = new AddContentApi(context);
            Map<Long, String> models = anki.getModelList(1);
            for (Map.Entry<Long, String> model : models.entrySet()) {
                if (model.getValue().equals(name)) {
                    return model.getKey();
                }
            }
        }
        return -1;
    }

    public static String[] getModelFields(long modelId, Context context) {
        String[] fields = null;
        if (initialized) {
            AddContentApi anki = new AddContentApi(context);
            fields = anki.getFieldList(modelId);
        }
        return fields;
    }

    public static boolean addCard(long deckId, long modelId, String modelKey, String[] fieldValues, Context context) {
        if (initialized) {
            AddContentApi anki = new AddContentApi(context);
            List<NoteInfo> dups = anki.findDuplicateNotes(modelId, modelKey);
            if (dups.isEmpty()) {
                anki.addNote(modelId, deckId, fieldValues, null);
                return true;
            }
        }
        return false;
    }

    public static boolean cardExists(long modelId, String modelKey, Context context) {
        if (initialized) {
            AddContentApi anki = new AddContentApi(context);
            List<NoteInfo> dups = anki.findDuplicateNotes(modelId, modelKey);
            if (!dups.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isApiAvailable(Context context) {
        return AddContentApi.getAnkiDroidPackageName(context) != null;
    }

    public static boolean haveApiPermissions(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(context, AddContentApi.READ_WRITE_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED;
    }

}
