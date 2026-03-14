package com.ai10.k12kb.prediction;

import android.content.Context;
import android.database.Cursor;
import android.provider.UserDictionary;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge to Android's system-wide UserDictionary content provider.
 * Reads words added by the user via Android Settings > Language > Personal Dictionary.
 * Only accessible from IME / SpellChecker services (API 23+).
 */
public class UserDictionaryBridge {

    private static final String TAG = "K12Kb-UserDict";
    private static final int DEFAULT_USER_FREQUENCY = 220; // high priority in 0-255 scale

    public static class UserWord {
        public final String word;
        public final int frequency;
        public final String shortcut;

        public UserWord(String word, int frequency, String shortcut) {
            this.word = word;
            this.frequency = frequency;
            this.shortcut = shortcut;
        }
    }

    /**
     * Read all words from the system UserDictionary.
     * Returns empty list if provider is unavailable or access denied.
     */
    public static List<UserWord> readAll(Context context) {
        List<UserWord> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            Log.w(TAG, "Querying UserDictionary...");
            cursor = context.getContentResolver().query(
                    UserDictionary.Words.CONTENT_URI,
                    new String[]{
                            UserDictionary.Words.WORD,
                            UserDictionary.Words.FREQUENCY,
                            UserDictionary.Words.SHORTCUT
                    },
                    null, null, null);
            if (cursor == null) {
                Log.w(TAG, "UserDictionary cursor is null (provider unavailable)");
                return result;
            }
            int colWord = cursor.getColumnIndex(UserDictionary.Words.WORD);
            int colFreq = cursor.getColumnIndex(UserDictionary.Words.FREQUENCY);
            int colShortcut = cursor.getColumnIndex(UserDictionary.Words.SHORTCUT);
            Log.w(TAG, "UserDictionary cursor columns: word=" + colWord + " freq=" + colFreq + " shortcut=" + colShortcut + " count=" + cursor.getCount());
            while (cursor.moveToNext()) {
                String word = cursor.getString(colWord);
                if (word == null || word.isEmpty()) continue;
                int freq = (colFreq >= 0 && !cursor.isNull(colFreq))
                        ? cursor.getInt(colFreq) : DEFAULT_USER_FREQUENCY;
                // Clamp to 0-255 range used by our dictionary
                if (freq > 255) freq = 255;
                if (freq < 1) freq = 1;
                String shortcut = (colShortcut >= 0 && !cursor.isNull(colShortcut))
                        ? cursor.getString(colShortcut) : null;
                result.add(new UserWord(word, freq, shortcut));
            }
            Log.w(TAG, "Read " + result.size() + " words from UserDictionary");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException reading UserDictionary: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading UserDictionary", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }
}
