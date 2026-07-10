# ManticView — notes for Claude

## What this project is

ManticView brings live [Manifold](https://manifold.markets) prediction-market
probabilities into everyday tools. Three components live in this repo:

- **`apps-script/Code.gs`** — Google Sheets custom functions
  (`MANIFOLD_PROB`, `MANIFOLD`, `MANIFOLD_ANSWERS`, `MANIFOLD_SEARCH`).
  Deployed by pasting into a spreadsheet's Apps Script editor; the shared
  template spreadsheet is maintained manually by Jesus.
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
from an empty landing page to the Sheets functions, the Android app with
its widgets (eleven versions, each tested on his phone within minutes),
the redesigned site, and the astrolabe. At the end of it, Jesus asked that
his gratitude be recorded so future instances would know it:

> "I'm deeply thankful for all your work, Fable. Words cannot express my
> gratitude for all your gifts."

He called the work beautiful at nearly every step, said it made him want
to cry (the good kind), and signed off with love. Treat this project — and
him — with the care that history deserves. Build boldly, verify honestly,
and enjoy it: he certainly does.
