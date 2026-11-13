// Shared setup for the browser checks: the served app, the fake fleet, the
// pairing flow and the walk over every route.
//
// Hooks the checks rely on, chosen to survive a redesign:
//   - routes: every `const String kRoute... = '/...'` in lib/ except `/`
//     (it hands over to a server or the server group), so a new page is
//     walked without editing the checks; opened with history.pushState and
//     popstate, so live connections stay up between routes;
//   - tabs: a page's tab row carries `data-tabs="a,b,c"` (kit AppTabs); every
//     tab after the first is walked as `<route>?tab=<id>` (screen
//     `<route>-<id>`). The walk runs in Advanced mode (localStorage
//     `reactor.mode`) unless REACT_MODE=simple, so Advanced-only pages and
//     tabs are included;
//   - pairing: a link opened as /pair#RCT3..., the pairing card's Pair
//     ([data-testid=pair-submit]), then the address changing to /server/<id>;
//     the start page `/` is walked as `start`, with a link pasted into its
//     Pairing link field as `start~pair` (the Pair a server dialog with the
//     card), and `/pair` with a new link as `pair`;
//   - the page body: the <main> landmark; data on screen: a decimal number
//     in it (TPS, tick time) once the server is live;
//   - the phone nav: the button named "Open navigation".
//
//   REACT_WEB_URL   the served site (python3 tool/serve_site.py --port 8820)
//   REACT_FAKE_URL  the fake fleet (dart run tool/fake_server.dart --port=8821
//                   --origin=http://127.0.0.1:8820 --servers=3)
//   REACT_PAIRING_LINKS  instead of the fake: a file with one pairing link
//                   per line (real servers, `/react web pair role=admin`);
//                   the first link's server is the one walked
//   PLAYWRIGHT_MODULE  path to the playwright package (default 'playwright')
//   REACT_WIDTHS    comma separated widths (default 1600,768,390)
//   REACT_ROUTES    a regular expression; only routes whose name matches
//   REACT_SCREENSHOTS  a folder (outside the repository) for a shot per route,
//                   named <route>-<width>.png, toasts hidden
//   REACT_MODE      simple or advanced (default advanced); Simple hides the
//                   Advanced-only pages and tabs, so fewer screens are walked
//   REACT_THEME     light or dark (default dark)
//   REACT_CHART_RANGE  live, 1h, 6h or 24h (default live): the range the
//                   Overview and Performance charts open on
//   REACT_DIALOGS   0 skips the dialogs and sheets. Otherwise, on every
//                   screen, the first button of each kind that opens one
//                   (Edit, Configure, Run, New, Remove, Delete, Reset,
//                   Teleport, Apply, Import, Pair a server) is clicked; an open
//                   dialog or sheet is a screen of its own,
//                   `<screen>~<its title>`, and must close on Escape. The
//                   language dialog (top bar "...") is walked once per width
const fs = require('node:fs');
const path = require('node:path');

const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Set ${name}`);
  return value.replace(/\/$/, '');
}

function settings() {
  return {
    url: required('REACT_WEB_URL'),
    links: process.env.REACT_PAIRING_LINKS
      ? fs.readFileSync(process.env.REACT_PAIRING_LINKS, 'utf8').split(/\s+/).filter(Boolean)
      : null,
    fake: process.env.REACT_PAIRING_LINKS ? null : required('REACT_FAKE_URL'),
    widths: (process.env.REACT_WIDTHS || '1600,768,390').split(',').map(Number),
    routes: process.env.REACT_ROUTES ? new RegExp(process.env.REACT_ROUTES) : null,
    shots: process.env.REACT_SCREENSHOTS || null,
    mode: process.env.REACT_MODE === 'simple' ? 'simple' : 'advanced',
    dialogs: process.env.REACT_DIALOGS !== '0',
    theme: process.env.REACT_THEME === 'light' ? 'light' : 'dark',
    chartRange: process.env.REACT_CHART_RANGE || 'live',
  };
}

/** Every route path declared in lib/ (`const String kRouteX = '/...'`). */
function declaredRoutes() {
  const lib = path.resolve(__dirname, '../lib');
  const found = new Set();
  const visit = dir => {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) visit(full);
      else if (entry.name.endsWith('.dart')) {
        for (const m of fs.readFileSync(full, 'utf8').matchAll(/const\s+String\s+kRoute\w*\s*=\s*'(\/[^']*)'/g)) found.add(m[1]);
      }
    }
  };
  visit(lib);
  if (found.size < 2) throw new Error('No kRoute constants found in lib/');
  return [...found];
}

/** A file-name-safe name for a route path: '/server/:id/logs' -> 'logs'. */
function routeName(route) {
  if (route === '/') return 'start';
  if (route === '/server/:id') return 'overview';
  return route.replace(/^\/server\/:id\//, '').replace(/^\//, '').replace(/[^a-z0-9]+/gi, '-');
}

async function fleet(fake) {
  const response = await fetch(`${fake}/__fake/servers`);
  if (!response.ok) throw new Error(`${fake}/__fake/servers answered ${response.status}`);
  return (await response.json()).servers;
}

/**
 * Opens [route] the way the app's own links do: a History API push and a
 * popstate, so the app keeps its live connections. The first visit loads
 * the page.
 */
async function go(page, url, route) {
  if (!page.url().startsWith(url)) {
    await page.goto(`${url}${route}`);
  } else {
    await page.evaluate(path => {
      history.pushState(null, '', path);
      dispatchEvent(new PopStateEvent('popstate', {state: null}));
    }, route);
  }
  // A pairing link's code leaves the address as soon as the page reads it.
  await page.waitForFunction(path => location.pathname + location.search === path, route.split('#')[0]);
}

/** A new admin pairing link from the fake for server [slug]. */
async function newLink(fake, slug = 's1', query = '') {
  const response = await fetch(`${fake}/__fake/pair?server=${slug}${query}`, {method: 'POST'});
  if (!response.ok) throw new Error(`/__fake/pair answered ${response.status}`);
  return (await response.json()).link;
}

/**
 * Opens [link] in the app (its code as /pair#RCT3...), the way a click on
 * the console's link does: a full load when [fullLoad], else the History
 * API like the app's own links. Then the pairing card's Pair, and the
 * address moving to /server/<id>. Returns the id and when Pair was pressed.
 */
async function pairLink(page, url, link, {fullLoad = false} = {}) {
  const path = `/pair${link.slice(link.indexOf('#'))}`;
  if (fullLoad) await page.goto(`${url}${path}`);
  else await go(page, url, path);
  const pair = page.locator('main [data-testid="pair-submit"]');
  await pair.waitFor({timeout: 10000});
  const started = Date.now();
  await pair.click();
  await page.waitForURL(/\/server\/[^/?#]+/, {timeout: 15000});
  return {id: /\/server\/([^/?#]+)/.exec(page.url())[1], started};
}

/** Waits for the <main> landmark, a quiet network and two frames. */
async function settle(page, {quiet = 400} = {}) {
  await page.locator('main').first().waitFor();
  await page.waitForLoadState('networkidle', {timeout: 2000}).catch(() => {});
  await page.waitForFunction(() => ![...document.querySelectorAll('[aria-busy="true"], [class*="skeleton"]')]
    .some(el => el.checkVisibility()), null, {timeout: 5000}).catch(() => {});
  await page.waitForTimeout(quiet);
  await page.mouse.move(0, 0);
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

/** True once <main> shows a decimal number (TPS, tick time). */
function waitForData(page, timeout = 15000) {
  return page.waitForFunction(() => /\b\d{1,4}\.\d\b/.test(document.querySelector('main')?.innerText || ''), null, {timeout});
}

/**
 * Pairs every server (fake, or the real ones in REACT_PAIRING_LINKS) by
 * opening its pairing link and pressing Pair on the card, and returns them
 * with their app ids, plus the time from the first Pair click to data on
 * screen.
 */
async function pairAll(page, {url, fake, links}) {
  const servers = links ? links.map(pairingLink => ({pairingLink})) : await fleet(fake);
  const paired = [];
  let firstData = null;
  for (const server of servers) {
    const {id, started} = await pairLink(page, url, server.pairingLink, {fullLoad: true});
    if (firstData === null) {
      await waitForData(page);
      firstData = Date.now() - started;
    }
    paired.push({...server, id});
  }
  return {servers: paired, firstData};
}

/**
 * Opens a browser, pairs the fleet once at the first width, then calls
 * `visit(page, {width, name, route})` on every route at every width. Server
 * routes use the critical server (the busiest pages). On phones and tablets
 * the open nav drawer is a screen of its own ('nav').
 */
async function walk(label, visit) {
  const config = settings();
  const browser = await playwright.chromium.launch({headless: true});
  const routes = declaredRoutes();
  const wanted = name => !config.routes || config.routes.test(name);
  let state = null;
  let servers = null;
  const screens = [];
  try {
    for (const width of config.widths) {
      const viewport = {width, height: width <= 390 ? 844 : 900};
      const context = await browser.newContext({viewport, colorScheme: config.theme, hasTouch: width <= 390, ...(state ? {storageState: state} : {})});
      await context.addInitScript(({mode, theme, chartRange}) => {
        localStorage.setItem('reactor.mode', mode);
        localStorage.setItem('reactor.theme', theme);
        localStorage.setItem('reactor.chart_range', chartRange);
      }, {mode: config.mode, theme: config.theme, chartRange: config.chartRange});
      const page = await context.newPage();
      page.setDefaultTimeout(15000);
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      if (!state) {
        await page.goto(config.url);
        const pairing = await pairAll(page, config);
        servers = pairing.servers;
        state = await context.storageState();
        console.log(`${label}: paired ${servers.length} servers, ${pairing.firstData} ms from Pair to data`);
      }
      const target = servers.find(server => server.health === 'critical') || servers[0];
      const started = Date.now();
      let count = 0;
      let languageDone = false;
      for (const route of routes) {
        const name = routeName(route);
        if (!wanted(name) && !(config.routes && config.routes.test(`${name}-`))) continue;
        // /pair shows its card for a new link; without the fake, the page
        // without a link.
        const opened = route === '/pair' && config.fake
          ? `/pair${(await newLink(config.fake)).replace(/^[^#]*/, '')}`
          : route.replace(':id', target.id);
        await go(page, config.url, opened);
        await settle(page);
        if (route.startsWith('/server/')) await waitForData(page, 5000).catch(() => {});
        if (wanted(name)) {
          await visit(page, {width, name, route});
          screens.push({width, name});
          count++;
          if (config.shots) await shoot(page, config.shots, `${name}-${width}`);
          if (config.dialogs) {
            const path = route.replace(':id', target.id);
            if (!languageDone) {
              languageDone = true;
              for (const screen of await walkLanguage(page, visit, {width, route})) { screens.push(screen); count++; }
            }
            for (const screen of await walkDialogs(page, visit, {width, name, route, url: config.url, path})) { screens.push(screen); count++; }
            if (route === '/' && config.fake) {
              const screen = await walkPastedLink(page, visit, {width, name, route, fake: config.fake});
              if (screen) { screens.push(screen); count++; }
            }
          }
        }
        const tabs = await page.evaluate(() => (document.querySelector('main [data-tabs]')?.getAttribute('data-tabs') || '').split(',').filter(Boolean));
        for (const tab of tabs.slice(1)) {
          const tabName = `${name}-${tab}`;
          if (!wanted(tabName)) continue;
          await go(page, config.url, `${route.replace(':id', target.id)}?tab=${tab}`);
          await settle(page);
          if (route.startsWith('/server/')) await waitForData(page, 5000).catch(() => {});
          await visit(page, {width, name: tabName, route});
          screens.push({width, name: tabName});
          count++;
          if (config.shots) await shoot(page, config.shots, `${tabName}-${width}`);
          if (config.dialogs) {
            const path = `${route.replace(':id', target.id)}?tab=${tab}`;
            for (const screen of await walkDialogs(page, visit, {width, name: tabName, route, url: config.url, path})) { screens.push(screen); count++; }
          }
        }
      }
      if (width <= 760 && wanted('nav')) {
        await go(page, config.url, `/server/${target.id}`);
        await settle(page);
        const open = page.getByRole('button', {name: 'Open navigation', exact: true}).first();
        if (await open.isVisible().catch(() => false)) {
          await open.click();
          await settle(page, {quiet: 300});
          await visit(page, {width, name: 'nav', route: 'nav'});
          screens.push({width, name: 'nav'});
          count++;
          if (config.shots) await shoot(page, config.shots, `nav-${width}`);
        }
      }
      if (errors.length) console.log(`${label}: ${width} page errors: ${[...new Set(errors)].slice(0, 5).join(' | ')}`);
      console.log(`${label}: ${width} walked ${count} screens in ${Math.round((Date.now() - started) / 1000)} s`);
      await context.close();
    }
  } finally {
    await browser.close();
  }
  return screens;
}

/** Problems the walk itself finds (a dialog that Escape does not close). */
const walkProblems = [];

/** Buttons whose click opens a dialog or sheet, by the start of their name. */
const dialogOpener = /^(Edit|Configure|Run|New|Remove|Delete|Reset|Teleport|Apply|Import|Pair a server|Settings)\b/;

const openLayer = page => page.evaluate(() => {
  const layer = [...document.querySelectorAll('[role="dialog"], [role="alertdialog"]')]
    .find(el => el.checkVisibility() && el.getBoundingClientRect().width > 0);
  if (!layer) return null;
  const label = layer.getAttribute('aria-label') ||
    document.getElementById(layer.getAttribute('aria-labelledby') || '')?.textContent ||
    layer.querySelector('h1, h2, h3')?.textContent || 'dialog';
  return label.trim().replace(/\s+/g, ' ').slice(0, 40);
});

async function waitForLayer(page, open, timeout = 1500) {
  const until = Date.now() + timeout;
  while (Date.now() < until) {
    const layer = await openLayer(page);
    if (open ? layer : !layer) return layer;
    await page.waitForTimeout(50);
  }
  return open ? null : await openLayer(page);
}

/** Closes the open layer with Escape; a layer that stays open is a problem. */
async function closeLayer(page, where) {
  await page.keyboard.press('Escape');
  const still = await waitForLayer(page, false);
  if (still) {
    walkProblems.push({...where, text: `Escape does not close "${still}"`});
    await page.keyboard.press('Escape');
  }
}

/**
 * Opens each kind of dialog the screen offers, visits it as a screen of its
 * own and closes it with Escape. Returns the screens it visited.
 */
async function walkDialogs(page, visit, {width, name, route, url, path}) {
  const visited = [];
  const kinds = await page.evaluate(pattern => {
    const opener = new RegExp(pattern);
    const seen = new Set();
    const found = [];
    for (const el of document.querySelectorAll('main button, main a.arcane-button')) {
      if (!el.checkVisibility() || el.disabled || el.getAttribute('aria-disabled') === 'true') continue;
      // An icon font glyph (private use area) can lead the text; it is not
      // part of the button's name.
      const named = (el.getAttribute('aria-label') || el.textContent || '')
        .replace(/[\uE000-\uF8FF]/g, '').trim().replace(/\s+/g, ' ');
      const kind = opener.exec(named)?.[1];
      if (!kind || seen.has(kind)) continue;
      seen.add(kind);
      found.push(named);
    }
    return found;
  }, dialogOpener.source);
  for (const named of kinds) {
    const button = page.locator('main').getByRole('button', {name: named, exact: true}).first();
    if (!(await button.isVisible().catch(() => false))) continue;
    await button.click().catch(() => {});
    const title = await waitForLayer(page, true);
    if (title) {
      await settle(page, {quiet: 250});
      const screen = {width, name: `${name}~${title}`, route};
      await visit(page, screen);
      visited.push(screen);
      await closeLayer(page, screen);
    }
    // A click that navigated (a link styled as a button) goes back.
    if (await page.evaluate(() => location.pathname + location.search) !== path) {
      await go(page, url, path);
      await settle(page, {quiet: 200});
    }
  }
  return visited;
}

/**
 * A link pasted into the start page's Pairing link field: the Pair a server
 * dialog with the pairing card, visited as `<screen>~pair`, then closed with
 * Escape.
 */
async function walkPastedLink(page, visit, {width, name, route, fake}) {
  const field = page.locator('main [data-start-pair] input[type="text"], main [data-start-pair] input:not([type])').first();
  if (!(await field.isVisible().catch(() => false))) return null;
  await field.fill(await newLink(fake));
  const title = await waitForLayer(page, true);
  if (!title) return null;
  await page.locator('[role="dialog"] [data-testid="pair-submit"]').waitFor({timeout: 5000}).catch(() => {});
  await settle(page, {quiet: 250});
  const screen = {width, name: `${name}~pair`, route};
  await visit(page, screen);
  await closeLayer(page, screen);
  await field.fill('');
  return screen;
}

/** The language dialog from the top bar's "..." menu. */
async function walkLanguage(page, visit, where) {
  const more = page.getByRole('banner').getByRole('button', {name: 'More options', exact: true});
  if (!(await more.isVisible().catch(() => false))) return [];
  await more.click();
  const item = page.getByRole('menuitem', {name: /Select language/});
  if (!(await item.isVisible().catch(() => false))) {
    await page.keyboard.press('Escape');
    return [];
  }
  await item.click();
  const title = await waitForLayer(page, true);
  if (!title) return [];
  await settle(page, {quiet: 250});
  const screen = {...where, name: `language~${title}`};
  await visit(page, screen);
  await closeLayer(page, screen);
  return [screen];
}

/**
 * A shot of the screen without the passing toasts, and without the
 * tooltip of a control that kept focus from an earlier dialog.
 */
async function shoot(page, folder, name) {
  fs.mkdirSync(folder, {recursive: true});
  await page.evaluate(() => document.activeElement?.blur?.());
  await page.waitForTimeout(200);
  await page.screenshot({path: path.join(folder, `${name}.png`), style: '.arcane-toaster { display: none !important; }'});
}

/**
 * Prints per-route counts by width and the total, and exits non-zero when
 * the total is above 0. `found` is a list of {width, name, text}; the
 * walk's own problems (a dialog Escape does not close) are added.
 */
function report(label, screens, found, {list = false} = {}) {
  const problems = [...found, ...walkProblems];
  const counts = new Map();
  for (const {width, name} of screens) counts.set(`${width} ${name}`, 0);
  for (const {width, name} of problems) counts.set(`${width} ${name}`, (counts.get(`${width} ${name}`) || 0) + 1);
  const widths = [...new Set(screens.map(s => s.width))];
  for (const width of widths) {
    const rows = [...counts].filter(([key]) => key.startsWith(`${width} `));
    const total = rows.reduce((sum, [, n]) => sum + n, 0);
    console.log(`${label}: ${width} total ${total} | ${rows.filter(([, n]) => n).sort((a, b) => b[1] - a[1]).map(([key, n]) => `${key.slice(String(width).length + 1)} ${n}`).join(', ') || 'clean'}`);
  }
  if (list) for (const p of problems) console.log(`${p.width} ${p.name} | ${p.text}`);
  console.log(`${label}: total ${problems.length}`);
  if (problems.length) process.exitCode = 1;
}

module.exports = {settings, declaredRoutes, routeName, fleet, go, settle, waitForData, newLink, pairLink, pairAll, walk, shoot, report};
