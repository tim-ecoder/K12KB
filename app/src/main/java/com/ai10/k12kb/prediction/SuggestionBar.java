package com.ai10.k12kb.prediction;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ai10.k12kb.R;

import java.math.RoundingMode;
import java.util.List;

/**
 * Configurable suggestion bar UI showing word predictions above the keyboard.
 * Styled to match the on-screen keyboard (Gboard dark theme).
 */
public class SuggestionBar extends LinearLayout {

    // Keyboard-matching colors (from colors.xml)
    private static int COLOR_SLOT_BG;
    private static int COLOR_SLOT_PRESSED;
    private static int COLOR_TEXT;
    private static int COLOR_TRANSLATION;

    private static final int SLOT_CORNER_RADIUS_DP = 5;
    private static final int SLOT_INSET_DP         = 3;
    private static final int SLOT_INSET_TB_DP      = 2;
    private static final int DIVIDER_HEIGHT_DP     = 2;

    private TextView[] slots;
    private int[] slotToSuggestion; // maps slot position to suggestion index
    private int numSlots;
    private boolean showingTranslations = false;
    private boolean phraseMatch = false;
    private int phraseResultCount = 0;
    private OnSuggestionClickListener clickListener;

    public interface OnSuggestionClickListener {
        void onSuggestionClicked(int index, String word);
    }

    public SuggestionBar(Context context, int heightDp, int slotCount) {
        super(context);

        COLOR_SLOT_BG = ContextCompat.getColor(context, R.color.keyboard_key_bg);
        COLOR_SLOT_PRESSED = ContextCompat.getColor(context, R.color.keyboard_pressed);
        COLOR_TEXT = ContextCompat.getColor(context, R.color.keyboard_text_color);
        COLOR_TRANSLATION = ContextCompat.getColor(context, R.color.candidate_translation);

        this.numSlots = Math.max(1, slotCount);
        this.slots = new TextView[numSlots];
        this.slotToSuggestion = new int[numSlots];
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int totalHeightDp = heightDp + DIVIDER_HEIGHT_DP * 2; // top + bottom dividers
        int totalHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, totalHeightDp, getResources().getDisplayMetrics());
        int dividerPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DIVIDER_HEIGHT_DP, getResources().getDisplayMetrics());

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, totalHeight));

        // Background: divider color fills all, bar layer inset top+bottom for divider stripes
        ColorDrawable dividerLayer = new ColorDrawable(ContextCompat.getColor(context, R.color.keyboard_background_color));
        ColorDrawable barLayer = new ColorDrawable(ContextCompat.getColor(context, R.color.keyboard_background_color));
        LayerDrawable bg = new LayerDrawable(new android.graphics.drawable.Drawable[]{dividerLayer, barLayer});
        bg.setLayerInset(0, 0, 0, 0, 0);                       // divider fills all
        bg.setLayerInset(1, 0, dividerPx, 0, dividerPx);       // bar layer leaves top+bottom for divider
        setBackground(bg);

        // Padding so slots don't overlap the divider stripes
        setPadding(0, dividerPx, 0, dividerPx);

        // Auto-compute font size: roughly 70% of height in dp
        float fontSizeSp = heightDp * 0.70f;
        if (fontSizeSp < 14) fontSizeSp = 14;
        if (fontSizeSp > 40) fontSizeSp = 40;

        int insetPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_INSET_DP, getResources().getDisplayMetrics());
        int insetTBPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_INSET_TB_DP, getResources().getDisplayMetrics());
        int cornerPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_CORNER_RADIUS_DP, getResources().getDisplayMetrics());

        int padHPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        for (int i = 0; i < numSlots; i++) {
            final int index = i;
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            lp.setMargins(insetPx, insetTBPx, (i == numSlots - 1) ? insetPx : 0, insetTBPx);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setIncludeFontPadding(false);
            tv.setTextColor(COLOR_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(padHPx, 0, padHPx, 0);
            tv.setBackground(createSlotDrawable(cornerPx));
            tv.setClickable(true);
            tv.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (clickListener != null) {
                        String text = ((TextView) v).getText().toString();
                        if (!text.isEmpty()) {
                            clickListener.onSuggestionClicked(slotToSuggestion[index], text);
                        }
                    }
                }
            });
            slots[i] = tv;
            addView(tv);
        }
    }

    private static StateListDrawable createSlotDrawable(int cornerPx) {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(COLOR_SLOT_PRESSED);
        pressed.setCornerRadius(cornerPx);

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(COLOR_SLOT_BG);
        normal.setCornerRadius(cornerPx);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.clickListener = listener;
    }

    public int getNumSlots() {
        return numSlots;
    }

    /**
     * Update displayed suggestions.
     */
    public void update(List<WordPredictor.Suggestion> suggestions, String prefix) {
        showingTranslations = false;
        String pfx = (prefix != null) ? prefix.toLowerCase() : "";
        int count = (suggestions != null) ? suggestions.size() : 0;

        // Build mapping: suggestion index -> slot position
        // Priority (index 0) in center, rarer words toward left
        int[] suggToSlot = new int[numSlots];
        int center = numSlots / 2;
        suggToSlot[0] = center;
        int right = center + 1;
        int left = center - 1;
        for (int s = 1; s < numSlots; s++) {
            if (right < numSlots) {
                suggToSlot[s] = right++;
            } else if (left >= 0) {
                suggToSlot[s] = left--;
            }
        }
        // Build reverse mapping for click handler
        for (int s = 0; s < numSlots; s++) {
            slotToSuggestion[suggToSlot[s]] = s;
        }

        // Clear all slots first
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setVisibility(View.VISIBLE);
            slots[i].setTypeface(null, Typeface.NORMAL);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = 1;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
        }

        // First pass: set text, typeface, ellipsize on all slots
        float[] textWidths = new float[numSlots];
        for (int s = 0; s < count && s < numSlots; s++) {
            String word = suggestions.get(s).word;
            int slot = suggToSlot[s];
            slots[slot].setText(word);
            slots[slot].setTextColor(COLOR_TEXT);
            if (s == 0) {
                slots[slot].setTypeface(null, Typeface.BOLD);
                slots[slot].setEllipsize(null);
            } else {
                slots[slot].setTypeface(null, Typeface.NORMAL);
                slots[slot].setEllipsize(TextUtils.TruncateAt.START);
            }
            textWidths[s] = slots[slot].getPaint().measureText(word);
        }

        // Second pass: set weights
        // Priority uses WRAP_CONTENT (text always fits) + fixed weight for
        // modest breathing room. Non-priority gets text-proportional weight.
        for (int s = 0; s < count && s < numSlots; s++) {
            int slot = suggToSlot[s];
            LayoutParams lp = (LayoutParams) slots[slot].getLayoutParams();
            if (s == 0) {
                lp.weight = 50f;
                lp.width = LayoutParams.WRAP_CONTENT;
            } else {
                lp.weight = Math.max(textWidths[s], 30f);
                lp.width = 0;
            }
            slots[slot].setLayoutParams(lp);
        }
    }

    /**
     * Update displayed translations (blue text).
     */
    public void updateTranslation(List<String> translations, String sourceWord, int maxSlots) {
        updateTranslation(translations, sourceWord, maxSlots, false, 0);
    }

    public void updateTranslation(List<String> translations, String sourceWord, int maxSlots, boolean isPhraseMatch) {
        updateTranslation(translations, sourceWord, maxSlots, isPhraseMatch, isPhraseMatch ? translations.size() : 0);
    }

    public void updateTranslation(List<String> translations, String sourceWord, int maxSlots,
                                   boolean isPhraseMatch, int numPhraseResults) {
        showingTranslations = true;
        phraseMatch = isPhraseMatch;
        phraseResultCount = numPhraseResults;
        int limit = Math.min(numSlots, maxSlots);
        int count = (translations != null) ? Math.min(translations.size(), limit) : 0;

        // Build center-based mapping: priority in center, next right, then left
        int[] transToSlot = new int[limit];
        int center = limit / 2;
        transToSlot[0] = center;
        int right = center + 1;
        int left = center - 1;
        for (int s = 1; s < limit; s++) {
            if (right < limit) {
                transToSlot[s] = right++;
            } else if (left >= 0) {
                transToSlot[s] = left--;
            }
        }

        // Setup all slots
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setTypeface(null, Typeface.NORMAL);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            slots[i].setTextColor(COLOR_TRANSLATION);
            slotToSuggestion[i] = i;
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            if (i < limit) {
                slots[i].setVisibility(View.VISIBLE);
                lp.weight = 1;
                lp.width = 0;
            } else {
                slots[i].setVisibility(View.GONE);
                lp.weight = 0;
                lp.width = 0;
            }
            slots[i].setLayoutParams(lp);
        }

        // Build reverse mapping for click handler
        for (int s = 0; s < count; s++) {
            slotToSuggestion[transToSlot[s]] = s;
        }

        // First pass: set text, typeface, ellipsize
        float[] transWidths = new float[limit];
        for (int i = 0; i < count; i++) {
            int slot = transToSlot[i];
            String word = translations.get(i);
            slots[slot].setText(word);
            slots[slot].setTextColor(COLOR_TRANSLATION);
            if (i < numPhraseResults) {
                slots[slot].setTypeface(null, Typeface.BOLD);
            } else {
                slots[slot].setTypeface(null, Typeface.NORMAL);
            }
            if (i == 0) {
                slots[slot].setEllipsize(null);
            } else {
                slots[slot].setEllipsize(TextUtils.TruncateAt.END);
            }
            transWidths[i] = slots[slot].getPaint().measureText(word);
        }

        // Second pass: set weights
        for (int i = 0; i < count; i++) {
            int slot = transToSlot[i];
            LayoutParams lp = (LayoutParams) slots[slot].getLayoutParams();
            if (i == 0) {
                lp.weight = 50f;
                lp.width = LayoutParams.WRAP_CONTENT;
            } else {
                lp.weight = Math.max(transWidths[i], 30f);
                lp.width = 0;
            }
            slots[slot].setLayoutParams(lp);
        }
    }

    public boolean isShowingTranslations() {
        return showingTranslations;
    }

    public boolean isPhraseMatch() {
        return phraseMatch;
    }

    /**
     * Check if a specific clicked index is a phrase (bigram) result.
     */
    public boolean isPhraseResult(int index) {
        return phraseMatch && index < phraseResultCount;
    }

    /**
     * Clear all suggestions.
     */
    public void clear() {
        showingTranslations = false;
        phraseMatch = false;
        phraseResultCount = 0;
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setVisibility(View.VISIBLE);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = 1;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
        }
    }
}
