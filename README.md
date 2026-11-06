<div align="center">

# 📢 AIUB Notices

**A native Android app that watches the official AIUB notice board — and taps you on the shoulder the moment something new is posted.**

[![CI](https://github.com/Shishir-ip/AIUB-Notices/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Shishir-ip/AIUB-Notices/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Shishir-ip/AIUB-Notices?include_prereleases&logo=github)](https://github.com/Shishir-ip/AIUB-Notices/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-004EA2.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
[![API](https://img.shields.io/badge/API-26%2B-004EA2?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)

</div>

---

## What it does

[AIUB](https://www.aiub.edu) publishes every official announcement — exam schedules, fee deadlines, results, events — on its [public notice board](https://www.aiub.edu/category/notices). But there is no way to *know* when something new appears: you have to keep checking the page yourself.

**AIUB Notices** fixes that with zero accounts, zero servers and zero tracking:

- 🔔 **Phone notifications** for every new notice (heads-up, importance HIGH)
- 🗓️ **Background polling** every 15/30/60 minutes via WorkManager — or a **real-time mode** that checks about once a minute so notifications land as fast as the device allows
- 📰 **Offline notice list** — the latest 30 notices are cached locally with DataStore
- 🌐 **One-tap open** — tapping a notice (or its notification) opens the official page in your browser
- 🚫 **No spam** — a baseline run marks everything as seen on first launch; at most 5 notifications per poll; grouped under one summary when several arrive together
- 🛡️ **Never crashes on site changes** — parsing failures, HTTP errors and layout changes just skip a cycle
- 🎨 **Material 3 dark UI** with the AIUB blue (`#004EA2`) accent

> ⚠️ **Disclaimer** — This is an unofficial, open-source student project and is **not affiliated with or endorsed by American International University-Bangladesh**. It only reads the public notice board, politely (one request per poll).

---

## Screens

| Notices list | Settings |
|:---:|:---:|
| Latest 30 notices, date + title + description, pull-to-refresh, "Monitoring started" banner on first launch | Toggle notifications, choose the 15/30/60-minute poll interval, send a test notification |

---

## How the scraper works

The notice board is **plain server-rendered HTML** — no JavaScript, no API keys. Each poll:

1. `GET https://www.aiub.edu/category/notices` with a custom User-Agent
   (`AIUBNoticesApp/1.0 (student project)`), 15s connect/read timeouts — **exactly one request**.
2. Parses each notice card with [Jsoup](https://jsoup.org):

   ```html
   <a href="/notice-slug">
     <div class="notification">
       <div class="date-custom ...">04 Nov <span>2026</span></div>
       <div class="notification-text">
         <h2 class="title">Notice title here ...</h2>
         <p class="desc">Short description</p>
         <a class="info-link" href="/notice-slug">Details</a>
       </div>
     </div>
   </a>
   ```

   - **Title** → `selectFirst("h2.title").text()`
   - **Description** → `selectFirst("p.desc").text()`
   - **Date** → `.date-custom` text (`"04 Nov 2026"`); the year comes from the inner `<span>`, falling back to the current year when it's missing
   - **URL** → the closest ancestor `a[href]`, resolved against `https://www.aiub.edu`
   - Jsoup's `.text()` unescapes HTML entities (`&amp;` → `&`) automatically

3. Diffs the parsed URLs against the **seen set** persisted in DataStore (capped at the last 500 entries).
4. New notices → notification channel `aiub_notices` (HIGH importance), max 5 per poll, grouped with a summary notification.
5. Everything currently on the page is recorded as seen.

Reliability rules baked in:

- HTTP ≠ 200 or **zero notices parsed** → the cycle is skipped and logged, nothing is marked or cleared
- Network failures → WorkManager `Result.retry()` with `BackoffPolicy.EXPONENTIAL`
- Periodic work is **not expedited**, so Android may defer it under Doze — notifications can arrive late, by design
- **Real-time mode** is a self-rescheduling chain of one-minute one-time checks (WorkManager's periodic floor is 15 minutes). The chain re-reads settings on every run and stops by itself when you switch modes. Android may still defer checks under Doze — so when you pick real-time, Settings shows a small **skippable** prompt to allow the app to run without battery restrictions (one tap in the system dialog; skipping just hides the prompt).

- Manual "Refresh now" + pull-to-refresh always work when the app is open (and are silent — you're already looking at the list)

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin, minSdk 26 · targetSdk 35 |
| UI | Jetpack Compose + Material 3 (dark by default, AIUB blue accent) |
| HTML parsing | [Jsoup](https://jsoup.org) |
| Background work | [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager): periodic (15/30/60 min) or real-time chain (~1 min, CONNECTED constraint) |
| Persistence | [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) (seen-set, cache, settings) |
| Notifications | NotificationCompat, single HIGH-importance channel, grouped summaries |
| Tests | JUnit 4 + a saved HTML fixture of the real page |

**Permissions:** `INTERNET` and `POST_NOTIFICATIONS`, plus `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (optional — only used to show the system battery dialog for real-time mode; the user can always decline or skip). No backend, no accounts, no analytics.

---

## Build it yourself

Requirements: **Android Studio** (or just the Android SDK 35 + JDK 17+), internet for the first Gradle sync.

```bash
git clone https://github.com/Shishir-ip/AIUB-Notices.git
cd AIUB-Notices
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # parser tests
```

Then open the project in Android Studio and press ▶, or install the APK manually:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release signing

The repository intentionally does **not** contain the release keystore. To build a signed release:

1. Generate a keystore (once):

   ```bash
   keytool -genkeypair -v \
     -keystore keystore/aiub-notices.jks \
     -alias aiub-notices -keyalg RSA -keysize 4096 -validity 10950
   ```

2. Create `keystore/keystore.properties` (gitignored):

   ```properties
   storeFile=aiub-notices.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=aiub-notices
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. Build: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

> 🔐 **Keep `keystore/` safe and backed up** — losing it means you can never update the app in place. If no keystore is present, `assembleRelease` falls back to the debug key so CI never breaks.

---

## Manual test checklist

1. **Install & baseline** — install the app, grant the notification permission on first launch. The app fetches the board, marks everything currently on it as seen *silently*, and shows the green **"Monitoring started"** banner. No notifications yet.
2. **Poll finds nothing new** — tap ⏻/refresh, or wait for a background cycle. The list refreshes, no notifications appear (everything is already seen).
3. **New notice → notification** — when AIUB posts a new notice (or test by clearing the app's *seen* data: Settings → Apps → AIUB Notices → Storage → Clear data, then wait for a poll): a heads-up notification appears with the notice title and `date · description`. Tapping it opens the notice in the browser.
4. **Grouping** — with several unseen notices, at most 5 child notifications appear under one summary notification ("N new AIUB notices").
5. **Offline behavior** — enable airplane mode and refresh: an error banner appears, the cached list stays usable, the app does not crash. Background polls simply retry with backoff.
6. **Test notification** — Settings → *Send test notification* posts a sample instantly.
7. **Interval** — Settings → pick 30 or 60 min; WorkManager reschedules (`adb shell dumpsys jobscheduler | grep aiub` shows the updated periodic job).
8. **Real-time mode** — Settings → pick *Real-time*: an immediate check runs, then roughly one per minute. If battery optimization is still on, a small prompt appears with **Allow** / **Skip**. *Allow* opens the one-tap system dialog (recommended for real-time reliability); *Skip* hides the prompt until you re-select real-time. Switching back to 15/30/60 min stops the chain automatically.

---

## Project structure

```
app/src/main/java/io/github/shishirip/aiubnotices/
├── AiubNoticesApp.kt          # Application: creates the notification channel
├── MainActivity.kt            # Single activity + POST_NOTIFICATIONS request
├── data/
│   ├── Notice.kt              # Notice model
│   ├── NoticeParser.kt        # Jsoup parsing (unit-tested against a fixture)
│   ├── NoticeRepository.kt    # One HTTP request + sync/diff logic
│   └── NoticeStore.kt         # DataStore: seen set, cache, settings
├── notify/
│   └── Notifier.kt            # Channel, grouped notifications, test notification
├── work/
│   ├── NoticePollWorker.kt    # CoroutineWorker: one poll cycle
│   └── PollScheduler.kt       # Unique periodic work, 15/30/60 min
└── ui/
    ├── AiubNoticesApp.kt      # Scaffold + screen switching
    ├── NoticesScreen.kt       # List, pull-to-refresh, banners
    ├── SettingsScreen.kt      # Toggles, interval, test button, about
    ├── AppViewModel.kt        # UiState + baseline + refresh
    └── theme/Theme.kt         # Material 3 dark theme, AIUB blue
```

---

## License

[MIT](LICENSE) © Shohidul Islam
