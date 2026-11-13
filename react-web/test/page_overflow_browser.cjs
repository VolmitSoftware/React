// No horizontal page scroll on any route at 1600, 768 and 390 px, with the
// fake fleet paired. A screen fails when the document or the <main>
// landmark is wider inside than its box; the problem names the outermost
// elements that stick out past the right edge. Prints the count per route
// and width and the total; exits 1 when the total is above 0.
// REACT_OVERFLOW_LIST=1 prints every problem. Setup and the other
// variables: test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/page_overflow_browser.cjs
const {walk, report} = require('./browser_support.cjs');

async function scan(page) {
  return page.evaluate(() => {
    const problems = [];
    const width = document.documentElement.clientWidth;
    const name = el => {
      const classes = [...el.classList].slice(0, 2).join('.');
      const r = el.getBoundingClientRect();
      return `${el.tagName.toLowerCase()}${classes ? '.' + classes : ''} ${Math.round(r.left)}..${Math.round(r.right)}`;
    };
    const sticking = root => {
      const edge = root === document.scrollingElement ? width : root.getBoundingClientRect().right;
      const out = [...root.querySelectorAll('*')].filter(el => {
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.right > edge + 1 && el.checkVisibility();
      });
      return out.filter(el => !out.includes(el.parentElement)).slice(0, 4).map(name);
    };
    const doc = document.scrollingElement;
    if (doc.scrollWidth > width + 1) {
      problems.push(`page scrolls sideways: ${doc.scrollWidth} in ${width} (${sticking(doc).join(', ')})`);
    }
    for (const main of document.querySelectorAll('main')) {
      const overflow = getComputedStyle(main).overflowX;
      if (overflow !== 'visible' && main.scrollWidth > main.clientWidth + 1) {
        problems.push(`main scrolls sideways: ${main.scrollWidth} in ${main.clientWidth} (${sticking(main).join(', ')})`);
      }
    }
    return problems;
  });
}

(async () => {
  const problems = [];
  const screens = await walk('page_overflow_browser', async (page, {width, name}) => {
    for (const text of await scan(page)) problems.push({width, name, text});
  });
  report('page_overflow_browser', screens, problems, {list: !!process.env.REACT_OVERFLOW_LIST});
})().catch(error => { console.error(error.stack || error.message || error); process.exit(2); });
