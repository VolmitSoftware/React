const assert = require('node:assert/strict');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

async function expectTooltip(page, text) {
  await page.waitForFunction(expected =>
    [...document.querySelectorAll('[role="tooltip"]')].some(element =>
      element.checkVisibility() && element.textContent.trim() === expected), text);
  assert.deepEqual(await page.locator('[role="tooltip"]:visible').allTextContents(), [text]);
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
    const light = page.getByRole('button', {name: 'Switch to light theme', exact: true});
    await light.focus();
    await expectTooltip(page, 'Switch to light theme');
    await page.keyboard.press('Enter');
    await expectTooltip(page, 'Switch to dark theme');
    assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Switch to dark theme');
    await page.reload();
    assert.equal(await page.evaluate(() => localStorage.getItem('reactor.theme')), 'light');
    const dark = page.getByRole('button', {name: 'Switch to dark theme', exact: true});
    await dark.hover();
    await expectTooltip(page, 'Switch to dark theme');
    await dark.click();
    await expectTooltip(page, 'Switch to light theme');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
    assert.equal(await page.locator('[role="tooltip"]:visible').count(), 0);

    await page.setViewportSize({width: 375, height: 844});
    await page.mouse.move(2, 820);
    const navigation = page.getByRole('banner').getByRole('button', {name: 'Open navigation', exact: true});
    await navigation.focus();
    await expectTooltip(page, 'Open navigation');
    await page.keyboard.press('Enter');
    await expectTooltip(page, 'Close navigation');
    assert.equal(await page.getByRole('banner').getByRole('button', {name: 'Close navigation', exact: true}).getAttribute('aria-expanded'), 'true');
    await page.keyboard.press('Enter');
    await expectTooltip(page, 'Open navigation');
    assert.equal(await navigation.getAttribute('aria-expanded'), 'false');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.deepEqual(errors, []);
    console.log('Toolbar browser passed: reactive tooltips, keyboard focus, Escape, theme persistence, mobile navigation.');
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch(error => {console.error(error); process.exitCode = 1;});
