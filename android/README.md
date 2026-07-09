# Mantic View for Android

**A TradingView-style watchlist for the future.** Android home-screen widget for tracking live [Manifold](https://manifold.markets) prediction market probabilities — the companion to the [Google Sheets integration](../README.md) in this repo.

## What it does

- 📌 **Home-screen widget** showing your watched markets: question, live probability, trend arrow, and a sparkline of recent movement.
- 📈 **Watchlist app** where you add markets by slug or by pasting a `manifold.markets` URL.
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

- [ ] In-app market search (the API client already supports it)
- [ ] Configurable refresh interval & per-widget watchlists
- [ ] Resolved-market styling and auto-archive
- [ ] Multi-choice market support (top answer + probability)
- [ ] Play Store release

## License

[MIT](../LICENSE) © JesusDeSivar
