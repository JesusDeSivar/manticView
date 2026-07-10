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
 *   Auto-refresh              — hourly time trigger for the same refresh
 *                               (add-on triggers may fire at most hourly)
 *   About ManticView          — credits and links
 */

var MV_ADDON_VERSION = '1.0.0';
var MV_WEBSITE = 'https://jesusdesivar.github.io/manticView/';
var MV_SIDEBAR_TITLE = 'ManticView';
var MV_MAX_REFRESH_CELLS = 500;

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
    .addSubMenu(ui.createMenu('Auto-refresh')
      .addItem('Every hour', 'mvAutoRefreshHourly')
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
 * recompute is to blank the formula and write it back.
 *
 * @return {number} How many cells were recalculated.
 */
function mvRefreshNow() {
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
  if (!cells.length) return 0;

  try {
    cells.forEach(function (c) { c.range.setFormula(''); });
    SpreadsheetApp.flush();
  } finally {
    cells.forEach(function (c) { c.range.setFormula(c.formula); });
    SpreadsheetApp.flush();
  }
  return cells.length;
}

/* ------------------------------------------------------------------ */
/* Auto-refresh trigger                                                */
/* ------------------------------------------------------------------ */

function mvAutoRefreshHourly() {
  mvSetAutoRefresh('hourly');
  SpreadsheetApp.getActiveSpreadsheet().toast('Manifold formulas will refresh every hour, even with the sheet closed.', 'ManticView', 5);
}

function mvAutoRefreshOff() {
  mvSetAutoRefresh('off');
  SpreadsheetApp.getActiveSpreadsheet().toast('Auto-refresh is off.', 'ManticView', 5);
}

function mvSetAutoRefresh(mode) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  ScriptApp.getUserTriggers(ss).forEach(function (t) {
    if (t.getHandlerFunction() === 'mvAutoRefreshTick') ScriptApp.deleteTrigger(t);
  });
  if (mode === 'hourly') {
    // Editor add-on time triggers may fire at most once per hour.
    ScriptApp.newTrigger('mvAutoRefreshTick')
      .forSpreadsheet(ss)
      .timeBased()
      .everyHours(1)
      .create();
  }
  return mvGetAutoRefreshMode();
}

function mvGetAutoRefreshMode() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var hasTrigger = ScriptApp.getUserTriggers(ss).some(function (t) {
    return t.getHandlerFunction() === 'mvAutoRefreshTick';
  });
  return hasTrigger ? 'hourly' : 'off';
}

function mvAutoRefreshTick(e) {
  try {
    mvRefreshNow();
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
 * @param {string} kind 'prob' | 'row' | 'answers' | 'sparkline'
 * @param {string} slug Market slug.
 * @param {Object} info {isBinary: boolean, url: string}
 * @return {string} A1 notation of where the insert landed.
 */
function mvInsertMarket(kind, slug, info) {
  slug = sanitizeForFormula_(slug);
  info = info || {};
  var sheet = SpreadsheetApp.getActiveSheet();
  var cell = sheet.getActiveCell();
  var rowsUsed = 1;

  if (kind === 'prob') {
    cell.setFormula('=MANIFOLD_PROB("' + slug + '")');
    cell.setNumberFormat('0.0%');
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
    cell.setFormula('=MANIFOLD_ANSWERS("' + slug + '")');
    rowsUsed = 2; // header + at least one answer; the spill handles the rest
  } else if (kind === 'sparkline') {
    cell.setFormula('=SPARKLINE(MANIFOLD_HISTORY("' + slug + '"), {"charttype","line";"color","#4F46E5";"linewidth",2})');
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
 * @param {string} kind 'portfolio' | 'positions'
 * @param {string} username Manifold username.
 * @return {string} A1 notation of where the insert landed.
 */
function mvInsertUser(kind, username) {
  var name = sanitizeForFormula_(parseUsername_(username));
  var sheet = SpreadsheetApp.getActiveSheet();
  var cell = sheet.getActiveCell();

  if (kind === 'portfolio') {
    cell.setFormula('=MANIFOLD_PORTFOLIO("' + name + '")');
  } else if (kind === 'positions') {
    cell.setFormula('=MANIFOLD_POSITIONS("' + name + '", 10)');
  } else {
    throw new Error('Unknown insert kind: ' + kind);
  }
  var landed = cell.getA1Notation();
  sheet.setActiveSelection(cell.offset(2, 0, 1, 1));
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
    autoRefresh: mvGetAutoRefreshMode()
  };
}

/**
 * Slugs and usernames are URL path segments; strip anything that could
 * escape a double-quoted string literal inside a formula.
 */
function sanitizeForFormula_(s) {
  return (s || '').toString().replace(/["'\\\s]/g, '');
}
