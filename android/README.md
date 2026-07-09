# Mantic View for Android

**A TradingView-style watchlist for the future.** Android home-screen widget for tracking live [Manifold](https://manifold.markets) prediction market probabilities — the companion to the [Google Sheets integration](../README.md) in this repo.

## What it does

- 📌 **Home-screen widget** showing your watched markets: question, live probability, 24-hour trend, and a sparkline of recent movement — seeded from real bet history so it's meaningful the moment you add a market.
- 📈 **Watchlist app** where you add markets by slug or by pasting a `manifold.markets` URL. YES/NO markets are tracked directly; multiple-choice markets track their top answer.
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

Or open the project in Android Studio and run it on a device/emulator. Add the widget from your launcher's widget picker ("Manifold watchlist").

## Why not truly live?

Android budgets background work for widgets — periodic refresh below ~15–30 minutes isn't reliably honored, which is fine for prediction markets (they move slower than crypto). The widget's ↻ button and the app's "Refresh all" fetch on demand whenever you want the exact now.

## Roadmap

- [x] In-app market search
- [x] Multi-choice market support, with a picker for which answer to track
- [x] Per-market comparison period for delta and sparkline (1H/6H/1D/1W/1M/ALL)
- [ ] Configurable refresh interval & per-widget watchlists
- [ ] Resolved-market styling and auto-archive
- [ ] Play Store release

## License

[MIT](../LICENSE) © JesusDeSivar
