# Mantic View

Bring [Manifold](https://manifold.markets) prediction markets directly into Google Sheets.

Mantic View is a [Google Apps Script project](apps-script/) that adds custom functions to your spreadsheet — and, deployed as an editor add-on, a search-and-insert sidebar, portfolio tracking, and scheduled refresh — powered by the free, no-key-required [Manifold API](https://docs.manifold.markets/api). See [`apps-script/README.md`](apps-script/README.md) for the add-on details and the Google Workspace Marketplace publishing guide.

Also in this repo: **[Mantic View for Android](android/)** — a home-screen widget with a TradingView-style watchlist of live market probabilities.

## Setup (about 1 minute)

1. Open (or create) a Google Sheets spreadsheet.
2. Go to **Extensions → Apps Script**.
3. Delete the placeholder code in the editor and paste in the contents of [`apps-script/Code.gs`](apps-script/Code.gs).
4. Click the **Save** icon (💾). No authorization prompt is needed for custom functions.
5. Back in your sheet, start typing `=MANIFOLD` in any cell — the functions appear with autocomplete.

Alternatively, make a copy of the [ready-made template](https://docs.google.com/spreadsheets/d/12rJBxcCn_i61uprzuQWXj5mz9BC8aYIlU7NlQszuqZ0/edit?usp=sharing) (**File → Make a copy**).

## Functions

Every function accepts either a market **slug** (`will-x-happen`) or the **full market URL** (`https://manifold.markets/user/will-x-happen`).

### `MANIFOLD_PROB(market)`

Current probability of a binary (YES/NO) market, as a number between 0 and 1. Format the cell as a percentage to display `87%` instead of `0.87`.

```
=MANIFOLD_PROB("will-ai-achieve-agi-by-2030")
=MANIFOLD_PROB("https://manifold.markets/someuser/will-ai-achieve-agi-by-2030")
```

### `MANIFOLD(market, [attribute])`

Any single attribute of a market. Defaults to `"probability"`.

```
=MANIFOLD("will-ai-achieve-agi-by-2030", "question")
=MANIFOLD("will-ai-achieve-agi-by-2030", "closeTime")
=MANIFOLD("will-ai-achieve-agi-by-2030", "volume24Hours")
```

Supported attributes: `probability`, `question`, `url`, `closeTime`, `isResolved`, `resolution`, `volume`, `volume24Hours`, `totalLiquidity`, `uniqueBettorCount`, `outcomeType`, `creatorName`, `lastUpdatedTime`. Time attributes come back as real dates you can format and sort.

### `MANIFOLD_ANSWERS(market)`

For multiple-choice markets: spills a two-column table (Answer, Probability), one row per answer. Binary markets return YES/NO rows.

```
=MANIFOLD_ANSWERS("who-will-win-the-2028-election")
```

### `MANIFOLD_SEARCH(term, [limit])`

Searches Manifold and spills a table of matching markets (Question, Probability, Slug, URL). `limit` defaults to 10, max 100.

```
=MANIFOLD_SEARCH("bitcoin", 5)
```

### `MANIFOLD_HISTORY(market, [points])`

Probability history of a binary market as a column of numbers, oldest first (sampled from the most recent 1000 bets; 50 points by default). Made to be wrapped in `SPARKLINE`:

```
=SPARKLINE(MANIFOLD_HISTORY("will-ai-achieve-agi-by-2030"), {"color","#4F46E5"})
```

### `MANIFOLD_USER(username, [attribute])`

Any public attribute of a Manifold user — `balance` (default), `name`, `totalDeposits`, `createdTime`, `url`, … Accepts a username, `@username`, or profile URL.

```
=MANIFOLD_USER("YourUsername", "balance")
```

### `MANIFOLD_PORTFOLIO(username)`

Spills a portfolio summary table: balance, investment value, net worth, total deposits, all-time profit, and daily profit — all in mana, all from public data.

```
=MANIFOLD_PORTFOLIO("YourUsername")
```

### `MANIFOLD_POSITIONS(username, [limit])`

Spills a user's open positions (Question, Value, Profit, Last bet, URL), largest first. `limit` defaults to 10, max 100.

```
=MANIFOLD_POSITIONS("YourUsername", 10)
```

## Refreshing data

Results are cached for 5 minutes to stay within Manifold's rate limits. Google Sheets also caches custom-function results, so to force a refresh you can pass any changing value as the **last argument** of any function — for example a cell containing `=NOW()` that recalculates on edit:

```
=MANIFOLD_PROB("will-ai-achieve-agi-by-2030", $A$1)   // where A1 = NOW()
```

With the full add-on installed, **Extensions → ManticView → Refresh markets now** re-fetches every `MANIFOLD*` formula in the spreadsheet at once, and **Auto-refresh → Every hour** keeps doing so on a schedule — even while the sheet is closed.

## Notes

- No API key or Manifold account is required; only public market data is read.
- Probabilities are raw numbers (0–1) so you can chart and compute with them directly.
- Terms of service for the hosted site: [TOS.html](TOS.html) · privacy policy: [privacy.html](privacy.html).

## License

[MIT](LICENSE) © JesusDeSivar
