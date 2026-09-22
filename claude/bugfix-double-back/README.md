# Двойной Back в Firefox — исправлено

21.09.2026. Устройство `10.20.0.3:54321`, BlackBerry KEY2 с Android 16
(LineageOS 23.2). Установленная клавиатура — `com.ai10.k12kb`, `v4.0d`,
`versionCode 3013`. Пользователь воспроизвёл проблему до установки исправления
и после установки подтвердил: «fixed».

## Симптом и причина

Одно нажатие Back в Firefox давало двойной возврат. Обход Back для Android 16
в `K12KbAccessibilityService.onKeyEvent` перехватывал исходное нажатие,
вызывал `requestHideSelf(0)`, затем отправлял новый Back через
`performGlobalAction(GLOBAL_ACTION_BACK)`.

В обходе были две связанные ошибки:

1. Условие проверяло `IsInputMode()`, но не видимость панели. Firefox мог
   сохранять привязку редактора при уже скрытом окне IME. В этом случае
   `RunWhenInputViewHidden` выполнял перевыпуск сразу: скрывать было нечего.
2. В течение 250 мс после перевыпуска **любой** Back пропускался через
   `return false`. Туда попадал и `ACTION_UP` исходного нажатия. Комментарий
   в конце обхода обещал поглотить DOWN и UP, но фактически UP мог уйти
   приложению после уже отправленного заменяющего Back. Изменение режима
   ввода при скрытии панели также могло вывести UP из условия обхода.

## Что показала трасса

Сбор шёл в файл без инъекции нажатий и без переключения клавиатуры:

```sh
adb -s 10.20.0.3:54321 logcat -v time \
  ImeTracker:V InsetsController:V InputDispatcher:W \
  K12Kb-AS:D K12Kb-IME:V '*:S'
```

Характерные события 21 сентября (время устройства):

```text
13:06:32.294 com.ai10.k12kb: onRequestHide ORIGIN_IME HIDE_SOFT_INPUT_FROM_IME
13:06:32.298 Firefox: onCancelled PHASE_CLIENT_ALREADY_HIDDEN
13:06:32.429 K12Kb-IME: NO KEY_DOWN AT K12KB. FOREIGN (?) KEY. KEY_CODE: 4 IGNORING.

13:06:46.250 com.ai10.k12kb: onRequestHide ORIGIN_IME HIDE_SOFT_INPUT_FROM_IME
13:06:46.253 Firefox: onCancelled PHASE_CLIENT_ALREADY_HIDDEN
13:06:46.378 K12Kb-IME: NO KEY_DOWN AT K12KB. FOREIGN (?) KEY. KEY_CODE: 4 IGNORING.
```

То есть панель уже скрыта, а через 128–135 мс приходит Back без парного DOWN
в IME — внутри окна пропуска в 250 мс. Это согласуется с утечкой исходного UP.
Релиз вырезает подробные D/V/I-логи K12KB; трасса не содержит полной пары
KeyEvent с `downTime`/`deviceId`, поэтому идентичность события устанавливалась
по коду и последовательности, а не по полному дампу событий.

## Точная правка

Файл: [K12KbAccessibilityService.java](../../app/src/main/java/com/ai10/k12kb/K12KbAccessibilityService.java).

Добавлены поля рядом с `lastBackResendUptime`:

```java
private long consumedBackDownTime = -1;
private int consumedBackDeviceId;
```

В начале `onKeyEvent`, после пропуска HOME/APP_SWITCH, но **до** проверки
режима ввода, видимости и окна в 250 мс, добавлено:

```java
if (kc == KeyEvent.KEYCODE_BACK
        && event.getDownTime() == consumedBackDownTime
        && event.getDeviceId() == consumedBackDeviceId) {
    if (event.getAction() == KeyEvent.ACTION_UP)
        consumedBackDownTime = -1;
    return true;
}
```

При перехвате первого DOWN, до `backResendPending = true` и запроса скрытия,
запоминается исходное нажатие:

```java
consumedBackDownTime = event.getDownTime();
consumedBackDeviceId = event.getDeviceId();
```

Теперь исходные повторные события и UP поглощаются независимо от того,
закрылось ли поле ввода и был ли уже отправлен заменяющий Back. Его события
имеют другую идентичность и не поглощаются этой проверкой.

В условие Android 16 обхода добавлена проверка видимости:

```java
&& K12KbIME.Instance.IsInputMode()
&& K12KbIME.Instance.isInputViewShown()
```

При скрытой панели hide-and-resend не запускается; Back идёт по обычному
пути. Механизм ожидания `onFinishInputView`, страховка 700 мс и окно 250 мс
сохранены. Телеграмный TAB-хак не менялся — см.
[разбор Telegram и Back](../search-back-telegram/README.md).

## Проверка и установка

- `:app:assembleRelease` — успешно. Сборка вывела предупреждения R8 о
  desugaring/API и отсутствующих `java.beans`-классах.
- `git diff --check` — успешно.
- Временный Java-тест с извлечённым блоком обработчика и заглушками Android
  проверил поглощение исходного UP после отключения редактора, поглощение UP
  внутри окна 250 мс, пропуск заменяющего события, обычный Back при скрытой
  панели и отсутствие второго перевыпуска от страховочного таймера.
  Это проверка логики, не инструментальный тест Android/Firefox.
- APK `app/build/outputs/apk/release/krab-v4.0d.apk` установлен через `adb
  install -r`; результат `Success`, время обновления на устройстве
  `2026-09-21 13:07:17`.
- После установки K12KB осталась выбранной IME, её служба доступности —
  включённой. `versionName` и `versionCode` не менялись.
- Пользователь подтвердил устранение проблемы. Отдельного подтверждения
  проверки Telegram Back/search на момент записи нет.

Изменение кода на момент записи не закоммичено. Исходная запись трассы:
`/tmp/k12kb-firefox-back-live.log`; временный тест: `/tmp/k12kb-back-test/`.
Эти временные файлы могут исчезнуть; существенные данные сохранены выше.

## Правило на будущее

Если перехватываем DOWN и заменяем действие, нужно владеть всей исходной
парой DOWN/UP. Нельзя решать судьбу UP заново по изменившемуся состоянию окна
или по общему таймеру пропуска синтетических событий. Наличие привязанного
редактора само по себе не доказывает, что окно IME показано.
