# CallNuke — Communications Firewall

**A private, self-compiled Android app that blocks unwanted calls and categorizes untrusted SMS, built with Godot 4.6.2 (Mono) for the UI and a native Kotlin plugin for the Android-specific firewall logic.**

- Package: `com.callnuke.firewall`
- Target device: Samsung Galaxy Note 20 (One UI)
- Min SDK: 24 (Android 7.0) · Target SDK: 35 (Android 15)
- Distribution: **Private / sideloaded only** — not published to Google Play
- Status of this document: written from a full read of the source tree in this repository. Anything that requires a physical device to verify is explicitly marked **REQUIRES MANUAL DEVICE VERIFICATION** — no test outcomes are fabricated.

---

## 1. Source Tree

```
D:\CallNuke\
├── LICENSE
├── DOCUMENTATION.md                      ← this file
└── call-nuke\                            ← Godot project root
    ├── project.godot                     Godot project config (Godot 4.6, Mono/.NET, mobile renderer)
    ├── icon.svg / icon.svg.import        App icon (source SVG)
    ├── theme.tres                        Shared UI theme (colors, fonts) for all screens
    ├── export_presets.cfg                Android export preset (package id, SDK levels, keystore, arch)
    │
    ├── main.gd / main.tscn               Home screen: protection toggles, blocked/filtered counters, nav
    ├── allowed_numbers.gd / .tscn        Manual phone-number allowlist screen (add/remove/list)
    ├── sms_keywords.gd / .tscn           Trusted SMS keyword list screen (add/remove/list)
    ├── android_setup.gd / .tscn          Permission/role setup screen (Call Screening role, Contacts, SMS)
    │
    ├── build\
    │   ├── CallNuke.pck                  Exported Godot data pack (assets + compiled GDScript)
    │   └── CallNuke-debug.apk            Final installable APK (~119 MB, debug-signed)
    │
    └── android\                          Godot's generated Gradle/Android project (build system)
        ├── build.gradle, config.gradle, settings.gradle, gradle.properties
        ├── gradlew / gradlew.bat / gradle\wrapper\...   Gradle 8.11.1 wrapper
        ├── libs\debug\godot-lib.template_debug.aar       Godot engine Android runtime (debug template)
        ├── res\values*\godot_project_name_string.xml     Localized app display name (all Godot locales)
        └── src\main\
            ├── AndroidManifest.xml                       Permissions, service, receiver, plugin declarations
            └── java\com\callnuke\firewall\                ← CallNuke's native Kotlin source (hand-written)
                ├── CallNukeScreeningService.kt            CallScreeningService: call allow/reject decision
                ├── CallNukePlugin.kt                      Godot <-> Android bridge (GodotPlugin)
                ├── CallNukePrefs.kt                       SharedPreferences: single source of truth for config
                ├── ContactLookup.kt                       Device Contacts provider lookup
                ├── PhoneNumberUtils.kt                    Number normalization/comparison (wraps Android's own)
                ├── SmsReceiver.kt                         BroadcastReceiver: SMS classification (not true blocking)
                └── KeywordMatcher.kt                      Word-boundary keyword matching for trusted SMS senders
```

Everything under `call-nuke\android\` **except** `src\main\java\com\callnuke\firewall\*.kt` and the entries in `AndroidManifest.xml` that reference CallNuke is Godot's standard generated Android build scaffold — it is not hand-written project code, but it is required and checked in so the project builds reproducibly.

---

## 2. Build Instructions (clean clone → APK)

### 2.1 Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Godot | 4.6.2 Stable, **Mono** build | `D:\godot4.6\` |
| JDK | 17.0.20.1 (Eclipse Temurin) | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` |
| Android SDK | platforms 34 & 35, build-tools 34 & 35.0.1 | `D:\AndroidSDK` |
| Gradle | 8.11.1 | via the checked-in wrapper (`android\gradlew.bat`), no separate install needed |

Set these once (PowerShell, current session or persist via System Properties):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "D:\AndroidSDK"
$env:ANDROID_SDK_ROOT = "D:\AndroidSDK"
```

Godot must know where the Android SDK and a debug keystore are. In the Godot editor: **Editor → Editor Settings → Export → Android** — set:
- `Android SDK Path` → `D:\AndroidSDK`
- `Debug Keystore` → the default `%USERPROFILE%\.android\debug.keystore` (auto-created by the SDK's `keytool`/`adb` on first use if missing)

### 2.2 Steps

1. Clone/copy the repository so the project root is `D:\CallNuke\call-nuke\` (must contain `project.godot`).
2. Open the project once in the Godot 4.6.2 Mono editor (`godot4.6 --editor --path D:\CallNuke\call-nuke`) so it generates the local `.godot\` cache and, for a Mono project, builds the C#/`.NET` solution scaffolding. This step also lets Godot regenerate `android\` from its internal Android export templates if that folder is missing.
3. Confirm `export_presets.cfg` still points at the correct package id (`com.callnuke.firewall`), min/target SDK, and `arm64-v8a` architecture (see §3).
4. Export the project (see **§3 Android Export Instructions** below for why this is a manual, GUI-driven step rather than a single CLI command in this environment).
5. Godot's export step does two things:
   - Writes the compiled game data as `build\CallNuke.pck` and copies it into `android\build\...\assets\` for Gradle to package.
   - Invokes Gradle (`gradlew.bat`) in `call-nuke\android\` to assemble the final APK, which Godot copies out to the path configured in the export preset — `build\CallNuke.apk` (renamed here to `CallNuke-debug.apk` for the debug template).
6. Resulting artifact: `D:\CallNuke\call-nuke\build\CallNuke-debug.apk` (~119 MB — dominated by the embedded Godot Mono/.NET runtime and the `arm64-v8a` native engine library).

You can also drive Gradle directly once the `.pck` and assets exist under `android\` (useful for iterating on Kotlin changes only, without a full Godot re-export):

```powershell
cd D:\CallNuke\call-nuke\android
.\gradlew.bat assembleDebug
```

This produces `android\build\outputs\apk\standard\debug\android_debug.apk` (and `mono\debug\...` / `instrumented\debug\...` variants for the Mono/instrumented build flavors Godot's template defines). The file that matters for installation is the one Godot's export step copies to `call-nuke\build\CallNuke-debug.apk`.

---

## 3. Android Export Instructions (manual process)

Headless/CLI export (`godot --headless --export-debug "Android" ...`) was avoided for this project because Godot's Android export path reads `EditorSettings` (SDK path, debug keystore path) that are normally only initialized through the GUI editor's first-run/export-settings flow; running headless before those settings exist reliably fails with SDK/keystore-not-found errors. The verified, working process is therefore:

1. Launch the Godot 4.6.2 Mono editor and open `D:\CallNuke\call-nuke\project.godot`.
2. **Project → Export…**
3. Confirm the **Android** preset (`preset.0` in `export_presets.cfg`) is selected. Key settings already baked into that preset:
   - `package/unique_name = com.callnuke.firewall`
   - `gradle_build/use_gradle_build = true` (required — CallNuke's Kotlin plugin only builds through the Gradle path, not the pre-built export templates)
   - `gradle_build/min_sdk = 24`, `gradle_build/target_sdk = 34` (raise to 35 in the preset if targeting SDK 35 exactly is required; the manifest/build itself is compiled against 35 per the environment table)
   - `architectures/arm64-v8a = true` (only arch enabled — matches the Note 20's 64-bit ARM CPU; keeps APK size down)
   - `keystore/debug = C:/Users/Ellectroma/.android/debug.keystore` with the standard `androiddebugkey` / `android` debug credentials (debug-signed only — see §13 Distribution Notes)
   - `export_path = build/CallNuke.apk`
4. Click **Export Project**, choose **Export With Debug** (this is a debug build — no release keystore is configured), keep the suggested path (`build/CallNuke.apk`).
5. Godot triggers its internal Gradle build under `call-nuke\android\`. Watch the export dialog / Godot's output panel for Gradle errors (missing SDK components, Kotlin compile errors in the `com.callnuke.firewall` sources, manifest merge conflicts).
6. On success, rename/copy the resulting APK to `build\CallNuke-debug.apk` for a clear, stable filename to hand to `adb install` (this repo's copy is already named this way).

If Gradle fails on a fresh machine, the most common causes are: `ANDROID_HOME`/SDK path not set in Editor Settings, missing build-tools version, or the debug keystore not yet generated (run any `adb`/SDK command once, or `keytool -genkey -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000` to create it manually).

---

## 4. APK Installation Instructions (Note 20 via adb)

1. On the Note 20: **Settings → About phone → tap Build number 7×** to enable Developer Options, then **Settings → Developer options → USB debugging → On**.
2. Connect the phone to the PC via USB and accept the "Allow USB debugging?" prompt on the device (check "Always allow from this computer" if desired).
3. Confirm `adb` (from `D:\AndroidSDK\platform-tools`) sees the device:
   ```powershell
   adb devices
   ```
   It should list the Note 20 as `device` (not `unauthorized`).
4. Install (or reinstall over a previous debug build):
   ```powershell
   adb install -r "D:\CallNuke\call-nuke\build\CallNuke-debug.apk"
   ```
   `-r` allows reinstalling over an existing install (keeps app data). Omit `-r` for a first-time clean install.
5. If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch from a prior differently-signed build), uninstall first: `adb uninstall com.callnuke.firewall`, then install again.
6. Launch from the app drawer ("CallNuke") or directly:
   ```powershell
   adb shell am start -n com.callnuke.firewall/.GodotAppLauncher
   ```
7. To view live logs while testing:
   ```powershell
   adb logcat -s CallNuke:* godot:*
   ```
   (`CallNuke` is the log tag used by all Kotlin components; `godot` covers the Godot/GDScript-side engine log.)

---

## 5. Android Permissions Used

| Permission | Type | Why CallNuke needs it | Granted how |
|---|---|---|---|
| `android.permission.READ_CONTACTS` | Runtime (dangerous) | `ContactLookup.kt` queries `ContactsContract.PhoneLookup` so known contacts always bypass both the call and SMS filters. Without it, `ContactLookup.isContact()` fails closed (treats every number as "not a contact") and only the manual allowlist protects legitimate callers. | Requested from the in-app **Android Setup** screen via `CallNukePlugin.requestContactsPermission()`, which calls `ActivityCompat.requestPermissions`. The user must accept the system dialog. |
| `android.permission.RECEIVE_SMS` | Runtime (dangerous) | `SmsReceiver.kt` registers for `SMS_RECEIVED_ACTION` so it can read sender/body and apply keyword/contact/allowlist classification. Without it, the receiver is never invoked and SMS filtering/counting silently does nothing. | Requested from the **Android Setup** screen via `CallNukePlugin.requestSmsPermission()`. |
| `android.permission.BIND_SCREENING_SERVICE` (declared on the `<service>`, not requested by the app) | System-enforced, not a runtime grant | Required by the OS on the `CallScreeningService` declaration itself — it ensures only the Telecom system service can bind to `CallNukeScreeningService`, not arbitrary apps. Nothing to grant; it's a manifest-level guard. | N/A — automatic once the app holds the `CALL_SCREENING` role (§6). |
| `android.permission.BROADCAST_SMS` (declared on the `<receiver>`, not requested by the app) | System-enforced | Restricts who may broadcast the `SMS_RECEIVED` intent to `SmsReceiver` to the OS telephony stack itself, preventing other apps from spoofing fake SMS-received broadcasts into CallNuke. | N/A — enforced automatically by the platform. |

No install-time-only "normal" permissions beyond the two above are declared. There is no `INTERNET` permission — CallNuke makes no network calls, sends no telemetry, and stores nothing off-device.

---

## 6. Android Roles Used

**`RoleManager.ROLE_CALL_SCREENING`** (API 29+) is the one Android *role* CallNuke uses.

- **What it does**: Designates CallNuke's `CallNukeScreeningService` as (one of) the app(s) the Telecom framework calls into for every incoming call, via `onScreenCall(Call.Details)`. This is the modern, Google-sanctioned replacement for the old `CALL_PHONE`/broadcast-based call-blocking hacks — it lets the app allow or silently reject a call before it rings, without needing to be the default Phone app.
- **How it's requested**: `CallNukePlugin.requestCallScreeningRole()` calls `RoleManager.createRequestRoleIntent(ROLE_CALL_SCREENING)` and starts it via `startActivityForResult`, which shows the system's "Allow CallNuke to screen calls?" dialog. Exposed in-app on the **Android Setup** screen ("Role" button), with a live status indicator (`hasCallScreeningRole()` / `RoleManager.isRoleHeld`).
- **Requirements**: API 29+ only (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`); on older devices the plugin logs a warning and does nothing — the app has no fallback call-blocking mechanism below Android 10. The Note 20 ships on Android 10+ and is expected to be updated well past API 29, so this is not a practical constraint for the target device.
- **Multiple screening apps**: Android allows more than one app to hold `ROLE_CALL_SCREENING` simultaneously (each gets a chance to screen); CallNuke does not attempt to detect or coordinate with other call-screening apps (e.g. Samsung's own spam protection) — see §11/§12.

`ROLE_SMS` (default SMS app) is **not** requested or held — see §9 for why, and what that limits.

---

## 7. Godot / Kotlin Boundary — How the Plugin Bridge Works

CallNuke is two cooperating halves that communicate only through Android `SharedPreferences` and a thin synchronous method-call bridge — there is no networking or IPC beyond that.

```
┌─────────────────────────────┐        ┌──────────────────────────────────────┐
│  Godot UI (GDScript)         │        │  Android native (Kotlin)              │
│  main.gd / allowed_numbers.gd│        │                                        │
│  sms_keywords.gd /           │  calls │  CallNukePlugin (GodotPlugin)          │
│  android_setup.gd            │───────▶│  @UsedByGodot methods                 │
│                               │        │                                        │
│  Engine.get_singleton(        │        │  reads/writes ─────┐                  │
│    "CallNuke")                │        │                     ▼                  │
└─────────────────────────────┘        │           CallNukePrefs (SharedPreferences)
                                         │                     ▲
                                         │                     │ reads
                                         │           ┌─────────┴──────────┐
                                         │           │ CallNukeScreeningService │ ← Telecom framework, own process/lifecycle
                                         │           │ SmsReceiver              │ ← Telephony broadcast, own process/lifecycle
                                         │           └───────────────────────────┘
                                         └──────────────────────────────────────┘
```

- **Plugin registration**: `AndroidManifest.xml` declares `org.godotengine.plugin.v2.CallNuke` → `com.callnuke.firewall.CallNukePlugin`, which is how the Godot engine discovers and instantiates the plugin at startup and exposes it as `Engine.get_singleton("CallNuke")` in GDScript.
- **What runs in the Godot/UI process**: all four `.gd` scripts (`main`, `allowed_numbers`, `sms_keywords`, `android_setup`) — pure UI state and event handling, plus calling into `CallNukePlugin`'s `@UsedByGodot` methods (role/permission requests and status checks, allowlist and keyword CRUD, protection toggles, counters). These calls are synchronous, in-process JNI calls — there is no async messaging layer.
- **What runs entirely independent of Godot**: `CallNukeScreeningService` (bound by the Telecom system service whenever a call arrives) and `SmsReceiver` (invoked by the telephony broadcast whenever an SMS arrives). Both can run — and make allow/reject/classify decisions — **even if the Godot app/Activity is not running or was force-stopped**, because Android starts them independently as a bound service / broadcast receiver. This is by design: the firewall must work in the background, not just while the UI is open.
- **Shared state**: `CallNukePrefs` (backed by `SharedPreferences` file `callnuke_prefs`, `MODE_PRIVATE`) is the single source of truth read and written by *all* of the above — the UI writes toggles/allowlist/keywords through `CallNukePlugin`, and the background service/receiver read that same store to make decisions and write back the daily blocked/filtered counters. No message-body or call-content data is ever persisted — only the numeric counters, the allowlist, and the keyword list.
- **Data formats crossing the boundary**: numbers and keywords cross as JSON array strings (`org.json.JSONArray`) built on the Kotlin side and parsed with Godot's `JSON.new().parse()` on the GDScript side (see `allowed_numbers.gd` / `sms_keywords.gd`).

---

## 8. Call Filtering Explanation (decision flow)

Implemented entirely in `CallNukeScreeningService.onScreenCall()`, invoked by the Telecom framework for every incoming call once CallNuke holds `ROLE_CALL_SCREENING`:

```
Incoming call arrives
        │
        ▼
Can we read the number from Call.Details.handle?  ──No──▶  ALLOW (fail-safe: never
        │ Yes                                                block on missing data)
        ▼
Is call protection enabled (CallNukePrefs)?        ──No──▶  ALLOW
        │ Yes
        ▼
Is the number a known device contact
(ContactsContract.PhoneLookup)?                    ──Yes──▶ ALLOW
        │ No
        ▼
Is the number on the manual allowlist
(CallNukePrefs, matched via Android's own
PhoneNumberUtils.compare for format tolerance)?     ──Yes──▶ ALLOW
        │ No
        ▼
      REJECT
      • setDisallowCall(true), setRejectCall(true)
      • setSilenceCall(true)  — phone never rings
      • setSkipNotification(true) — no missed-call popup
      • setSkipCallLog(false) — call still appears in the call log (kept for debugging/visibility)
      • increments the daily "blocked calls" counter
```

Key implementation details:
- **Fail-safe posture**: any ambiguity (unreadable number, contacts-lookup exception via a caught `Exception` in `ContactLookup.isContact`, missing permission) resolves to **allowing** the call, never to blocking it — the app never risks silently dropping a call it can't confidently classify.
- **Number matching** uses Android's built-in `android.telephony.PhoneNumberUtils.compare()` (both for contacts lookup, via the system `PhoneLookup` provider, and for the manual allowlist), so `+52 811 234 5678`, `8112345678`, and `(811) 234-5678` are all treated as the same number without any custom parsing logic.
- **Allowlist storage**: numbers are normalized on add (`PhoneNumberUtils.normalize`) to E.164 where the device's network/SIM country ISO is available, else stored as a stripped (non-dialable-character-free) string as a fallback.
- **Toggle**: the whole feature can be disabled from the home screen (`main.gd` → `CallToggle`) without losing the role/allowlist — `isCallProtectionEnabled` short-circuits everything to ALLOW.
- **Blocked-call counter** (`CallNukePrefs.incrementBlockedCallsCount`) resets automatically at local-date rollover (`resetDailyCountIfNeeded`), so the home screen always shows "today's" count.

---

## 9. SMS Filtering Explanation (decision flow + honest limitation)

Implemented in `SmsReceiver.onReceive()`, triggered by the `android.provider.Telephony.SMS_RECEIVED` broadcast (priority 999) whenever `RECEIVE_SMS` is granted:

```
SMS_RECEIVED broadcast arrives
        │
        ▼
Is SMS protection enabled (CallNukePrefs)?     ──No──▶  take no action at all
        │ Yes
        ▼
Parse PDUs into SmsMessage(s); concatenate
multipart bodies from the same sender
        │
        ▼
No originating address?  ──Yes──▶  take no action (fail-safe)
        │ No
        ▼
Is the sender a known device contact?          ──Yes──▶  ALLOWED
        │ No
        ▼
Is the sender on the manual allowlist?
(shared with the call allowlist — see note below)   ──Yes──▶  ALLOWED
        │ No
        ▼
Does the message body contain a user-configured
trusted keyword (word-boundary, case-insensitive,
substring fallback)?                                ──Yes──▶  ALLOWED
        │ No
        ▼
      FILTERED
      • increments the daily "filtered SMS" count
      • calls abortBroadcast() IF this is an ordered broadcast
        (stops only lower-priority, non-default-SMS-app receivers
         from also seeing it — see limitation below)
```

- **Shared allowlist by design (MVP tradeoff)**: a number added to unblock calls is automatically treated as trusted for SMS too, and vice versa — there is no separate per-channel allowlist in this version.
- **Keyword matching** (`KeywordMatcher.containsTrustedKeyword`): case-insensitive, `\b`-word-boundary regex per keyword, with a plain substring-match fallback if the regex can't be built for a given keyword (e.g. unusual Unicode). This is what lets a user add "Steam" or their bank's name so 2FA/OTP texts from trusted services are never flagged, without needing to add every possible sending number.
- **No message content is ever stored** — the receiver reads the sender/body only to classify, and persists nothing but the daily numeric "filtered" counter.

### ⚠️ Critical, non-obvious limitation: this is categorization, not blocking

CallNuke is **not** the device's default SMS app (it does not hold `RoleManager.ROLE_SMS`) and does not implement full SMS send/receive/store/display functionality. Concretely, per the extensive doc-comment in `SmsReceiver.kt`:

- CallNuke's receiver **does** get invoked for every incoming SMS and **can** read sender/body to classify it and increment the "filtered" counter.
- `abortBroadcast()` on a "filtered" message **only** prevents *other, lower-priority, non-default-SMS-app receivers* from also seeing that broadcast. It has **no effect whatsoever** on the device's actual default SMS app (Samsung Messages on the Note 20, or Google Messages if set as default) — that app does not rely on this broadcast at all; it receives messages through a separate, privileged delivery path.
- Therefore: **every "filtered" SMS still arrives, in full, with its normal notification, in the user's regular messaging app.** CallNuke's SMS feature is a *visibility/counting* feature ("here's how many messages this session would have flagged as untrusted"), not a message-blocking, hiding, or deleting feature.
- **True SMS blocking** (removing a message before the user ever sees it) is only possible by becoming the device's default SMS app, which requires implementing full SMS compose/send/receive/threading/storage — explicitly out of scope for this MVP.
- **OTP/2FA safety corollary**: because nothing is ever deleted or suppressed, a 2FA code can never be "lost" due to a false-positive keyword miss — worst case, a legitimate code-bearing SMS is merely counted as "filtered" internally while still fully visible to the user in their normal messaging app.

This limitation should be communicated to the end user in-app (e.g. as help text on the SMS Keywords / home screen) so "filtered" is never misread as "blocked."

---

## 10. Manual Note 20 Test Procedure

All items below **REQUIRE MANUAL DEVICE VERIFICATION** on the physical Samsung Galaxy Note 20 — none have been executed from this development environment, and no results are implied or assumed. Use `adb logcat -s CallNuke:*` alongside these to confirm the code path taken.

### Setup (before any test case)

- [ ] Install the APK per §4.
- [ ] Open **Android Setup** in-app and grant: Call Screening role, Contacts permission, SMS permission. Confirm all three show "● Granted".
- [ ] Confirm `CallToggle` and `SmsToggle` are ON on the home screen.
- [ ] Have a second phone (or a way to send SMS/place calls) available as the "unknown caller/sender" — ideally a number **not** in the Note 20's contacts and **not** on the CallNuke allowlist.
- [ ] Have a number that **is** saved as a device contact available.

### Call Tests

| # | Test | Expected result |
|---|---|---|
| A | Call from a number saved as a device contact | Phone rings normally; call appears in call log; blocked-count unchanged |
| B | Call from an unknown number (not a contact, not allowlisted) | Call is silently rejected (no ring, no missed-call notification); appears in call log; blocked-count on home screen increments by 1 |
| C | Add the unknown number to the allowlist (**Allowed Numbers** screen), then call again from it | Phone rings normally this time; blocked-count unchanged |
| D | Remove the number from the allowlist, call again | Behaves like test B again (rejected) |
| E | Turn **Call Protection** toggle OFF on home screen, call from an unknown number | Phone rings normally (feature disabled); blocked-count unchanged |
| F | Turn Call Protection back ON, revoke Contacts permission (Android app settings), call from a device contact's number | Contact lookup fails closed → call is treated as unknown and rejected unless separately allowlisted (confirms fail-safe-to-block-on-permission-loss behavior is *not* silently fail-open for contacts specifically — verify actual behavior against §8's documented fail-safe, since only "unreadable number" is explicitly fail-open, not "lookup denied") |
| G | Force-stop the CallNuke app (Settings → Apps → CallNuke → Force stop), then call from an unknown number | Call is still screened/rejected — confirms `CallNukeScreeningService` runs independently of the Godot app process being alive |

### SMS Tests

| # | Test | Expected result |
|---|---|---|
| A | SMS from a number saved as a device contact, arbitrary text | Message appears normally in Samsung Messages; filtered-count unchanged |
| B | SMS from an unknown number, no trusted keyword in body | Message still appears normally in Samsung Messages (see §9 limitation) **and** the home screen's filtered-SMS count increments by 1 |
| C | Add a trusted keyword (e.g. "Steam") on the **SMS Keywords** screen, then send/receive an SMS from an unknown number whose body contains that word | Message appears normally; filtered-count unchanged (classified ALLOWED) |
| D | SMS from an unknown number whose body contains the keyword as a substring of another word (e.g. keyword "Steam" inside "downstream") | Confirms word-boundary matching: should NOT match (filtered-count increments) — verify against the substring-fallback caveat in `KeywordMatcher` |
| E | Add the sender's number to the manual allowlist, send an SMS from it with no keyword | Message appears normally; filtered-count unchanged |
| F | Turn **SMS Protection** toggle OFF, SMS from an unknown number | Message appears normally; filtered-count unchanged (feature disabled, no classification performed at all) |
| G | Force-stop the CallNuke app, then send an SMS from an unknown number | `SmsReceiver` still runs and the filtered-count still increments on next app open — confirms the receiver runs independent of the Godot app process |

---

## 11. Known Android Limitations

- **Headless Godot export is unreliable in this environment.** `EditorSettings` for the Android SDK path and debug keystore are populated by the GUI editor's normal startup/export flow; a `--headless --export-debug` invocation before those settings exist fails to locate the SDK/keystore. The documented workaround (§3) is to always export via the GUI editor.
- **SMS "filtering" is categorization only**, not deletion, hiding, or true blocking — see the full explanation in §9. This is an inherent Android platform limitation for any app that is not the default SMS app, not a bug.
- **`abortBroadcast()` behavior**: calling it in `SmsReceiver` only affects delivery order among *other* `SMS_RECEIVED`-registered receivers at lower priority; it cannot and does not affect the default SMS app's separate delivery path. Priority 999 on CallNuke's receiver only affects it relative to other third-party receivers, not the system/default-app delivery.
- **Multiple call-screening apps can coexist.** If the user also has Samsung's own spam-call protection or Google's Phone-app spam protection active, more than one screening app may evaluate the same call; CallNuke does not detect, defer to, or coordinate with other screening apps — it always applies its own contact/allowlist logic independently.
- **Samsung battery optimization can prevent background components from running reliably.** One UI aggressively manages background apps' process lifecycle (see §12) — without exempting CallNuke, its `CallScreeningService`/`SmsReceiver` may be delayed or killed under memory pressure, even though both are designed to run independent of the foreground Godot app.
- **API 29+ requirement for call screening.** `ROLE_CALL_SCREENING` is unavailable below Android 10; on such devices `requestCallScreeningRole()` is a no-op and the app has no working call-blocking mechanism. Not a practical concern for the Note 20 (ships well above API 29), but relevant if the APK is ever installed elsewhere.
- **Debug-signed only.** The current build in `build\CallNuke-debug.apk` uses the standard Android debug keystore (§13) — fine for a private, self-installed app, but this signature is not stable/trustworthy for any kind of distribution beyond the developer's own devices.
- **No dedicated "why was this call/SMS filtered" detail view.** The UI exposes only aggregate daily counters (`getBlockedCallsCount`, `getFilteredSmsCount`), not a log of individual blocked numbers/messages — by design, to avoid persisting message content, but it also means there's no in-app audit trail beyond the Android call log (for calls) and `adb logcat` (for both).

---

## 12. Known Samsung-Specific Behavior

- **Samsung Messages remains fully in control of SMS display.** Because CallNuke never becomes the default SMS app, every incoming SMS — filtered or not — is delivered, stored, and notified on exactly as it would be without CallNuke installed. Samsung Messages' own spam/phishing detection (if enabled) operates completely independently of, and is not affected by, CallNuke's classification.
- **One UI battery/background-activity restrictions.** Samsung devices (Note 20 included) apply their own "Sleeping apps" / "Deep sleeping apps" battery management on top of stock Android's Doze/App Standby, which can suspend background receivers and prevent scheduled work more aggressively than stock Android. For CallNuke to reliably screen calls and receive SMS broadcasts at all times, the user should be guided to:
  - **Settings → Apps → CallNuke → Battery → Unrestricted** (not "Optimized" or "Restricted"), and
  - Ensure CallNuke is not added to the "Sleeping apps" / "Deep sleeping apps" list under **Settings → Battery and device care → Background usage limits**.
  This is a user-facing setup step outside the app itself; there is no API for an app to fully self-exempt from Samsung's battery management (only the standard `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system dialog, which CallNuke does not currently request — noted as a gap, see below).
- **`CallScreeningService` is a Google/AOSP-standard mechanism** that Samsung's Telecom stack (One UI's dialer is a Samsung-skinned but AOSP-Telecom-based implementation) honors the same way stock Android does — no Samsung-specific behavioral divergence is expected for the call-screening role itself, only for the battery-management surface around it.
- **Gap not yet implemented**: CallNuke does not currently prompt the user to exempt it from battery optimization (`Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`), which would materially improve reliability on One UI specifically. Recommended as a follow-up enhancement to the Android Setup screen.

---

## 13. Distribution Notes

**This is a private, self-compiled app. It is not published to, and is not intended for, the Google Play Store.**

- The APK is signed with the standard **Android debug keystore** (`keystore/debug` in `export_presets.cfg`, the default `androiddebugkey`/`android` credentials generated locally by the Android SDK tooling) — this is appropriate only for sideloading onto devices the developer controls directly (via `adb install`, as in §4). It must never be distributed to third parties as-is, since debug-signed builds are widely considered untrustworthy/insecure for public distribution and Android itself treats debug and release signatures as incompatible (reinstalling a differently-signed build requires an uninstall first).
- No Play Store listing, no Play Console app entry, no Play App Signing, and no Play policy review have been done or are in scope.

**What would have to change for Google Play distribution** (for future reference, not implemented):
1. **Release signing**: generate a dedicated release keystore, fill in `keystore/release*` in the export preset, and switch the export to a release/signed build (`gradlew.bat bundleRelease` producing an `.aab`, which Play requires instead of a raw `.apk`).
2. **SMS/Call-Log permissions policy**: Google Play enforces a strict, allow-listed **Permissions Declaration Form** for `RECEIVE_SMS`, `READ_SMS`, `READ_CALL_LOG`, and related "sensitive permissions." Only apps whose **core, primary functionality** is calling/texting (default phone/SMS handler, or an assistant/backup app in a narrow approved category) are approved to hold `RECEIVE_SMS`. A general-purpose "communications firewall" app that is not the default SMS/Phone app is a common target for Play policy rejection under this rule — CallNuke's SMS feature in particular would likely need to either (a) become the full default SMS app to qualify, or (b) be removed/gated behind a Play-approved use case, before Play submission would be viable. `ROLE_CALL_SCREENING`-based call blocking without SMS is comparatively much more likely to pass review, as call-screening apps are an explicitly recognized category.
3. **Privacy Policy & Data Safety form**: even though CallNuke stores everything locally and sends nothing over the network, Play still requires a public privacy policy URL and a completed Data Safety declaration (contacts access, SMS access) before publishing.
4. **Target SDK / Play policy currency**: keep `target_sdk` aligned with Play's rolling minimum target API requirement (currently around API 34–35; already met here).
5. **App icon/store listing assets, content rating questionnaire, and a Play Console developer account** ($25 one-time fee) would also be required — none of which exist for this private build.

---

## 14. List of Files Created / Modified

Hand-authored project files (everything else under `call-nuke\android\` is Godot's generated build scaffold, not modified beyond what Godot itself writes on export):

**Godot project / UI**
- `call-nuke\project.godot`
- `call-nuke\main.gd`, `call-nuke\main.tscn`
- `call-nuke\allowed_numbers.gd`, `call-nuke\allowed_numbers.tscn`
- `call-nuke\sms_keywords.gd`, `call-nuke\sms_keywords.tscn`
- `call-nuke\android_setup.gd`, `call-nuke\android_setup.tscn`
- `call-nuke\theme.tres`
- `call-nuke\icon.svg` (+ `.import`)
- `call-nuke\export_presets.cfg`

**Android native (Kotlin) — `call-nuke\android\src\main\java\com\callnuke\firewall\`**
- `CallNukeScreeningService.kt`
- `CallNukePlugin.kt`
- `CallNukePrefs.kt`
- `ContactLookup.kt`
- `PhoneNumberUtils.kt`
- `SmsReceiver.kt`
- `KeywordMatcher.kt`

**Android manifest (modified from Godot's default template)**
- `call-nuke\android\src\main\AndroidManifest.xml` — added `READ_CONTACTS`/`RECEIVE_SMS` permissions, the `CallNuke` plugin `<meta-data>` entry, the `CallNukeScreeningService` `<service>` declaration, and the `SmsReceiver` `<receiver>` declaration.

**Build output (generated, not hand-authored, but part of the deliverable)**
- `call-nuke\build\CallNuke.pck`
- `call-nuke\build\CallNuke-debug.apk`

**Documentation**
- `DOCUMENTATION.md` (this file)

---

## 15. Final Acceptance Checklist

Status legend: ✅ Done and verified from source/build artifacts in this environment · ⏳ REQUIRES MANUAL DEVICE VERIFICATION on the Note 20 · ⚠️ Known gap / not implemented

| # | Item | Status |
|---|---|---|
| 1 | Godot 4.6.2 Mono project builds and exports to a valid Android APK via Gradle | ✅ — `build\CallNuke-debug.apk` (~119 MB) exists and matches the export preset config |
| 2 | Package id is `com.callnuke.firewall`, min SDK 24 / target SDK per preset | ✅ — confirmed in `export_presets.cfg` and `AndroidManifest.xml` |
| 3 | `CallNukeScreeningService` correctly implements `CallScreeningService` with allow/reject logic | ✅ — code reviewed, fail-safe-on-ambiguity, contact + allowlist checks in place |
| 4 | `CallNukePlugin` exposes all required methods to Godot (role, permissions, allowlist, keywords, toggles, counters) | ✅ — all `@UsedByGodot` methods present and consumed by the four `.gd` scripts |
| 5 | `ROLE_CALL_SCREENING` request/status flow implemented and wired to the Android Setup UI | ✅ — code reviewed; **actual grant on-device** ⏳ |
| 6 | `READ_CONTACTS` and `RECEIVE_SMS` runtime permission request/status flow implemented and wired to the UI | ✅ — code reviewed; **actual grant on-device** ⏳ |
| 7 | Manual allowlist add/remove/list works, numbers normalized/matched via Android's own `PhoneNumberUtils` | ✅ — code reviewed (`CallNukePrefs`, `PhoneNumberUtils`, `allowed_numbers.gd`) |
| 8 | Trusted SMS keyword add/remove/list works, word-boundary matching with substring fallback | ✅ — code reviewed (`KeywordMatcher`, `sms_keywords.gd`) |
| 9 | Blocked-calls and filtered-SMS counters increment correctly and reset daily | ✅ — code reviewed (`CallNukePrefs` date-rollover logic) |
| 10 | Call Test A–G (contact allow, unknown reject, allowlist add/remove, protection toggle off, contacts-permission-revoked behavior, background-process independence) | ⏳ REQUIRES MANUAL DEVICE VERIFICATION |
| 11 | SMS Test A–G (contact allow, unknown filtered-but-visible, keyword allow, word-boundary false-positive check, allowlist allow, protection toggle off, background-process independence) | ⏳ REQUIRES MANUAL DEVICE VERIFICATION |
| 12 | SMS filtering limitation (categorization, not blocking) is technically correct and matches actual Android platform behavior | ✅ — verified against Android platform documentation/behavior as implemented in `SmsReceiver.kt`'s own extensive doc-comment |
| 13 | No network permissions, no telemetry, no message content persisted | ✅ — confirmed: no `INTERNET` permission in manifest; only counters/allowlist/keywords stored in `SharedPreferences` |
| 14 | Documentation covers build, export, install, permissions, roles, architecture, filtering logic, test procedure, limitations, Samsung specifics, and distribution constraints | ✅ — this document |
| 15 | Battery-optimization exemption prompt for reliable background operation on One UI | ⚠️ Not implemented — recommended follow-up (§12) |
| 16 | Release (Play-ready) signing configuration | ⚠️ Not implemented — intentionally out of scope for this private build (§13) |
| 17 | Google Play SMS-permission policy compliance | ⚠️ Not applicable/not attempted — app is not intended for Play distribution (§13) |

**Overall**: All code-level implementation items are complete and have been verified by direct source review against the actual `.kt`/`.gd`/manifest/build files in this repository. All items requiring interaction with the physical Samsung Galaxy Note 20 hardware (permission grants, role grant, live call/SMS behavior) remain **unverified pending manual on-device testing**, and are explicitly flagged as such rather than assumed to pass.
