# CLAUDE.md — Sidebed Light

Guidance for any agent (or human) working in this repository.

## What this app is

**Sidebed Light** is an Android bedside ("sidebed") night light. While *armed*, it
watches the accelerometer: nudge the phone and the flashlight turns on dim, shake it
and it ramps to full brightness, and after a few seconds of stillness it switches off
again. It can arm/disarm itself on a nightly schedule, be killed with a volume-down
gesture or a notification button, and offers a melatonin-friendly red-screen mode.

The whole point is to be operable half-asleep in the dark, so the UX is: *move →
light, shake → brighter, stop → dark.*

## Tech stack

| Piece            | Version / choice                                     |
|------------------|------------------------------------------------------|
| Language         | Kotlin 2.0.21                                         |
| UI               | Jetpack Compose (Material 3), single-Activity        |
| Build            | Gradle 8.11.1 (wrapper) + AGP 8.7.3                  |
| JDK              | 17 bytecode (builds fine on the installed JDK 21)    |
| compileSdk / target | 35 (Android 15)                                   |
| minSdk           | 26 (Android 8.0)                                      |
| Persistence      | DataStore (Preferences)                              |
| Async            | Coroutines / Flow                                     |

Versions are centralized in `gradle/libs.versions.toml`.

## Build / run

The Android SDK is expected at the path in `local.properties` (`sdk.dir`). The Gradle
wrapper self-bootstraps; no global Gradle needed.

```bash
./gradlew :app:assembleDebug          # build debug APK
./gradlew installDebug                 # build + install to a connected device/emulator
./gradlew :app:lintDebug               # static analysis (not run by assemble)
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
The debug build uses the `.debug` applicationId suffix so it can co-exist with release.

> There are currently no unit/instrumented tests. The logic worth testing in isolation
> is `MotionEngine` / `SidebedSettings.toMotionConfig()` (pure-ish) — start there if
> adding tests. Camera/sensor/window behaviour needs a real device.

### Installing to the dev device (wireless ADB)

Dev phone: **Galaxy S23 Ultra (SM-S918B)**, serial `R5CW51XVDZL`.
Static LAN IP **`192.168.50.7`** (DHCP-reserved — does **not** change). The pairing and
connect *ports* are randomised each session, so discover the connect port via mDNS.

On the phone: *Developer options → Wireless debugging → On*. Only when re-pairing, tap
*Pair device with pairing code* to get the 6-digit code + pairing port.

```bash
ADB="$ANDROID_HOME/platform-tools/adb.exe"   # adb is not on PATH

# 1. Pair (only when re-pairing) — pairing dialog's port + code:
"$ADB" pair 192.168.50.7:<PAIRING_PORT> <CODE>

# 2. Discover the (different) connect port via mDNS, then connect:
PORT=$("$ADB" mdns services | grep _adb-tls-connect | grep 192.168.50.7 | grep -oE ':[0-9]+' | head -1 | tr -d ':')
"$ADB" connect "192.168.50.7:$PORT"

# 3. Install + launch. The device appears twice (via IP and via mDNS), so pin one with -s:
"$ADB" -s "192.168.50.7:$PORT" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "192.168.50.7:$PORT" shell am start -n com.sidebed.light.debug/com.sidebed.light.MainActivity
```

Note: `applicationId` is `com.sidebed.light.debug` (debug suffix) but the launcher
activity class is `com.sidebed.light.MainActivity`. Once paired, future sessions usually
only need steps 2–3 (re-pair only if the phone "forgets" this computer).

## Architecture

Single process, single Activity (`MainActivity`) for the UI, plus a foreground
`Service` that does the real work. They communicate through **`SidebedState`** — a
plain object of `MutableStateFlow`s (same process, no IPC needed).

```
package com.sidebed.light
├─ SidebedApp                Application; owns SettingsRepository, creates notif channel
├─ SidebedState              shared runtime flows: isArmed/isLightOn/lightIntensity/motionLevel
├─ Permissions               runtime-permission checks + settings intents
├─ MainActivity              Compose host + Home/Settings navigation + arm gating
├─ RedLightActivity          manual full-screen red lamp (drag = brightness, tap = exit)
├─ data/
│  ├─ Settings               SidebedSettings data class + LightMode enum
│  └─ SettingsRepository     DataStore read/write; Context.settingsRepository accessor
├─ light/
│  ├─ LightController        interface: setIntensity(0..1) / turnOff()
│  ├─ TorchController        camera2 LED; variable brightness on API 33+, else on/off
│  └─ RedOverlayController   red system-overlay window (needs overlay permission)
├─ motion/
│  ├─ MotionConfig           thresholds derived from settings (toMotionConfig())
│  └─ MotionEngine           accelerometer → linear accel → intensity / idle-timeout
├─ service/
│  ├─ SidebedLightService    foreground service wiring everything together
│  ├─ ServiceController      arm()/disarm() entry points + action constants
│  ├─ LightActionReceiver    notification "Turn off" / body tap → disarm
│  ├─ VolumeWatcher          ContentObserver; volume→0 transition fires disarm
│  └─ VolumeKeyWatcher       MediaSession VolumeProvider; volume-down at minimum disarms
├─ schedule/
│  ├─ ScheduleManager        installs the two daily exact alarms (arm/disarm)
│  ├─ ScheduleReceiver       alarm fired → arm/disarm + reschedule next day
│  └─ BootReceiver           reinstall schedule after reboot / app update
└─ ui/
   ├─ SidebedViewModel       settings StateFlow + arm/disarm/update actions
   ├─ HomeScreen             status + ActivationOrb (live glow) + arm/disarm
   ├─ SettingsScreen         all tunables + schedule pickers + permissions
   ├─ Components             SectionCard / LabeledSlider / ToggleRow / TimePickerDialog…
   └─ theme/                 warm low-blue dark Material 3 theme
```

### Core data flow (armed)

```
accelerometer → MotionEngine (gravity high-pass → |linear accel|)
   ├─ magnitude ≥ moveThreshold  → onLight(intensity 0..1)
   │        SidebedLightService maps via min/max% → LightController.setIntensity()
   │        and publishes SidebedState.lightIntensity (UI reacts)
   └─ idle ≥ offDelay            → onIdle() → LightController.turnOff()
```

`MotionEngine` callbacks are delivered on the **main thread** (a main-looper Handler is
passed to `registerListener`) so the red overlay (`WindowManager`) and torch can be
touched directly.

## How each requirement is implemented

- **Motion → light:** `MotionEngine`. Gravity is removed with a low-pass filter; the
  residual magnitude drives intensity. Three thresholds (`SidebedSettings.toMotionConfig`):
  `activationThreshold` (off→on, a deliberate pick-up — default 2x, configurable via
  *Activation*), `idleThreshold` (a tiny floor — once on, any motion above it keeps the
  light alive), and `moveThreshold`/`shakeThreshold` (the *Sensitivity*/*Shake strength*
  band over which brightness climbs).
- **Move = lowest, shake = max:** the service maps the engine's 0..1 intensity into the
  `minBrightnessPct`..`maxBrightnessPct` window, so any movement is ≥ the floor and a
  full shake hits the ceiling.
- **Auto-off:** `offDelaySeconds` (default 7, up to 2m). The off-timer runs **only while
  the phone is completely idle** (below `idleThreshold`); any motion resets it. On timeout
  the light turns off but stays **armed**, so the next pick-up relights it.
- **Nightly schedule (e.g. 23:00→06:00):** `ScheduleManager` sets two daily alarms. When
  the exact-alarm permission is granted, arm uses `setAlarmClock` (doze-exempt, may start
  the FGS from the background — adds a status-bar alarm icon) and disarm uses
  `setExactAndAllowWhileIdle`; otherwise both fall back to inexact `setAndAllowWhileIdle`.
  Each alarm reschedules itself for the next day. The arm only fires on the weekdays in
  `scheduleDaysMask` (a 7-bit Sun..Sat mask, all on by default); the disarm runs daily and
  is a no-op when nothing is armed.
- **Volume-to-zero turns off:** two complementary watchers (toggle: *Behaviour →
  Volume to zero turns off*):
  - `VolumeWatcher` — a ContentObserver; a downward transition of media **or** ring
    volume to 0 disarms (catches lowering by any means).
  - `VolumeKeyWatcher` — a `MediaSession` + remote `VolumeProvider` that captures the
    actual volume-down key **even when volume is already at minimum** (a ContentObserver
    can't, since the value never changes). Normal up/down still pass through to the
    stream with the system slider. Best-effort: only while ours is the active media session.
- **Notification with "Turn off":** the foreground-service notification. Per the spec,
  **both** the action button and tapping the body disarm (both fire `LightActionReceiver`).
  Low-importance, silent, no timestamp. Kept non-dismissable: a `deleteIntent` re-posts it
  (service `ACTION_RESHOW`) if it's swiped away on Android 13+, guarded by `started` so a
  real disarm still clears it.
- **Light off on deactivation:** `TorchController.turnOff()` force-calls `setTorchMode(false)`
  (it doesn't trust the cached on/off flag), and `onDestroy` releases **both** the torch and
  the red overlay — so disarming always leaves the LED off regardless of light mode.
- **Settings:** brightness floor/ceiling, sensitivity, activation (pick-up) threshold,
  shake strength, off-delay (2s–2m), schedule + times, light mode, red brightness,
  volume gesture, wake lock — all in `SidebedSettings`, persisted via DataStore.
- **Red light (melatonin):** phone flash LEDs are white only, so red is **screen-based**:
  - `RedLightActivity` — a manual red lamp you can open anytime (drag = brightness).
  - `RedOverlayController` — armed `RED_SCREEN` mode draws a non-touchable red overlay
    window (needs "Display over other apps"; selecting red in Settings prompts for it,
    and it falls back to the LED if still not granted).
  - Brightness mapping: floor = `minBrightnessPct` for both modes; ceiling =
    `maxBrightnessPct` for torch, `redBrightnessPct` for red (no double-attenuation).

## Key decisions & platform caveats

- **Torch needs no permission** (`CameraManager.setTorchMode` /
  `turnOnTorchWithStrengthLevel`). Variable brightness requires API 33+ **and** a device
  that reports `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL > 1`; otherwise it's on/off only.
- **Wake lock:** while armed (and "Keep sensing with screen off" is on) a `PARTIAL_WAKE_LOCK`
  is held so accelerometer events keep flowing with the screen off. Intended for a phone
  charging on the nightstand; document the battery trade-off to users.
- **Foreground service type** is `specialUse` (there is no "flashlight" FGS type). The
  subtype is declared via a `<property>` in the manifest. `ServiceCompat.startForeground`
  passes the type only on API 34+.
- **Exact alarms:** only `SCHEDULE_EXACT_ALARM` (user-granted) is declared — `USE_EXACT_ALARM`
  was removed for Google Play compliance (Play restricts it to alarm-clock/calendar apps).
  Settings surfaces a "grant" row; without it the schedule degrades to inexact alarms.
- **Red overlay can't reliably power the screen on** by itself; red mode is best with the
  screen on. This is the honest limitation of screen-based red light.
- **Volume gesture stream ambiguity:** which stream the volume rocker controls (media vs
  ring) is device/state-dependent, so the watcher checks both `STREAM_MUSIC` and
  `STREAM_RING`.

## Conventions

- Kotlin official style; 4-space indent; trailing commas on multiline.
- One feature area per package (`light`, `motion`, `service`, `schedule`, `ui`, `data`).
- Keep `LightController` the only thing that touches a physical light source.
- Runtime state belongs in `SidebedState`; persisted state belongs in `SidebedSettings`.
- API-gated calls are guarded inline with `Build.VERSION.SDK_INT >= …` (lint isn't part
  of `assembleDebug`, so don't rely on it to catch `NewApi`).
- User-facing strings go in `res/values/strings.xml`.

## Possible next steps

- Unit tests for `MotionEngine`/`toMotionConfig` (extract the math to a testable pure fn).
- Make `RedLightActivity` motion-reactive (reuse `MotionEngine`).
- Per-app battery-optimization exemption prompt for rock-solid scheduling on aggressive OEMs.
- A "fade out" instead of a hard off at the idle timeout.
- Google Play: release signing is configured (`keystore.properties`, git-ignored), the
  R8 release AAB builds and was verified on-device, and the listing copy, store graphics,
  data-safety answers and `specialUse` justification live in `docs/play/` +
  `PRIVACY_POLICY.md`. Remaining: the closed-test rollout and console submission.
