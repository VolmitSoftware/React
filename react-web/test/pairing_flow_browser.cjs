// Pairs every fake server one after another in one page through its
// pairing link and checks each pairing lands on the new server's page (H1):
// the address moves to /server/<id>, <main> stops being the pairing card and
// shows live data, the page throws nothing, and the fleet stores every
// server. A second link for a server already paired takes its entry over in
// place (one entry per server).
//
// Odd pairings open the link with a full page load (a click on the console's
// link), even ones through the History API like the app's own links. API
// answers are delayed (REACT_LATENCY_MS, default 150) so the timing is closer
// to a server that is not on the same machine.
//
//   REACT_WEB_URL, REACT_FAKE_URL, PLAYWRIGHT_MODULE as in browser_support.cjs
const {fleet, newLink, pairLink, waitForData} = require('./browser_support.cjs');

const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = process.env.REACT_WEB_URL.replace(/\/$/, '');
const fake = process.env.REACT_FAKE_URL.replace(/\/$/, '');
const latency = Number(process.env.REACT_LATENCY_MS || 150);

const stored = page => page.evaluate(() => JSON.parse(localStorage.getItem('reactor.fleet') || '[]').length);

async function pairOne(page, server, index, errors) {
  const errorsBefore = errors.length;
  const {id, started} = await pairLink(page, url, server.pairingLink, {fullLoad: index % 2 === 0});
  const problems = [];
  await waitForData(page).catch(() => problems.push('no live data in <main>'));
  const toData = Date.now() - started;
  if (await page.locator('main [data-testid="pair-submit"]').count()) {
    problems.push('<main> still shows the pairing card');
  }
  if (await page.evaluate(() => location.hash !== '')) problems.push('the link code is still in the address');
  await page.waitForTimeout(1500);
  const newErrors = errors.slice(errorsBefore);
  if (newErrors.length) problems.push(`${newErrors.length} page errors: ${[...new Set(newErrors)].slice(0, 2).join(' | ')}`);
  const count = await stored(page);
  if (count !== index + 1) problems.push(`fleet stores ${count} entries`);
  console.log(`pairing ${index + 1} (${server.name}): ${toData} ms to data, ${newErrors.length} page errors`);
  return {id, problems};
}

async function main() {
  const servers = await fleet(fake);
  const browser = await playwright.chromium.launch({headless: true});
  const failures = [];
  try {
    const context = await browser.newContext({viewport: {width: 1600, height: 900}, colorScheme: 'dark'});
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    if (latency > 0) {
      await page.route(`${fake}/**/api/v1/**`, async route => {
        await new Promise(resolve => setTimeout(resolve, latency));
        await route.continue();
      });
    }
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(url);
    const ids = [];
    for (const [index, server] of servers.entries()) {
      try {
        const {id, problems} = await pairOne(page, server, index, errors);
        ids.push(id);
        for (const problem of problems) failures.push(`pairing ${index + 1}: ${problem}`);
      } catch (error) {
        failures.push(`pairing ${index + 1}: ${error.message.split('\n')[0]}`);
        if (errors.length) failures.push(`page errors so far: ${[...new Set(errors)].slice(0, 2).join(' | ')}`);
        break;
      }
    }
    if (!failures.length) {
      if (new Set(ids).size !== servers.length) failures.push(`expected ${servers.length} distinct ids, got ${ids.join(', ')}`);
      const errorsBefore = errors.length;
      const {id} = await pairLink(page, url, await newLink(fake, 's1', '&role=viewer&label=again'));
      await page.waitForTimeout(1500);
      const count = await stored(page);
      if (count !== servers.length) failures.push(`second link: fleet stores ${count} entries, expected ${servers.length}`);
      if (id !== ids[0]) failures.push(`second link: landed on ${id}, expected the first entry ${ids[0]}`);
      if (errors.length > errorsBefore) failures.push(`second link: ${errors.length - errorsBefore} page errors`);
      console.log(`second link for a paired server: fleet still has ${count} entries, same id ${id === ids[0]}`);
    }
    await context.close();
  } finally {
    await browser.close();
  }
  if (failures.length) {
    console.log(`FAIL\n  ${failures.join('\n  ')}`);
    process.exitCode = 1;
  } else {
    console.log('PASS');
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
