# ManticView for Google Sheets

Live [Manifold](https://manifold.markets) prediction-market data in Google
Sheets — as a set of custom functions and, with the files in this directory
deployed together, a full **editor add-on** with a search sidebar, one-click
inserts, portfolio tracking, and scheduled refresh.

## Files

| File | Role |
| --- | --- |
| `Code.gs` | The custom functions. **Self-contained** — pasting this single file into any spreadsheet's Apps Script editor still works, exactly as before. |
| `Addon.gs` | Add-on layer: Extensions menu, refresh (cache-bust + recalc), hourly auto-refresh trigger, and the sidebar's server functions. |
| `Sidebar.html` | The sidebar UI, in the ManticView pearl/indigo design. |
| `appsscript.json` | Manifest: V8 runtime, minimal OAuth scopes, and a URL whitelist restricted to `api.manifold.markets`. |

## Custom functions

| Function | Returns |
| --- | --- |
| `MANIFOLD_PROB(market)` | Live probability of a binary market (0–1). |
| `MANIFOLD(market, attribute)` | Any market attribute — `question`, `closeTime`, `volume24Hours`, `isResolved`, … |
| `MANIFOLD_ANSWERS(market)` | Answers table for multiple-choice markets (spills). |
| `MANIFOLD_SEARCH(term, limit)` | Search results table (spills). |
| `MANIFOLD_HISTORY(market, points)` | Probability history as a column — wrap in `SPARKLINE()`. |
| `MANIFOLD_USER(username, attribute)` | Any public attribute of a user — `balance` by default. |
| `MANIFOLD_PORTFOLIO(username)` | Portfolio summary table: balance, net worth, profit… |
| `MANIFOLD_POSITIONS(username, limit)` | Open positions table, largest first (spills). |

`market` is a slug or full URL; `username` is a name, `@name`, or profile
URL. Every function takes an optional trailing `refresh` argument — pass a
cell containing `=NOW()` to recalculate on every sheet recalc. Everything
reads Manifold's **public** API: no API key, no account, nothing stored.

## Developing with clasp

```bash
npm install -g @google/clasp
clasp login
cd apps-script
clasp create --type sheets --title "ManticView (dev)"   # first time only
clasp push                                              # upload these files
```

Add `"fileExtension": "gs"` (and `"rootDir": "."`) to the generated
`.clasp.json` so clasp keeps the `.gs` names used in this repo. Don't commit
`.clasp.json` — it pins your personal script ID.

To try the add-on experience: open the bound spreadsheet, reload, and the
**Extensions → ManticView (dev)** menu appears. For the real add-on install
flow use the script editor's **Deploy → Test deployments** and add a test
install for Sheets.

## Rate limits & caching

- API responses are cached for 5 minutes (`CacheService`), well within
  Manifold's public limit (~500 requests/minute per IP).
- **Refresh now** bumps a cache-salt so every formula refetches once, then
  rewrites each `MANIFOLD*` formula to defeat Sheets' own memoization.
- **Auto-refresh** runs the same routine on a clock trigger at the cadence
  you choose — 5m / 10m / 30m / 1h / 6h / daily. Apps Script clock triggers
  fire on Google's schedule with some jitter, so the interval is
  approximate; the chosen cadence is remembered in document properties, and
  each run (manual or automatic) is written to a small ring buffer surfaced
  as the sidebar's **Refresh log**.
