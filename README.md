# Mantic View

Bring [Manifold](https://manifold.markets) prediction markets directly into Google Sheets.

Mantic View is a small [Google Apps Script](apps-script/Code.gs) that adds custom functions to your spreadsheet, powered by the free, no-key-required [Manifold API](https://docs.manifold.markets/api).

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

## Refreshing data

Results are cached for 5 minutes to stay within Manifold's rate limits. Google Sheets also caches custom-function results, so to force a refresh you can pass any changing value as the **last argument** of any function — for example a cell containing `=NOW()` that recalculates on edit:

```
=MANIFOLD_PROB("will-ai-achieve-agi-by-2030", $A$1)   // where A1 = NOW()
```

## Notes

- No API key or Manifold account is required; only public market data is read.
- Probabilities are raw numbers (0–1) so you can chart and compute with them directly.
- Terms of service for the hosted site: [TOS.html](TOS.html).

## License

[MIT](LICENSE) © JesusDeSivar
