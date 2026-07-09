# Mantic View for Android

**A TradingView-style watchlist for the future.** Android home-screen widget for tracking live [Manifold](https://manifold.markets) prediction market probabilities — the companion to the [Google Sheets integration](../README.md) in this repo.

## What it does

- 📌 **Watchlist widget (4×2)** showing your watched markets TradingView-style: question, live probability, and the trend over your chosen period.
- 📊 **Single-market widget (2×2)** you configure when placing it: one market with a big probability, delta, and a large sparkline chart seeded from real bet history.
- 📈 **Watchlist app** where you add markets by slug, URL, or search. YES/NO markets are tracked directly; multiple-choice markets track an answer of your choice.
- 🎨 **Widget theme** picker (System / Light / Dark) and a spinner on the widgets while a manual refresh runs.
- 🔋 **Battery-friendly refresh**: probabilities update every 30 minutes in the background via WorkManager (only when online), with an on-demand ↻ button on the widget itself. Background work stops automatically when you remove your last widget.
- 🔓 Uses the free public [Manifold API](https://docs.manifold.markets/api) — no account, no API key.

Tapping a market on the widget opens it on manifold.markets; tapping the header opens the watchlist app.

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
- [x] Multi-choice market support, with a picker for which answer to track
- [x] Per-market comparison period for delta and sparkline (1H/6H/1D/1W/1M/ALL)
- [x] Single-market 2×2 widget with a large chart, configurable at placement
- [x] Widget light/dark/system theme setting
- [ ] Configurable refresh interval & per-widget watchlists
- [ ] Resolved-market styling and auto-archive
- [ ] Play Store release

## License

[MIT](../LICENSE) © JesusDeSivar
