// No card inside a card, on every route at 1600, 768 and 390 px with the
// fake fleet paired. A box is a card when it draws a visible border on at
// least three sides, or a background that differs from the page's together
// with a corner radius or a full border; `.arcane-card` (the library's card
// under every theme) is always one. A screen fails for each card that sits
// inside another card. Not cards: form controls, buttons, tabs, toggle
// groups, badges, key caps, table and list rows, tooltips, popovers, menus,
// toasts, skeleton bars, meters and progress bars, any box shorter than
// 32 px (a chip, tag, legend bar or dot), and what a chart
// ([role="img"]) or heatmap ([role="grid"]) draws inside itself. A dialog
// or sheet is a surface of its own: what is inside it is judged up to it.
// Prints the count per route and width and the total; exits 1 when the
// total is above 0. REACT_CARDS_LIST=1 prints every nested card. Setup and
// the other variables: test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/nested_cards_browser.cjs
const {walk, report} = require('./browser_support.cjs');

async function scan(page) {
  return page.evaluate(() => {
    const notCards = [
      'input', 'textarea', 'select', 'button', '[role="button"]', '.arcane-button', '[role="combobox"]', '[role="switch"]', '[role="checkbox"]', '[role="radio"]', '[role="slider"]',
      '[role="tablist"]', '[role="tab"]', '.arcane-toggle-group',
      '[class*="badge"]', 'kbd', '[class*="swatch"]',
      'tr', '[role="row"]', '[role="option"]', '[role="menuitem"]', '[role="menuitemradio"]', '[role="treeitem"]',
      '[role="tooltip"]', '[role="menu"]', '[role="listbox"]', '[class*="popover"]', '[class*="hover-card"]', '[class*="dropdown-content"]', '[class*="toast"]', '[class*="sonner"]',
      '[class*="skeleton"]', '[role="meter"]', '[role="progressbar"]',
      '[role="img"]', '[role="grid"]', 'canvas', 'svg',
    ].join(', ');
    const layers = '[role="dialog"], [role="alertdialog"]';
    // Any computed colour (rgb(), color(srgb ...), oklch(), ...) as
    // [r, g, b, alpha 0-1], painted on a one-pixel canvas.
    const pixel = document.createElement('canvas').getContext('2d', {willReadFrequently: true});
    const colours = new Map();
    const parse = text => {
      if (!text || text === 'transparent') return [0, 0, 0, 0];
      if (!colours.has(text)) {
        pixel.clearRect(0, 0, 1, 1);
        pixel.fillStyle = 'rgba(0, 0, 0, 0)';
        pixel.fillStyle = text;
        pixel.fillRect(0, 0, 1, 1);
        const [r, g, b, a] = pixel.getImageData(0, 0, 1, 1).data;
        colours.set(text, [r, g, b, a / 255]);
      }
      return colours.get(text);
    };
    const opaque = el => {
      for (let node = el; node; node = node.parentElement) {
        const c = parse(getComputedStyle(node).backgroundColor);
        if (c[3] > 0.95) return c;
      }
      return [0, 0, 0, 1];
    };
    const page = opaque(document.querySelector('main') || document.body);
    const tinted = cs => {
      if (cs.backgroundImage && cs.backgroundImage !== 'none' && !cs.backgroundImage.startsWith('url(')) return true;
      const c = parse(cs.backgroundColor);
      if (c[3] < 0.04) return false;
      return [0, 1, 2].some(i => Math.abs(c[i] * c[3] + page[i] * (1 - c[3]) - page[i]) > 2);
    };
    const isCard = el => {
      if (el.matches('.arcane-card')) return true;
      const cs = getComputedStyle(el);
      // Shorter than a small button: a chip, tag, key cap, bar or dot.
      if (el.getBoundingClientRect().height < 32) return false;
      const sides = ['Top', 'Right', 'Bottom', 'Left'].filter(side =>
        parseFloat(cs[`border${side}Width`]) > 0 && !['none', 'hidden'].includes(cs[`border${side}Style`]) && parse(cs[`border${side}Color`])[3] > 0.04).length;
      const round = ['TopLeft', 'TopRight', 'BottomRight', 'BottomLeft'].some(corner => parseFloat(cs[`border${corner}Radius`]) > 0);
      return sides >= 3 || (tinted(cs) && (round || sides === 4));
    };
    const shown = el => el.checkVisibility({opacityProperty: true, visibilityProperty: true}) && el.getBoundingClientRect().width >= 4 && el.getBoundingClientRect().height >= 4;
    const name = el => {
      const classes = [...el.classList].filter(c => !/^is-|^has-/.test(c)).slice(0, 2).join('.');
      const r = el.getBoundingClientRect();
      return `${el.tagName.toLowerCase()}${classes ? '.' + classes : ''} ${Math.round(r.width)}x${Math.round(r.height)}`;
    };
    const cards = new Set([...document.body.querySelectorAll('*')].filter(el => !el.closest(notCards) && shown(el) && isCard(el)));
    const nested = [];
    for (const el of cards) {
      for (let up = el.parentElement; up && up !== document.body && !up.matches(layers); up = up.parentElement) {
        if (cards.has(up)) {
          nested.push(`${name(el)} in ${name(up)}`);
          break;
        }
      }
    }
    return {cards: cards.size, nested};
  });
}

(async () => {
  const problems = [];
  const cards = new Map();
  const screens = await walk('nested_cards_browser', async (page, {width, name}) => {
    const result = await scan(page);
    cards.set(width, (cards.get(width) || 0) + result.cards);
    for (const text of result.nested) problems.push({width, name, text});
  });
  console.log(`nested_cards_browser: cards seen ${[...cards].map(([width, n]) => `${width} ${n}`).join(', ')}`);
  report('nested_cards_browser', screens, problems, {list: !!process.env.REACT_CARDS_LIST});
})().catch(error => { console.error(error.stack || error.message || error); process.exit(2); });
