/**
 * ManticView — editor add-on layer.
 *
 * Everything here powers the Extensions menu and the sidebar; the custom
 * functions themselves live in Code.gs and work with or without this file.
 *
 * Menu:
 *   Search & insert markets…  — opens the sidebar
 *   Refresh markets now       — invalidates the cache and recalculates
 *                               every MANIFOLD formula in the spreadsheet
 *   Auto-refresh              — clock trigger for the same refresh, from
 *                               every 5 minutes up to daily
 *   About ManticView          — credits and links
 */

var MV_ADDON_VERSION = '1.3.4';
var MV_WEBSITE = 'https://jesusdesivar.github.io/manticView/';
var MV_SIDEBAR_TITLE = 'ManticView';
var MV_MAX_REFRESH_CELLS = 500;
var MV_LOG_PER_SOURCE = 5;

/* ------------------------------------------------------------------ */
/* Lifecycle & menu                                                    */
/* ------------------------------------------------------------------ */

function onInstall(e) {
  onOpen(e);
}

function onOpen(e) {
  var ui = SpreadsheetApp.getUi();
  ui.createAddonMenu()
    .addItem('Search & insert markets…', 'mvShowSidebar')
    .addSeparator()
    .addItem('Refresh markets now', 'mvMenuRefresh')
    .addSubMenu(ui.createMenu('Auto-refresh (approximate)')
      .addItem('About every 5 minutes', 'mvAutoRefresh5m')
      .addItem('About every 10 minutes', 'mvAutoRefresh10m')
      .addItem('About every 30 minutes', 'mvAutoRefresh30m')
      .addItem('About every hour', 'mvAutoRefresh1h')
      .addItem('About every 6 hours', 'mvAutoRefresh6h')
      .addItem('About once a day', 'mvAutoRefresh1d')
      .addItem('Off', 'mvAutoRefreshOff'))
    .addSeparator()
    .addItem('About ManticView', 'mvShowAbout')
    .addToUi();
}

function mvShowSidebar() {
  var html = HtmlService.createHtmlOutputFromFile('Sidebar')
    .setTitle(MV_SIDEBAR_TITLE);
  SpreadsheetApp.getUi().showSidebar(html);
}

function mvShowAbout() {
  var html = HtmlService.createHtmlOutput(
    '<div style="font-family:Georgia,serif;padding:8px 6px;color:#17171E;">' +
    '<h2 style="margin:0 0 6px;font-weight:600;">ManticView ' + MV_ADDON_VERSION + '</h2>' +
    '<p style="font-family:system-ui,sans-serif;font-size:13px;color:#3E4055;line-height:1.5;margin:0 0 10px;">' +
    'Live Manifold prediction-market probabilities as plain numbers in your spreadsheet. ' +
    'Data comes from the public Manifold API; nothing about you or your sheet is sent anywhere else.</p>' +
    '<p style="font-family:system-ui,sans-serif;font-size:13px;margin:0;">' +
    '<a href="' + MV_WEBSITE + '" target="_blank">Website</a> · ' +
    '<a href="' + MV_WEBSITE + 'sheets.html" target="_blank">Function guide</a> · ' +
    '<a href="https://github.com/JesusDeSivar/manticView" target="_blank">GitHub</a></p>' +
    '<p style="font-family:system-ui,sans-serif;font-size:11px;color:#8890A6;margin:12px 0 0;">' +
    'Independent open-source project — not affiliated with Manifold Markets, Inc.</p>' +
    '</div>'
  ).setWidth(340).setHeight(190);
  SpreadsheetApp.getUi().showModalDialog(html, 'About');
}

/* ------------------------------------------------------------------ */
/* Refresh                                                             */
/* ------------------------------------------------------------------ */

function mvMenuRefresh() {
  var count = mvRefreshNow();
  SpreadsheetApp.getActiveSpreadsheet().toast(
    count ? 'Refreshed ' + count + ' Manifold formula cell' + (count === 1 ? '' : 's') + '.'
          : 'No Manifold formulas found in this spreadsheet.',
    'ManticView', 5);
}

/**
 * Invalidates every cached Manifold response, then forces each formula
 * cell that calls a MANIFOLD function to recalculate. Sheets memoizes
 * custom-function results by their arguments, so the only reliable way to
 * recompute is to blank the formula and write it back. Every run is
 * written to the refresh log so background auto-refreshes leave a trace.
 *
 * @param {string} [source] 'auto' for background ticks, else 'manual'.
 * @return {number} How many cells were recalculated.
 */
function mvRefreshNow(source) {
  // Bump the cache salt (see cacheKey_ in Code.gs): all cached API
  // responses become unreachable at once.
  CacheService.getScriptCache().put('mv:salt', String(Date.now() % 100000000), 21600);

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var matches = ss.createTextFinder('MANIFOLD').matchFormulaText(true).findAll();

  var seen = {};
  var cells = [];
  for (var i = 0; i < matches.length && cells.length < MV_MAX_REFRESH_CELLS; i++) {
    var r = matches[i];
    var formula = r.getFormula();
    if (!formula || formula.indexOf('MANIFOLD') === -1) continue;
    var id = r.getSheet().getSheetId() + '!' + r.getA1Notation();
    if (seen[id]) continue;
    seen[id] = true;
    cells.push({ range: r, formula: formula });
  }

  if (cells.length) {
    try {
      cells.forEach(function (c) { c.range.setFormula(''); });
      SpreadsheetApp.flush();
    } finally {
      cells.forEach(function (c) { c.range.setFormula(c.formula); });
      SpreadsheetApp.flush();
    }
  }

  mvLogRefresh_(cells.length, source === 'auto' ? 'auto' : 'manual');
  return cells.length;
}

/* ------------------------------------------------------------------ */
/* Refresh log                                                         */
/* ------------------------------------------------------------------ */

/**
 * Appends a refresh event to a log in document properties, newest first,
 * keeping the most recent MV_LOG_PER_SOURCE of each source. Capping per
 * source (rather than a single total) guarantees the last few auto-refreshes
 * are always visible even after a burst of manual refreshes — the whole
 * point, since auto-refresh fires in the background with no toast. Never
 * throws — logging must not break a refresh.
 *
 * @param {number} count Cells recalculated.
 * @param {string} source 'auto' or 'manual'.
 */
function mvLogRefresh_(count, source) {
  try {
    var props = PropertiesService.getDocumentProperties();
    var log = JSON.parse(props.getProperty('mv:refreshlog') || '[]');
    log.unshift({ t: Date.now(), n: count, s: source === 'auto' ? 'auto' : 'manual' });
    // log stays newest-first, so filtering keeps the newest of each source.
    var kept = { auto: 0, manual: 0 };
    log = log.filter(function (e) {
      var k = e.s === 'auto' ? 'auto' : 'manual';
      if (kept[k] >= MV_LOG_PER_SOURCE) return false;
      kept[k]++;
      return true;
    });
    props.setProperty('mv:refreshlog', JSON.stringify(log));
  } catch (err) {
    console.error('ManticView log write failed: ' + err);
  }
}

/**
 * Recent refresh events for the sidebar, newest first.
 * @return {Array<{t:number, n:number, s:string}>}
 */
function mvGetRefreshLog() {
  try {
    return JSON.parse(PropertiesService.getDocumentProperties().getProperty('mv:refreshlog') || '[]');
  } catch (err) {
    return [];
  }
}

/* ------------------------------------------------------------------ */
/* Auto-refresh trigger                                                */
/* ------------------------------------------------------------------ */

/**
 * Cadences Apps Script clock triggers can express: everyMinutes accepts
 * only 1/5/10/15/30, everyHours only 1/2/4/6/8/12, plus everyDays.
 */
// Labels say "about every…" because Apps Script time-driven triggers fire on
// Google's schedule with jitter — not a precise interval from when they're set.
var MV_REFRESH_MODES = {
  '5m':  { label: 'about every 5 minutes',  build: function (b) { return b.everyMinutes(5); } },
  '10m': { label: 'about every 10 minutes', build: function (b) { return b.everyMinutes(10); } },
  '30m': { label: 'about every 30 minutes', build: function (b) { return b.everyMinutes(30); } },
  '1h':  { label: 'about every hour',       build: function (b) { return b.everyHours(1); } },
  '6h':  { label: 'about every 6 hours',    build: function (b) { return b.everyHours(6); } },
  '1d':  { label: 'about once a day',       build: function (b) { return b.everyDays(1); } }
};

function mvAutoRefresh5m()  { mvMenuSetAutoRefresh('5m'); }
function mvAutoRefresh10m() { mvMenuSetAutoRefresh('10m'); }
function mvAutoRefresh30m() { mvMenuSetAutoRefresh('30m'); }
function mvAutoRefresh1h()  { mvMenuSetAutoRefresh('1h'); }
function mvAutoRefresh6h()  { mvMenuSetAutoRefresh('6h'); }
function mvAutoRefresh1d()  { mvMenuSetAutoRefresh('1d'); }
function mvAutoRefreshOff() { mvMenuSetAutoRefresh('off'); }

function mvMenuSetAutoRefresh(mode) {
  mvSetAutoRefresh(mode);
  var msg = MV_REFRESH_MODES[mode]
    ? 'Manifold formulas will refresh ' + MV_REFRESH_MODES[mode].label + ', even with the sheet closed.'
    : 'Auto-refresh is off.';
  SpreadsheetApp.getActiveSpreadsheet().toast(msg, 'ManticView', 6);
}

function mvSetAutoRefresh(mode) {
  // Time-driven triggers are their own builder family: .timeBased() chains
  // directly off newTrigger(). (.forSpreadsheet() is only for onOpen/onEdit/
  // onChange triggers and cannot be combined with timeBased.)
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'mvAutoRefreshTick') ScriptApp.deleteTrigger(t);
  });
  var props = PropertiesService.getDocumentProperties();
  if (MV_REFRESH_MODES[mode]) {
    MV_REFRESH_MODES[mode].build(
      ScriptApp.newTrigger('mvAutoRefreshTick').timeBased()
    ).create();
    props.setProperty('mv:autorefresh', mode);
  } else {
    props.deleteProperty('mv:autorefresh');
  }
  return mvGetAutoRefreshMode();
}

/**
 * Triggers don't expose their interval, so the chosen mode is remembered
 * in document properties; the trigger's existence is the source of truth
 * for on/off.
 */
function mvGetAutoRefreshMode() {
  var hasTrigger = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'mvAutoRefreshTick';
  });
  if (!hasTrigger) return 'off';
  return PropertiesService.getDocumentProperties().getProperty('mv:autorefresh') || '1h';
}

function mvAutoRefreshTick(e) {
  try {
    mvRefreshNow('auto');
  } catch (err) {
    // Never let a background tick surface an error dialog.
    console.error('ManticView auto-refresh failed: ' + err);
  }
}

/* ------------------------------------------------------------------ */
/* Sidebar RPC — search                                                */
/* ------------------------------------------------------------------ */

/**
 * Searches Manifold for the sidebar. Returns lightweight market records.
 *
 * @param {string} term Search text.
 * @param {Object} opts {openOnly: boolean, type: 'ALL'|'BINARY'|'MULTIPLE_CHOICE'}
 */
function mvSearch(term, opts) {
  term = (term || '').toString().trim();
  if (!term) return [];
  opts = opts || {};
  var url = MANIFOLD_API_BASE + '/search-markets?term=' + encodeURIComponent(term) + '&limit=20';
  if (opts.openOnly) url += '&filter=open';
  if (opts.type === 'BINARY' || opts.type === 'MULTIPLE_CHOICE') url += '&contractType=' + opts.type;

  var markets = fetchJson_(url, true);
  return markets.map(function (m) {
    return {
      question: m.question,
      slug: m.slug,
      url: m.url,
      probability: typeof m.probability === 'number' ? m.probability : null,
      outcomeType: m.outcomeType,
      volume24Hours: num_(m.volume24Hours),
      uniqueBettorCount: num_(m.uniqueBettorCount),
      closeTime: m.closeTime || null,
      isResolved: !!m.isResolved,
      creatorName: m.creatorName
    };
  });
}

/* ------------------------------------------------------------------ */
/* Sidebar RPC — inserts                                               */
/* ------------------------------------------------------------------ */

/**
 * Inserts formulas for a market at the active cell, then moves the
 * selection just below what was written so repeated inserts stack.
 *
 * With info.withTitle, the market's question is written as plain text
 * beside ('prob'/'sparkline': title left, value right) or above
 * ('answers') the inserted formula. 'row' already includes the question.
 *
 * @param {string} kind 'prob' | 'row' | 'answers' | 'sparkline'
 * @param {string} slug Market slug.
 * @param {Object} info {isBinary: boolean, url: string, question: string, withTitle: boolean}
 * @return {string} A1 notation of where the insert landed.
 */
function mvInsertMarket(kind, slug, info) {
  slug = sanitizeForFormula_(slug);
  info = info || {};
  var sheet = SpreadsheetApp.getActiveSheet();
  var cell = sheet.getActiveCell();
  var withTitle = !!info.withTitle && kind !== 'row';
  var title = (info.question || slug).toString();
  var rowsUsed = 1;

  if (kind === 'prob') {
    var probCell = cell;
    if (withTitle) {
      cell.setValue(title);
      probCell = cell.offset(0, 1);
    }
    probCell.setFormula('=MANIFOLD_PROB("' + slug + '")');
    probCell.setNumberFormat('0.0%');
  } else if (kind === 'row') {
    var probFormula = info.isBinary
      ? '=MANIFOLD_PROB("' + slug + '")'
      : '="multi"';
    var formulas = [
      '=MANIFOLD("' + slug + '", "question")',
      probFormula,
      '=MANIFOLD("' + slug + '", "volume24Hours")',
      '=MANIFOLD("' + slug + '", "closeTime")',
      '=HYPERLINK("' + sanitizeForFormula_(info.url || 'https://manifold.markets/') + '", "open")'
    ];
    var range = sheet.getRange(cell.getRow(), cell.getColumn(), 1, formulas.length);
    range.setFormulas([formulas]);
    if (info.isBinary) range.getCell(1, 2).setNumberFormat('0.0%');
    range.getCell(1, 4).setNumberFormat('yyyy-mm-dd');
  } else if (kind === 'answers') {
    var answersCell = cell;
    if (withTitle) {
      cell.setValue(title);
      answersCell = cell.offset(1, 0);
      rowsUsed = 1;
    }
    answersCell.setFormula('=MANIFOLD_ANSWERS("' + slug + '")');
    rowsUsed += 2; // header + at least one answer; the spill handles the rest
  } else if (kind === 'sparkline') {
    var sparkCell = cell;
    if (withTitle) {
      cell.setValue(title);
      sparkCell = cell.offset(0, 1);
    }
    sparkCell.setFormula('=SPARKLINE(MANIFOLD_HISTORY("' + slug + '"), {"charttype","line";"color","#4F46E5";"linewidth",2})');
  } else {
    throw new Error('Unknown insert kind: ' + kind);
  }

  var landed = cell.getA1Notation();
  sheet.setActiveSelection(cell.offset(rowsUsed, 0, 1, 1));
  return landed;
}

/**
 * Inserts portfolio formulas for a username at the active cell.
 *
 * With opts.withLabel, a "@username" line (linked to their profile) is
 * written above the table so stacked portfolios stay identifiable.
 *
 * @param {string} kind 'portfolio' | 'positions'
 * @param {string} username Manifold username.
 * @param {Object} opts {withLabel: boolean}
 * @return {string} A1 notation of where the insert landed.
 */
function mvInsertUser(kind, username, opts) {
  var name = sanitizeForFormula_(parseUsername_(username));
  opts = opts || {};
  var sheet = SpreadsheetApp.getActiveSheet();
  var cell = sheet.getActiveCell();
  var formulaCell = cell;
  var rowsUsed = 2;

  if (opts.withLabel) {
    cell.setFormula('=HYPERLINK("https://manifold.markets/' + name + '", "@' + name + '")');
    formulaCell = cell.offset(1, 0);
    rowsUsed = 3;
  }
  if (kind === 'portfolio') {
    formulaCell.setFormula('=MANIFOLD_PORTFOLIO("' + name + '")');
  } else if (kind === 'positions') {
    formulaCell.setFormula('=MANIFOLD_POSITIONS("' + name + '", 10)');
  } else {
    throw new Error('Unknown insert kind: ' + kind);
  }
  var landed = cell.getA1Notation();
  sheet.setActiveSelection(cell.offset(rowsUsed, 0, 1, 1));
  return landed;
}

/* ------------------------------------------------------------------ */
/* Sidebar RPC — portfolio preview                                     */
/* ------------------------------------------------------------------ */

/**
 * Fetches a user's portfolio summary and top positions for the sidebar.
 */
function mvPortfolioPreview(username) {
  var u = fetchUser_(username);
  var p = fetchJson_(MANIFOLD_API_BASE + '/get-user-portfolio?userId=' + encodeURIComponent(u.id));
  var balance = num_(p.balance);
  var invested = num_(p.investmentValue);
  var netWorth = balance + invested;

  var positions = [];
  try {
    var data = fetchJson_(
      MANIFOLD_API_BASE + '/get-user-contract-metrics-with-contracts?userId=' + encodeURIComponent(u.id) + '&limit=5',
      true
    );
    var contracts = {};
    (data.contracts || []).forEach(function (c) { contracts[c.id] = c; });
    Object.keys(data.metricsByContract || {}).forEach(function (cid) {
      var c = contracts[cid];
      if (!c) return;
      var value = 0, profit = 0;
      data.metricsByContract[cid].forEach(function (m) {
        value += num_(m.payout);
        profit += num_(m.profit);
      });
      positions.push({ question: c.question, url: c.url, value: value, profit: profit });
    });
    positions.sort(function (a, b) { return b.value - a.value; });
    positions = positions.slice(0, 5);
  } catch (err) {
    // Portfolio summary is still useful without positions.
  }

  return {
    name: u.name,
    username: u.username,
    url: 'https://manifold.markets/' + u.username,
    balance: balance,
    invested: invested,
    netWorth: netWorth,
    dailyProfit: num_(p.dailyProfit),
    allTimeProfit: netWorth - num_(p.totalDeposits),
    positions: positions
  };
}

/* ------------------------------------------------------------------ */
/* Sidebar RPC — status                                                */
/* ------------------------------------------------------------------ */

function mvSidebarStatus() {
  return {
    version: MV_ADDON_VERSION,
    autoRefresh: mvGetAutoRefreshMode(),
    log: mvGetRefreshLog()
  };
}

/**
 * Slugs and usernames are URL path segments; strip anything that could
 * escape a double-quoted string literal inside a formula.
 */
function sanitizeForFormula_(s) {
  return (s || '').toString().replace(/["'\\\s]/g, '');
}
