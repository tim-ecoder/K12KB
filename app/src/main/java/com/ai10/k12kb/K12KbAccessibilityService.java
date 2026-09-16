package com.ai10.k12kb;

import android.content.ComponentName;
import android.database.ContentObserver;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

import static com.ai10.k12kb.FileJsonUtils.GetContext;
import static com.ai10.k12kb.FileJsonUtils.LogErrorToGui;
import static com.ai10.k12kb.K12KbSettings.RES_PLUGIN_DATA;


public class K12KbAccessibilityService extends AccessibilityService {

    public static K12KbAccessibilityService Instance;
    public static String TAG3 = "K12Kb-AS";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    public static int STD_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

    public final ArrayList<SearchClickPlugin> searchClickPlugins = new ArrayList<>();

    public final ArrayList<SearchClickPlugin> clickerPlugins = new ArrayList<>();

    K12KbSettings k12KbSettings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    //ExecutorService executorService;

    ArrayList<String> DefaultSearchWords;



    private final ArrayList<String> SearchClickPackages = new ArrayList<>();

    K12KbAccServiceOptions k12KbAccServiceOptions;



    public static class K12KbAccServiceOptions {
        public static final String ResName = "k12kb_as_options";

        public static class MetaKeyPlusKey {
            @JsonProperty(index=10)
            public String MetaKeyCode;
            public int MetaKeyCodeInt;

            @JsonProperty(index=20)
            public String KeyKeyCode;
            public int KeyKeyCodeInt;
        }

        public static class DigitsPadHackOptionsAppMarker {
            @JsonProperty(index=10)
            String PackageName;
            @JsonProperty(index=20)
            String DigitsPadMarkerNodeId;
        }

        @JsonProperty(index=10)
        public boolean SearchPluginsEnabled;
        @JsonProperty(index=20)
        public ArrayList<String> RetranslateKeyboardKeyCodes = new ArrayList<>();

        @JsonProperty(index=30)
        public ArrayList<MetaKeyPlusKey> RetranslateKeyboardMetaKeyPlusKeyList = new ArrayList<>();

        @JsonProperty(index=40)
        public boolean DigitsPadPluginEnabled;

        @JsonProperty(index=41)
        public DigitsPadHackOptionsAppMarker[] DigitsPadPluginAppMarkers;

        @JsonProperty(index=50)
        public boolean SelectedNodeClickHack;

        @JsonProperty(index=60)
        public boolean SelectedNodeHighlight;
    }



    @Override
    protected void onServiceConnected() {
        Log.v(TAG3, "onServiceConnected()");
        super.onServiceConnected();

        try {

            Context psc = GetContext(this);
            k12KbSettings = K12KbSettings.Get(psc.getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
            k12KbAccServiceOptions = FileJsonUtils.DeserializeFromJsonApplyPatches(K12KbAccServiceOptions.ResName, new TypeReference<K12KbAccServiceOptions>() {}, this);

            LoadRetranslationData();

            if(k12KbAccServiceOptions.DigitsPadPluginEnabled) {
                DigitsPadHackOptionsAppMarkers = k12KbAccServiceOptions.DigitsPadPluginAppMarkers;
            }

            if (!k12KbAccServiceOptions.SearchPluginsEnabled)
                return;
            LoadSearchPluginData();


            AccessibilityServiceInfo info = getServiceInfo();
            if (k12KbAccServiceOptions.SearchPluginsEnabled) {
                //info.packageNames = SearchClickPackages.toArray(new String[SearchClickPackages.size()]);
                info.packageNames = null;
                //info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            } else {
                info.packageNames = null;
                info.flags = 0;
                info.eventTypes = 0;
            }
            info.eventTypes = BASE_EVENT_TYPES;
            currentEventTypes = BASE_EVENT_TYPES;
            setServiceInfo(info);
            StartWatchingSelectedIme();
            RefreshEventTypeSubscription();

            } catch(Throwable ex) {
                Log.e(TAG3, "onServiceConnected Exception: "+ex);
                LogErrorToGui("onServiceConnected Exception: "+ex);
                new Thread(new Runnable() { public void run() {
                    FileJsonUtils.SleepWithWakes(300);
                    StopService();
                } }).start();
            }
    }
    @Override
    public void onDestroy() {
        Log.v(TAG3, "onDestroy()");
        CancelPendingSearch();
        if (imeChangeObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(imeChangeObserver);
            } catch (Throwable ex) {
                Log.e(TAG3, "unregisterContentObserver: " + ex);
            }
            imeChangeObserver = null;
        }
        Instance = null;
        super.onDestroy();
    }

    WindowManager _currentWindowManager;
    WindowManager.LayoutParams _layoutParams;

    @Override
    public void onCreate() {
        Log.v(TAG3, "onCreate()");
        try {
            FileJsonUtils.Initialize(this);
            super.onCreate();

            Instance = this;
            _currentWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            _layoutParams = InitializeLayoutParams();
            //executorService = Executors.newFixedThreadPool(2);
        } catch(Throwable ex) {
            Log.e(TAG3, "onCreate Exception: "+ex);
            new Thread(new Runnable() { public void run() {
                FileJsonUtils.SleepWithWakes(300);
                StopService();
            } }).start();
        }

    }

    public void StopService() {
        disableSelf();
    }

    @Override
    public void onInterrupt() {
        Log.v(TAG3, "onInterrupt()");
    }

    public AccessibilityNodeInfo CurFocus;

    private long lastAccessibilityEventTime = 0;
    private static final long ACCESSIBILITY_THROTTLE_MS = 80;

    /**
     * Типы событий, на которые служба подписана всегда. Дёшевы: приходят на смену
     * окна и фокуса, а не на каждое изменение содержимого.
     */
    private static final int BASE_EVENT_TYPES =
            AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

    /** Набор типов, на который служба подписана прямо сейчас. */
    private int currentEventTypes = BASE_EVENT_TYPES;
    /** Пакет окна, которое сейчас на экране — по нему решаем, нужна ли подписка. */
    private String lastWindowPackage = "";
    /**
     * Выбрана ли в системе наша клавиатура. Пока выбрана чужая, служба не нужна
     * никому: все её функции (плагины поиска, digits-хак, режим курсора,
     * подсветка узла) обслуживают ввод именно через K12KB. Поэтому при чужой IME
     * подписка снимается полностью — приложения перестают строить для нас
     * события, а не просто получают их отброшенными на нашей стороне.
     */
    private volatile boolean ourImeSelected = true;
    private ContentObserver imeChangeObserver;

    /**
     * Включает и выключает подписку на TYPE_WINDOW_CONTENT_CHANGED.
     *
     * Замер на BlackBerry KEY2 (Android 15), скролл ленты Discord, dumpsys gfxinfo:
     *
     *   служба включена, подписка есть:  janky 162/884 (18.3%), 99p 200 мс,
     *                                    Slow UI thread 124, Missed Vsync 85
     *   служба выключена:                janky  39/699 ( 5.6%), 99p  73 мс,
     *                                    Slow UI thread  28, Missed Vsync  7
     *   служба включена, подписки нет:   janky  43/687 ( 6.3%), 99p 121 мс,
     *                                    Slow UI thread  35, Missed Vsync 10
     *
     * То есть платит не наш обработчик (к дереву Discord мы в том прогоне почти
     * не обращались), а само приложение: пока хоть один сервис подписан на этот
     * тип, приложение обязано строить AccessibilityEvent со всем текстом в своём
     * UI-потоке на каждое изменение содержимого. Throttle внутри
     * onAccessibilityEvent тут не помогает — он срабатывает уже после того, как
     * событие построено и доставлено.
     *
     * Поэтому подписываемся только когда события действительно кому-то нужны.
     */
    public void RefreshEventTypeSubscription() {
        try {
            int target;
            if (!ourImeSelected) {
                target = 0;
            } else if (NeedContentChangedEvents()) {
                target = BASE_EVENT_TYPES | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            } else {
                target = BASE_EVENT_TYPES;
            }
            if (target == currentEventTypes)
                return;
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null)
                return;
            info.eventTypes = target;
            setServiceInfo(info);
            currentEventTypes = target;
            Log.d(TAG3, "eventTypes = 0x" + Integer.toHexString(target)
                    + " ourIme=" + ourImeSelected + " package=" + lastWindowPackage);
        } catch (Throwable ex) {
            Log.e(TAG3, "RefreshEventTypeSubscription: " + ex);
        }
    }

    /**
     * Выбрана ли наша клавиатура: сравниваем пакет из
     * Settings.Secure.DEFAULT_INPUT_METHOD со своим. Пакет службы и IME один и тот
     * же APK, поэтому dev-сборка так же корректно узнаёт свою клавиатуру, а не
     * основную.
     */
    private boolean ReadOurImeSelected() {
        try {
            String current = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (current == null || current.isEmpty())
                return false;
            ComponentName cn = ComponentName.unflattenFromString(current);
            String pkg = (cn != null) ? cn.getPackageName() : current;
            return getPackageName().equals(pkg);
        } catch (Throwable ex) {
            Log.e(TAG3, "ReadOurImeSelected: " + ex);
            // Не смогли выяснить — работаем как раньше, чтобы не отключить службу зря.
            return true;
        }
    }

    /** Следим за сменой клавиатуры, чтобы подписка включалась и снималась сама. */
    private void StartWatchingSelectedIme() {
        ourImeSelected = ReadOurImeSelected();
        if (imeChangeObserver != null)
            return;
        imeChangeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean now = ReadOurImeSelected();
                if (now == ourImeSelected)
                    return;
                ourImeSelected = now;
                Log.d(TAG3, "selected IME changed, ours = " + now);
                if (!now) {
                    // Уходя, прибираем за собой: висящая подсветка и выбранный узел
                    // относятся к нашему режиму курсора.
                    SetCurrentNodeInfo(null);
                    TryRemoveRectangleFast();
                }
                RefreshEventTypeSubscription();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false, imeChangeObserver);
    }

    /**
     * Кому нужны события об изменении содержимого:
     * - плагину поиска или кликера, если текущий пакет вообще в их списке;
     * - digits-хаку, если для текущего пакета заведён маркер;
     * - навигационному режиму;
     * - режиму курсора, но только когда узел уже выбран: пока его нет, новый
     *   придёт с TYPE_VIEW_FOCUSED, на который мы подписаны всегда.
     */
    private boolean NeedContentChangedEvents() {
        if (k12KbAccServiceOptions == null)
            return true;
        String pkg = lastWindowPackage;
        if (k12KbAccServiceOptions.SearchPluginsEnabled && pkg != null && !pkg.isEmpty()
                && ContainsContains(pkg))
            return true;
        if (k12KbAccServiceOptions.DigitsPadPluginEnabled && DigitsPadMarkersContain(pkg))
            return true;
        K12KbIME ime = K12KbIME.Instance;
        if (ime == null)
            return false;
        if (ime.IsNavMode())
            return true;
        return ime._modeGestureAtViewMode == InputMethodServiceCoreGesture.GestureAtViewMode.Pointer
                && (k12KbAccServiceOptions.SelectedNodeClickHack
                    || k12KbAccServiceOptions.SelectedNodeHighlight)
                && ime.CurrentNodeInfo != null;
    }

    // --- отрицательный кэш поиска поля ---

    /**
     * Как долго не повторять поиск в том же окне после неудачи. Полный обход
     * дерева стоит дорого, а поле, которого нет, не появится от того, что мы
     * посмотрим ещё раз через 20 мс. Появление настоящего поля приходит со
     * сменой окна или фокуса — они сбрасывают кэш немедленно.
     */
    private static final long SEARCH_FAIL_TTL_MS = 250;

    /**
     * Отложенный поиск: окно при открытии сыплет десятками contentChanged, а
     * искать достаточно один раз, когда оно устоялось.
     */
    private static final long SEARCH_DEBOUNCE_MS = 200;

    /**
     * Предел откладывания. Telegram при открытии поиска шлёт contentChanged
     * непрерывно, и дебаунс, отменяющий предыдущий запрос, откладывал бы поиск
     * до конца потока — поле не находилось бы вовсе.
     */
    private static final long SEARCH_DEBOUNCE_MAX_MS = 400;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    /** Когда был запрошен первый отложенный поиск текущей серии. */
    private long pendingSearchFirstAt = 0;

    private String lastSearchFailPackage;
    private int lastSearchFailWindowId = -1;
    private long lastSearchFailAt = 0;

    private void CancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        pendingSearchFirstAt = 0;
    }

    /** Сбросить память о неудачном поиске — окно или фокус изменились по-настоящему. */
    private void ResetSearchFailCache() {
        lastSearchFailPackage = null;
        lastSearchFailWindowId = -1;
        lastSearchFailAt = 0;
    }

    private boolean SearchRecentlyFailed(String packageName, int windowId) {
        if (lastSearchFailPackage == null)
            return false;
        if (!lastSearchFailPackage.equals(packageName) || lastSearchFailWindowId != windowId)
            return false;
        return SystemClock.uptimeMillis() - lastSearchFailAt < SEARCH_FAIL_TTL_MS;
    }

    /** Сколько осталось до конца паузы после неудачного поиска, 0 — паузы нет. */
    private long SearchFailCooldownLeft(String packageName, int windowId) {
        if (lastSearchFailPackage == null)
            return 0;
        if (!lastSearchFailPackage.equals(packageName) || lastSearchFailWindowId != windowId)
            return 0;
        long passed = SystemClock.uptimeMillis() - lastSearchFailAt;
        return passed < SEARCH_FAIL_TTL_MS ? (SEARCH_FAIL_TTL_MS - passed) : 0;
    }

    private void RememberSearchFail(String packageName, int windowId) {
        lastSearchFailPackage = packageName;
        lastSearchFailWindowId = windowId;
        lastSearchFailAt = SystemClock.uptimeMillis();
    }

    /** Заведён ли для пакета маркер digits-хака. */
    private boolean DigitsPadMarkersContain(String packageName) {
        if (packageName == null || packageName.isEmpty() || DigitsPadHackOptionsAppMarkers == null)
            return false;
        for (int i = 0; i < DigitsPadHackOptionsAppMarkers.length; i++) {
            K12KbAccServiceOptions.DigitsPadHackOptionsAppMarker marker = DigitsPadHackOptionsAppMarkers[i];
            if (marker != null && marker.PackageName != null
                    && marker.PackageName.equalsIgnoreCase(packageName))
                return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.v(TAG3, "onAccessibilityEvent() eventType: "+event.getEventType() +" "+event.getPackageName());

        try {
            if (!ourImeSelected) {
                // Выбрана чужая клавиатура — событий быть не должно вовсе, но одно
                // может доехать между сменой IME и снятием подписки.
                return;
            }

            if(K12KbIME.Instance == null)
                return;

            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                // Пакет окна и подписка пересчитываются на дешёвых событиях —
                // на них мы подписаны всегда.
                CharSequence pkg = event.getPackageName();
                if (pkg != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        && !getPackageName().contentEquals(pkg)) {
                    // Окно самой клавиатуры — тоже окно, и оно шлёт это событие.
                    // Считать его "текущим приложением" нельзя: пользователь всё
                    // это время остаётся в своём приложении, а подписка на
                    // contentChanged снималась (нашего пакета нет в плагинах), и
                    // плагин переставал видеть изменения — поиск не срабатывал.
                    lastWindowPackage = pkg.toString();
                }
                RefreshEventTypeSubscription();
            }

            // Throttle: skip rapid-fire events to keep main thread free for key events
            // Always process TYPE_WINDOW_STATE_CHANGED (app switches) immediately
            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                long now = SystemClock.uptimeMillis();
                if (now - lastAccessibilityEventTime < ACCESSIBILITY_THROTTLE_MS)
                    return;
                lastAccessibilityEventTime = now;
            }

            if (
                    event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
                            && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED
                            && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                LogEventD(event);
                return;
            }



            if((event.getEventType() & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                if(event.getContentChangeTypes() == (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)) {
                    //Этот хак нужен потому что Blackberry.Dialer жестко спамит эвентами (вроде как никому эти эвенты больше не нужны)
                    //Log.d(TAG3, "IGNORING TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
                    return;
                }
                //Этот хак нужен чтобы не было никакой работы плагинов, когда пользователь набирает большие тексты, которые генерят как раз эти события
                //if(event.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)
                    CharSequence className = event.getClassName();
                    if(className != null && (className.toString().equalsIgnoreCase("android.widget.EditText")
                            || className.toString().equalsIgnoreCase("android.webkit.WebView")
                            || className.toString().equalsIgnoreCase("android.widget.ScrollView")
                    )) {
                        Log.d(TAG3, "IGNORING android.widget.EditText || android.webkit.WebView || android.widget.ScrollView at:" + event.getPackageName());
                        //android.widget.ScrollView
                        return;
                    }
            }

            /** NOTE: Это затормаживает некоторые Web-приложения */
            //AccessibilityNodeInfo root1 = getRootInActiveWindow();
            //if (root1 == null) {
            //    return;
            //}

            if(event.getPackageName() != null && !event.getPackageName().equals(K12KbIME.Instance._lastPackageName))
                return;
            if(K12KbIME.Instance.IsInputMode()) {
                if(CurFocus == null || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    CurFocus = GetFocusedNode(event.getSource());
                    if (CurFocus == null) {
                        CurFocus = GetFocusedNode(getRootInActiveWindow());
                        Log.v(TAG3, "CurFocus: GetFocusedNode(getRootInActiveWindow())");
                    } else {
                        Log.v(TAG3, "CurFocus: GetFocusedNode(event.getSource())");
                    }
                }
            } else {
                Log.v(TAG3, "CurFocus: CurFocus = null");
                CurFocus = null;
            }


            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOW_STATE_CHANGED");
            if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_VIEW_FOCUSED");
            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOW_CONTENT_CHANGED");
                Log.d(TAG3, "TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
            }
            if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_VIEW_SCROLLED");

            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOWS_CHANGED");

            if(event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_ANNOUNCEMENT");


            /** NOTE: Это затормаживает некоторые Web-приложения */
            //LogEventD(event);

            if(k12KbAccServiceOptions.DigitsPadPluginEnabled)
                ProcessDigitsPadHack(event);

            if(
                    (K12KbIME.Instance._modeGestureAtViewMode == InputMethodServiceCoreGesture.GestureAtViewMode.Pointer
                    || K12KbIME.Instance.IsNavMode())
                    && (
                    k12KbAccServiceOptions.SelectedNodeClickHack
                            || k12KbAccServiceOptions.SelectedNodeHighlight
            ))
                ProcessGesturePointerModeAndNodeSelection(event);

            if(k12KbAccServiceOptions.SearchPluginsEnabled)
                ProcessSearchPlugins(event);

        } catch (Throwable ex) {
            Log.e(TAG3, "onAccessibilityEvent Exception: "+ex);
        }
    }



    /** Наглый (или дерзкий) фокус (если видит что фокус не установлен в хост приложении - ставит его принудительно на первый попавшийс элемент)*/
    public static final boolean BrashFocuser = false;


    //region NODES_SELECTION

    private void ProcessGesturePointerModeAndNodeSelection(AccessibilityEvent event) {

        if(K12KbIME.Instance != null && K12KbIME.Instance.IsInputMode()) {
            Log.d(TAG3,"ProcessGesturePointerModeAndNodeSelection:K12KbIME.Instance.IsInputMode()");
            SetCurrentNodeInfo(null);
            TryRemoveRectangle();
            return;
        }

        if(K12KbIME.Instance != null && !K12KbIME.Instance.pref_pointer_mode_rect_and_autofocus) {
            TryRemoveRectangleFast();
        }

        Log.d(TAG3,"ProcessGesturePointerModeAndNodeSelection:LOGIC");

        if(event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED
        )
            return;

        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED && event.getWindowId() == -1) {
            SetCurrentNodeInfo(null);
            TryRemoveRectangleFast();
            return;
        }

        AccessibilityNodeInfo info;
        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            info = event.getSource();
            if(
                    info != null
                    && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && !info.isFocused()
                    && SelectionRectView != null
                    && SelectionRectView.IsSameNodeHash(info)
            ) {
                Log.d(TAG3, "FOCUS MOVED OUT: HASH: "+info.hashCode());
                SetCurrentNodeInfo(null);
                TryRemoveRectangleFast();
                return;
            }

        } else {
            info = null;
        }

        if (K12KbIME.Instance != null
                && (K12KbIME.Instance._modeGestureAtViewMode == InputMethodServiceCoreGesture.GestureAtViewMode.Pointer || K12KbIME.Instance.IsNavMode())
                && !K12KbIME.Instance.IsInputMode())
        {

            info = GetFocusedNode(info);

            if(K12KbIME.Instance != null && K12KbIME.Instance.pref_pointer_mode_rect_and_autofocus && BrashFocuser) {
                if (info == null) {
                    info = GetFocusedNode(getRootInActiveWindow());
                }
                if (info == null) {
                    info = FindFocusableRecurs(GetRoot(getRootInActiveWindow()), 0);
                    if (info != null) {
                        Log.d(TAG3, "FindFocusableRecurs(GetRoot(getRootInActiveWindow()) HASH: " + info.hashCode());
                        boolean clickRes = false;
                        clickRes = info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        if (clickRes) {
                            Log.d(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_FOCUS) OK");
                            return;
                        } else {
                            Log.d(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_FOCUS) FAIL");
                            info = null;
                        }
                    }
                }
            }

            if (k12KbAccServiceOptions.SelectedNodeClickHack) {
                //Имитация click в приложениях (Telegram, BB.Hub) где не работает симуляция KEYCODE_ENTER/SPACE
                if (info != null) {
                    PreparePointerClickHack(info);
                } else {
                    SetCurrentNodeInfo(null);
                }
            }
            if (k12KbAccServiceOptions.SelectedNodeHighlight
                    && K12KbIME.Instance != null && K12KbIME.Instance.pref_pointer_mode_rect_and_autofocus) {
                if (info != null) {
                    //if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    ProcessSelectionRectangle(info);
                    //}
                } else {
                    TryRemoveRectangle();
                }

            }


        }
    }

    private AccessibilityNodeInfo FindFocusedRecurs(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        if(info.isFocused())
            return info;
        for (int i = 0; i < info.getChildCount(); i++)  {
            AccessibilityNodeInfo res = FindFocusedRecurs(info.getChild(i));
            if(res != null)
                return res;
        }
        return null;
    }

    private AccessibilityNodeInfo FindFocusableRecurs(AccessibilityNodeInfo info, int level) {
        if(level > 10)
            return null;
        if(info == null)
            return null;
        if(info.isFocusable())
            return info;
        for (int i = 0; i < Math.min(info.getChildCount(), 10); i++)  {
            AccessibilityNodeInfo res = FindFocusableRecurs(info.getChild(i), level + 1);
            if(res != null)
                return res;
        }
        return null;
    }

    private void FindAllSelectedRecurs(AccessibilityNodeInfo info, ArrayList<AccessibilityNodeInfo> array) {
        if(info == null)
            return;
        if(info.isFocused())
            array.add(info);
        for (int i = 0; i < info.getChildCount(); i++)  {
            FindAllSelectedRecurs(info.getChild(i), array);
        }
        return;
    }

    private AccessibilityNodeInfo GetFocusedNode(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        if(info.isFocused()) {
            Log.d(TAG3, "GetFocusedNode .isFocused() HASH "+info.hashCode());
            return info;
        }

        // Use framework findFocus() — single IPC call instead of
        // recursive tree traversal with thousands of getChild() IPC calls
        AccessibilityNodeInfo info1 = info.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (info1 == null) {
            info1 = info.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if(info1 != null) {
            Log.d(TAG3, "GetFocusedNode findFocus FOUND HASH: "+info1.hashCode());
            return info1;
        } else {
            Log.d(TAG3, "GetFocusedNode findFocus NOT FOUND");
            return null;
        }
    }

    private AccessibilityNodeInfo GetRoot(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        AccessibilityNodeInfo info1 = info;
        for (int i = 0; i < 20; i++) {
            AccessibilityNodeInfo root = info1.getParent();
            if(root == null)
                return info1;
            info1 = root;
        }
        return null;
    }

     private void ProcessSelectionRectangle(AccessibilityNodeInfo info) {

        Rect rect = new Rect();
        info.getBoundsInScreen(rect);
        //Если квадрат
        if(SelectionRectView == null) {
            //&& Math.abs(rect.top - rect.bottom) < 1620/2
            SelectionRectView = CreateRectangleView();
            LestSelectionRectView = SelectionRectView;
            Log.d(TAG3, "DRAW [ASYNC] FIRST-TIME HASH: "+info.hashCode());
            SelectionRectView.SetNodeInfo(info);
            SelectionRectView.removed = false;
            SelectionRectView.RemoveRectOnNextDraw = false;
            _currentWindowManager.addView(SelectionRectView, _layoutParams);
            return;
        }

        if(!SelectionRectView.removed && !SelectionRectView.RemoveRectOnNextDraw && SelectionRectView.IsSameNodeAndRect(info)) {
            Log.d(TAG3, "SelectionRectView.IsSameRect(info) HASH: "+info.hashCode());
            return;
        }

        if(!SelectionRectView.removed && !SelectionRectView.RemoveRectOnNextDraw)
            TryRemoveRectangle();
        Log.d(TAG3, "DRAW [ASYNC] REDRAW HASH: "+info.hashCode());
        SelectionRectView.RemoveRectOnNextDraw = false;
        SelectionRectView.SetNodeInfo(info);
        SelectionRectView.setVisibility(View.GONE);
        SelectionRectView.setVisibility(View.VISIBLE);
    }



    RectView SelectionRectView;
    RectView LestSelectionRectView;

    private RectView CreateRectangleView() {
        RectView rectView = new RectView(this);
        rectView.setClickable(false);
        rectView.setFocusable(false);
        rectView.setFocusableInTouchMode(false);
        rectView.setLongClickable(false);
        rectView.setKeepScreenOn(false);
        //rectView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        return rectView;
    }


    class RectView extends View {

        public AccessibilityNodeInfo SelectedNode;
        public Rect SelectedNodeRect;

        public RectView(Context context) {
            super(context);
            int color = 0xFF86888A;
            if(K12KbIME.Instance != null)
                color = K12KbIME.Instance.pref_pointer_mode_rect_color;
            paintMainer = new Paint();
            paintMainer.setColor(color);
            paintMainer.setStrokeWidth(3);
            paintMainer.setStyle(Paint.Style.STROKE);

            paintTransparenter = new Paint();
            paintTransparenter.setAlpha(0);
            paintTransparenter.setStrokeWidth(3);
            paintTransparenter.setStyle(Paint.Style.STROKE);
        }

        public void SetNodeInfo(AccessibilityNodeInfo info) {
            SelectedNode = info;
        }

        public boolean IsSameNodeAndRect(AccessibilityNodeInfo info) {
            if(SelectedNode != null && SelectedNode.hashCode() == info.hashCode() && IsSameRect(info))
                return true;
            return false;
        }

        public boolean IsSameNodeHash(AccessibilityNodeInfo info) {
            if(SelectedNode != null && SelectedNode.hashCode() == info.hashCode())
                return true;
            return false;
        }

        private boolean IsSameRect(AccessibilityNodeInfo info) {
            if(SelectedNodeRect == null)
                return true;
            Rect rect = new Rect();
            info.getBoundsInScreen(rect);
            if(rect.top == SelectedNodeRect.top
            && rect.bottom == SelectedNodeRect.bottom
            && rect.left == SelectedNodeRect.left
            && rect.right == SelectedNodeRect.right)
                return true;
            return false;
        }

        public RectView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public RectView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            //Toast.makeText(K12KbAccessibilityService.Instance, "rectView.isHardwareAccelerated(): "+canvas.isHardwareAccelerated(), Toast.LENGTH_SHORT).show();
            if(SelectedNode == null)
                return;

            if(RemoveRectOnNextDraw) {


                SelectedNodeRect = new Rect();
                SelectedNode.getBoundsInScreen(SelectedNodeRect);

                canvas.drawRect(SelectedNodeRect, paintTransparenter);
                Log.d(TAG3, "OnDraw() HIDE");
                removed = true;
                RemoveRectOnNextDraw = false;
                SetNodeInfo(null);
                return;
            }

            Log.d(TAG3, "OnDraw() SHOW");
            removed = false;
            SelectedNodeRect = new Rect();
            SelectedNode.getBoundsInScreen(SelectedNodeRect);
            /** deltaY нужен чтобы делать смещение рамки на величину пустого черного поля в Unihertz Titan Slim,
             * в Key1-2 delta=0 и все работает и без этого хака*/
            int[] locationOnScreen = new int[2];
            getLocationOnScreen(locationOnScreen);
            int dx = locationOnScreen[0];
            int dy = locationOnScreen[1];
            SelectedNodeRect = new Rect(SelectedNodeRect.left, SelectedNodeRect.top - dy, SelectedNodeRect.right, SelectedNodeRect.bottom - dy);
            //Большое выделение не нужно - это скорее всего isSelectable крупных блоков-контейнеров
            if(Math.abs(SelectedNodeRect.top - SelectedNodeRect.bottom) < 1620/3*2)
                canvas.drawRect(SelectedNodeRect, paintMainer);
        }

        public boolean RemoveRectOnNextDraw;

        public boolean removed;

        Paint paintMainer;

        Paint paintTransparenter;
    }






    private static WindowManager.LayoutParams InitializeLayoutParams() {
        WindowManager.LayoutParams lp1 = new WindowManager.LayoutParams();
        lp1.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp1.format = PixelFormat.TRANSLUCENT;

        lp1.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;


        lp1.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp1.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp1.gravity = Gravity.FILL;
        return lp1;
    }

    public boolean TryRemoveRectangle() {
        if(SelectionRectView == null)
            return false;
        if(!SelectionRectView.RemoveRectOnNextDraw && !SelectionRectView.removed) {
            Log.d(TAG3, "REMOVE_ASYNC_SIGNAL! TryRemoveRectangle() HASH: "+SelectionRectView.SelectedNode.hashCode());

            SelectionRectView.RemoveRectOnNextDraw = true;
            SelectionRectView.setVisibility(View.GONE);
            SelectionRectView.setVisibility(View.VISIBLE);

            return true;
        }
        return false;
    }

    public boolean TryRemoveRectangleFast() {
        if(SelectionRectView == null)
            return false;
        if(!SelectionRectView.RemoveRectOnNextDraw && !SelectionRectView.removed) {
            Log.d(TAG3, "TryRemoveRectangleFast() HASH: "+SelectionRectView.SelectedNode.hashCode());

            //SelectionRectView.setVisibility(View.INVISIBLE);
            //SelectionRectView.invalidate();

            _currentWindowManager.removeViewImmediate(SelectionRectView);
            SelectionRectView = null;
            return true;
        }
        return false;
    }

    //endregion


    //region GESTURE_POINTER

    private void PreparePointerClickHack(AccessibilityNodeInfo info) {
        Rect rect = new Rect();
        info.getBoundsInScreen(rect);
        float x = rect.centerX();
        float y = rect.centerY();
        SetCurrentNodeInfo(new InputMethodServiceCoreCustomizable.AsNodeClicker() {
            public void Click(boolean isLongClick) {
                Path clickPath = new Path();
                GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
                clickPath.moveTo(x, y);
                if(!isLongClick)
                    clickBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
                else {
                    int longPressTime = ViewConfiguration.getLongPressTimeout();
                    clickBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, longPressTime+longPressTime/2));
                }
                dispatchGesture(clickBuilder.build(), null, null);
            }
        });

    }



    private void SetCurrentNodeInfo(InputMethodServiceCoreCustomizable.AsNodeClicker info) {
        if(K12KbIME.Instance != null) {
            K12KbIME.Instance.SetCurrentNodeInfo(info);
        }
        // Режиму курсора события об изменении содержимого нужны только пока узел
        // выбран — подписка следует за ним.
        RefreshEventTypeSubscription();
    }

    //endregion


    //region SEARCH PLUGIN

    private void ProcessSearchPlugins(AccessibilityEvent event) {

        if(K12KbIME.Instance != null && K12KbIME.Instance.IsActiveInputMode()) {
            Log.d(TAG3, "ProcessSearchPlugins:K12KbIME.Instance.IsActiveInputMode()");
            // Отложенный поиск обязан быть снят вместе с хаком, иначе он
            // выстрелит уже в режиме ввода и поставит хак заново.
            CancelPendingSearch();
            SetSearchHack(null);
            return;
        }

        CharSequence packageNameCs = event.getPackageName();
        if (packageNameCs == null || packageNameCs.length() == 0)
            return;
        String packageName = packageNameCs.toString();

        if(!ContainsContains(packageName)) {
            //LogEventD(event);
            return;
        }
        Log.d(TAG3, "ProcessSearchPlugins:LOGIC");

        // getRootInActiveWindow() — синхронный IPC, заставляющий приложение
        // построить дерево узлов в своём UI-потоке. Раньше он вызывался до
        // цикла, то есть на каждом событии от любого пакета из списка, хотя
        // ProcessSearchField первым делом отсеивает событие по пакету и типу и
        // до дерева чаще всего не доходит. Спрашиваем корень только когда хоть
        // один плагин действительно заинтересован — приложения, которые спамят
        // contentChanged (диалер, телеграм), перестают платить за каждое событие.
        // Смена окна и фокуса — редкие и значимые события: поле могло появиться
        // именно сейчас, поэтому память о прошлой неудаче сбрасывается.
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            ResetSearchFailCache();
            CancelPendingSearch();
        }

        if (!AnyPluginInterested(eventType, packageName))
            return;

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            ScheduleSearch(packageName, eventType, event.getWindowId());
            return;
        }
        RunSearchPlugins(packageName, eventType, event.getWindowId(), event);
    }

    /** Отложить поиск, отменив предыдущий запрос: важно последнее состояние окна. */
    private void ScheduleSearch(final String packageName, final int eventType, final int windowId) {
        long now = SystemClock.uptimeMillis();
        long firstAt = pendingSearchFirstAt;
        if (pendingSearch == null) {
            // Передний фронт: первое событие серии обрабатываем немедленно.
            // Иначе после выхода из поиска экран перестраивается, поток
            // contentChanged откладывает поиск до своего конца, и нажатие,
            // сделанное раньше, попадает в момент, когда хак ещё не поставлен —
            // первое нажатие пропадало. Дороже это не обходится: от повторных
            // обходов защищает отрицательный кэш, а неудачный поиск переносится
            // на конец его паузы.
            RunSearchPlugins(packageName, eventType, windowId, null);
            return;
        }
        if (firstAt != 0 && now - firstAt >= SEARCH_DEBOUNCE_MAX_MS) {
            // Ждём уже достаточно долго — ищем сейчас, не откладывая дальше.
            CancelPendingSearch();
            RunSearchPlugins(packageName, eventType, windowId, null);
            return;
        }
        CancelPendingSearch();
        pendingSearchFirstAt = (firstAt != 0) ? firstAt : now;
        PostSearch(packageName, eventType, windowId, SEARCH_DEBOUNCE_MS, false);
    }

    /**
     * Поставить отложенный поиск в очередь.
     *
     * @param resetFail сбрасывать ли перед запуском память о неудаче: так
     *                  переносится запрос, попавший в паузу отрицательного кэша.
     */
    private void PostSearch(final String packageName, final int eventType, final int windowId,
                            long delay, final boolean resetFail) {
        pendingSearch = new Runnable() {
            public void run() {
                pendingSearch = null;
                pendingSearchFirstAt = 0;
                try {
                    if (resetFail)
                        ResetSearchFailCache();
                    RunSearchPlugins(packageName, eventType, windowId, null);
                } catch (Throwable ex) {
                    Log.e(TAG3, "delayed search: " + ex);
                }
            }
        };
        searchHandler.postDelayed(pendingSearch, delay);
    }

    /** Прогнать плагины по текущему окну. event == null, если поиск отложенный. */
    private void RunSearchPlugins(String packageName, int eventType, int windowId, AccessibilityEvent event) {
        // Хак стоит на живом узле — искать нечего. Проверка стоит одну дешёвую
        // транзакцию refresh() и экономит getRootInActiveWindow(): тот же выход
        // внутри ProcessSearchField срабатывал уже после запроса дерева, то есть
        // приложение всё равно строило его в своём UI-потоке.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && IsSearchHackSet(packageName) && IsSearchHackNodeUsable())
            return;

        long wait = SearchFailCooldownLeft(packageName, windowId);
        if (wait > 0) {
            // Поиск не отменяем, а переносим на конец паузы. Иначе терялся
            // последний запрос серии: первый поиск идёт по window-state-changed,
            // когда дерево ещё не построено, записывает неудачу, а следующие
            // contentChanged попадают в паузу и отбрасываются. События к тому
            // моменту заканчиваются — повторить поиск оказывается некому.
            CancelPendingSearch();
            PostSearch(packageName, eventType, windowId, wait, true);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        for (SearchClickPlugin plugin : searchClickPlugins) {
            if (ProcessSearchField(eventType, packageName, root, event, plugin)) {
                return;
            }
        }
        for (SearchClickPlugin plugin : clickerPlugins) {
            if (ProcessSearchField(eventType, packageName, root, event, plugin)) {
                return;
            }
        }
        // Ни один плагин поля не нашёл — не повторять обход ближайшие
        // SEARCH_FAIL_TTL_MS для этого же окна.
        RememberSearchFail(packageName, windowId);
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && IsSearchHackSet(packageName)) {
            SetSearchHack(null);
            //LogEventD(event);
        }
    }

    /**
     * Совпадает ли хоть один плагин по пакету и типу события — те же условия,
     * с которых начинается ProcessSearchField, но без обращения к дереву узлов.
     */
    private boolean AnyPluginInterested(int eventType, String packageName) {
        for (int i = 0; i < searchClickPlugins.size(); i++) {
            SearchClickPlugin p = searchClickPlugins.get(i);
            if (packageName.contains(p.getPartiallyPackageName()) && p.checkEventType(eventType))
                return true;
        }
        for (int i = 0; i < clickerPlugins.size(); i++) {
            SearchClickPlugin p = clickerPlugins.get(i);
            if (packageName.contains(p.getPartiallyPackageName()) && p.checkEventType(eventType))
                return true;
        }
        return false;
    }

    private boolean ContainsContains(String packageName) {
        for (String pkg:SearchClickPackages
             ) {
            if(packageName.contains(pkg))
                return true;
        }
        return false;
    }


    private void LoadSearchPluginData() throws Exception {
        SearchClickPlugin.SearchClickPluginData data2 = FileJsonUtils.DeserializeFromJsonApplyPatches(RES_PLUGIN_DATA, new TypeReference<SearchClickPlugin.SearchClickPluginData>() {}, getApplicationContext());

        if (data2.DefaultSearchWords != null && !data2.DefaultSearchWords.isEmpty()) {
            DefaultSearchWords = data2.DefaultSearchWords;
        } else {
            DefaultSearchWords = new ArrayList<>();
            DefaultSearchWords.add("Search");
            Log.e(TAG3, "DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
            FileJsonUtils.LogErrorToGui("DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
        }
        LoadSearchPlugins(data2.SearchPlugins, searchClickPlugins);
        LoadSearchPlugins(data2.ClickerPlugins, clickerPlugins);
    }

    private void LoadSearchPlugins(ArrayList<SearchClickPlugin.SearchClickPluginData.SearchPluginData> clickPluginData, ArrayList<SearchClickPlugin> _clickPlugins) {
        for (SearchClickPlugin.SearchClickPluginData.SearchPluginData data : clickPluginData) {
            SearchClickPlugin shp = new SearchClickPlugin(data.PackageName);
            SearchClickPackages.add(data.PackageName);
            if (data.SearchFieldId != null && !data.SearchFieldId.isEmpty())
                shp.setId(data.SearchFieldId);
            else if (data.DynamicSearchMethod != null) {
                shp.DynamicSearchMethod = data.DynamicSearchMethod;
            }
            if (data.AdditionalEventTypeTypeWindowContentChanged)
                shp.setEvents(STD_EVENTS | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            else
                shp.setEvents(STD_EVENTS);
            if (data.CustomClickAdapterClickParent)
                shp.setConverter(new NodeClickableConverter() {
                    @Override
                    public AccessibilityNodeInfo getNode(AccessibilityNodeInfo info) {
                        return info.getParent();
                    }
                });
            else if(data.CustomClickAdapterClickFirstChild)
                shp.setConverter(new NodeClickableConverter() {
                    @Override
                    public AccessibilityNodeInfo getNode(AccessibilityNodeInfo info) {
                        if(info.getChildCount() == 0)
                            return null;
                        return info.getChild(0);
                    }
                });

            shp.WaitBeforeSendChar = data.WaitBeforeSendCharMs;

            _clickPlugins.add(shp);
        }

        for (SearchClickPlugin plugin : _clickPlugins) {
            if (plugin.getId() == null || plugin.getId().isEmpty()) {
                String value = GetFromSetting(plugin);
                if (value != null && value.length() > 0) {
                    plugin.setId(value);
                }
            }
        }
    }


    private String GetFromSetting(SearchClickPlugin plugin) {
        k12KbSettings.CheckSettingOrSetDefault(plugin.getPreferenceKey(), "");
        return k12KbSettings.GetStringValue(plugin.getPreferenceKey());
    }

    private void SetToSetting(SearchClickPlugin plugin, String value) {
        k12KbSettings.SetStringValue(plugin.getPreferenceKey(), value);
    }

    public void ClearFromSettings(SearchClickPlugin plugin) {
        k12KbSettings.ClearFromSettings(plugin.getPreferenceKey());
    }

    /**
     * Диагностический дамп события.
     *
     * ВАЖНО: getSource() и особенно getRootInActiveWindow() — синхронный IPC в
     * процесс приложения-источника, который заставляет его построить дерево
     * узлов в своём UI-потоке. Раньше этот метод вызывался безусловно на каждом
     * событии неподходящего типа, причём getRootInActiveWindow() дважды — то
     * есть отладочный лог сам тормозил чужие приложения. Теперь дамп собирается
     * только при явно включённом verbose-логе, а дорогие вызовы делаются один
     * раз каждый.
     */
    private void LogEventD(AccessibilityEvent event) {
        if (!Log.isLoggable(TAG3, Log.VERBOSE)) return;
        Log.v(TAG3, "--------------------LogEventD--------------------");
        Log.v(TAG3, "event.getEventType() " + event.getEventType());
        Log.v(TAG3, "event.getPackageName() " + event.getPackageName());
        Log.v(TAG3, "event.getClassName() " + event.getClassName());
        Log.v(TAG3, "event.getWindowId() " + event.getWindowId());
        Log.v(TAG3, "event.getText() " + event.getText());
        Log.v(TAG3, "event.getContentDescription() " + event.getContentDescription());
        AccessibilityNodeInfo info = event.getSource();
        Log.v(TAG3, "event.getSource() HASH " + (info != null ? info.hashCode() : "@NULL"));
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Log.v(TAG3, "getRootInActiveWindow() HASH " + (root != null ? root.hashCode() : "@NULL"));
        Log.v(TAG3, "--------------------LogEventD--------------------");
    }

    private boolean ProcessSearchField(int eventType, String fullPackageName, AccessibilityNodeInfo root, AccessibilityEvent event, SearchClickPlugin searchClickPlugin) {
        if (!fullPackageName.contains(searchClickPlugin.getPartiallyPackageName()))
            return false;
        if(!searchClickPlugin.checkEventType(eventType))
            return false;

        if(event != null && (eventType & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.d(TAG3, "TYPE_WINDOW_CONTENT_CHANGED TYPES: " +event.getContentChangeTypes());
        }

        // Хак уже стоит на живом узле — искать поле заново незачем. Раньше поиск
        // выполнялся всегда, и только потом результат сравнивался с установленным
        // хаком: полный обход дерева ради вывода "ничего не изменилось".
        if (IsSearchHackSet(fullPackageName) && IsSearchHackNodeUsable())
            return true;

        AccessibilityNodeInfo info = searchClickPlugin.Convert(FindOrGetFromCache(root, searchClickPlugin));

        if (info != null) {
            if (IsSearchHackSet(fullPackageName, info))
                return true;
            if(info.isFocused() )
                return true;
            Log.d(TAG3, "SetSearchHack=SET package: "+ fullPackageName);
            Log.d(TAG3, "SetSearchHack=SET getClassName: " + (event != null ? event.getClassName() : null));
            SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData = new SearchClickPlugin.SearchPluginLauncher(fullPackageName, info, searchClickPlugin.WaitBeforeSendChar);
            SetSearchHack(searchPluginLaunchData);
            return true;
        } else {
            Log.d(TAG3, "SetSearchHack=NULL package: "+ fullPackageName);
            Log.d(TAG3, "SetSearchHack=NULL: getClassName: " + (event != null ? event.getClassName() : null));
            SetSearchHack(null);
            return false;
        }
    }

    private AccessibilityNodeInfo FindOrGetFromCache(AccessibilityNodeInfo root, SearchClickPlugin searchClickPlugin) {
        if(root == null) return null;
        AccessibilityNodeInfo info = null;
        String fieldId = searchClickPlugin.getId();
        if(fieldId != "") {
            List<AccessibilityNodeInfo> infoList  = root.findAccessibilityNodeInfosByViewId(fieldId);
            if (infoList.size() > 0) {
                //Log.d(TAG, "SetSearchHack: production mode: take from cache");
                info = infoList.get(0);
            }
        } else {
            info = searchClickPlugin.findId(root);

            if (info != null) {
                if (info.getViewIdResourceName() != null) {
                    Log.d(TAG3, "SetSearchHack: research mode: field found " + info.getViewIdResourceName());
                    searchClickPlugin.setId(info.getViewIdResourceName());
                    SetToSetting(searchClickPlugin, info.getViewIdResourceName());
                } else {
                    //AccessibilityNodeInfo info2 = info.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    Log.d(TAG3, "SetSearchHack: getViewIdResourceName() == null " + info.getContentDescription());
                }
            }
        }
        return info;
    }





    private void SetSearchHack(SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData) {
        if (K12KbIME.Instance != null) {
            K12KbIME.Instance.SetSearchHack(searchPluginLaunchData);
        }
    }

    private void SetDigitsHack(boolean value) {
        if (K12KbIME.Instance != null) {
            K12KbIME.Instance.SetDigitsHack(value);
        }
    }

    private boolean IsSearchHackSet(String packageName, AccessibilityNodeInfo info) {
        if (K12KbIME.Instance == null)
            return false;
        if(K12KbIME.Instance.SearchPluginLauncher == null)
            return false;
        return K12KbIME.Instance.SearchPluginLauncher.IsSameAsMine(packageName, info);
    }

    /** Жив ли узел, на котором стоит хак. */
    private boolean IsSearchHackNodeUsable() {
        if (K12KbIME.Instance == null || K12KbIME.Instance.SearchPluginLauncher == null)
            return false;
        return K12KbIME.Instance.SearchPluginLauncher.IsNodeStillUsable();
    }

    private boolean IsSearchHackSet(String packageName) {
        if (K12KbIME.Instance == null)
            return false;
        if(K12KbIME.Instance.SearchPluginLauncher == null)
            return false;
        return K12KbIME.Instance.SearchPluginLauncher.IsSameAsMine(packageName);
    }



    //endregion

    //region DIGITS-PAD

    K12KbAccServiceOptions.DigitsPadHackOptionsAppMarker[] DigitsPadHackOptionsAppMarkers;


    private void ProcessDigitsPadHack(AccessibilityEvent event) {

        if(
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            if (K12KbIME.Instance != null && K12KbIME.Instance.IsInputMode()) {
                Log.d(TAG3, " ProcessDigitsPadHack:K12KbIME.Instance.IsInputMode()");
                SetDigitsHack(false);
                return;
            }
            // Пакет проверяем до getRootInActiveWindow(): раньше дерево строилось
            // на каждом событии, а имя пакета читалось уже из его корня.
            CharSequence eventPackage = event.getPackageName();
            if (!DigitsPadMarkersContain(eventPackage != null ? eventPackage.toString() : null))
                return;
            Log.d(TAG3, " ProcessDigitsPadHack:LOGIC");
            if (ContainsAllDigitsButtons2()) {
                //ContainsAllDigitsButtons(root);
                SetDigitsHack(true);
            }
        }
    }

    private static AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text, int recursLevel) {
        if(recursLevel > 4)
            return null;
        if (node == null)
            return null;
        if(node.getViewIdResourceName() != null)
            Log.d(TAG3, node.getViewIdResourceName());
        if (node.getText() != null) {
            if (node.getText().toString().startsWith(text))
                return node;
            //else Log.d(TAG, "TEXT: "+node.getText());
        }
        for (int i = 0; i < node.getChildCount() && i < 15; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = FindFirstByTextRecursive(child, text, recursLevel+1);
            if (result != null)
                return result;
        }
        return null;
    }

    private boolean ContainsAllDigitsButtons(AccessibilityNodeInfo node) {
        for(int i = 0; i < 10; i++) {
            AccessibilityNodeInfo info3 = FindFirstByTextRecursive(node, Integer.toString(i), 0);
            if(info3 == null)
                return false;
        }
        return true;
    }

    private boolean ContainsAllDigitsButtons2() {
        AccessibilityNodeInfo node = getRootInActiveWindow();
        if(node == null)
            return false;
        for (int i = 0; i < DigitsPadHackOptionsAppMarkers.length; i++) {
            K12KbAccServiceOptions.DigitsPadHackOptionsAppMarker marker = DigitsPadHackOptionsAppMarkers[i];
            if(!marker.PackageName.equalsIgnoreCase(node.getPackageName().toString()))
                continue;
            if(marker.DigitsPadMarkerNodeId == null)
                return true;
            List<AccessibilityNodeInfo> nodeMarkers = node.findAccessibilityNodeInfosByViewId(marker.DigitsPadMarkerNodeId);
            if(nodeMarkers != null && nodeMarkers.size() > 0 && nodeMarkers.get(0).isVisibleToUser())
                return true;
        }
        return false;
    }

    //endregion

    //region KEY RETRANSLATION
    // ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности

    int[] RetranslateKeyCodes;

    private void LoadRetranslationData() throws NoSuchFieldException, IllegalAccessException {
        if(k12KbAccServiceOptions.RetranslateKeyboardKeyCodes != null && !k12KbAccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty()) {
            RetranslateKeyCodes = new int[k12KbAccServiceOptions.RetranslateKeyboardKeyCodes.size()];
            int i = 0;
            for (String keyCode: k12KbAccServiceOptions.RetranslateKeyboardKeyCodes) {
                int keyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCode);

                //Не перехватываем HOME, BACK если не включена настройка защиты текста
                //Т.к. этот перехват лишний раз может повлиять на отзывчивость этих функций
                if(K12KbIME.Instance != null && !K12KbIME.Instance.pref_ensure_entered_text
                    && (keyCodeInt == 3 || keyCodeInt == 4))
                    continue;
                RetranslateKeyCodes[i] = keyCodeInt;
                i++;
            }
        }
        if(k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList != null && !k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList.isEmpty()) {
            for(K12KbAccServiceOptions.MetaKeyPlusKey pair : k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList) {
                pair.MetaKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(pair.MetaKeyCode);
                pair.KeyKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(pair.KeyKeyCode);
            }
        }
    }
    private long lastBackResendUptime = 0;
    /** Ждём ли сейчас закрытия панели, чтобы перевыпустить BACK. */
    private boolean backResendPending = false;
    /**
     * Подстраховка на случай, если скрытие панели не завершится.
     *
     * В трассе видно, что такое бывает: запрос скрытия заканчивался
     * "onFailed at PHASE_WM_NOTIFY_HIDE_ANIMATION_FINISHED". Без подстраховки
     * BACK в этом случае пропал бы совсем. Срок взят с запасом к измеренным
     * 331 мс фактического скрытия и снимается, как только приходит настоящее
     * onFinishInputView.
     */
    private static final long BACK_RESEND_FALLBACK_MS = 700;
    private Runnable backResendFallback = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // Fast-path: never intercept system navigation keys — minimize IPC latency
        int kc = event.getKeyCode();
        if (kc == KeyEvent.KEYCODE_HOME || kc == KeyEvent.KEYCODE_APP_SWITCH)
            return false;

        // Android 16 BACK workaround: when IME has visible UI, framework's
        // OnBackInvokedCallback either hides IME (1st press) and only navigates on
        // 2nd press, or with WILL_NOT_DISMISS does nothing at all. We want 1-press
        // BACK to navigate. Strategy: hide IME panels ourselves, then re-fire BACK.
        if (Build.VERSION.SDK_INT >= 36
                && kc == KeyEvent.KEYCODE_BACK
                && K12KbIME.Instance != null
                && K12KbIME.Instance.IsInputMode()) {
            long now = android.os.SystemClock.uptimeMillis();
            // Let our own re-injected BACK pass through (within 250ms window).
            // Отсчёт идёт от момента перевыпуска, а не от нажатия: скрытие
            // панели занимает сотни миллисекунд, и от нажатия наш же BACK в
            // это окно уже не попадал бы — мы перехватили бы его снова.
            if (now - lastBackResendUptime < 250) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0
                    && !backResendPending) {
                backResendPending = true;
                // 1) Hide IME panels (so framework no longer registers a back callback)
                try {
                    K12KbIME.Instance.requestHideSelf(0);
                } catch (Throwable ignored) {}
                // 2) Перевыпустить BACK, когда панель ДЕЙСТВИТЕЛЬНО убрана.
                // Раньше тут стоял postDelayed(120), а фактическое скрытие по
                // трассе занимает 331 мс: приложение получало навигацию поверх
                // ещё не закрытого IME. Момент закрытия известен точно — это
                // onFinishInputView, по нему и работаем.
                final Runnable resendBack = new Runnable() {
                    @Override public void run() {
                        if (!backResendPending)
                            return;
                        backResendPending = false;
                        mainHandler.removeCallbacks(backResendFallback);
                        try {
                            K12KbIME.Instance.CancelWhenInputViewHidden();
                        } catch (Throwable ignored) {}
                        lastBackResendUptime = android.os.SystemClock.uptimeMillis();
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                        } catch (Throwable ignored) {}
                    }
                };
                backResendFallback = resendBack;
                mainHandler.postDelayed(resendBack, BACK_RESEND_FALLBACK_MS);
                try {
                    K12KbIME.Instance.RunWhenInputViewHidden(resendBack);
                } catch (Throwable ignored) {
                    resendBack.run();
                }
            }
            return true; // consume DOWN and UP of the original BACK
        }

        if (K12KbIME.Instance == null)
            return false;
        if(k12KbAccServiceOptions.RetranslateKeyboardKeyCodes == null || k12KbAccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty())
            return false;
        try {
            //Это ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности
            //Зажатие speed_key+Буква не передается в сервис клавиатуры

            // Этот блок ХАК-а нужен на К2_не_РСТ иначе при нажатиии speed_key вызывается меню биндинга букв
            if (IsRetranslateKeyCode(event)) {
                KeyEvent event1 = GetCopy(event);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return K12KbIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return K12KbIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                }
            }

            if(k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList != null && !k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList.isEmpty()) {
                for (K12KbAccServiceOptions.MetaKeyPlusKey pair : k12KbAccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList) {
                    if(pair.KeyKeyCodeInt != event.getKeyCode())
                        continue;
                    if(!IsMeta(event, pair.MetaKeyCodeInt))
                        continue;
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {

                        KeyEvent event1 = GetCopy(event);
                        return K12KbIME.Instance.onKeyDown(event1.getKeyCode(), event1);

                        /* Раньше работало только так, теперь заработало и по-нормальному
                        executorService.execute(
                                () -> {
                                    try {
                                        KeyEvent event1 = GetCopy(event);
                                        //Было sleep 100 из-за задержки в K12KbIME относительно AS
                                        Thread.sleep(1);
                                        K12KbIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                                    } catch (Throwable ignored) {
                                    }
                                });
                        return true;

                         */
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        //executorService.execute(() -> {K12KbIME.Instance.onKeyUp(event.getKeyCode(), event);});
                        KeyEvent event1 = GetCopy(event);
                        return K12KbIME.Instance.onKeyUp(event1.getKeyCode(), event1);

                    }
                }
            }
        } catch(Throwable ex) {
            Log.e(TAG3, "onKeyEvent Exception: "+ex);
        }
        return false;
    }

    private boolean IsRetranslateKeyCode(KeyEvent event) {
        for (int retr : RetranslateKeyCodes) {
            if(retr == event.getKeyCode())
                return true;
        }
        return false;
    }

    private boolean IsMeta(KeyEvent event, int meta) {
        if((event.getMetaState() & meta) == meta)
            return true;
        return false;
    }

    private KeyEvent GetCopyNewTime(KeyEvent keyEvent) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    private KeyEvent GetCopy(KeyEvent keyEvent) {
        return new KeyEvent(keyEvent.getDownTime(), keyEvent.getEventTime(), keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    //endregion


    public void IntentQuickSettings() {
        Log.d(TAG3, "performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)");
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public void IntentNotifications() {
        Log.d(TAG3, "performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)");
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

}
