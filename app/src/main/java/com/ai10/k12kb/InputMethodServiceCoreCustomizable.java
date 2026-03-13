package com.ai10.k12kb;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.annotation.RequiresApi;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import com.android.internal.telephony.ITelephony;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.android.voiceime.VoiceRecognitionTrigger;
import com.ai10.k12kb.input.CallStateCallback;


import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import static com.ai10.k12kb.FileJsonUtils.LogErrorToGui;

public abstract class InputMethodServiceCoreCustomizable extends InputMethodServiceCorePrediction {
    protected boolean pref_show_toast = false;
    protected boolean pref_show_default_onscreen_keyboard = true;
    protected boolean pref_alt_space = true;
    protected boolean pref_manage_call = false;
    protected boolean pref_long_press_key_alt_symbol = false;
    protected boolean pref_vibrate_on_key_down = false;
    protected boolean pref_ensure_entered_text = true;
    protected int pref_gesture_mode_at_view_mode = 0;
    protected boolean pref_pointer_mode_rect_and_autofocus = true;
    protected int pref_pointer_mode_rect_color = 0;
    protected boolean pref_auto_capitalization = true;
    protected boolean pref_nav_pad_on_hold = true;
    protected boolean pref_transparency_mode = true;

    static final String TRANSPARENCY_MODE_PREFIX = "TRANSPARENCY_MODE_";
    protected boolean _currentAppTransparency = true;

    private boolean metaHoldCtrl; // только первая буква будет большая
    protected boolean metaFixedModeFirstLetterUpper; // только первая буква будет большая
    protected boolean metaFixedModeCapslock; //все следующий буквы будут большие
    protected boolean metaHoldShift; //нажатие клавишь с зажатым альтом
    protected boolean symPadAltShift;
    protected boolean metaHoldAlt; //нажатие клавишь с зажатым альтом

    protected boolean metaHoldSym;
    protected boolean metaFixedModeFirstSymbolAlt;
    protected boolean metaFixedModeAllSymbolsAlt;

    protected boolean keyboardStateFixed_SymbolOnScreenKeyboard;
    protected boolean keyboardStateFixed_FnSymbolOnScreenKeyboard;
    protected boolean keyboardStateFixed_NavModeAndKeyboard;

    protected boolean keyboardStateHolding_NavModeAndKeyboard;

    protected boolean needUpdateVisualInsideSingleEvent;

    protected ViewSatedaKeyboard keyboardView;
    protected Processable OnStartInput;
    protected Processable OnFinishInput;
    protected Processable BeforeSendChar;
    protected Processable AfterSendChar;
    protected int[] ViewModeExcludeKeyCodes;

    public String keyboard_mechanics_res;

    protected Vibrator vibratorService;

    protected int TIME_VIBRATE;

    //region KEYBOARD_MECHANICS LOAD

    KeyboardMechanics KeyboardMechanics;

    public static class KeyboardMechanics {
        @JsonProperty(index = 20)
        public ArrayList<Action> OnStartInputActions;
        @JsonProperty(index = 30)
        public ArrayList<Action> OnFinishInputActions;
        @JsonProperty(index = 40)
        public ArrayList<Action> BeforeSendCharActions;
        @JsonProperty(index = 50)
        public ArrayList<Action> AfterSendCharActions;
        @JsonProperty(index = 60)
        public ArrayList<String> ViewModeKeyTransparencyExcludeKeyCodes;

        @JsonProperty(index = 90)
        public KeyGroupProcessor OnAnyKeyBeforeActions;

        @JsonProperty(index = 100)
        public ArrayList<KeyGroupProcessor> KeyGroupProcessors = new ArrayList<>();

        @JsonProperty(index = 105)
        public ArrayList<KeyGroupProcessor> NavKeyGroupProcessors = new ArrayList<>();


        @JsonProperty(index = 110)
        public GestureProcessor GestureProcessor = new GestureProcessor();

        public static class GestureProcessor {
            @JsonProperty(index = 10)
            public boolean KeyboardGesturesAtInputModeEnabled;
            @JsonProperty(index = 20)
            public boolean KeyHoldPlusGestureEnabled;
            @JsonProperty(index = 30)
            public ArrayList<Action> OnGestureDoubleClick;
            @JsonProperty(index = 40)
            public ArrayList<Action> OnGestureTripleClick;
            @JsonProperty(index = 50)
            public ArrayList<Action> OnGestureSecondClickUp;
        }


        public static class KeyGroupProcessor {
            @JsonProperty(index = 10)
            public ArrayList<String> KeyCodes = new ArrayList<>();
            public ArrayList<Integer> KeyCodeList = new ArrayList<>();

            @JsonProperty(index = 20)
            public ArrayList<Action> OnShortPress;
            @JsonProperty(index = 30)
            public ArrayList<Action> OnDoublePress;
            @JsonProperty(index = 40)
            public ArrayList<Action> OnLongPress;
            @JsonProperty(index = 50)
            public ArrayList<Action> OnHoldOn;
            @JsonProperty(index = 60)
            public ArrayList<Action> OnHoldOff;
            @JsonProperty(index = 70)
            public ArrayList<Action> OnTriplePress;
            @JsonProperty(index = 80)
            public ArrayList<Action> OnUndoShortPress;
        }

        public static class Action {
            @JsonProperty(index = 5)
            public String Comment;
            @JsonProperty(index = 10)
            public ArrayList<String> MetaModeMethodNames;
            public ArrayList<IActionMethod> MetaModeMethods;

            @JsonProperty(index = 20)
            public String ActionMethodName;
            public IActionMethod ActionMethod;

            @JsonProperty(index = 30)
            public boolean MethodNeedsKeyPressParameter;
            @JsonProperty(index = 40)
            public boolean NeedUpdateVisualState;
            @JsonProperty(index = 45)
            public boolean NeedUpdateGestureVisualState;
            @JsonProperty(index = 50)
            public boolean StopProcessingAtSuccessResult;
            @JsonProperty(index = 60)
            public String CustomKeyCode;
            public int CustomKeyCodeInt;
            @JsonProperty(index = 70)
            public char CustomChar;
            @JsonProperty(index = 80)
            public boolean MethodNeedsKeyEventParameter;
            @JsonProperty(index = 90)
            public boolean MethodNeedsNavActionParameter;

        }
    }





    void LoadKeyActions(StringBuilder sbSubStage, KeyProcessingMode keyAction, InputMethodServiceCoreCustomizable.KeyboardMechanics.KeyGroupProcessor kgp) throws Exception {
        String keyCode = "NONE";
        if(kgp.KeyCodes != null && !kgp.KeyCodes.isEmpty())
            keyCode = kgp.KeyCodes.get(0);

        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnShortPress) KEY_CODE: %s",keyCode));
        keyAction.OnShortPress = ProcessMethodMappingAndCreateProcessable(kgp.OnShortPress, keyAction.OnShortPress);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnUndoShortPress) KEY_CODE: %s",keyCode));
        keyAction.OnUndoShortPress = ProcessMethodMappingAndCreateProcessable(kgp.OnUndoShortPress, keyAction.OnUndoShortPress);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnDoublePress) KEY_CODE: %s",keyCode));
        keyAction.OnDoublePress = ProcessMethodMappingAndCreateProcessable(kgp.OnDoublePress, keyAction.OnDoublePress);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnLongPress) KEY_CODE: %s",keyCode));
        keyAction.OnLongPress = ProcessMethodMappingAndCreateProcessable(kgp.OnLongPress, keyAction.OnLongPress);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOn) KEY_CODE: %s",keyCode));
        keyAction.OnHoldOn = ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOn, keyAction.OnHoldOn);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOff) KEY_CODE: %s",keyCode));
        keyAction.OnHoldOff = ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOff, keyAction.OnHoldOff);
        sbSubStage.setLength(0);
        sbSubStage.append(String.format("ProcessMethodMappingAndCreateProcessable(kgp.OnTriplePress) KEY_CODE: %s",keyCode));
        keyAction.OnTriplePress = ProcessMethodMappingAndCreateProcessable(kgp.OnTriplePress, keyAction.OnTriplePress);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void LoadKeyProcessingMechanics(Context context) throws Exception {

        String LOADING_STAGE = "";
        StringBuilder SB_STAGE = new StringBuilder();
        try {

            LOADING_STAGE = "DeserializeFromJson(keyboard_mechanics_res): "+keyboard_mechanics_res;
            KeyboardMechanics = FileJsonUtils.DeserializeFromJsonApplyPatches(keyboard_mechanics_res, new TypeReference<KeyboardMechanics>() {
            }, context);

            LOADING_STAGE = "LoadActionMethodsStringMapping()";
            LoadActionMethodsStringMapping();

            LOADING_STAGE = "KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes";
            if (KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes != null) {
                ViewModeExcludeKeyCodes = new int[KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes.size()];
                int i = 0;
                for (String keyCode1 : KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes) {
                    ViewModeExcludeKeyCodes[i] = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCode1);
                    i++;
                }
            }

            LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnStartInputActions)";
            OnStartInput = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnStartInputActions, OnStartInput);

            LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnFinishInputActions)";
            OnFinishInput = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnFinishInputActions, OnFinishInput);

            LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.BeforeSendCharActions)";
            BeforeSendChar = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.BeforeSendCharActions, BeforeSendChar);

            LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.AfterSendCharActions)";
            AfterSendChar = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.AfterSendCharActions, AfterSendChar);



            if(KeyboardMechanics.OnAnyKeyBeforeActions != null) {
                LOADING_STAGE = "AnyKeyBeforeAction = new KeyProcessingMode()";
                AnyKeyBeforeAction = new KeyProcessingMode();
                LoadKeyActions(SB_STAGE, AnyKeyBeforeAction, KeyboardMechanics.OnAnyKeyBeforeActions);
            }

            LOADING_STAGE = "KeyboardMechanics.KeyGroupProcessors";
            for (KeyboardMechanics.KeyGroupProcessor kgp : KeyboardMechanics.KeyGroupProcessors) {

                kgp.KeyCodeList = new ArrayList<>();
                for (String keyCodeCode : kgp.KeyCodes) {
                    LOADING_STAGE = "FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCodeCode): "+keyCodeCode;
                    int value = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCodeCode);
                    kgp.KeyCodeList.add(value);

                }
                if(kgp.KeyCodeList.isEmpty())
                    throw new Exception("CAN NOT LOAD KEYBOARD MECHANICS ON STAGE "+LOADING_STAGE+" key-codes CAN NOT BE EMPTY");

                int[] kc_arr = new int[kgp.KeyCodeList.size()];
                for (int _i = 0; _i < kgp.KeyCodeList.size(); _i++) { kc_arr[_i] = kgp.KeyCodeList.get(_i).intValue(); }

                for (int kc : kc_arr) {
                    KeyProcessingMode keyAction1 = mainModeKeyProcessorsMap.get(kc);
                    if(keyAction1 == null) {
                        keyAction1 = new KeyProcessingMode();
                        mainModeKeyProcessorsMap.put(kc, keyAction1);
                    }
                    LoadKeyActions(SB_STAGE, keyAction1, kgp);
                }
            }

            LOADING_STAGE = "KeyboardMechanics.NavKeyGroupProcessors";
            for (KeyboardMechanics.KeyGroupProcessor kgp : KeyboardMechanics.NavKeyGroupProcessors) {

                kgp.KeyCodeList = new ArrayList<>();
                for (String keyCodeCode : kgp.KeyCodes) {
                    LOADING_STAGE = "FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCodeCode): "+keyCodeCode;
                    int value = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCodeCode);
                    kgp.KeyCodeList.add(value);

                }
                if(kgp.KeyCodeList.isEmpty())
                    throw new Exception("CAN NOT LOAD KEYBOARD MECHANICS ON STAGE "+LOADING_STAGE+" key-codes CAN NOT BE EMPTY");

                int[] kc_arr = new int[kgp.KeyCodeList.size()];
                for (int _i = 0; _i < kgp.KeyCodeList.size(); _i++) { kc_arr[_i] = kgp.KeyCodeList.get(_i).intValue(); }

                for (int kc : kc_arr) {
                    KeyProcessingMode keyAction1 = new KeyProcessingMode();
                    navKeyProcessorsMap.put(kc, keyAction1);
                    LoadKeyActions(SB_STAGE, keyAction1, kgp);
                }

            }

            if (KeyboardMechanics.GestureProcessor != null) {
                super.KeyboardGesturesAtInputModeEnabled = KeyboardMechanics.GestureProcessor.KeyboardGesturesAtInputModeEnabled;

                if (!KeyboardMechanics.GestureProcessor.KeyboardGesturesAtInputModeEnabled)
                    return;
                super.KeyHoldPlusGestureEnabled = KeyboardMechanics.GestureProcessor.KeyHoldPlusGestureEnabled;

                if (KeyboardMechanics.GestureProcessor.OnGestureDoubleClick != null) {
                    LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureDoubleClick)";
                    super.OnGestureDoubleClick = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureDoubleClick, super.OnGestureDoubleClick);
                }
                if (KeyboardMechanics.GestureProcessor.OnGestureTripleClick != null) {
                    LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureTripleClick)";
                    super.OnGestureTripleClick = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureTripleClick, super.OnGestureTripleClick);
                }
                if (KeyboardMechanics.GestureProcessor.OnGestureSecondClickUp != null) {
                    LOADING_STAGE = "ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureSecondClickUp)";
                    super.OnGestureSecondClickUp = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.GestureProcessor.OnGestureSecondClickUp, super.OnGestureSecondClickUp);
                }
            }

        } catch (Throwable ex) {
            if(SB_STAGE.length() > 0)
                LOADING_STAGE += "SUB_STAGE: "+SB_STAGE;
            Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS ON STAGE "+LOADING_STAGE+" EXCEPTION: " + ex);
            throw new Exception("CAN NOT LOAD KEYBOARD MECHANICS ON STAGE "+LOADING_STAGE+" EXCEPTION: " + ex);
        }
    }


    boolean DoNothingAndMakeUndoAtSubsequentKeyAction(KeyPressData keyPressData) {
        return true;
    }

    private InputMethodServiceCoreKeyPress.Processable ProcessMethodMappingAndCreateProcessable(ArrayList<KeyboardMechanics.Action> list, InputMethodServiceCoreKeyPress.Processable processable) throws Exception {
        if (list == null || list.isEmpty()) {
            return null;
        }

        for (KeyboardMechanics.Action action : list) {
            ActionMethod method;
            if (action.MethodNeedsNavActionParameter) {
                // Эта проверка должна быть до action.CustomKeyCode так как action.CustomKeyCode в нем используется тоже, но есть и без него вариант, отдельный
                method = FindActionMethodByName(action.ActionMethodName);

                if (!method.getType().equals(NavActionData.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                    throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                }

                if(action.CustomKeyCode == null || action.CustomKeyCode.isEmpty()) {
                    Log.e(TAG2, "ACTION METHOD MethodNeedsNavActionParameter NEED CustomKeyCode parameter");
                    throw new Exception("ACTION METHOD MethodNeedsNavActionParameter NEED CustomKeyCode parameter");
                }

                action.CustomKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(action.CustomKeyCode);


            } else if (action.CustomKeyCode != null && !action.CustomKeyCode.isEmpty()) {
                action.CustomKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(action.CustomKeyCode);
                method = FindActionMethodByName(action.ActionMethodName);
                if (!method.getType().equals(Integer.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                    throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                }
            } else if (action.CustomChar > 0) {
                method = FindActionMethodByName(action.ActionMethodName);
                if(method == null) return null;
                if (!method.getType().equals(Character.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                    throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                }
            } else if (action.MethodNeedsKeyPressParameter) {
                method = FindActionMethodByName(action.ActionMethodName);

                if (!method.getType().equals(KeyPressData.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                    throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                }
            } else if (action.MethodNeedsKeyEventParameter) {
                method = FindActionMethodByName(action.ActionMethodName);

                if (!method.getType().equals(KeyEvent.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                    throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + action.ActionMethodName + " NEED: " + method.getType());
                }
            } else {
                method = FindActionMethodByName(action.ActionMethodName);

            }
            action.ActionMethod = method;

            if (action.MetaModeMethodNames != null && !action.MetaModeMethodNames.isEmpty()) {
                action.MetaModeMethods = new ArrayList<>();
                for (String metaMethodName : action.MetaModeMethodNames) {

                    ActionMethod metaMethod = FindActionMethodByName(metaMethodName);

                    action.MetaModeMethods.add(metaMethod);
                    if (!metaMethod.getType().equals(Object.class)) {
                        Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH " + metaMethodName + " NEED: " + metaMethod.getType());
                        throw new Exception("ACTION METHOD PARAMETER TYPE MISMATCH " + metaMethodName + " NEED: " + metaMethod.getType());
                    }
                }
            }
        }

        Processable2 p = (Processable2) processable;
        if(p == null) {
            p = new Processable2();
            p.Actions = list;
            p.Keyboard = this;
        } else {
            p.Actions.addAll(list);
        }
        return p;
    }

    ActionMethod FindActionMethodByName(String methodName) throws Exception {
        if (!Methods.containsKey(methodName)) {
            Log.e(TAG2, "CAN NOT FIND PREDEFINED ACTION METHOD " + methodName);
            LogErrorToGui("CAN NOT FIND PREDEFINED ACTION METHOD " + methodName);
            throw new Exception("CAN NOT FIND PREDEFINED ACTION METHOD " + methodName);
        }
        return Methods.get(methodName);
    }

    //endregion

    //region MAIN HASHMAP ActionMethods

    interface IActionMethod<T> {
        boolean invoke(T t);
    }

    class ActionMethod<T> implements IActionMethod<T> {

        Class _class;

        public Class getType() {
            return _class;
        }

        private ActionMethod() {
        }

        public ActionMethod(IActionMethod<T> IActionMethod, Class class1) {
            _class = class1;
            _method = IActionMethod;
        }

        IActionMethod<T> _method;

        @Override
        public boolean invoke(T t) {
            return _method.invoke(t);
        }
    }

    HashMap<String, ActionMethod> Methods = new HashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.O)
    void LoadActionMethodsStringMapping() {
        MAIN_ActionMethodsHashMapInitializer();
        //CodeGenerate();
    }

    private void CodeGenerate() {
        String methodsHashFulfill = "";
        Method[] methods = InputMethodServiceCoreCustomizable.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()))
                continue;
            if (!Modifier.isPublic(method.getModifiers()))
                continue;
            if (!method.getReturnType().equals(boolean.class))
                continue;

            if (method.getParameterTypes().length == 0) {
                //Methods.put("ActionTryAcceptCall", (Object o) -> ActionTryAcceptCall());
                methodsHashFulfill += "Methods.put(\"" + method.getName() + "\", InitializeMethod3((Object o) -> " + method.getName() + "(), Object.class));\n";
            }

            if (method.getParameterTypes().length != 1)
                continue;

            if (method.getParameterTypes()[0].equals(KeyPressData.class)) {
                //Methods.put("ActionSendCtrlPlusKey", (Method3<KeyPressData>)this::ActionSendCtrlPlusKey);
                methodsHashFulfill += "Methods.put(\"" + method.getName() + "\", InitializeMethod3(this::" + method.getName() + ", KeyPressData.class));\n";
            } else if (method.getParameterTypes()[0].equals(int.class)) {
                //Methods.put("ActionKeyDown", (Method3<Integer>)this::ActionKeyDown);
                methodsHashFulfill += "Methods.put(\"" + method.getName() + "\", InitializeMethod3(this::" + method.getName() + ", Integer.class));\n";
            } else if (method.getParameterTypes()[0].equals(char.class)) {
                //Methods.put("ActionKeyDown", (Method3<Character>)this::ActionSendCharToInput);
                methodsHashFulfill += "Methods.put(\"" + method.getName() + "\", InitializeMethod3(this::" + method.getName() + ", Character.class));\n";
            }
        }
        Log.d(TAG2, methodsHashFulfill);
    }


    private <T> ActionMethod<T> InitializeMethod3(IActionMethod<T> IActionMethod, Class class1) {
        return new ActionMethod<T>(IActionMethod, class1);
    }

    // Helper for KeyPressData methods resolved via reflection (for methods in subclasses)
    private ActionMethod<KeyPressData> objActionKeyPress(final String methodName) {
        final java.lang.reflect.Method m;
        try {
            m = this.getClass().getMethod(methodName, KeyPressData.class);
        } catch (Exception e) {
            Log.e(TAG2, "objActionKeyPress lookup error: " + methodName + " " + e);
            return new ActionMethod<KeyPressData>(new IActionMethod<KeyPressData>() {
                public boolean invoke(KeyPressData p) { return false; }
            }, KeyPressData.class);
        }
        return new ActionMethod<KeyPressData>(new IActionMethod<KeyPressData>() {
            public boolean invoke(KeyPressData p) {
                try {
                    return (Boolean) m.invoke(InputMethodServiceCoreCustomizable.this, p);
                } catch (Exception e) {
                    Log.e(TAG2, "objActionKeyPress invoke error: " + methodName + " " + e);
                    return false;
                }
            }
        }, KeyPressData.class);
    }

    // Helper to create IActionMethod<Object> without lambdas (avoids invokedynamic/BootstrapMethodError)
    private ActionMethod<Object> objAction(final String methodName) {
        final java.lang.reflect.Method m;
        try {
            m = this.getClass().getMethod(methodName);
        } catch (Exception e) {
            Log.e(TAG2, "objAction lookup error: " + methodName + " " + e);
            return new ActionMethod<Object>(new IActionMethod<Object>() {
                public boolean invoke(Object o) { return false; }
            }, Object.class);
        }
        return new ActionMethod<Object>(new IActionMethod<Object>() {
            public boolean invoke(Object o) {
                try {
                    return (Boolean) m.invoke(InputMethodServiceCoreCustomizable.this);
                } catch (Exception e) {
                    Log.e(TAG2, "objAction invoke error: " + methodName + " " + e);
                    return false;
                }
            }
        }, Object.class);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void MAIN_ActionMethodsHashMapInitializer() {

        Methods.put("ActionChangeFirstLetterShiftMode", objAction("ActionChangeFirstLetterShiftMode"));
        Methods.put("ActionChangeFirstSymbolAltMode", objAction("ActionChangeFirstSymbolAltMode"));
        Methods.put("ActionChangeFixedAltModeState", objAction("ActionChangeFixedAltModeState"));
        Methods.put("ActionChangeGestureModeEnableState", objAction("ActionChangeGestureModeEnableState"));
        Methods.put("ActionChangeKeyboardLayout", objAction("ActionChangeKeyboardLayout"));
        Methods.put("ActionChangeShiftCapslockState", objAction("ActionChangeShiftCapslockState"));
        Methods.put("ActionChangeSwipePanelVisibility", objAction("ActionChangeSwipePanelVisibility"));
        Methods.put("ActionDeletePreviousSymbol", objAction("ActionDeletePreviousSymbol"));
        Methods.put("ActionDeleteUntilPrevCrLf", objAction("ActionDeleteUntilPrevCrLf"));
        Methods.put("ActionDisableAndResetGestureMode", objAction("ActionDisableAndResetGestureMode"));
        Methods.put("ActionDisableAndResetGesturesAtInputMode", objAction("ActionDisableAndResetGesturesAtInputMode"));
        Methods.put("ActionDisableGestureMode", objAction("ActionDisableGestureMode"));
        Methods.put("ActionDisableHoldAltMode", objAction("ActionDisableHoldAltMode"));
        Methods.put("ActionDisableHoldCtrlMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionDisableHoldCtrlMode(p); } }, KeyPressData.class));
        Methods.put("ActionDisableHoldShiftMode", objAction("ActionDisableHoldShiftMode"));
        Methods.put("ActionDisableNavSymFnKeyboard", objAction("ActionDisableNavSymFnKeyboard"));
        Methods.put("ActionDoNothing", objAction("ActionDoNothing"));
        Methods.put("ActionEnableFixedAltModeState", objAction("ActionEnableFixedAltModeState"));
        Methods.put("ActionEnableGestureAtInputModeAndUpDownMode", objAction("ActionEnableGestureAtInputModeAndUpDownMode"));
        Methods.put("ActionEnableHoldAltMode", objAction("ActionEnableHoldAltMode"));
        Methods.put("ActionEnableHoldCtrlMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionEnableHoldCtrlMode(p); } }, KeyPressData.class));
        Methods.put("ActionEnableHoldShiftMode", objAction("ActionEnableHoldShiftMode"));
        Methods.put("ActionKeyDown", InitializeMethod3(new IActionMethod<Integer>() { public boolean invoke(Integer p) { return ActionKeyDown(p); } }, Integer.class));
        Methods.put("ActionKeyDownUpDefaultFlags", InitializeMethod3(new IActionMethod<Integer>() { public boolean invoke(Integer p) { return ActionKeyDownUpDefaultFlags(p); } }, Integer.class));
        Methods.put("ActionKeyDownUpNoMetaKeepTouch", InitializeMethod3(new IActionMethod<Integer>() { public boolean invoke(Integer p) { return ActionKeyDownUpNoMetaKeepTouch(p); } }, Integer.class));
        Methods.put("ActionResetDoubleClickGestureState", objAction("ActionResetDoubleClickGestureState"));
        Methods.put("ActionSendCharDoublePressNoMeta", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharDoublePressNoMeta(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolAltMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharLongPressAltSymbolAltMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolNoMeta", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharLongPressAltSymbolNoMeta(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolShiftMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharLongPressAltSymbolShiftMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharLongPressCapitalize", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharLongPressCapitalize(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharLongPressCapitalizeAltMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharLongPressCapitalizeAltMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressAltMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharSinglePressAltMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressNoMeta", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharSinglePressNoMeta(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressShiftMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharSinglePressShiftMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressSymMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharSinglePressSymMode(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharToInput", InitializeMethod3(new IActionMethod<Character>() { public boolean invoke(Character p) { return ActionSendCharToInput(p); } }, Character.class));
        Methods.put("ActionSendCtrlPlusKey", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCtrlPlusKey(p); } }, KeyPressData.class));

        Methods.put("ActionSetNeedUpdateVisualState", objAction("ActionSetNeedUpdateVisualState"));
        Methods.put("ActionTryAcceptCall", objAction("ActionTryAcceptCall"));
        Methods.put("ActionTryCapitalizeFirstLetter", objAction("ActionTryCapitalizeFirstLetter"));
        Methods.put("ActionTryChangeKeyboardLayoutAtBaseMetaShift", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionTryChangeKeyboardLayoutAtBaseMetaShift(p); } }, KeyPressData.class));
        Methods.put("ActionTryChangeSymPadLayout", objAction("ActionTryChangeSymPadLayout"));
        Methods.put("ActionTryChangeSymPadVisibilityAtInputMode", objAction("ActionTryChangeSymPadVisibilityAtInputMode"));
        Methods.put("ActionTryDeclineCall", objAction("ActionTryDeclineCall"));
        Methods.put("ActionTryDisableAltModeUponSpace", objAction("ActionTryDisableAltModeUponSpace"));
        Methods.put("ActionTryDisableCapslockShiftMode", objAction("ActionTryDisableCapslockShiftMode"));
        Methods.put("ActionTryDisableFirstLetterShiftMode", objAction("ActionTryDisableFirstLetterShiftMode"));
        Methods.put("ActionTryDisableFirstSymbolAltMode", objAction("ActionTryDisableFirstSymbolAltMode"));
        Methods.put("ActionTryDisableFixedAltModeState", objAction("ActionTryDisableFixedAltModeState"));
        Methods.put("ActionTryDisableNavModeAndKeyboard", objAction("ActionTryDisableNavModeAndKeyboard"));
        Methods.put("ActionTryDisableSymPad", objAction("ActionTryDisableSymPad"));
        Methods.put("ActionTryDoubleSpaceDotSpaceConversion", objAction("ActionTryDoubleSpaceDotSpaceConversion"));
        Methods.put("ActionTryEnableNavModeAndKeyboard", objAction("ActionTryEnableNavModeAndKeyboard"));
        Methods.put("ActionTryResetSearchPlugin", objAction("ActionTryResetSearchPlugin"));
        Methods.put("ActionTryTurnOffGesturesMode", objAction("ActionTryTurnOffGesturesMode"));
        Methods.put("ActionUnCrLf", objAction("ActionUnCrLf"));
        Methods.put("InputIsDate", objAction("InputIsDate"));
        Methods.put("InputIsNumber", objAction("InputIsNumber"));
        Methods.put("InputIsPhone", objAction("InputIsPhone"));
        Methods.put("InputIsText", objAction("InputIsText"));
        Methods.put("MetaIsActionBeforeMeta", objAction("MetaIsActionBeforeMeta"));
        Methods.put("MetaIsPackageChanged", objAction("MetaIsPackageChanged"));
        Methods.put("MetaIsAltMode", objAction("MetaIsAltMode"));
        Methods.put("MetaIsAltPressed", objAction("MetaIsAltPressed"));
        Methods.put("MetaIsCtrlPressed", objAction("MetaIsCtrlPressed"));
        Methods.put("MetaIsShiftMode", objAction("MetaIsShiftMode"));
        Methods.put("MetaIsShiftPressed", objAction("MetaIsShiftPressed"));
        Methods.put("MetaIsSymPadAltShiftMode", objAction("MetaIsSymPadAltShiftMode"));
        Methods.put("MetaStateIsOnCall", objAction("MetaStateIsOnCall"));
        Methods.put("PrefLongPressAltSymbol", objAction("PrefLongPressAltSymbol"));
        Methods.put("ActionTrySearchInputActivateOnLetterHack", objAction("ActionTrySearchInputActivateOnLetterHack"));
        Methods.put("ActionSetNavModeHoldOffState", objAction("ActionSetNavModeHoldOffState"));
        Methods.put("ActionSetNavModeHoldOnState", objAction("ActionSetNavModeHoldOnState"));
        Methods.put("ActionTryVibrate", objAction("ActionTryVibrate"));
        Methods.put("InputIsAnyInput", objAction("InputIsAnyInput"));
        Methods.put("ActionChangeGestureAtInputModeUpAndDownMode", objAction("ActionChangeGestureAtInputModeUpAndDownMode"));
        Methods.put("ActionEnableGestureAtInputMode", objAction("ActionEnableGestureAtInputMode"));
        Methods.put("ActionSendCharDoublePressShiftMode", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharDoublePressShiftMode(p); } }, KeyPressData.class));
        //2.4
        Methods.put("ActionSetKeyTransparency", objAction("ActionSetKeyTransparency"));
        Methods.put("ActionToggleTransparencyMode", objAction("ActionToggleTransparencyMode"));
        Methods.put("InputIsInputFieldAndEnteredText", objAction("InputIsInputFieldAndEnteredText"));
        Methods.put("PrefEnsureEnteredText", objAction("PrefEnsureEnteredText"));
        Methods.put("InputIsDigitsPad", objAction("InputIsDigitsPad"));
        Methods.put("ActionResetDigitsHack", objAction("ActionResetDigitsHack"));
        Methods.put("ActionTryChangeGesturePointerModeAtViewMode", objAction("ActionTryChangeGesturePointerModeAtViewMode"));
        Methods.put("ActionTryChangeGestureModeStateAtInputMode", objAction("ActionTryChangeGestureModeStateAtInputMode"));
        Methods.put("ActionResetGesturePointerMode", objAction("ActionResetGesturePointerMode"));
        Methods.put("ActionTryChangeGestureInputScrollMode", objAction("ActionTryChangeGestureInputScrollMode"));
        Methods.put("ActionTryDisableGestureInputScrollMode", objAction("ActionTryDisableGestureInputScrollMode"));
        Methods.put("MetaIsViewMode", objAction("MetaIsViewMode"));
        Methods.put("ActionTryPerformClickCurrentNode", objAction("ActionTryPerformClickCurrentNode"));
        Methods.put("ActionTryPerformLongClickCurrentNode", objAction("ActionTryPerformLongClickCurrentNode"));
        Methods.put("ActionTryRemoveSelectedNodeRectangle", objAction("ActionTryRemoveSelectedNodeRectangle"));
        Methods.put("InputIsGestureCursor", objAction("InputIsGestureCursor"));
        Methods.put("MetaIsDisabled", objAction("MetaIsDisabled"));
        Methods.put("ActionTryDisableGestureCursorModeUnHoldState", objAction("ActionTryDisableGestureCursorModeUnHoldState"));
        Methods.put("ActionTryEnableGestureCursorModeOnHoldState", objAction("ActionTryEnableGestureCursorModeOnHoldState"));
        Methods.put("ActionDeletePrevWord", objAction("ActionDeletePrevWord"));
        Methods.put("MetaIsKey0Pressed", objAction("MetaIsKey0Pressed"));
        Methods.put("ActionSendCharFromAltPopup", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharFromAltPopup(p); } }, KeyPressData.class));
        Methods.put("ActionSendCharFromAltPopupAtSingleAltTriplePress", InitializeMethod3(new IActionMethod<KeyPressData>() { public boolean invoke(KeyPressData p) { return ActionSendCharFromAltPopupAtSingleAltTriplePress(p); } }, KeyPressData.class));
        Methods.put("ActionSendEnterOrCustomButton", objAction("ActionSendEnterOrCustomButton"));
        //2.5
        Methods.put("ActionTryResetHoldCtrlMode", objAction("ActionTryResetHoldCtrlMode"));
        //2.6
        Methods.put("ActionDisableFirstSymbolAltMode", objAction("ActionDisableFirstSymbolAltMode"));

        Methods.put("ActionStartVoiceListening", objAction("ActionStartVoiceListening"));
        Methods.put("ActionChangeBackKeyboardLayout", objAction("ActionChangeBackKeyboardLayout"));
        Methods.put("ActionWait300", objAction("ActionWait300"));
        Methods.put("ActionDeleteFwdWord", objAction("ActionDeleteFwdWord"));
        Methods.put("ActionDeleteUntilFwdCrLf", objAction("ActionDeleteUntilFwdCrLf"));
        Methods.put("ActionTryEnableGestureInputScrollMode", objAction("ActionTryEnableGestureInputScrollMode"));
        Methods.put("MetaIsSymPressed", objAction("MetaIsSymPressed"));
        Methods.put("ActionEnableHoldSymMode", objAction("ActionEnableHoldSymMode"));
        Methods.put("ActionDisableHoldSymMode", objAction("ActionDisableHoldSymMode"));
        Methods.put("TryDoTelegramRightDialogueExitHack", objAction("TryDoTelegramRightDialogueExitHack"));
        Methods.put("TryChangeSelectionStartDirection", objAction("TryChangeSelectionStartDirection"));

        //2.7
        Methods.put("ActionPasteTextFromClipboardByLetters", objAction("ActionPasteTextFromClipboardByLetters"));
        Methods.put("ActionMoveCursorPrevWord", InitializeMethod3(new IActionMethod<KeyEvent>() { public boolean invoke(KeyEvent p) { return ActionMoveCursorPrevWord(p); } }, KeyEvent.class));
        Methods.put("ActionMoveCursorFwdWord", InitializeMethod3(new IActionMethod<KeyEvent>() { public boolean invoke(KeyEvent p) { return ActionMoveCursorFwdWord(p); } }, KeyEvent.class));
        Methods.put("ActionSendNavKey", InitializeMethod3(new IActionMethod<NavActionData>() { public boolean invoke(NavActionData p) { return ActionSendNavKey(p); } }, NavActionData.class));

        Methods.put("ActionToggleTranslationMode", objActionKeyPress("ActionToggleTranslationMode"));
        Methods.put("ActionTogglePredictionBar", objActionKeyPress("ActionTogglePredictionBar"));




    }

    //endregion

    //region Processable2

    class Processable2 implements InputMethodServiceCoreKeyPress.Processable {
        @Override
        public boolean Process(KeyPressData keyPressData, KeyEvent keyEvent) {
            if (Actions == null || Actions.isEmpty())
                return true;
            try {
                for (KeyboardMechanics.Action action : Actions) {

                    if (action.MetaModeMethods != null && !action.MetaModeMethods.isEmpty()) {
                        boolean metaResult = true;
                        for (IActionMethod metaMethod : action.MetaModeMethods) {
                            metaResult &= metaMethod.invoke(Keyboard);;
                            if(!metaResult)
                                break;
                        }

                        if (metaResult) {
                            boolean result = InvokeMethod(keyPressData, action, keyEvent);
                            if (result && action.NeedUpdateVisualState)
                                Keyboard.ActionSetNeedUpdateVisualState();
                            if (action.NeedUpdateGestureVisualState)
                                Keyboard.ActionSetNeedUpdateGestureNotification();
                            if (action.StopProcessingAtSuccessResult && result) {
                                return true;
                            }
                        }
                    }
                }
                for (KeyboardMechanics.Action action : Actions) {

                    if (action.MetaModeMethods == null || action.MetaModeMethods.isEmpty()) {
                        boolean result = InvokeMethod(keyPressData, action, keyEvent);
                        if (result && action.NeedUpdateVisualState)
                            Keyboard.ActionSetNeedUpdateVisualState();
                        if (action.NeedUpdateGestureVisualState)
                            Keyboard.ActionSetNeedUpdateGestureNotification();
                        if (action.StopProcessingAtSuccessResult && result) {
                            return true;
                        }
                    }

                }
            } catch (Throwable ex) {
                Log.e(TAG2, "Can not Process Actions " + ex);
            }
            return true;
        }

        private boolean InvokeMethod(KeyPressData keyPressData, KeyboardMechanics.Action action, KeyEvent keyEvent) {
            boolean result;
            if (action.ActionMethod == null) {
                Log.e(TAG2, "action.ActionMethod == null; MethodName: " + action.ActionMethodName + " KeyCode: " + keyPressData.KeyCode);
                return false;
            }
            // Эта проверка должна быть до action.CustomKeyCode так как action.CustomKeyCode в нем используется тоже, но есть и без него вариант, отдельный
            if (action.MethodNeedsNavActionParameter) {
                result = action.ActionMethod.invoke(new NavActionData(action.CustomKeyCodeInt, keyEvent.getMetaState()));
            } else if (action.CustomKeyCodeInt > 0) {
                result = action.ActionMethod.invoke(action.CustomKeyCodeInt);
            } else if (action.CustomChar > 0) {
                result = action.ActionMethod.invoke(action.CustomChar);
            } else if (action.MethodNeedsKeyPressParameter) {
                result = action.ActionMethod.invoke(keyPressData);
            } else if (action.MethodNeedsKeyEventParameter) {
                result = action.ActionMethod.invoke(keyEvent);
            } else {
                result = action.ActionMethod.invoke(Keyboard);
            }

            return result;
        }


        ArrayList<KeyboardMechanics.Action> Actions = null;

        InputMethodServiceCoreCustomizable Keyboard = null;
    }

    //endregion

    //region CALL_MANAGER

    private void ShowDebugToast(String text) {
        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
        toast.show();
    }

    protected CallStateCallback callStateCallback;

    protected TelephonyManager telephonyManager;
    protected TelecomManager telecomManager;

    protected TelephonyManager getTelephonyManager() {
        return (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
    }

    protected TelecomManager getTelecomManager() {
        return (TelecomManager) this.getSystemService(Context.TELECOM_SERVICE);
    }

    private boolean IsCalling() {
        if (callStateCallback == null) {
            ShowDebugToast("callStateCallback == null");
            Log.e(TAG2, "callStateCallback == null");
            return false;
        }
        return callStateCallback.isCalling();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean AcceptCallOnCalling() {
        Log.d(TAG2, "AcceptCallOnCalling hello");
        if (telecomManager == null) {
            Log.e(TAG2, "telecomManager == null");
            ShowDebugToast("telecomManager == null");
        }
        if (!IsCalling())
            return false;
        // Accept calls using SHIFT key
        if (this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG2, "handleShiftOnCalling callStateCallback - Calling");
            //ShowDebugToast("ACCEPT-CALL");
            telecomManager.acceptRingingCall();
            return true;
        } else {
            Log.e(TAG2, "AcceptCallOnCalling no permission");
            return false;
        }
    }

    private boolean DeclinePhone() {
        if (telecomManager == null) {
            Log.e(TAG2, "telecomManager == null");
            ShowDebugToast("telecomManager == null");
            return false;
        }
        if (telephonyManager == null) {
            Log.e(TAG2, "telephonyManager == null");
            ShowDebugToast("telephonyManager == null");
            return false;
        }
        if (!IsCalling())
            return false;

        if (this.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG2, "DeclinePhone no permission");
            ShowDebugToast("DeclinePhone no permission");
            return false;

        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ShowDebugToast("END-CALL (A>=28)");
            return telecomManager.endCall();
        } else {

            try {
                //telephonyManager = getTelephonyManager();
                Class<?> classTelephony = Class.forName(telephonyManager.getClass().getName());
                Method methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony");
                methodGetITelephony.setAccessible(true);
                ITelephony telephonyService = (ITelephony) methodGetITelephony.invoke(telephonyManager);
                if (telephonyService != null) {
                    ShowDebugToast("END-CALL (A<=27:REFLECTION)");
                    boolean ret = telephonyService.endCall();
                    Log.d(TAG2, "END-CALL (A<=27:REFLECTION) RET: " + ret);
                    return ret;
                } else {
                    Log.e(TAG2, "telephonyService == null (reflection)");
                    ShowDebugToast("telephonyService == null (reflection)");
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("LOG", "Can't disconnect call");
                ShowDebugToast("Can't disconnect call " + e);
                return false;
            }
        }
    }



    //endregion


    //region Actions NAV-MODE

    public boolean ActionTryDisableNavModeAndKeyboard() {
        if (keyboardStateFixed_NavModeAndKeyboard) {
            keyboardStateFixed_NavModeAndKeyboard = false;
            keyboardStateFixed_SymbolOnScreenKeyboard = false;
            metaFixedModeFirstSymbolAlt = false;
            metaFixedModeAllSymbolsAlt = false;
            DetermineForceFirstUpper(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    int navSwitcherKeyCode = -1;

    public boolean ActionTryEnableNavModeAndKeyboard() {
        if (!keyboardStateFixed_NavModeAndKeyboard) {
            //Двойное нажание SYM -> Режим навигации
            keyboardStateFixed_NavModeAndKeyboard = true;
            keyboardStateFixed_FnSymbolOnScreenKeyboard = false;
            keyboardView.setFnNavMode(false);
            navSwitcherKeyCode = LastShortPressKey1.KeyCode;
            return true;
        }
        return false;
    }

    public boolean ActionSetNavModeHoldOnState() {
        navSwitcherKeyCode = KeyDownList1.get(KeyDownList1.size()-1).KeyCode;
        keyboardStateHolding_NavModeAndKeyboard = true;
        return true;
    }

    public boolean ActionSetNavModeHoldOffState() {
        keyboardStateHolding_NavModeAndKeyboard = false;
        return true;
    }

    protected void keyDownUpKeepTouch2(int keyEventCode, InputConnection ic, int meta) {
        if(IsInputMode()) {
            keyDownUp(keyEventCode, ic, meta, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        }
        else
            keyDownUp(keyEventCode, ic, meta,0);
    }

    public static class NavActionData {
        public NavActionData(int navKeyCode, int metaState) {
            NavKeyCode = navKeyCode;
            MetaState = metaState;
        }
        public int NavKeyCode;
        public int MetaState;
    }

    public boolean ActionSendNavKey(NavActionData navData) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null && navData.NavKeyCode != 0) {

            //Удаляем из meta состояния SYM т.к. он мешает некоторым приложениям воспринимать NAV символы с зажатием SYM
            int meta = navData.MetaState & (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);

            /** Это (Флаги KeepTouch) нужно чтобы в Telegram не выбивало курсор при движении курсора
             * С другой стороны надо передавать мета состояние чтобы работало сочетания Shift+Tab(SYM+A)
             */
            keyDownUpKeepTouch2(navData.NavKeyCode, inputConnection, meta);
            //ProcessOnCursorMovement(getCurrentInputEditorInfo());
            //keyDownUpMeta(navigationKeyCode, inputConnection, meta);
        }
        return true;
    }

    //endregion

    //region Actions CALL

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean ActionTryAcceptCall() {
        if (pref_manage_call && IsCalling()) {
            if (AcceptCallOnCalling()) return true;
        }
        return false;
    }

    public boolean ActionTryDeclineCall() {
        if (pref_manage_call && IsCalling()) {
            return DeclinePhone();
        }
        return false;
    }

    public boolean MetaStateIsOnCall() {
        if (pref_manage_call && IsCalling()) {
            return true;
        }
        return false;
    }


    //endregion



    //region Actions ALT-MODE
    public boolean ActionEnableHoldAltMode() {
        metaHoldAlt = true;
        return true;
    }

    public boolean ActionDisableHoldAltMode() {
        metaHoldAlt = false;
        DetermineForceFirstUpper(getCurrentInputEditorInfo());
        return true;
    }

    public boolean ActionChangeFirstSymbolAltMode() {
        metaFixedModeFirstSymbolAlt = !metaFixedModeFirstSymbolAlt;
        return true;
    }

    public boolean ActionTryDisableFirstSymbolAltMode() {
        if (metaFixedModeFirstSymbolAlt && !pref_alt_space) {
            metaFixedModeFirstSymbolAlt = false;
            return true;
        }
        return false;
    }

    public boolean ActionDisableFirstSymbolAltMode() {
        metaFixedModeFirstSymbolAlt = false;
        return true;
    }

    public boolean ActionChangeFixedAltModeState() {
        metaFixedModeFirstSymbolAlt = false;
        metaFixedModeAllSymbolsAlt = !metaFixedModeAllSymbolsAlt;
        return true;
    }

    public boolean ActionEnableFixedAltModeState() {
        metaFixedModeFirstSymbolAlt = false;
        metaFixedModeAllSymbolsAlt = true;
        return true;
    }

    public boolean ActionTryDisableFixedAltModeState() {
        if (metaFixedModeAllSymbolsAlt) {
            metaFixedModeAllSymbolsAlt = false;
            metaFixedModeFirstSymbolAlt = false;
            //TODO: NEW! Разобраться с обращениями к этому ресурсоемкому методу!
            DetermineForceFirstUpper(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableAltModeUponSpace() {
        if(metaFixedModeFirstSymbolAlt && pref_alt_space) {
            metaFixedModeFirstSymbolAlt = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions SHIFT-MODE

    public boolean ActionEnableHoldShiftMode() {
        metaHoldShift = true;
        return true;
    }

    public boolean ActionDisableHoldShiftMode() {
        metaHoldShift = false;
        return true;
    }

    public boolean ActionChangeFirstLetterShiftMode() {
        metaFixedModeFirstLetterUpper = !metaFixedModeFirstLetterUpper;
        return true;
    }

    public boolean ActionTryDisableFirstLetterShiftMode() {
        if (metaFixedModeFirstLetterUpper) {
            metaFixedModeFirstLetterUpper = false;
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableCapslockShiftMode() {
        if (metaFixedModeCapslock) {
            metaFixedModeCapslock = false;
            DetermineForceFirstUpper(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    public boolean ActionChangeShiftCapslockState() {
        metaFixedModeFirstLetterUpper = false;
        metaFixedModeCapslock = !metaFixedModeCapslock;
        return true;
    }

    //endregion

    //region Actions SYM-PAD

    public boolean ActionEnableHoldSymMode() {
        metaHoldSym = true;
        return true;
    }

    public boolean ActionDisableHoldSymMode() {
        metaHoldSym = false;
        return true;
    }

    public boolean ActionDisableNavSymFnKeyboard() {
        //Отключаем режим навигации
        keyboardStateFixed_NavModeAndKeyboard = false;
        keyboardStateFixed_FnSymbolOnScreenKeyboard = false;
        keyboardView.setFnNavMode(false);
        return true;
    }

    public boolean ActionTryChangeSymPadVisibilityAtInputMode() {
        if (IsInputMode()) {
            if (!keyboardStateFixed_SymbolOnScreenKeyboard) {
                keyboardStateFixed_SymbolOnScreenKeyboard = true;
                metaFixedModeAllSymbolsAlt = true;
                symPadAltShift = true;
                metaFixedModeFirstSymbolAlt = false;
            } else {
                symPadAltShift = false;
                keyboardStateFixed_SymbolOnScreenKeyboard = false;
                metaFixedModeFirstSymbolAlt = false;
                metaFixedModeAllSymbolsAlt = false;
                DetermineForceFirstUpper(getCurrentInputEditorInfo());
            }
            //TODO: Много лишних вызовов апдейта нотификаций
            return true;
        }
        return false;
    }


    public boolean ActionTryChangeSymPadLayout() {
        if (keyboardStateFixed_SymbolOnScreenKeyboard) {
            symPadAltShift = !symPadAltShift;
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableSymPad() {
        if (keyboardStateFixed_SymbolOnScreenKeyboard) {
            keyboardStateFixed_SymbolOnScreenKeyboard = false;
            symPadAltShift = false;
            metaFixedModeFirstSymbolAlt = false;
            metaFixedModeAllSymbolsAlt = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions CTRL-MODE

    public boolean ActionEnableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaHoldCtrl = true;

        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(
                        keyPressData.BaseKeyEvent.getDownTime(),
                        keyPressData.BaseKeyEvent.getEventTime(),
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    public boolean ActionTryResetHoldCtrlMode() {
        if(metaHoldCtrl) {
            metaHoldCtrl = false;
            return true;
        }
        return false;
    }

    public boolean ActionDisableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaHoldCtrl = false;
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                keyPressData.BaseKeyEvent.getDownTime(),
                keyPressData.BaseKeyEvent.getEventTime(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    //endregion

    //region Actions Search-Hack

    public boolean ActionTryResetSearchPlugin() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (SearchPluginLauncher != null
                && !editorInfo.packageName.equals(SearchPluginLauncher.PackageName)) {
            SetSearchHack(null);
            return true;
        }
        return false;
    }

    public boolean ActionTrySearchInputActivateOnLetterHack() {
        if (IsInputMode() && isInputViewShown() && SearchPluginLauncher != null) {
            Log.d(TAG2, "NO_FIRE SearchPluginAction INPUT_MODE");
            SetSearchHack(null);
            return true;
        }
        if (SearchPluginLauncher != null) {
            Log.d(TAG2, "FIRE SearchPluginAction!");
            SearchPluginLauncher.FirePluginAction();
            SetSearchHack(null);
            return true;
        }
        return false;
    }

    //endregion

    //region Action KEYs

    public boolean ActionKeyDown(int customKeyCode) {
        return super.ActionKeyDown(customKeyCode);
    }

    protected boolean _isKeyTransparencyInsideUpDownEvent = false;

    //Метод onKeyDown и onKeyUp вернут false как будто бы он ничего не делал
    //Это нужно например для KEYCODE_HOME который нет возможности (пока) эмулировать программно
    public boolean ActionSetKeyTransparency() {
        _isKeyTransparencyInsideUpDownEvent = true;
        return true;
    }

    boolean GetTransparencyForCurrentApp() {
        if (_lastPackageName == null || _lastPackageName.isEmpty())
            return pref_transparency_mode;
        String prefName = TRANSPARENCY_MODE_PREFIX + _lastPackageName;
        String stored = k12KbSettings.GetStringValue(prefName);
        if (stored.isEmpty())
            return pref_transparency_mode;
        return Boolean.parseBoolean(stored);
    }

    public boolean ActionToggleTransparencyMode() {
        _currentAppTransparency = !_currentAppTransparency;
        if (_lastPackageName != null && !_lastPackageName.isEmpty()) {
            k12KbSettings.SetStringValue(
                TRANSPARENCY_MODE_PREFIX + _lastPackageName,
                String.valueOf(_currentAppTransparency));
        }
        Toast.makeText(getApplicationContext(),
            _currentAppTransparency ? "Transparency ON" : "Transparency OFF",
            Toast.LENGTH_SHORT).show();
        return true;
    }

    public boolean ActionKeyDownUpDefaultFlags(int customKeyCode) {
        return super.ActionKeyDownUpDefaultFlags(customKeyCode);
    }

    public boolean ActionKeyDownUpNoMetaKeepTouch(int keyEventCode) {
        super.ActionCustomKeyDownUpNoMetaKeepTouch(keyEventCode);
        return true;
    }

    //endregion


    //region Actions Misc. InputMode

    public boolean ActionSendEnterOrCustomButton() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        ei.makeCompatible(27);

        switch (ei.imeOptions & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        {
            case EditorInfo.IME_ACTION_SEND:
                ic.performEditorAction(EditorInfo.IME_ACTION_SEND);
                return true;
            case EditorInfo.IME_ACTION_GO:
                ic.performEditorAction(EditorInfo.IME_ACTION_GO);
                return true;
            case EditorInfo.IME_ACTION_NEXT:
                ic.performEditorAction(EditorInfo.IME_ACTION_NEXT);
                return true;
            case EditorInfo.IME_ACTION_SEARCH:
                ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
                return true;
        }

        _isKeyTransparencyInsideUpDownEvent = true;
        return true;
    }

    public boolean ActionSendCharToInput(char char1) {
        SendLetterOrSymbol(char1);
        return true;
    }

    public boolean ActionTryCapitalizeFirstLetter() {
        return DetermineForceFirstUpper(getCurrentInputEditorInfo());
    }


    public boolean ActionDeletePreviousSymbol() {
        DeleteLastSymbol();
        return true;
    }

    public boolean ActionDeleteUntilPrevCrLf() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int dist1 = findPrevEnterDistance(c);
        //Это первый абзац в тексте
        if (dist1 == 0 && c.length() > 0)
            dist1 = c.length();
        else if(dist1 > 1) dist1--;
        if (dist1 > 0) {
            inputConnection.deleteSurroundingText(dist1, 0);
        }
        return true;
    }

    public boolean ActionDeleteUntilFwdCrLf() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextAfterCursor(Integer.MAX_VALUE - Character.MAX_VALUE, 0);
        int dist1 = findFwdEnterDistance(c);
        //Последний абзац в тексте
        if(dist1 == 0 && c.length() > 0)
            dist1 = c.length();
        else if(dist1 > 1) dist1--;
        if (dist1 > 0) {
            inputConnection.deleteSurroundingText(0, dist1);
        }
        return true;
    }

    public boolean ActionDeletePrevWord() {
        InputConnection inputConnection = getCurrentInputConnection();
        int dist = findPrevWordOrOtherLen();

        if (dist > 0) {
            inputConnection.deleteSurroundingText(dist, 0);
        }
        return true;
    }



    private int findPrevWordOrOtherLen() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int dist1 = findPrevEnterDistance(c);
        //Это первый абзац в тексте
        if (dist1 == 0 && c.length() > 0)
            dist1 = c.length();
        else if(dist1 > 1) dist1--;
        int nearWhitespaceCount = 0;
        for (int i = c.length() - 1; i >= 0; i--) {
            if(isWhitespace(c.charAt(i)))
                nearWhitespaceCount++;
            else
                break;
        }
        return FindMinGreater0(AllBackWhiteSpaceDistArray(c, nearWhitespaceCount), dist1);
    }

    private int findFwdWordOrOtherLen() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextAfterCursor(Integer.MAX_VALUE - Character.MAX_VALUE, 0);
        int dist1 = findFwdEnterDistance(c);
        //Последнее слово в тексте
        if(dist1 == 0 && c.length() > 0)
            dist1 = c.length();
        else if(dist1 > 1) dist1--;
        int nearWhitespaceCount = 0;
        for (int i = 0; i < c.length(); i++) {
            if(isWhitespace(c.charAt(i)))
                nearWhitespaceCount++;
            else
                break;
        }
        return FindMinGreater0(AllFwdWhiteSpaceDistArray(c, nearWhitespaceCount), dist1);
    }

    private int FindMinGreater0(Integer[] arr, int firstDist) {
        arr[0] = firstDist;
        java.util.Arrays.sort(arr, new Comparator<Integer>() {
            @Override
            public int compare(Integer object1, Integer object2) {
                if(object1 == 0)
                    return object2;
                return object1.compareTo(object2);
            }
        });
        for (int i = 0; i < arr.length; i++) {
            if(arr[i] == 0)
                continue;
            return arr[i];
        }
        return 0;
    }

    char[] WHITESPACECHARS = {' ', '.', ',', '\t', ':', ';', '?', '!'};

    private Integer[] AllBackWhiteSpaceDistArray(CharSequence cs, int nearWhiteSpace) {
        Integer[] rez = new Integer[WHITESPACECHARS.length+1];
        for(int i = 1, j = 0; i <= WHITESPACECHARS.length; i++, j++) {
            int r1 = findPrevCharDistance(cs, WHITESPACECHARS[j], nearWhiteSpace);
            rez[i] = r1;
        }
        return rez;
    }

    private Integer[] AllFwdWhiteSpaceDistArray(CharSequence cs, int nearWhiteSpace) {
        Integer[] rez = new Integer[WHITESPACECHARS.length+1];
        for(int i = 1, j = 0; i <= WHITESPACECHARS.length; i++, j++) {
            int r1 = findFwdCharDistance(cs, WHITESPACECHARS[j], nearWhiteSpace);
            rez[i] = r1;
        }
        return rez;
    }

    boolean isWhitespace(char c1) {
        for(int i = 1, j = 0; i <= WHITESPACECHARS.length; i++, j++) {
            if(WHITESPACECHARS[j] == c1)
                return true;
        }
        return false;
    }

    public boolean ActionDeleteFwdWord() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextAfterCursor(Integer.MAX_VALUE - Character.MAX_VALUE, 0);
        int dist = findFwdWordOrOtherLen();

        if (dist <= c.length()) {
            inputConnection.deleteSurroundingText(0, dist);
        }
        return true;
    }

    public boolean ActionMoveCursorPrevWord(KeyEvent event) {
        InputConnection inputConnection = getCurrentInputConnection();

        ExtractedTextRequest request1 = new ExtractedTextRequest();
        ExtractedText extractedText1 = inputConnection.getExtractedText(request1, 0);
        int start1 = extractedText1.selectionStart;
        int end1 = extractedText1.selectionEnd;
        if(Math.abs(start1-end1) > 0) {
            inputConnection.setSelection(end1, end1);
        }

        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int dist = findPrevWordOrOtherLen();
        if(Math.abs(start1-end1) > 0) {
            inputConnection.setSelection(start1, end1);
        }
        if (dist >= 0) {
            if(MetaIsShiftPressed() || (event.getMetaState() & KeyEvent.META_SHIFT_LEFT_ON) != 0) {
                ExtractedTextRequest request = new ExtractedTextRequest();
                ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
                int start = extractedText.selectionStart;
                int end = extractedText.selectionEnd;
                Log.d(TAG2, String.format("SELECTION BEFORE START: %d END: %d", start, end));
                inputConnection.setSelection(start1, end1-dist);
            }
            else {
                dist = c.length() - dist;
                inputConnection.setSelection(dist, dist);
            }
        }
        return true;
    }

    public boolean ActionMoveCursorFwdWord(KeyEvent event) {
        InputConnection inputConnection = getCurrentInputConnection();

        ExtractedTextRequest request1 = new ExtractedTextRequest();
        ExtractedText extractedText1 = inputConnection.getExtractedText(request1, 0);
        int start1 = extractedText1.selectionStart;
        int end1 = extractedText1.selectionEnd;
        if(Math.abs(start1-end1) > 0) {
            inputConnection.setSelection(end1, end1);
        }

        CharSequence c = inputConnection.getTextAfterCursor(Integer.MAX_VALUE - Character.MAX_VALUE, 0);
        int dist = findFwdWordOrOtherLen();
        if(Math.abs(start1-end1) > 0) {
            inputConnection.setSelection(start1, end1);
        }
        if (dist <= c.length()) {
            CharSequence c2 = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);

            if(MetaIsShiftPressed()  || (event.getMetaState() & KeyEvent.META_SHIFT_LEFT_ON) != 0) {
                ExtractedTextRequest request = new ExtractedTextRequest();
                ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
                int start = extractedText.selectionStart;
                int end = extractedText.selectionEnd;
                //this.getCurrentInputEditorInfo().initialSelStart
                Log.d(TAG2, String.format("SELECTION BEFORE START: %d END: %d", start, end));
                inputConnection.setSelection(start1, end1+dist);
            }
            else {
                dist = dist + c2.length();
                inputConnection.setSelection(dist, dist);
            }
        }
        return true;
    }

    public boolean TryChangeSelectionStartDirection() {
        if(!IsInputMode())
            return false;
        InputConnection inputConnection = getCurrentInputConnection();
        ExtractedTextRequest request = new ExtractedTextRequest();
        ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
        int start = extractedText.selectionStart;
        int end = extractedText.selectionEnd;
        if(Math.abs(start-end) == 0)
            return false;
        inputConnection.setSelection(end, start);
        return true;
    }

     public boolean ActionUnCrLf() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int pos = findPrevEnterAbsPos(c);
        inputConnection.setSelection(pos, pos);
        return true;
    }

    public boolean ActionTryDoubleSpaceDotSpaceConversion() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence back_letter = inputConnection.getTextBeforeCursor(2, 0);
        Log.d(TAG2, "KEYCODE_SPACE back_letter " + back_letter);
        if (back_letter.length() == 2 && Character.isLetterOrDigit(back_letter.charAt(0)) && back_letter.charAt(1) == ' ') {
            inputConnection.deleteSurroundingText(1, 0);
            inputConnection.commitText(". ", 2);
            return true;
        }
        return false;
    }

    //endregion

    public void SetCurrentNodeInfo(AsNodeClicker info) {
        CurrentNodeInfo = info;
    }

    //region Actions OTHER

    AsNodeClicker CurrentNodeInfo;

    interface AsNodeClicker {
        void Click(boolean isLongClick);
    }

    public boolean ActionTryRemoveSelectedNodeRectangle() {
        if(K12KbAccessibilityService.Instance != null) {
            return K12KbAccessibilityService.Instance.TryRemoveRectangle();
            //return true;
        }
        return false;
    }


    public boolean ActionTryPerformClickCurrentNode() {
        if(_modeGestureAtViewMode == GestureAtViewMode.Pointer || IsNavMode()) {
            if (CurrentNodeInfo != null) {
                CurrentNodeInfo.Click(false);
            }
            return true;
        }
        return false;
    }

    public boolean ActionTryPerformLongClickCurrentNode() {
        if(_modeGestureAtViewMode == GestureAtViewMode.Pointer || IsNavMode()) {
            if (CurrentNodeInfo != null) {
                CurrentNodeInfo.Click(true);
            }
            return true;
        }
        return false;
    }

    public boolean ActionResetDigitsHack() {
        SetDigitsHack(false);
        return true;
    }

    public boolean ActionTryVibrate() {
        if(pref_vibrate_on_key_down && vibratorService != null) {
            Vibrate(TIME_VIBRATE);
            return true;
        }
        return false;
    }

    //SAME AS: "need-update-visual-state": true,
    public boolean ActionSetNeedUpdateVisualState() {
        needUpdateVisualInsideSingleEvent = true;
        return true;
    }

    //TODO: Блин вот хоть убей не помню нафига этот хак, но помню что без него что-то не работало (возвожно на К1)
    public boolean ActionTryChangeKeyboardLayoutAtBaseMetaShift(KeyPressData keyPressData) {
        if(!IsShiftMeta(keyPressData.MetaBase))
            return false;
        return ActionChangeKeyboardLayout();
    }



    public boolean ActionChangeSwipePanelVisibility() {
        if (keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = false;
            UpdateKeyboardVisibilityOnPrefChange();
        } else if (!keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = true;
            UpdateKeyboardVisibilityOnPrefChange();
        }
        return true;
    }



    public boolean ActionDoNothing() {
        return true;
    }

    public boolean ActionWait300() {
        FileJsonUtils.SleepWithWakes(300);
        return true;
    }



    public boolean ActionChangeKeyboardLayout() {
        ChangeLanguage();
        return true;
    }

    public boolean ActionChangeBackKeyboardLayout() {
        ChangeLanguageBack();
        return true;
    }

    public boolean ActionPasteTextFromClipboardByLetters() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        CharSequence cs = clipboard.getText();
        int len = cs.length();
        if(len <= 0)
            return false;
        for(int i = 0; i < len; i++) {
            char c1 = cs.charAt(i);
            sendKeyChar(c1);
        }
        return true;
    }


    //endregion

    //region Actions LETTERS (CHARS)

    public boolean ActionSendCtrlPlusKey(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        //Для K1 надо очищать мета статус от всего лишено, оставляем только shift для Ctrl+Shift+Z;
        int metaBase =  (keyPressData.MetaBase & KeyEvent.META_SHIFT_LEFT_ON) > 0 ? KeyEvent.META_SHIFT_LEFT_ON : 0;
        Log.i(TAG2, "Sending CTRL+"+keyPressData.KeyCode+" SHIFT:"+metaBase);

        InputConnection ic = getCurrentInputConnection();

        if(CurrentSelectionEnd == 0 && CurrentSelectionStart > 0) {
            //В Unihertz BUG если выделять с конца влево до упора, то в SEL END становится 0 и не работает ctrl+c/ctrl+x и надо поменять местами начало выделения и конец
            Log.d(TAG2, "Selection END=0, changing SEL_END and SEL_START");
            ic.setSelection(CurrentSelectionEnd, CurrentSelectionStart);
        }

        keyDownUpKeepTouch(keyPressData.KeyCode, ic, meta | metaBase);

        return true;
    }

    protected int CurrentSelectionStart = 0;
    protected int CurrentSelectionEnd = 0;


    public boolean ActionSendCharSinglePressSymMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharSinglePressShiftMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        SendLetterOrSymbol(code2send);
        //keyboardLayoutManager.ScanCodeKeyCodeMapping.put(keyPressData.ScanCode, keyPressData.KeyCode);
        return true;
    }

    public boolean ActionSendCharSinglePressAltMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharSinglePressNoMeta(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharDoublePressNoMeta(KeyPressData keyPressData) {
        return SendCharDoublePress(keyPressData, false);
    }

    public boolean ActionSendCharDoublePressShiftMode(KeyPressData keyPressData) {
        return SendCharDoublePress(keyPressData, true);
    }

    private boolean SendCharDoublePress(KeyPressData keyPressData, boolean isShiftMode) {
        //TODO: Подумать как сделать эту логику более кастомизиремым через keyboard_mechanics
        int code2send;
        int letterBeforeCursor = GetLetterBeforeCursor();
        /*
        // Для случая SYM символовол по double-press
        int letterAlted = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        if(letterBeforeCursor == letterAlted) {
            DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
            SendLetterOrSymbol(code2send);
            return true;
        }
*/

        boolean wasAltSymbol = false;
        int letterAlted = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        if(letterBeforeCursor == letterAlted) {
            wasAltSymbol = true;
        }

        if(IsNotPairedLetter(keyPressData)) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, isShiftMode, false);
            SendLetterOrSymbol(code2send);
            return true;
        }

        boolean needShift = false;
        //Определяем была ли первая из сдвоенных букв Заглавной
        int letterShifted = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        if(letterBeforeCursor == letterShifted)
            needShift = true;

        if(!wasAltSymbol) {
            DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, needShift, true);
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, needShift, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressCapitalize(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, true);
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressCapitalizeAltMode(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, true);
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressAltSymbolNoMeta(KeyPressData keyPressData) {
        int code2send;

        if(keyPressData.Short2ndLongPress ) {
            if (IsNotPairedLetter(keyPressData))
                DeleteLastSymbol();
            //Сначала обратаываем случай, когда уже случился ввод ALT-символа через SinglePressAltMode
            //В этом случае надо переделать этот символ в ALT-POPUP-символ
            int letterBeforeCursor = GetLetterBeforeCursor();
            int letterAlted = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
            if(letterAlted == letterBeforeCursor) {
                code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData);
                if (code2send == 0) {
                    code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
                }
            } else {
                code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
            }

        } else {

            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        }

        DeleteLastSymbol();
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressAltSymbolShiftMode(KeyPressData keyPressData) {
        int code2send;

        if(keyPressData.Short2ndLongPress ) {
            DeleteLastSymbol();
            if (IsNotPairedLetter(keyPressData))
                DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);

        } else {
            DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }

        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharFromAltPopup(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData);
        if (code2send == 0) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharFromAltPopupAtSingleAltTriplePress(KeyPressData keyPressData) {
        int code2send;
        int letterBeforeCursor = GetLetterBeforeCursor();
        int letterAltShifted = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        if(letterAltShifted == letterBeforeCursor) {
            code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData);
            if (code2send != 0) {
                DeleteLastSymbol();
                SendLetterOrSymbol(code2send);
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean ActionSendCharLongPressAltSymbolAltMode(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            //if(!MetaIsAltMode()) //для случая с triple-press
            DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData);
            if (code2send == 0) {
                code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
            }
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }



    //endregion

    //region PREFERENCES

    public boolean PrefLongPressAltSymbol() {
        return pref_long_press_key_alt_symbol;
    }

    public boolean PrefEnsureEnteredText() {
        return pref_ensure_entered_text;
    }

    //endregion

    //region META

    public boolean MetaIsKey0Pressed() {
        return gestureCursorAtInputEnabledByHold;
    }

    public abstract boolean MetaIsPackageChanged();
    
    public boolean MetaIsDisabled() {return false;}

    public boolean MetaIsActionBeforeMeta() {
        return true;
    }

    public boolean MetaIsShiftPressed() {

        return metaHoldShift;
    }

    public boolean MetaIsCtrlPressed() {

        return metaHoldCtrl;
    }

    public boolean MetaIsShiftMode() {

        return metaFixedModeFirstLetterUpper || metaFixedModeCapslock || metaHoldShift;
    }

    public boolean MetaIsAltMode() {
        return metaFixedModeFirstSymbolAlt || metaFixedModeAllSymbolsAlt || metaHoldAlt;
    }

    public boolean MetaIsAltPressed() {
        return metaHoldAlt;
    }

    public boolean MetaIsSymPressed() {
        return metaHoldSym;
    }


    public boolean MetaIsSymPadAltShiftMode() {
        return keyboardStateFixed_SymbolOnScreenKeyboard && symPadAltShift;
    }

    //endregion

    //region INPUT_TYPE

    public boolean InputIsGestureCursor() {
        return mode_gesture_cursor_at_input_mode;
    }

    public boolean InputIsDigitsPad() {
        return _digitsHackActive;
    }

    public boolean InputIsInputFieldAndEnteredText() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if(editorInfo == null) return false;
        if(editorInfo.inputType <= 0) return false;
        InputConnection ic = super.getCurrentInputConnection();
        if(ic == null) return false;
        CharSequence c = ic.getTextBeforeCursor(1, 0);
        if(c != null && c.length() > 0) return true;
        c = ic.getTextAfterCursor(1, 0);
        if(c != null && c.length() > 0) return true;
        return false;
    }

    public boolean InputIsAnyInput() {
        return IsInputMode();
    }

    public boolean MetaIsViewMode() {
        return !IsInputMode();
    }


    public boolean InputIsNumber() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;
    }

    public boolean InputIsPhone() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public boolean InputIsDate() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_DATETIME;
    }

    public boolean InputIsText() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    //endregion

    //region Voice Recognition

    protected VoiceRecognitionTrigger mVoiceRecognitionTrigger;
    public long LastOnDraw = 0;

    public boolean ActionStartVoiceListening() {
        if(!IsInputMode())
            return false;
        long methodCallTime = SystemClock.uptimeMillis();
        if (K12KbAccessibilityService.Instance != null && K12KbAccessibilityService.Instance.CurFocus != null) {

            Log.v(TAG2, "info.performAction(AccessibilityNodeInfo.ACTION_CLICK)");
            boolean answer = K12KbAccessibilityService.Instance.CurFocus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            //Для случая уезжающего окна поиска как в Яндекс.Навигаторе плагин хватает поле, которое уже не существует
            if (!answer) {
                Log.e(TAG2, "info.performAction(AccessibilityNodeInfo.ACTION_CLICK) == false");
                return false;
            }

            FileJsonUtils.SleepWithWakes(250);
        }

        if(
               mVoiceRecognitionTrigger != null
           && mVoiceRecognitionTrigger.isInstalled()
           && mVoiceRecognitionTrigger.isEnabled()) {
            Log.v(TAG2, "mVoiceRecognitionTrigger.startVoiceRecognition()");

            mVoiceRecognitionTrigger.startVoiceRecognition();
            return true;
        }
        return false;
    }

    protected void updateVoiceImeStatus() {
    }

    //endregion

    //
    public boolean TryDoTelegramRightDialogueExitHack() {
        //На случай модификацией-клонов телеграмма, которые меняют последнюю букву пакета + .web
        if(!_lastPackageName.contains("org.telegram.messenge"))
            return false;
        keyDownUp(KeyEvent.KEYCODE_TAB, getCurrentInputConnection(), 0,KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        return true;
    }

    //region end all the rest -->
    //endregion

    private void SendLetterOrSymbol(int code2send) {


        BeforeSendChar.Process(null, null);

        Log.v(TAG2, SystemClock.uptimeMillis()+" KEY SEND: "+String.format("%c", code2send));
        sendKeyChar((char) code2send);

        AfterSendChar.Process(null, null);

        if (wordPredictor != null && wordPredictor.isEnabled()) {
            ShowDictLoadingToast();
            wordPredictor.onCharacterTyped((char) code2send);
        }
    }



    protected abstract void UpdateKeyboardModeVisualization(boolean updateSwipePanelData);



    protected abstract void ChangeLanguage();
    protected abstract void ChangeLanguageBack();

    protected abstract void UpdateKeyboardVisibilityOnPrefChange();

    protected boolean IsShiftMeta(int meta) {
        return (meta & ( KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON)) > 0;
    }

    protected boolean IsNavMode() {
        return keyboardStateFixed_NavModeAndKeyboard || keyboardStateHolding_NavModeAndKeyboard;
    }

    boolean _digitsHackActive = false;

    public void SetDigitsHack(boolean value) {
        if(_digitsHackActive != value) {
            Log.d(TAG2, "DIGITS-PAD-HACK SET="+value);
            _digitsHackActive = value;
            //TODO: Это сделано тут в обход keyboard_mechanics т.к. определение номеронабирателя и вызов метода происходит после onStartInput
            if(value)
                UpdateKeyboardModeVisualization();
        }
    }
    public void SetSearchHack(SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData) {
        if(SearchPluginLauncher == null && searchPluginLaunchData == null)
            return;
        if((IsInputMode() || isInputViewShown()) && searchPluginLaunchData != null) {
            Log.d(TAG2, "SetSearchHack IS NOT SET IsInputMode()=true");
            return;
        }
        if(SearchPluginLauncher != null && searchPluginLaunchData == null) {
            Log.d(TAG2, "SetSearchHack NULL");
        } else if(SearchPluginLauncher != null && !SearchPluginLauncher.Equals(searchPluginLaunchData)) {
            Log.d(TAG2, "SetSearchHack CHANGE");
        } else {
            Log.d(TAG2, "SetSearchHack NEW");
        }
        SearchPluginLauncher = searchPluginLaunchData;
    }
    public SearchClickPlugin.SearchPluginLauncher SearchPluginLauncher;


    private boolean IsNotPairedLetter(KeyPressData keyPressData) {
        //TODO: По сути - это определение сдвоенная буква или нет, наверное можно как-то оптимальнее сделать потом
        int code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, true);
        if(code2send == 0)
            return true;
        int code2sendNoDoublePress = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        return code2send == code2sendNoDoublePress;
    }

    //TODO: Иногда вызывается по несколько раз подряд (видимо из разных мест)
    protected boolean DetermineForceFirstUpper(EditorInfo editorInfo) {
        //Log.d(TAG2, "DetermineForceFirstUpper");
        //Если мы вывалились из зоны ввода текста
        //NOTE: Проверка не дает вводить Заглавную прям на первом входе в приложение. Видимо не успевает еще активироваться.
        //if(!isInputViewShown())
        //    return;

        if(!pref_auto_capitalization)
            return false;

        if(editorInfo == null)
            return false;


        if (MetaIsAltMode()
                || metaFixedModeCapslock)
            return false;

        int makeFirstBig = 0;
        if (editorInfo.inputType != InputType.TYPE_NULL) {
            Log.d(TAG2, "getCursorCapsMode(editorInfo.inputType)");
            makeFirstBig = getCurrentInputConnection().getCursorCapsMode(editorInfo.inputType);
        }

        if(makeFirstBig != 0){
            if(!metaFixedModeFirstLetterUpper) {
                metaFixedModeFirstLetterUpper = true;
                Log.d(TAG2, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = true");
                return true;
            }
        }else {
            //makeFirstBig == 0
            if(metaFixedModeFirstLetterUpper) {
                metaFixedModeFirstLetterUpper = false;
                Log.d(TAG2, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = false");
                return true;
            }
        }
        return false;
    }

    int findPrevEnterAbsPos(CharSequence c) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int len = c.length();
        if(c.charAt(len - 1) == '\r' || c.charAt(len-1) == '\n') {
            len--;
        }
        for(int i = len; i > 0; i--) {
            if(c.charAt(i-1) == '\r' || c.charAt(i-1) == '\n')
                return i;
        }
        return 0;
    }

    int findPrevEnterDistance(CharSequence c) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int r_dist = findPrevCharDistance(c, '\r', 0);
        int n_dist = findPrevCharDistance(c, '\n', 0);
        if(r_dist == 0 && n_dist == 0) return 0;
        if(r_dist > 0 && n_dist == 0) return r_dist;
        if(r_dist == 0 && n_dist > 0) return n_dist;
        if(r_dist > n_dist) return n_dist;
        return r_dist;
    }

    int findFwdEnterDistance(CharSequence c) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int r_dist = findFwdCharDistance(c, '\r', 0);
        int n_dist = findFwdCharDistance(c, '\n', 0);
        if(r_dist == 0 && n_dist == 0) return 0;
        if(r_dist > 0 && n_dist == 0) return r_dist;
        if(r_dist == 0 && n_dist > 0) return n_dist;
        if(r_dist > n_dist) return n_dist;
        return r_dist;
    }

    int findPrevCharDistance(CharSequence c, char c1, int nearWhitespace) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int len = c.length();
        for(int i = len - nearWhitespace; i > 0; i--) {
            if(c.charAt(i - 1) == c1)
                return len - i + 1;
        }
        return 0;
    }

    int findFwdCharDistance(CharSequence c, char c1, int nearWhitespace) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int len = c.length();
        for(int i = nearWhitespace; i < len ; i++) {
            if(c.charAt(i) == c1)
                return i + 1; //Включая сам символ
        }
        return 0;
    }

    private void DeleteLastSymbol() {
        InputConnection inputConnection = getCurrentInputConnection();
        if(inputConnection!=null) {
            inputConnection.deleteSurroundingText(1, 0);
        }
        if (wordPredictor != null && wordPredictor.isEnabled()) {
            ShowDictLoadingToast();
            wordPredictor.onBackspace();
        }
    }

    private int GetLetterBeforeCursor() {
        InputConnection inputConnection = getCurrentInputConnection();
        if(inputConnection!=null) {
            CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
            if (c.length() > 0)
                return c.charAt(0);
        }
        return 0;
    }

    private void Vibrate(int ms) {

        if(vibratorService == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibratorService.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            vibratorService.vibrate(ms);
        }
    }
}
