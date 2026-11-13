// Layout and wording rules that screenshots kept breaking, measured on
// every route, tab, dialog and sheet at 1600, 768 and 390 px with the fake
// fleet paired. A screen fails on:
//   - a toolbar whose controls are not the height of its row: page
//     toolbars at the library's default size, card and section heads, list
//     rows and filter bars at sm (dense); a dense row in the page header or
//     a regular one in a card head;
//   - a select whose chosen text does not fit and is cut without an
//     ellipsis;
//   - a word broken over two lines in a list cell ("Scop / es"), or text
//     spilling out of its cell over the next one;
//   - a tile whose unit repeats its label ("TPS 9.3 TPS");
//   - a chart with more than one tab stop (the plot is one stop; the arrow
//     keys walk the samples);
//   - a form control without a name, a field label that points at no
//     control, or a field error its control does not reference;
//   - on the Console, a page taller than the window (the log grows instead
//     of scrolling inside itself) or, on phones, a log taller than three
//     quarters of the window.
// Prints the count per route and width and the total; exits 1 when the
// total is above 0. REACT_RULES_LIST=1 prints every problem. Setup and the
// other variables: test/browser_support.cjs.
//
//   REACT_WEB_URL=http://127.0.0.1:8820 REACT_FAKE_URL=http://127.0.0.1:8821 \
//     PLAYWRIGHT_MODULE=/path/to/node_modules/playwright node test/layout_rules_browser.cjs
const {walk, report} = require('./browser_support.cjs');

async function scan(page, name) {
  return page.evaluate(name => {
    const problems = [];
    const shown = el => el.checkVisibility() && el.getBoundingClientRect().width > 1;
    const describe = el => {
      const text = (el.getAttribute('aria-label') || el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 30);
      return `${el.tagName.toLowerCase()}${text ? ` "${text}"` : ''}`;
    };
    const layer = [...document.querySelectorAll('[role="dialog"], [role="alertdialog"]')].find(shown);
    const scope = layer || document.querySelector('main') || document.body;

    // The library's two control heights, read off its buttons on screen (a
    // closed sheet's buttons measure 0).
    const height = size => {
      const button = [...document.querySelectorAll(`.arcane-button[data-size="${size}"]`)].find(shown);
      return button ? Math.round(button.getBoundingClientRect().height) : (size === 'sm' ? 32 : 36);
    };
    const md = height('md');
    const sm = height('sm');

    // Toolbars: one height per row, the right one for where the row is.
    const controlsOf = row => [...row.querySelectorAll('button, a.arcane-button, select, input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"]), .arcane-toggle-group-item')]
      .filter(el => shown(el) && !el.closest('[data-info-tip], .ui-info, [role="menu"], .arcane-floating-content'))
      .filter(el => el.closest('[data-action-row]') === row);
    for (const row of scope.querySelectorAll('[data-action-row="toolbar"]')) {
      if (!shown(row)) continue;
      const dense = row.classList.contains('is-dense');
      const inHead = !!row.closest('.ui-card-head, .ui-section-head, .ui-list-row');
      const inPageHeader = !!row.closest('.ui-page-header');
      if (inHead && !dense) problems.push(`regular toolbar in a card head: ${describe(row)}`);
      if (inPageHeader && dense) problems.push(`dense toolbar in the page header: ${describe(row)}`);
      const want = dense ? sm : md;
      for (const el of controlsOf(row)) {
        const h = Math.round(el.getBoundingClientRect().height);
        if (Math.abs(h - want) > 1) problems.push(`${dense ? 'dense' : 'regular'} toolbar control ${h} px, not ${want}: ${describe(el)}`);
      }
    }

    // Selects: the chosen text fits, or ends in an ellipsis.
    const canvas = document.createElement('canvas').getContext('2d');
    for (const select of scope.querySelectorAll('select')) {
      if (!shown(select)) continue;
      const style = getComputedStyle(select);
      const text = select.options[select.selectedIndex]?.text || '';
      canvas.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
      let room = select.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
      // A chevron drawn over the select takes its room too.
      const chevron = select.parentElement?.querySelector('.arcane-native-select-chevron');
      if (chevron && shown(chevron)) {
        room = Math.min(room, chevron.getBoundingClientRect().left - select.getBoundingClientRect().left - parseFloat(style.paddingLeft) - 2);
      }
      if (canvas.measureText(text).width > room + 1 && style.textOverflow !== 'ellipsis') {
        problems.push(`select text cut: "${text}" needs ${Math.round(canvas.measureText(text).width)} px, has ${Math.round(room)}`);
      }
    }

    // List cells: no text spilling out of its cell over its neighbour
    // (cut text must end in an ellipsis inside the cell).
    for (const cell of scope.querySelectorAll('.ui-list-cell')) {
      if (!shown(cell)) continue;
      const edge = cell.getBoundingClientRect().right + 1;
      const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (!node.textContent.trim()) continue;
        const range = document.createRange();
        range.selectNodeContents(node);
        if (range.getBoundingClientRect().right <= edge) continue;
        let clipped = false;
        for (let el = node.parentElement; el && el !== cell.parentElement; el = el.parentElement) {
          const style = getComputedStyle(el);
          if (['hidden', 'clip'].includes(style.overflowX) && el.getBoundingClientRect().right <= edge) { clipped = true; break; }
        }
        if (!clipped) problems.push(`text spills out of a list cell: "${node.textContent.trim().slice(0, 30)}"`);
      }
    }

    // List cells: no word broken over two lines.
    for (const cell of scope.querySelectorAll('.ui-list-cell')) {
      if (!shown(cell)) continue;
      const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        const text = node.textContent;
        for (const match of text.matchAll(/\S{2,}/g)) {
          const range = document.createRange();
          range.setStart(node, match.index);
          range.setEnd(node, match.index + match[0].length);
          const tops = new Set([...range.getClientRects()].filter(r => r.width > 0).map(r => Math.round(r.top)));
          if (tops.size > 1) problems.push(`word broken in a list cell: "${match[0]}"`);
        }
      }
    }

    // Tiles: the unit never repeats the label.
    for (const card of scope.querySelectorAll('.ui-metric .arcane-stat-card')) {
      if (!shown(card)) continue;
      const label = (card.querySelector(':scope > span')?.textContent || '').trim().toLowerCase();
      const spans = [...(card.querySelector(':scope > div')?.children || [])].map(el => el.textContent.trim().toLowerCase());
      const unit = spans[1] || '';
      if (unit && (unit === label || label.endsWith(` ${unit}`))) problems.push(`unit repeats the label: ${label} ... ${unit}`);
    }
    for (const stat of scope.querySelectorAll('.ui-stat')) {
      const label = (stat.querySelector('.ui-stat-label')?.textContent || '').trim().toLowerCase();
      const value = (stat.querySelector('.ui-stat-value')?.textContent || '').trim().toLowerCase();
      const unit = value.split(' ').slice(1).join(' ');
      if (unit && (unit === label || label.endsWith(` ${unit}`))) problems.push(`unit repeats the label: ${label} ... ${value}`);
    }

    // Charts: one tab stop each.
    for (const card of scope.querySelectorAll('[data-chart-card]')) {
      const stops = [...card.querySelectorAll('[tabindex]')].filter(el => el.getAttribute('tabindex') !== '-1' && el.closest('[data-chart-card]') === card && el.closest('svg, [role="img"]'));
      if (stops.length > 1) problems.push(`chart with ${stops.length} tab stops`);
    }

    // Fields: every control on screen has a name, and a field's label names
    // its control; a field's error is linked to its control.
    for (const control of scope.querySelectorAll('input:not([type="hidden"]), select, textarea, [role="switch"], [role="combobox"]')) {
      if (!shown(control)) continue;
      const named = control.getAttribute('aria-label') || control.getAttribute('aria-labelledby') ||
        (control.id && document.querySelector(`label[for="${CSS.escape(control.id)}"]`)) || control.closest('label');
      if (!named) problems.push(`control without a name: ${describe(control)}`);
    }
    for (const field of scope.querySelectorAll('[data-slot="field"]')) {
      const label = field.querySelector(':scope > [data-slot="field-label"] label[for], :scope > label[data-slot="field-label"][for], :scope > [data-slot="field-content"] > [data-slot="field-label"] label[for], :scope > [data-slot="field-content"] > label[data-slot="field-label"][for]');
      if (label && !document.getElementById(label.htmlFor)) problems.push(`field label points at nothing: "${label.textContent.trim()}"`);
      for (const error of field.querySelectorAll('[data-slot="field-error"]')) {
        if (!shown(error)) continue;
        const linked = [...field.querySelectorAll('[aria-describedby]')].some(el => el.getAttribute('aria-describedby').split(/\s+/).includes(error.id));
        if (!linked) problems.push(`field error not linked to its control: "${error.textContent.trim().slice(0, 30)}"`);
      }
    }

    // Console: the page fits the window; the log scrolls inside itself.
    if (name === 'console' && !layer) {
      const page = document.querySelector('.ui-page');
      const log = document.querySelector('[role="log"]');
      if (page && page.scrollHeight > page.clientHeight + 2) problems.push(`console page grows: ${page.scrollHeight} in ${page.clientHeight}`);
      if (log && window.innerWidth <= 760 && log.getBoundingClientRect().height > window.innerHeight * 0.75) problems.push(`console log ${Math.round(log.getBoundingClientRect().height)} px on a ${window.innerHeight} px phone`);
      if (log && log.scrollHeight > log.clientHeight + 2 && getComputedStyle(log).overflowY === 'visible') problems.push('console log does not scroll inside itself');
    }
    return problems;
  }, name);
}

(async () => {
  const problems = [];
  const screens = await walk('layout_rules_browser', async (page, {width, name}) => {
    for (const text of await scan(page, name)) problems.push({width, name, text});
  });
  report('layout_rules_browser', screens, problems, {list: !!process.env.REACT_RULES_LIST});
})().catch(error => { console.error(error.stack || error.message || error); process.exit(2); });
