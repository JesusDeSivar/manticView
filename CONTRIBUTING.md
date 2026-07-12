# Contributing to Mantic View

Thanks for your interest in Mantic View! This is a small, MIT-licensed
project that brings live [Manifold](https://manifold.markets) prediction-market
probabilities into everyday tools. Contributions — bug reports, fixes, and
features — are welcome.

## The repository at a glance

| Directory | What it is |
| --- | --- |
| [`apps-script/`](apps-script/) | The Google Sheets add-on: custom functions (`Code.gs`), the Extensions-menu / trigger / RPC layer (`Addon.gs`), the search sidebar (`Sidebar.html`), and the manifest (`appsscript.json`). |
| [`android/`](android/) | A Kotlin app: a Compose watchlist manager plus two Jetpack Glance home-screen widgets. |
| Root `*.html`, `assets/` | The project website (GitHub Pages). |

Everything reads Manifold's **public** API — no API key, no account, and no
user data is stored.

## Ground rules

- Keep it public-safe: only public, read-only Manifold data, and no
  credentials, keystores, or personal deployment details in the repo.
- Match the style of the code and prose around your change.
- Verify your claims against the live Manifold API before coding — the API
  has sharp edges (for example, multi-choice markets resolve to the winning
  **answer ID**, not `"YES"`/`"NO"`).

## Working on the Google Sheets add-on

Apps Script can't execute in a normal local environment, so verify the two
ways that don't need a Google account:

```bash
# 1. Syntax-check the script files
node --check apps-script/Code.gs
node --check apps-script/Addon.gs
```

For the sidebar UI, render `apps-script/Sidebar.html` in a browser and stub
`google.script.run` (e.g. with a `Proxy`) to exercise its behavior without a
live spreadsheet.

To try it inside Sheets, paste the files into a spreadsheet's **Extensions →
Apps Script** editor, or use [`clasp`](https://github.com/google/clasp) —
see [`apps-script/README.md`](apps-script/README.md) for the clasp flow. A
couple of Apps Script gotchas worth knowing:

- Time-driven triggers chain `.timeBased()` directly off `newTrigger()`;
  `.forSpreadsheet()` is only for `onOpen`/`onEdit`/`onChange` triggers.
- Triggers don't expose their interval, so the chosen auto-refresh cadence
  is remembered in document properties. Allowed clock cadences: `everyMinutes`
  1/5/10/15/30, `everyHours` 1/2/4/6/8/12, `everyDays`.

Bump `MV_ADDON_VERSION` in `Addon.gs` when you change add-on behavior.

## Working on the Android app

```bash
cd android
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
```

Or open `android/` in Android Studio. Requirements: Android Studio with an
Android SDK; min SDK 26, target SDK 35. Add the widgets from your launcher's
widget picker to test them. Bump `versionCode` and `versionName` in
`android/app/build.gradle.kts` for releases. Release signing needs your own
keystore and a `keystore.properties` (both gitignored) — see
[`android/README.md`](android/README.md).

One hard-won lesson: widgets must collect DataStore **Flows inside the
composition** — reading once in `provideGlance` leaves stale data on screen.

## Working on the website

The site is plain static HTML/CSS in the repo root (`index.html`,
`sheets.html`, `android.html`, `privacy.html`, `TOS.html`) with styles in
`assets/css/style.css`. Open the files directly in a browser to preview.

## Pull requests

1. Branch off `main`.
2. Keep changes focused, with a clear description of what and why.
3. Note how you verified the change (the checks above).
4. Open a PR against `main`.

By contributing, you agree that your contributions are licensed under the
project's [MIT License](LICENSE).
