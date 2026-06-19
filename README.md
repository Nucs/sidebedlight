# 🌙 Sidebed Light

A motion-activated **bedside night light** for Android. Keep your phone on the
nightstand — nudge it and the flashlight glows dim, shake it for full brightness, and
it fades back to dark on its own. Built for half-asleep, one-handed, in-the-dark use.

## Features

- **Motion-driven light** — gentle movement = lowest brightness, a shake = maximum
  (true variable LED brightness on Android 13+, on/off elsewhere).
- **Auto-off** — turns off after a few seconds of stillness (default 7s), stays armed.
- **Nightly schedule** — auto-arm and auto-disarm at set times (e.g. 23:00 → 06:00).
- **Volume-down to kill** — take the volume all the way to 0 to turn it off.
- **One-tap off** — persistent notification whose button *and* body both turn it off.
- **Red light mode** — melatonin-friendly screen-based red light (the LED is white-only).
- **Tunable** — brightness floor/ceiling, motion sensitivity, shake threshold, off-delay.
- **Slick dark UI** — warm, low-blue Material 3 theme with a live "breathing" glow.

## Build & install

Requirements: Android SDK (platform 35, build-tools 35) and a JDK (17+). The Gradle
wrapper downloads everything else.

```bash
# Point Gradle at your SDK (already present as local.properties on this machine)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew installDebug      # build and install to a connected device
# or
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
```

## How to use

1. Open the app and tap the glowing orb to **activate** (grant the notification
   permission when asked).
2. Put the phone down. Touch or shake it to light up your bedside; stop and it goes dark.
3. Tap the notification (or its **Turn off** button), or turn the volume to 0, to stop.
4. **Settings** (top-right gear): tune brightness/sensitivity, set a nightly schedule,
   or switch to red-light mode. The moon icon opens the manual red lamp.

## Permissions

| Permission | Why |
|------------|-----|
| Notifications | The required foreground-service "Turn off" notification |
| Display over other apps | Only for armed **red-screen** mode (optional) |
| Exact alarms | The nightly auto on/off schedule (auto-granted via `USE_EXACT_ALARM`) |

Torch control needs **no** permission.

## Notes & limitations

- Best used **while charging** — sensing with the screen off holds a wake lock.
- Red mode is **screen-based** (phone flashlights can't emit red) and works best with
  the screen on.
- The nightly schedule shows a status-bar alarm icon (a side effect of using a reliable,
  doze-exempt alarm to wake the light).

See [`CLAUDE.md`](./CLAUDE.md) for architecture and implementation details.
