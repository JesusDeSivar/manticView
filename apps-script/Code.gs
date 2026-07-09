/**
 * Mantic View — Manifold Markets custom functions for Google Sheets.
 *
 * Setup: in your spreadsheet open Extensions > Apps Script, paste this file
 * into the editor (replacing the default Code.gs) and save. The functions
 * below become available in your sheet immediately.
 *
 * All functions accept either a market slug ("will-x-happen") or a full
 * market URL ("https://manifold.markets/user/will-x-happen").
 *
 * Data comes from the public Manifold API (https://docs.manifold.markets/api)
 * and needs no API key. Responses are cached for 5 minutes to stay well
 * within rate limits; pass a changing value as the last `refresh` argument
 * (e.g. a cell with NOW() in it) to force a recalculation.
 */

var MANIFOLD_API_BASE = 'https://api.manifold.markets/v0';
var CACHE_SECONDS = 300;

/**
 * Returns the current probability (0–1) of a binary Manifold market.
 * Format the cell as a percentage to display it as one.
 *
 * @param {string} market Market slug or full manifold.markets URL.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {number} Probability between 0 and 1.
 * @customfunction
 */
function MANIFOLD_PROB(market, refresh) {
  var m = fetchMarket_(market);
  if (typeof m.probability !== 'number') {
    throw new Error('Market "' + m.question + '" is not a binary market. Use MANIFOLD_ANSWERS for multiple-choice markets.');
  }
  return m.probability;
}

/**
 * Returns a single attribute of a Manifold market.
 *
 * Supported attributes: "probability" (default), "question", "url",
 * "closeTime", "isResolved", "resolution", "volume", "volume24Hours",
 * "totalLiquidity", "uniqueBettorCount", "outcomeType", "creatorName",
 * "lastUpdatedTime".
 *
 * @param {string} market Market slug or full manifold.markets URL.
 * @param {string} [attribute] Attribute name. Defaults to "probability".
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return The requested attribute value.
 * @customfunction
 */
function MANIFOLD(market, attribute, refresh) {
  var m = fetchMarket_(market);
  var attr = (attribute || 'probability').toString();
  var timeFields = { closeTime: true, createdTime: true, lastUpdatedTime: true, resolutionTime: true };

  if (!(attr in m)) {
    if (attr === 'probability') {
      throw new Error('Market "' + m.question + '" has no single probability. Use MANIFOLD_ANSWERS for multiple-choice markets.');
    }
    throw new Error('Unknown attribute "' + attr + '". See function help for supported attributes.');
  }
  var value = m[attr];
  if (timeFields[attr] && typeof value === 'number') {
    return new Date(value);
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return value;
}

/**
 * Returns the answers of a multiple-choice Manifold market as a table
 * with columns: Answer, Probability.
 *
 * @param {string} market Market slug or full manifold.markets URL.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array>} One row per answer.
 * @customfunction
 */
function MANIFOLD_ANSWERS(market, refresh) {
  var m = fetchMarket_(market);
  if (!m.answers || !m.answers.length) {
    if (typeof m.probability === 'number') {
      return [['Answer', 'Probability'], ['YES', m.probability], ['NO', 1 - m.probability]];
    }
    throw new Error('Market "' + m.question + '" has no answers.');
  }
  var rows = [['Answer', 'Probability']];
  m.answers.forEach(function (a) {
    rows.push([a.text, typeof a.probability === 'number' ? a.probability : '']);
  });
  return rows;
}

/**
 * Searches Manifold markets and returns a table with columns:
 * Question, Probability, Slug, URL.
 *
 * @param {string} term Search term.
 * @param {number} [limit] Max results (1–100). Defaults to 10.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array>} One row per market.
 * @customfunction
 */
function MANIFOLD_SEARCH(term, limit, refresh) {
  if (!term) throw new Error('Provide a search term.');
  var n = Math.max(1, Math.min(100, Math.floor(Number(limit) || 10)));
  var url = MANIFOLD_API_BASE + '/search-markets?term=' + encodeURIComponent(term) + '&limit=' + n;
  var markets = fetchJson_(url);
  var rows = [['Question', 'Probability', 'Slug', 'URL']];
  markets.forEach(function (m) {
    rows.push([
      m.question,
      typeof m.probability === 'number' ? m.probability : '',
      m.slug,
      m.url
    ]);
  });
  return rows;
}

/**
 * Extracts the market slug from a slug or full manifold.markets URL.
 */
function parseSlug_(market) {
  if (!market) throw new Error('Provide a market slug or URL.');
  var s = market.toString().trim();
  var match = s.match(/manifold\.markets\/[^\/]+\/([^\/?#\s]+)/);
  if (match) return match[1];
  return s.replace(/[?#].*$/, '').replace(/^\/+|\/+$/g, '');
}

/**
 * Fetches a market by slug, with caching.
 */
function fetchMarket_(market) {
  var slug = parseSlug_(market);
  return fetchJson_(MANIFOLD_API_BASE + '/slug/' + encodeURIComponent(slug));
}

/**
 * GET a URL and parse JSON, using CacheService to avoid hammering the API.
 */
function fetchJson_(url) {
  var cache = CacheService.getScriptCache();
  var key = 'mv:' + url;
  var cached = cache.get(key);
  if (cached) return JSON.parse(cached);

  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  var code = response.getResponseCode();
  var body = response.getContentText();
  if (code === 404) {
    throw new Error('Market not found. Check the slug or URL.');
  }
  if (code < 200 || code >= 300) {
    throw new Error('Manifold API error (HTTP ' + code + '): ' + body.slice(0, 200));
  }
  var data = JSON.parse(body);
  // CacheService values are capped at 100 KB; skip caching oversized payloads.
  if (body.length < 100000) {
    cache.put(key, body, CACHE_SECONDS);
  }
  return data;
}
