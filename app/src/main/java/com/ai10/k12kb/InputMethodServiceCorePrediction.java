package com.ai10.k12kb;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.ai10.k12kb.prediction.SuggestionBar;
import com.ai10.k12kb.prediction.TranslationManager;
import com.ai10.k12kb.prediction.WordDictionary;
import com.ai10.k12kb.prediction.WordPredictor;

import java.util.List;

public abstract class InputMethodServiceCorePrediction extends InputMethodServiceCoreGesture {

    // --- Fields moved from InputMethodServiceCoreCustomizable ---
    protected WordPredictor wordPredictor;
    protected boolean dictLoadingToastShown = false;
    protected KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    // --- Fields moved from K12KbIME ---
    protected SuggestionBar suggestionBar;
    protected TranslationManager translationManager;
    protected int predictionSlotCount = 4;
    protected int translationSlotCount = 4;
    protected boolean predictionBarHiddenByDefault = false;
    protected boolean predictionBarVisibleThisSession = false;
    protected boolean predictionBarOpenedByTranslation = false;

    // --- Method moved from InputMethodServiceCoreCustomizable ---

    protected void ShowDictLoadingToast() {
        if (!wordPredictor.isEngineReady() && !dictLoadingToastShown) {
            dictLoadingToastShown = true;
            Toast.makeText(getApplicationContext(),
                    getString(R.string.prediction_loading_toast),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // --- Methods moved from K12KbIME ---

    protected void updatePredictorWordAtCursor() {
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null || wordPredictor == null) return;
            CharSequence before = ic.getTextBeforeCursor(96, 0);
            if (before == null || before.length() == 0) {
                wordPredictor.setPreviousWord("");
                wordPredictor.setCurrentWord("");
                return;
            }
            // Extract the word at cursor (characters before cursor until non-word char)
            int end = before.length();
            int start = end;
            while (start > 0) {
                char c = before.charAt(start - 1);
                if (WordDictionary.isWordChar(c)) {
                    start--;
                } else {
                    break;
                }
            }
            String currentWord = (start < end) ? before.subSequence(start, end).toString() : "";

            // Extract previous word (word before the current word)
            int prevEnd = start;
            // Skip whitespace/punctuation between words
            while (prevEnd > 0 && !WordDictionary.isWordChar(before.charAt(prevEnd - 1))) {
                prevEnd--;
            }
            int prevStart = prevEnd;
            while (prevStart > 0) {
                char c = before.charAt(prevStart - 1);
                if (WordDictionary.isWordChar(c)) {
                    prevStart--;
                } else {
                    break;
                }
            }
            String previousWord = (prevStart < prevEnd) ? before.subSequence(prevStart, prevEnd).toString() : "";
            wordPredictor.setPreviousWord(previousWord);
            wordPredictor.setCurrentWord(currentWord);
        } catch (Throwable ex) {
            Log.w(TAG2, "updatePredictorWordAtCursor error: " + ex);
        }
    }

    protected void acceptSuggestion(int index) {
        if (wordPredictor == null) return;
        String currentWord = wordPredictor.getCurrentWord();
        String replacement = wordPredictor.acceptSuggestion(index);
        if (replacement == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // Delete the current word being typed (if any) and insert the suggestion + space
        if (currentWord != null && !currentWord.isEmpty()) {
            ic.deleteSurroundingText(currentWord.length(), 0);
        }
        ic.commitText(replacement + " ", 1);
        suggestionBar.clear();
    }

    protected void updateSuggestionBarWithTranslation(List<WordPredictor.Suggestion> suggestions) {
        if(predictionBarHiddenByDefault && !predictionBarVisibleThisSession)
            return;
        if (translationManager != null && translationManager.isEnabled()) {
            String word = wordPredictor.getCurrentWord();
            String prevWord = wordPredictor.getPreviousWord();
            List<String> translations = null;
            if (word != null && !word.isEmpty()) {
                translations = translationManager.translate(word, prevWord);
            }
            if (translations != null && !translations.isEmpty()) {
                boolean isPhraseMatch = translationManager.wasLastPhraseMatch();
                int phraseResultCount = translationManager.getLastPhraseResultCount();
                // Limit to translationSlotCount
                if (translations.size() > translationSlotCount) {
                    translations = translations.subList(0, translationSlotCount);
                }
                suggestionBar.updateTranslation(translations, word, translationSlotCount, isPhraseMatch, phraseResultCount);
                setSuggestionBarShown(true);
                return;
            }
        }
        // Fall back to normal predictions (limited to predictionSlotCount)
        if (suggestions != null && suggestions.size() > predictionSlotCount) {
            suggestions = suggestions.subList(0, predictionSlotCount);
        }
        suggestionBar.update(suggestions, wordPredictor.getCurrentWord());
        if (suggestions != null && !suggestions.isEmpty()) {
            setSuggestionBarShown(true);
        } else {
            //Do not close suggestion bar, either it jumps
            //setSuggestionBarShown(false);
        }
    }

    protected void acceptTranslation(String translatedWord, boolean isPhraseResult) {
        if (translatedWord == null || translatedWord.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // Replace the current word (and previous word if bigram phrase match) with the translation
        if (wordPredictor != null) {
            String currentWord = wordPredictor.getCurrentWord();
            String previousWord = wordPredictor.getPreviousWord();
            if (isPhraseResult && previousWord != null && !previousWord.isEmpty()) {
                // Bigram match: delete current word + space + previous word
                int deleteLen = (currentWord != null ? currentWord.length() : 0)
                        + 1 // space between words
                        + previousWord.length();
                ic.deleteSurroundingText(deleteLen, 0);
            } else if (currentWord != null && !currentWord.isEmpty()) {
                // Single word: delete only current word
                ic.deleteSurroundingText(currentWord.length(), 0);
            }
            wordPredictor.reset();
        }
        ic.commitText(translatedWord + " ", 1);
        suggestionBar.clear();
    }

    public boolean ActionToggleTranslationMode(KeyPressData keyPressData) {
        if (translationManager == null || suggestionBar == null) return false;
        // Update languages BEFORE toggle so dictionary loads correct direction
        updateTranslationLanguages();
        boolean enabled = translationManager.toggle();
        String msg = enabled ?
                "\uD83C\uDF10 Translation " + translationManager.getSourceLang().toUpperCase() + " \u2192 " + translationManager.getTargetLang().toUpperCase() :
                "\uD83C\uDF10 Translation OFF";
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        if (enabled) {
            // Show prediction bar if hidden (translation needs visible bar)
            if (predictionBarHiddenByDefault && !predictionBarVisibleThisSession) {
                predictionBarVisibleThisSession = true;
                predictionBarOpenedByTranslation = true;
                setSuggestionBarShown(true);
            }
            // Translate current word immediately
            if (wordPredictor != null) {
                updatePredictorWordAtCursor();
                String word = wordPredictor.getCurrentWord();
                String prevWord = wordPredictor.getPreviousWord();
                if (word != null && !word.isEmpty()) {
                    List<String> translations = translationManager.translate(word, prevWord);
                    if (!translations.isEmpty()) {
                        boolean isPhraseMatch = translationManager.wasLastPhraseMatch();
                        int phraseCount = translationManager.getLastPhraseResultCount();
                        if (translations.size() > translationSlotCount) {
                            translations = translations.subList(0, translationSlotCount);
                        }
                        suggestionBar.updateTranslation(translations, word, translationSlotCount, isPhraseMatch, phraseCount);
                        setSuggestionBarShown(true);
                    }
                }
            }
        } else {
            // Translation disabled — restore predictions
            if (predictionBarOpenedByTranslation) {
                predictionBarOpenedByTranslation = false;
                predictionBarVisibleThisSession = false;
                setSuggestionBarShown(false);
                suggestionBar.clear();
            } else if (wordPredictor != null) {
                // Force engine to recompute suggestions for current word
                // (don't use updatePredictorWordAtCursor — IC can return null)
                wordPredictor.setCurrentWord(wordPredictor.getCurrentWord());
                List<WordPredictor.Suggestion> latest = wordPredictor.getLatestSuggestions();
                if (latest != null && latest.size() > predictionSlotCount) {
                    latest = latest.subList(0, predictionSlotCount);
                }
                suggestionBar.update(latest, wordPredictor.getCurrentWord());
            } else {
                suggestionBar.clear();
            }
        }
        return true;
    }

    public boolean ActionTogglePredictionBar(KeyPressData keyPressData) {
        if (suggestionBar == null || wordPredictor == null) return false;

        if (!predictionBarHiddenByDefault) return false;

        if(!predictionBarVisibleThisSession) {
            predictionBarVisibleThisSession = true;
            setSuggestionBarShown(true);
            Toast.makeText(getApplicationContext(), "\uD83D\uDD2E Predictions ON", Toast.LENGTH_SHORT).show();
            // Read word at cursor and force prediction update
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence before = ic.getTextBeforeCursor(96, 0);
                if (before != null && before.length() > 0) {
                    int end = before.length();
                    int start = end;
                    while (start > 0 && WordDictionary.isWordChar(before.charAt(start - 1))) start--;
                    String word = (start < end) ? before.subSequence(start, end).toString() : "";
                    wordPredictor.setCurrentWord(word);
                }
            }
            suggestionBar.update(wordPredictor.getLatestSuggestions(), wordPredictor.getCurrentWord());
        } else {
            predictionBarVisibleThisSession = false;
            setSuggestionBarShown(false);
            Toast.makeText(getApplicationContext(), "\uD83D\uDD2E Predictions OFF", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    protected void reloadDictionaryForCurrentLanguage() {
        try {
            if (wordPredictor == null) return;
            KeyboardLayout kl = keyboardLayoutManager.GetCurrentKeyboardLayout();
            if (kl == null) return;
            String kbName = kl.KeyboardName;
            String locale = "en"; // default
            if (kbName != null) {
                String lower = kbName.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("русск") || lower.contains("russian")) {
                    locale = "ru";
                } else if (lower.contains("украин") || lower.contains("ukrain")) {
                    locale = "ru"; // use Russian dictionary as fallback for Ukrainian
                }
            }
            wordPredictor.loadDictionary(getApplicationContext(), locale);
        } catch (Throwable ex) {
            Log.e(TAG2, "reloadDictionaryForCurrentLanguage error: " + ex);
        }
    }

    protected void updateTranslationLanguages() {
        if (translationManager == null || keyboardLayoutManager == null) return;
        try {
            String currentLang = layoutToLangCode(keyboardLayoutManager.GetCurrentKeyboardLayout());
            String nextLang = layoutToLangCode(keyboardLayoutManager.GetNextKeyboardLayout());
            translationManager.updateLanguages(currentLang, nextLang);
        } catch (Throwable ex) {
            Log.w(TAG2, "updateTranslationLanguages error: " + ex);
        }
    }

    protected String layoutToLangCode(KeyboardLayout kl) {
        if (kl == null) return "en";
        String name = kl.KeyboardName;
        if (name == null) return "en";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("русск") || lower.contains("russian")) return "ru";
        if (lower.contains("deutsch") || lower.contains("german")) return "de";
        if (lower.contains("français") || lower.contains("french")) return "fr";
        if (lower.contains("español") || lower.contains("spanish")) return "es";
        return "en";
    }

    // --- Suggestion bar visibility (replaces framework setCandidatesViewShown) ---

    protected void setSuggestionBarShown(boolean shown) {
        if (suggestionBar != null) {
            suggestionBar.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Wraps keyboardView and suggestionBar into a single input view container.
     * Call this from onCreateInputView() instead of returning keyboardView alone.
     */
    protected View createInputViewWithSuggestions(View keyboardView) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (suggestionBar != null) {
            if (suggestionBar.getParent() != null) {
                ((ViewGroup) suggestionBar.getParent()).removeView(suggestionBar);
            }
            container.addView(suggestionBar);
        }

        if (keyboardView != null) {
            if (keyboardView.getParent() != null) {
                ((ViewGroup) keyboardView.getParent()).removeView(keyboardView);
            }
            container.addView(keyboardView);
        }

        return container;
    }

    // --- Lifecycle overrides ---

    @Override
    public View onCreateCandidatesView() {
        // Suggestion bar is embedded in the input view — no framework candidates needed
        return null;
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        outInsets.contentTopInsets = outInsets.visibleTopInsets;
    }

    @Override
    public void onDestroy() {
        if (wordPredictor != null) {
            wordPredictor.shutdown();
            wordPredictor = null;
        }
        super.onDestroy();
    }

    // --- Lifecycle helpers called from K12KbIME ---

    protected void initPrediction() {
        boolean predictionEnabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_17_PREDICTION_ENABLED);
        if (predictionEnabled) {
            // Fresh predictor — but engine+dictionaries are static inside WordPredictor
            // so if they were loaded before, they're reused instantly (no new threads)
            wordPredictor = new WordPredictor();
            int engineMode = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE);
            int dictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
            wordPredictor.setDictSize(dictSize);
            wordPredictor.setNextWordEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION));
            wordPredictor.setKeyboardAwareEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE));
            wordPredictor.setEngineMode(engineMode);
            wordPredictor.loadDictionary(getApplicationContext(), "en", new Runnable() {
                public void run() {
                    wordPredictor.preloadDictionary(getApplicationContext(), "ru");
                }
            });
            Log.i(TAG2, "onCreate: WordPredictor initialized (engine cached: " + wordPredictor.isEngineReady() + ")");
            int predictionHeight = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_15_PREDICTION_HEIGHT);
            if (predictionHeight < 10) predictionHeight = 36;
            predictionSlotCount = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_16_PREDICTION_COUNT);
            if (predictionSlotCount < 1) predictionSlotCount = 4;
            translationSlotCount = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_22_TRANSLATION_COUNT);
            if (translationSlotCount < 1) translationSlotCount = 4;
            int barSlots = Math.max(predictionSlotCount, translationSlotCount);
            suggestionBar = new SuggestionBar(this, predictionHeight, barSlots);
            wordPredictor.setSuggestLimit(predictionSlotCount);
            wordPredictor.setListener(new WordPredictor.SuggestionListener() {
                public void onSuggestionsUpdated(final List<WordPredictor.Suggestion> suggestions) {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        updateSuggestionBarWithTranslation(suggestions);
                    } else {
                        final String pfx = wordPredictor.getCurrentWord();
                        suggestionBar.post(new Runnable() {
                            public void run() {
                                updateSuggestionBarWithTranslation(suggestions);
                            }
                        });
                    }
                }
            });
            suggestionBar.setOnSuggestionClickListener(new SuggestionBar.OnSuggestionClickListener() {
                public void onSuggestionClicked(int index, String word) {
                    if (suggestionBar.isShowingTranslations()) {
                        acceptTranslation(word, suggestionBar.isPhraseResult(index));
                    } else {
                        acceptSuggestion(index);
                    }
                }
            });
            // Initialize translation manager
            translationManager = new TranslationManager(getApplicationContext());
            int transDictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE);
            translationManager.setMaxEntries(transDictSize);
            translationManager.setOnDictionaryLoadedListener(() -> {
                if (suggestionBar == null || wordPredictor == null || translationManager == null) return;
                if (!translationManager.isEnabled()) return;
                suggestionBar.post(() -> {
                    if (wordPredictor == null || translationManager == null) return;
                    if (!translationManager.isEnabled()) return;
                    String word = wordPredictor.getCurrentWord();
                    String prevWord = wordPredictor.getPreviousWord();
                    if (word != null && !word.isEmpty()) {
                        List<String> translations = translationManager.translate(word, prevWord);
                        if (!translations.isEmpty()) {
                            boolean isPhraseMatch = translationManager.wasLastPhraseMatch();
                            int phraseCount = translationManager.getLastPhraseResultCount();
                            if (translations.size() > translationSlotCount)
                                translations = translations.subList(0, translationSlotCount);
                            suggestionBar.updateTranslation(translations, word, translationSlotCount, isPhraseMatch, phraseCount);
                            setSuggestionBarShown(true);
                        }
                    }
                });
            });
            predictionBarHiddenByDefault = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN);
        }
    }

    protected void onStartInputPrediction() {
        predictionBarVisibleThisSession = false;
        if (wordPredictor != null && !predictionBarHiddenByDefault) {
            setSuggestionBarShown(true);
            updatePredictorWordAtCursor();
        } else {
            setSuggestionBarShown(false);
        }
    }

    protected void onFinishInputPredictionSettingsReload() {
        if (wordPredictor != null) {
            int newDictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
            wordPredictor.setDictSize(newDictSize);
            wordPredictor.setNextWordEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION));
            wordPredictor.setKeyboardAwareEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE));
            reloadDictionaryForCurrentLanguage();
        }
        if (translationManager != null) {
            int newTransDictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE);
            translationManager.setMaxEntries(newTransDictSize);
        }
    }

    protected void onFinishInputPredictionCleanup() {
        if (translationManager != null && translationManager.isEnabled()) {
            translationManager.setEnabled(false);
        }
        predictionBarOpenedByTranslation = false;
    }

    protected void onUpdateSelectionPrediction(int newSelStart, int newSelEnd) {
        if (wordPredictor != null && wordPredictor.isEnabled() && newSelStart == newSelEnd) {
            updatePredictorWordAtCursor();
        }
    }
}
