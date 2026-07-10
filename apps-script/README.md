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

## Publishing to the Google Workspace Marketplace

The one-time checklist, in order:

1. **GCP project** — create a standard Google Cloud project and link it to
   the script (Apps Script editor → Project settings → change project).
2. **OAuth consent screen** — configure it in the GCP console: app name
   *ManticView*, logo, support email, authorized domain
   `jesusdesivar.github.io`, links to the
   [privacy policy](https://jesusdesivar.github.io/manticView/privacy.html)
   and [terms](https://jesusdesivar.github.io/manticView/TOS.html), and the
   four scopes from `appsscript.json`.
3. **Scope verification** — `script.external_request` is a sensitive scope,
   so Google reviews the app before public listing. The justification is
   short and honest: the add-on fetches public, read-only market data from
   `api.manifold.markets` (enforced by `urlFetchWhitelist`) and touches only
   the current spreadsheet (`spreadsheets.currentonly`).
4. **Versioned deployment** — in the script editor create a deployment
   (Deploy → New deployment → Add-on) and note the deployment ID.
5. **Marketplace SDK** — enable the *Google Workspace Marketplace SDK* in
   the GCP project. Under *App configuration* choose **Sheets add-on**,
   paste the deployment ID and version. Under *Store listing* add the
   description, screenshots (1280×800), icons (128px and 32px — the
   astrolabe from `assets/img/`), category *Productivity*, pricing *Free*.
6. **Submit for review.** Reviews commonly take a few days to a couple of
   weeks; Google emails follow-ups through the process.

## Rate limits & caching

- API responses are cached for 5 minutes (`CacheService`), well within
  Manifold's public limit (~500 requests/minute per IP).
- **Refresh now** bumps a cache-salt so every formula refetches once, then
  rewrites each `MANIFOLD*` formula to defeat Sheets' own memoization.
- Auto-refresh runs the same routine on an hourly time trigger — hourly is
  the fastest Google allows for editor add-on triggers.
