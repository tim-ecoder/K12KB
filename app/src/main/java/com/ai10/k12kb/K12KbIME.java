package com.ai10.k12kb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;
import androidx.core.os.BuildCompat;
import android.telephony.PhoneStateListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;

import android.widget.Toast;
import com.google.android.voiceime.VoiceRecognitionTrigger;
import com.ai10.k12kb.input.CallStateCallback;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static android.content.ContentValues.TAG;
import static com.ai10.k12kb.FileJsonUtils.GetContext;
import static com.ai10.k12kb.KeyboardLayoutManager.IsCurrentDevice;
import static com.ai10.k12kb.KeyboardLayoutManager.getDeviceFullMODEL;
import static com.ai10.k12kb.K12KbSettings.RES_KEYBOARD_MECHANICS_DEFAULT;

@Keep
public class K12KbIME extends InputMethodServiceCoreCustomizable implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {


    public static K12KbIME Instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public String TITLE_NAV_TEXT;
    public String TITLE_NAV_FV_TEXT;
    public String TITLE_SYM_TEXT;
    public String TITLE_SYM2_TEXT;
    public String TITLE_GESTURE_INPUT;
    public String TITLE_GESTURE_INPUT_UP_DOWN;
    public String TITLE_GESTURE_INPUT_LIST;
    public String TITLE_GESTURE_VIEW_POINTER;
    public String TITLE_GESTURE_VIEW;
    public String TITLE_GESTURE_OFF;
    public String TITLE_DIGITS_TEXT;








    //settings
    private int pref_swipe_panel_height = 10;
    private boolean preference_show_flag = false;
    private boolean pref_system_icon_no_notification_text = false;

    //Предзагружаем клавиатуры, чтобы не плодить объекты
    private Keyboard keyboardNavigation;

    private Keyboard keyboardSwipeOnScreen;


    private final NotificationProcessor notificationProcessor = new NotificationProcessor();

    private Toast mainToast;


    KeyboardLayout.KeyboardLayoutOptions.IconRes AltOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes navIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes navFnIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes digitsPadIconRes;

    private String deviceFullMODEL;


    private boolean isNotStarted = true;

    private String onCreateError = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    @Override
    public void onCreate() {

        String STEP = "init";
        try {
            STEP = "FileJsonUtils.Initialize";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            FileJsonUtils.Initialize(this);

            STEP = "super.onCreate (keyboard_core.json)";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            super.onCreate();

            Instance = this;

            STEP = "getString resources";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            TITLE_NAV_TEXT = getString(R.string.notification_kb_state_nav_mode);
            TITLE_NAV_FV_TEXT = getString(R.string.notification_kb_state_nav_fn_mode);
            TITLE_SYM_TEXT = getString(R.string.notification_kb_state_alt_mode);
            TITLE_SYM2_TEXT = getString(R.string.notification_kb_state_sym_mode);
            TITLE_GESTURE_INPUT = getString(R.string.notification_kb_state_gesture_input);
            TITLE_GESTURE_INPUT_UP_DOWN = getString(R.string.notification_kb_state_gesture_input_up_down);
            TITLE_GESTURE_VIEW = getString(R.string.notification_kb_state_gesture_view);
            TITLE_GESTURE_OFF = getString(R.string.notification_kb_state_gesture_off);
            TITLE_GESTURE_INPUT_LIST = getString(R.string.notification_kb_state_gesture_input_list);
            TITLE_GESTURE_VIEW_POINTER = getString(R.string.notification_kb_state_gesture_pointer);
            TITLE_DIGITS_TEXT = getString(R.string.notification_kb_state_digits_pad);

            STEP = "CreateIconRes";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            AltOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt_one, R.drawable.ic_kb_alt_one);
            AltAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt_all);
            AltHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt);
            SymOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym_one, R.drawable.ic_kb_sym_one);
            SymAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym_all);
            SymHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym);

            navIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav, R.drawable.ic_kb_nav);
            navFnIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav_fn, R.drawable.ic_kb_nav_fn);
            digitsPadIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_digits, R.drawable.ic_kb_digits);

            STEP = "telephony setup";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            callStateCallback = new CallStateCallback();
            telephonyManager = getTelephonyManager();
            telecomManager = getTelecomManager();

            if (telephonyManager != null) {
                try {
                    telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
                } catch (Throwable texc) {
                    Log.w(TAG2, "telephony listen failed (non-critical): " + texc);
                }
            }

            STEP = "SharedPreferences";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            Context psc = GetContext(this);
            k12KbSettings = K12KbSettings.Get(psc.getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

            TIME_VIBRATE = CoreKeyboardSettings.TimeVibrate;

            STEP = "LoadSettingsAndKeyboards";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            deviceFullMODEL = getDeviceFullMODEL();
            Log.i(TAG2, "  deviceFullMODEL=" + deviceFullMODEL);
            LoadSettingsAndKeyboards(deviceFullMODEL);

            STEP = "LoadKeyProcessingMechanics";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            LoadKeyProcessingMechanics(this);

            STEP = "SatedaKeyboard + inflate keyboard view";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            keyboardSwipeOnScreen = new SatedaKeyboard(this, R.xml.space_empty, pref_swipe_panel_height);

            keyboardView = (ViewSatedaKeyboard) getLayoutInflater().inflate(R.layout.keyboard, null);
            keyboardView.setKeyboard(keyboardSwipeOnScreen);
            keyboardView.setOnKeyboardActionListener(this);
            keyboardView.setPreviewEnabled(false);
            keyboardView.setService(this);
            keyboardView.clearAnimation();
            keyboardView.setShowFlag(preference_show_flag);

            STEP = "WordPredictor + SuggestionBar";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            initPrediction();

            STEP = "keyboardNavigation";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            keyboardNavigation = new Keyboard(this, R.xml.navigation);

            STEP = "notificationProcessor.Initialize";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            try {
                notificationProcessor.Initialize(getApplicationContext());
            } catch (Throwable nexc) {
                Log.w(TAG2, "notification init failed (non-critical): " + nexc);
            }

            STEP = "vibratorService";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            vibratorService = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            STEP = "UpdateVisualization";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            try {
                UpdateGestureModeVisualization(false);
                UpdateKeyboardModeVisualization();
            } catch (Throwable vexc) {
                Log.w(TAG2, "visualization update failed (non-critical): " + vexc);
            }

            STEP = "LoadShortcuts";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            try {
                LoadShortcuts();
            } catch (Throwable sexc) {
                Log.w(TAG2, "LoadShortcuts failed (non-critical): " + sexc);
            }

            STEP = "VoiceRecognitionTrigger";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            mVoiceRecognitionTrigger = new VoiceRecognitionTrigger(this);
            mVoiceRecognitionTrigger.register(new VoiceRecognitionTrigger.Listener() {

                @Override
                public void onVoiceImeEnabledStatusChange() {
                    // The call back is done on the main thread.
                    updateVoiceImeStatus();
                }
            });

            isNotStarted = false;
            Log.i(TAG2, "onCreate COMPLETED SUCCESSFULLY");

        } catch(Throwable ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            onCreateError = "FAILED AT STEP [" + STEP + "]: " + ex + "\n" + sw.toString();
            Log.e(TAG2, "onCreate " + onCreateError);
            FileJsonUtils.LogErrorToGui("onCreate " + onCreateError);
            isNotStarted = true;
            Instance = null;
            // Do NOT throw - show error in keyboard view instead
        }
    }




    //region LoadShortcuts

    private void LoadShortcuts() {

        Context c = K12KbIME.Instance;

        ShortcutInfo dsQuickSettings = new ShortcutInfo.Builder(c, "shct_id_QuickSettings")
                .setShortLabel("Show quick settings")
                .setIcon(Icon.createWithResource(c, R.drawable.ic_rus_shift_all))
                .setIntents(
                        new Intent[]{
                                new Intent(IntentQuickSettings.ACTION).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                .setRank(1)
                .build();

        ShortcutInfo dsNotifications = new ShortcutInfo.Builder(c, "shct_id_Notifications")
                .setShortLabel("Show notifications")
                .setIcon(Icon.createWithResource(c, R.drawable.ic_rus_shift_all))
                .setIntents(
                        new Intent[]{
                                new Intent(IntentNotifications.ACTION).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                .setRank(1)
                .build();

        final ShortcutManager shortcutManager = c.getSystemService(ShortcutManager.class);
        shortcutManager.setDynamicShortcuts(Arrays.asList(dsQuickSettings, dsNotifications));
    }


    //endregion

    @Override
    public void onDestroy() {
        Instance = null;
        notificationProcessor.CancelAll();
        if (telephonyManager != null) {
            telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_NONE);
        }
        if(mVoiceRecognitionTrigger != null) {
            mVoiceRecognitionTrigger.unregister(this);
        }
        super.onDestroy();
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG2, "onCreateInputView isNotStarted=" + isNotStarted);
        if(isNotStarted) {
            // Show error message in keyboard area instead of returning null
            if (onCreateError != null) {
                android.widget.TextView errorView = new android.widget.TextView(this);
                errorView.setText("K12KB INIT ERROR:\n" + onCreateError);
                errorView.setTextSize(10);
                errorView.setTextColor(0xFFFF4444);
                errorView.setBackgroundColor(0xFF000000);
                errorView.setPadding(8, 8, 8, 8);
                errorView.setMaxLines(15);
                return errorView;
            }
            return null;
        }
        super.onCreateInputView();
        keyboardView.setOnKeyboardActionListener(this);
        return createInputViewWithSuggestions(keyboardView);
    }


    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        Log.d(TAG2, "onStartInputView");
        if(isNotStarted)
            return;
        super.onStartInputView(info, restarting);
        mVoiceRecognitionTrigger.onStartInputView();
        IsVisualKeyboardOpen = true;
        /*
        if (wordPredictor != null && !predictionBarHiddenByDefault) {
            setCandidatesViewShown(true);
            updatePredictorWordAtCursor();
        } else {
            setCandidatesViewShown(false);
        }
         */


    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        Log.d(TAG2, "onFinishInputView");
        super.onFinishInputView(finishingInput);
        IsVisualKeyboardOpen = false;
    }


    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG2, "onStartInput restarting="+restarting+
                " package: " + editorInfo.packageName
                + " editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType)
                +" editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions));
        if(isNotStarted)
            return;
        try {
        super.onStartInput(editorInfo, restarting);
        if(restarting)
            return;
        // Reset session-level prediction bar visibility on new input
        isPackageChangedInsideSingleEvent = false;
        // Обрабатываем переход между приложениями
        if (!editorInfo.packageName.equals(_lastPackageName)) {

            //TODO: Перенести в keyboard_mechanics с использованием PackageHistory
            // Не надо сохранять это каждый раз
            //SetGestureDefaultPointerMode(_lastPackageName, _modeGestureAtViewMode);

            //PackageHistory.add(_lastPackageName);
            _lastPackageName = editorInfo.packageName;
            isPackageChangedInsideSingleEvent = true;
            _currentAppTransparency = GetTransparencyForCurrentApp();

        }

        // Defer heavy work (requestShowSelf IPC, prediction init, action chains)
        // so the main thread can process key events between frames
        mainHandler.post(() -> {
            try {
                onStartInputDeferred(editorInfo);
            } catch (Throwable ex) {
                Log.e(TAG2, "onStartInput deferred exception: " + ex);
            }
        });

        } catch(Throwable ex) {
            Log.e(TAG2, "onStartInput exception: "+ex);
            FileJsonUtils.LogErrorToGui("onStartInput exception: "+ex);
        }
    }

    private void onStartInputDeferred(EditorInfo editorInfo) {
        if(isNotStarted)
            return;

        //Это нужно чтобы показать клаву (перейти в режим редактирования)
        if (pref_show_default_onscreen_keyboard
                && !isInputViewShown()
                && getCurrentInputConnection() != null
                && IsInputMode()
                && Orientation == 1) {
            keyboardView.setOnTouchListener(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                this.requestShowSelf(InputMethodManager.SHOW_IMPLICIT);
            } else {
                this.showWindow(false);
            }
        } else if (!pref_show_default_onscreen_keyboard
                && wordPredictor != null
                && getCurrentInputConnection() != null
                && IsInputMode()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                this.requestShowSelf(InputMethodManager.SHOW_IMPLICIT);
            }
        }

        onStartInputPrediction();

        OnStartInput.Process(null, null);
        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;
        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;
        isPackageChangedInsideSingleEvent = false;
    }

    @Override
    public void onFinishInput() {
        Log.d(TAG2, "onFinishInput ");
        if(isNotStarted)
            return;
        super.onFinishInput();

        try {
            OnFinishInput.Process(null, null);
            if (needUpdateVisualInsideSingleEvent)
                UpdateKeyboardModeVisualization();
            needUpdateVisualInsideSingleEvent = false;
            if (needUpdateGestureNotificationInsideSingleEvent)
                UpdateGestureModeVisualization();
            needUpdateGestureNotificationInsideSingleEvent = false;
            //TODO: Проверить как это работает
            if (_lastPackageName.equals("com.ai10.k12kb")) {
                LoadSettingsAndKeyboards(deviceFullMODEL);
                onFinishInputPredictionSettingsReload();
            }

            //keyboardView.setShowFlag(preference_show_flag);
            //if (currentSoftKeyboard.getHeight() != GetSwipeKeyboardHeight())
                //currentSoftKeyboard = new Keyboard(this, R.xml.space_empty, GetSwipeKeyboardHeight());
            //    currentSoftKeyboard = new Keyboard(this, R.xml.space_empty);

            onFinishInputPredictionCleanup();

            if (suggestionBar != null) {
                //This makes VKBD Hiding Slower
                //suggestionBar.clear();
                //setCandidatesViewShown(false);
            }

            // requestHideSelf removed: system handles hide on input finish,
            // extra call creates redundant IPC
        } catch(Throwable ex) {
            Log.e(TAG2, "onFinishInput exception: "+ex);
            FileJsonUtils.LogErrorToGui("onFinishInput exception: "+ex);
            //throw new RuntimeException("onFinishInput exception: ", ex);
        }


    }

    private boolean isPackageChangedInsideSingleEvent;
    @Override
    public boolean MetaIsPackageChanged() {
        return isPackageChangedInsideSingleEvent;
    }



    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(isNotStarted)
            return false;
        keyboardView.hidePopup(false);
        Log.v(TAG2, "onKeyDown " + event);

        if(
            !IsInputMode()
            && _currentAppTransparency
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null
            && !_digitsHackActive
            && !IsNavMode())  {
            Log.d(TAG2, "App transparency mode");
            return false;
        }

        //region Режим "Навигационные клавиши"

        /**
         * Особенность в том, что NAV режим не работал для движения в режиме курсора по BB Launcher (чтобы работал flag должен быть 0)
         * Но в режиме ввода текста чтобы фокус не выбивало при NAV-навигации в Telegram flag должен быть KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
         */

        if (IsNavMode() && !IsNavModeExtraKeyTransparency(keyCode)) {

            ProcessCoreOnKeyDown(keyCode, event, navKeyProcessorsMap);
            return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;

        }

        //endregion

        needUpdateVisualInsideSingleEvent = false;
        _isKeyTransparencyInsideUpDownEvent = false;
        boolean processed = ProcessCoreOnKeyDown(keyCode, event, mainModeKeyProcessorsMap);
        if(_isKeyTransparencyInsideUpDownEvent)
            return false;
        if (!processed)
            return false;


        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        boolean isShiftSelection = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT;// && event.getRepeatCount() == 0);
        return !isShiftSelection;
    }

    private boolean IsNavModeExtraKeyTransparency(int keyCode) {
        return navSwitcherKeyCode == keyCode || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT;
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG2, "onKeyUp " + event);
        if(isNotStarted)
            return false;
        //region Блок навигационной клавиатуры

        if (IsNavMode() && !IsNavModeExtraKeyTransparency(keyCode)) {
            ProcessCoreOnKeyUp(keyCode, event, navKeyProcessorsMap);
            return true;
        }
        //endregion

        if(
            !IsInputMode()
            && _currentAppTransparency
            && FindAtKeyDownList(keyCode, event.getScanCode()) == null
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null)  {

            Log.d(TAG2, "App transparency mode");
            return false;
        }

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessCoreOnKeyUp(keyCode, event, mainModeKeyProcessorsMap);

        if (_isKeyTransparencyInsideUpDownEvent) {
            _isKeyTransparencyInsideUpDownEvent = false;
            return false;
        }

        if (!processed)
            return false;

        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;

        //Log.d(TAG2, "KEY_DOWN_LIST: "+KeyDownList1.size());

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        if(keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            getCurrentInputConnection().clearMetaKeyStates(KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);
        }
        return true;
    }




    //TODO: Вынести в XML/JSON
    public int getNavigationCode(int keyCode) {
        int keyEventCode = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_Q: //Q
                keyEventCode = KeyEvent.KEYCODE_ESCAPE; //ESC
                break;
            case KeyEvent.KEYCODE_W: //W (1)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F1; //F1
                    break;
                }
            case KeyEvent.KEYCODE_Y: //Y
                keyEventCode = KeyEvent.KEYCODE_MOVE_HOME; //Home
                break;
            case KeyEvent.KEYCODE_E: //E (2)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F2; //F2
                    break;
                }
            case KeyEvent.KEYCODE_U: //U
                keyEventCode = KeyEvent.KEYCODE_DPAD_UP; //Arrow Up
                break;
            case KeyEvent.KEYCODE_R: //R (3)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F3; //F3
                    break;
                }
            case KeyEvent.KEYCODE_I: //I
                keyEventCode = KeyEvent.KEYCODE_MOVE_END; //END
                break;
            case KeyEvent.KEYCODE_T: //T
            case KeyEvent.KEYCODE_O: //O
                keyEventCode = KeyEvent.KEYCODE_PAGE_UP; //Page Up
                break;
            case KeyEvent.KEYCODE_P: //P
                keyEventCode = -7; //FN
                break;

            case KeyEvent.KEYCODE_A: //A
                keyEventCode = KeyEvent.KEYCODE_TAB; //Tab
                break;
            case KeyEvent.KEYCODE_S: //S
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F4; //F4
                    break;
                }
            case KeyEvent.KEYCODE_H: //H
                keyEventCode = KeyEvent.KEYCODE_DPAD_LEFT; //Arrow Left
                break;
            case KeyEvent.KEYCODE_D: //D
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F5; //F5
                    break;
                }
            case KeyEvent.KEYCODE_J: //J
                keyEventCode = KeyEvent.KEYCODE_DPAD_DOWN; //Arrow Down
                break;
            case KeyEvent.KEYCODE_F: //F
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = KeyEvent.KEYCODE_F6; //F6
                    break;
                }
            case KeyEvent.KEYCODE_K: //K
                keyEventCode = KeyEvent.KEYCODE_DPAD_RIGHT; //Arrow Right
                break;
            case KeyEvent.KEYCODE_G: //G
            case KeyEvent.KEYCODE_L: //L
                keyEventCode = KeyEvent.KEYCODE_PAGE_DOWN; //Page down
                break;

            case KeyEvent.KEYCODE_Z: //Z (7)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = KeyEvent.KEYCODE_F7; //F7
                break;
            case KeyEvent.KEYCODE_X: //X (8)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = KeyEvent.KEYCODE_F8; //F8
                break;
            case KeyEvent.KEYCODE_C: //C (9)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = KeyEvent.KEYCODE_F9; //F9
                break;

            case KeyEvent.KEYCODE_0: //0
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = KeyEvent.KEYCODE_F10; //F10
                break;

            default:
        }

        return keyEventCode;
    }

    //private int lastOrientation = 0;
    private int lastVisibility = -1;

    private int Orientation = 1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if(isNotStarted)
            return;
        if (newConfig.orientation == 2) {
            Orientation = 2;
            if (keyboardView.getVisibility() == View.VISIBLE) {
                lastVisibility = View.VISIBLE;
                keyboardView.setVisibility(View.GONE);
            }
        } else if (newConfig.orientation == 1) {
            Orientation = 1;
            if (lastVisibility == View.VISIBLE) {
                keyboardView.setVisibility(View.VISIBLE);
            } else
                keyboardView.setVisibility(View.GONE);
        }

    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
                                  final int newSelStart, final int newSelEnd,
                                  final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);
        if (true) {
            Log.i(TAG2, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }
        CurrentSelectionStart = newSelStart;
        CurrentSelectionEnd = newSelEnd;
        ProcessOnCursorMovement(getCurrentInputEditorInfo());

        // Update word predictor when cursor moves (not just from typing)
        onUpdateSelectionPrediction(newSelStart, newSelEnd);
    }




    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG2, "onPress");
        //onKey(primaryCode, null);
    }

    @Override
    public void onRelease(int primaryCode) {
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        Log.d(TAG2, "onKeyLongPress " + event);
        return false;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        Log.d(TAG2, "onKey " + primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if (IsNavMode()) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_UP, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case 0: //SPACE
                case 32: //SPACE
                    break;

                case 111: //ESC
                case 61:  //TAB
                    keyDownUpKeepTouch2(primaryCode, inputConnection, 0);
                    break;
                case 122: //HOME
                case 123: //END
                case 92:  //Page UP
                case 93:  //Page DOWN
                    keyDownUpKeepTouch2(primaryCode, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case -7:  //Switch F1-F12
                    keyboardStateFixed_FnSymbolOnScreenKeyboard = !keyboardStateFixed_FnSymbolOnScreenKeyboard;
                    UpdateKeyboardModeVisualization();
                    keyboardView.setFnNavMode(keyboardStateFixed_FnSymbolOnScreenKeyboard);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_ENTER, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                default:

            }
        } else if (keyboardView.isEmojiMode()) {
            switch (primaryCode) {
                case -7:
                    if (keyboardView.getEmojiPage() < EmojiData.getPageCount() - 1) {
                        Keyboard ek = new Keyboard(this, keyboardLayoutManager.GetCurrentKeyboardLayout().SymXmlId);
                        keyboardView.setKeyboard(ek);
                        keyboardView.prepareEmojiLayer(keyboardView.getEmojiPage() + 1);
                    } else {
                        UpdateKeyboardModeVisualization();
                    }
                    break;
                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case Keyboard.KEYCODE_DONE:
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_ENTER, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                default:
                    String emoji = keyboardView.getEmojiForKey(primaryCode);
                    if (emoji != null) {
                        inputConnection.commitText(emoji, 1);
                    }
            }
        } else {
            switch (primaryCode) {
                //Хак чтобы не ставился пробел после свайпа по свайп-анели
                case 0: //SPACE
                case 32: //SPACE
                    break;
                case 21: //LEFT
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case -7:
                    if (keyboardStateFixed_SymbolOnScreenKeyboard) {
                        Keyboard ek = new Keyboard(this, keyboardLayoutManager.GetCurrentKeyboardLayout().SymXmlId);
                        keyboardView.setKeyboard(ek);
                        keyboardView.prepareEmojiLayer(0);
                    }
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpKeepTouch2(KeyEvent.KEYCODE_ENTER, inputConnection, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                default:
                    char code = (char) primaryCode;
                    inputConnection.commitText(String.valueOf(code), 1);
            }
        }

    }

    @Override
    public void onText(CharSequence text) {
        Log.d(TAG2, "onText: " + text);
    }

    @Override
    public void swipeLeft() {
        LogKeyboardTest("swipeLeft");
    }

    @Override
    public void swipeRight() {
        LogKeyboardTest("swipeRight");
    }

    @Override
    public void swipeDown() {
        LogKeyboardTest("swipeDown");

    }

    @Override
    public void swipeUp() {
        LogKeyboardTest("swipeUp");
    }

    float lastGestureX = 0;

    void printSamples(MotionEvent ev) {
        final int historySize = ev.getHistorySize();
        final int pointerCount = ev.getPointerCount();
        for (int h = 0; h < historySize; h++) {
            LogKeyboardTest(String.format("At time %d:", ev.getHistoricalEventTime(h)));
            for (int p = 0; p < pointerCount; p++) {
                LogKeyboardTest(String.format("  pointer HISTORY %d: (%f,%f)",
                        ev.getPointerId(p), ev.getHistoricalX(p, h), ev.getHistoricalY(p, h)));
            }
        }
        LogKeyboardTest(String.format("At time %d:", ev.getEventTime()));
        for (int p = 0; p < pointerCount; p++) {
            LogKeyboardTest(String.format("  pointer %d: RAW (%f,%f)",
                    ev.getPointerId(p), ev.getRawX(), ev.getRawY()));
            MotionEvent.PointerCoords pc =  new MotionEvent.PointerCoords();
            ev.getPointerCoords(p, pc);
            LogKeyboardTest(String.format("  pointer %d: PC (%f,%f)",
                    ev.getPointerId(p), pc.x, pc.y));

            LogKeyboardTest(String.format("  pointer %d: AXIS (%f,%f)",
                    ev.getPointerId(p), ev.getAxisValue(MotionEvent.AXIS_VSCROLL), ev.getAxisValue(MotionEvent.AXIS_HSCROLL)));
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        //LogKeyboardTest("onTouch: "+motionEvent.getAction());
        //printSamples(motionEvent);


        if(
        keyboardStateFixed_FnSymbolOnScreenKeyboard
        || keyboardStateFixed_NavModeAndKeyboard)
            return false;

        //Log.d(TAG, "onTouch "+motionEvent);
        InputConnection inputConnection = getCurrentInputConnection();
        int motionEventAction = motionEvent.getAction();
        if (!keyboardStateFixed_SymbolOnScreenKeyboard) {
            if (motionEventAction == MotionEvent.ACTION_DOWN) lastGestureX = motionEvent.getX();
            if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX + (36 - pref_gesture_motion_sensitivity) < motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorRightSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            } else if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX - (36 - pref_gesture_motion_sensitivity) > motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorLeftSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        } else {
            //alt-popup-keyboard
            if (motionEventAction == MotionEvent.ACTION_MOVE)
                keyboardView.coordsToIndexKey(motionEvent.getX());
            if (motionEventAction == MotionEvent.ACTION_UP) keyboardView.hidePopup(true);
        }
        return false;
    }

     @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
         //Log.d(TAG2, "onGenericMotionEvent(): " + motionEvent);

        boolean ret = ProcessGestureAtMotionEvent(motionEvent);
         if(needUpdateGestureNotificationInsideSingleEvent)
             UpdateGestureModeVisualization();
         needUpdateGestureNotificationInsideSingleEvent = false;
         return ret;
    }


    public boolean IsSym2Mode() {
        return MetaIsAltMode() && IsShiftModeOrSymPadAltShiftMode();
    }

    private boolean IsShiftModeOrSymPadAltShiftMode() {
        return MetaIsShiftPressed() || MetaIsSymPadAltShiftMode();
    }



    @Override
    protected void ChangeLanguage() {
        keyboardLayoutManager.ChangeLayout();
        if(pref_show_toast) {
            if(mainToast != null) {
                mainToast.cancel();
            }
            mainToast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName, Toast.LENGTH_SHORT);
            mainToast.show();
        }
        reloadDictionaryForCurrentLanguage();
        updateTranslationLanguages();
    }

    @Override
    protected void ChangeLanguageBack() {
        keyboardLayoutManager.ChangeLayoutBack();
        if(pref_show_toast) {
            if(mainToast != null) {
                mainToast.cancel();
            }
            mainToast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName, Toast.LENGTH_SHORT);
            mainToast.show();
        }
        reloadDictionaryForCurrentLanguage();
        updateTranslationLanguages();
    }

    protected void UpdateKeyboardVisibilityOnPrefChange() {
        if (pref_show_default_onscreen_keyboard) {
            UpdateKeyboardModeVisualization(true);
            ShowKeyboard();
        } else {
            HideKeyboard();
        }
    }


    //region VISUAL UPDATE


    protected void UpdateGestureModeVisualization() {
        UpdateGestureModeVisualization(IsInputMode());
    }

    //@Override
    protected void UpdateGestureModeVisualization(boolean isInput) {
        boolean changed = false;
        if(IsNoGesturesMode()) {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
        }
        else if(isInput) {
            if(_modeGestureScrollAtInputMode) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_LIST);
            } else if (mode_gesture_cursor_at_input_mode) {
                if (mode_gesture_cursor_plus_up_down) {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input_up_down);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_UP_DOWN);
                } else {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT);
                }
            } else {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
            }
        } else { //!isIsInput
            if(_modeGestureAtViewMode == GestureAtViewMode.Disabled) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
            } else if (_modeGestureAtViewMode == GestureAtViewMode.Pointer) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_pointer);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW_POINTER);
            } else {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW);
            }
        }

        if (changed)
            notificationProcessor.UpdateNotificationGestureMode();
    }


    private boolean setSmallIcon2(int resId) {
        return notificationProcessor.SetSmallIconGestureMode(resId);
    }


    private void HideSwipePanelOnHidePreferenceAndVisibleState() {
        if (!pref_show_default_onscreen_keyboard) {
            if (wordPredictor != null && IsInputMode()) {
                // Hide swype-pad but keep IME window for prediction bar
                keyboardView.setOnTouchListener(null);
                if (keyboardView.getVisibility() == View.VISIBLE) {
                    keyboardView.setVisibility(View.GONE);
                }
                if (!predictionBarHiddenByDefault || predictionBarVisibleThisSession) {
                    setSuggestionBarShown(true);
                }
            } else {
                HideKeyboard();
            }
        }
    }

    private boolean hideRequested = false;

    @SuppressLint("ClickableViewAccessibility")
    protected void HideKeyboard() {
        keyboardView.setOnTouchListener(null);
        if (keyboardView.getVisibility() == View.VISIBLE) {
            keyboardView.setVisibility(View.GONE);
        }
        if (!hideRequested) {
            hideRequested = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                this.requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS);
            } else {
                this.hideWindow();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected void ShowKeyboard() {
        hideRequested = false;
        keyboardView.setOnTouchListener(this);
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
        if (!keyboardView.isShown())
            this.showWindow(true);
    }

    @Override
    protected void UpdateKeyboardModeVisualization() {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }

    @Override
    protected void UpdateKeyboardModeVisualization(boolean updateSwipePanelData) {
        Log.d(TAG2, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode=" + metaFixedModeFirstLetterUpper + " doubleShiftCapsMode=" + metaFixedModeCapslock + " doubleAltPressAllSymbolsAlted=" + metaFixedModeAllSymbolsAlt + " altPressSingleSymbolAltedMode=" + metaFixedModeFirstSymbolAlt);
        KeyboardLayout keyboardLayout = keyboardLayoutManager.GetCurrentKeyboardLayout();

        String languageOnScreenNaming = keyboardLayout.KeyboardName;
        boolean changed;
        boolean needUsefulKeyboard = false;
        if(_digitsHackActive) {
            changed = UpdateNotification(digitsPadIconRes, TITLE_DIGITS_TEXT);
        } else if (IsNavMode()) {
            if (!keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                changed = UpdateNotification(navIconRes, TITLE_NAV_TEXT);
            } else {
                changed = UpdateNotification(navFnIconRes, TITLE_NAV_FV_TEXT);
            }
            if( (pref_nav_pad_on_hold && keyboardStateHolding_NavModeAndKeyboard)
                    || keyboardStateFixed_NavModeAndKeyboard) {
                keyboardView.setKeyboard(keyboardNavigation);
                keyboardView.prepareNavigationLayer();
                needUsefulKeyboard = true;
            }
        } else if (keyboardStateFixed_SymbolOnScreenKeyboard) {

            if (IsSym2Mode()) {
                changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM_TEXT);
            }

            //Keyboard k1 = keyboardLayoutManager.GetSymKeyboard(symPadAltShift);
            Keyboard k1 = new Keyboard(this, keyboardLayoutManager.GetCurrentKeyboardLayout().SymXmlId);
            keyboardView.setKeyboard(k1);
            keyboardView.prepareSymAltLayer(keyboardLayoutManager.GetCurrentKeyboardLayout(), keyboardLayoutManager.GetDefaultKeyboardLayout(), symPadAltShift);

            needUsefulKeyboard = true;

        } else if (metaFixedModeAllSymbolsAlt || metaHoldAlt) {
            if (IsSym2Mode()) {
                if(metaHoldAlt)
                    changed = UpdateNotification(SymHoldIconRes, TITLE_SYM2_TEXT);
                else
                    changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else if (metaHoldAlt){
                changed = UpdateNotification(AltHoldIconRes, TITLE_SYM_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM2_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData, keyboardLayout.Resources.FlagResId, false);
        } else if (metaFixedModeFirstSymbolAlt) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(SymOneIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltOneIconRes, TITLE_SYM_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData, keyboardLayout.Resources.FlagResId, true);
        } else if (metaFixedModeCapslock || metaHoldShift) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftMode(updateSwipePanelData, languageOnScreenNaming, keyboardLayout.Resources.FlagResId);
        } else if (metaFixedModeFirstLetterUpper) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftOneMode(updateSwipePanelData, languageOnScreenNaming, keyboardLayout.Resources.FlagResId);
        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLowercaseRes, languageOnScreenNaming);
            UpdateKeyboardViewLetterMode(updateSwipePanelData, languageOnScreenNaming, keyboardLayout.Resources.FlagResId);
        }

        if (needUsefulKeyboard)
            if (IsInputMode())
                ShowKeyboard();
            else
                HideKeyboard();
        else if(pref_show_default_onscreen_keyboard) {
            if (IsInputMode())
                ShowKeyboard();
            else
                HideKeyboard();
        } else
            HideSwipePanelOnHidePreferenceAndVisibleState();

        if (changed && !pref_system_icon_no_notification_text)
            notificationProcessor.UpdateNotificationLayoutMode();
    }

    private void UpdateKeyboardViewShiftOneMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming, flagResId);
            keyboardView.setShiftFirst();
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewLetterMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.notShift();
            keyboardView.setLang(languageOnScreenNaming, flagResId);
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewShiftMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming, flagResId);
            keyboardView.setShiftAll();
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewAltMode(boolean updateSwipePanelData, int flagResId, boolean single) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            if (IsSym2Mode()) {
                keyboardView.setLang(TITLE_SYM2_TEXT, flagResId);
            } else {
                keyboardView.setLang(TITLE_SYM_TEXT, flagResId);
            }
            keyboardView.setAltMode(single);
        }
    }

    private boolean UpdateNotification(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes, String notificationText) {
        if(!pref_system_icon_no_notification_text) {
            boolean changed = notificationProcessor.SetSmallIconLayout(iconRes.MipmapResId);
            changed |= notificationProcessor.SetContentTitleLayout(notificationText);
            return changed;
        }
        this.showStatusIcon(iconRes.DrawableResId);
        return true;
    }

    //endregion

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {
        Log.d(TAG2, "onGetSuggestions");
    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        Log.d(TAG2, "onGetSentenceSuggestions");
    }


    private boolean IsNavKeyCode(int keyCode) {
        return FindAtKeyActionOptionList(keyCode, -1, navKeyProcessorsMap) != null;
    }

    private boolean IsViewModeKeyCode(int keyCode, int meta) {
        for (int keyCode1 : ViewModeExcludeKeyCodes) {
            if(keyCode == keyCode1)
                return false;
        }
        return true;
    }

    private boolean IsViewModeKeyCodeOld(int keyCode, int meta) {
        if (keyCode == KeyEvent.KEYCODE_0 && (meta & KeyEvent.META_ALT_ON) == 0) return false;
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT) return false;
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return false;
        if (keyCode == KeyEvent.KEYCODE_FUNCTION) return false;
        if(pref_manage_call) {
            if (keyCode == KeyEvent.KEYCODE_SPACE) return false;
            if (keyCode == KeyEvent.KEYCODE_SYM) return false;
        } else {
            if (keyCode == KeyEvent.KEYCODE_SPACE && IsShiftMeta(meta)) return false;
        }
        return true;
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    protected boolean ProcessOnCursorMovement(EditorInfo editorInfo)
    {
        //AnyButtonPressOnHoldPlusButtonTime = SystemClock.uptimeMillis();
        return DetermineFirstBigCharStateAndUpdateVisualization1(editorInfo);
    }

    protected boolean DetermineFirstBigCharStateAndUpdateVisualization1(EditorInfo editorInfo)
    {
        boolean needUpdateVisual = DetermineForceFirstUpper(editorInfo);
        if(needUpdateVisual) {
            UpdateKeyboardModeVisualization();
            return true;
        }
        return false;

    }



    private void playClick(int i) {
/* Пока запиливаем звук, он ругается в дебаге
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch(i){
            case 32:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;

            case Keyboard.KEYCODE_DONE:
            case 10:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;

            case Keyboard.KEYCODE_DELETE:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;

            default:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
*/
    }


    //region LOAD SETTINGS & KEYBOARDS




    private void LoadSettingsAndKeyboards(String deviceFullMODEL) throws Exception {

        pref_gesture_motion_sensitivity = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);
        pref_show_toast = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_2_SHOW_TOAST);
        pref_alt_space = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_3_ALT_SPACE);
        preference_show_flag = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_4_FLAG);
        pref_long_press_key_alt_symbol = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_5_LONG_PRESS_ALT);
        pref_manage_call = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_6_MANAGE_CALL);
        pref_swipe_panel_height = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);
        pref_show_default_onscreen_keyboard = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);
        pref_gesture_mode_at_view_mode = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE);
        SetGestureModeAtViewModeDefault();
        pref_system_icon_no_notification_text = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);
        pref_vibrate_on_key_down = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN);
        pref_ensure_entered_text = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT);
        pref_pointer_mode_rect_and_autofocus = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_13_POINTER_MODE_RECT);
        pref_pointer_mode_rect_color = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR);
        pref_nav_pad_on_hold = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_14_NAV_PAD_ON_HOLD);
        pref_auto_capitalization = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_28_AUTO_CAPITALIZATION);
        k12KbSettings.CheckSettingOrSetDefault(k12KbSettings.APP_PREFERENCES_29_TRANSPARENCY_MODE, true);
        pref_transparency_mode = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_29_TRANSPARENCY_MODE);
        _currentAppTransparency = pref_transparency_mode;

        keyboard_mechanics_res = null;

        ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts = new ArrayList<>();

        ArrayList<KeyboardLayout.KeyboardLayoutOptions> allLayouts;
        allLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());


        //for each keyboard layout in active layouts find in settings and if setting is true then set keyboard layout to active
        for (KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : allLayouts) {
            if (keyboardLayoutOptions.DeviceModelRegexp != null && keyboardLayoutOptions.DeviceModelRegexp != "") {
                boolean isDevice = IsCurrentDevice(deviceFullMODEL, keyboardLayoutOptions);
                if (!isDevice)
                    continue;
            }
            k12KbSettings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), k12KbSettings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);

            if (keyboard_mechanics_res == null) {
                if (keyboardLayoutOptions.CustomKeyboardMechanics != null && !keyboardLayoutOptions.CustomKeyboardMechanics.isEmpty()) {
                    keyboard_mechanics_res = keyboardLayoutOptions.CustomKeyboardMechanics;
                } else {
                    keyboard_mechanics_res = RES_KEYBOARD_MECHANICS_DEFAULT;
                }
            }

            boolean enabled = k12KbSettings.GetBooleanValue(keyboardLayoutOptions.getPreferenceName());
            if (enabled) {
                activeLayouts.add(keyboardLayoutOptions);
            }
        }

        if (keyboard_mechanics_res == null) {
            keyboard_mechanics_res = RES_KEYBOARD_MECHANICS_DEFAULT;
        }

        if(activeLayouts.isEmpty()) {
            KeyboardLayout.KeyboardLayoutOptions defLayout = allLayouts.get(0);
            k12KbSettings.SetBooleanValue(defLayout.getPreferenceName(), true);
            activeLayouts.add(defLayout);
        }
        keyboardLayoutManager.Initialize(activeLayouts, getResources(), getApplicationContext());

    }



    private void SetGestureModeAtViewModeDefault() {
        switch (pref_gesture_mode_at_view_mode) {
            case 0:
                _modeGestureAtViewModeDisabledPermanently = true;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Disabled;
                break;
            case 1:
                _modeGestureAtViewModeDisabledPermanently = false;
                _modeGestureAtViewModePointerAfterEnable = false;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Disabled;
                break;
            case 2:
                _modeGestureAtViewModeDisabledPermanently = false;
                _modeGestureAtViewModePointerAfterEnable = true;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Disabled;
                break;
            case 3:
                _modeGestureAtViewModeDisabledPermanently = false;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Scroll;
                break;
            case 4:
                _modeGestureAtViewModeDisabledPermanently = false;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Pointer;
                break;
            default:
                _modeGestureAtViewModeDisabledPermanently = false;
                GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Scroll;
        }
    }


    //endregion

    boolean IsBitMaskContaining(int bitmask, int bits) {
        return (bitmask & bits) > 0;
    }




    private void ProcessImeOptions() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if(editorInfo != null && editorInfo.inputType != 0 && editorInfo.imeOptions != 0) {
            if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_MASK_ACTION)) {
                if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_ACTION_DONE)) {
                    //TODO: Доделать
                }
            }
        }
    }

    private boolean IsCtrlPressed(KeyPressData keyPressData) {
        return (keyPressData.MetaBase & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) > 0;
    }

    @Override
    protected boolean IsNoGesturesMode() {
        return IsNavMode();
    }

    @Override
    protected boolean IsGestureModeAtViewEnabled() {
        return _modeGestureAtViewMode != GestureAtViewMode.Disabled;
    }

    //endregion

}
