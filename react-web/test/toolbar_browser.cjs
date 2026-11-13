// The shell's own controls, without a server: the top bar "..." menu (theme,
// Simple/Advanced, language), the phone drawer and the first-run page.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/toolbar_browser.cjs
const assert = require('node:assert/strict');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

async function openMore(page) {
  const more = page.getByRole('banner').getByRole('button', {name: 'More options', exact: true});
  await more.click();
  await page.getByRole('menu').first().waitFor();
}

async function main() {
  const browser = await chromium.launch({headless: true,
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? {executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE} : {})});
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    if (!localStorage.getItem('reactor.theme')) localStorage.setItem('reactor.theme', 'dark');
  });
  try {
    await page.goto(process.env.REACT_WEB_URL || 'http://127.0.0.1:8080');
    // No server yet: / is Home with the Pairing link field, inside the shell.
    await page.getByRole('heading', {name: 'Home', level: 1}).waitFor();
    await page.getByRole('textbox', {name: 'Pairing link'}).waitFor();
    assert.equal(await page.getByRole('navigation').count() > 0, true);

    await openMore(page);
    await page.getByRole('menuitem', {name: 'Switch to light theme'}).click();
    await page.waitForFunction(() => document.documentElement.classList.contains('light'));
    assert.equal(await page.evaluate(() => localStorage.getItem('reactor.theme')), 'light');
    await page.reload();
    await page.getByRole('heading', {name: 'Home', level: 1}).waitFor();
    await openMore(page);
    await page.getByRole('menuitem', {name: 'Switch to dark theme'}).click();
    await page.waitForFunction(() => document.documentElement.classList.contains('dark'));

    await openMore(page);
    await page.getByRole('menuitem', {name: 'Switch to Advanced'}).click();
    assert.equal(await page.evaluate(() => localStorage.getItem('reactor.mode')), 'advanced');
    await openMore(page);
    await page.getByRole('menuitem', {name: 'Switch to Simple'}).waitFor();
    await page.keyboard.press('Escape');

    await openMore(page);
    await page.getByRole('menuitem', {name: /Select language/}).click();
    const dialog = page.getByRole('dialog', {name: 'Select language'});
    await dialog.waitFor();
    assert.equal(await dialog.getByRole('radio').count(), 18);
    await page.keyboard.press('Escape');
    await dialog.waitFor({state: 'hidden'});

    await page.setViewportSize({width: 375, height: 844});
    const open = page.getByRole('banner').getByRole('button', {name: 'Open navigation', exact: true});
    await open.waitFor();
    assert.equal(await open.getAttribute('aria-expanded'), 'false');
    await open.click();
    const drawer = page.locator('#ui-nav-drawer [role="dialog"]');
    await drawer.waitFor();
    await drawer.getByRole('link', {name: 'Settings'}).waitFor();
    await page.keyboard.press('Escape');
    await drawer.waitFor({state: 'hidden'});
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.deepEqual(errors, []);
    console.log('Toolbar browser passed: first run, theme, mode, language, phone drawer.');
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch(error => {console.error(error); process.exitCode = 1;});
