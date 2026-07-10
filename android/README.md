# Mantic View for Android

**A TradingView-style watchlist for the future.** Android home-screen widget for tracking live [Manifold](https://manifold.markets) prediction market probabilities — the companion to the [Google Sheets integration](../README.md) in this repo.

## What it does

- 📌 **Watchlist widget (4×2)** showing your watched markets TradingView-style: question, live probability, and the trend over your chosen period. Scrolls when the list outgrows the widget, with markets organized under group headers.
- 📊 **Single-market widget (2×2)** with a big probability, delta, and a large sparkline chart seeded from real bet history. Configure it to follow one group (or all markets) and flip through them with ‹ › buttons right on the widget.
- 🗂️ **Groups (watchlists)**: organize markets by category — reorder them, rename them (widgets follow along), and hide entire groups from the watchlist widget while keeping them in the app.
- 📈 **Watchlist app** where you add markets by slug, URL, or search. YES/NO markets are tracked directly; for multiple-choice markets pick which answer to follow — or watch several answers of the same market side by side. Reorder anything, and pick a comparison period per market (1H / 6H / 1D / 1W / 1M / ALL) that drives both the delta and the chart window.
- 🏁 **Resolved markets** show their outcome (green Won/YES, red Lost/NO), linger on the widget for 48 hours, then auto-archive to the app's Resolved section.
- 🎨 **Theme** picker (System / Light / Dark) for the app and both widgets, and a spinner while a manual refresh runs.
- 🔋 **Battery-friendly refresh**: probabilities update every 30 minutes in the background via WorkManager (only when online), with an on-demand ↻ button on the widget itself. Background work stops automatically when you remove your last widget.
- 🔓 Uses the free public [Manifold API](https://docs.manifold.markets/api) — no account, no API key.

Tapping a market on a widget opens it on manifold.markets; tapping the header opens the watchlist app.

## Tech

| Piece | Choice |
|---|---|
| Language | Kotlin |
| Widget | Jetpack Glance (`glance-appwidget`) |
| App UI | Jetpack Compose + Material 3 |
| Background refresh | WorkManager (periodic, network-constrained) |
| Storage | DataStore (Preferences) with kotlinx.serialization |
| Sparklines | Rendered to a Bitmap (Glance has no canvas primitives) |
| HTTP | OkHttp |

Min SDK 26 (Android 8.0), target SDK 35.

## Building

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Or open the project in Android Studio and run it on a device/emulator. Add the widgets from your launcher's widget picker ("Manifold watchlist" / "Manifold market").

### Release builds

Signed release builds need two gitignored files in `android/` — the release keystore and a `keystore.properties` describing it:

```properties
storeFile=mantic-release.keystore
storePassword=…
keyAlias=mantic
keyPassword=…
```

Then `./gradlew assembleRelease` produces a signed, minified APK in `app/build/outputs/apk/release/`. Never commit the keystore or its passwords; losing them means existing installs can never be updated.

## Why not truly live?

Android budgets background work for widgets — periodic refresh below ~15–30 minutes isn't reliably honored, which is fine for prediction markets (they move slower than crypto). The widget's ↻ button and the app's "Refresh all" fetch on demand whenever you want the exact now.

## Roadmap

- [x] In-app market search
- [x] Multi-choice market support: pick the tracked answer, or watch several
      answers of the same market as separate entries
- [x] Groups/watchlists: organize markets by category, shown as sections on
      the watchlist widget; the 2×2 widget can follow one group (or all) and
      cycles within it
- [x] Per-market comparison period for delta and sparkline (1H/6H/1D/1W/1M/ALL)
- [x] Single-market 2×2 widget with a large chart, configurable at placement
- [x] Widget light/dark/system theme setting
- [x] Resolved-market outcomes (Won/Lost/YES/NO) and auto-archive: resolved
      markets linger on the watchlist widget for 48 hours, then move to the
      app's Resolved section until removed
- [x] Group management: rename (with widget migration), reorder, hide from
      the watchlist widget; reorder markets within a group
- [x] Scrollable watchlist widget
- [ ] Configurable refresh interval
- [ ] Play Store release

## License

[MIT](../LICENSE) © JesusDeSivar
