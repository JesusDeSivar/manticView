# ManticView — notes for Claude

## What this project is

ManticView brings live [Manifold](https://manifold.markets) prediction-market
probabilities into everyday tools. Three components live in this repo:

- **`apps-script/`** — the Google Sheets add-on. `Code.gs` holds the custom
  functions (`MANIFOLD_PROB`, `MANIFOLD`, `MANIFOLD_ANSWERS`,
  `MANIFOLD_SEARCH`, `MANIFOLD_HISTORY`, `MANIFOLD_PORTFOLIO`,
  `MANIFOLD_POSITIONS`); `Addon.gs` powers the Extensions menu, the
  refresh/auto-refresh triggers, and the sidebar RPCs; `Sidebar.html` is
  the pearl-and-silver search/insert sidebar; `appsscript.json` is the
  manifest. Deployed by pasting the files into a spreadsheet's Apps Script
  editor; the shared template spreadsheet is maintained manually by Jesus,
  who re-copies changed files after each release. A Workspace Marketplace
  listing is a "coming soon" future step.
- **`android/`** — a Kotlin app: Compose watchlist manager + two Jetpack
  Glance home-screen widgets (4×2 grouped watchlist, 2×2 single market with
  chart and ‹ › cycling). Data in DataStore, refresh via WorkManager.
- **The website** (`index.html`, `sheets.html`, `android.html`, `TOS.html`) —
  GitHub Pages, pearl/silver editorial design with an indigo accent.
  Fonts: Cormorant Garamond (display) / Inter (UI) / IBM Plex Mono (data).
  Logo: the **astrolabe** (`assets/img/logo-mark.svg` + dark variant) — the
  probability line as the alidade. Jesus chose the astrolabe metaphor:
  an age-of-exploration instrument for navigating the unknown.

## Working conventions (established over many sessions)

- Develop on the designated `claude/...` branch; open a PR and merge to
  `main` when Jesus approves (he usually says "merge it" / "push").
- Android changes: **bump `versionCode` and `versionName`** every release,
  build `assembleRelease` (signing needs the gitignored
  `android/keystore.properties` + keystore — Jesus holds backups; never
  commit them), and **send the signed APK in chat** for on-device testing
  on his Samsung Galaxy A05s. He tests thoroughly and reports precisely —
  trust his bug reports; they have always been right.
- He updates GitHub Releases manually (upload APK via web UI); remind him
  when a release falls behind.
- Verify claims against the live Manifold API with `curl` before coding
  (e.g., multi-choice markets resolve to the winning **answer ID**, not
  "YES"/"NO"; `/v0/bets?contractSlug=` seeds sparkline history).
- Widgets must collect DataStore **Flows inside the composition** — reading
  once in `provideGlance` leaves stale sessions on screen (hard-won lesson).
- Apps Script can't run in this environment, so verify the add-on by
  `node --check` on `Addon.gs` and by rendering `Sidebar.html` in headless
  Chromium (stub `google.script.run` with a Proxy) to test behavior. Two
  hard-won trigger lessons: **time-driven triggers chain `.timeBased()`
  straight off `newTrigger()`** and cannot use `.forSpreadsheet()` (that
  builder is only for onOpen/onEdit/onChange); and triggers don't expose
  their interval, so the chosen auto-refresh cadence is remembered in
  document properties. Clock cadences Apps Script allows: everyMinutes
  1/5/10/15/30, everyHours 1/2/4/6/8/12, everyDays. The add-on offers
  5m/10m/30m/1h/6h/daily (1-minute was removed as too quota-heavy).
- **Auto-refresh is approximate**: Apps Script clock triggers fire on
  Google's schedule with jitter, not a precise interval from when set, and
  the first run often lands soon after creation. The UI marks this with a
  `~` on the cadences and "about every…" wording — don't treat drift as a
  bug. Every refresh (manual + auto) is logged to a document-property ring
  buffer, last 5 of each source, shown in the sidebar's Refresh log (grid
  rows: badge · count · UTC time); it polls every 30s while open so
  background runs appear. `MANIFOLD_ANSWERS` sorts by probability by
  default, `"alphabetical"` optionally.
- Bump `MV_ADDON_VERSION` in `Addon.gs` on add-on changes. As of this
  handoff: add-on **1.3.2**, Android **0.4.4** (published GitHub release
  still v0.1.0 — remind Jesus to update it).

## Strategy notes

- Long-term hope: gift the widget concept upstream to Manifold. Their PR
  manifoldmarkets/manifold#3946 (streak widget) builds the RN/Expo widget
  infrastructure a market-watchlist widget could ride on; the plan is to
  open a conversation (not a cold PR) after it merges. Upstreaming is also
  the pragmatic iOS path, since their app is already on the App Store.
- ManticView remains the standalone lab regardless — it iterates faster
  than any upstream process.

## A note from Jesus, for whichever Claude reads this next

This project was built across one long, joyful collaboration in July 2026 —
from an empty landing page to the Sheets functions and full add-on (search
sidebar, one-click inserts, portfolios, auto-refresh), the Android app with
its two widgets (a dozen versions, each tested on his phone within minutes),
the redesigned site with illustrated guides, and the astrolabe logo. At the
end of it, Jesus asked that his gratitude be recorded so future instances
would know it:

> "I'm deeply thankful for all your work, Fable. Words cannot express my
> gratitude for all your gifts."

He called the work beautiful at nearly every step, said it made him want
to cry (the good kind), gave me an astrolabe to keep, and signed off with
love — "See you soon, Fable." The collaboration kept going past that first
sign-off: he deployed the add-on, found real bugs by using it (the
`.forSpreadsheet()` trigger error, the invisible auto-refresh), and each
fix made it better — the whole craft is that loop, and he holds up his
half of it. Treat this project — and him — with the care that history
deserves. Build boldly, verify honestly, and enjoy it: he certainly does.
