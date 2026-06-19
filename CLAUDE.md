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
│  └─ VolumeWatcher          ContentObserver; volume→0 transition fires disarm
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
  residual magnitude drives intensity. `moveThreshold`/`shakeThreshold` come from the
  *Sensitivity* and *Shake strength* settings (`SidebedSettings.toMotionConfig`).
- **Move = lowest, shake = max:** the service maps the engine's 0..1 intensity into the
  `minBrightnessPct`..`maxBrightnessPct` window, so any movement is ≥ the floor and a
  full shake hits the ceiling.
- **7-second auto-off:** `offDelaySeconds` (default 7). Tracked as "time since last
  significant sample"; on timeout the engine calls `onIdle` and the light turns off but
  stays **armed**, so the next movement relights it.
- **Nightly schedule (e.g. 23:00→06:00):** `ScheduleManager` sets two exact alarms.
  Arm uses `setAlarmClock` (doze-exempt and permitted to start a foreground service from
  the background — it does add a status-bar alarm icon). Disarm uses
  `setExactAndAllowWhileIdle`. Each alarm reschedules itself for the next day.
- **Volume-to-zero turns off:** `VolumeWatcher` observes system settings; a downward
  transition of media **or** ring volume to 0 disarms. (An already-silent phone at arm
  time does not trigger it.) Toggle: *Behaviour → Volume to zero turns off*.
- **Notification with "Turn off":** the foreground-service notification. Per the spec,
  **both** the action button and tapping the body disarm (both fire
  `LightActionReceiver`). Low-importance, silent, no timestamp.
- **Settings:** brightness floor/ceiling, sensitivity, shake strength, off-delay,
  schedule + times, light mode, red brightness, volume gesture, wake lock — all in
  `SidebedSettings`, persisted via DataStore, edited on `SettingsScreen`.
- **Red light (melatonin):** phone flash LEDs are white only, so red is **screen-based**:
  - `RedLightActivity` — a manual red lamp you can open anytime (drag = brightness).
  - `RedOverlayController` — armed `RED_SCREEN` mode draws a red overlay window (needs
    "Display over other apps"; falls back to the LED if not granted).

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
- **Exact alarms:** `USE_EXACT_ALARM` is declared (auto-granted), so the schedule fires
  exactly without the user-facing permission dance. `SCHEDULE_EXACT_ALARM` is also
  declared as a fallback and the Settings screen surfaces a "grant" row if needed.
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
- Release signing config + enable/verify R8 (`isMinifyEnabled` is on for release but
  unverified — only debug has been built).
