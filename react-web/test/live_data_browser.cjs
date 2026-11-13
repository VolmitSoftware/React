// Live data against the fake fleet, one page, no reloads:
//   switch  (C1) open a control page on server 1, switch to server 2 by
//           address, check the page shows server 2's values and that a
//           toggle is sent to server 2 only;
//   revision (H3) a change made outside the page (POST /__fake/mutate) shows
//           on the open page without a click;
//   counter (H5) another device raises the server's counter mark; the next
//           toggle gets a 409, re-bases, retries once and lands;
//   logs    (H2) the log socket closes for idleness; a line written after
//           that still reaches the page;
//   revoke  (M3) revoking an entry's token stops all its traffic for good
//           and the page says Pairing ended.
// The fake must run with --idle-timeout=5 for the logs step.
//
// Hooks: routes come from the kRoute constants (a control page is the first
// route naming tweaks or tuning, opened on its tweaks tab; the log page the
// first naming logs or console); switches are [role=switch] or .arcane-toggle-switch inside
// <main>; everything else is observed on the network and through
// /__fake/state.
//
//   REACT_WEB_URL, REACT_FAKE_URL, PLAYWRIGHT_MODULE as in browser_support.cjs
const {declaredRoutes, fleet, go, pairLink, settle, waitForData} = require('./browser_support.cjs');

const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = process.env.REACT_WEB_URL.replace(/\/$/, '');
const fake = process.env.REACT_FAKE_URL.replace(/\/$/, '');

const routes = declaredRoutes();
const tweaksRoute = routes.find(r => /^\/server\/:id\/.*(tweaks|tuning)/.test(r));
const controlRoute = tweaksRoute && /tuning/.test(tweaksRoute) ? `${tweaksRoute}?tab=tweaks` : tweaksRoute;
const logsRoute = routes.find(r => /^\/server\/:id\/.*(logs|console)/.test(r));

const post = async (path) => {
  const response = await fetch(`${fake}${path}`, {method: 'POST'});
  if (!response.ok) throw new Error(`${path} answered ${response.status}`);
  return response.json();
};
const state = async (slug) => (await fetch(`${fake}/__fake/state?server=${slug}`)).json();

const SWITCHES = 'main [role=switch], main .arcane-toggle-switch';

/** Visible switches in <main> that are on. Hidden ones (closed knob panels) do not count. */
function countOn() {
  const all = [...document.querySelectorAll('main [role=switch], main .arcane-toggle-switch')];
  const unique = [...new Set(all.map(el => el.closest('.arcane-toggle-switch') || el))].filter(el => el.checkVisibility());
  return unique.filter(el => el.getAttribute('aria-checked') === 'true' || el.classList.contains('active') || el.checked === true).length;
}
const checkedCount = page => page.evaluate(countOn);
const visibleSwitch = (page, index) => page.locator(SWITCHES).locator('visible=true').nth(index);

async function pair(page, link) {
  const {id} = await pairLink(page, url, link);
  await waitForData(page);
  return id;
}

function slugOf(server) {
  return /\/(s\d+)$/.exec(server.directUrl)[1];
}

async function main() {
  if (!controlRoute) throw new Error('No tweaks or tuning route found');
  const servers = await fleet(fake);
  const browser = await playwright.chromium.launch({headless: true});
  const failures = [];
  const check = (ok, message) => {
    console.log(`${ok ? 'ok  ' : 'FAIL'} ${message}`);
    if (!ok) failures.push(message);
  };
  try {
    const context = await browser.newContext({viewport: {width: 1600, height: 900}, colorScheme: 'dark'});
    // Advanced lists every tweak with its switch; Simple folds them into
    // closed category groups.
    await context.addInitScript(() => localStorage.setItem('reactor.mode', 'advanced'));
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const requests = [];
    page.on('response', response => {
      const u = new URL(response.url());
      if (u.port === new URL(fake).port && u.pathname.includes('/api/v1/')) {
        requests.push({at: Date.now(), method: response.request().method(), path: u.pathname, status: response.status()});
      }
    });
    const sockets = [];
    page.on('websocket', socket => sockets.push({at: Date.now(), url: socket.url()}));
    await page.goto(url);
    const [one, two] = servers;
    const idOne = await pair(page, one.pairingLink);
    const idTwo = await pair(page, two.pairingLink);
    const s1 = slugOf(one);
    const s2 = slugOf(two);

    // switch (C1)
    const enabled = async slug => Object.values((await state(slug)).tweaks).filter(Boolean).length;
    if (await enabled(s1) === await enabled(s2)) {
      await post(`/__fake/mutate?server=${s2}&kind=tweaks&id=${Object.keys((await state(s2)).tweaks)[0]}`);
    }
    await go(page, url, controlRoute.replace(':id', idOne));
    await settle(page);
    await page.waitForFunction(() => document.querySelectorAll('main [role=switch], main .arcane-toggle-switch').length > 0);
    const onOne = await checkedCount(page);
    const switchStart = Date.now();
    await go(page, url, controlRoute.replace(':id', idTwo));
    await page.waitForFunction(
      `(${countOn.toString()})() === ${Object.values((await state(s2)).tweaks).filter(Boolean).length}`,
      null,
      {timeout: 5000},
    ).catch(() => {});
    const onTwo = await checkedCount(page);
    const twoEnabled = Object.values((await state(s2)).tweaks).filter(Boolean).length;
    check(onTwo === twoEnabled && onOne !== onTwo, `switch: page shows server 2 values (${onTwo} on, server 2 has ${twoEnabled}, server 1 showed ${onOne})`);
    check(requests.some(r => r.at >= switchStart && r.method === 'GET' && r.path.startsWith(`/${s2}/`)), 'switch: server 2 data fetched after the switch');
    const putsBefore = requests.length;
    await visibleSwitch(page, 0).click();
    await page.waitForTimeout(800);
    const puts = requests.slice(putsBefore).filter(r => r.method === 'PUT');
    const landed = puts.filter(r => r.status === 200);
    check(landed.length === 1 && puts.every(r => r.path.startsWith(`/${s2}/`)), `switch: toggle sent ${puts.map(p => `${p.path} ${p.status}`).join(', ') || 'nothing'}`);

    // revision (H3)
    const firstTweak = Object.keys((await state(s2)).tweaks)[1];
    const before = await checkedCount(page);
    const mutateAt = Date.now();
    await post(`/__fake/mutate?server=${s2}&kind=tweaks&id=${firstTweak}`);
    const changed = await page.waitForFunction(
      `(${countOn.toString()})() !== ${before}`,
      null,
      {timeout: 5000},
    ).then(() => true, () => false);
    const refetches = requests.filter(r => r.at >= mutateAt && r.method === 'GET' && /\/tweaks$/.test(r.path)).length;
    check(changed, `revision: open page updated ${Date.now() - mutateAt} ms after an outside change, no click`);
    check(refetches === 1, `revision: ${refetches} tweaks refetch for one bump`);

    // counter (H5)
    await post(`/__fake/counter?server=${s2}`);
    const counterAt = Date.now();
    const target = Object.keys((await state(s2)).tweaks)[2];
    const wasOn = (await state(s2)).tweaks[target];
    await visibleSwitch(page, 2).click();
    await page.waitForTimeout(1000);
    const writes = requests.filter(r => r.at >= counterAt && r.method === 'PUT').map(r => r.status);
    const isOn = (await state(s2)).tweaks[target];
    check(writes.join(',') === '409,200' && isOn !== wasOn, `counter: writes answered ${writes.join(',')}, tweak ${wasOn} -> ${isOn}`);

    // logs (H2)
    if (logsRoute) {
      await go(page, url, logsRoute.replace(':id', idOne));
      await settle(page);
      await post(`/__fake/logs?server=${s1}&quiet=true`);
      await page.waitForTimeout(7000);
      const marker = `after-idle-${Date.now()}`;
      await post(`/__fake/log?server=${s1}&line=${marker}`);
      const shown = await page.waitForFunction(m => (document.querySelector('main')?.innerText || '').includes(m), marker, {timeout: 8000}).then(() => true, () => false);
      check(shown, 'logs: a line written after the idle close reaches the page');
      await post(`/__fake/logs?server=${s1}&quiet=false`);
    } else {
      check(false, 'logs: no logs route');
    }

    // revoke (M3)
    const viewer = await post(`/__fake/pair?server=${s1}&role=viewer&label=revoke-check`);
    const idViewer = await pair(page, viewer.link);
    await page.waitForTimeout(500);
    await post(`/__fake/revoke?server=${s1}&tokenId=${viewer.tokenId}`);
    await page.waitForTimeout(1000);
    const revokedAt = Date.now();
    await page.waitForTimeout(8000);
    const after = (await state(s1)).requests.filter(r => r.tokenId === viewer.tokenId && r.status === 401).length;
    await page.waitForTimeout(4000);
    const later = (await state(s1)).requests.filter(r => r.tokenId === viewer.tokenId && r.status === 401).length;
    check(later === after, `revoke: no retries after revocation (${after} then ${later} rejected requests)`);
    const reopened = sockets.filter(socket => socket.at >= revokedAt && socket.url.includes(`/${s1}/ws/metrics`)).length;
    check(reopened === 0, `revoke: ${reopened} metrics sockets reopened`);
    const shown = (await page.locator('main').innerText()).match(/Pairing ended/);
    check(Boolean(shown), `revoke: the page says ${shown ? shown[0] : 'nothing about it'}`);
    check(page.url().includes(idViewer), 'revoke: still on the revoked entry page');
    console.log(`revoke: watched ${Math.round((Date.now() - revokedAt) / 1000)} s`);

    check(errors.length === 0, `page errors: ${errors.length}${errors.length ? ` (${[...new Set(errors)].slice(0, 3).join(' | ')})` : ''}`);
    await context.close();
  } finally {
    await browser.close();
  }
  if (failures.length) {
    console.log(`FAIL (${failures.length})`);
    process.exitCode = 1;
  } else {
    console.log('PASS');
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
