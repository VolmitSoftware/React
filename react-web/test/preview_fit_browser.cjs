// Charts, heatmaps and any preview fit their frame, on every route at 1600,
// 768 and 390 px with the fake fleet paired. The previews are found by
// role: a chart is [role="img"], a heatmap [role="grid"], anything else
// marked [data-preview]; a [role="img"] under 64x24 px is an icon, not a
// chart. The log viewport ([role="log"]) is the one region allowed to
// scroll inside itself, so it and what it holds are skipped.
// A screen fails when
//   - a preview is larger inside than its box (scrollHeight > clientHeight
//     + 1 or scrollWidth > clientWidth + 1) and clips or scrolls, so it
//     shows an inner scrollbar or cuts its content;
//   - a box inside the preview, or between it and <main>, clips or scrolls
//     what it holds;
//   - a preview sticks out of its frame (the nearest card around it, or its
//     parent), past its right or bottom edge, or is wider than the frame.
// Form controls, text cut with an ellipsis and SVG drawing internals clip
// their own content; those are not counted. Prints the count per route and
// width and the total, and the count per kind (chart, heatmap, preview); exits 1 when the total is above 0. REACT_FIT_LIST=1 prints
// every problem. Setup and the other variables: test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/preview_fit_browser.cjs
const {walk, report} = require('./browser_support.cjs');

async function scan(page) {
  return page.evaluate(() => {
    const kinds = {img: 'chart', grid: 'heatmap'};
    const roots = '[role="img"], [role="grid"], [data-preview]';
    const ownClip = 'button, input, textarea, select, [role="combobox"], svg *, .sr-only';
    const problems = [];
    const main = document.querySelector('main');
    const shown = el => el.checkVisibility({opacityProperty: true, visibilityProperty: true}) && el.getBoundingClientRect().width >= 2;
    const name = el => {
      const classes = [...el.classList].slice(0, 2).join('.');
      const label = el.getAttribute('aria-label');
      return `${el.tagName.toLowerCase()}${classes ? '.' + classes : ''}${label ? ` "${label.slice(0, 30)}"` : ''}`;
    };
    const size = el => `${el.clientWidth}x${el.clientHeight} holds ${el.scrollWidth}x${el.scrollHeight}`;
    const larger = el => el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1;
    const clips = el => {
      const cs = getComputedStyle(el);
      return cs.overflowX !== 'visible' || cs.overflowY !== 'visible';
    };
    const cutsOwn = el => el.matches(ownClip) || getComputedStyle(el).textOverflow === 'ellipsis';
    const isCard = el => {
      if (el.matches('.arcane-card')) return true;
      const cs = getComputedStyle(el);
      const sides = ['Top', 'Right', 'Bottom', 'Left'].filter(side => parseFloat(cs[`border${side}Width`]) > 0 && !['none', 'hidden'].includes(cs[`border${side}Style`])).length;
      return sides >= 3;
    };
    // An icon with role="img" (a status mark) is not a chart.
    const big = el => el.getAttribute('role') !== 'img' || (el.getBoundingClientRect().width >= 64 && el.getBoundingClientRect().height >= 24);
    const found = [...document.querySelectorAll(roots)].filter(el => shown(el) && big(el) && !el.closest('[role="tooltip"], [role="log"]'));
    const outer = found.filter(el => !found.some(other => other !== el && other.contains(el)));
    for (const root of outer) {
      const kind = root.hasAttribute('data-preview') ? 'preview' : kinds[root.getAttribute('role')];
      const seen = new Set();
      const add = (el, text) => {
        if (seen.has(el)) return;
        seen.add(el);
        problems.push(`${kind} ${name(root)}: ${text}`);
      };
      if (clips(root) && larger(root)) add(root, `scrolls or clips itself: ${size(root)}`);
      for (const el of root.querySelectorAll('*')) {
        if (!shown(el) || cutsOwn(el) || !clips(el) || !larger(el)) continue;
        add(el, `${name(el)} clips inside: ${size(el)}`);
      }
      let frame = root.parentElement;
      for (let el = root.parentElement; el && el !== main && el !== document.body; el = el.parentElement) {
        if (clips(el) && larger(el) && !cutsOwn(el)) add(el, `${name(el)} clips around: ${size(el)}`);
      }
      for (let el = root.parentElement; el && el !== main && el !== document.body; el = el.parentElement) {
        if (isCard(el)) {
          frame = el;
          break;
        }
      }
      if (frame) {
        const r = root.getBoundingClientRect();
        const f = frame.getBoundingClientRect();
        const out = [];
        if (r.width > f.width + 1) out.push(`${Math.round(r.width)} wide in ${Math.round(f.width)}`);
        if (r.right > f.right + 1) out.push(`right ${Math.round(r.right - f.right)}px`);
        if (r.bottom > f.bottom + 1) out.push(`bottom ${Math.round(r.bottom - f.bottom)}px`);
        if (r.left < f.left - 1) out.push(`left ${Math.round(f.left - r.left)}px`);
        if (out.length) add(frame, `sticks out of ${name(frame)} (${out.join(', ')})`);
      }
    }
    return {previews: outer.length, problems};
  });
}

(async () => {
  const problems = [];
  const byKind = new Map();
  let previews = 0;
  const screens = await walk('preview_fit_browser', async (page, {width, name}) => {
    const result = await scan(page);
    previews += result.previews;
    for (const text of result.problems) {
      problems.push({width, name, text});
      const kind = text.split(' ')[0];
      byKind.set(kind, (byKind.get(kind) || 0) + 1);
    }
  });
  console.log(`preview_fit_browser: ${previews} previews checked; by kind ${[...byKind].map(([k, n]) => `${k} ${n}`).join(', ') || 'none'}`);
  report('preview_fit_browser', screens, problems, {list: !!process.env.REACT_FIT_LIST});
})().catch(error => { console.error(error.stack || error.message || error); process.exit(2); });
