// Load and pairing timings against the fake fleet, median of
// REACT_TIMING_RUNS (default 5) fresh browser contexts at 1600 px:
//   - cold load: from navigation to the app's <main> with text, empty cache;
//   - pair to data: from the Pair click to a live number in <main>, for the
//     first server of an empty fleet;
//   - reload to data: with the whole fleet paired, from navigation to a
//     server's overview until a live number shows.
// Prints the numbers; exits 1 only when a step fails. Setup and variables:
// test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/load_timing_browser.cjs
const playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {settings, pairAll, waitForData} = require('./browser_support.cjs');

const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];

(async () => {
  const config = settings();
  const runs = Number(process.env.REACT_TIMING_RUNS || 5);
  const browser = await playwright.chromium.launch({headless: true});
  const cold = [];
  const pair = [];
  const reload = [];
  try {
    for (let run = 0; run < runs; run++) {
      const context = await browser.newContext({viewport: {width: 1600, height: 900}, colorScheme: 'dark'});
      const page = await context.newPage();
      page.setDefaultTimeout(20000);
      let started = Date.now();
      await page.goto(config.url);
      await page.waitForFunction(() => (document.querySelector('main')?.innerText || '').trim().length > 0);
      cold.push(Date.now() - started);
      const {servers, firstData} = await pairAll(page, config);
      pair.push(firstData);
      started = Date.now();
      await page.goto(`${config.url}/server/${servers[0].id}`);
      await waitForData(page);
      reload.push(Date.now() - started);
      await context.close();
    }
  } finally {
    await browser.close();
  }
  console.log(`load_timing_browser: cold load ${median(cold)} ms (${cold.join(', ')})`);
  console.log(`load_timing_browser: pair to data ${median(pair)} ms (${pair.join(', ')})`);
  console.log(`load_timing_browser: reload to data ${median(reload)} ms (${reload.join(', ')})`);
})().catch(error => { console.error(error.stack || error.message || error); process.exit(1); });
