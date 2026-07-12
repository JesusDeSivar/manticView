/**
 * ManticView — Manifold Markets custom functions for Google Sheets.
 *
 * This file is self-contained: paste it alone into any spreadsheet's
 * Apps Script editor (Extensions > Apps Script) and the functions below
 * become available immediately. Together with Addon.gs, Sidebar.html and
 * appsscript.json it also forms the ManticView editor add-on, which adds
 * a search sidebar, one-click inserts, and scheduled refresh.
 *
 * All market functions accept either a market slug ("will-x-happen") or a
 * full market URL ("https://manifold.markets/user/will-x-happen"). User
 * functions accept a username, "@username", or a profile URL.
 *
 * Data comes from the public Manifold API (https://docs.manifold.markets/api)
 * and needs no API key. Responses are cached for 5 minutes to stay well
 * within rate limits; pass a changing value as the last `refresh` argument
 * (e.g. a cell with NOW() in it) to force a recalculation, or use the
 * add-on's "Refresh now" menu item.
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
 * with columns: Answer, Probability. Sorted by probability (highest
 * first) by default.
 *
 * @param {string} market Market slug or full manifold.markets URL.
 * @param {string} [sortBy] "probability" (default, highest first) or
 *   "alphabetical" (A→Z by answer text). Aliases: "prob"/"p"/"desc" and
 *   "alpha"/"az"/"name".
 * @param {number} [limit] Max answers to return. Defaults to all.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array>} One row per answer.
 * @customfunction
 */
function MANIFOLD_ANSWERS(market, sortBy, limit, refresh) {
  var m = fetchMarket_(market);
  var alphabetical = /^(alpha|alphabetical|az|a-z|name)$/i.test((sortBy || '').toString().trim());
  var lim = Math.floor(Number(limit));
  if (!isFinite(lim) || lim <= 0) lim = 0; // 0 = no limit

  if (!m.answers || !m.answers.length) {
    if (typeof m.probability === 'number') {
      return [['Answer', 'Probability'], ['YES', m.probability], ['NO', 1 - m.probability]];
    }
    throw new Error('Market "' + m.question + '" has no answers.');
  }

  var answers = m.answers.slice();
  if (alphabetical) {
    answers.sort(function (a, b) {
      return (a.text || '').localeCompare(b.text || '');
    });
  } else {
    answers.sort(function (a, b) {
      var pa = typeof a.probability === 'number' ? a.probability : -1;
      var pb = typeof b.probability === 'number' ? b.probability : -1;
      return pb - pa;
    });
  }

  if (lim > 0) answers = answers.slice(0, lim);

  var rows = [['Answer', 'Probability']];
  answers.forEach(function (a) {
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
 * Returns the probability history of a binary market as a single column,
 * oldest first — made to be wrapped in SPARKLINE:
 *
 *   =SPARKLINE(MANIFOLD_HISTORY("will-x-happen"), {"color","#4F46E5"})
 *
 * History is sampled from the market's most recent 1000 bets, so on very
 * active markets the series covers a shorter window of time.
 *
 * @param {string} market Market slug or full manifold.markets URL.
 * @param {number} [points] Number of points (2–300). Defaults to 50.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array<number>>} One probability per row, chronological.
 * @customfunction
 */
function MANIFOLD_HISTORY(market, points, refresh) {
  var slug = parseSlug_(market);
  var n = Math.max(2, Math.min(300, Math.floor(Number(points) || 50)));

  var cache = CacheService.getScriptCache();
  var key = cacheKey_('hist:' + slug + ':' + n);
  var cached = cache.get(key);
  if (cached) {
    return JSON.parse(cached).map(function (p) { return [p]; });
  }

  var bets = fetchJson_(MANIFOLD_API_BASE + '/bets?contractSlug=' + encodeURIComponent(slug) + '&limit=1000', true);
  var probs = [];
  for (var i = bets.length - 1; i >= 0; i--) { // API returns newest first
    if (typeof bets[i].probAfter === 'number') probs.push(bets[i].probAfter);
  }
  if (!probs.length) {
    throw new Error('No probability history found. MANIFOLD_HISTORY works on binary (YES/NO) markets with at least one bet.');
  }
  var sampled = sampleSeries_(probs, n);
  cache.put(key, JSON.stringify(sampled), CACHE_SECONDS);
  return sampled.map(function (p) { return [p]; });
}

/**
 * Returns a single attribute of a Manifold user.
 *
 * Supported attributes: "balance" (default), "name", "username", "bio",
 * "totalDeposits", "createdTime", "lastBetTime", "url", and any other
 * field of the public user object.
 *
 * @param {string} username Username, "@username", or profile URL.
 * @param {string} [attribute] Attribute name. Defaults to "balance".
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return The requested attribute value.
 * @customfunction
 */
function MANIFOLD_USER(username, attribute, refresh) {
  var u = fetchUser_(username);
  var attr = (attribute || 'balance').toString();
  var timeFields = { createdTime: true, lastBetTime: true };
  if (attr === 'url') return 'https://manifold.markets/' + u.username;
  if (!(attr in u)) {
    throw new Error('Unknown user attribute "' + attr + '".');
  }
  var value = u[attr];
  if (timeFields[attr] && typeof value === 'number') {
    return new Date(value);
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return value;
}

/**
 * Returns a Manifold user's portfolio summary as a two-column table:
 * balance, investment value, net worth, total deposits, all-time profit,
 * and today's profit. All figures are in mana.
 *
 * @param {string} username Username, "@username", or profile URL.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array>} Metric/value rows.
 * @customfunction
 */
function MANIFOLD_PORTFOLIO(username, refresh) {
  var u = fetchUser_(username);
  var p = fetchJson_(MANIFOLD_API_BASE + '/get-user-portfolio?userId=' + encodeURIComponent(u.id));
  var balance = num_(p.balance);
  var invested = num_(p.investmentValue);
  var deposits = num_(p.totalDeposits);
  var netWorth = balance + invested;
  return [
    ['Metric', 'Mana'],
    ['Balance', balance],
    ['Investment value', invested],
    ['Net worth', netWorth],
    ['Total deposits', deposits],
    ['All-time profit', netWorth - deposits],
    ['Daily profit', num_(p.dailyProfit)]
  ];
}

/**
 * Returns a Manifold user's open market positions as a table with columns:
 * Question, Value, Profit, Last bet, URL. Sorted by position value,
 * largest first. All figures are in mana.
 *
 * @param {string} username Username, "@username", or profile URL.
 * @param {number} [limit] Max positions (1–100). Defaults to 10.
 * @param {*} [refresh] Optional. Any changing value to force a refresh.
 * @return {Array<Array>} One row per position.
 * @customfunction
 */
function MANIFOLD_POSITIONS(username, limit, refresh) {
  var n = Math.max(1, Math.min(100, Math.floor(Number(limit) || 10)));
  var name = parseUsername_(username);

  var cache = CacheService.getScriptCache();
  var key = cacheKey_('pos:' + name + ':' + n);
  var cached = cache.get(key);
  var positions;
  if (cached) {
    positions = JSON.parse(cached);
  } else {
    var u = fetchUser_(name);
    var data = fetchJson_(
      MANIFOLD_API_BASE + '/get-user-contract-metrics-with-contracts?userId=' + encodeURIComponent(u.id) + '&limit=' + n,
      true
    );
    var contracts = {};
    (data.contracts || []).forEach(function (c) { contracts[c.id] = c; });

    positions = [];
    var byContract = data.metricsByContract || {};
    Object.keys(byContract).forEach(function (cid) {
      var c = contracts[cid];
      if (!c) return;
      var value = 0, profit = 0, lastBet = 0;
      byContract[cid].forEach(function (m) {
        value += num_(m.payout);
        profit += num_(m.profit);
        if (num_(m.lastBetTime) > lastBet) lastBet = num_(m.lastBetTime);
      });
      var url = c.url ||
        (c.creatorUsername && c.slug
          ? 'https://manifold.markets/' + c.creatorUsername + '/' + c.slug
          : '');
      positions.push([c.question, value, profit, lastBet, url]);
    });
    positions.sort(function (a, b) { return b[1] - a[1]; });
    positions = positions.slice(0, n);
    var body = JSON.stringify(positions);
    if (body.length < 100000) cache.put(key, body, CACHE_SECONDS);
  }

  var rows = [['Question', 'Value', 'Profit', 'Last bet', 'URL']];
  positions.forEach(function (p) {
    rows.push([p[0], p[1], p[2], p[3] ? new Date(p[3]) : '', p[4]]);
  });
  return rows;
}

/* ------------------------------------------------------------------ */
/* Internals                                                           */
/* ------------------------------------------------------------------ */

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
 * Extracts a username from a username, "@username", or profile URL.
 */
function parseUsername_(username) {
  if (!username) throw new Error('Provide a Manifold username.');
  var s = username.toString().trim();
  var match = s.match(/manifold\.markets\/([^\/?#\s]+)/);
  if (match) s = match[1];
  return s.replace(/^@/, '');
}

/**
 * Fetches a market by slug, with caching.
 */
function fetchMarket_(market) {
  var slug = parseSlug_(market);
  return fetchJson_(MANIFOLD_API_BASE + '/slug/' + encodeURIComponent(slug));
}

/**
 * Fetches a user by username, with caching.
 */
function fetchUser_(username) {
  var name = parseUsername_(username);
  return fetchJson_(MANIFOLD_API_BASE + '/user/' + encodeURIComponent(name));
}

/**
 * Evenly samples a series down to n points, always keeping the last value
 * (the current probability).
 */
function sampleSeries_(series, n) {
  if (series.length <= n) return series;
  var out = [];
  var step = (series.length - 1) / (n - 1);
  for (var i = 0; i < n; i++) {
    out.push(series[Math.round(i * step)]);
  }
  return out;
}

function num_(v) {
  return typeof v === 'number' && isFinite(v) ? v : 0;
}

/**
 * Cache keys include a "salt" the add-on bumps on "Refresh now", which
 * invalidates every cached response at once. Standalone (non-add-on)
 * installs never set a salt and behave as before.
 */
function cacheKey_(suffix) {
  var salt = CacheService.getScriptCache().get('mv:salt') || '';
  return 'mv:' + salt + ':' + suffix;
}

/**
 * GET a URL and parse JSON, using CacheService to avoid hammering the API.
 * Pass skipRawCache=true for large responses that are post-processed and
 * cached in reduced form by the caller.
 */
function fetchJson_(url, skipRawCache) {
  var cache = CacheService.getScriptCache();
  var key = cacheKey_(url);
  if (!skipRawCache) {
    var cached = cache.get(key);
    if (cached) return JSON.parse(cached);
  }

  var response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  var code = response.getResponseCode();
  var body = response.getContentText();
  if (code === 404) {
    throw new Error('Not found on Manifold. Check the slug, URL, or username.');
  }
  if (code < 200 || code >= 300) {
    throw new Error('Manifold API error (HTTP ' + code + '): ' + body.slice(0, 200));
  }
  var data = JSON.parse(body);
  // CacheService values are capped at 100 KB; skip caching oversized payloads.
  if (!skipRawCache && body.length < 100000) {
    cache.put(key, body, CACHE_SECONDS);
  }
  return data;
}
