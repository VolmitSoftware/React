// Pairing links against the fake fleet, in one browser:
//   card      a link opens one card: the server's name, until when the link
//             works, what a viewer link allows, the browser's name, Remember
//             this browser, Details (Advanced) with the fingerprint; the
//             code leaves the address bar at once;
//   remember  Remember off: the bearer is in sessionStorage only; another
//             tab and a later session see "Pairing ended";
//   failures  a used, an expired and a revoked link, a page origin the
//             server does not allow, and a server that does not answer each
//             say their own sentence (an https page's local network block is
//             covered by the redeemer's unit tests: this page is http);
//   ended     revoking or expiring a pairing shows "Pairing ended." with a
//             New link field on the open page; a new link pasted there pairs
//             the same entry again;
//   start     the Continue list shows Online, Not reachable and Pairing ended
//             rows with counts; a server that stops while Home is open keeps
//             its counts dimmed under Checking until one summary fails; a link pasted into the Pairing link field
//             and into the Pair a server dialog shows the card there; Forget
//             server removes a row after asking.
//
//   REACT_WEB_URL, REACT_FAKE_URL, PLAYWRIGHT_MODULE as in browser_support.cjs.
//   The fake must allow REACT_WEB_URL's origin and list 5 servers.
const {go, newLink, pairLink, settle, waitForData} = require('./browser_support.cjs');

const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = process.env.REACT_WEB_URL.replace(/\/$/, '');
const fake = process.env.REACT_FAKE_URL.replace(/\/$/, '');
const origin = new URL(url).origin;

const post = async path => {
  const response = await fetch(`${fake}${path}`, {method: 'POST'});
  if (!response.ok) throw new Error(`${path} answered ${response.status}`);
  return response.json();
};
const fakeState = async slug => (await fetch(`${fake}/__fake/state?server=${slug}`)).json();
const mint = (slug, query = '') => post(`/__fake/pair?server=${slug}${query}`);
const hashOf = link => link.slice(link.indexOf('#'));
const mainText = page => page.locator('main').innerText();

async function openLink(page, link) {
  await go(page, url, `/pair${hashOf(link)}`);
  await page.locator('main [data-pairing-card]').waitFor({timeout: 10000});
}

/** The card's problem sentence after Pair (or on open). */
async function problemOf(page) {
  const problem = page.locator('main [data-testid="pair-problem"]');
  await problem.waitFor({timeout: 8000});
  return (await problem.innerText()).replace(/\s+/g, ' ').trim();
}

async function main() {
  const browser = await playwright.chromium.launch({headless: true});
  const failures = [];
  const check = (ok, message) => {
    console.log(`${ok ? 'ok  ' : 'FAIL'} ${message}`);
    if (!ok) failures.push(message);
  };
  try {
    const context = await browser.newContext({viewport: {width: 1600, height: 900}, colorScheme: 'dark'});
    await context.addInitScript(() => localStorage.setItem('reactor.mode', 'advanced'));
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(url);

    // card
    const viewer = await mint('s1', '&role=viewer&label=laptop');
    await page.goto(`${url}/pair${hashOf(viewer.link)}`);
    await page.locator('main [data-pairing-card]').waitFor();
    const card = await mainText(page);
    check(await page.evaluate(() => location.hash === '' && location.pathname === '/pair'), 'card: the code left the address bar');
    check(/Survival/.test(card), 'card: names the server');
    check(/Link works until \d\d:\d\d, once\./.test(card), 'card: says until when the link works');
    check(/See performance, players and incidents/.test(card) && !/Run console commands/.test(card), 'card: a viewer link lists only what a viewer may do');
    const named = await page.locator('#pair-browser-name').inputValue();
    check(/^(Chrome|Firefox|Safari|Edge)( on \w+)?$/.test(named), `card: browser name from the user agent (${named})`);
    check(await page.getByRole('switch', {name: 'Remember this browser'}).isChecked().catch(() => null) !== false, 'card: Remember this browser is on');
    await page.getByText('Details', {exact: true}).click();
    const fingerprint = await page.locator('main [data-testid="pair-fingerprint"]').innerText();
    const s1 = (await (await fetch(`${fake}/__fake/servers`)).json()).servers[0];
    check(fingerprint.trim() === s1.fingerprint, 'card: Details show the server fingerprint');
    check(/laptop/.test(await mainText(page)), 'card: Details show the link name');

    // remember off
    await page.locator('#pair-browser-name').fill('Check browser');
    await page.getByText('Remember this browser', {exact: true}).click();
    const started = Date.now();
    await page.locator('main [data-testid="pair-submit"]').click();
    await page.waitForURL(/\/server\/[^/?#]+/);
    await waitForData(page);
    check(true, `remember: Pair to live data ${Date.now() - started} ms`);
    const tabOnlyId = /\/server\/([^/?#]+)/.exec(page.url())[1];
    const stored = await page.evaluate(id => ({
      local: JSON.parse(localStorage.getItem('reactor.fleet') || '[]').find(e => e.id === id),
      session: sessionStorage.getItem(`reactor.token.${id}`),
    }), tabOnlyId);
    check(stored.local && !stored.local.bearer && stored.local.remember === false, 'remember: localStorage keeps the entry without its bearer');
    check(Boolean(stored.session), 'remember: the bearer is in sessionStorage');
    const tokens = (await fakeState('s1')).tokens;
    check(tokens[viewer.tokenId]?.client === 'Check browser', `remember: the server lists the browser as ${tokens[viewer.tokenId]?.client}`);
    const other = await context.newPage();
    await other.goto(`${url}/server/${tabOnlyId}`);
    await other.locator('main [data-pairing-ended]').waitFor({timeout: 8000}).then(
      () => check(true, 'remember: another tab shows Pairing ended'),
      () => check(false, 'remember: another tab shows Pairing ended'),
    );
    await other.close();

    // failures
    const used = await mint('s2');
    await pairLink(page, url, used.link);
    await openLink(page, used.link);
    await page.locator('main [data-testid="pair-submit"]').click();
    let said = await problemOf(page);
    check(/This link was already used\..*Run \/react web pair again/.test(said), `used: "${said}"`);
    check(await page.locator('#pair-new-link').isVisible(), 'used: New link field');

    const late = await mint('s2', '&linkSeconds=1');
    await page.waitForTimeout(1200);
    await openLink(page, late.link);
    said = await problemOf(page);
    check(/This link expired\./.test(said) && !/Link works until/.test(await mainText(page)), `expired: "${said}"`);

    const cancelled = await mint('s2');
    await post(`/__fake/revoke?server=s2&tokenId=${cancelled.tokenId}`);
    await openLink(page, cancelled.link);
    await page.locator('main [data-testid="pair-submit"]').click();
    said = await problemOf(page);
    check(/This link was revoked\./.test(said), `revoked: "${said}"`);

    await post('/__fake/origins?allow=http://other.example');
    const refused = await mint('s2');
    await openLink(page, refused.link);
    await page.locator('main [data-testid="pair-submit"]').click();
    said = await problemOf(page);
    check(said.includes(`Add ${origin} to allowedOrigins in plugins/React/web.toml`), `origin: "${said}"`);
    await post(`/__fake/origins?allow=${encodeURIComponent(origin)}`);
    await page.locator('main [data-testid="pair-submit"]').click();
    await page.waitForURL(/\/server\//).then(
      () => check(true, 'origin: Pair again after allowing the origin pairs'),
      () => check(false, 'origin: Pair again after allowing the origin pairs'),
    );

    await post('/__fake/down?server=s5&down=true');
    const silent = await mint('s5');
    await openLink(page, silent.link);
    await page.locator('main [data-testid="pair-submit"]').click();
    said = await problemOf(page);
    check(/The server did not answer\..*Check that it is running/.test(said), `unreachable: "${said}"`);
    await post('/__fake/down?server=s5&down=false');

    // ended
    const s3 = await mint('s3');
    const {id: s3Id} = await pairLink(page, url, s3.link);
    await waitForData(page);
    const revokedAt = Date.now();
    await post(`/__fake/revoke?server=s3&tokenId=${s3.tokenId}`);
    await page.locator('main [data-pairing-ended]').waitFor({timeout: 5000}).then(
      () => check(true, `ended: revoke shows Pairing ended in ${Date.now() - revokedAt} ms`),
      () => check(false, 'ended: revoke shows Pairing ended'),
    );
    check(/Pairing ended\./.test(await mainText(page)), 'ended: the page says Pairing ended.');
    const again = await mint('s3');
    await page.locator(`#new-link-${s3Id}`).fill(again.link);
    await page.locator('[role="dialog"] [data-testid="pair-submit"]').click();
    await page.waitForFunction(() => !document.querySelector('main [data-pairing-ended]'), null, {timeout: 8000});
    await waitForData(page);
    check(page.url().includes(`/server/${s3Id}`), 'ended: a new link pasted there pairs the same entry again');

    const s4 = await mint('s4');
    const {id: s4Id} = await pairLink(page, url, s4.link);
    await waitForData(page);
    await post(`/__fake/expire?server=s4&tokenId=${s4.tokenId}`);
    await page.locator('main [data-pairing-ended]').waitFor({timeout: 5000}).then(
      () => check(true, 'ended: an expired pairing shows Pairing ended'),
      () => check(false, 'ended: an expired pairing shows Pairing ended'),
    );

    // start: s3 stops while Home is open. Its row keeps its counts, dimmed,
    // under Checking, until one summary fails; then Not reachable.
    await go(page, url, '/');
    const s3Row = `main [role="row"]:has(a[href="/server/${s3Id}"])`;
    await page.locator(`${s3Row} [data-reach="online"]`).waitFor({timeout: 5000});
    await page.locator(`${s3Row} [data-counts]:not([data-stale])`).waitFor({timeout: 5000});
    await page.evaluate(row => {
      window.__reach = [];
      const watch = () => {
        const el = document.querySelector(row);
        const reach = el?.querySelector('[data-reach]')?.getAttribute('data-reach');
        const counts = el?.querySelector('[data-counts]');
        const seen = `${reach}${counts ? (counts.hasAttribute('data-stale') ? '+stale' : '+counts') : ''}`;
        if (window.__reach.at(-1)?.seen !== seen) window.__reach.push({seen, at: performance.now()});
      };
      new MutationObserver(watch).observe(document.querySelector('main'), {subtree: true, childList: true, attributes: true});
      window.__downAt = performance.now();
      watch();
    }, s3Row);
    await post('/__fake/down?server=s3&down=true');
    await page.evaluate(() => { window.__downAt = performance.now(); });
    await page.locator(`${s3Row} [data-reach="unreachable"]`).waitFor({timeout: 5000}).catch(() => {});
    const steps = await page.evaluate(() => window.__reach.map(step => `${step.seen} ${Math.max(0, Math.round(step.at - window.__downAt))} ms`));
    check(steps.some(step => step.startsWith('checking+stale')) && steps.at(-1).startsWith('unreachable'),
      `start: a stopped server's row keeps its counts dimmed under Checking, then Not reachable (${steps.join(' > ')})`);
    const reach = await page.locator('main [data-reach]').evaluateAll(els => els.map(el => el.getAttribute('data-reach')));
    check(reach.includes('online') && reach.includes('unreachable') && reach.includes('ended'), `start: rows ${reach.join(', ')}`);
    check(/Players\s\d+ · TPS\s\d+\.\d · Incidents\s\d+/.test(await mainText(page)), 'start: an online row shows players, TPS and incidents');
    check(await page.locator(`#new-link-${s4Id}`).isVisible(), 'start: the ended row holds the New link field');
    const upAt = Date.now();
    await post('/__fake/down?server=s3&down=false');
    await page.waitForFunction(() => !document.querySelector('main [data-reach="unreachable"]'), null, {timeout: 5000}).then(
      () => check(true, `start: a server that came back is Online in ${Date.now() - upAt} ms`),
      () => check(false, 'start: a server that came back is Online'),
    );

    const s4again = await mint('s4');
    await page.locator(`#new-link-${s4Id}`).fill(s4again.link);
    await page.locator('[role="dialog"] [data-testid="pair-submit"]').click();
    await page.waitForURL(new RegExp(`/server/${s4Id}`));
    await waitForData(page);
    await go(page, url, '/');
    await page.waitForFunction(() => document.querySelectorAll('main [data-reach]').length > 0 && !document.querySelector('main [data-reach="ended"]'), null, {timeout: 5000}).then(
      () => check(true, 'start: a new link in the ended row pairs it again and the row is back'),
      () => check(false, 'start: a new link in the ended row pairs it again and the row is back'),
    );

    const pasted = await mint('s5');
    await page.locator('main [data-start-pair] input').first().fill(pasted.link);
    await page.locator('[role="dialog"] [data-testid="pair-submit"]').waitFor({timeout: 5000});
    check(/Minigames/.test(await page.locator('[role="dialog"]').last().innerText()), 'start: a pasted link shows the card in the dialog');
    await page.locator('[role="dialog"] [data-testid="pair-submit"]').click();
    await page.waitForURL(/\/server\//);
    await waitForData(page);
    check(true, 'start: Pair in the dialog lands on the server');

    await go(page, url, '/');
    await settle(page);
    await page.getByRole('button', {name: /^Switch server/}).first().click();
    await page.getByRole('menuitem', {name: 'Pair a server'}).click();
    await page.locator('#pair-dialog-link').waitFor();
    await page.locator('#pair-dialog-link').fill((await mint('s2', '&role=operator')).link);
    await page.locator('[role="dialog"] [data-testid="pair-submit"]').waitFor();
    check(/Run actions and switch features/.test(await page.locator('[role="dialog"]').last().innerText()), 'dialog: the switcher\'s Pair a server takes a pasted link');
    await page.getByRole('button', {name: 'Cancel', exact: true}).last().click();
    await page.locator('#pair-dialog-link').waitFor().then(() => check(true, 'dialog: Cancel goes back to the field'), () => check(false, 'dialog: Cancel goes back to the field'));
    await page.keyboard.press('Escape');
    await page.waitForFunction(() => !document.querySelector('#pair-dialog-link'), null, {timeout: 3000}).then(
      () => check(true, 'dialog: Escape closes it'),
      () => check(false, 'dialog: Escape closes it'),
    );

    const rowsBefore = await page.locator('main [data-reach]').count();
    const moreButtons = page.locator('main [role="row"]').getByRole('button', {name: 'More actions'});
    await moreButtons.last().click();
    await page.getByRole('menuitem', {name: 'Forget server'}).click();
    await page.locator('[role="alertdialog"]').waitFor();
    check(/\/react web revoke tok_/.test(await page.locator('[role="alertdialog"]').innerText()), 'forget: the question names the revoke command');
    await page.locator('[role="alertdialog"] [data-confirm]').click();
    await page.waitForFunction(n => document.querySelectorAll('main [data-reach]').length === n - 1, rowsBefore, {timeout: 5000}).then(
      () => check(true, 'forget: the row is gone'),
      () => check(false, 'forget: the row is gone'),
    );

    // a session after the tab-only pairing: it is gone
    const saved = await context.storageState();
    await context.close();
    const later = await browser.newContext({viewport: {width: 1600, height: 900}, storageState: saved});
    const next = await later.newPage();
    await next.goto(`${url}/`);
    await next.locator('main [data-reach]').first().waitFor();
    const endedRows = await next.locator('main [data-reach="ended"]').count();
    check(endedRows >= 1, `remember: after the browser closed, the tab-only pairing shows as ended (${endedRows} ended rows)`);
    await later.close();

    check(errors.length === 0, `page errors: ${errors.length}${errors.length ? ` (${[...new Set(errors)].slice(0, 3).join(' | ')})` : ''}`);
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
