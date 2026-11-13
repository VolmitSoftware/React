// Button sizes, heights and order on every route at 1600, 768 and 390 px,
// with the fake fleet paired. The allowed heights are the library's own: the
// default (md) and small (sm) arcane_jaspr buttons, `.arcane-button
// [data-size]`, measured wherever the walk meets them. A screen fails on:
//   - a button, field, dropdown or toggle whose height is not one of them;
//   - controls of one row with different heights (a button beside a taller
//     field included);
//   - a group of buttons with uneven gaps;
//   - a primary button that is not the last control of its row, or two
//     primary buttons in one row;
//   - a row of controls that wrapped onto a second line (a card or section
//     head whose filters take their own line under the title on a phone
//     is two rows, by the kit's layout);
//   - icons inside buttons at more than one size;
//   - at 760 px and below, a footer ([data-action-row="footer"]) whose
//     buttons are not stacked full width with the primary on top;
//   - two or more buttons laid out in a row outside the kit's action rows
//     ([data-action-row]), the top bar and toggle groups;
//   - buttons of one group spaced other than the gap token (8 px);
//   - a secondary or Cancel that is not an outline button (icon buttons
//     included), or a primary that is not filled;
//   - a borderless (ghost) button in the page or a dialog, other than the
//     (i) tip: icon buttons are outlined like their neighbours; only the
//     top bar's lone buttons are ghost;
//   - an icon-only button without an aria-label or a tooltip.
// Dialogs and sheets are walked as screens of their own (browser_support.cjs).
// A row is the controls laid out by one flex row, through inline wrappers
// and through one-line boxes inside that row. Not buttons here: tabs, menu
// items, options, switches, checkboxes, tree rows, and links of the
// navigation (nav, [role="navigation"], .arcane-sidebar). Exempt, each with
// its reason, in `exempt` below. Prints the count per route and width and
// the total; exits 1 when the total is above 0. REACT_BUTTONS_LIST=1 prints
// every problem; REACT_BUTTONS_WITHIN (a selector list) checks only the
// controls inside those parts. Setup and the other variables:
// test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/button_uniformity_browser.cjs
const {walk, report} = require('./browser_support.cjs');

const within = process.env.REACT_BUTTONS_WITHIN || null;

/** Controls that are not buttons of a row, each with its reason. */
const exempt = [
  // The library's own corner close mark on a dialog, sheet or toast.
  '.arcane-dialog-close', '.arcane-sheet-close', '[class*="toast"]', '[class*="sonner"]',
  // Hover targets drawn inside a chart and the cells of a heatmap.
  'svg', '[role="grid"]',
  // The navigation's links and rows.
  'nav', '[role="navigation"]', '.arcane-sidebar',
  // The scrim behind an open drawer or sheet closes it on a tap.
  '[class*="scrim"]', '[class*="overlay"]',
  // A chart's legend wraps inside its card; its items toggle series.
  '[data-legend]',
];

async function scan(page) {
  return page.evaluate(({skip, within}) => {
    const problems = [];
    const controlSelector = 'button, [role="button"], a.arcane-button, select, input:not([type="checkbox"]):not([type="radio"]):not([type="range"]):not([type="hidden"]):not([type="color"]):not([type="file"])';
    const notButtons = '[role="tab"], [role="menuitem"], [role="menuitemradio"], [role="menuitemcheckbox"], [role="option"], [role="switch"], [role="checkbox"], [role="treeitem"], summary, [data-arcane-command-item]';
    const shown = el => {
      if (!el.checkVisibility({opacityProperty: true, visibilityProperty: true})) return false;
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      // Parked above or left of the page until focused (a skip link).
      if (r.bottom <= 0 || r.right <= 0) return false;
      return !el.closest('[aria-hidden="true"], .sr-only, [inert]');
    };
    const label = el => (el.getAttribute('aria-label') || el.textContent || el.getAttribute('placeholder') || el.value || '').trim().replace(/\s+/g, ' ').slice(0, 30);
    const describe = el => `${el.tagName.toLowerCase()}${el.classList.length ? '.' + [...el.classList].filter(c => c).slice(0, 2).join('.') : ''} "${label(el)}" ${Math.round(el.getBoundingClientRect().height)}`;
    const controls = [...document.querySelectorAll(controlSelector)].filter(el => shown(el) && !el.matches(notButtons) && !el.closest(notButtons) && !el.matches(skip) && !el.closest(skip) && (!within || el.closest(within)));

    // The library's default and small button heights on this screen.
    const reference = {};
    for (const size of ['md', 'sm']) {
      const source = [...document.querySelectorAll(`.arcane-button[data-size="${size}"]`)].find(el => el.checkVisibility());
      if (source) reference[size] = Math.round(source.getBoundingClientRect().height * 10) / 10;
    }

    const isFlexRow = el => {
      const cs = getComputedStyle(el);
      return (cs.display === 'flex' || cs.display === 'inline-flex') && cs.flexDirection.startsWith('row');
    };
    const rowOf = el => {
      let node = el;
      let root = null;
      const height = el.getBoundingClientRect().height;
      while (node.parentElement && node.parentElement !== document.body) {
        const parent = node.parentElement;
        const display = getComputedStyle(parent).display;
        // A head whose filters take their own line under the title (phones)
        // holds two rows: the title's and the controls'.
        if (parent.matches('.ui-card-head, .ui-section-head') && getComputedStyle(parent).flexWrap === 'wrap') break;
        if (isFlexRow(parent)) {
          root = parent;
          node = parent;
          continue;
        }
        if (display === 'inline' || display === 'contents' || display === 'inline-block') {
          node = parent;
          continue;
        }
        // A one-line box laid out inside a flex row is part of that row.
        if (parent.parentElement && isFlexRow(parent.parentElement) && parent.getBoundingClientRect().height <= height * 1.5 + 1) {
          node = parent;
          continue;
        }
        break;
      }
      return root;
    };

    // Rows.
    const rows = new Map();
    for (const el of controls) {
      const root = rowOf(el);
      if (!root) continue;
      if (!rows.has(root)) rows.set(root, []);
      rows.get(root).push(el);
    }
    const where = el => `${el.tagName.toLowerCase()}${el.classList.length ? '.' + [...el.classList].slice(0, 2).join('.') : ''}`;
    for (const [root, items] of rows) {
      if (items.length < 2) continue;
      const rects = items.map(el => el.getBoundingClientRect());
      const heights = rects.map(r => r.height);
      const low = Math.min(...heights);
      if (Math.max(...heights) - low > 1) problems.push(`heights differ in ${where(root)}: ${items.map(describe).join(' + ')}`);
      const tops = rects.map(r => r.top);
      if (Math.max(...tops) - Math.min(...tops) > low / 2) problems.push(`row wraps in ${where(root)}: ${items.map(describe).join(' + ')}`);
      const primaries = items.filter(el => el.matches('[data-variant="primary"], [data-action-slot="primary"]'));
      if (primaries.length > 1) problems.push(`two primary buttons in ${where(root)}: ${primaries.map(describe).join(' + ')}`);
      for (const primary of primaries) {
        const left = primary.getBoundingClientRect().left;
        const after = items.filter(el => el !== primary && el.getBoundingClientRect().left > left + 1);
        if (after.length) problems.push(`primary not last in ${where(root)}: ${describe(primary)} before ${after.map(describe).join(' + ')}`);
      }
    }

    // Gaps inside a group: a flex row whose children are one control each.
    const groups = new Set();
    for (const el of controls) {
      let child = el;
      while (child.parentElement && !isFlexRow(child.parentElement)) child = child.parentElement;
      if (child.parentElement) groups.add(child.parentElement);
    }
    for (const group of groups) {
      // A toggle group's items are joined, not spaced.
      if (group.querySelector('.arcane-toggle-group-item')) continue;
      const kids = [...group.children].filter(shown);
      if (kids.length < 3) continue;
      const inner = kids.map(kid => (kid.matches(controlSelector) ? kid : kid.querySelectorAll(controlSelector).length === 1 ? kid.querySelector(controlSelector) : null));
      if (inner.some(el => !el || !controls.includes(el))) continue;
      const rects = inner.map(el => el.getBoundingClientRect()).sort((a, b) => a.left - b.left);
      if (Math.max(...rects.map(r => r.top)) - Math.min(...rects.map(r => r.top)) > 4) continue;
      const gaps = rects.slice(1).map((r, i) => Math.round(r.left - rects[i].right));
      if (Math.max(...gaps) - Math.min(...gaps) > 1) problems.push(`uneven gaps ${gaps.join('/')} in ${where(group)}: ${inner.map(describe).join(' + ')}`);
    }

    // Buttons in a row outside the kit's rows.
    const kitRows = '[data-action-row], .ui-topbar-row, .arcane-toggle-group, [role="tablist"], [role="radiogroup"]';
    for (const [root, items] of rows) {
      const buttons = items.filter(el => el.matches('button, [role="button"], a.arcane-button') && !el.closest(kitRows));
      if (buttons.length < 2 || root.closest(kitRows)) continue;
      problems.push(`buttons outside an action row in ${where(root)}: ${buttons.map(describe).join(' + ')}`);
    }

    // The gap token between the buttons of one group.
    for (const group of document.querySelectorAll('.ui-actions-end, .ui-actions-lead')) {
      const kids = [...group.children].filter(shown);
      if (kids.length < 2 || group.querySelector('.arcane-toggle-group-item')) continue;
      const rects = kids.map(kid => kid.getBoundingClientRect());
      if (Math.max(...rects.map(r => r.top)) - Math.min(...rects.map(r => r.top)) > 4) continue;
      rects.sort((a, b) => a.left - b.left);
      const gaps = rects.slice(1).map((r, i) => Math.round(r.left - rects[i].right));
      if (gaps.some(gap => Math.abs(gap - 8) > 1)) problems.push(`gap ${gaps.join('/')} not 8 in ${where(group)}`);
    }

    // One look per slot: secondary, Cancel and "..." outline, the primary
    // filled.
    for (const el of controls) {
      const slot = el.getAttribute('data-action-slot');
      if (!slot) continue;
      const variant = el.getAttribute('data-variant');
      const ok = slot === 'primary' ? ['primary', 'destructive'].includes(variant) : variant === 'outline';
      if (!ok) problems.push(`${slot} drawn as ${variant}: ${describe(el)}`);
    }
    // No borderless button beside bordered controls: in the page and in
    // dialogs every button is outlined or filled, except the (i) tip.
    for (const el of controls) {
      if (el.getAttribute('data-variant') !== 'ghost' || el.matches('[data-info-tip]')) continue;
      if (!el.closest('main, [role="dialog"], [role="alertdialog"]')) continue;
      problems.push(`ghost button: ${describe(el)}`);
    }

    // Icon-only buttons: a name and a tooltip.
    const ownText = el => {
      const copy = el.cloneNode(true);
      copy.querySelectorAll('[data-arcane-semantic-icon], i, svg, .sr-only').forEach(x => x.remove());
      return copy.textContent.trim();
    };
    for (const el of controls) {
      if (!el.matches('button, [role="button"], a.arcane-button') || ownText(el)) continue;
      if (!el.getAttribute('aria-label')) problems.push(`icon button without a name: ${describe(el)}`);
      const tip = el.closest('.arcane-floating-container')?.querySelector('[role="tooltip"]');
      if (!tip && !el.getAttribute('title')) problems.push(`icon button without a tooltip: ${describe(el)}`);
    }

    // Footers on phones: stacked, full width, primary on top.
    if (window.innerWidth <= 760) {
      for (const footer of document.querySelectorAll('[data-action-row="footer"]')) {
        const buttons = controls.filter(el => footer.contains(el));
        if (buttons.length < 2) continue;
        const end = footer.getBoundingClientRect();
        const narrow = buttons.filter(el => el.getBoundingClientRect().width < end.width - 2);
        if (narrow.length) problems.push(`footer not full width in ${where(footer)}: ${narrow.map(describe).join(' + ')}`);
        const primary = buttons.find(el => el.matches('[data-action-slot="primary"], [data-variant="primary"]'));
        if (primary && buttons.some(el => el !== primary && el.getBoundingClientRect().top < primary.getBoundingClientRect().top)) problems.push(`footer primary not on top in ${where(footer)}: ${describe(primary)}`);
      }
    }

    // Icon sizes inside buttons.
    const sizes = new Map();
    for (const el of controls) {
      for (const icon of el.querySelectorAll('i, svg')) {
        if (!shown(icon)) continue;
        const w = Math.round(icon.getBoundingClientRect().width);
        if (!sizes.has(w)) sizes.set(w, describe(el));
      }
    }
    if (sizes.size > 1) problems.push(`icons at ${[...sizes.keys()].sort((a, b) => a - b).join(', ')} px: ${[...sizes].map(([w, el]) => `${w} in ${el}`).join('; ')}`);
    return {problems, reference, heights: controls.map(el => ({height: Math.round(el.getBoundingClientRect().height * 10) / 10, text: describe(el)}))};
  }, {skip: exempt.join(', '), within});
}

(async () => {
  const problems = [];
  const measured = [];
  const reference = new Map();
  const screens = await walk('button_uniformity_browser', async (page, {width, name}) => {
    const result = await scan(page);
    for (const text of result.problems) problems.push({width, name, text});
    measured.push({width, name, heights: result.heights});
    for (const [size, height] of Object.entries(result.reference)) {
      const key = `${width} ${size}`;
      if (!reference.has(key)) reference.set(key, height);
    }
  });
  // Heights, once the walk has met the library's buttons at each width.
  for (const {width, name, heights} of measured) {
    const allowed = [...new Set(['md', 'sm'].map(size => reference.get(`${width} ${size}`)).filter(h => h !== undefined))];
    if (!allowed.length) {
      problems.push({width, name, text: 'no library button (.arcane-button[data-size="md"|"sm"]) on any route to measure'});
      continue;
    }
    for (const {height, text} of heights) {
      if (!allowed.some(a => Math.abs(a - height) <= 0.75)) problems.push({width, name, text: `height ${height} not ${allowed.join(' or ')}: ${text}`});
    }
  }
  console.log(`button_uniformity_browser: library heights ${[...reference].map(([key, h]) => `${key} ${h}`).join(', ') || 'none found'}`);
  report('button_uniformity_browser', screens, problems, {list: !!process.env.REACT_BUTTONS_LIST});
})().catch(error => { console.error(error.stack || error.message || error); process.exit(2); });
