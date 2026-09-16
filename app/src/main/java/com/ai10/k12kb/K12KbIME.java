package com.ai10.k12kb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.Uri;
import com.ai10.k12kb.prediction.WordDictionary;
import com.ai10.k12kb.prediction.LanguagePacks;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
            AltOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_alt_one);
            AltAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_alt_all);
            AltHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_alt);
            SymOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_sym_one);
            SymAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_sym_all);
            SymHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_sym);

            navIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_nav);
            navFnIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_nav_fn);
            digitsPadIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(0, R.drawable.ic_kb_digits);

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

            STEP = "WatchLanguagePacks";
            Log.i(TAG2, "onCreate STEP: " + STEP);
            StartWatchingLanguagePacks();

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
                .setIcon(Icon.createWithResource(c, R.mipmap.ic_launcher))
                .setIntents(
                        new Intent[]{
                                new Intent(IntentQuickSettings.ACTION).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                .setRank(1)
                .build();

        ShortcutInfo dsNotifications = new ShortcutInfo.Builder(c, "shct_id_Notifications")
                .setShortLabel("Show notifications")
                .setIcon(Icon.createWithResource(c, R.mipmap.ic_launcher))
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
        StopWatchingLanguagePacks();
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


    /**
     * Так же, как это делает клавиатура AOSP (LatinIME): она не переопределяет
     * этот метод и полностью полагается на штатную логику
     * InputMethodService — решение зависит от конфигурации устройства
     * (есть ли аппаратная клавиатура и включена ли системная настройка
     * "показывать экранную клавиатуру при физической"), но НИКОГДА не от
     * inputType текущего поля.
     *
     * K12KB же ориентируется на аппаратную клавиатуру, поэтому штатный ответ
     * при видимой физической клавиатуре — false — ему не подходит: панель
     * предсказаний и свайп-панель нужны и в этом случае. Возвращаем true, когда
     * к нам привязан редактор, и false, когда редактора нет — тогда система
     * показывает нас по запросу приложения (в Termux это клик по экрану) и не
     * показывает в режиме просмотра.
     */
    @Override
    public boolean onEvaluateInputViewShown() {
        if (IsInputMode())
            return true;
        return super.onEvaluateInputViewShown();
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
        RunAfterInputViewHidden();

        // Приложение убрало окно клавиатуры — приводим свою сторону в то же
        // состояние. Раньше мы не делали ничего: keyboardView оставался VISIBLE,
        // hideRequested — false, окно мы не отпускали. Телеграм в это время
        // просит скрыть клавиатуру и закрыть сессию, но пока окно держится,
        // закрыть её не может: onFinishInput приходил через полторы секунды, а
        // состояние оставалось рассогласованным — isInputViewShown() и isShown()
        // отдавали true при пустом touchableRegion, и на следующем входе в чат
        // ShowKeyboard() уже ничего не делал, панель не появлялась.
        HideKeyboard();
    }


    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG2, "onStartInput restarting="+restarting+
                " package: " + editorInfo.packageName
                + " editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType)
                +" editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions)
                +" editorInfo.fieldId: "+editorInfo.fieldId);
        if(isNotStarted)
            return;
        // Привязка новая — признак завершения ввода снимаем.
        SetInputFinishing(false);
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
        // Признак — привязанный редактор, а не inputType > 0: терминалы (Termux)
        // ставят TYPE_NULL, и по inputType они выглядели как "поля нет".
        // На isInputViewShown() опираться нельзя: он отдаёт состояние самого
        // IMS, а оно расходится с системным. В Телеграме на втором входе в чат
        // видно mIsInputViewShown=true при mInputShown=false и пустом
        // touchableRegion — окно формально «показано», занимает ноль пикселей, и
        // просить показ было некому: ветка с этим условием не выполнялась, а
        // ShowKeyboard пропускал showWindow(true), потому что isShown() тоже
        // отдавал true. Панель не появлялась до тапа по полю.
        //
        // Повторный запрос, когда окно и правда видно, системе ничего не стоит.
        if (pref_show_default_onscreen_keyboard
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
                && IsInputMode()
                && !(predictionBarHiddenByDefault && !predictionBarVisibleThisSession)) {
            // Окно IME открывается только ради панели предсказаний, поэтому
            // просить показ, когда панель скрыта, незачем.
            //
            // Это не оптимизация, а исправление: запрос уходил на каждый
            // onStartInput, в том числе когда приложение только что попросило
            // клавиатуру убрать. Телеграм при выходе из диалога шлёт
            // HIDE_SOFT_INPUT, затем HIDE_SOFT_INPUT_FROM_VIEW и
            // HIDE_SOFT_INPUT_CLOSE_CURRENT_SESSION, а наш показ между ними не
            // давал сессии закрыться: onFinishInput не приходил, привязка
            // оставалась, и плагин поиска оставался выключенным по IsInputMode().
            // Без запроса Телеграм закрывает ввод сам, и плагин заряжается.
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
        // До конца разбора этого события режима ввода нет: см. SetInputFinishing.
        SetInputFinishing(true);
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
            // getPackageName(), а не константа: в dev-сборке пакет другой, и
            // возврат из её же настроек не перезагружал бы конфигурацию.
            if (_lastPackageName.equals(getPackageName())) {
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




    /**
     * Режим F-клавиш: включается клавишей FN внутри навигации. Механика читает
     * его как мета-условие, поэтому раскладка F1-F12 задаётся в json, а не в коде.
     */
    @Override
    public boolean MetaIsFnMode() {
        return keyboardStateFixed_FnSymbolOnScreenKeyboard;
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
            ShowToast(keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName);
        }
        // Смена раскладки сама по себе словарь не грузит: он понадобится, только
        // если предсказания сейчас работают. Направление перевода обновляем
        // всегда — это дёшево, а сам словарь перевода грузится по Ctrl+T.
        EnsurePredictionDictionary();
        updateTranslationLanguages();
    }

    @Override
    protected void ChangeLanguageBack() {
        keyboardLayoutManager.ChangeLayoutBack();
        if(pref_show_toast) {
            ShowToast(keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName);
        }
        // Смена раскладки сама по себе словарь не грузит: он понадобится, только
        // если предсказания сейчас работают. Направление перевода обновляем
        // всегда — это дёшево, а сам словарь перевода грузится по Ctrl+T.
        EnsurePredictionDictionary();
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
            // IsInputMode теперь не зависит от inputType: раньше в терминале с
            // TYPE_NULL управление уходило в else и клавиатура прятала сама себя
            // через requestHideSelf, хотя приложение её только что показало.
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

    /**
     * Дело, которое ждёт, пока окно IME действительно уберут.
     *
     * Скрытие панели асинхронно: между запросом и фактическим закрытием в
     * трассе проходило 331 мс (запрос 19.701 — onFinishInputView 20.032), и это
     * анимация WindowManager, а не наша задержка. Ждать её фиксированным
     * таймером нельзя — Back-обход ждал 120 мс и перевыпускал BACK, когда
     * панель была ещё в середине анимации.
     */
    private Runnable afterInputViewHidden = null;

    /**
     * Выполнить дело, когда окно IME будет убрано.
     *
     * Если окна и так нет, ждать нечего — выполняем сразу: скрывать нечего,
     * значит onFinishInputView не придёт и ждать его пришлось бы вечно.
     */
    public void RunWhenInputViewHidden(Runnable action) {
        if (action == null)
            return;
        if (!isInputViewShown()) {
            Log.d(TAG2, "ПАНЕЛЬ: окно уже убрано, выполняем сразу");
            action.run();
            return;
        }
        afterInputViewHidden = action;
    }

    /** Окно убрано — выполнить отложенное дело, если оно есть. */
    private void RunAfterInputViewHidden() {
        Runnable action = afterInputViewHidden;
        if (action == null)
            return;
        afterInputViewHidden = null;
        Log.d(TAG2, "ПАНЕЛЬ: окно убрано, выполняем отложенное дело");
        action.run();
    }

    /** Отложенное дело больше не нужно (сработала подстраховка по таймеру). */
    public void CancelWhenInputViewHidden() {
        afterInputViewHidden = null;
    }

    private boolean hideRequested = false;

    @SuppressLint("ClickableViewAccessibility")
    protected void HideKeyboard() {
        Log.d(TAG2, "ПАНЕЛЬ: HideKeyboard visibility=" + keyboardView.getVisibility()
                + " isShown=" + keyboardView.isShown()
                + " hideRequested=" + hideRequested
                + " inputViewShown=" + isInputViewShown()
                               + " IsInputMode=" + IsInputMode());
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
        Log.d(TAG2, "ПАНЕЛЬ: ShowKeyboard visibility=" + keyboardView.getVisibility()
                + " isShown=" + keyboardView.isShown()
                + " hideRequested=" + hideRequested
                + " inputViewShown=" + isInputViewShown()
                               + " IsInputMode=" + IsInputMode());
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
            UpdateKeyboardViewAltMode(updateSwipePanelData, PanelFlagRes(keyboardLayout.Resources), FlagDrawable(keyboardLayout.Resources), false);
        } else if (metaFixedModeFirstSymbolAlt) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(SymOneIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltOneIconRes, TITLE_SYM_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData, PanelFlagRes(keyboardLayout.Resources), FlagDrawable(keyboardLayout.Resources), true);
        } else if (metaFixedModeCapslock || metaHoldShift) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftMode(updateSwipePanelData, languageOnScreenNaming, PanelFlagRes(keyboardLayout.Resources), FlagDrawable(keyboardLayout.Resources));
        } else if (metaFixedModeFirstLetterUpper) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftOneMode(updateSwipePanelData, languageOnScreenNaming, PanelFlagRes(keyboardLayout.Resources), FlagDrawable(keyboardLayout.Resources));
        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLowercaseRes, languageOnScreenNaming);
            UpdateKeyboardViewLetterMode(updateSwipePanelData, languageOnScreenNaming, PanelFlagRes(keyboardLayout.Resources), FlagDrawable(keyboardLayout.Resources));
        }

        // Видимость экранной клавиатуры не зависит от inputType: терминалы
        // (Termux) ставят TYPE_NULL, и прежний IsInputMode() возвращал false —
        // система окно показывала (по showSoftInput от приложения), а мы тут же
        // прятали его содержимое через HideKeyboard, поэтому клавиатуры не было
        // видно. AOSP так не делает: LatinIME нигде не смотрит на inputType,
        // решая, показывать ли себя.
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

    private void UpdateKeyboardViewShiftOneMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId, Drawable flagDrawable) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming, flagResId, flagDrawable);
            keyboardView.setShiftFirst();
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewLetterMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId, Drawable flagDrawable) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.notShift();
            keyboardView.setLang(languageOnScreenNaming, flagResId, flagDrawable);
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewShiftMode(boolean updateSwipePanelData, String languageOnScreenNaming, int flagResId, Drawable flagDrawable) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming, flagResId, flagDrawable);
            keyboardView.setShiftAll();
            keyboardView.setSwipePanelMode();
        }
    }

    private void UpdateKeyboardViewAltMode(boolean updateSwipePanelData, int flagResId, Drawable flagDrawable, boolean single) {
        keyboardView.setKeyboard(keyboardSwipeOnScreen);
        if (updateSwipePanelData) {
            if (IsSym2Mode()) {
                keyboardView.setLang(TITLE_SYM2_TEXT, flagResId, flagDrawable);
            } else {
                keyboardView.setLang(TITLE_SYM_TEXT, flagResId, flagDrawable);
            }
            keyboardView.setAltMode(single);
        }
    }

    /**
     * Значок раскладки: в режиме с текстом — уведомление, иначе значок клавиатуры
     * в статус-баре.
     *
     * Языки из пакетов: showStatusIcon отдаёт системе только номер ресурса, и
     * ищется он в APK клавиатуры — картинку из чужого APK туда не передать.
     * Поэтому значки на все коды языков нарисованы заранее в самой клавиатуре
     * (см. ResolveIcon), и до ветки с пакетом дело доходит лишь для языка, кода
     * которого мы не знаем; там значок показывает уведомление — оно умеет
     * произвольную картинку.
     */
    /** Уведомление со значком из пакета отправлено нами и ждёт снятия. */
    private boolean packIconNotificationShown = false;

    private boolean UpdateNotification(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes, String notificationText) {
        boolean fromPack = iconRes.PackageName != null;
        // Номер ресурса из чужого APK система ищет в APK клавиатуры: отдавать
        // его нельзя ни showStatusIcon, ни уведомлению — покажется чужая
        // картинка или не покажется ничего.
        int ownIcon = fromPack ? 0 : NotificationIconId(iconRes);

        if (!pref_system_icon_no_notification_text || fromPack) {
            Icon packIcon = fromPack ? PackIcon(iconRes) : null;
            if (fromPack && packIcon == null) {
                // Картинку из пакета не достали — уведомление со значком 0
                // система отвергает исключением, поэтому просто ничего не
                // показываем до следующего обновления вида.
                if (packIconNotificationShown) {
                    packIconNotificationShown = false;
                    notificationProcessor.CancelLayout();
                }
                Log.w(TAG2, "Значок раскладки из пакета недоступен, показывать нечего");
                return false;
            }
            boolean changed = fromPack
                    ? notificationProcessor.SetSmallIconBitmapLayout(
                            iconRes.PackageName + ":" + NotificationIconId(iconRes), packIcon)
                    : notificationProcessor.SetSmallIconLayout(ownIcon);
            changed |= notificationProcessor.SetContentTitleLayout(notificationText);
            if (fromPack) {
                // В режиме системного значка уведомление снаружи не публикуется
                // (см. условие в UpdateKeyboardModeVisualization), поэтому здесь
                // отправляем его сами, иначе значок остался бы только в билдере.
                this.hideStatusIcon();
                if (changed)
                    notificationProcessor.UpdateNotificationLayoutMode();
                packIconNotificationShown = pref_system_icon_no_notification_text;
            }
            return changed;
        }
        if (packIconNotificationShown) {
            // Возврат к своему значку из раскладки пакета: уведомление, которое
            // мы отправили сами, иначе осталось бы висеть навсегда.
            packIconNotificationShown = false;
            notificationProcessor.CancelLayout();
        }
        if (ownIcon == 0) {
            Log.w(TAG2, "У раскладки нет своего значка, слот статус-бара не трогаем");
            return false;
        }
        this.showStatusIcon(iconRes.DrawableResId);
        return true;
    }

    /**
     * Ресурс для уведомления: mipmap, если он есть, иначе drawable. Общие значки
     * языков нарисованы только в drawable — держать их во второй плотности
     * значило бы возить в APK лишние сотни файлов.
     */
    private int NotificationIconId(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes) {
        return iconRes.MipmapResId != 0 ? iconRes.MipmapResId : iconRes.DrawableResId;
    }

    /**
     * Значок языка из APK пакета — готовой картинкой, а не ссылкой на ресурс.
     *
     * Результат запоминается: вид клавиатуры обновляется на каждое изменение
     * Shift, Alt и Sym, а тут createPackageContext, чтение ресурсов чужого APK
     * и разбор PNG — на потоке ввода это чувствуется.
     */
    private final HashMap<String, Icon> packIconCache = new HashMap<>();
    private final HashMap<String, Drawable> packFlagCache = new HashMap<>();

    private Icon PackIcon(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes) {
        String key = iconRes.PackageName + ":" + NotificationIconId(iconRes);
        synchronized (packIconCache) {
            if (packIconCache.containsKey(key))
                return packIconCache.get(key);
        }
        Icon icon = LoadPackIcon(iconRes);
        if (icon != null) {
            // Неудачу не запоминаем: чаще всего она случается ровно во время
            // установки пакета, и запомненный null означал бы значок, который
            // уже не появится до перезапуска.
            synchronized (packIconCache) {
                packIconCache.put(key, icon);
            }
        }
        return icon;
    }

    private Icon LoadPackIcon(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes) {
        try {
            Context pc = createPackageContext(iconRes.PackageName, Context.CONTEXT_IGNORE_SECURITY);
            int resId = NotificationIconId(iconRes);
            if (resId == 0)
                return null;
            Drawable d = pc.getResources().getDrawable(resId, pc.getTheme());
            if (d instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
                if (bitmap != null)
                    return Icon.createWithBitmap(bitmap);
            }
            int w = Math.max(1, d.getIntrinsicWidth());
            int h = Math.max(1, d.getIntrinsicHeight());
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(bitmap));
            return Icon.createWithBitmap(bitmap);
        } catch (Throwable ex) {
            Log.w(TAG2, "Значок из пакета " + iconRes.PackageName + " не загружен: " + ex);
            return null;
        }
    }

    /**
     * Картинка языка для жестовой панели: флаг раскладки, а если его нет —
     * значок языка. Прежде тут подставлялся британский флаг, и любая раскладка
     * без своего флага показывалась английской.
     */
    private int PanelFlagRes(KeyboardLayout.KeyboardLayoutOptions options) {
        if (options == null)
            return 0;
        if (options.FlagResId != 0)
            return options.FlagResId;
        // Значок из пакета сюда не годится: панель ищет ресурс в APK самой
        // клавиатуры, и чужой номер дал бы либо пустоту, либо чужую картинку.
        if (options.IconLowercaseRes != null
                && options.IconLowercaseRes.PackageName == null
                && options.IconLowercaseRes.DrawableResId != 0)
            return options.IconLowercaseRes.DrawableResId;
        return 0;
    }

    /** Флаг раскладки: из своих ресурсов или из APK языкового пакета. */
    private Drawable FlagDrawable(KeyboardLayout.KeyboardLayoutOptions options) {
        if (options == null || options.FlagPackageName == null || options.FlagResId == 0)
            return null;
        String key = options.FlagPackageName + ":" + options.FlagResId;
        synchronized (packFlagCache) {
            if (packFlagCache.containsKey(key))
                return packFlagCache.get(key);
        }
        Drawable d = LoadFlagDrawable(options);
        if (d != null) {
            synchronized (packFlagCache) {
                packFlagCache.put(key, d);
            }
        }
        return d;
    }

    private Drawable LoadFlagDrawable(KeyboardLayout.KeyboardLayoutOptions options) {
        try {
            Context pc = createPackageContext(options.FlagPackageName, Context.CONTEXT_IGNORE_SECURITY);
            return pc.getResources().getDrawable(options.FlagResId, pc.getTheme());
        } catch (Throwable ex) {
            Log.w(TAG2, "Флаг из пакета " + options.FlagPackageName + " не загружен: " + ex);
            return null;
        }
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




    // --- языковые пакеты ---

    private BroadcastReceiver languagePackReceiver;

    /**
     * Перечитать раскладки и словари, когда языковой пакет поставили или сняли.
     *
     * Без этого пакет замечался бы только при следующем запуске клавиатуры:
     * список раскладок и найденные пакеты читаются один раз в onCreate, а
     * служба ввода живёт, пока её не выгрузит система.
     */
    /** Свои пакеты данных: com.ai10.k12kb.lang.<язык>[.idx…]. */
    private static final String LANGUAGE_PACK_PREFIX = "com.ai10.k12kb.lang.";

    private void StartWatchingLanguagePacks() {
        try {
            languagePackReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Uri data = intent.getData();
                    final String pkg = data != null ? data.getSchemeSpecificPart() : null;
                    // Приходит любое обновление любого приложения, а на пакетном
                    // обновлении из магазина — десятки подряд. Перечитывать
                    // языки имеет смысл только на своих пакетах.
                    if (pkg == null || !pkg.startsWith(LANGUAGE_PACK_PREFIX))
                        return;
                    Log.i(TAG2, "Изменился пакет " + pkg + ", перечитываем языки");
                    LanguagePacks.invalidate();
                    ScheduleLanguagePacksReload();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            registerReceiver(languagePackReceiver, filter);
        } catch (Throwable ex) {
            Log.e(TAG2, "Не удалось подписаться на установку пакетов: " + ex);
        }
    }

    /**
     * Перечитать раскладки и словари после изменения состава пакетов.
     *
     * Работа идёт на главном потоке службы: карты обработчиков клавиш и список
     * раскладок читает обработчик нажатий, и менять их из фона — гонка, где
     * нажатие попадает в наполовину заменённый обработчик. Взамен события
     * складываются в одну отложенную перезагрузку: замена пакета присылает
     * REMOVED и ADDED подряд, а магазин — десятки подряд.
     */
    private void ScheduleLanguagePacksReload() {
        Handler h = new Handler(Looper.getMainLooper());
        h.removeCallbacks(languagePacksReload);
        h.postDelayed(languagePacksReload, 700);
    }

    private final Runnable languagePacksReload = new Runnable() {
        public void run() {
            try {
                synchronized (packIconCache) { packIconCache.clear(); }
                synchronized (packFlagCache) { packFlagCache.clear(); }
                LoadSettingsAndKeyboards(deviceFullMODEL);
                LoadKeyProcessingMechanics(K12KbIME.this);
                if (wordPredictor != null)
                    wordPredictor.dropLoadedDictionaries();
                EnsurePredictionDictionary();
                updateTranslationLanguages();
                UpdateKeyboardModeVisualization();
            } catch (Throwable ex) {
                Log.e(TAG2, "Перечитать языки не удалось: " + ex);
            }
        }
    };


    private void StopWatchingLanguagePacks() {
        if (languagePackReceiver == null)
            return;
        try {
            unregisterReceiver(languagePackReceiver);
        } catch (Throwable ignored) {
        }
        languagePackReceiver = null;
    }

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

        // Правило нормализации сменилось — кеши словаря, собранные на устройстве
        // по прежнему, ищут по другим ключам, и ё- и й-слова в них так и не
        // нашлись бы. Выбрасываем их один раз; готовые индексы из пакетов
        // перечитаются сами, а собственный кеш соберётся заново за 5–20 секунд.
        if (k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_32_NORMALIZATION_REVISION)
                != WordDictionary.NORMALIZATION_REVISION) {
            WordDictionary.clearCacheFiles(getApplicationContext());
            k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_32_NORMALIZATION_REVISION,
                    WordDictionary.NORMALIZATION_REVISION);
            Log.w(TAG2, "Кеши словаря сброшены: правило нормализации теперь "
                    + WordDictionary.NORMALIZATION_REVISION);
        }

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
