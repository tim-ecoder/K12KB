package com.ai10.k12kb;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import static com.ai10.k12kb.KeyboardLayoutManager.IsCurrentDevice;
import static com.ai10.k12kb.KeyboardLayoutManager.getDeviceFullMODEL;

import yuku.ambilwarna.AmbilWarnaDialog;

public class ActivitySettings extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    public static final int REQUEST_PERMISSION_CODE = 101;
    private static final int REQUEST_STORAGE_PERMISSION_CODE = 102;

    private K12KbSettings k12KbSettings;

    private LinearLayout layout;

    private float touchY;

    private boolean pendingSave = false;
    private boolean pendingLoad = false;

    private boolean SetSwitchStateOrDefault(Switch switch1, String settingName) {
        boolean enabled = k12KbSettings.GetBooleanValue(settingName);
        switch1.setChecked(enabled);
        return enabled;
    }

    private void SetProgressOrDefault(SeekBar seekBar, String settingName) {
        seekBar.setProgress(k12KbSettings.GetIntValue(settingName));
    }

    private String deviceFullMODEL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        if (k12KbSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        }

        setContentView(R.layout.activity_settings);

        layout = (LinearLayout) findViewById(R.id.activity_settings);
        LinearLayout containerKeyboardLayouts = (LinearLayout) findViewById(R.id.container_keyboard_layouts);
        try {

            ArrayList<KeyboardLayout.KeyboardLayoutOptions> keyboardLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());

            Switch defaultKeyboardLayoutSwitch = (Switch) findViewById(R.id.default_keyboard_layout);
            boolean isFirst = true;
            int enableCount = 0;
            deviceFullMODEL = getDeviceFullMODEL();
            for (KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : keyboardLayouts) {

                boolean isDevice = IsCurrentDevice(deviceFullMODEL, keyboardLayoutOptions);
                if(!isDevice)
                    continue;

                Switch currentKeyboardLayoutSwitch;
                if(isFirst) {
                    currentKeyboardLayoutSwitch = defaultKeyboardLayoutSwitch;
                    currentKeyboardLayoutSwitch.setVisibility(View.VISIBLE);
                    ((ViewGroup)currentKeyboardLayoutSwitch.getParent()).removeView(currentKeyboardLayoutSwitch);
                    isFirst = false;
                } else {
                    currentKeyboardLayoutSwitch = new Switch(this);
                    currentKeyboardLayoutSwitch.setId(keyboardLayoutOptions.getId());
                    currentKeyboardLayoutSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultKeyboardLayoutSwitch.getTextSize());
                }

                // Style as individual pill
                LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                int marginH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int marginT = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
                pillParams.setMargins(marginH, marginT, marginH, 0);
                currentKeyboardLayoutSwitch.setLayoutParams(pillParams);
                currentKeyboardLayoutSwitch.setBackgroundResource(R.drawable.bg_item_pill);
                int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                currentKeyboardLayoutSwitch.setPadding(padH, pad, padH, pad);
                TypedValue _tv = new TypedValue();
                getTheme().resolveAttribute(R.attr.textPrimaryColor, _tv, true);
                currentKeyboardLayoutSwitch.setTextColor(_tv.data);
                currentKeyboardLayoutSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

                containerKeyboardLayouts.addView(currentKeyboardLayoutSwitch);

                currentKeyboardLayoutSwitch.setText(keyboardLayoutOptions.OptionsName);
                k12KbSettings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), k12KbSettings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
                boolean enabled = SetSwitchStateOrDefault(currentKeyboardLayoutSwitch, keyboardLayoutOptions.getPreferenceName());
                if(enabled)
                    enableCount++;

                currentKeyboardLayoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        k12KbSettings.SetBooleanValue(keyboardLayoutOptions.getPreferenceName(), isChecked);
                    }
                });

            }

            if(isFirst) {
                Switch currentKeyboardLayoutSwitch = defaultKeyboardLayoutSwitch;
                currentKeyboardLayoutSwitch.setVisibility(View.VISIBLE);
                ((ViewGroup)currentKeyboardLayoutSwitch.getParent()).removeView(currentKeyboardLayoutSwitch);

                KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions = keyboardLayouts.get(0);

                LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                int marginH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int marginT = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
                pillParams.setMargins(marginH, marginT, marginH, 0);
                currentKeyboardLayoutSwitch.setLayoutParams(pillParams);
                currentKeyboardLayoutSwitch.setBackgroundResource(R.drawable.bg_item_pill);
                int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                currentKeyboardLayoutSwitch.setPadding(padH, pad, padH, pad);
                TypedValue _tv2 = new TypedValue();
                getTheme().resolveAttribute(R.attr.textPrimaryColor, _tv2, true);
                currentKeyboardLayoutSwitch.setTextColor(_tv2.data);
                currentKeyboardLayoutSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

                containerKeyboardLayouts.addView(currentKeyboardLayoutSwitch);

                currentKeyboardLayoutSwitch.setText(keyboardLayoutOptions.OptionsName);
                k12KbSettings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), k12KbSettings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
                boolean enabled = SetSwitchStateOrDefault(currentKeyboardLayoutSwitch, keyboardLayoutOptions.getPreferenceName());
                if(enabled)
                    enableCount++;

                currentKeyboardLayoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        k12KbSettings.SetBooleanValue(keyboardLayoutOptions.getPreferenceName(), isChecked);
                    }
                });

            }

            if(enableCount == 0) {
                KeyboardLayout.KeyboardLayoutOptions defLayout = keyboardLayouts.get(0);
                k12KbSettings.SetBooleanValue(defLayout.getPreferenceName(), true);
                SetSwitchStateOrDefault(defaultKeyboardLayoutSwitch, defLayout.getPreferenceName());
            }

        } catch (Throwable ex) {
            Toast.makeText(this, "", Toast.LENGTH_SHORT).show();
            return;
        }

        SeekBar sens_bottom_bar = (SeekBar) findViewById(R.id.seekBar);
        SetProgressOrDefault(sens_bottom_bar, k12KbSettings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);

        sens_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_1_SENS_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Switch toast_show_lang = (Switch) findViewById(R.id.toast_show_lang);
        SetSwitchStateOrDefault(toast_show_lang, k12KbSettings.APP_PREFERENCES_2_SHOW_TOAST);
        toast_show_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_2_SHOW_TOAST, isChecked);
            }
        });


        Switch switch_alt_space = (Switch) findViewById(R.id.switch_alt_space);
        SetSwitchStateOrDefault(switch_alt_space, k12KbSettings.APP_PREFERENCES_3_ALT_SPACE);

        switch_alt_space.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_3_ALT_SPACE, isChecked);

            }
        });

        Switch switch_flag = (Switch) findViewById(R.id.switch_flag);
        SetSwitchStateOrDefault(switch_flag, k12KbSettings.APP_PREFERENCES_4_FLAG);

        switch_flag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_4_FLAG, isChecked);
            }
        });

        Switch switch_long_press_alt = (Switch) findViewById(R.id.switch_long_press_alt);
        SetSwitchStateOrDefault(switch_long_press_alt, k12KbSettings.APP_PREFERENCES_5_LONG_PRESS_ALT);

        switch_long_press_alt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_5_LONG_PRESS_ALT, isChecked);
            }
        });

        Switch switch_manage_call = (Switch) findViewById(R.id.switch_manage_call);

        SetSwitchStateOrDefault(switch_manage_call, k12KbSettings.APP_PREFERENCES_6_MANAGE_CALL);
        switch_manage_call.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked && ActivitySettings.this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED){
                    if (ActivityCompat.checkSelfPermission(ActivitySettings.this, Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(ActivitySettings.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                    }
                }
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_6_MANAGE_CALL, isChecked);
            }
        });

        SeekBar height_bottom_bar = (SeekBar) findViewById(R.id.seekBarBtnPanel);
        SetProgressOrDefault(height_bottom_bar, k12KbSettings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);

        height_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Switch switch_show_default_onscreen_keyboard = (Switch) findViewById(R.id.switch_show_default_onscreen_keyboard);
        SetSwitchStateOrDefault(switch_show_default_onscreen_keyboard, k12KbSettings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);

        switch_show_default_onscreen_keyboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL, isChecked);
            }
        });

        Spinner staticSpinner = (Spinner) findViewById(R.id.static_spinner_p9);

        ArrayAdapter<CharSequence> staticAdapter = ArrayAdapter
                .createFromResource(this, R.array.pref_p9_gesture_modes_array, android.R.layout.simple_spinner_item);

        staticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        staticSpinner.setAdapter(staticAdapter);

        int spinner_p9_from_pref =  k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE);
        staticSpinner.setSelection(spinner_p9_from_pref);

        staticSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                staticSpinner.setSelection(spinner_p9_from_pref);
            }
        });


        Switch switch_notification_icon_system = (Switch) findViewById(R.id.switch_notification_icon_system);
        SetSwitchStateOrDefault(switch_notification_icon_system, k12KbSettings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);

        switch_notification_icon_system.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM, isChecked);
            }
        });

        Switch switch_vibrate_on_key_down = (Switch) findViewById(R.id.switch_vibrate_on_key_down);
        SetSwitchStateOrDefault(switch_vibrate_on_key_down, k12KbSettings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN);

        switch_vibrate_on_key_down.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN, isChecked);
            }
        });

        Switch switch_ensure_entered_text = (Switch) findViewById(R.id.switch_ensure_entered_text);
        SetSwitchStateOrDefault(switch_ensure_entered_text, k12KbSettings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT);

        switch_ensure_entered_text.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT, isChecked);
            }
        });


        Switch switch_pointer_mode_rect = (Switch) findViewById(R.id.switch_pointer_mode_rect);
        SetSwitchStateOrDefault(switch_pointer_mode_rect, k12KbSettings.APP_PREFERENCES_13_POINTER_MODE_RECT);

        switch_pointer_mode_rect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_13_POINTER_MODE_RECT, isChecked);
            }
        });

        int color = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR);

        final View colorSwatch = findViewById(R.id.color_swatch_p13a);
        android.graphics.drawable.GradientDrawable swatchBg = new android.graphics.drawable.GradientDrawable();
        swatchBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        swatchBg.setCornerRadius(6 * getResources().getDisplayMetrics().density);
        swatchBg.setColor(color);
        colorSwatch.setBackground(swatchBg);

        colorSwatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int currentColor = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR);
                new AmbilWarnaDialog(
                        ActivitySettings.this,
                        currentColor,
                        new AmbilWarnaDialog.OnAmbilWarnaListener() {
                            @Override
                            public void onOk(AmbilWarnaDialog dialog, int color) {
                                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR, color);
                                android.graphics.drawable.GradientDrawable bg =
                                        (android.graphics.drawable.GradientDrawable) colorSwatch.getBackground();
                                bg.setColor(color);
                            }

                            @Override
                            public void onCancel(AmbilWarnaDialog dialog) {
                            }
                        }
                ).show();
            }
        });

        Switch switchAutoCapitalization = (Switch) findViewById(R.id.switch_auto_capitalization);
        SetSwitchStateOrDefault(switchAutoCapitalization, k12KbSettings.APP_PREFERENCES_28_AUTO_CAPITALIZATION);
        switchAutoCapitalization.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_28_AUTO_CAPITALIZATION, isChecked);
            }
        });

        Switch switchTransparencyMode = (Switch) findViewById(R.id.switch_transparency_mode);
        k12KbSettings.CheckSettingOrSetDefault(k12KbSettings.APP_PREFERENCES_29_TRANSPARENCY_MODE, true);
        SetSwitchStateOrDefault(switchTransparencyMode, k12KbSettings.APP_PREFERENCES_29_TRANSPARENCY_MODE);
        switchTransparencyMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_29_TRANSPARENCY_MODE, isChecked);
            }
        });

        Switch switchLightTheme = (Switch) findViewById(R.id.switch_light_theme);
        SetSwitchStateOrDefault(switchLightTheme, k12KbSettings.APP_PREFERENCES_18_LIGHT_THEME);
        switchLightTheme.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_18_LIGHT_THEME, isChecked);
                Intent restart = new Intent(ActivitySettings.this, ActivitySettings.class);
                finish();
                startActivity(restart);
            }
        });







        Switch switch_p14 = (Switch) findViewById(R.id.switch_p14_nav_pad_on_hold);
        SetSwitchStateOrDefault(switch_p14, k12KbSettings.APP_PREFERENCES_14_NAV_PAD_ON_HOLD);

        switch_p14.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_14_NAV_PAD_ON_HOLD, isChecked);
            }
        });

        Button btnSaveSettings = (Button) findViewById(R.id.button_save_settings);
        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkStoragePermission()) {
                    saveSettingsToFile();
                } else {
                    pendingSave = true;
                    requestStoragePermission();
                }
            }
        });

        Button btnLoadSettings = (Button) findViewById(R.id.button_load_settings);
        btnLoadSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkStoragePermission()) {
                    loadSettingsFromFile();
                } else {
                    pendingLoad = true;
                    requestStoragePermission();
                }
            }
        });

        PillBadgeHelper.applyToContainer(layout);

        PillBadgeHelper.applyHints(layout, new int[][] {
            {R.id.toast_show_lang, R.string.pref_p_2_hint},
            {R.id.switch_alt_space, R.string.pref_p_3_hint},
            {R.id.switch_long_press_alt, R.string.pref_p_5_hint},
            {R.id.switch_manage_call, R.string.pref_p_6_hint},
            {R.id.switch_show_default_onscreen_keyboard, R.string.pref_p_8_hint},
            {R.id.static_spinner_p9, R.string.pref_p9_gesture_modes_comment},
            {R.id.switch_ensure_entered_text, R.string.pref_p_12_hint},
            {R.id.switch_pointer_mode_rect, R.string.pref_p_13_hint},
            {R.id.button_pointer_mode_rect_color_picker, R.string.pref_p_13a_hint},
            {R.id.switch_p14_nav_pad_on_hold, R.string.pref_p_14_hint},
        });

    }


    private File getSettingsFile() {
        FileJsonUtils.Initialize(this);
        File dir = new File(FileJsonUtils.PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "settings.json");
    }

    private boolean checkStoragePermission() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingSave) {
                    pendingSave = false;
                    saveSettingsToFile();
                }
                if (pendingLoad) {
                    pendingLoad = false;
                    loadSettingsFromFile();
                }
            } else {
                pendingSave = false;
                pendingLoad = false;
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveSettingsToFile() {
        try {
            SharedPreferences prefs = getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();

            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("app", "K12KB");
            root.put("exported_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date()));
            root.put("device", android.os.Build.MODEL);

            JSONObject input = new JSONObject();
            JSONObject display = new JSONObject();
            JSONObject gesture = new JSONObject();
            JSONObject phone = new JSONObject();
            JSONObject navigation = new JSONObject();
            JSONObject keyboardLayouts = new JSONObject();
            JSONObject jsonPatches = new JSONObject();
            JSONObject swipeModes = new JSONObject();
            JSONObject other = new JSONObject();

            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                Object jsonVal = val;
                if (val instanceof Boolean) {
                    jsonVal = ((Boolean) val).booleanValue();
                } else if (val instanceof Integer) {
                    jsonVal = ((Integer) val).intValue();
                } else if (val instanceof Long) {
                    jsonVal = ((Long) val).longValue();
                } else if (val instanceof Float) {
                    jsonVal = ((Float) val).floatValue();
                }

                // Categorize by key name
                if (key.endsWith(".js")) {
                    jsonPatches.put(key, jsonVal);
                } else if (key.startsWith("GESTURE_AT_VIEW_MODE_")) {
                    swipeModes.put(key, jsonVal);
                } else if (key.equals("sens_bottom_bar") || key.equals("height_bottom_bar")
                        || key.equals("show_default_onscreen_keyboard")
                        || key.equals("long_press_alt") || key.equals("alt_space")
                        || key.equals("vibrate_on_key_down") || key.equals("ensure_entered_text")
                        || key.equals("prediction_height") || key.equals("prediction_count")
                        || key.equals("prediction_enabled")
                        || key.equals("prediction_engine")) {
                    input.put(key, jsonVal);
                } else if (key.equals("show_toast") || key.equals("flag")
                        || key.equals("notification_icon_system")
                        || key.equals("light_theme")) {
                    display.put(key, jsonVal);
                } else if (key.equals("gesture_mode_at_view_mode")
                        || key.equals("pointer_mode_rect") || key.equals("pointer_mode_rect_color")) {
                    gesture.put(key, jsonVal);
                } else if (key.equals("manage_call")) {
                    phone.put(key, jsonVal);
                } else if (key.equals("nav_pad_on_hold")) {
                    navigation.put(key, jsonVal);
                } else if (key.startsWith("keyboard_layout_") || key.startsWith("kl_")) {
                    keyboardLayouts.put(key, jsonVal);
                } else {
                    other.put(key, jsonVal);
                }
            }

            JSONObject settings = new JSONObject();
            settings.put("input", input);
            settings.put("display", display);
            settings.put("gesture", gesture);
            settings.put("phone", phone);
            settings.put("navigation", navigation);
            root.put("settings", settings);
            root.put("keyboard_layouts", keyboardLayouts);
            if (jsonPatches.length() > 0) {
                root.put("json_patches", jsonPatches);
            }
            if (swipeModes.length() > 0) {
                root.put("swipe_modes", swipeModes);
            }
            if (other.length() > 0) {
                root.put("other", other);
            }

            File file = getSettingsFile();
            FileWriter writer = new FileWriter(file);
            writer.write(root.toString(2));
            writer.close();
            Toast.makeText(this, "Settings saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadSettingsFromFile() {
        try {
            File file = getSettingsFile();
            if (!file.exists()) {
                Toast.makeText(this, "No settings file found at " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            SharedPreferences prefs = getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear().apply();

            JSONObject settings = json.getJSONObject("settings");
            Iterator<String> sections = settings.keys();
            while (sections.hasNext()) {
                String section = sections.next();
                JSONObject sectionObj = settings.getJSONObject(section);
                applyJsonToEditor(editor, sectionObj);
            }
            if (json.has("keyboard_layouts")) {
                applyJsonToEditor(editor, json.getJSONObject("keyboard_layouts"));
            }
            if (json.has("json_patches")) {
                applyJsonToEditor(editor, json.getJSONObject("json_patches"));
            }
            if (json.has("swipe_modes")) {
                applyJsonToEditor(editor, json.getJSONObject("swipe_modes"));
            }
            if (json.has("other")) {
                applyJsonToEditor(editor, json.getJSONObject("other"));
            }

            editor.commit();
            Toast.makeText(this, "Settings loaded from " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Intent restart = new Intent(this, ActivitySettings.class);
            finish();
            startActivity(restart);
        } catch (Exception e) {
            Toast.makeText(this, "Load failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyJsonToEditor(SharedPreferences.Editor editor, JSONObject obj) throws org.json.JSONException {
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = obj.get(key);
            if (val instanceof Boolean) {
                editor.putBoolean(key, ((Boolean) val).booleanValue());
            } else if (val instanceof Number) {
                editor.putInt(key, ((Number) val).intValue());
            } else if (val instanceof String) {
                editor.putString(key, (String) val);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                touchY = event.getY() - layout.getY();
            }
            break;
            case MotionEvent.ACTION_MOVE: {
                display.getSize(size);
                if((event.getY() - touchY) >= 10) break;
                if((event.getY() - touchY) <=  size.y - layout.getHeight() - 300) break;
                layout.setY(event.getY() - touchY);
            }
            break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        int step = 500;
        if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
            display.getSize(size);
            if(layout.getY()+step <=  size.y - layout.getHeight() - 300){
                layout.setY(layout.getY()+step);
            }else{
                layout.setY(size.y - layout.getHeight() - 300);
            }
            return true;
        }
        if(keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            display.getSize(size);
            if(layout.getY()-step >=  10){
                layout.setY(layout.getY()-step);
            }else{
                layout.setY(10);
            }
            return true;
        }
        if(keyCode == KeyEvent.KEYCODE_BACK){
            Intent switchActivityIntent = new Intent(this, ActivityMain.class);
            startActivity(switchActivityIntent);
        }
        return false;
    }
}