# Google Play listing & compliance pack — Sidebed Light

Everything you need to paste into the Play Console. Assets are in this folder
(`icon-512.png`, `feature-graphic.png`) and `../../screenshots/`.

## App details
- **App name:** Sidebed Light
- **Package name:** `com.sidebed.light`
- **Default language:** English (US)
- **App or game:** App
- **Free or paid:** Free
- **Category:** Tools (alt: Lifestyle)
- **Contact email:** elibelash@gmail.com
- **Privacy policy URL:** https://github.com/Nucs/sidebedlight/blob/main/PRIVACY_POLICY.md

## Short description (≤80 chars)
> Motion-activated bedside night light — pick it up to glow, shake for bright.

## Full description (≤4000 chars)
> Sidebed Light turns your phone into a smart bedside night light. Leave it on the
> nightstand: pick it up and the flashlight glows dim, shake it and it ramps to full
> brightness, then it fades back to dark once the phone is completely still — all
> operable half-asleep, in the dark, with one hand.
>
> Features
> • Motion-activated: a deliberate pick-up turns the light on at its lowest level.
> • Shake for brightness: the harder/longer you shake, the brighter it gets, and it holds.
> • Auto-off: switches off after a configurable delay (2s–2 min) of complete stillness.
> • Nightly schedule: arm and disarm automatically (e.g. 23:00 → 06:00).
> • Volume-down to turn off, even at minimum volume.
> • One-tap off: a persistent notification whose button and body both turn it off.
> • Melatonin-friendly red mode: a screen-based red light instead of the white LED.
> • Fully tunable: brightness floor/ceiling, sensitivity, pick-up threshold, shake
>   strength, off-delay, and more.
> • Clean, warm, low-blue dark interface designed for night use.
>
> No ads, no accounts, no tracking, and no internet access — everything runs on your
> device. Best used while charging on the nightstand.

## Data safety form answers
- **Does your app collect or share any of the required user data types?** No.
- **Data collected:** None.
- **Data shared:** None.
- **Is all data encrypted in transit?** N/A (no data is collected or transmitted).
- **Do you provide a way to request data deletion?** N/A (no data is collected).

## Foreground service declaration (FOREGROUND_SERVICE_SPECIAL_USE)
Play Console → App content → "Foreground service permissions". Use this justification:

> Sidebed Light is a user-initiated bedside night light. After the user explicitly arms
> it (or during the schedule they configure), the app must continuously read the
> accelerometer and control the camera flash while the screen is off, so a foreground
> service is required to keep sensing and lighting reliably through the night. None of the
> predefined foreground-service types (camera, connectedDevice, dataSync, location,
> mediaPlayback, mediaProjection, microphone, phoneCall, health, remoteMessaging,
> shortService, systemExempted) describes "continuously monitor motion and drive the
> flashlight," so FOREGROUND_SERVICE_SPECIAL_USE is used. The service runs only while the
> light is armed and stops as soon as the user disarms it.

If asked for a demo video, screen-record: arming the light → moving the phone to light up
→ disarming via the notification.

## Permissions rationale (if Play asks)
- **FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE** — run the bedside light while armed (see above).
- **POST_NOTIFICATIONS** — the required ongoing "light is active / Turn off" notification.
- **SCHEDULE_EXACT_ALARM** — optional nightly on/off schedule; user-granted, falls back to inexact.
- **SYSTEM_ALERT_WINDOW** — optional red-screen light overlay; only when red mode is on.
- **WAKE_LOCK** — keep the accelerometer sensing while the screen is off.
- **RECEIVE_BOOT_COMPLETED** — re-arm the schedule after a reboot.
- **FLASHLIGHT** — torch control (normal permission).
- No CAMERA, location, contacts, microphone, or storage permissions are requested.

## Content rating
IARC questionnaire: answer "No" to all violence/sexual/profanity/gambling/etc. questions.
Result: Everyone / PEGI 3.

## Target audience & ads
- Target age: 13+ (or "All ages"); not designed for or targeted at children.
- Contains ads? No.

## Required assets — status
- [x] App icon 512×512 (32-bit PNG) — `icon-512.png`
- [x] Feature graphic 1024×500 — `feature-graphic.png`
- [x] Phone screenshots (≥2, 16:9 or 9:16) — `../../screenshots/screenshot-1..3.jpg`
- [ ] (Optional) 7" / 10" tablet screenshots

## Build to upload
Signed **App Bundle**: `app/build/outputs/bundle/release/app-release.aab`
(build with `./gradlew :app:bundleRelease`). Enroll in **Play App Signing** when prompted
— our `keystore.jks` is the *upload* key (keep it backed up).

## Pre-launch reminders
- New personal developer accounts: run a **closed test with ≥12 testers for ≥14 days**
  before you can request production access.
- compileSdk/targetSdk is 35 (meets Play's target-API requirement).
- versionCode 1 / versionName 1.0.0 for the first release.
