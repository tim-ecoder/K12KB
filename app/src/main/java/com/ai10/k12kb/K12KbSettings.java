package com.ai10.k12kb;

import android.content.SharedPreferences;
import com.fasterxml.jackson.annotation.JsonProperty;

public class K12KbSettings {

    public static final String APP_PREFERENCES = "kbsettings";

    public final String APP_PREFERENCES_14_NAV_PAD_ON_HOLD = "nav_pad_on_hold";
    public final String APP_PREFERENCES_15_PREDICTION_HEIGHT = "prediction_height";
    public final String APP_PREFERENCES_16_PREDICTION_COUNT = "prediction_count";
    public final String APP_PREFERENCES_17_PREDICTION_ENABLED = "prediction_enabled";
    public final String APP_PREFERENCES_18_LIGHT_THEME = "light_theme";
    public final String APP_PREFERENCES_19_PREDICTION_ENGINE = "prediction_engine";
    public final String APP_PREFERENCES_20_INTERFACE_LANG = "interface_lang";
    public final String APP_PREFERENCES_21_WORD_LEARNING = "word_learning";
    public final String APP_PREFERENCES_22_TRANSLATION_COUNT = "translation_count";
    public final String APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN = "prediction_bar_hidden";
    public final String APP_PREFERENCES_24_DICT_SIZE = "dict_size";
    public final String APP_PREFERENCES_25_TRANS_DICT_SIZE = "trans_dict_size";
    public final String APP_PREFERENCES_26_NEXT_WORD_PREDICTION = "next_word_prediction";
    public final String APP_PREFERENCES_27_KEYBOARD_AWARE = "keyboard_aware";
    public final String APP_PREFERENCES_28_AUTO_CAPITALIZATION = "auto_capitalization";
    public final String APP_PREFERENCES_29_TRANSPARENCY_MODE = "transparency_mode";
    public final String APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR = "pointer_mode_rect_color";
    public final String APP_PREFERENCES_13_POINTER_MODE_RECT = "pointer_mode_rect";
    public final String APP_PREFERENCES_12_ENSURE_ENTERED_TEXT = "ensure_entered_text";
    public final String APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN = "vibrate_on_key_down";
    public final String APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM = "notification_icon_system";
    public final String APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE = "gesture_mode_at_view_mode";
    public final String APP_PREFERENCES_8_SHOW_SWIPE_PANEL = "show_default_onscreen_keyboard";
    public final String APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR = "height_bottom_bar";
    public final String APP_PREFERENCES_6_MANAGE_CALL = "manage_call";
    public final String APP_PREFERENCES_5_LONG_PRESS_ALT = "long_press_alt";
    public final String APP_PREFERENCES_4_FLAG = "flag";
    public final String APP_PREFERENCES_3_ALT_SPACE = "alt_space";
    public final String APP_PREFERENCES_2_SHOW_TOAST = "show_toast";
    public final String APP_PREFERENCES_1_SENS_BOTTOM_BAR = "sens_bottom_bar";

    public static String RES_KEYBOARD_MECHANICS_DEFAULT = "bb_key_1_2/keyboard_mechanics";
    public static String RES_KEYBOARD_LAYOUTS = "keyboard_layouts";
    public static String RES_KEYBOARD_CORE = "keyboard_core";
    public static String RES_PLUGIN_DATA = "plugin_data";


    public final boolean KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT = false;
    public final boolean JS_PATCH_IS_ENABLED_DEFAULT = false;
    private static K12KbSettings _instance;
    SharedPreferences _mSettings;

    public static K12KbSettings Get(SharedPreferences mSettings) {
        if (_instance == null) {
            _instance = new K12KbSettings(mSettings);
        }
        return _instance;
    }

    public void ClearFromSettings(String pref_name) {
        if(_mSettings.contains(pref_name)) {
            _mSettings.edit().remove(pref_name).commit();
        }
    }

    public void CheckSettingOrSetDefault(String pref_name, boolean default_value) {
        if(!_mSettings.contains(pref_name)) {
            _mSettings.edit().putBoolean(pref_name, default_value).apply();
        }
    }

    public void CheckSettingOrSetDefault(String pref_name, String default_value) {
        if(!_mSettings.contains(pref_name)) {
            _mSettings.edit().putString(pref_name, default_value).apply();
        }
    }

    public void CheckSettingOrSetDefault(String pref_name, int default_value) {
        if(!_mSettings.contains(pref_name)) {
            _mSettings.edit().putInt(pref_name, default_value).apply();
        }
    }

    private K12KbSettings(SharedPreferences mSettings) {
        _mSettings = mSettings;
        CheckSettingOrSetDefault(APP_PREFERENCES_1_SENS_BOTTOM_BAR, 1);
        CheckSettingOrSetDefault(APP_PREFERENCES_2_SHOW_TOAST, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_3_ALT_SPACE, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_4_FLAG, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_5_LONG_PRESS_ALT, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_6_MANAGE_CALL, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR, 10);
        CheckSettingOrSetDefault(APP_PREFERENCES_8_SHOW_SWIPE_PANEL, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE, 3);
        CheckSettingOrSetDefault(APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_12_ENSURE_ENTERED_TEXT, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_13_POINTER_MODE_RECT, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR, 0xFF86888A);
        CheckSettingOrSetDefault(APP_PREFERENCES_14_NAV_PAD_ON_HOLD, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_15_PREDICTION_HEIGHT, 36);
        CheckSettingOrSetDefault(APP_PREFERENCES_16_PREDICTION_COUNT, 4);
        CheckSettingOrSetDefault(APP_PREFERENCES_17_PREDICTION_ENABLED, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_18_LIGHT_THEME, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_19_PREDICTION_ENGINE, 2);
        CheckSettingOrSetDefault(APP_PREFERENCES_20_INTERFACE_LANG, 0);
        CheckSettingOrSetDefault(APP_PREFERENCES_21_WORD_LEARNING, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_22_TRANSLATION_COUNT, 4);
        CheckSettingOrSetDefault(APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_24_DICT_SIZE, 35000);
        CheckSettingOrSetDefault(APP_PREFERENCES_25_TRANS_DICT_SIZE, 35000);
        CheckSettingOrSetDefault(APP_PREFERENCES_26_NEXT_WORD_PREDICTION, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_27_KEYBOARD_AWARE, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_28_AUTO_CAPITALIZATION, true);
    }

    public boolean GetBooleanValue(String name) {
        return _mSettings.getBoolean(name, false);
    }

    public String GetStringValue(String name) {
        return _mSettings.getString(name, "");
    }

    public int GetIntValue(String name) {
        return _mSettings.getInt(name, 0);
    }

    public void SetBooleanValue(String name, boolean value) {
        _mSettings.edit().putBoolean(name, value).apply();
    }

    public void SetIntValue(String name, int value) {
        _mSettings.edit().putInt(name, value).apply();
    }
    public boolean isDarkTheme() {
        return _mSettings.getBoolean(APP_PREFERENCES_18_LIGHT_THEME, false);
    }

    public void SetStringValue(String name, String value) {
        _mSettings.edit().putString(name, value).apply();
    }


    public static String CoreKeyboardSettingsResFileName = "keyboard_core";
    public static class CoreKeyboardSettings {

        @JsonProperty(index=10)
        public int TimeShortPress;
        @JsonProperty(index=20)
        public int TimeDoublePress;
        @JsonProperty(index=25)
        public int TimeTriplePress;
        @JsonProperty(index=30)
        public int TimeLongPress;
        @JsonProperty(index=40)
        public int TimeLongAfterShortPress;
        @JsonProperty(index=50)
        public int TimeWaitGestureUponKey0Hold;
        @JsonProperty(index=70)
        public int GestureFingerPressRadius;
        @JsonProperty(index=80)
        public int GestureMotionBaseSensitivity;

        @JsonProperty(index=90)
        public int GestureRow4BeginY;
        @JsonProperty(index=100)
        public int GestureRow1BeginY;
        @JsonProperty(index=110)
        public int TimeVibrate;

    }
}
