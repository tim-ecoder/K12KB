package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native SymSpell prediction engine.
 *
 * Delegates fuzzy matching AND prefix lookups to the C library
 * (libnativesymspell.so). All word indexes live in native memory —
 * no Java-side HashMaps needed. On cache hit, loading is just mmap + ready.
 */
public class NativeSymSpellEngine implements PredictionEngine {

    private static final String TAG = "NativeSymSpellEngine";
    private static final int DEFAULT_MAX_WORDS = 35000;
    private static final String CACHE_DIR = "native_dict_cache";
    private int maxWords = DEFAULT_MAX_WORDS;

    private volatile NativeSymSpell nativeSymSpell;
    private final Object loadLock = new Object();
    private volatile boolean ready = false;
    private volatile String loadedLocale = "";
    private String keyboardLayout = "qwerty";
    private boolean nextWordEnabled = true;
    private boolean keyboardAwareEnabled = true;

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        NativeSymSpell ss = nativeSymSpell; // local snapshot for thread safety
        if (!ready || ss == null) return Collections.emptyList();

        String normalizedPrev = (previousWord != null && !previousWord.isEmpty())
                ? WordDictionary.normalize(previousWord) : null;

        // Next-word prediction: empty input with a previous word
        if (input == null || input.isEmpty()) {
            if (!nextWordEnabled) return Collections.emptyList();
            if (normalizedPrev == null || normalizedPrev.isEmpty()) return Collections.emptyList();
            return bigramNextWord(ss, normalizedPrev, limit);
        }

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        // 1. Prefix completions (from native prefix lookup)
        NativeSymSpell.SuggestItem[] completions = ss.prefixLookup(normalized, 100);
        for (NativeSymSpell.SuggestItem item : completions) {
            String word = item.original;
            String normEntry = WordDictionary.normalize(word);
            if (!normEntry.startsWith(normalized)) continue;
            if (word.length() <= input.length()) continue;
            if (word.equalsIgnoreCase(input)) continue;

            int effFreq = WordDictionary.effectiveFrequency(item.frequency);
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double score = computeScore(normalized, normEntry, word, item.frequency, 0, true, input.length());
            // Bigram boost for completions
            if (nextWordEnabled && normalizedPrev != null) {
                int bgFreq = ss.getBigramFrequency(normalizedPrev, normEntry);
                if (bgFreq > 0) {
                    score += bgFreq / 100.0;
                }
            }
            String key = word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new WordPredictor.Suggestion(word, 0, score), limit);
            }
        }

        // 2. Native SymSpell fuzzy matches (skip for single char, skip entirely if disabled)
        if (keyboardAwareEnabled && normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            NativeSymSpell.SuggestItem[] nativeResults =
                    ss.lookupWeighted(normalized, symLimit, keyboardLayout);

            for (NativeSymSpell.SuggestItem item : nativeResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                String word = item.original;
                if (word.equals(input)) continue;
                double score = computeScore(normalized, item.term, word, item.frequency,
                        item.distance, false, input.length());
                // Bonus for low weighted distance (adjacent key typos)
                if (item.weightedDistance >= 0 && item.weightedDistance < item.distance) {
                    score += (item.distance - item.weightedDistance) * 0.5;
                }
                // Bigram boost for fuzzy matches
                if (nextWordEnabled && normalizedPrev != null) {
                    int bgFreq = ss.getBigramFrequency(normalizedPrev, item.term);
                    if (bgFreq > 0) {
                        score += bgFreq / 100.0;
                    }
                }
                String key = word.toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    insertSorted(top, new WordPredictor.Suggestion(word, item.distance, score), limit);
                }
            }
        }

        return top;
    }

    private List<WordPredictor.Suggestion> bigramNextWord(NativeSymSpell ss, String normalizedPrev, int limit) {
        NativeSymSpell.BigramItem[] items = ss.bigramLookup(normalizedPrev, limit);
        if (items.length == 0) return Collections.emptyList();

        List<WordPredictor.Suggestion> results = new ArrayList<>();
        for (NativeSymSpell.BigramItem item : items) {
            double score = item.frequency / 50.0;
            results.add(new WordPredictor.Suggestion(item.word, 0, score));
        }
        return results;
    }

    public void setMaxWords(int max) {
        if (max > 0) this.maxWords = max;
    }

    public void setKeyboardLayout(String layout) {
        if (layout != null && !layout.isEmpty()) {
            this.keyboardLayout = layout;
        }
    }

    public void setNextWordEnabled(boolean enabled) {
        this.nextWordEnabled = enabled;
    }

    public void setKeyboardAwareEnabled(boolean enabled) {
        this.keyboardAwareEnabled = enabled;
    }

    @Override
    public void loadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            if (ready && locale.equals(loadedLocale)) {
                return; // already loaded
            }
            boolean fromCache = load(context, locale);
            if (nativeSymSpell != null && nativeSymSpell.isValid()) {
                // Always load user words (even after cache load — user may have added new words)
                int before = nativeSymSpell.size();
                loadUserWords(context);
                if (nativeSymSpell.size() > before) {
                    nativeSymSpell.buildIndex();
                    Log.w(TAG, "Added " + (nativeSymSpell.size() - before) + " new user words, rebuilt index");
                    saveNativeCache(context, locale);
                } else if (!fromCache) {
                    saveNativeCache(context, locale);
                }
            }
        }
    }

    @Override
    public void preloadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            if (locale.equals(loadedLocale)) return;

            // Only ensure cache file exists — don't replace the active dictionary
            String cachePath = new File(context.getFilesDir(), CACHE_DIR + "/" + locale + ".ssnd").getAbsolutePath();
            if (new File(cachePath).exists()) {
                Log.d(TAG, "Cache exists for " + locale + ", skip preload");
                return;
            }

            // No cache — build from text to create it, then restore current dictionary
            NativeSymSpell prevSS = nativeSymSpell;
            String prevLocale = loadedLocale;
            boolean prevReady = ready;

            boolean fromCache = load(context, locale);
            if (!fromCache) {
                saveNativeCache(context, locale);
            }

            NativeSymSpell preloaded = nativeSymSpell;
            nativeSymSpell = prevSS;
            loadedLocale = prevLocale;
            ready = prevReady;

            if (preloaded != null && preloaded != prevSS) {
                preloaded.destroy();
            }
            Log.d(TAG, "Preloaded cache for " + locale + ", active dict remains " + loadedLocale);
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLoadedLocale() {
        return loadedLocale;
    }

    /**
     * Load dictionary for locale. Returns true if loaded from cache, false if built from text.
     */
    private boolean load(Context context, String locale) {
        long startTime = System.currentTimeMillis();

        // Try native binary cache first (mmap — very fast, no text re-parse needed)
        // Don't set ready=false — keep old dict functional during atomic swap
        String cachePath = new File(context.getFilesDir(), CACHE_DIR + "/" + locale + ".ssnd").getAbsolutePath();
        NativeSymSpell cached = NativeSymSpell.loadFromCache(cachePath);
        if (cached != null && cached.size() > 0) {
            NativeSymSpell old = nativeSymSpell;
            nativeSymSpell = cached;
            loadedLocale = locale;
            if (old != null && old != cached) {
                old.destroy();
            }
            ready = true;

            // v2→v3 upgrade: if cache has 0 bigrams, load from JSON and re-save
            if (nativeSymSpell.bigramCount() == 0) {
                loadBigrams(context, locale);
                if (nativeSymSpell.bigramCount() > 0) {
                    nativeSymSpell.buildBigramIndex();
                    saveNativeCache(context, locale);
                    Log.w(TAG, "Upgraded cache to v3 with " + nativeSymSpell.bigramCount() + " bigrams");
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-cache", nativeSymSpell.size(), elapsed);
            Log.w(TAG, "Loaded " + locale + " from native cache: " + nativeSymSpell.size()
                    + " words, " + nativeSymSpell.bigramCount() + " bigrams in " + elapsed + "ms");
            return true;
        }

        // Cache miss — full rebuild from text needed, predictions unavailable during build
        ready = false;
        WordDictionary.recordLoadingStart(locale);

        // v1 cache exists but couldn't be loaded (version mismatch) — delete it
        File cacheFile = new File(cachePath);
        if (cacheFile.exists()) {
            cacheFile.delete();
            Log.w(TAG, "Deleted stale v1 cache: " + cachePath);
        }

        // Build from text dictionary
        if (!NativeSymSpell.isAvailable()) {
            Log.w(TAG, "Native library not available, cannot load");
            return false;
        }

        NativeSymSpell old = nativeSymSpell;
        nativeSymSpell = new NativeSymSpell(2, 7);
        if (old != null) {
            old.destroy();
        }
        if (!nativeSymSpell.isValid()) {
            Log.e(TAG, "Failed to create native SymSpell instance");
            return false;
        }

        int wordCount = loadFromAssets(context, locale);
        if (wordCount <= 0) {
            Log.e(TAG, "No words loaded for locale " + locale);
            return false;
        }

        long buildStart = System.currentTimeMillis();
        nativeSymSpell.buildIndex();
        nativeSymSpell.buildBigramIndex();
        long buildElapsed = System.currentTimeMillis() - buildStart;
        Log.w(TAG, "Native buildIndex for " + locale + ": " + buildElapsed + "ms"
                + " (" + nativeSymSpell.bigramCount() + " bigrams)");

        ready = true;
        loadedLocale = locale;
        long elapsed = System.currentTimeMillis() - startTime;
        WordDictionary.recordLoadStats(locale, "native-assets", wordCount, elapsed);
        Log.w(TAG, "Loaded " + locale + " from assets (native): " + wordCount
                + " words in " + elapsed + "ms");

        // Cache is saved by caller (loadDictionary) after adding user/learned words
        return false;
    }

    private void loadBigrams(Context context, String locale) {
        String bigramFile = "dictionaries/" + locale + "_bigrams.json";
        try {
            InputStream is = context.getAssets().open(bigramFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            int count = 0;
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String word1 = keys.next();
                JSONArray pairs = root.getJSONArray(word1);
                for (int i = 0; i < pairs.length(); i++) {
                    JSONArray pair = pairs.getJSONArray(i);
                    String word2 = pair.getString(0);
                    int freq = pair.getInt(1);
                    String normalized2 = WordDictionary.normalize(word2);
                    nativeSymSpell.addBigram(word1, normalized2, word2, freq);
                    count++;
                }
            }
            Log.d(TAG, "Loaded " + count + " bigrams for " + locale);
        } catch (java.io.FileNotFoundException e) {
            Log.d(TAG, "No bigram file for " + locale);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load bigrams for " + locale + ": " + e);
        }
    }

    private int loadFromAssets(Context context, String locale) {
        String txtFilename = "dictionaries/" + locale + "_base.txt";
        String jsonFilename = "dictionaries/" + locale + "_base.json";
        try {
            InputStream is;
            boolean useTxt;
            try {
                is = context.getAssets().open(txtFilename);
                useTxt = true;
            } catch (java.io.FileNotFoundException e) {
                is = context.getAssets().open(jsonFilename);
                useTxt = false;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 16384);

            // Collect all word-frequency pairs
            ArrayList<String[]> allEntries = new ArrayList<>();
            if (useTxt) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) break;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    allEntries.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
                }
            } else {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
                org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    allEntries.add(new String[]{obj.getString("w"), String.valueOf(obj.getInt("f"))});
                }
            }
            reader.close();

            // Sort by frequency descending
            Collections.sort(allEntries, new Comparator<String[]>() {
                public int compare(String[] a, String[] b) {
                    try { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); }
                    catch (NumberFormatException e) { return 0; }
                }
            });
            if (maxWords > 0 && allEntries.size() > maxWords) {
                allEntries = new ArrayList<>(allEntries.subList(0, maxWords));
            }

            // Add to native SymSpell with both normalized and original forms
            for (String[] entry : allEntries) {
                if (Thread.currentThread().isInterrupted()) break;
                int freq;
                try { freq = Integer.parseInt(entry[1]); }
                catch (NumberFormatException e) { continue; }
                String word = entry[0];
                String normalized = WordDictionary.normalize(word);

                nativeSymSpell.addWord(normalized, word, freq);
            }

            // Load bigrams
            loadBigrams(context, locale);

            return allEntries.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary: " + e);
            return 0;
        }
    }

    private void loadUserWords(Context context) {
        List<UserDictionaryBridge.UserWord> userWords = UserDictionaryBridge.readAll(context);
        if (userWords.isEmpty()) return;
        int added = 0;
        for (UserDictionaryBridge.UserWord uw : userWords) {
            int freq = Math.max(uw.frequency, 200);
            String normalized = WordDictionary.normalize(uw.word);
            nativeSymSpell.addWord(normalized, uw.word, freq);
            if (uw.shortcut != null && !uw.shortcut.isEmpty()) {
                String normShort = WordDictionary.normalize(uw.shortcut);
                nativeSymSpell.addWord(normShort, uw.shortcut, freq);
            }
            added++;
        }
        if (added > 0) {
            Log.d(TAG, "Added " + added + " user dictionary words (native)");
        }
    }

    private void saveNativeCache(Context context, String locale) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            File dir = new File(context.getFilesDir(), CACHE_DIR);
            dir.mkdirs();
            String path = new File(dir, locale + ".ssnd").getAbsolutePath();
            if (nativeSymSpell.save(path)) {
                Log.d(TAG, "Saved native cache: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save native cache: " + e);
        }
    }

    private double computeScore(String normalizedInput, String normalizedCandidate,
                                String word, int frequency, int distance,
                                boolean isPrefix, int inputLen) {
        int effFreq = WordDictionary.effectiveFrequency(frequency);
        double distanceScore = 1.0 / (1 + distance);
        double frequencyScore = effFreq / 1600.0;
        double prefixBonus = 0;
        if (isPrefix) {
            if (inputLen <= 2) prefixBonus = 2.0;
            else prefixBonus = 5.0;
        } else if (normalizedCandidate.startsWith(normalizedInput)) {
            if (inputLen <= 2) prefixBonus = 1.5;
            else prefixBonus = 3.0;
        }

        int lenDiff = Math.abs(word.length() - inputLen);
        double lengthBonus;
        if (lenDiff == 0) lengthBonus = 0.35;
        else if (lenDiff == 1) lengthBonus = 0.2;
        else if (lenDiff == 2) lengthBonus = 0.05;
        else lengthBonus = -0.15 * Math.min(lenDiff, 4);

        return distanceScore + frequencyScore + prefixBonus + lengthBonus;
    }

    private void insertSorted(List<WordPredictor.Suggestion> list, WordPredictor.Suggestion item, int limit) {
        list.add(item);
        Collections.sort(list, new Comparator<WordPredictor.Suggestion>() {
            public int compare(WordPredictor.Suggestion a, WordPredictor.Suggestion b) {
                int s = Double.compare(b.score, a.score);
                if (s != 0) return s;
                return Integer.compare(a.distance, b.distance);
            }
        });
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
    }
}
