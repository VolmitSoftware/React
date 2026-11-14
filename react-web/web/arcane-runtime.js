(function() {
  'use strict';
const ARCANE = (window.Arcane = window.Arcane || {});
ARCANE.version = '1';
ARCANE.surfaces = ARCANE.surfaces || {};
ARCANE.groups = ARCANE.groups || {};
ARCANE.tabs = ARCANE.tabs || {};
ARCANE.panels = ARCANE.panels || {};
ARCANE.stack = ARCANE.stack || [];
ARCANE.actions = ARCANE.actions || {};
ARCANE.scripts = ARCANE.scripts || {};
ARCANE.config = ARCANE.config || {
  reducedMotion: window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  storageKey: 'arcane.state',
  themeStorageKey: 'arcane.theme'
};

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  'object',
  'embed',
  '[tabindex]:not([tabindex="-1"])',
  '[contenteditable="true"]'
].join(',');

function dec(s) {
  if (s == null) return '';
  try { return decodeURIComponent(s); } catch (e) { return s; }
}

function enc(s) {
  if (s == null) return '';
  try { return encodeURIComponent(s); } catch (e) { return s; }
}

function parseAction(str) {
  if (!str) return null;
  const trimmed = str.trim();
  if (!trimmed) return null;
  const parts = trimmed.split(/\s+/);
  const verb = parts[0];
  const args = parts.slice(1).map(dec);
  return { verb: verb, args: args };
}

function parseActions(str) {
  if (!str) return [];
  return str.split(';').map(parseAction).filter(Boolean);
}

function fireEvent(el, name, detail) {
  if (!el) el = document;
  const evt = new CustomEvent(name, {
    bubbles: true,
    cancelable: true,
    detail: detail || {}
  });
  el.dispatchEvent(evt);
  return evt;
}

function getFocusable(root) {
  if (!root) return [];
  const list = root.querySelectorAll(FOCUSABLE_SELECTOR);
  const out = [];
  for (let i = 0; i < list.length; i++) {
    const el = list[i];
    if (el.offsetParent === null && el.getClientRects().length === 0) continue;
    if (el.hasAttribute('disabled')) continue;
    if (el.getAttribute('aria-hidden') === 'true') continue;
    // Closed details can retain layout boxes for content that is not tabbable.
    let closedDetailsContent = false;
    for (let ancestor = el.parentElement; ancestor && root.contains(ancestor); ancestor = ancestor.parentElement) {
      if (ancestor.tagName !== 'DETAILS' || ancestor.hasAttribute('open')) continue;
      const summary = Array.from(ancestor.children).find(function(child) {
        return child.tagName === 'SUMMARY';
      });
      if (!summary || !summary.contains(el)) {
        closedDetailsContent = true;
        break;
      }
    }
    if (closedDetailsContent) continue;
    out.push(el);
  }
  return out;
}

function findClosest(el, sel) {
  if (!el) return null;
  if (el.closest) return el.closest(sel);
  let cur = el;
  while (cur && cur.nodeType === 1) {
    if (cur.matches && cur.matches(sel)) return cur;
    cur = cur.parentNode;
  }
  return null;
}

function querySurface(type, id) {
  if (!id) return null;
  const sel = '[data-arcane-surface="' + type + '"][data-arcane-id="' +
    cssEscape(id) + '"]';
  return document.querySelector(sel);
}

function querySurfaceById(id) {
  if (!id) return null;
  const sel = '[data-arcane-surface][data-arcane-id="' + cssEscape(id) + '"]';
  return document.querySelector(sel);
}

function cssEscape(value) {
  if (window.CSS && window.CSS.escape) return window.CSS.escape(value);
  return String(value).replace(/[!"#$%&'()*+,./:;<=>?@\[\\\]^`{|}~]/g, '\\$&');
}

function withinSurface(el, type) {
  return findClosest(el, '[data-arcane-surface' +
    (type ? '="' + type + '"' : '') + ']');
}

function setBoolAttr(el, name, value) {
  if (!el) return;
  if (value) el.setAttribute(name, '');
  else el.removeAttribute(name);
}

function readJson(str, fallback) {
  if (!str) return fallback;
  try { return JSON.parse(str); } catch (e) { return fallback; }
}

function debounce(fn, delay) {
  let t = null;
  return function() {
    const args = arguments;
    const ctx = this;
    if (t) clearTimeout(t);
    t = setTimeout(function() { fn.apply(ctx, args); }, delay);
  };
}

function nextFrame(fn) {
  if (window.requestAnimationFrame) {
    requestAnimationFrame(function() { requestAnimationFrame(fn); });
  } else {
    setTimeout(fn, 16);
  }
}

ARCANE.util = {
  parseAction: parseAction,
  parseActions: parseActions,
  fireEvent: fireEvent,
  getFocusable: getFocusable,
  findClosest: findClosest,
  cssEscape: cssEscape,
  querySurface: querySurface,
  querySurfaceById: querySurfaceById,
  enc: enc,
  dec: dec,
  readJson: readJson,
  debounce: debounce,
  nextFrame: nextFrame
};

function surfaceState(el) {
  if (!el) return 'closed';
  return el.getAttribute('data-arcane-state') || 'closed';
}

function surfaceIsOpen(el) {
  return surfaceState(el) === 'open';
}

function surfaceType(el) {
  if (!el) return null;
  return el.getAttribute('data-arcane-surface');
}

function surfaceId(el) {
  if (!el) return null;
  return el.getAttribute('data-arcane-id');
}

function surfaceGroup(el) {
  if (!el) return null;
  return el.getAttribute('data-arcane-surface-group');
}

function restoreSurfaceFocus(surface, target) {
  if (!target || !target.isConnected || !target.focus) return;
  const top = ARCANE.stack[ARCANE.stack.length - 1];
  if (top && !top.el.contains(target)) return;
  const active = document.activeElement;
  if (active && active !== document.body && active !== document.documentElement &&
      active !== target && !surface.contains(active)) return;
  try { target.focus({ preventScroll: true }); } catch (e) { target.focus(); }
}

function trapFocus(surface, e) {
  if (!surface) return;
  if (surface.getAttribute('data-arcane-focus-trap') === 'false') return;
  const focusable = getFocusable(surface);
  if (focusable.length === 0) {
    e.preventDefault();
    if (!surface.hasAttribute('tabindex')) surface.setAttribute('tabindex', '-1');
    surface.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (e.shiftKey) {
    if (document.activeElement === first || focusable.indexOf(document.activeElement) < 0) {
      e.preventDefault();
      last.focus();
    }
  } else {
    if (document.activeElement === last || focusable.indexOf(document.activeElement) < 0) {
      e.preventDefault();
      first.focus();
    }
  }
}

function syncSurfaceStack() {
  const previousTop = ARCANE.stack[ARCANE.stack.length - 1];
  ARCANE.stack = ARCANE.stack.filter(function(entry) {
    if (entry.el.isConnected && surfaceIsOpen(entry.el)) return true;
    if (entry.el._arcaneAnchorReposition) {
      window.removeEventListener('resize', entry.el._arcaneAnchorReposition);
      window.removeEventListener('scroll', entry.el._arcaneAnchorReposition, true);
      entry.el._arcaneAnchorReposition = null;
    }
    return false;
  });

  const opens = document.querySelectorAll('[data-arcane-surface][data-arcane-state="open"]');
  for (let i = 0; i < opens.length; i++) {
    const el = opens[i];
    if (surfaceIsOpen(el) && !ARCANE.stack.some(function(entry) { return entry.el === el; })) {
      openSurface(surfaceType(el), surfaceId(el));
    }
  }

  const hasOverlay = ARCANE.stack.some(function(entry) {
    return entry.type === 'dialog' || entry.type === 'sheet' || entry.type === 'drawer';
  });
  document.body.classList.toggle('arcane-overlay-open', hasOverlay);
  if (hasOverlay) document.body.setAttribute('data-arcane-overlay-open', 'true');
  else document.body.removeAttribute('data-arcane-overlay-open');

  if (previousTop && !ARCANE.stack.some(function(entry) { return entry.el === previousTop.el; })) {
    const el = previousTop.el;
    const target = el._arcanePrevFocus;
    if (el.getAttribute('data-arcane-restore-focus') !== 'false') {
      restoreSurfaceFocus(el, target);
    }
    el._arcanePrevFocus = null;
  }
}

function observeSurfaces() {
  if (ARCANE._surfaceObserver) return;
  ARCANE._surfaceObserver = new MutationObserver(function(records) {
    const changed = records.some(function(record) {
      if (record.type === 'attributes') return record.target.hasAttribute('data-arcane-surface');
      const nodes = Array.from(record.addedNodes).concat(Array.from(record.removedNodes));
      return nodes.some(function(node) {
        return node.nodeType === 1 && (node.hasAttribute('data-arcane-surface') ||
          node.querySelector('[data-arcane-surface]'));
      });
    });
    if (changed) syncSurfaceStack();
  });
  ARCANE._surfaceObserver.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['data-arcane-state']
  });
}

function applyAnchor(surfaceEl) {
  if (!surfaceEl) return;
  const anchorId = surfaceEl.getAttribute('data-arcane-anchor');
  if (!anchorId) return;
  const anchorEl = document.querySelector(
    '[data-arcane-anchor-id="' + cssEscape(anchorId) + '"]'
  );
  if (!anchorEl) return;
  positionAnchored(surfaceEl, anchorEl);
}

function positionAnchored(surfaceEl, anchorEl) {
  const placement = surfaceEl.getAttribute('data-arcane-anchor-placement') || 'bottom';
  const align = surfaceEl.getAttribute('data-arcane-anchor-align') || 'start';
  const offset = parseInt(
    surfaceEl.getAttribute('data-arcane-anchor-offset') || '8', 10
  );
  // offsetWidth rounds to the nearest pixel; a border-box surface pinned at a
  // width rounded down wraps its widest line, so round its width up.
  const style = typeof window.getComputedStyle === 'function' ? window.getComputedStyle(surfaceEl) : null;
  const fit = style && style.boxSizing === 'border-box' ? Math.ceil(parseFloat(style.width)) : NaN;
  const sw = isNaN(fit) ? surfaceEl.offsetWidth : Math.max(surfaceEl.offsetWidth, fit);
  const sh = surfaceEl.offsetHeight;

  if (!surfaceEl._arcaneAnchorInlineStyle) {
    surfaceEl._arcaneAnchorInlineStyle = {
      position: surfaceEl.style.position,
      top: surfaceEl.style.top,
      left: surfaceEl.style.left,
      bottom: surfaceEl.style.bottom,
      right: surfaceEl.style.right,
      transform: surfaceEl.style.transform,
      width: surfaceEl.style.width,
      minWidth: surfaceEl.style.minWidth
    };
  }
  // Leave the flow at the measured size before reading the anchor. While the
  // surface is in flow it can push its anchor (a centred parent re-centres
  // once the surface is gone), so an earlier read would place the surface
  // over its own trigger.
  surfaceEl.style.position = 'fixed';
  surfaceEl.style.bottom = 'auto';
  surfaceEl.style.right = 'auto';
  const anchorTransform = surfaceEl.getAttribute('data-arcane-anchor-transform');
  if (anchorTransform && surfaceEl.style.transform === anchorTransform) {
    surfaceEl.style.transform = 'none';
  }
  surfaceEl.style.width = sw + 'px';
  surfaceEl.style.minWidth = sw + 'px';
  const rect = anchorEl.getBoundingClientRect();
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  // An ancestor's data-arcane-anchor-top names an element (a page under a
  // fixed top bar) whose top edge the surface stays below.
  let edge = 0;
  const holder = typeof surfaceEl.closest === 'function' ? surfaceEl.closest('[data-arcane-anchor-top]') : null;
  const below = holder ? document.querySelector(holder.getAttribute('data-arcane-anchor-top')) : null;
  if (below) edge = Math.max(0, below.getBoundingClientRect().top);
  let top = 0;
  let left = 0;

  let actualPlacement = placement;
  if (placement === 'bottom' && rect.bottom + offset + sh > vh && rect.top - offset - sh > edge) {
    actualPlacement = 'top';
  } else if (placement === 'top' && rect.top - offset - sh < edge && rect.bottom + offset + sh < vh) {
    actualPlacement = 'bottom';
  } else if (placement === 'right' && rect.right + offset + sw > vw && rect.left - offset - sw > 0) {
    actualPlacement = 'left';
  } else if (placement === 'left' && rect.left - offset - sw < 0 && rect.right + offset + sw < vw) {
    actualPlacement = 'right';
  }

  if (actualPlacement === 'bottom') {
    top = rect.bottom + offset;
    left = align === 'end' ? rect.right - sw : (align === 'center' ? rect.left + (rect.width - sw) / 2 : rect.left);
  } else if (actualPlacement === 'top') {
    top = rect.top - sh - offset;
    left = align === 'end' ? rect.right - sw : (align === 'center' ? rect.left + (rect.width - sw) / 2 : rect.left);
  } else if (actualPlacement === 'right') {
    left = rect.right + offset;
    top = align === 'end' ? rect.bottom - sh : (align === 'center' ? rect.top + (rect.height - sh) / 2 : rect.top);
  } else if (actualPlacement === 'left') {
    left = rect.left - sw - offset;
    top = align === 'end' ? rect.bottom - sh : (align === 'center' ? rect.top + (rect.height - sh) / 2 : rect.top);
  }

  if (left < 4) left = 4;
  if (top < edge + 4) top = edge + 4;
  if (left + sw > vw - 4) left = vw - sw - 4;
  if (top + sh > vh - 4) top = vh - sh - 4;

  surfaceEl.style.top = top + 'px';
  surfaceEl.style.left = left + 'px';
  surfaceEl.setAttribute('data-arcane-actual-placement', actualPlacement);
}

function openSurface(type, id, opts) {
  opts = opts || {};
  const el = querySurface(type, id);
  if (!el) return false;
  if (surfaceIsOpen(el) && ARCANE.stack.some(function(entry) { return entry.el === el; })) return true;

  const groupName = surfaceGroup(el);
  const exclusive = el.getAttribute('data-arcane-exclusive') === 'true';
  if (groupName) {
    const peers = document.querySelectorAll(
      '[data-arcane-surface-group="' + cssEscape(groupName) + '"][data-arcane-state="open"]'
    );
    for (let i = 0; i < peers.length; i++) {
      if (peers[i] !== el) {
        closeSurface(surfaceType(peers[i]), surfaceId(peers[i]), { silent: true, restoreFocus: false });
      }
    }
  }

  if (exclusive) {
    const all = document.querySelectorAll(
      '[data-arcane-surface="' + type + '"][data-arcane-state="open"]'
    );
    for (let i = 0; i < all.length; i++) {
      if (all[i] !== el) closeSurface(type, surfaceId(all[i]), { silent: true });
    }
  }

  if (opts.trigger) {
    el._arcanePrevFocus = opts.trigger;
  } else {
    el._arcanePrevFocus = document.activeElement;
  }

  el.removeAttribute('hidden');
  el.removeAttribute('inert');
  el.setAttribute('data-arcane-state', 'open');
  el.setAttribute('aria-hidden', 'false');

  const anchorId = el.getAttribute('data-arcane-anchor');
  if (anchorId) {
    const anchorEl = document.querySelector(
      '[data-arcane-anchor-id="' + cssEscape(anchorId) + '"]'
    );
    if (anchorEl && anchorEl.hasAttribute('aria-expanded')) {
      anchorEl.setAttribute('aria-expanded', 'true');
    }
  }

  applyAnchor(el);

  ARCANE.stack.push({ type: type, id: id, el: el });

  if (type === 'dialog' || type === 'sheet' || type === 'drawer') {
    document.body.classList.add('arcane-overlay-open');
    document.body.setAttribute('data-arcane-overlay-open', 'true');
  }

  nextFrame(function() {
    // A tooltip or hover card opened since (a pointer resting on another
    // trigger) never takes focus, so it does not keep this one from it.
    const at = ARCANE.stack.findIndex(function(entry) { return entry.el === el; });
    const covered = ARCANE.stack.slice(at + 1).some(function(entry) {
      return entry.type !== 'tooltip' && entry.type !== 'hovercard';
    });
    if (!el.isConnected || !surfaceIsOpen(el) || at < 0 || covered) return;
    el.classList.add('arcane-surface-open');
    el.classList.remove('arcane-surface-closing');

    if (type === 'hovercard' || type === 'tooltip') return;

    const focusTarget = el.querySelector('[data-arcane-autofocus]') ||
      getFocusable(el)[0];
    if (focusTarget) {
      if (!focusTarget.hasAttribute('tabindex') && focusTarget.tabIndex < 0) {
        focusTarget.setAttribute('tabindex', '-1');
      }
      try { focusTarget.focus({ preventScroll: false }); } catch (e) { focusTarget.focus(); }
    } else {
      if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '-1');
      el.focus();
    }
  });

  fireEvent(el, 'arcane:open', { type: type, id: id });
  fireEvent(document, 'arcane:surface-open', { type: type, id: id });

  if (!opts.skipAnchorListener && el.getAttribute('data-arcane-anchor')) {
    el._arcaneAnchorReposition = function() { applyAnchor(el); };
    window.addEventListener('resize', el._arcaneAnchorReposition);
    window.addEventListener('scroll', el._arcaneAnchorReposition, true);
  }

  return true;
}

function closeSurface(type, id, opts) {
  opts = opts || {};
  let el;
  if (id) {
    el = querySurface(type, id);
  } else {
    const list = document.querySelectorAll(
      '[data-arcane-surface="' + type + '"][data-arcane-state="open"]'
    );
    if (list.length > 0) el = list[list.length - 1];
  }
  if (!el) return false;
  if (!surfaceIsOpen(el)) return false;

  el.setAttribute('data-arcane-state', 'closed');
  el.setAttribute('inert', '');
  el.setAttribute('aria-hidden', 'true');
  el.classList.remove('arcane-surface-open');
  el.classList.add('arcane-surface-closing');

  const anchorId = el.getAttribute('data-arcane-anchor');
  if (anchorId) {
    const anchorEl = document.querySelector(
      '[data-arcane-anchor-id="' + cssEscape(anchorId) + '"]'
    );
    if (anchorEl && anchorEl.hasAttribute('aria-expanded')) {
      anchorEl.setAttribute('aria-expanded', 'false');
    }
  }

  ARCANE.stack = ARCANE.stack.filter(function(entry) {
    return entry.el !== el;
  });

  const stillOpen = document.querySelectorAll(
    '[data-arcane-surface="dialog"][data-arcane-state="open"], ' +
    '[data-arcane-surface="sheet"][data-arcane-state="open"], ' +
    '[data-arcane-surface="drawer"][data-arcane-state="open"]'
  ).length;
  if (stillOpen === 0) {
    document.body.classList.remove('arcane-overlay-open');
    document.body.removeAttribute('data-arcane-overlay-open');
  }

  if (el._arcaneAnchorReposition) {
    window.removeEventListener('resize', el._arcaneAnchorReposition);
    window.removeEventListener('scroll', el._arcaneAnchorReposition, true);
    el._arcaneAnchorReposition = null;
  }

  const restoreFocus = opts.restoreFocus !== false && el.getAttribute('data-arcane-restore-focus') !== 'false';
  const prevFocus = el._arcanePrevFocus;

  const finalize = function() {
    if (surfaceIsOpen(el)) return;
    el.setAttribute('hidden', '');
    el.classList.remove('arcane-surface-closing');
    const inlineStyle = el._arcaneAnchorInlineStyle;
    el.style.position = inlineStyle ? inlineStyle.position : '';
    el.style.top = inlineStyle ? inlineStyle.top : '';
    el.style.left = inlineStyle ? inlineStyle.left : '';
    if (inlineStyle) {
      el.style.bottom = inlineStyle.bottom;
      el.style.right = inlineStyle.right;
      el.style.transform = inlineStyle.transform;
    }
    el.style.width = inlineStyle ? inlineStyle.width : '';
    el.style.minWidth = inlineStyle ? inlineStyle.minWidth : '';
    el._arcaneAnchorInlineStyle = null;
    el.removeAttribute('data-arcane-actual-placement');
    if (restoreFocus) {
      restoreSurfaceFocus(el, prevFocus);
    }
    el._arcanePrevFocus = null;
  };

  if (ARCANE.config.reducedMotion || opts.immediate) {
    finalize();
  } else {
    const cs = window.getComputedStyle(el);
    const dur = parseFloat(cs.transitionDuration || '0') || 0;
    if (dur > 0) {
      let done = false;
      const handler = function() {
        if (done) return;
        done = true;
        el.removeEventListener('transitionend', handler);
        finalize();
      };
      el.addEventListener('transitionend', handler);
      setTimeout(handler, dur * 1000 + 100);
    } else {
      finalize();
    }
  }

  if (!opts.silent) {
    fireEvent(el, 'arcane:close', { type: type, id: id });
    fireEvent(document, 'arcane:surface-close', { type: type, id: id });
  }
  return true;
}

function toggleSurface(type, id, opts) {
  const el = querySurface(type, id);
  if (!el) return false;
  if (surfaceIsOpen(el)) return closeSurface(type, id, opts);
  return openSurface(type, id, opts);
}

function dismissTopSurface() {
  if (ARCANE.stack.length === 0) return false;
  const top = ARCANE.stack[ARCANE.stack.length - 1];
  return closeSurface(top.type, top.id);
}

ARCANE.surfaces.open = openSurface;
ARCANE.surfaces.close = closeSurface;
ARCANE.surfaces.toggle = toggleSurface;
ARCANE.surfaces.dismissTop = dismissTopSurface;
ARCANE.surfaces.applyAnchor = applyAnchor;

function groupRoot(groupId) {
  return document.querySelector('[data-arcane-group="' + cssEscape(groupId) +
    '"][data-arcane-group-mode]');
}

function groupValues(groupId) {
  const root = groupRoot(groupId);
  if (!root) return [];
  const raw = root.getAttribute('data-arcane-group-value') || '';
  if (!raw) return [];
  return raw.split('\u001f').filter(Boolean);
}

function groupMode(groupId) {
  const root = groupRoot(groupId);
  if (!root) return 'single';
  return root.getAttribute('data-arcane-group-mode') || 'single';
}

function setGroupRawValues(groupId, values) {
  const root = groupRoot(groupId);
  if (!root) return;
  root.setAttribute('data-arcane-group-value', values.join('\u001f'));
  syncGroupItems(groupId, values);
  fireEvent(root, 'arcane:change', { groupId: groupId, value: values });
  const changeAction = root.getAttribute('data-arcane-group-change');
  if (changeAction) {
    runActions(changeAction, { trigger: root, groupId: groupId, value: values });
  }
}

function syncGroupItems(groupId, values) {
  const root = groupRoot(groupId);
  if (!root) return;
  const items = document.querySelectorAll(
    '[data-arcane-group="' + cssEscape(groupId) + '"][data-arcane-value]'
  );
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (item === root) continue;
    const v = item.getAttribute('data-arcane-value');
    const selected = values.indexOf(v) >= 0;
    item.setAttribute('data-arcane-state', selected ? 'selected' : 'unselected');
    const role = item.getAttribute('role');
    if (role === 'option' || role === 'tab') {
      item.setAttribute('aria-selected', selected ? 'true' : 'false');
    }
    if (role === 'checkbox' || role === 'switch' || role === 'radio' ||
        role === 'menuitemcheckbox' || role === 'menuitemradio') {
      item.setAttribute('aria-checked', selected ? 'true' : 'false');
      item.setAttribute('data-state', selected ? 'checked' : 'unchecked');
    }
    if (item.tagName === 'INPUT' && (item.type === 'checkbox' || item.type === 'radio')) {
      item.checked = selected;
    }
  }
}

function setGroupValue(groupId, value) {
  const mode = groupMode(groupId);
  if (mode === 'multi') {
    setGroupRawValues(groupId, value ? [value] : []);
  } else {
    setGroupRawValues(groupId, value ? [value] : []);
  }
}

function toggleGroupValue(groupId, value) {
  const mode = groupMode(groupId);
  const cur = groupValues(groupId);
  if (mode === 'multi') {
    const idx = cur.indexOf(value);
    let next;
    if (idx >= 0) {
      next = cur.slice();
      next.splice(idx, 1);
    } else {
      const root = groupRoot(groupId);
      const max = root ? parseInt(root.getAttribute('data-arcane-group-max') || '0', 10) : 0;
      if (max > 0 && cur.length >= max) {
        next = cur.slice(1);
        next.push(value);
      } else {
        next = cur.concat([value]);
      }
    }
    setGroupRawValues(groupId, next);
  } else {
    if (cur.length === 1 && cur[0] === value) {
      const root = groupRoot(groupId);
      if (root && root.getAttribute('data-arcane-group-required') === 'true') return;
      setGroupRawValues(groupId, []);
    } else {
      setGroupRawValues(groupId, [value]);
    }
  }
}

function clearGroup(groupId) {
  setGroupRawValues(groupId, []);
}

ARCANE.groups.values = groupValues;
ARCANE.groups.mode = groupMode;
ARCANE.groups.set = setGroupValue;
ARCANE.groups.toggle = toggleGroupValue;
ARCANE.groups.clear = clearGroup;

function activateTab(groupId, tabId) {
  const triggers = document.querySelectorAll(
    '[data-arcane-tabs-group="' + cssEscape(groupId) +
    '"][data-arcane-tab]'
  );
  for (let i = 0; i < triggers.length; i++) {
    const t = triggers[i];
    const id = t.getAttribute('data-arcane-tab');
    const active = id === tabId;
    t.setAttribute('data-arcane-state', active ? 'active' : 'inactive');
    if (t.getAttribute('role') === 'tab') {
      t.setAttribute('aria-selected', active ? 'true' : 'false');
      t.setAttribute('tabindex', active ? '0' : '-1');
    }
  }
  const panels = document.querySelectorAll(
    '[data-arcane-tabs-group="' + cssEscape(groupId) +
    '"][data-arcane-tab-panel]'
  );
  for (let i = 0; i < panels.length; i++) {
    const p = panels[i];
    const id = p.getAttribute('data-arcane-tab-panel');
    const active = id === tabId;
    p.setAttribute('data-arcane-state', active ? 'active' : 'inactive');
    if (active) p.removeAttribute('hidden');
    else p.setAttribute('hidden', '');
  }
  const containers = document.querySelectorAll(
    '[data-arcane-tabs-group="' + cssEscape(groupId) +
    '"]:not([data-arcane-tab]):not([data-arcane-tab-panel])'
  );
  for (let i = 0; i < containers.length; i++) {
    containers[i].setAttribute('data-arcane-tabs-active', tabId);
    fireEvent(containers[i], 'arcane:tabs-change', {
      groupId: groupId, tabId: tabId
    });
  }
}

ARCANE.tabs.activate = activateTab;

function setPanelState(groupId, panelId, expanded) {
  const trigger = document.querySelector(
    '[data-arcane-panel-group="' + cssEscape(groupId) +
    '"][data-arcane-panel="' + cssEscape(panelId) + '"]'
  );
  const content = document.querySelector(
    '[data-arcane-panel-group="' + cssEscape(groupId) +
    '"][data-arcane-panel-content="' + cssEscape(panelId) + '"]'
  );
  if (trigger) {
    trigger.setAttribute('data-arcane-state', expanded ? 'expanded' : 'collapsed');
    trigger.setAttribute('aria-expanded', expanded ? 'true' : 'false');
  }
  if (content) {
    content.setAttribute('data-arcane-state', expanded ? 'expanded' : 'collapsed');
    if (expanded) content.removeAttribute('hidden');
    else content.setAttribute('hidden', '');
  }
}

function expandPanel(groupId, panelId) {
  const trigger = document.querySelector(
    '[data-arcane-panel-group="' + cssEscape(groupId) +
    '"][data-arcane-panel="' + cssEscape(panelId) + '"]'
  );
  if (trigger && trigger.getAttribute('data-arcane-panel-exclusive') === 'true') {
    const all = document.querySelectorAll(
      '[data-arcane-panel-group="' + cssEscape(groupId) +
      '"][data-arcane-panel][data-arcane-state="expanded"]'
    );
    for (let i = 0; i < all.length; i++) {
      const id = all[i].getAttribute('data-arcane-panel');
      if (id !== panelId) setPanelState(groupId, id, false);
    }
  }
  setPanelState(groupId, panelId, true);
  fireEvent(document, 'arcane:panel-change', {
    groupId: groupId, panelId: panelId, expanded: true
  });
}

function collapsePanel(groupId, panelId) {
  setPanelState(groupId, panelId, false);
  fireEvent(document, 'arcane:panel-change', {
    groupId: groupId, panelId: panelId, expanded: false
  });
}

function togglePanel(groupId, panelId) {
  const trigger = document.querySelector(
    '[data-arcane-panel-group="' + cssEscape(groupId) +
    '"][data-arcane-panel="' + cssEscape(panelId) + '"]'
  );
  if (!trigger) return;
  const expanded = trigger.getAttribute('data-arcane-state') === 'expanded';
  if (expanded) collapsePanel(groupId, panelId);
  else expandPanel(groupId, panelId);
}

ARCANE.panels.expand = expandPanel;
ARCANE.panels.collapse = collapsePanel;
ARCANE.panels.toggle = togglePanel;

function stepperRoot(groupId) {
  return document.querySelector('[data-arcane-stepper="' + cssEscape(groupId) +
    '"][data-arcane-step-active]');
}

function goToStep(groupId, target) {
  const root = stepperRoot(groupId);
  if (!root) return;
  const cur = parseInt(root.getAttribute('data-arcane-step-active') || '0', 10);
  const count = parseInt(root.getAttribute('data-arcane-step-count') || '1', 10);
  let next = cur;
  if (target === 'next') next = Math.min(cur + 1, count - 1);
  else if (target === 'prev') next = Math.max(cur - 1, 0);
  else {
    const n = parseInt(target, 10);
    if (!isNaN(n)) next = Math.max(0, Math.min(n, count - 1));
  }
  if (next === cur) return;
  root.setAttribute('data-arcane-step-active', next.toString());
  const steps = document.querySelectorAll(
    '[data-arcane-stepper="' + cssEscape(groupId) + '"][data-arcane-step]'
  );
  for (let i = 0; i < steps.length; i++) {
    const stepEl = steps[i];
    const idx = parseInt(stepEl.getAttribute('data-arcane-step') || '0', 10);
    let state = 'pending';
    if (idx < next) state = 'completed';
    else if (idx === next) state = 'active';
    stepEl.setAttribute('data-arcane-state', state);
  }
  fireEvent(root, 'arcane:step-change', { groupId: groupId, step: next });
}

ARCANE.stepper = {
  go: goToStep
};

function paginationRoot(groupId) {
  return document.querySelector('[data-arcane-pagination="' + cssEscape(groupId) +
    '"][data-arcane-page-active]');
}

function goToPage(groupId, target) {
  const root = paginationRoot(groupId);
  if (!root) return;
  const cur = parseInt(root.getAttribute('data-arcane-page-active') || '1', 10);
  const count = parseInt(root.getAttribute('data-arcane-page-count') || '1', 10);
  let next = cur;
  if (target === 'next') next = Math.min(cur + 1, count);
  else if (target === 'prev') next = Math.max(cur - 1, 1);
  else if (target === 'first') next = 1;
  else if (target === 'last') next = count;
  else {
    const n = parseInt(target, 10);
    if (!isNaN(n)) next = Math.max(1, Math.min(n, count));
  }
  if (next === cur) return;
  root.setAttribute('data-arcane-page-active', next.toString());
  fireEvent(root, 'arcane:page-change', { groupId: groupId, page: next });
}

ARCANE.pagination = {
  go: goToPage
};

function carouselRoot(groupId) {
  return document.querySelector('[data-arcane-carousel="' + cssEscape(groupId) +
    '"][data-arcane-carousel-active]');
}

function setCarouselSlide(groupId, idx) {
  const root = carouselRoot(groupId);
  if (!root) return;
  const count = parseInt(root.getAttribute('data-arcane-carousel-count') || '1', 10);
  const loop = root.getAttribute('data-arcane-carousel-loop') === 'true';
  let next = idx;
  if (loop) {
    if (next < 0) next = count - 1;
    if (next >= count) next = 0;
  } else {
    if (next < 0) next = 0;
    if (next >= count) next = count - 1;
  }
  root.setAttribute('data-arcane-carousel-active', next.toString());
  const slides = document.querySelectorAll(
    '[data-arcane-carousel="' + cssEscape(groupId) +
    '"][data-arcane-carousel-slide]'
  );
  for (let i = 0; i < slides.length; i++) {
    const s = slides[i];
    const sIdx = parseInt(s.getAttribute('data-arcane-carousel-slide') || '0', 10);
    s.setAttribute('data-arcane-state', sIdx === next ? 'active' : 'inactive');
  }
  fireEvent(root, 'arcane:carousel-change', { groupId: groupId, index: next });
}

function goToSlide(groupId, target) {
  const root = carouselRoot(groupId);
  if (!root) return;
  const cur = parseInt(root.getAttribute('data-arcane-carousel-active') || '0', 10);
  let next = cur;
  if (target === 'next') next = cur + 1;
  else if (target === 'prev') next = cur - 1;
  else {
    const n = parseInt(target, 10);
    if (!isNaN(n)) next = n;
  }
  setCarouselSlide(groupId, next);
}

function startCarouselAutoplay(root) {
  const ms = parseInt(root.getAttribute('data-arcane-carousel-autoplay') || '0', 10);
  if (!ms || ms <= 0) return;
  const groupId = root.getAttribute('data-arcane-carousel');
  if (root._arcaneCarouselTimer) clearInterval(root._arcaneCarouselTimer);
  root._arcaneCarouselTimer = setInterval(function() {
    goToSlide(groupId, 'next');
  }, ms);
  root.addEventListener('mouseenter', function() {
    if (root._arcaneCarouselTimer) clearInterval(root._arcaneCarouselTimer);
  });
  root.addEventListener('mouseleave', function() {
    startCarouselAutoplay(root);
  });
}

ARCANE.carousel = {
  go: goToSlide,
  set: setCarouselSlide,
  startAutoplay: startCarouselAutoplay
};

function sliderRoot(sliderId) {
  return document.querySelector('[data-arcane-slider="' + cssEscape(sliderId) + '"][data-arcane-slider-min]');
}

function sliderClampValue(root, value) {
  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');
  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');
  const step = parseFloat(root.getAttribute('data-arcane-slider-step') || '0') || 0;
  let v = Math.max(min, Math.min(max, value));
  if (step > 0) {
    const ticks = Math.round((v - min) / step);
    v = min + ticks * step;
  }
  if (v < min) v = min;
  if (v > max) v = max;
  return v;
}

function sliderApplyValue(root, value, opts) {
  opts = opts || {};
  const sliderId = root.getAttribute('data-arcane-slider');
  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');
  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');
  const range = root.getAttribute('data-arcane-slider-range') === 'true';
  const role = opts.role || 'value';

  const clamped = sliderClampValue(root, value);
  const pct = max > min ? ((clamped - min) / (max - min)) * 100 : 0;

  if (range) {
    let lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || min);
    let hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || max);
    if (role === 'lo') {
      lo = clamped;
      if (lo > hi) lo = hi;
    } else {
      hi = clamped;
      if (hi < lo) hi = lo;
    }
    root.setAttribute('data-arcane-slider-lo', String(lo));
    root.setAttribute('data-arcane-slider-hi', String(hi));
    const loPct = max > min ? ((lo - min) / (max - min)) * 100 : 0;
    const hiPct = max > min ? ((hi - min) / (max - min)) * 100 : 0;
    const fill = root.querySelector('[data-arcane-slider-fill]');
    if (fill) {
      fill.style.left = loPct + '%';
      fill.style.right = (100 - hiPct) + '%';
      fill.style.width = (hiPct - loPct) + '%';
    }
    const thumbLo = root.querySelector('[data-arcane-slider-thumb="lo"]');
    if (thumbLo) {
      thumbLo.style.left = loPct + '%';
      thumbLo.setAttribute('aria-valuenow', String(lo));
    }
    const thumbHi = root.querySelector('[data-arcane-slider-thumb="hi"]');
    if (thumbHi) {
      thumbHi.style.left = hiPct + '%';
      thumbHi.setAttribute('aria-valuenow', String(hi));
    }
    fireEvent(root, 'arcane:slider-change', {
      sliderId: sliderId, lo: lo, hi: hi
    });
  } else {
    root.setAttribute('data-arcane-slider-value', String(clamped));
    const fill = root.querySelector('[data-arcane-slider-fill]');
    if (fill) {
      fill.style.width = pct + '%';
    }
    const thumb = root.querySelector('[data-arcane-slider-thumb]');
    if (thumb) {
      thumb.style.left = pct + '%';
      thumb.setAttribute('aria-valuenow', String(clamped));
    }
    fireEvent(root, 'arcane:slider-change', {
      sliderId: sliderId, value: clamped
    });
  }

  const changeAction = root.getAttribute('data-arcane-slider-change');
  if (changeAction) {
    runActions(changeAction, { trigger: root, sliderId: sliderId });
  }
}

function sliderValueFromPointer(root, clientX) {
  const track = root.querySelector('[data-arcane-slider-track]') || root;
  const rect = track.getBoundingClientRect();
  if (rect.width <= 0) return 0;
  const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');
  const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');
  const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  return min + ratio * (max - min);
}

function sliderRoleFromTarget(root, target) {
  const t = findClosest(target, '[data-arcane-slider-thumb]');
  if (t) {
    return t.getAttribute('data-arcane-slider-thumb') === 'hi' ? 'hi' : 'lo';
  }
  if (root.getAttribute('data-arcane-slider-range') === 'true') {
    const lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || '0');
    const hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || '100');
    const target_value = sliderValueFromPointer(root, event.clientX);
    return Math.abs(target_value - lo) < Math.abs(target_value - hi) ? 'lo' : 'hi';
  }
  return 'value';
}

function bindSliders() {
  let activeSlider = null;
  let activeRole = null;

  function onMove(e) {
    if (!activeSlider) return;
    e.preventDefault();
    const v = sliderValueFromPointer(activeSlider, e.clientX);
    sliderApplyValue(activeSlider, v, { role: activeRole });
  }

  function onUp() {
    activeSlider = null;
    activeRole = null;
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    document.removeEventListener('pointercancel', onUp);
  }

  document.addEventListener('pointerdown', function(e) {
    const root = findClosest(e.target, '[data-arcane-slider]');
    if (!root) return;
    if (root.getAttribute('data-arcane-disabled') === 'true') return;
    const range = root.getAttribute('data-arcane-slider-range') === 'true';
    let role = 'value';
    if (range) {
      const t = findClosest(e.target, '[data-arcane-slider-thumb]');
      if (t) {
        role = t.getAttribute('data-arcane-slider-thumb');
      } else {
        const v = sliderValueFromPointer(root, e.clientX);
        const lo = parseFloat(root.getAttribute('data-arcane-slider-lo') || '0');
        const hi = parseFloat(root.getAttribute('data-arcane-slider-hi') || '100');
        role = Math.abs(v - lo) < Math.abs(v - hi) ? 'lo' : 'hi';
      }
    }
    activeSlider = root;
    activeRole = role;
    const v = sliderValueFromPointer(root, e.clientX);
    sliderApplyValue(root, v, { role: role });
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
    document.addEventListener('pointercancel', onUp);
  }, true);

  document.addEventListener('keydown', function(e) {
    const thumb = findClosest(e.target, '[data-arcane-slider-thumb]');
    if (!thumb) return;
    const root = findClosest(thumb, '[data-arcane-slider]');
    if (!root) return;
    if (root.getAttribute('data-arcane-disabled') === 'true') return;
    const role = thumb.getAttribute('data-arcane-slider-thumb');
    const step = parseFloat(root.getAttribute('data-arcane-slider-step') || '1') || 1;
    const min = parseFloat(root.getAttribute('data-arcane-slider-min') || '0');
    const max = parseFloat(root.getAttribute('data-arcane-slider-max') || '100');
    let cur;
    if (role === 'lo' || role === 'hi') {
      cur = parseFloat(root.getAttribute('data-arcane-slider-' + role) || '0');
    } else {
      cur = parseFloat(root.getAttribute('data-arcane-slider-value') || '0');
    }
    let next = cur;
    if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') next = cur - step;
    else if (e.key === 'ArrowRight' || e.key === 'ArrowUp') next = cur + step;
    else if (e.key === 'PageDown') next = cur - step * 10;
    else if (e.key === 'PageUp') next = cur + step * 10;
    else if (e.key === 'Home') next = min;
    else if (e.key === 'End') next = max;
    else return;
    e.preventDefault();
    sliderApplyValue(root, next, {
      role: role === 'lo' || role === 'hi' ? role : 'value'
    });
  }, true);
}

function setSliderValue(sliderId, value, role) {
  const root = sliderRoot(sliderId);
  if (!root) return;
  const v = parseFloat(value);
  if (isNaN(v)) return;
  sliderApplyValue(root, v, { role: role || 'value' });
}

ARCANE.slider = {
  set: setSliderValue,
  bind: bindSliders
};

function filterCommand(cmdId, query) {
  const surface = querySurface('command', cmdId) ||
    querySurface('popover', cmdId) ||
    document.querySelector('[data-arcane-command="' + cssEscape(cmdId) + '"]');
  if (!surface) return;
  const q = (query || '').toLowerCase().trim();
  const items = surface.querySelectorAll('[data-arcane-command-item]');
  let visibleCount = 0;
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    const label = (item.getAttribute('data-label') || '').toLowerCase();
    const keywords = (item.getAttribute('data-keywords') || '').toLowerCase();
    const disabled = item.getAttribute('data-arcane-disabled') === 'true';
    let matches = q === '' || label.indexOf(q) >= 0 || keywords.indexOf(q) >= 0;
    if (matches) {
      item.removeAttribute('hidden');
      item.setAttribute('data-arcane-state', disabled ? 'disabled' : 'visible');
      if (!disabled) visibleCount++;
    } else {
      item.setAttribute('hidden', '');
      item.setAttribute('data-arcane-state', 'hidden');
    }
  }
  const groups = surface.querySelectorAll('[data-arcane-command-group]');
  for (let i = 0; i < groups.length; i++) {
    const g = groups[i];
    const groupId = g.getAttribute('data-arcane-command-group');
    const groupItems = surface.querySelectorAll(
      '[data-arcane-command-item][data-arcane-command-group-id="' + cssEscape(groupId) + '"]:not([hidden])'
    );
    if (groupItems.length === 0) {
      g.setAttribute('hidden', '');
    } else {
      g.removeAttribute('hidden');
    }
  }
  const empty = surface.querySelector('[data-arcane-command-empty]');
  if (empty) {
    if (visibleCount === 0) {
      empty.removeAttribute('hidden');
    } else {
      empty.setAttribute('hidden', '');
    }
  }
  fireEvent(surface, 'arcane:command-filter', { id: cmdId, query: q, visible: visibleCount });
}

function commandSelectFirst(cmdId) {
  const surface = querySurface('command', cmdId) ||
    querySurface('popover', cmdId);
  if (!surface) return;
  const visible = surface.querySelectorAll(
    '[data-arcane-command-item]:not([hidden]):not([data-arcane-disabled="true"])'
  );
  if (visible.length === 0) return;
  visible[0].click();
}

function bindCommandKeyboard() {
  document.addEventListener('keydown', function(e) {
    if (e.key !== 'Enter' && e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const input = e.target;
    if (!input || !input.matches || !input.matches('[data-arcane-command-input]')) return;
    const cmdId = input.getAttribute('data-arcane-command-input');
    const surface = querySurface('command', cmdId) ||
      querySurface('popover', cmdId);
    if (!surface) return;
    const visible = Array.prototype.slice.call(surface.querySelectorAll(
      '[data-arcane-command-item]:not([hidden]):not([data-arcane-disabled="true"])'
    ));
    if (visible.length === 0) return;
    let activeIdx = visible.findIndex(function(el) {
      return el.getAttribute('data-arcane-state') === 'active';
    });
    if (e.key === 'Enter') {
      e.preventDefault();
      const target = activeIdx >= 0 ? visible[activeIdx] : visible[0];
      if (target) target.click();
      return;
    }
    e.preventDefault();
    if (activeIdx >= 0) {
      visible[activeIdx].setAttribute('data-arcane-state', 'visible');
    }
    let next = activeIdx;
    if (e.key === 'ArrowDown') next = activeIdx < 0 ? 0 : (activeIdx + 1) % visible.length;
    else next = activeIdx <= 0 ? visible.length - 1 : activeIdx - 1;
    visible[next].setAttribute('data-arcane-state', 'active');
    visible[next].scrollIntoView({ block: 'nearest' });
  }, true);
}

ARCANE.command = {
  filter: filterCommand,
  selectFirst: commandSelectFirst
};

function cycleRoot(cycleId) {
  return document.querySelector('[data-arcane-cycle="' + cssEscape(cycleId) + '"]');
}

function cycleValues(root) {
  const raw = root.getAttribute('data-arcane-cycle-values');
  if (!raw) return [];
  try { return JSON.parse(raw); } catch (e) { return []; }
}

function cycleLabels(root) {
  const raw = root.getAttribute('data-arcane-cycle-labels');
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (e) { return null; }
}

function cycleStep(cycleId, delta) {
  const root = cycleRoot(cycleId);
  if (!root) return;
  const values = cycleValues(root);
  if (!values.length) return;
  const cur = parseInt(root.getAttribute('data-arcane-cycle-active') || '0', 10);
  const loop = root.getAttribute('data-arcane-cycle-loop') !== 'false';
  let next = cur + delta;
  if (loop) {
    if (next < 0) next = values.length - 1;
    if (next >= values.length) next = 0;
  } else {
    if (next < 0) next = 0;
    if (next >= values.length) next = values.length - 1;
  }
  if (next === cur) return;
  setCycleIndex(cycleId, next);
}

function setCycleIndex(cycleId, idx) {
  const root = cycleRoot(cycleId);
  if (!root) return;
  const values = cycleValues(root);
  if (idx < 0 || idx >= values.length) return;
  const labels = cycleLabels(root);
  root.setAttribute('data-arcane-cycle-active', idx.toString());
  root.setAttribute('data-arcane-value', values[idx]);
  const display = root.querySelector('[data-arcane-cycle-display]') || root;
  if (display !== root) {
    display.textContent = labels && labels[idx] != null ? labels[idx] : values[idx];
  } else {
    const label = root.querySelector('[data-arcane-cycle-label]');
    if (label) {
      label.textContent = labels && labels[idx] != null ? labels[idx] : values[idx];
    }
  }
  fireEvent(root, 'arcane:cycle-change', {
    id: cycleId, index: idx, value: values[idx]
  });
}

ARCANE.cycle = {
  step: cycleStep,
  set: setCycleIndex
};

function _calRoot(target){
  if (!target) return null;
  if (target.matches && target.matches('[data-arcane-calendar]')) return target;
  return target.querySelector ? target.querySelector('[data-arcane-calendar]') : null;
}
function _calMonthLabel(d){
  const months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
  return months[d.getMonth()] + ' ' + d.getFullYear();
}
function _calParseDate(s){
  if (!s) return null;
  const parts = String(s).split('-');
  if (parts.length !== 3) return null;
  const y = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10) - 1;
  const d = parseInt(parts[2], 10);
  if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
  return new Date(y, m, d);
}
function _calFmtDate(d){
  if (!d) return '';
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return y + '-' + m + '-' + dd;
}
function _calIsSameDay(a, b){
  if (!a || !b) return false;
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
function _calInRange(d, start, end){
  if (!d || !start || !end) return false;
  const t = d.getTime();
  return t >= start.getTime() && t <= end.getTime();
}
function _calDisabled(d, root){
  const min = _calParseDate(root.getAttribute('data-arcane-min'));
  const max = _calParseDate(root.getAttribute('data-arcane-max'));
  if (min && d.getTime() < min.getTime()) return true;
  if (max && d.getTime() > max.getTime()) return true;
  return false;
}
function _calWeekNumber(date){
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil((((d - yearStart) / 86400000) + 1) / 7);
}
function _calHumanize(d){
  if (!d) return '';
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  return months[d.getMonth()] + ' ' + d.getDate() + ', ' + d.getFullYear();
}
function _calDisplay(root){
  const mode = root.getAttribute('data-arcane-mode') || 'single';
  if (mode === 'range'){
    const rs = root.getAttribute('data-arcane-range-start');
    const re = root.getAttribute('data-arcane-range-end');
    if (!rs || !re) return '';
    const sd = _calParseDate(rs); const ed = _calParseDate(re);
    return _calHumanize(sd) + ' - ' + _calHumanize(ed);
  }
  const sel = root.getAttribute('data-arcane-selected');
  if (!sel) return '';
  return _calHumanize(_calParseDate(sel));
}
function renderCalendar(root){
  if (!root) return;
  const year = parseInt(root.getAttribute('data-arcane-year') || String(new Date().getFullYear()), 10);
  const month = parseInt(root.getAttribute('data-arcane-month') || String(new Date().getMonth()), 10);
  const firstDayOfWeek = parseInt(root.getAttribute('data-arcane-first-day') || '0', 10);
  const showWeekNumbers = root.getAttribute('data-arcane-show-week-numbers') === 'true';
  const showToday = root.getAttribute('data-arcane-show-today') !== 'false';
  const mode = root.getAttribute('data-arcane-mode') || 'single';
  const selected = _calParseDate(root.getAttribute('data-arcane-selected'));
  const rangeStart = _calParseDate(root.getAttribute('data-arcane-range-start'));
  const rangeEnd = _calParseDate(root.getAttribute('data-arcane-range-end'));
  const pendingStart = _calParseDate(root.getAttribute('data-arcane-pending-start'));
  const displayMonth = new Date(year, month, 1);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const monthStart = new Date(year, month, 1);
  const monthEnd = new Date(year, month + 1, 0);
  const startOffset = (monthStart.getDay() - firstDayOfWeek + 7) % 7;
  const gridStart = new Date(year, month, 1 - startOffset);
  const id = root.getAttribute('data-arcane-id') || '';
  let html = '';
  // Keep generated markup out of the global script's literal DOM text. Jaspr
  // hydrates inline scripts in the app tree, and raw tag-shaped strings can be
  // exposed as hidden template controls by DOM/a11y tooling. JavaScript
  // resolves \x3C to "<" only when this function actually renders a calendar.
  html += '\x3Cdiv class="arcane-calendar-header" data-arcane-calendar-header>';
  html += '\x3Cbutton type="button" class="arcane-calendar-nav arcane-calendar-prev" data-arcane-action="calendar.prev:' + id + '" aria-label="Previous month">&#8249;\x3C/button>';
  html += '\x3Cdiv class="arcane-calendar-label" data-arcane-calendar-label>' + _calMonthLabel(displayMonth) + '\x3C/div>';
  html += '\x3Cbutton type="button" class="arcane-calendar-nav arcane-calendar-next" data-arcane-action="calendar.next:' + id + '" aria-label="Next month">&#8250;\x3C/button>';
  html += '\x3C/div>';
  if (showToday){
    html += '\x3Cdiv class="arcane-calendar-today-row">\x3Cbutton type="button" class="arcane-calendar-today" data-arcane-action="calendar.today:' + id + '">Today\x3C/button>\x3C/div>';
  }
  const weekdays = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
  html += '\x3Cdiv class="arcane-calendar-weekdays">';
  if (showWeekNumbers) html += '\x3Cdiv class="arcane-calendar-weekday arcane-calendar-week-num">#\x3C/div>';
  for (let i = 0; i < 7; i++){
    html += '\x3Cdiv class="arcane-calendar-weekday">' + weekdays[(firstDayOfWeek + i) % 7] + '\x3C/div>';
  }
  html += '\x3C/div>';
  html += '\x3Cdiv class="arcane-calendar-grid">';
  let iter = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate());
  for (let w = 0; w < 6; w++){
    if (showWeekNumbers){
      const weekNo = _calWeekNumber(iter);
      html += '\x3Cdiv class="arcane-calendar-weeknum">' + weekNo + '\x3C/div>';
    }
    for (let d = 0; d < 7; d++){
      const inMonth = iter.getMonth() === month;
      const disabled = _calDisabled(iter, root);
      const isToday = _calIsSameDay(iter, today);
      const isSelected = mode === 'single' && _calIsSameDay(iter, selected);
      const inRange = mode === 'range' && rangeStart && rangeEnd && _calInRange(iter, rangeStart, rangeEnd);
      const isRangeStart = mode === 'range' && _calIsSameDay(iter, rangeStart);
      const isRangeEnd = mode === 'range' && _calIsSameDay(iter, rangeEnd);
      const isPendingStart = mode === 'range' && _calIsSameDay(iter, pendingStart);
      const classes = ['arcane-calendar-day'];
      if (!inMonth) classes.push('arcane-calendar-day-other-month');
      if (disabled) classes.push('arcane-calendar-day-disabled');
      if (isToday) classes.push('arcane-calendar-day-today');
      if (isSelected) classes.push('arcane-calendar-day-selected');
      if (inRange) classes.push('arcane-calendar-day-in-range');
      if (isRangeStart) classes.push('arcane-calendar-day-range-start');
      if (isRangeEnd) classes.push('arcane-calendar-day-range-end');
      if (isPendingStart) classes.push('arcane-calendar-day-pending');
      const iso = _calFmtDate(iter);
      const label = iter.getDate();
      if (disabled){
        html += '\x3Cbutton type="button" class="' + classes.join(' ') + '" disabled aria-disabled="true">' + label + '\x3C/button>';
      } else {
        html += '\x3Cbutton type="button" class="' + classes.join(' ') + '" data-arcane-action="calendar.select:' + id + '" data-arcane-value="' + iso + '" aria-pressed="' + (isSelected || isRangeStart || isRangeEnd ? 'true' : 'false') + '">' + label + '\x3C/button>';
      }
      iter = new Date(iter.getFullYear(), iter.getMonth(), iter.getDate() + 1);
    }
    if (iter.getMonth() !== month && iter > monthEnd) break;
  }
  html += '\x3C/div>';
  root.innerHTML = html;
}
function _calEmitChange(root, value){
  let evt;
  try { evt = new CustomEvent('arcane:change', { detail: { value: value }, bubbles: true }); }
  catch (e){ evt = document.createEvent('CustomEvent'); evt.initCustomEvent('arcane:change', true, false, { value: value }); }
  root.dispatchEvent(evt);
  const id = root.getAttribute('data-arcane-id');
  if (!id) return;
  const trigger = document.querySelector('[data-arcane-calendar-trigger="' + cssEscape(id) + '"]');
  if (trigger){
    const label = trigger.querySelector('[data-arcane-calendar-display]');
    if (label) label.textContent = _calDisplay(root) || (trigger.getAttribute('data-arcane-placeholder') || '');
  }
}
function _calResolveRoot(target){
  if (!target) return null;
  if (typeof target === 'string'){
    return _calRoot(document.querySelector('[data-arcane-id="' + cssEscape(target) + '"]'));
  }
  return _calRoot(target);
}
function _calClosePicker(id){
  const picker = document.querySelector('[data-arcane-surface][data-arcane-calendar-anchor="' + cssEscape(id) + '"]');
  if (picker) closeSurface(surfaceType(picker), surfaceId(picker));
}
function calendarPrev(target){
  const root = _calResolveRoot(target);
  if (!root) return;
  const year = parseInt(root.getAttribute('data-arcane-year'), 10);
  const month = parseInt(root.getAttribute('data-arcane-month'), 10);
  const d = new Date(year, month - 1, 1);
  root.setAttribute('data-arcane-year', String(d.getFullYear()));
  root.setAttribute('data-arcane-month', String(d.getMonth()));
  renderCalendar(root);
}
function calendarNext(target){
  const root = _calResolveRoot(target);
  if (!root) return;
  const year = parseInt(root.getAttribute('data-arcane-year'), 10);
  const month = parseInt(root.getAttribute('data-arcane-month'), 10);
  const d = new Date(year, month + 1, 1);
  root.setAttribute('data-arcane-year', String(d.getFullYear()));
  root.setAttribute('data-arcane-month', String(d.getMonth()));
  renderCalendar(root);
}
function calendarToday(target){
  const root = _calResolveRoot(target);
  if (!root) return;
  const now = new Date();
  root.setAttribute('data-arcane-year', String(now.getFullYear()));
  root.setAttribute('data-arcane-month', String(now.getMonth()));
  renderCalendar(root);
}
function calendarSelect(target, value){
  const root = _calResolveRoot(target);
  if (!root) return;
  const picked = _calParseDate(value);
  if (!picked || _calDisabled(picked, root)) return;
  const mode = root.getAttribute('data-arcane-mode') || 'single';
  const id = root.getAttribute('data-arcane-id');
  if (mode === 'range'){
    const pendingStart = root.getAttribute('data-arcane-pending-start');
    if (!pendingStart){
      root.setAttribute('data-arcane-pending-start', value);
      root.removeAttribute('data-arcane-range-start');
      root.removeAttribute('data-arcane-range-end');
      renderCalendar(root);
    } else {
      let startD = _calParseDate(pendingStart);
      let endD = picked;
      if (startD.getTime() > endD.getTime()){
        const tmp = startD; startD = endD; endD = tmp;
      }
      root.setAttribute('data-arcane-range-start', _calFmtDate(startD));
      root.setAttribute('data-arcane-range-end', _calFmtDate(endD));
      root.removeAttribute('data-arcane-pending-start');
      renderCalendar(root);
      _calEmitChange(root, _calFmtDate(startD) + '/' + _calFmtDate(endD));
      _calClosePicker(id);
    }
  } else {
    root.setAttribute('data-arcane-selected', value);
    renderCalendar(root);
    _calEmitChange(root, value);
    _calClosePicker(id);
  }
}
function bindCalendars(){
  const nodes = document.querySelectorAll('[data-arcane-calendar]:not([data-arcane-calendar-bound])');
  for (let i = 0; i < nodes.length; i++){
    nodes[i].setAttribute('data-arcane-calendar-bound', 'true');
    if (!nodes[i].innerHTML.trim()) renderCalendar(nodes[i]);
  }
}

function bindTimePickers() {
  if (!ARCANE._timePopupBound) {
    ARCANE._timePopupBound = true;
    document.addEventListener('arcane:open', function(event) {
      const popup = event.target;
      if (!popup.matches || !popup.matches('[data-arcane-time-popup]')) return;
      const columns = popup.querySelectorAll('[data-arcane-time-column]');
      for (const column of columns) {
        const selected = column.querySelector('[aria-pressed="true"]');
        if (!selected) continue;
        column.scrollTop = selected.offsetTop -
          (column.clientHeight - selected.offsetHeight) / 2;
      }
    });
  }
  const pickers = document.querySelectorAll('[data-arcane-time-picker]');
  for (let i = 0; i < pickers.length; i++) {
    const picker = pickers[i];
    if (picker.__arcaneTimeBound) continue;
    picker.__arcaneTimeBound = true;
    const pickerId = picker.getAttribute('data-arcane-time-picker');
    const use24 = picker.getAttribute('data-arcane-time-24h') === 'true';
    const clearable = picker.getAttribute('data-arcane-time-clearable') === 'true';
    const placeholder = picker.getAttribute('data-arcane-time-placeholder') || 'Select time...';
    const hourGroup = pickerId + '-hour';
    const minuteGroup = pickerId + '-minute';
    const periodGroup = pickerId + '-period';
    function updateDisplay() {
      const hourSet = ARCANE.groups.values(hourGroup);
      const minuteSet = ARCANE.groups.values(minuteGroup);
      const periodSet = ARCANE.groups.values(periodGroup);
      const hour = hourSet.length > 0 ? hourSet[0] : null;
      const minute = minuteSet.length > 0 ? minuteSet[0] : null;
      const period = periodSet.length > 0 ? periodSet[0] : null;
      const display = picker.querySelector('[data-arcane-time-display]');
      if (!display) return;
      if (hour === null && minute === null) {
        display.textContent = placeholder;
        picker.removeAttribute('data-arcane-time-value');
        return;
      }
      const h = hour !== null ? parseInt(hour, 10) : 0;
      const m = minute !== null ? parseInt(minute, 10) : 0;
      const pad = function(n) { return n < 10 ? '0' + n : '' + n; };
      let text;
      if (use24) {
        text = pad(h) + ':' + pad(m);
      } else {
        const p = period === 'pm' ? 'PM' : 'AM';
        text = pad(h) + ':' + pad(m) + ' ' + p;
      }
      display.textContent = text;
      picker.setAttribute('data-arcane-time-value', text);
      fireEvent(picker, 'arcane:change', {
        id: pickerId, hour: h, minute: m, period: period, text: text
      });
    }
    document.addEventListener('arcane:change', function(ev) {
      const detail = ev.detail || {};
      if (detail.groupId === hourGroup || detail.groupId === minuteGroup || detail.groupId === periodGroup) {
        updateDisplay();
      }
    });
    const clear = picker.querySelector('[data-arcane-time-clear]');
    if (clear && clearable) {
      clear.addEventListener('click', function(ev) {
        ev.preventDefault();
        ev.stopPropagation();
        ARCANE.groups.clear(hourGroup);
        ARCANE.groups.clear(minuteGroup);
        ARCANE.groups.clear(periodGroup);
        updateDisplay();
      });
    }
    updateDisplay();
  }
}

ARCANE.timePicker = {
  bind: bindTimePickers
};

function otpContext(target) {
  if (!target || !target.matches || !target.matches('input[data-otp-index]')) return null;
  const root = target.closest('[data-arcane-otp]');
  if (!root || root.getAttribute('data-disabled') === 'true' || target.disabled) return null;
  const inputs = Array.from(root.querySelectorAll('input[data-otp-index]'));
  return { inputs: inputs, index: inputs.indexOf(target) };
}

function focusOtpDigit(inputs, index) {
  const input = inputs[Math.max(0, Math.min(index, inputs.length - 1))];
  if (!input || input.disabled) return;
  input.focus();
  input.select();
}

function bindOtpInputs() {
  document.addEventListener('input', function(event) {
    const context = otpContext(event.target);
    if (!context || event.isComposing) return;
    const input = event.target;
    const digits = input.value.replace(/[^0-9]/g, '');
    // The hydrated callback needs the whole edit after the DOM slots are split.
    event._arcaneOtpValue = digits;
    let next = context.index;
    if (digits.length > 1) {
      const start = digits.length >= context.inputs.length ? 0 : context.index;
      for (let i = start; i < context.inputs.length; i++) {
        context.inputs[i].value = digits[i - start] || '';
      }
      next = start + digits.length;
    } else {
      input.value = digits;
      if (digits) next++;
    }
    context.inputs.forEach(function(digit) {
      digit.classList.toggle('filled', !!digit.value);
      digit.setAttribute('data-state', digit.value ? 'filled' : 'empty');
    });
    if (digits) focusOtpDigit(context.inputs, next);
  }, true);

  document.addEventListener('keydown', function(event) {
    const context = otpContext(event.target);
    if (!context || event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return;
    let next = context.index;
    if (event.key === 'ArrowLeft' || (event.key === 'Backspace' && !event.target.value)) next--;
    else if (event.key === 'ArrowRight') next++;
    else return;
    event.preventDefault();
    focusOtpDigit(context.inputs, next);
  }, true);

  document.addEventListener('paste', function(event) {
    const context = otpContext(event.target);
    if (!context || !event.clipboardData) return;
    event.preventDefault();
    const digits = event.clipboardData.getData('text').replace(/[^0-9]/g, '');
    if (!digits) return;
    event.target.value = digits;
    event.target.dispatchEvent(new InputEvent('input', {
      bubbles: true, inputType: 'insertFromPaste', data: digits
    }));
  }, true);

  document.addEventListener('focusin', function(event) {
    if (otpContext(event.target)) event.target.select();
  }, true);
}

function formRoot(formId) {
  return document.querySelector('[data-arcane-form="' + cssEscape(formId) + '"]');
}

function fieldEls(formId, fieldName) {
  return document.querySelectorAll(
    '[data-arcane-form="' + cssEscape(formId) +
    '"][data-arcane-field="' + cssEscape(fieldName) + '"]'
  );
}

function fieldValue(formId, fieldName) {
  const els = fieldEls(formId, fieldName);
  if (!els.length) return null;
  const el = els[0];
  if (el.tagName === 'INPUT') {
    if (el.type === 'checkbox') return el.checked;
    if (el.type === 'radio') {
      for (let i = 0; i < els.length; i++) if (els[i].checked) return els[i].value;
      return null;
    }
    return el.value;
  }
  if (el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') return el.value;
  return el.getAttribute('data-arcane-field-value') || el.textContent;
}

function setFieldValue(formId, fieldName, value) {
  const els = fieldEls(formId, fieldName);
  for (let i = 0; i < els.length; i++) {
    const el = els[i];
    if (el.tagName === 'INPUT' && el.type === 'checkbox') {
      el.checked = !!value;
    } else if (el.tagName === 'INPUT' && el.type === 'radio') {
      el.checked = el.value === value;
    } else if (el.tagName === 'INPUT' || el.tagName === 'SELECT' ||
      el.tagName === 'TEXTAREA') {
      el.value = value;
    } else {
      el.setAttribute('data-arcane-field-value', value);
    }
  }
  const root = formRoot(formId);
  if (root) {
    fireEvent(root, 'arcane:field-change', {
      formId: formId, field: fieldName, value: value
    });
  }
}

function collectForm(formId) {
  const root = formRoot(formId);
  if (!root) return null;
  const out = {};
  const fields = root.querySelectorAll('[data-arcane-field]');
  const seen = {};
  for (let i = 0; i < fields.length; i++) {
    const name = fields[i].getAttribute('data-arcane-field');
    if (seen[name]) continue;
    seen[name] = true;
    out[name] = fieldValue(formId, name);
  }
  return out;
}

function validateField(formId, fieldName) {
  const els = fieldEls(formId, fieldName);
  if (!els.length) return { ok: true };
  const el = els[0];
  const validate = el.getAttribute('data-arcane-field-validate') || '';
  const required = el.getAttribute('data-arcane-field-required') === 'true';
  const value = fieldValue(formId, fieldName);
  const errors = [];

  if (required) {
    if (value == null || value === '' || value === false) {
      errors.push('required');
    }
  }
  if (value && validate) {
    const rules = validate.split(/\s+/);
    for (let i = 0; i < rules.length; i++) {
      const r = rules[i];
      if (r === 'email') {
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) errors.push('email');
      } else if (r === 'url') {
        try { new URL(value); } catch (e) { errors.push('url'); }
      } else if (r.indexOf('min:') === 0) {
        const min = parseInt(r.substring(4), 10);
        if (typeof value === 'string' && value.length < min) errors.push('min');
      } else if (r.indexOf('max:') === 0) {
        const max = parseInt(r.substring(4), 10);
        if (typeof value === 'string' && value.length > max) errors.push('max');
      } else if (r.indexOf('pattern:') === 0) {
        try {
          const re = new RegExp(r.substring(8));
          if (!re.test(value)) errors.push('pattern');
        } catch (e) {}
      }
    }
  }

  for (let i = 0; i < els.length; i++) {
    if (errors.length) {
      els[i].setAttribute('data-arcane-field-invalid', 'true');
      els[i].setAttribute('aria-invalid', 'true');
    } else {
      els[i].removeAttribute('data-arcane-field-invalid');
      els[i].setAttribute('aria-invalid', 'false');
    }
  }
  const errorEl = document.querySelector(
    '[data-arcane-form="' + cssEscape(formId) +
    '"][data-arcane-field-error="' + cssEscape(fieldName) + '"]'
  );
  if (errorEl) {
    errorEl.textContent = errors.length ? errorEl.getAttribute('data-arcane-error-' + errors[0]) || errors[0] : '';
    if (errors.length) errorEl.removeAttribute('hidden');
    else errorEl.setAttribute('hidden', '');
  }
  return { ok: errors.length === 0, errors: errors };
}

function validateForm(formId) {
  const root = formRoot(formId);
  if (!root) return { ok: true };
  const fields = root.querySelectorAll('[data-arcane-field]');
  let allOk = true;
  const seen = {};
  for (let i = 0; i < fields.length; i++) {
    const name = fields[i].getAttribute('data-arcane-field');
    if (seen[name]) continue;
    seen[name] = true;
    const result = validateField(formId, name);
    if (!result.ok) allOk = false;
  }
  return { ok: allOk };
}

function submitForm(formId) {
  const root = formRoot(formId);
  if (!root) return;
  const validation = validateForm(formId);
  if (!validation.ok) {
    fireEvent(root, 'arcane:form-invalid', { formId: formId });
    return;
  }
  const data = collectForm(formId);
  fireEvent(root, 'arcane:form-submit', { formId: formId, data: data });
  if (root.getAttribute('data-arcane-form-reset-on-submit') === 'true') {
    resetForm(formId);
  }
}

function resetForm(formId) {
  const root = formRoot(formId);
  if (!root) return;
  if (root.tagName === 'FORM') root.reset();
  const fields = root.querySelectorAll('[data-arcane-field]');
  for (let i = 0; i < fields.length; i++) {
    const el = fields[i];
    if (el.tagName === 'INPUT') {
      if (el.type === 'checkbox' || el.type === 'radio') el.checked = el.defaultChecked;
      else el.value = el.defaultValue;
    } else if (el.tagName === 'SELECT') {
      for (let j = 0; j < el.options.length; j++) el.options[j].selected = el.options[j].defaultSelected;
    } else if (el.tagName === 'TEXTAREA') {
      el.value = el.defaultValue;
    }
    el.removeAttribute('data-arcane-field-invalid');
    el.setAttribute('aria-invalid', 'false');
  }
  fireEvent(root, 'arcane:form-reset', { formId: formId });
}

ARCANE.forms = {
  submit: submitForm,
  reset: resetForm,
  validate: validateForm,
  setField: setFieldValue,
  fieldValue: fieldValue,
  collect: collectForm
};

function setThemeMode(mode) {
  document.documentElement.setAttribute('data-arcane-theme', mode);
  document.documentElement.classList.remove('light', 'dark');
  if (mode === 'dark' || mode === 'light') {
    document.documentElement.classList.add(mode);
  }
  try { localStorage.setItem('arcane.theme.mode', mode); } catch (e) {}
  fireEvent(document, 'arcane:theme-change', { mode: mode });
}

function toggleThemeMode() {
  const cur = document.documentElement.getAttribute('data-arcane-theme') ||
    (document.documentElement.classList.contains('dark') ? 'dark' : 'light');
  setThemeMode(cur === 'dark' ? 'light' : 'dark');
}

function setStylesheet(id) {
  document.documentElement.setAttribute('data-arcane-stylesheet', id);
  try { localStorage.setItem('arcane.theme.stylesheet', id); } catch (e) {}
  fireEvent(document, 'arcane:stylesheet-change', { id: id });
}

function setPalette(stylesheetId, paletteId) {
  document.documentElement.setAttribute('data-arcane-stylesheet', stylesheetId);
  document.documentElement.setAttribute('data-arcane-palette', paletteId);
  try {
    localStorage.setItem('arcane.theme.stylesheet', stylesheetId);
    localStorage.setItem('arcane.theme.palette', paletteId);
  } catch (e) {}
  fireEvent(document, 'arcane:palette-change', { id: paletteId, stylesheetId: stylesheetId });
}

function hydrateThemeFromStorage() {
  try {
    const m = localStorage.getItem('arcane.theme.mode');
    if (m && (m === 'light' || m === 'dark')) {
      document.documentElement.setAttribute('data-arcane-theme', m);
      document.documentElement.classList.remove('light', 'dark');
      document.documentElement.classList.add(m);
    }
    const s = localStorage.getItem('arcane.theme.stylesheet');
    if (s) document.documentElement.setAttribute('data-arcane-stylesheet', s);
    const p = localStorage.getItem('arcane.theme.palette');
    if (p) document.documentElement.setAttribute('data-arcane-palette', p);
  } catch (e) {}
}

ARCANE.theme = {
  setMode: setThemeMode,
  toggleMode: toggleThemeMode,
  setStylesheet: setStylesheet,
  setPalette: setPalette,
  hydrate: hydrateThemeFromStorage
};

function ensureToastSurface() {
  let surf = document.querySelector('[data-arcane-toast-surface]');
  if (!surf) {
    surf = document.createElement('div');
    surf.setAttribute('data-arcane-toast-surface', '');
    surf.setAttribute('role', 'region');
    surf.setAttribute('aria-label', 'Notifications');
    surf.setAttribute('aria-live', 'polite');
    document.body.appendChild(surf);
  }
  return surf;
}

function showToast(payload) {
  const surf = ensureToastSurface();
  const el = document.createElement('div');
  el.className = 'arcane-toast';
  el.setAttribute('data-arcane-toast', '');
  el.setAttribute('data-arcane-toast-variant', payload.variant || 'info');
  el.setAttribute('role', 'status');
  if (payload.title) {
    const titleEl = document.createElement('div');
    titleEl.className = 'arcane-toast-title';
    titleEl.textContent = payload.title;
    el.appendChild(titleEl);
  }
  const msgEl = document.createElement('div');
  msgEl.className = 'arcane-toast-message';
  msgEl.textContent = payload.message || '';
  el.appendChild(msgEl);
  const closeBtn = document.createElement('button');
  closeBtn.type = 'button';
  closeBtn.className = 'arcane-toast-close';
  closeBtn.setAttribute('aria-label', 'Dismiss');
  closeBtn.textContent = '\u00d7';
  closeBtn.addEventListener('click', function() { dismissToast(el); });
  el.appendChild(closeBtn);
  surf.appendChild(el);
  nextFrame(function() { el.classList.add('arcane-toast-shown'); });
  const duration = payload.duration || 4000;
  if (duration > 0) {
    setTimeout(function() { dismissToast(el); }, duration);
  }
  fireEvent(document, 'arcane:toast', { variant: payload.variant, message: payload.message });
}

function dismissToast(el) {
  if (!el) return;
  el.classList.remove('arcane-toast-shown');
  el.classList.add('arcane-toast-leaving');
  setTimeout(function() {
    if (el.parentNode) el.parentNode.removeChild(el);
  }, 250);
}

ARCANE.toast = {
  show: showToast,
  dismiss: dismissToast
};

function copyText(text, feedback) {
  if (!navigator.clipboard) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand('copy');
      ta.remove();
      if (feedback) showToast({ message: feedback, variant: 'success' });
      fireEvent(document, 'arcane:copy', { text: text });
      return Promise.resolve(true);
    } catch (e) {
      ta.remove();
      return Promise.reject(e);
    }
  }
  return navigator.clipboard.writeText(text).then(function() {
    if (feedback) showToast({ message: feedback, variant: 'success' });
    fireEvent(document, 'arcane:copy', { text: text });
    return true;
  });
}

ARCANE.copy = copyText;

function navGo(href) {
  const evt = fireEvent(document, 'arcane:nav', { href: href });
  if (!evt.defaultPrevented) {
    window.location.href = href;
  }
}

function navExternal(href) {
  window.open(href, '_blank', 'noopener,noreferrer');
}

function navBack() {
  if (window.history.length > 1) window.history.back();
}

ARCANE.nav = {
  go: navGo,
  external: navExternal,
  back: navBack
};

function surfaceFocusTrigger(ctx) {
  const trigger = ctx.trigger;
  if (!trigger || !trigger.contains) return trigger;
  const clicked = ctx.event && findClosest(ctx.event.target, FOCUSABLE_SELECTOR);
  if (clicked && trigger.contains(clicked)) return clicked;
  const active = document.activeElement;
  if (active && trigger.contains(active) && active.matches(FOCUSABLE_SELECTOR)) return active;
  if (trigger.matches(FOCUSABLE_SELECTOR)) return trigger;
  return getFocusable(trigger)[0] || trigger;
}

function runAction(action, ctx) {
  if (!action) return;
  const verb = action.verb;
  const args = action.args;
  ctx = ctx || {};

  switch (verb) {
    case 'noop':
      return;
    case 'surface.open':
      openSurface(args[0], args[1], { trigger: surfaceFocusTrigger(ctx) });
      return;
    case 'surface.close':
      closeSurface(args[0], args[1]);
      return;
    case 'surface.toggle':
      toggleSurface(args[0], args[1], { trigger: surfaceFocusTrigger(ctx) });
      return;
    case 'surface.dismiss': {
      const surface = withinSurface(ctx.trigger);
      if (surface) {
        closeSurface(surfaceType(surface), surfaceId(surface));
      } else {
        dismissTopSurface();
      }
      return;
    }
    case 'value.set':
      setGroupValue(args[0], args[1] || '');
      return;
    case 'value.toggle':
      toggleGroupValue(args[0], args[1] || '');
      return;
    case 'value.clear':
      clearGroup(args[0]);
      return;
    case 'tab.activate':
      activateTab(args[0], args[1]);
      return;
    case 'panel.expand':
      expandPanel(args[0], args[1]);
      return;
    case 'panel.collapse':
      collapsePanel(args[0], args[1]);
      return;
    case 'panel.toggle':
      togglePanel(args[0], args[1]);
      return;
    case 'step.go':
      goToStep(args[0], args[1]);
      return;
    case 'page.go':
      goToPage(args[0], args[1]);
      return;
    case 'carousel.go':
      goToSlide(args[0], args[1]);
      return;
    case 'slider.set':
      setSliderValue(args[0], args[1] || '0', args[2]);
      return;
    case 'cycle.next':
      cycleStep(args[0], 1);
      return;
    case 'cycle.prev':
      cycleStep(args[0], -1);
      return;
    case 'calendar.prev':
      calendarPrev(args[0]);
      return;
    case 'calendar.next':
      calendarNext(args[0]);
      return;
    case 'calendar.today':
      calendarToday(args[0]);
      return;
    case 'calendar.select':
      calendarSelect(args[0], (ctx.trigger && ctx.trigger.getAttribute) ? ctx.trigger.getAttribute('data-arcane-value') : args[1]);
      return;
    case 'command.filter': {
      const inp = ctx.trigger;
      const q = inp && inp.value !== undefined ? inp.value : (args[1] || '');
      filterCommand(args[0], q);
      return;
    }
    case 'command.selectFirst':
      commandSelectFirst(args[0]);
      return;
    case 'form.submit':
      submitForm(args[0]);
      return;
    case 'form.reset':
      resetForm(args[0]);
      return;
    case 'form.validate':
      validateForm(args[0]);
      return;
    case 'field.set':
      setFieldValue(args[0], args[1], args[2] || '');
      return;
    case 'copy':
      copyText(args[0] || '', args[1]);
      return;
    case 'nav.go':
      navGo(args[0]);
      return;
    case 'nav.external':
      navExternal(args[0]);
      return;
    case 'nav.back':
      navBack();
      return;
    case 'theme.mode.set':
      setThemeMode(args[0]);
      return;
    case 'theme.mode.toggle':
      toggleThemeMode();
      return;
    case 'theme.stylesheet.set':
      setStylesheet(args[0]);
      return;
    case 'theme.palette.set': {
      const parts = (args[0] || '').split('/');
      setPalette(parts[0] || '', parts[1] || '');
      return;
    }
    case 'toast.show': {
      const payload = readJson(args[0], { message: args[0] || '' });
      showToast(payload);
      return;
    }
    case 'event.dispatch': {
      const detail = args[1] ? readJson(args[1], {}) : {};
      fireEvent(document, args[0], detail);
      return;
    }
    case 'script.run': {
      const fn = ARCANE.scripts[args[0]];
      if (typeof fn === 'function') {
        fn.apply(null, args.slice(1));
      }
      return;
    }
    default:
      if (verb && verb.indexOf(':') > 0) {
        fireEvent(document, verb, { args: args, ctx: ctx });
        return;
      }
      console.warn('[arcane] unknown action:', verb);
  }
}

function runActions(str, ctx) {
  if (!str) return;
  const list = parseActions(str);
  for (let i = 0; i < list.length; i++) {
    runAction(list[i], ctx);
  }
}

ARCANE.run = runActions;
ARCANE.runOne = runAction;

function findActionTarget(el, attr) {
  let cur = el;
  while (cur && cur !== document) {
    if (cur.nodeType === 1 && cur.hasAttribute(attr)) return cur;
    cur = cur.parentNode;
  }
  return null;
}

function shouldHandleClick(el) {
  if (!el) return false;
  if (el.hasAttribute('data-arcane-disabled')) return false;
  if (el.hasAttribute('disabled')) return false;
  return true;
}

function autoDismissAfterAction(trigger) {
  if (!trigger) return;
  if (trigger.getAttribute('data-arcane-keep-open') === 'true') return;
  const surf = withinSurface(trigger);
  if (!surf) return;
  const stype = surfaceType(surf);
  if (stype !== 'menu' && stype !== 'context-menu' && stype !== 'popover') return;
  if (surf.getAttribute('data-arcane-keep-open-on-action') === 'true') return;
  const sid = surfaceId(surf);
  setTimeout(function() { closeSurface(stype, sid); }, 0);
}

function onDocumentClick(e) {
  if (e.defaultPrevented) return;

  const trigger = findActionTarget(e.target, 'data-arcane-action');
  if (trigger && shouldHandleClick(trigger)) {
    if (trigger.tagName === 'A' && trigger.getAttribute('href')) {
      const href = trigger.getAttribute('href');
      if (href.indexOf('#') !== 0 && trigger.target !== '_blank') {
        e.preventDefault();
      }
    }
    if (trigger.tagName === 'BUTTON' || trigger.getAttribute('role') === 'button') {
      e.preventDefault();
    }
    runActions(trigger.getAttribute('data-arcane-action'), { trigger: trigger, event: e });
    autoDismissAfterAction(trigger);
    return;
  }

  const menuItem = findClosest(e.target, '[role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"]');
  if (menuItem && shouldHandleClick(menuItem) && !menuItem.hasAttribute('aria-haspopup')) {
    autoDismissAfterAction(menuItem);
    return;
  }

  if (ARCANE.stack.length > 0) {
    const top = ARCANE.stack[ARCANE.stack.length - 1];
    const surfaceEl = top.el;
    if (surfaceEl && !surfaceEl.contains(e.target)) {
      const scrimCloses = surfaceEl.getAttribute('data-arcane-scrim-closes') !== 'false';
      const dismissible = surfaceEl.getAttribute('data-arcane-dismissible') !== 'false';
      if (scrimCloses && dismissible) {
        if ((top.type === 'popover' || top.type === 'menu' || top.type === 'context-menu' || top.type === 'tooltip') ||
            e.target.classList.contains('arcane-overlay-scrim') ||
            e.target.hasAttribute('data-arcane-scrim') ||
            (top.type === 'dialog' || top.type === 'sheet' || top.type === 'drawer')) {
          if (top.type === 'dialog' || top.type === 'sheet' || top.type === 'drawer') {
            if (e.target.classList.contains('arcane-overlay-scrim') ||
                e.target.hasAttribute('data-arcane-scrim') ||
                e.target === surfaceEl.parentElement) {
              closeSurface(top.type, top.id);
            }
          } else {
            closeSurface(top.type, top.id);
          }
        }
      }
    }
  }
}

function onDocumentChange(e) {
  const target = findActionTarget(e.target, 'data-arcane-change');
  if (target) {
    runActions(target.getAttribute('data-arcane-change'), { trigger: target, event: e });
  }
  if (e.target.matches && e.target.matches('[data-arcane-form] [data-arcane-field]')) {
    const formEl = findClosest(e.target, '[data-arcane-form]');
    if (formEl) {
      const fid = formEl.getAttribute('data-arcane-form');
      const fname = e.target.getAttribute('data-arcane-field');
      if (fid && fname) validateField(fid, fname);
    }
  }
}

function onDocumentInput(e) {
  const target = findActionTarget(e.target, 'data-arcane-input');
  if (target) {
    runActions(target.getAttribute('data-arcane-input'), { trigger: target, event: e });
  }
}

function onDocumentSubmit(e) {
  const formEl = findActionTarget(e.target, 'data-arcane-form');
  if (formEl) {
    e.preventDefault();
    const fid = formEl.getAttribute('data-arcane-form');
    if (fid) submitForm(fid);
  }
  const target = findActionTarget(e.target, 'data-arcane-submit');
  if (target) {
    runActions(target.getAttribute('data-arcane-submit'), { trigger: target, event: e });
  }
}

function onDocumentDblClick(e) {
  const target = findActionTarget(e.target, 'data-arcane-dblclick');
  if (target) {
    runActions(target.getAttribute('data-arcane-dblclick'), { trigger: target, event: e });
  }
}

function onDocumentContextMenu(e) {
  const target = findActionTarget(e.target, 'data-arcane-contextmenu');
  if (target) {
    e.preventDefault();
    runActions(target.getAttribute('data-arcane-contextmenu'), { trigger: target, event: e });
  }
}

function onDocumentKeyDown(e) {
  if (e.defaultPrevented) return;
  if (handleListboxKey(e)) return;
  if (handleMenuKey(e)) return;
  if (e.key === 'Escape') {
    if (ARCANE.stack.length > 0) {
      const top = ARCANE.stack[ARCANE.stack.length - 1];
      const surfaceEl = top.el;
      if (surfaceEl && surfaceEl.getAttribute('data-arcane-escape-closes') !== 'false') {
        closeSurface(top.type, top.id);
        e.preventDefault();
        e.stopPropagation();
        return;
      }
    }
  }
  if (e.key === 'Tab' && ARCANE.stack.length > 0) {
    const top = ARCANE.stack[ARCANE.stack.length - 1];
    const surfaceEl = top.el;
    if (surfaceEl && (top.type === 'dialog' || top.type === 'sheet' ||
        surfaceEl.getAttribute('data-arcane-focus-trap') === 'true')) {
      trapFocus(surfaceEl, e);
    }
  }
  if (e.key === 'Enter' || e.key === ' ') {
    const target = e.target;
    if (target && target.matches && target.matches('[data-arcane-action]') &&
        target.tagName !== 'BUTTON' && target.tagName !== 'A' &&
        target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA' &&
        target.tagName !== 'SELECT') {
      e.preventDefault();
      runActions(target.getAttribute('data-arcane-action'), { trigger: target, event: e });
    }
  }
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' ||
      e.key === 'ArrowUp' || e.key === 'ArrowDown') {
    handleArrowKey(e);
  }

  const keyTarget = findActionTarget(e.target, 'data-arcane-keydown');
  if (keyTarget) {
    runActions(keyTarget.getAttribute('data-arcane-keydown'), { trigger: keyTarget, event: e });
  }
}

function handleListboxKey(e) {
  const target = e.target;
  if (!target || !target.closest || e.isComposing || e.ctrlKey || e.altKey || e.metaKey) return false;
  if (target.matches('input, textarea, [contenteditable="true"]')) return false;
  const trigger = target.closest('[aria-haspopup="listbox"][aria-controls]');
  const surface = trigger
    ? document.getElementById(trigger.getAttribute('aria-controls'))
    : target.closest('[role="listbox"][data-arcane-surface]');
  if (!surface || surface.getAttribute('role') !== 'listbox' ||
      !surface.hasAttribute('data-arcane-surface') ||
      (trigger && (trigger.disabled || trigger.getAttribute('aria-disabled') === 'true'))) return false;
  const type = surfaceType(surface);
  const id = surfaceId(surface);
  if (e.key === 'Tab' && surfaceIsOpen(surface)) {
    const anchor = document.querySelector('[aria-controls="' + cssEscape(id) + '"][aria-haspopup="listbox"]');
    if (anchor) anchor.focus({ preventScroll: true });
    surface._arcanePrevFocus = null;
    closeSurface(type, id, { immediate: true });
    return false;
  }
  if (e.key === 'Enter' || e.key === ' ') {
    if (trigger) {
      e.preventDefault();
      surface._arcaneTypeahead = '';
      trigger.click();
      return true;
    }
    const option = target.closest('[role="option"]');
    if (option && !option.disabled && option.getAttribute('aria-disabled') !== 'true') {
      e.preventDefault();
      option.click();
      return true;
    }
    return false;
  }
  const directional = ['ArrowDown', 'ArrowUp', 'Home', 'End'].indexOf(e.key) >= 0;
  const printable = e.key.length === 1 && e.key !== ' ';
  if (!directional && !printable) return false;
  if (directional) surface._arcaneTypeahead = '';
  const items = Array.prototype.slice.call(surface.querySelectorAll(
    '[role="option"]:not([disabled]):not([hidden]):not([aria-disabled="true"]):not([data-arcane-disabled="true"])'
  ));
  if (!items.length) return false;
  const current = items.indexOf(target.closest('[role="option"]'));
  let next = current;
  if (e.key === 'Home') next = 0;
  else if (e.key === 'End') next = items.length - 1;
  else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    if (trigger) {
      next = items.findIndex(function(item) { return item.getAttribute('aria-selected') === 'true'; });
      if (next < 0) next = e.key === 'ArrowUp' ? items.length - 1 : 0;
    } else {
      next = (current + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    }
  } else {
    const now = Date.now();
    const previous = surfaceIsOpen(surface) && now - (surface._arcaneTypeaheadAt || 0) < 700
      ? (surface._arcaneTypeahead || '') : '';
    const query = previous + e.key.toLocaleLowerCase();
    surface._arcaneTypeahead = query;
    surface._arcaneTypeaheadAt = now;
    const repeated = Array.from(query).every(function(character) { return character === query[0]; });
    const prefix = repeated ? query[0] : query;
    for (let step = repeated ? 1 : 0; step <= items.length; step++) {
      const index = (Math.max(current, 0) + step) % items.length;
      const label = (items[index].getAttribute('data-label') || items[index].textContent || '').trim().toLocaleLowerCase();
      if (label.startsWith(prefix)) { next = index; break; }
    }
  }
  if (next < 0) return false;
  e.preventDefault();
  const focusOption = function() {
    if (!surfaceIsOpen(surface)) return;
    items[next].focus({ preventScroll: true });
    items[next].scrollIntoView({ block: 'nearest' });
  };
  if (!surfaceIsOpen(surface)) {
    openSurface(type, id, { trigger: trigger });
    nextFrame(focusOption);
  } else {
    focusOption();
  }
  return true;
}

function handleMenuKey(e) {
  const target = e.target;
  if (!target || !target.closest || e.isComposing || e.ctrlKey || e.altKey || e.metaKey) return false;
  if (target.matches('input, textarea, [contenteditable="true"]')) return false;
  const trigger = target.closest('[aria-haspopup="menu"][aria-controls]');
  const surface = trigger ? document.getElementById(trigger.getAttribute('aria-controls'))
    : target.closest('[role="menu"][data-arcane-surface]');
  if (!surface || surfaceType(surface) !== 'menu') return false;
  const item = target.closest('[role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"]');
  const active = trigger || item;
  if (active && !shouldHandleClick(active)) return false;

  if ((e.key === 'Enter' || e.key === ' ') && active) {
    e.preventDefault();
    active.click();
    return true;
  }

  const bar = surface.closest('[role="menubar"]');
  if (bar && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
    const triggers = Array.from(bar.querySelectorAll('[data-arcane-menubar-trigger]'));
    const current = triggers.findIndex(function(button) {
      return button === trigger || button.getAttribute('aria-controls') === surface.id;
    });
    if (current >= 0 && triggers.length) {
      e.preventDefault();
      const next = (current + (e.key === 'ArrowRight' ? 1 : triggers.length - 1)) % triggers.length;
      const open = surfaceIsOpen(surface);
      triggers[next].focus();
      if (open) triggers[next].click();
      return true;
    }
  }

  if (e.key === 'Tab' && surfaceIsOpen(surface)) {
    closeSurface('menu', surfaceId(surface), { immediate: true });
    return false;
  }
  const items = Array.from(surface.querySelectorAll(
    '[role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"]'
  )).filter(function(candidate) {
    return shouldHandleClick(candidate) && candidate.getAttribute('aria-disabled') !== 'true' &&
      candidate.closest('[role="menu"]') === surface;
  });
  if (!items.length) return false;
  const current = items.indexOf(item);
  let next = -1;
  if (e.key === 'ArrowDown') next = (current + 1) % items.length;
  else if (e.key === 'ArrowUp') next = current < 0 ? items.length - 1 : (current + items.length - 1) % items.length;
  else if (e.key === 'Home') next = 0;
  else if (e.key === 'End') next = items.length - 1;
  else if (e.key.length === 1 && /\S/.test(e.key)) {
    const now = Date.now();
    const previous = now - (surface._arcaneMenuSearchTime || 0) < 700 ? surface._arcaneMenuSearch || '' : '';
    const query = previous + e.key.toLocaleLowerCase();
    surface._arcaneMenuSearch = query;
    surface._arcaneMenuSearchTime = now;
    const prefix = Array.from(query).every(function(letter) { return letter === query[0]; }) ? query[0] : query;
    for (let step = 1; step <= items.length; step++) {
      const index = (Math.max(current, 0) + step) % items.length;
      const label = (items[index].getAttribute('data-label') || items[index].textContent || '').trim().toLocaleLowerCase();
      if (label.startsWith(prefix)) { next = index; break; }
    }
  }
  if (next < 0) return false;
  e.preventDefault();
  const focusItem = function() {
    if (!surfaceIsOpen(surface)) return;
    items[next].focus({ preventScroll: true });
    items[next].scrollIntoView({ block: 'nearest' });
  };
  if (!surfaceIsOpen(surface)) {
    openSurface('menu', surfaceId(surface), {
      trigger: surfaceFocusTrigger({ trigger: trigger, event: e })
    });
    nextFrame(focusItem);
  } else focusItem();
  return true;
}

function handleArrowKey(e) {
  const tab = findClosest(e.target, '[role="tab"][data-arcane-tab]');
  if (tab) {
    const groupId = tab.getAttribute('data-arcane-tabs-group');
    const orientation = (findClosest(tab, '[data-arcane-tabs-orientation]') || {}).getAttribute &&
      findClosest(tab, '[data-arcane-tabs-orientation]').getAttribute('data-arcane-tabs-orientation') || 'horizontal';
    const isHorizontal = orientation === 'horizontal';
    const forward = isHorizontal ? e.key === 'ArrowRight' : e.key === 'ArrowDown';
    const backward = isHorizontal ? e.key === 'ArrowLeft' : e.key === 'ArrowUp';
    if (forward || backward) {
      e.preventDefault();
      const triggers = Array.prototype.slice.call(document.querySelectorAll(
        '[data-arcane-tabs-group="' + cssEscape(groupId) + '"][data-arcane-tab]'
      ));
      const idx = triggers.indexOf(tab);
      let next = forward ? idx + 1 : idx - 1;
      if (next < 0) next = triggers.length - 1;
      if (next >= triggers.length) next = 0;
      const nextTab = triggers[next];
      if (nextTab) {
        activateTab(groupId, nextTab.getAttribute('data-arcane-tab'));
        nextTab.focus();
      }
      return;
    }
  }

  const opt = findClosest(e.target, '[data-arcane-group][data-arcane-value]');
  if (opt) {
    const surf = withinSurface(opt);
    if (surf && (surfaceType(surf) === 'menu' || surfaceType(surf) === 'context-menu' ||
        surfaceType(surf) === 'popover' || surfaceType(surf) === 'command')) {
      const forward = e.key === 'ArrowDown';
      const backward = e.key === 'ArrowUp';
      if (forward || backward) {
        e.preventDefault();
        const groupId = opt.getAttribute('data-arcane-group');
        const items = Array.prototype.slice.call(surf.querySelectorAll(
          '[data-arcane-group="' + cssEscape(groupId) + '"][data-arcane-value]:not([data-arcane-disabled="true"])'
        ));
        const idx = items.indexOf(opt);
        let next = forward ? idx + 1 : idx - 1;
        if (next < 0) next = items.length - 1;
        if (next >= items.length) next = 0;
        if (items[next]) items[next].focus();
        return;
      }
    }
  }
}

function onPointerOver(e) {
  const trigger = findActionTarget(e.target, 'data-arcane-mouseenter');
  if (trigger) {
    runActions(trigger.getAttribute('data-arcane-mouseenter'), { trigger: trigger, event: e });
  }
  const hoverTrigger = findActionTarget(e.target, 'data-arcane-hover-target');
  if (hoverTrigger) {
    const targetId = hoverTrigger.getAttribute('data-arcane-hover-target');
    const surfaceType = hoverTrigger.getAttribute('data-arcane-hover-surface') || 'tooltip';
    const delay = parseInt(hoverTrigger.getAttribute('data-arcane-hover-delay') || '120', 10);
    if (hoverTrigger._arcaneHoverTimer) clearTimeout(hoverTrigger._arcaneHoverTimer);
    hoverTrigger._arcaneHoverTimer = setTimeout(function() {
      openSurface(surfaceType, targetId, { trigger: hoverTrigger });
    }, delay);
  }
}

function onPointerOut(e) {
  const trigger = findActionTarget(e.target, 'data-arcane-mouseleave');
  if (trigger && !trigger.contains(e.relatedTarget)) {
    runActions(trigger.getAttribute('data-arcane-mouseleave'), { trigger: trigger, event: e });
  }
  const hoverTrigger = findActionTarget(e.target, 'data-arcane-hover-target');
  if (hoverTrigger && !hoverTrigger.contains(e.relatedTarget)) {
    if (hoverTrigger._arcaneHoverTimer) clearTimeout(hoverTrigger._arcaneHoverTimer);
    const targetId = hoverTrigger.getAttribute('data-arcane-hover-target');
    const surfaceType = hoverTrigger.getAttribute('data-arcane-hover-surface') || 'tooltip';
    const delay = parseInt(hoverTrigger.getAttribute('data-arcane-hover-close-delay') || '60', 10);
    hoverTrigger._arcaneHoverTimer = setTimeout(function() {
      const surf = querySurface(surfaceType, targetId);
      if (surf && !surf.matches(':hover')) {
        closeSurface(surfaceType, targetId);
      }
    }, delay);
  }
}

function onFocusIn(e) {
  const trigger = findActionTarget(e.target, 'data-arcane-focus');
  if (trigger) {
    runActions(trigger.getAttribute('data-arcane-focus'), { trigger: trigger, event: e });
  }
}

function onFocusOut(e) {
  const trigger = findActionTarget(e.target, 'data-arcane-blur');
  if (trigger) {
    runActions(trigger.getAttribute('data-arcane-blur'), { trigger: trigger, event: e });
  }
}

ARCANE.delegation = {
  click: onDocumentClick,
  change: onDocumentChange,
  input: onDocumentInput,
  submit: onDocumentSubmit,
  dblclick: onDocumentDblClick,
  contextmenu: onDocumentContextMenu,
  keydown: onDocumentKeyDown,
  pointerover: onPointerOver,
  pointerout: onPointerOut,
  focusin: onFocusIn,
  focusout: onFocusOut
};

function bindGroupItemClicks() {
  document.addEventListener('click', function(e) {
    const item = findClosest(e.target, '[data-arcane-group][data-arcane-value]');
    if (!item) return;
    if (item.hasAttribute('data-arcane-action')) return;
    if (item.tagName === 'INPUT') return;
    const groupId = item.getAttribute('data-arcane-group');
    const root = groupRoot(groupId);
    if (!root || root === item) return;
    const value = item.getAttribute('data-arcane-value');
    const mode = root.getAttribute('data-arcane-group-mode') || 'single';
    if (item.getAttribute('data-arcane-disabled') === 'true') return;
    if (mode === 'multi') toggleGroupValue(groupId, value);
    else setGroupValue(groupId, value);
    if (root.getAttribute('data-arcane-group-close-surface') === 'true') {
      const surf = withinSurface(item);
      if (surf) closeSurface(surfaceType(surf), surfaceId(surf));
    }
  }, true);
}

function bindNativeInputs() {
  document.addEventListener('change', function(e) {
    const inp = e.target;
    if (!inp || !inp.matches) return;
    if (inp.matches('input[data-arcane-group][data-arcane-value]')) {
      const groupId = inp.getAttribute('data-arcane-group');
      const value = inp.getAttribute('data-arcane-value');
      const mode = groupMode(groupId);
      if (mode === 'multi') {
        if (inp.checked) {
          const cur = groupValues(groupId);
          if (cur.indexOf(value) < 0) setGroupRawValues(groupId, cur.concat([value]));
        } else {
          const cur = groupValues(groupId);
          const idx = cur.indexOf(value);
          if (idx >= 0) {
            const next = cur.slice();
            next.splice(idx, 1);
            setGroupRawValues(groupId, next);
          }
        }
      } else if (inp.checked) {
        setGroupValue(groupId, value);
      }
    }
    if (inp.matches('select[data-arcane-group]')) {
      const groupId = inp.getAttribute('data-arcane-group');
      const mode = groupMode(groupId);
      if (mode === 'multi') {
        const values = [];
        for (let i = 0; i < inp.options.length; i++) {
          if (inp.options[i].selected) values.push(inp.options[i].value);
        }
        setGroupRawValues(groupId, values);
      } else {
        setGroupValue(groupId, inp.value);
      }
    }
  }, true);
}

function setSelectText(element, value) {
  if (element.firstChild && element.childNodes.length === 1 && element.firstChild.nodeType === 3) {
    element.firstChild.nodeValue = value;
  } else {
    element.textContent = value;
  }
}

function syncStyledSelect(surface) {
  const triggerId = surface.getAttribute('data-arcane-select-trigger');
  if (!triggerId) return;
  const backing = document.querySelector('select[data-arcane-select-backing="' + cssEscape(triggerId) + '"]');
  const trigger = document.getElementById(triggerId);
  if (!backing || !trigger) return;
  const value = surface.getAttribute('data-arcane-group-value') || '';
  backing.value = value;
  const label = trigger.querySelector('[data-arcane-select-label]');
  const selected = backing.selectedOptions[0];
  if (label) setSelectText(label, selected ? selected.label : surface.getAttribute('data-arcane-select-placeholder'));
  if (backing.validity.valid && backing.getAttribute('data-arcane-select-has-error') !== 'true') {
    trigger.removeAttribute('aria-invalid');
    trigger.removeAttribute('aria-describedby');
    const error = document.getElementById(triggerId + '-error');
    if (error) { error.hidden = true; setSelectText(error, ''); }
  }
}

function bindStyledSelects() {
  document.addEventListener('arcane:change', function(e) {
    const surface = e.target;
    if (surface && surface.hasAttribute && surface.hasAttribute('data-arcane-select-trigger')) {
      syncStyledSelect(surface);
    }
  }, true);
  document.addEventListener('invalid', function(e) {
    const backing = e.target;
    if (!backing || !backing.matches('select[data-arcane-select-backing]')) return;
    e.preventDefault();
    const triggerId = backing.getAttribute('data-arcane-select-backing');
    const trigger = document.getElementById(triggerId);
    if (!trigger) return;
    trigger.setAttribute('aria-invalid', 'true');
    const error = document.getElementById(triggerId + '-error');
    if (error) {
      if (backing.getAttribute('data-arcane-select-has-error') !== 'true') setSelectText(error, backing.validationMessage);
      error.hidden = false;
      trigger.setAttribute('aria-describedby', error.id);
    }
    trigger.focus();
  }, true);
  document.addEventListener('reset', function(e) {
    const form = e.target;
    setTimeout(function() {
      if (e.defaultPrevented) return;
      const controls = document.querySelectorAll('select[data-arcane-select-backing]');
      for (let i = 0; i < controls.length; i++) {
        const backing = controls[i];
        if (backing.form !== form) continue;
        const groupId = backing.getAttribute('data-arcane-group');
        const surface = groupRoot(groupId);
        if (!surface) continue;
        const value = backing.getAttribute('data-arcane-select-default') || '';
        surface.setAttribute('data-arcane-group-value', value);
        syncGroupItems(groupId, value ? [value] : []);
        syncStyledSelect(surface);
        closeSurface(surfaceType(surface), surfaceId(surface), { immediate: true });
        fireEvent(surface, 'arcane:reset', {});
      }
    }, 0);
  }, true);
}

function bindOutsideTabs() {
  document.addEventListener('click', function(e) {
    const tab = findClosest(e.target, '[data-arcane-tab][data-arcane-tabs-group]');
    if (!tab) return;
    if (tab.hasAttribute('data-arcane-action')) return;
    if (tab.getAttribute('data-arcane-disabled') === 'true') return;
    const groupId = tab.getAttribute('data-arcane-tabs-group');
    const tabId = tab.getAttribute('data-arcane-tab');
    activateTab(groupId, tabId);
  }, true);
}

function bindAccordions() {
  document.addEventListener('click', function(e) {
    const trigger = findClosest(e.target, '[data-arcane-panel-group][data-arcane-panel]');
    if (!trigger) return;
    if (trigger.hasAttribute('data-arcane-action')) return;
    if (trigger.getAttribute('data-arcane-disabled') === 'true') return;
    const groupId = trigger.getAttribute('data-arcane-panel-group');
    const panelId = trigger.getAttribute('data-arcane-panel');
    togglePanel(groupId, panelId);
  }, true);
}

function bindCarousels() {
  const carousels = document.querySelectorAll('[data-arcane-carousel][data-arcane-carousel-autoplay]');
  for (let i = 0; i < carousels.length; i++) {
    startCarouselAutoplay(carousels[i]);
  }
}

function bindContextMenus() {
  document.addEventListener('contextmenu', function(e) {
    const trigger = findActionTarget(e.target, 'data-arcane-context-trigger');
    if (!trigger) return;
    e.preventDefault();
    const targetId = trigger.getAttribute('data-arcane-context-trigger');
    const surface = querySurface('context-menu', targetId);
    if (!surface) return;
    surface._arcaneContextX = e.clientX;
    surface._arcaneContextY = e.clientY;
    surface.style.position = 'fixed';
    surface.style.top = e.clientY + 'px';
    surface.style.left = e.clientX + 'px';
    openSurface('context-menu', targetId, { trigger: trigger, skipAnchorListener: true });
    surface.style.left = Math.max(4, Math.min(e.clientX, window.innerWidth - surface.offsetWidth - 4)) + 'px';
    surface.style.top = Math.max(4, Math.min(e.clientY, window.innerHeight - surface.offsetHeight - 4)) + 'px';
  }, true);
}

function hydrateInitialState() {
  hydrateThemeFromStorage();
  const surfaces = document.querySelectorAll('[data-arcane-surface][data-arcane-state="closed"]');
  for (let i = 0; i < surfaces.length; i++) {
    surfaces[i].setAttribute('hidden', '');
    surfaces[i].setAttribute('aria-hidden', 'true');
  }
  syncSurfaceStack();
  observeSurfaces();
}

function arcaneInit() {
  if (ARCANE._initialized) return;
  ARCANE._initialized = true;

  document.addEventListener('click', onDocumentClick, false);
  document.addEventListener('change', onDocumentChange, false);
  document.addEventListener('input', onDocumentInput, false);
  document.addEventListener('submit', onDocumentSubmit, false);
  document.addEventListener('dblclick', onDocumentDblClick, false);
  document.addEventListener('contextmenu', onDocumentContextMenu, false);
  document.addEventListener('keydown', onDocumentKeyDown, false);
  document.addEventListener('pointerover', onPointerOver, false);
  document.addEventListener('pointerout', onPointerOut, false);
  document.addEventListener('focusin', onFocusIn, false);
  document.addEventListener('focusout', onFocusOut, false);

  bindGroupItemClicks();
  bindNativeInputs();
  bindStyledSelects();
  bindOutsideTabs();
  bindAccordions();
  bindContextMenus();
  bindCarousels();
  bindSliders();
  bindCommandKeyboard();
  bindCalendars();
  bindTimePickers();
  bindOtpInputs();
  hydrateInitialState();

  fireEvent(document, 'arcane:ready', {});
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', arcaneInit);
} else {
  arcaneInit();
}

ARCANE.init = arcaneInit;

})();

(function() {
  'use strict';

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindAllComponents);
  } else {
    setTimeout(bindAllComponents, 100);
  }

  function bindAllComponents() {
    bindSliders();
    bindColorInputs();
    bindCheckboxes();
    bindToggleSwitches();
    bindRadioButtons();
    bindNumberInputs();
    bindFileUploads();
    bindMutableText();
    bindComboboxes();
    bindCalendars();
    bindDatePickers();
    bindTimePickers();
    bindFormattedInputs();
    bindToggleButtonGroups();
    bindCycleButtons();
    bindToggleButtons();
    bindButtons();
    bindCopyButtons();
    bindExpandersAccordions();
    bindSelectors();
    bindTreeViews();
    bindPagination();
    bindBackToTop();
    bindContextMenus();
    bindMenubars();
    bindResizables();
    bindCommandPalettes();
    bindSteps();
    bindTimelines();
    bindDotIndicators();
    bindTrackers();
    bindDocsToc();
    bindPopovers();
    bindTooltips();
    bindDialogs();
    bindDrawers();
    bindMobileMenus();
    bindSheets();
    bindEmailDialogs();
    bindTimeDialogs();
    bindItemPickers();
    bindChatScreens();
    bindMapDebugMode();
    bindMapPinTooltips();
    bindLocationListHover();
    bindRainbowTheme();
    bindCarousels();
  }

    // ===== SLIDERS =====
  function bindSliders() {
    // Regular sliders
    document.querySelectorAll('.arcane-slider').forEach(function(slider) {
      if (slider.dataset.arcaneInteractive) return;
      slider.dataset.arcaneInteractive = 'true';

      var input = slider.querySelector('.arcane-slider-input');
      var track = slider.querySelector('.arcane-slider-track-fill');
      var thumb = slider.querySelector('.arcane-slider-thumb');
      var valueDisplay = slider.querySelector('.arcane-slider-value');

      if (!input) return;

      input.addEventListener('input', function() {
        var min = parseFloat(input.min) || 0;
        var max = parseFloat(input.max) || 100;
        var value = parseFloat(input.value);
        var percent = ((value - min) / (max - min)) * 100;

        // Update track fill
        if (track) track.style.width = percent + '%';

        // Update thumb position
        if (thumb) {
          var thumbSize = parseInt(thumb.style.width) || 18;
          thumb.style.left = 'calc(' + percent + '% - ' + (thumbSize / 2) + 'px)';
        }

        // Update value display
        if (valueDisplay) {
          var text = valueDisplay.textContent;
          var prefix = text.match(/^[^\d-]*/)?.[0] || '';
          var suffix = text.match(/[^\d]*$/)?.[0] || '';
          valueDisplay.textContent = prefix + Math.round(value) + suffix;
        }
      });
    });

    // Range sliders
    document.querySelectorAll('.arcane-range-slider').forEach(function(slider) {
      if (slider.dataset.arcaneInteractive) return;
      slider.dataset.arcaneInteractive = 'true';

      var minInput = slider.querySelector('.arcane-range-slider-input-min');
      var maxInput = slider.querySelector('.arcane-range-slider-input-max');
      var track = slider.querySelector('.arcane-range-slider-track-fill');
      var minThumb = slider.querySelector('.arcane-range-slider-thumb-min');
      var maxThumb = slider.querySelector('.arcane-range-slider-thumb-max');
      var valuesDisplay = slider.querySelector('.arcane-range-slider-values');

      if (!minInput || !maxInput) return;

      function updateRange() {
        var min = parseFloat(minInput.min) || 0;
        var max = parseFloat(minInput.max) || 100;
        var minVal = parseFloat(minInput.value);
        var maxVal = parseFloat(maxInput.value);
        var minPercent = ((minVal - min) / (max - min)) * 100;
        var maxPercent = ((maxVal - min) / (max - min)) * 100;

        // Update track
        if (track) {
          track.style.left = minPercent + '%';
          track.style.width = (maxPercent - minPercent) + '%';
        }

        // Update thumbs
        var thumbSize = minThumb ? parseInt(minThumb.style.width) || 18 : 18;
        if (minThumb) minThumb.style.left = 'calc(' + minPercent + '% - ' + (thumbSize / 2) + 'px)';
        if (maxThumb) maxThumb.style.left = 'calc(' + maxPercent + '% - ' + (thumbSize / 2) + 'px)';

        // Update display
        if (valuesDisplay) {
          valuesDisplay.textContent = Math.round(minVal) + ' – ' + Math.round(maxVal);
        }
      }

      minInput.addEventListener('input', function() {
        var maxVal = parseFloat(maxInput.value);
        if (parseFloat(minInput.value) >= maxVal) {
          minInput.value = maxVal - 1;
        }
        updateRange();
      });

      maxInput.addEventListener('input', function() {
        var minVal = parseFloat(minInput.value);
        if (parseFloat(maxInput.value) <= minVal) {
          maxInput.value = minVal + 1;
        }
        updateRange();
      });
    });

    // Legacy slider support (fallback for old class names)
    document.querySelectorAll('input[type="range"]:not(.arcane-slider-input):not(.arcane-range-slider-input)').forEach(function(input) {
      if (input.dataset.arcaneInteractive) return;
      input.dataset.arcaneInteractive = 'true';

      var container = input.closest('div');
      if (!container) return;

      input.addEventListener('input', function() {
        var min = parseFloat(input.min) || 0;
        var max = parseFloat(input.max) || 100;
        var value = parseFloat(input.value);
        var percent = ((value - min) / (max - min)) * 100;

        // Update any track fill
        container.querySelectorAll('[style*="position: absolute"]').forEach(function(el) {
          if (el.style.width && el.style.width.includes('%') && el !== input) {
            el.style.width = percent + '%';
          }
        });

        // Update any thumb
        container.querySelectorAll('[data-arcane-slider-thumb]').forEach(function(thumb) {
          if (thumb.style.position === 'absolute' && thumb.style.width) {
            var thumbSize = parseInt(thumb.style.width) || 18;
            thumb.style.left = 'calc(' + percent + '% - ' + (thumbSize / 2) + 'px)';
          }
        });
      });
    });
  }

    function bindColorInputs() {
    document.querySelectorAll('.arcane-color-input').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      if (container.dataset.disabled === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var nativeInput = container.querySelector('.arcane-color-input-native');
      var hexInput = container.querySelector('.arcane-color-input-hex');
      var swatch = container.querySelector('.arcane-color-input-swatch');
      var presets = container.querySelectorAll('.arcane-color-input-preset');

      function updateColor(color) {
        color = color.toUpperCase();
        container.dataset.value = color;

        if (swatch) {
          swatch.style.background = color;
        }

        if (nativeInput) {
          nativeInput.value = color;
        }

        if (hexInput) {
          hexInput.value = color;
        }

        presets.forEach(function(preset) {
          if (preset.dataset.color === color) {
            preset.style.borderColor = 'var(--arcane-accent)';
          } else {
            preset.style.borderColor = 'var(--arcane-border)';
          }
        });
      }

      if (nativeInput) {
        nativeInput.addEventListener('input', function() {
          updateColor(nativeInput.value);
        });
      }

      if (hexInput) {
        hexInput.addEventListener('input', function() {
          var val = hexInput.value.trim().toUpperCase();
          if (!val.startsWith('#')) val = '#' + val;
          if (/^#[0-9A-F]{6}$/.test(val)) {
            updateColor(val);
          }
        });
      }

      presets.forEach(function(preset) {
        preset.addEventListener('click', function(e) {
          e.preventDefault();
          var color = preset.dataset.color;
          if (color) {
            updateColor(color);
          }
        });
      });
    });
  }

  function bindNumberInputs() {
    document.querySelectorAll('.arcane-number-input').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      if (container.dataset.disabled === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var decrementBtn = container.querySelector('.arcane-number-input-decrement');
      var incrementBtn = container.querySelector('.arcane-number-input-increment');
      var display = container.querySelector('.arcane-number-input-display');

      function getValue() {
        return parseFloat(container.dataset.value) || 0;
      }

      function getMin() {
        return parseFloat(container.dataset.min) || 0;
      }

      function getMax() {
        return parseFloat(container.dataset.max) || 100;
      }

      function getStep() {
        return parseFloat(container.dataset.step) || 1;
      }

      function getDecimals() {
        return parseInt(container.dataset.decimals) || 0;
      }

      function updateValue(newValue) {
        var min = getMin();
        var max = getMax();
        var decimals = getDecimals();

        newValue = Math.max(min, Math.min(max, newValue));
        container.dataset.value = newValue.toString();

        if (display) {
          display.textContent = decimals > 0 ? newValue.toFixed(decimals) : Math.round(newValue).toString();
        }

        if (decrementBtn) {
          decrementBtn.disabled = newValue <= min;
          decrementBtn.style.cursor = newValue <= min ? 'var(--arcane-step-cursor-disabled, not-allowed)' : 'var(--arcane-step-cursor, pointer)';
          decrementBtn.style.color = newValue <= min ? 'var(--arcane-muted)' : 'var(--arcane-on-surface)';
        }
        if (incrementBtn) {
          incrementBtn.disabled = newValue >= max;
          incrementBtn.style.cursor = newValue >= max ? 'var(--arcane-step-cursor-disabled, not-allowed)' : 'var(--arcane-step-cursor, pointer)';
          incrementBtn.style.color = newValue >= max ? 'var(--arcane-muted)' : 'var(--arcane-on-surface)';
        }
      }

      if (decrementBtn) {
        decrementBtn.addEventListener('click', function(e) {
          e.preventDefault();
          if (!decrementBtn.disabled) {
            updateValue(getValue() - getStep());
          }
        });
      }

      if (incrementBtn) {
        incrementBtn.addEventListener('click', function(e) {
          e.preventDefault();
          if (!incrementBtn.disabled) {
            updateValue(getValue() + getStep());
          }
        });
      }
    });
  }

  function bindCheckboxes() {
    document.querySelectorAll('.arcane-checkbox').forEach(function(checkbox) {
      if (checkbox.dataset.arcaneInteractive === 'true') return;
      if (checkbox.dataset.disabled === 'true') return;
      checkbox.dataset.arcaneInteractive = 'true';

      var box = checkbox.querySelector('.arcane-checkbox-box');
      if (!box) return;

      checkbox.addEventListener('click', function(e) {
        e.preventDefault();
        var isChecked = checkbox.dataset.checked === 'true';
        var newState = !isChecked;
        checkbox.dataset.checked = newState.toString();

        if (newState) {
          box.style.background = 'var(--arcane-success)';
          box.style.borderColor = 'var(--arcane-success)';
          box.innerHTML = '<span style="color: var(--arcane-success-foreground); font-size: 12px; font-weight: bold;">✓</span>';
        } else {
          box.style.background = 'transparent';
          box.style.borderColor = 'var(--arcane-border)';
          box.innerHTML = '';
        }
      });
    });
  }

  function bindToggleSwitches() {
    document.querySelectorAll('.arcane-toggle-switch').forEach(function(toggle) {
      if (toggle.dataset.arcaneInteractive === 'true') return;
      if (toggle.dataset.disabled === 'true') return;
      toggle.dataset.arcaneInteractive = 'true';

      var track = toggle.querySelector('.arcane-toggle-switch-track');
      var thumb = toggle.querySelector('.arcane-toggle-switch-thumb');
      if (!track || !thumb) return;

      toggle.addEventListener('click', function(e) {
        e.preventDefault();
        var isOn = toggle.dataset.checked === 'true';
        var newState = !isOn;
        toggle.dataset.checked = newState.toString();

        if (newState) {
          track.style.background = 'var(--arcane-success)';
          thumb.style.transform = 'translateX(20px)';
        } else {
          track.style.background = 'var(--arcane-surface-variant)';
          thumb.style.transform = 'translateX(0)';
        }
      });
    });
  }

  function bindRadioButtons() {
    document.querySelectorAll('.arcane-radio-group').forEach(function(group) {
      if (group.hasAttribute('data-arcane-group')) return;
      if (group.dataset.arcaneInteractive === 'true') return;
      group.dataset.arcaneInteractive = 'true';

      var radios = group.querySelectorAll('.arcane-radio-item, .arcane-radio-card, .arcane-radio-button, .arcane-radio');

      radios.forEach(function(radio) {
        var input = radio.querySelector('input[type="radio"]');
        if (!input || input.disabled) return;

        radio.addEventListener('click', function(e) {
          if (e.target === input) return;
          e.preventDefault();

          input.checked = true;
          input.dispatchEvent(new Event('change', { bubbles: true }));

          radios.forEach(function(r) {
            var rInput = r.querySelector('input[type="radio"]');
            var isChecked = rInput && rInput.checked;

            var circle = r.querySelector('.arcane-radio-circle');
            if (circle) {
              circle.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';
              var dot = circle.querySelector('div');
              if (isChecked && !dot) {
                dot = document.createElement('div');
                dot.style.cssText = 'width: 10px; height: 10px; border-radius: 50%; background: var(--arcane-accent);';
                circle.appendChild(dot);
              } else if (!isChecked && dot) {
                dot.remove();
              }
            }

            if (r.classList.contains('arcane-radio-card')) {
              r.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';
              r.style.borderWidth = isChecked ? '2px' : '1px';
              r.style.background = isChecked ? 'var(--arcane-accent-container)' : 'var(--arcane-surface)';
            }

            if (r.classList.contains('arcane-radio-button')) {
              r.style.background = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-surface)';
              r.style.color = isChecked ? 'var(--arcane-accent-foreground)' : 'var(--arcane-on-surface)';
              r.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';
            }

            var indicator = r.querySelector('.arcane-radio-indicator');
            if (indicator) {
              indicator.style.borderColor = isChecked ? 'var(--arcane-accent)' : 'var(--arcane-border)';
              if (isChecked) {
                indicator.innerHTML = '<div data-arcane-intrinsic-shape="radio-dot" style="width: 10px; height: 10px; border-radius: 50%; background: var(--arcane-accent);"></div>';
              } else {
                indicator.innerHTML = '';
              }
            }
          });
        });
      });
    });
  }

  function bindMutableText() {
    document.querySelectorAll('.arcane-mutable-text').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var display = container.querySelector('.arcane-mutable-display');
      var editContainer = container.querySelector('.arcane-mutable-edit');
      var input = container.querySelector('.arcane-mutable-input');
      var saveBtn = container.querySelector('.arcane-mutable-save');
      var cancelBtn = container.querySelector('.arcane-mutable-cancel');
      var charCounter = container.querySelector('.arcane-mutable-counter');

      if (!display || !editContainer || !input) return;

      var trigger = container.dataset.trigger || 'click';
      var originalValue = '';

      function showEdit() {
        originalValue = display.textContent.trim();
        input.value = originalValue;
        display.style.display = 'none';
        editContainer.style.display = 'flex';
        input.focus();
        input.select();
        updateCharCounter();
      }

      function hideEdit(save) {
        if (save) {
          var newValue = input.value.trim();
          var minLength = parseInt(container.dataset.minLength) || 0;
          var maxLength = parseInt(container.dataset.maxLength) || Infinity;

          if (newValue.length < minLength || newValue.length > maxLength) {
            input.style.borderColor = 'var(--arcane-error)';
            return;
          }

          display.textContent = newValue || originalValue;
          container.dataset.value = newValue;
        }
        display.style.display = '';
        editContainer.style.display = 'none';
        input.style.borderColor = '';
      }

      function updateCharCounter() {
        if (!charCounter) return;
        var maxLength = parseInt(container.dataset.maxLength) || 0;
        if (maxLength > 0) {
          charCounter.textContent = input.value.length + '/' + maxLength;
          charCounter.style.color = input.value.length > maxLength ? 'var(--arcane-error)' : 'var(--arcane-muted)';
        }
      }

      if (trigger === 'click' || trigger === 'doubleClick') {
        var eventType = trigger === 'doubleClick' ? 'dblclick' : 'click';
        display.addEventListener(eventType, function(e) {
          e.preventDefault();
          showEdit();
        });
      } else if (trigger === 'hover') {
        var hoverTimer = null;
        display.addEventListener('mouseenter', function() {
          hoverTimer = setTimeout(showEdit, 500);
        });
        display.addEventListener('mouseleave', function() {
          if (hoverTimer) clearTimeout(hoverTimer);
        });
      }

      var editIcon = container.querySelector('.arcane-mutable-edit-icon');
      if (editIcon) {
        editIcon.addEventListener('click', function(e) {
          e.stopPropagation();
          showEdit();
        });
      }

      input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          hideEdit(true);
        } else if (e.key === 'Escape') {
          e.preventDefault();
          hideEdit(false);
        }
      });

      input.addEventListener('input', updateCharCounter);

      if (saveBtn) {
        saveBtn.addEventListener('click', function(e) {
          e.preventDefault();
          hideEdit(true);
        });
      }

      if (cancelBtn) {
        cancelBtn.addEventListener('click', function(e) {
          e.preventDefault();
          hideEdit(false);
        });
      }

      document.addEventListener('click', function(e) {
        if (editContainer.style.display !== 'none' && !container.contains(e.target)) {
          hideEdit(false);
        }
      });
    });
  }

  function bindFileUploads() {
    document.querySelectorAll('.arcane-file-upload').forEach(function(dropzone) {
      if (dropzone.dataset.arcaneInteractive === 'true') return;
      dropzone.dataset.arcaneInteractive = 'true';

      var fileInput = dropzone.querySelector('.arcane-file-input');
      if (!fileInput) return;

      dropzone.addEventListener('click', function(e) {
        if (e.target !== fileInput) {
          fileInput.click();
        }
      });

      fileInput.addEventListener('change', function() {
        var files = fileInput.files;
        if (!files || files.length === 0) return;

        var fileList = dropzone.querySelector('.arcane-file-list');
        if (fileList) {
          fileList.innerHTML = '';
          for (var i = 0; i < files.length; i++) {
            var file = files[i];
            var size = file.size < 1024 ? file.size + ' B' :
                       file.size < 1024 * 1024 ? (file.size / 1024).toFixed(1) + ' KB' :
                       (file.size / (1024 * 1024)).toFixed(1) + ' MB';

            var item = document.createElement('div');
            item.style.cssText = 'display: flex; align-items: center; justify-content: space-between; padding: 8px; background: var(--arcane-surface-variant); border-radius: 4px; font-size: 0.875rem;';
            item.innerHTML = '<span>' + file.name + '</span><span style="color: var(--arcane-muted);">' + size + '</span>';
            fileList.appendChild(item);
          }
        }
      });

      dropzone.addEventListener('dragover', function(e) {
        e.preventDefault();
        dropzone.style.borderColor = 'var(--arcane-accent)';
      });

      dropzone.addEventListener('dragleave', function() {
        dropzone.style.borderColor = 'var(--arcane-border)';
      });

      dropzone.addEventListener('drop', function(e) {
        e.preventDefault();
        dropzone.style.borderColor = 'var(--arcane-border)';
        if (e.dataTransfer?.files?.length > 0) {
          fileInput.files = e.dataTransfer.files;
          fileInput.dispatchEvent(new Event('change'));
        }
      });
    });
  }

  function bindComboboxes() {
    document.querySelectorAll('.arcane-combobox').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var trigger = container.querySelector('.arcane-combobox-trigger');
      var dropdown = container.querySelector('.arcane-combobox-dropdown');
      var searchInput = container.querySelector('.arcane-combobox-search');
      var options = container.querySelectorAll('.arcane-combobox-option');

      if (!trigger) return;

      var isOpen = false;
      var selectedIndex = -1;

      function openDropdown() {
        if (dropdown) {
          dropdown.style.display = 'block';
          isOpen = true;
          container.classList.add('open');
          if (searchInput) searchInput.focus();
        }
      }

      function closeDropdown() {
        if (dropdown) {
          dropdown.style.display = 'none';
          isOpen = false;
          container.classList.remove('open');
          selectedIndex = -1;
        }
      }

      function updateHighlight() {
        options.forEach(function(opt, i) {
          opt.style.backgroundColor = i === selectedIndex ? 'var(--arcane-surface-variant)' : '';
        });
      }

      trigger.addEventListener('click', function(e) {
        e.stopPropagation();
        if (isOpen) closeDropdown();
        else openDropdown();
      });

      if (searchInput) {
        searchInput.addEventListener('input', function() {
          var query = searchInput.value.toLowerCase();
          options.forEach(function(opt) {
            var label = opt.textContent.toLowerCase();
            opt.style.display = label.includes(query) ? '' : 'none';
          });
        });
      }

      container.addEventListener('keydown', function(e) {
        if (!isOpen) return;

        if (e.key === 'ArrowDown') {
          e.preventDefault();
          selectedIndex = Math.min(selectedIndex + 1, options.length - 1);
          updateHighlight();
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          selectedIndex = Math.max(selectedIndex - 1, 0);
          updateHighlight();
        } else if (e.key === 'Enter' && selectedIndex >= 0) {
          e.preventDefault();
          options[selectedIndex].click();
        } else if (e.key === 'Escape') {
          closeDropdown();
        }
      });

      options.forEach(function(opt) {
        opt.addEventListener('click', function() {
          var label = opt.querySelector('div > div')?.textContent || opt.textContent;
          var valueSpan = trigger.querySelector('span');
          if (valueSpan) {
            valueSpan.textContent = label;
            valueSpan.style.color = 'var(--arcane-on-surface)';
          }
          closeDropdown();
        });
      });

      document.addEventListener('click', function(e) {
        if (!container.contains(e.target)) closeDropdown();
      });
    });
  }

  function bindCalendars() {
    document.querySelectorAll('.arcane-calendar').forEach(function(calendar) {
      if (calendar.dataset.arcaneInteractive === 'true') return;
      calendar.dataset.arcaneInteractive = 'true';

      var days = calendar.querySelectorAll('.arcane-calendar-day:not(.disabled):not(.outside)');
      days.forEach(function(day) {
        day.addEventListener('click', function() {
          calendar.querySelectorAll('.arcane-calendar-day.selected').forEach(function(d) {
            d.classList.remove('selected');
            d.style.background = 'transparent';
            d.style.color = 'var(--arcane-on-surface)';
          });

          day.classList.add('selected');
          day.style.background = 'var(--arcane-accent)';
          day.style.color = 'var(--arcane-on-accent)';
        });
      });
    });
  }

  function bindDatePickers() {
    document.querySelectorAll('.arcane-date-picker').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var trigger = container.querySelector('.arcane-date-picker-trigger');
      var dropdown = container.querySelector('.arcane-date-picker-dropdown');
      var clearBtn = container.querySelector('.arcane-date-picker-clear');

      if (!trigger) return;

      var isOpen = false;

      function toggleDropdown() {
        isOpen = !isOpen;
        if (dropdown) dropdown.style.display = isOpen ? 'block' : 'none';
        container.classList.toggle('open', isOpen);
      }

      trigger.addEventListener('click', function(e) {
        if (e.target.closest('.arcane-date-picker-clear')) return;
        e.stopPropagation();
        toggleDropdown();
      });

      if (clearBtn) {
        clearBtn.addEventListener('click', function(e) {
          e.stopPropagation();
          var valueSpan = trigger.querySelector('span:nth-child(2)');
          if (valueSpan) {
            valueSpan.textContent = container.dataset.placeholder || 'Select date...';
            valueSpan.style.color = 'var(--arcane-muted)';
          }
        });
      }

      document.addEventListener('click', function(e) {
        if (!container.contains(e.target) && isOpen) {
          isOpen = false;
          if (dropdown) dropdown.style.display = 'none';
          container.classList.remove('open');
        }
      });
    });
  }

  function bindTimePickers() {
    document.querySelectorAll('.arcane-time-picker').forEach(function(picker) {
      if (picker.dataset.arcaneInteractive === 'true') return;
      picker.dataset.arcaneInteractive = 'true';

      var hourCol = picker.querySelector('.arcane-time-hour-column');
      var minuteCol = picker.querySelector('.arcane-time-minute-column');
      var periodCol = picker.querySelector('.arcane-time-period-column');

      // Bind hour selection
      if (hourCol) {
        hourCol.querySelectorAll('.arcane-time-option').forEach(function(option) {
          option.addEventListener('click', function() {
            hourCol.querySelectorAll('.arcane-time-option').forEach(function(opt) {
              opt.classList.remove('selected');
              opt.style.background = 'transparent';
              opt.style.color = 'var(--arcane-on-surface)';
            });
            option.classList.add('selected');
            option.style.background = 'var(--arcane-accent)';
            option.style.color = 'var(--arcane-accent-foreground)';
            picker.dataset.hour = option.dataset.value;
          });
        });
      }

      // Bind minute selection
      if (minuteCol) {
        minuteCol.querySelectorAll('.arcane-time-option').forEach(function(option) {
          option.addEventListener('click', function() {
            minuteCol.querySelectorAll('.arcane-time-option').forEach(function(opt) {
              opt.classList.remove('selected');
              opt.style.background = 'transparent';
              opt.style.color = 'var(--arcane-on-surface)';
            });
            option.classList.add('selected');
            option.style.background = 'var(--arcane-accent)';
            option.style.color = 'var(--arcane-accent-foreground)';
            picker.dataset.minute = option.dataset.value;
          });
        });
      }

      // Bind AM/PM selection
      if (periodCol) {
        periodCol.querySelectorAll('.arcane-time-period-btn').forEach(function(btn) {
          btn.addEventListener('click', function() {
            periodCol.querySelectorAll('.arcane-time-period-btn').forEach(function(b) {
              b.classList.remove('selected');
              b.style.background = 'transparent';
              b.style.color = 'var(--arcane-muted)';
            });
            btn.classList.add('selected');
            btn.style.background = 'var(--arcane-accent)';
            btn.style.color = 'var(--arcane-accent-foreground)';
            picker.dataset.period = btn.dataset.value;
          });
        });
      }
    });
  }

  function bindFormattedInputs() {
    document.querySelectorAll('.arcane-formatted-input').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      if (container.classList.contains('disabled')) return;
      container.dataset.arcaneInteractive = 'true';

      var segments = container.querySelectorAll('.arcane-formatted-input-segment input');

      segments.forEach(function(input, index) {
        // Auto-advance to next field when max length reached
        input.addEventListener('input', function(e) {
          var maxLen = parseInt(input.getAttribute('maxlength') || '0');
          if (maxLen > 0 && input.value.length >= maxLen) {
            // Move to next input
            if (index < segments.length - 1) {
              segments[index + 1].focus();
              segments[index + 1].select();
            }
          }

          // Dispatch change event on container
          container.dispatchEvent(new CustomEvent('formattedchange', {
            detail: { values: getFormattedValues() }
          }));
        });

        // Handle backspace to go to previous field
        input.addEventListener('keydown', function(e) {
          if (e.key === 'Backspace' && input.value === '' && index > 0) {
            segments[index - 1].focus();
          }
          // Allow arrow key navigation
          if (e.key === 'ArrowLeft' && input.selectionStart === 0 && index > 0) {
            segments[index - 1].focus();
          }
          if (e.key === 'ArrowRight' && input.selectionStart === input.value.length && index < segments.length - 1) {
            segments[index + 1].focus();
          }
        });
      });

      function getFormattedValues() {
        var values = [];
        segments.forEach(function(seg) {
          values.push(seg.value);
        });
        return values;
      }
    });
  }


    function bindToggleButtonGroups() {
    document.querySelectorAll('.arcane-toggle-button-group').forEach(function(group) {
      if (group.dataset.arcaneInteractive === 'true') return;
      group.dataset.arcaneInteractive = 'true';

      var buttons = group.querySelectorAll('button');
      buttons.forEach(function(btn) {
        btn.addEventListener('click', function(e) {
          e.preventDefault();

          buttons.forEach(function(b) {
            b.style.backgroundColor = 'transparent';
            b.style.color = 'var(--arcane-on-surface-variant)';
            b.style.boxShadow = 'none';
          });

          btn.style.backgroundColor = 'var(--arcane-surface)';
          btn.style.color = 'var(--arcane-on-surface)';
          btn.style.boxShadow = 'var(--arcane-shadow-sm)';
        });
      });
    });
  }

  function bindCycleButtons() {
    document.querySelectorAll('.arcane-cycle-button').forEach(function(btn) {
      if (btn.dataset.arcaneInteractive === 'true') return;
      if (btn.disabled) return;
      btn.dataset.arcaneInteractive = 'true';

      btn.addEventListener('click', function(e) {
        e.preventDefault();

        var optionsStr = btn.dataset.options || '';
        var options = optionsStr.split('|').filter(function(o) { return o.length > 0; });
        var currentIndex = parseInt(btn.dataset.index) || 0;

        if (options.length > 0) {
          var nextIndex = (currentIndex + 1) % options.length;
          btn.dataset.index = nextIndex.toString();

          var labelSpan = btn.querySelector('.arcane-cycle-button-label');
          if (labelSpan) {
            labelSpan.textContent = options[nextIndex];
          }
        }

        btn.style.transform = 'scale(0.95)';
        setTimeout(function() {
          btn.style.transform = 'scale(1)';
        }, 100);

        var indicator = btn.querySelector('.arcane-cycle-button-indicator');
        if (indicator) {
          indicator.style.transition = 'transform 0.3s ease';
          indicator.style.transform = 'rotate(360deg)';
          setTimeout(function() {
            indicator.style.transition = 'none';
            indicator.style.transform = 'rotate(0deg)';
          }, 300);
        }
      });
    });
  }

  function bindToggleButtons() {
    document.querySelectorAll('.arcane-toggle-button').forEach(function(btn) {
      if (btn.dataset.arcaneInteractive === 'true') return;
      if (btn.disabled) return;
      if (btn.closest('.arcane-toggle-button-group')) return;
      btn.dataset.arcaneInteractive = 'true';

      btn.addEventListener('click', function(e) {
        e.preventDefault();
        var isPressed = btn.getAttribute('aria-pressed') === 'true';
        var newState = !isPressed;

        btn.setAttribute('aria-pressed', newState.toString());
        btn.classList.toggle('active', newState);

        if (newState) {
          btn.style.backgroundColor = 'var(--arcane-accent)';
          btn.style.color = 'var(--arcane-accent-foreground)';
          btn.style.border = 'none';
        } else {
          btn.style.backgroundColor = 'transparent';
          btn.style.color = 'var(--arcane-on-surface)';
          btn.style.border = '1px solid var(--arcane-border)';
        }
      });
    });
  }

  function bindButtons() {
    document.querySelectorAll('.arcane-button').forEach(function(btn) {
      if (btn.dataset.arcaneButtonInteractive === 'true') return;
      btn.dataset.arcaneButtonInteractive = 'true';

      btn.addEventListener('mousedown', function() { btn.style.transform = 'scale(0.98)'; });
      btn.addEventListener('mouseup', function() { btn.style.transform = 'scale(1)'; });
      btn.addEventListener('mouseleave', function() { btn.style.transform = 'scale(1)'; });
    });
  }

  // ===== COPY BUTTONS =====
  function bindCopyButtons() {
    document.querySelectorAll('[data-code]').forEach(function(btn) {
      if (btn.dataset.arcaneCopyBound) return;
      btn.dataset.arcaneCopyBound = 'true';

      btn.addEventListener('click', function(e) {
        e.preventDefault();
        var code = this.dataset.code;
        if (!code) return;

        navigator.clipboard.writeText(code).then(function() {
          // Find the icon inside the button and swap it
          var icon = btn.querySelector('svg');
          if (icon) {
            var originalPath = icon.innerHTML;
            // Set to checkmark icon
            icon.innerHTML = '<polyline points="20 6 9 17 4 12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"></polyline>';
            btn.style.color = 'var(--arcane-success)';

            setTimeout(function() {
              icon.innerHTML = originalPath;
              btn.style.color = '';
            }, 2000);
          }
        }).catch(function(err) {
          console.warn('[Arcane] Clipboard write failed:', err);
        });
      });
    });
  }


    function bindExpandersAccordions() {
    document.querySelectorAll('.arcane-expander-header, .arcane-accordion-header').forEach(function(header) {
      if (header.dataset.arcaneInteractive) return;
      header.dataset.arcaneInteractive = 'true';

      header.addEventListener('click', function() {
        var isExpanded = header.getAttribute('aria-expanded') === 'true';
        header.setAttribute('aria-expanded', (!isExpanded).toString());

        var container = header.closest('.arcane-expander, .arcane-accordion, [style*="border-radius"]');
        var content = header.nextElementSibling;
        if (content) {
          content.style.display = isExpanded ? 'none' : 'block';
        }

        var icon = header.querySelector('[style*="transform"], svg');
        if (icon) {
          icon.style.transform = isExpanded ? 'rotate(0deg)' : 'rotate(180deg)';
        }
      });
    });
  }

  function bindSelectors() {
    document.querySelectorAll('.arcane-selector').forEach(function(selector) {
      if (selector.dataset.arcaneInteractive) return;
      selector.dataset.arcaneInteractive = 'true';

      var wrapper = selector.closest('.arcane-selector-wrapper');
      if (!wrapper) return;

      selector.addEventListener('click', function(e) {
        e.stopPropagation();
        var dropdown = wrapper.querySelector('.arcane-selector-dropdown');
        var arrow = selector.querySelector('[style*="transform"]');

        if (dropdown) {
          var isOpen = dropdown.style.display !== 'none';
          dropdown.style.display = isOpen ? 'none' : 'block';
          if (arrow) arrow.style.transform = isOpen ? 'rotate(0)' : 'rotate(180deg)';
        } else {
          if (arrow) {
            var isRotated = arrow.style.transform.includes('180');
            arrow.style.transform = isRotated ? 'rotate(0)' : 'rotate(180deg)';
          }
        }
      });

      document.addEventListener('click', function(e) {
        if (!wrapper.contains(e.target)) {
          var dropdown = wrapper.querySelector('.arcane-selector-dropdown');
          var arrow = selector.querySelector('[style*="transform"]');
          if (dropdown) dropdown.style.display = 'none';
          if (arrow) arrow.style.transform = 'rotate(0)';
        }
      });
    });

    document.querySelectorAll('.arcane-selector-option').forEach(function(option) {
      if (option.dataset.arcaneOptionInteractive) return;
      option.dataset.arcaneOptionInteractive = 'true';

      option.addEventListener('click', function() {
        var wrapper = option.closest('.arcane-selector-wrapper');
        var selector = wrapper?.querySelector('.arcane-selector');
        var dropdown = wrapper?.querySelector('.arcane-selector-dropdown');
        var label = option.querySelector('span');

        if (selector && label) {
          var valueSpan = selector.querySelector('span');
          if (valueSpan) {
            valueSpan.textContent = label.textContent;
            valueSpan.style.color = 'var(--arcane-on-surface)';
          }
        }

        wrapper?.querySelectorAll('.arcane-selector-option').forEach(function(opt) {
          opt.style.backgroundColor = 'transparent';
          opt.style.color = 'var(--arcane-on-surface)';
          var check = opt.querySelector('span:last-child');
          if (check && check.textContent === '✓') check.remove();
        });

        option.style.backgroundColor = 'var(--arcane-accent-container)';
        option.style.color = 'var(--arcane-accent)';

        if (dropdown) dropdown.style.display = 'none';
        var arrow = selector?.querySelector('[style*="transform"]');
        if (arrow) arrow.style.transform = 'rotate(0)';
      });
    });
  }

  function bindTreeViews() {
    document.querySelectorAll('[role="tree"]').forEach(function(tree) {
      if (tree.dataset.arcaneInteractive) return;
      tree.dataset.arcaneInteractive = 'true';

      tree.querySelectorAll('[role="treeitem"]').forEach(function(item) {
        item.addEventListener('click', function(e) {
          e.stopPropagation();

          var hasChildren = item.getAttribute('aria-expanded') !== null;
          var isExpanded = item.getAttribute('aria-expanded') === 'true';
          var isSelected = item.getAttribute('aria-selected') === 'true';

          if (hasChildren) {
            item.setAttribute('aria-expanded', (!isExpanded).toString());
            var group = item.nextElementSibling;
            if (group && group.getAttribute('role') === 'group') {
              group.style.display = isExpanded ? 'none' : 'block';
            }
            var icon = item.querySelector('[style*="transform"]');
            if (icon) {
              icon.style.transform = isExpanded ? 'rotate(0deg)' : 'rotate(90deg)';
            }
          }

          tree.querySelectorAll('[role="treeitem"]').forEach(function(other) {
            other.setAttribute('aria-selected', 'false');
            other.style.background = 'transparent';
            var label = other.querySelector('span:last-child');
            if (label) {
              label.style.color = 'var(--arcane-on-surface)';
              label.style.fontWeight = 'normal';
            }
          });

          item.setAttribute('aria-selected', 'true');
          item.style.background = 'var(--arcane-surface-variant)';
          var label = item.querySelector('span:last-child');
          if (label) {
            label.style.color = 'var(--arcane-accent)';
            label.style.fontWeight = '500';
          }
        });
      });
    });
  }

  function bindPagination() {
    document.querySelectorAll('nav[aria-label="Pagination"]').forEach(function(nav) {
      if (nav.dataset.arcaneInteractive) return;
      nav.dataset.arcaneInteractive = 'true';

      nav.querySelectorAll('button').forEach(function(btn) {
        btn.addEventListener('click', function() {
          if (btn.disabled) return;

          nav.querySelectorAll('button').forEach(function(b) {
            if (b.getAttribute('aria-current') === 'page') {
              b.removeAttribute('aria-current');
              if (b.style.background?.includes('accent')) {
                b.style.background = 'transparent';
                b.style.border = '1px solid var(--arcane-border)';
              } else {
                b.style.background = 'var(--arcane-surface-variant)';
              }
              b.style.fontWeight = 'normal';
            }
          });

          var pageNum = parseInt(btn.textContent);
          if (!isNaN(pageNum)) {
            btn.setAttribute('aria-current', 'page');
            btn.style.background = 'var(--arcane-accent)';
            btn.style.border = '1px solid var(--arcane-accent)';
            btn.style.fontWeight = '500';
          }
        });
      });
    });
  }

  function bindBackToTop() {
    document.querySelectorAll('[title="Back to top"], button[style*="position: fixed"][style*="bottom"]').forEach(function(btn) {
      if (btn.dataset.arcaneInteractive) return;
      btn.dataset.arcaneInteractive = 'true';

      btn.addEventListener('click', function() {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });

      window.addEventListener('scroll', function() {
        if (window.scrollY > 300) {
          btn.style.opacity = '1';
          btn.style.pointerEvents = 'auto';
        } else {
          btn.style.opacity = '0';
          btn.style.pointerEvents = 'none';
        }
      });
    });
  }

  function bindContextMenus() {
    document.querySelectorAll('.arcane-context-menu-trigger').forEach(function(trigger) {
      if (trigger.dataset.arcaneInteractive === 'true') return;
      trigger.dataset.arcaneInteractive = 'true';

      var menu = trigger.querySelector('.arcane-context-menu');
      if (!menu) return;

      trigger.addEventListener('contextmenu', function(e) {
        e.preventDefault();

        document.querySelectorAll('.arcane-context-menu').forEach(function(m) {
          m.style.display = 'none';
        });

        menu.style.display = 'block';
        menu.style.left = e.clientX + 'px';
        menu.style.top = e.clientY + 'px';

        var rect = menu.getBoundingClientRect();
        if (rect.right > window.innerWidth) {
          menu.style.left = (e.clientX - rect.width) + 'px';
        }
        if (rect.bottom > window.innerHeight) {
          menu.style.top = (e.clientY - rect.height) + 'px';
        }
      });

      menu.querySelectorAll('.arcane-context-menu-item:not(.disabled)').forEach(function(item) {
        item.addEventListener('click', function() {
          menu.style.display = 'none';
        });
      });
    });

    document.addEventListener('click', function() {
      document.querySelectorAll('.arcane-context-menu').forEach(function(m) {
        m.style.display = 'none';
      });
    });

    document.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') {
        document.querySelectorAll('.arcane-context-menu').forEach(function(m) {
          m.style.display = 'none';
        });
      }
    });
  }

  function bindMenubars() {
    document.querySelectorAll('.arcane-menubar').forEach(function(menubar) {
      if (menubar.dataset.arcaneInteractive === 'true') return;
      menubar.dataset.arcaneInteractive = 'true';

      var menus = menubar.querySelectorAll('.arcane-menubar-menu');
      var activeMenu = null;

      function setMenuOpen(menu, open) {
        var state = open ? 'open' : 'closed';
        var trigger = menu.querySelector('.arcane-menubar-trigger');
        var content = menu.querySelector('.arcane-menubar-content');
        menu.setAttribute('data-state', state);
        menu.classList.toggle('open', open);
        if (trigger) {
          trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
          trigger.setAttribute('data-state', state);
        }
        if (content) {
          content.setAttribute('data-state', state);
          content.hidden = !open;
          if (!open) {
            content.querySelectorAll('.arcane-menubar-item.submenu-trigger').forEach(function(sub) {
              sub.setAttribute('aria-expanded', 'false');
            });
          }
        }
      }

      function closeAll() {
        menus.forEach(function(menu) { setMenuOpen(menu, false); });
        activeMenu = null;
      }

      function openMenu(menu) {
        closeAll();
        setMenuOpen(menu, true);
        activeMenu = menu;
      }

      menus.forEach(function(menu) {
        var trigger = menu.querySelector('.arcane-menubar-trigger');
        var content = menu.querySelector('.arcane-menubar-content');
        if (!trigger || !content) return;

        trigger.addEventListener('click', function(e) {
          e.stopPropagation();
          if (activeMenu === menu) {
            closeAll();
          } else {
            openMenu(menu);
          }
        });

        menu.addEventListener('mouseenter', function() {
          if (activeMenu && activeMenu !== menu) openMenu(menu);
        });

        content.querySelectorAll('.arcane-menubar-item.submenu-trigger').forEach(function(sub) {
          sub.addEventListener('click', function(e) {
            if (e.target.closest('.arcane-menubar-submenu')) return;
            e.stopPropagation();
            var expanded = sub.getAttribute('aria-expanded') === 'true';
            sub.setAttribute('aria-expanded', expanded ? 'false' : 'true');
          });
          sub.addEventListener('keydown', function(e) {
            if (e.target !== sub) return;
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowRight') {
              e.preventDefault();
              sub.setAttribute('aria-expanded', 'true');
            } else if (e.key === 'ArrowLeft') {
              sub.setAttribute('aria-expanded', 'false');
            }
          });
        });

        content.querySelectorAll('.arcane-menubar-item:not(.disabled):not(.submenu-trigger)').forEach(function(item) {
          item.addEventListener('click', function() { closeAll(); });
        });
      });

      document.addEventListener('click', function(e) {
        if (activeMenu && !menubar.contains(e.target)) closeAll();
      });

      document.addEventListener('keydown', function(e) {
        if (e.key !== 'Escape' || !activeMenu) return;
        var trigger = activeMenu.querySelector('.arcane-menubar-trigger');
        closeAll();
        if (trigger) trigger.focus();
      });
    });
  }

  function bindResizables() {
    document.querySelectorAll('.arcane-resizable').forEach(function(container) {
      if (container.dataset.arcaneInteractive === 'true') return;
      container.dataset.arcaneInteractive = 'true';

      var isHorizontal = container.dataset.direction === 'horizontal';
      var handles = container.querySelectorAll('.arcane-resizable-handle');
      var panels = container.querySelectorAll('.arcane-resizable-panel');

      handles.forEach(function(handle, handleIndex) {
        var isDragging = false;
        var startPos = 0;
        var startSizes = [];

        handle.addEventListener('mousedown', function(e) {
          e.preventDefault();
          isDragging = true;
          startPos = isHorizontal ? e.clientX : e.clientY;

          startSizes = [];
          panels.forEach(function(p) {
            var rect = p.getBoundingClientRect();
            startSizes.push(isHorizontal ? rect.width : rect.height);
          });

          container.classList.add('dragging');
          // The drag cursor has to sit on <body> so it survives the pointer
          // leaving the handle, and <body> is outside the themed root, so the
          // themed value is resolved here rather than referenced with var().
          // Themes that leave the seam unset fall back to the modern keyword.
          var resizeSeam = isHorizontal ? '--arcane-resize-cursor-ew' : '--arcane-resize-cursor-ns';
          var themedCursor = getComputedStyle(container).getPropertyValue(resizeSeam).trim();
          document.body.style.cursor = themedCursor || (isHorizontal ? 'col-resize' : 'row-resize');
          document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', function(e) {
          if (!isDragging) return;

          var currentPos = isHorizontal ? e.clientX : e.clientY;
          var delta = currentPos - startPos;

          var panel1 = panels[handleIndex];
          var panel2 = panels[handleIndex + 1];

          if (panel1 && panel2) {
            var newSize1 = startSizes[handleIndex] + delta;
            var newSize2 = startSizes[handleIndex + 1] - delta;

            var min1 = parseFloat(panel1.dataset.minSize) || 10;
            var max1 = parseFloat(panel1.dataset.maxSize) || 90;
            var min2 = parseFloat(panel2.dataset.minSize) || 10;
            var max2 = parseFloat(panel2.dataset.maxSize) || 90;

            var containerSize = isHorizontal ? container.clientWidth : container.clientHeight;
            var minPx1 = containerSize * min1 / 100;
            var maxPx1 = containerSize * max1 / 100;
            var minPx2 = containerSize * min2 / 100;
            var maxPx2 = containerSize * max2 / 100;

            if (newSize1 >= minPx1 && newSize1 <= maxPx1 && newSize2 >= minPx2 && newSize2 <= maxPx2) {
              panel1.style.flex = '0 0 ' + newSize1 + 'px';
              panel2.style.flex = '0 0 ' + newSize2 + 'px';
            }
          }
        });

        document.addEventListener('mouseup', function() {
          if (isDragging) {
            isDragging = false;
            container.classList.remove('dragging');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
          }
        });

        handle.addEventListener('keydown', function(e) {
          var step = e.shiftKey ? 50 : 10;
          var delta = 0;

          if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
            delta = -step;
          } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
            delta = step;
          }

          if (delta !== 0) {
            e.preventDefault();
            var panel1 = panels[handleIndex];
            var panel2 = panels[handleIndex + 1];

            if (panel1 && panel2) {
              var rect1 = panel1.getBoundingClientRect();
              var rect2 = panel2.getBoundingClientRect();
              var size1 = isHorizontal ? rect1.width : rect1.height;
              var size2 = isHorizontal ? rect2.width : rect2.height;

              panel1.style.flex = '0 0 ' + (size1 + delta) + 'px';
              panel2.style.flex = '0 0 ' + (size2 - delta) + 'px';
            }
          }
        });
      });
    });
  }

  function bindCommandPalettes() {
    // Guard against multiple bindings
    if (window.__arcaneCommandBound) return;
    window.__arcaneCommandBound = true;

    // Track selection state per overlay
    var selectedIndices = new WeakMap();

    // Inject hover/selected CSS styles if not already present
    if (!document.getElementById('arcane-command-styles')) {
      var style = document.createElement('style');
      style.id = 'arcane-command-styles';
      style.textContent = '\
        .arcane-command-item:hover:not(.disabled),\
        .neon-command-item:hover:not(.disabled) {\
          background-color: var(--accent, var(--secondary, rgba(255,255,255,0.1)));\
        }\
        .arcane-command-item.selected:not(.disabled),\
        .neon-command-item.selected:not(.disabled) {\
          background-color: var(--accent, var(--secondary, rgba(255,255,255,0.1)));\
          outline: 2px solid var(--ring, var(--primary, #3b82f6));\
          outline-offset: -2px;\
        }\
        .arcane-command-item.js-hidden,\
        .neon-command-item.js-hidden,\
        .arcane-command-group-heading.js-hidden,\
        .neon-command-group-heading.js-hidden {\
          display: none !important;\
        }\
      ';
      document.head.appendChild(style);
    }

    // Helper to find the overlay from any target
    function findOverlay(target) {
      return target.closest('.arcane-command-overlay, .neon-command-overlay');
    }

    // Helper to get current visible items in an overlay
    function getVisibleItems(overlay) {
      var allItems = overlay.querySelectorAll('.arcane-command-item:not(.disabled):not(.js-hidden), .neon-command-item:not(.disabled):not(.js-hidden)');
      return Array.from(allItems).filter(function(item) {
        return item.offsetParent !== null;
      });
    }

    // Get/set selected index for an overlay
    function getSelectedIndex(overlay) {
      return selectedIndices.get(overlay) || -1;
    }
    function setSelectedIndex(overlay, index) {
      selectedIndices.set(overlay, index);
    }

    function updateSelection(overlay, items) {
      var selectedIndex = getSelectedIndex(overlay);
      overlay.querySelectorAll('.arcane-command-item, .neon-command-item').forEach(function(item) {
        item.classList.remove('selected');
      });
      if (selectedIndex >= 0 && items[selectedIndex]) {
        items[selectedIndex].classList.add('selected');
        items[selectedIndex].scrollIntoView({ block: 'nearest' });
      }
    }

    // Close overlay helper
    function closeOverlay(overlay) {
      if (!overlay) return;
      overlay.style.display = 'none';
      setSelectedIndex(overlay, -1);
      // Dispatch custom event for Jaspr to handle state update
      overlay.dispatchEvent(new CustomEvent('arcane-command-close', { bubbles: true }));
    }

    // Handle item click - navigate via data-href. A same-tab row goes through
    // the runtime's nav.go, which fires the cancelable `arcane:nav` event
    // before it sets location, so an app router can take the move in place.
    function handleItemClick(item, overlay) {
      var href = item.dataset.href;
      var target = item.dataset.target;
      if (href) {
        if (target === '_blank') {
          window.open(href, '_blank', 'noopener,noreferrer');
        } else {
          window.Arcane.nav.go(href);
        }
        closeOverlay(overlay);
      }
    }

    // Filter items based on search query
    function filterItems(overlay, query) {
      var list = overlay.querySelector('.arcane-command-list, .neon-command-list');
      if (!list) return;

      var q = query.toLowerCase().trim();

      // Get all children of the list (headings and items are siblings)
      var children = Array.from(list.children);

      // First pass: filter all items
      children.forEach(function(child) {
        if (child.classList.contains('arcane-command-item') || child.classList.contains('neon-command-item')) {
          if (!q) {
            child.classList.remove('js-hidden');
            return;
          }
          var label = (child.dataset.label || '').toLowerCase();
          var keywords = (child.dataset.keywords || '').toLowerCase();
          var matches = label.includes(q) || keywords.includes(q);
          child.classList.toggle('js-hidden', !matches);
        }
      });

      // Second pass: hide headings that have no visible items following them
      var currentHeading = null;
      var hasVisibleItems = false;

      children.forEach(function(child, index) {
        var isHeading = child.classList.contains('arcane-command-group-heading') ||
                        child.classList.contains('neon-command-group-heading');
        var isItem = child.classList.contains('arcane-command-item') ||
                     child.classList.contains('neon-command-item');

        if (isHeading) {
          // Before processing new heading, finalize the previous one
          if (currentHeading) {
            if (!q) {
              currentHeading.classList.remove('js-hidden');
            } else {
              currentHeading.classList.toggle('js-hidden', !hasVisibleItems);
            }
          }
          // Start tracking new heading
          currentHeading = child;
          hasVisibleItems = false;
        } else if (isItem && currentHeading) {
          // Check if this item is visible
          if (!child.classList.contains('js-hidden')) {
            hasVisibleItems = true;
          }
        }
      });

      // Finalize the last heading
      if (currentHeading) {
        if (!q) {
          currentHeading.classList.remove('js-hidden');
        } else {
          currentHeading.classList.toggle('js-hidden', !hasVisibleItems);
        }
      }
    }

    // Auto-focus input when overlay appears
    function focusCommandInput(overlay) {
      if (!overlay) return;
      var input = overlay.querySelector('.arcane-command-input, .neon-command-input');
      if (input) {
        setTimeout(function() {
          input.focus();
        }, 50);
      }
    }

    // Watch for overlay appearance using MutationObserver
    var observer = new MutationObserver(function(mutations) {
      mutations.forEach(function(mutation) {
        mutation.addedNodes.forEach(function(node) {
          if (node.nodeType === 1) {
            // Check if the added node is an overlay or contains one
            if (node.classList && (node.classList.contains('arcane-command-overlay') ||
                                   node.classList.contains('neon-command-overlay'))) {
              focusCommandInput(node);
            } else if (node.querySelector) {
              var overlay = node.querySelector('.arcane-command-overlay, .neon-command-overlay');
              if (overlay) {
                focusCommandInput(overlay);
              }
            }
          }
        });
      });
    });

    observer.observe(document.body, { childList: true, subtree: true });

    // Also focus any existing overlay on page load
    var existingOverlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');
    if (existingOverlay && existingOverlay.style.display !== 'none') {
      focusCommandInput(existingOverlay);
    }

    // Document-level click handler (works for dynamically rendered overlays)
    document.addEventListener('click', function(e) {
      var overlay = findOverlay(e.target);

      // Handle item clicks. A row that carries its own runtime action is left
      // to the interaction runtime, so one activation navigates once.
      var item = e.target.closest('.arcane-command-item, .neon-command-item');
      if (item && item.hasAttribute('data-arcane-action')) return;
      if (item && overlay && !item.classList.contains('disabled')) {
        e.preventDefault();
        e.stopPropagation();
        handleItemClick(item, overlay);
        return;
      }

      // Handle click-outside to close
      var anyOverlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');
      if (anyOverlay && anyOverlay.style.display !== 'none') {
        var dialog = anyOverlay.querySelector('.arcane-command-dialog, .neon-command-dialog');
        if (anyOverlay.dataset.commandClosable === 'true') {
          if (!dialog || !dialog.contains(e.target)) {
            closeOverlay(anyOverlay);
          }
        }
      }
    }, true); // Capture phase

    // Document-level mouseover for hover selection
    document.addEventListener('mouseover', function(e) {
      var item = e.target.closest('.arcane-command-item, .neon-command-item');
      if (!item) return;
      var overlay = findOverlay(item);
      if (!overlay) return;
      if (item.classList.contains('disabled') || item.classList.contains('js-hidden')) return;

      var items = getVisibleItems(overlay);
      var idx = items.indexOf(item);
      if (idx >= 0) {
        setSelectedIndex(overlay, idx);
        updateSelection(overlay, items);
      }
    });

    // Document-level keyboard handler (works for dynamically rendered overlays)
    document.addEventListener('keydown', function(e) {
      var overlay = document.querySelector('.arcane-command-overlay, .neon-command-overlay');
      if (overlay && overlay.hasAttribute('data-arcane-surface')) return;
      if (!overlay || overlay.style.display === 'none') {
        // Only handle Ctrl+K when no overlay is open
        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
          e.preventDefault();
          e.stopPropagation();
          var trigger = document.querySelector('[data-command-trigger]');
          if (trigger) {
            trigger.click();
          }
        }
        return;
      }

      var items = getVisibleItems(overlay);
      var selectedIndex = getSelectedIndex(overlay);

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        e.stopPropagation();
        if (items.length > 0) {
          selectedIndex = selectedIndex < 0 ? 0 : Math.min(selectedIndex + 1, items.length - 1);
          setSelectedIndex(overlay, selectedIndex);
          updateSelection(overlay, items);
        }
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        e.stopPropagation();
        if (items.length > 0) {
          selectedIndex = Math.max(selectedIndex - 1, 0);
          setSelectedIndex(overlay, selectedIndex);
          updateSelection(overlay, items);
        }
      } else if (e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
        if (selectedIndex >= 0 && items[selectedIndex]) {
          handleItemClick(items[selectedIndex], overlay);
        } else if (items.length > 0) {
          handleItemClick(items[0], overlay);
        }
      } else if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        closeOverlay(overlay);
      }
    }, true); // Capture phase

    // Document-level input handler for search filtering
    document.addEventListener('input', function(e) {
      var input = e.target;
      if (!input.classList.contains('arcane-command-input') && !input.classList.contains('neon-command-input')) return;
      var overlay = findOverlay(input);
      if (!overlay) return;

      setSelectedIndex(overlay, -1);
      filterItems(overlay, input.value);
    });
  }

  function bindSteps() {
    document.querySelectorAll('.arcane-steps').forEach(function(steps) {
      if (steps.dataset.arcaneInteractive === 'true') return;
      steps.dataset.arcaneInteractive = 'true';

      var indicators = steps.querySelectorAll('.arcane-steps-indicator:not([disabled])');

      indicators.forEach(function(indicator) {
        indicator.addEventListener('click', function() {
          var stepIndex = indicator.dataset.stepIndex;
          if (stepIndex === undefined) return;

          var event = new CustomEvent('arcane-step-click', {
            bubbles: true,
            detail: { stepIndex: parseInt(stepIndex) }
          });
          steps.dispatchEvent(event);
        });

        indicator.addEventListener('mouseenter', function() {
          if (!indicator.disabled) {
            indicator.style.transform = 'scale(1.1)';
          }
        });

        indicator.addEventListener('mouseleave', function() {
          indicator.style.transform = 'scale(1)';
        });
      });
    });
  }

  function bindTimelines() {
    document.querySelectorAll('.arcane-timeline').forEach(function(timeline) {
      if (timeline.dataset.arcaneInteractive === 'true') return;
      timeline.dataset.arcaneInteractive = 'true';

      var items = timeline.querySelectorAll('.arcane-timeline-item');

      items.forEach(function(item) {
        item.addEventListener('mouseenter', function() {
          var content = item.querySelector('.arcane-timeline-content');
          if (content) {
            content.style.transform = 'translateX(4px)';
            content.style.transition = 'transform 0.15s ease';
          }
        });

        item.addEventListener('mouseleave', function() {
          var content = item.querySelector('.arcane-timeline-content');
          if (content) {
            content.style.transform = 'translateX(0)';
          }
        });
      });
    });
  }

  function bindDotIndicators() {
    // Dot indicators
    document.querySelectorAll('.arcane-dot-indicator').forEach(function(indicator) {
      if (indicator.dataset.arcaneInteractive === 'true') return;
      indicator.dataset.arcaneInteractive = 'true';

      var dots = indicator.querySelectorAll('.arcane-dot');
      var currentIndex = parseInt(indicator.dataset.currentIndex || '0');

      dots.forEach(function(dot, i) {
        dot.addEventListener('click', function() {
          if (indicator.dataset.interactive !== 'true') return;

          // Update visual state
          dots.forEach(function(d, j) {
            var isActive = j === i;
            d.style.background = isActive ? 'var(--arcane-accent)' : 'var(--arcane-muted)';
            d.style.transform = isActive ? 'scale(1.2)' : 'scale(1)';
          });

          indicator.dataset.currentIndex = i.toString();
          indicator.dispatchEvent(new CustomEvent('dotchange', { detail: { index: i } }));
        });
      });
    });

    // Step indicators
    document.querySelectorAll('.arcane-step-indicator').forEach(function(indicator) {
      if (indicator.dataset.arcaneInteractive === 'true') return;
      indicator.dataset.arcaneInteractive = 'true';

      var steps = indicator.querySelectorAll('.arcane-step');
      var circles = indicator.querySelectorAll('.arcane-step-circle');
      var allowNav = indicator.dataset.allowNavigation === 'true';

      circles.forEach(function(circle, i) {
        if (!allowNav) return;
        if (circle.disabled) return;

        circle.addEventListener('click', function() {
          var currentStep = parseInt(indicator.dataset.currentStep || '0');
          if (i > currentStep) return; // Can't skip ahead

          // Update step visuals
          circles.forEach(function(c, j) {
            var isCompleted = j < i;
            var isActive = j === i;
            c.style.background = isCompleted || isActive ? 'var(--arcane-accent)' : 'var(--arcane-surface-variant)';
            c.style.color = isCompleted || isActive ? 'var(--arcane-accent-foreground)' : 'var(--arcane-muted)';
            c.innerHTML = isCompleted ? '✓' : (j + 1).toString();
          });

          indicator.dataset.currentStep = i.toString();
          indicator.dispatchEvent(new CustomEvent('stepchange', { detail: { step: i } }));
        });
      });
    });
  }

  function bindTrackers() {
    // Grid tracker cells
    document.querySelectorAll('.arcane-tracker').forEach(function(tracker) {
      if (tracker.dataset.arcaneInteractive === 'true') return;
      tracker.dataset.arcaneInteractive = 'true';

      var cells = tracker.querySelectorAll('.arcane-tracker-cell');

      cells.forEach(function(cell) {
        // Show tooltip on hover
        cell.addEventListener('mouseenter', function() {
          cell.style.transform = 'scale(1.2)';
          cell.style.zIndex = '1';
        });

        cell.addEventListener('mouseleave', function() {
          cell.style.transform = 'scale(1)';
          cell.style.zIndex = '';
        });

        // Click handler
        cell.addEventListener('click', function() {
          if (cell.style.cursor !== 'pointer') return;

          var index = parseInt(cell.dataset.index || '0');
          var level = cell.dataset.level || 'unknown';

          tracker.dispatchEvent(new CustomEvent('celltap', {
            detail: { index: index, level: level }
          }));
        });
      });
    });

    // Uptime tracker bars
    document.querySelectorAll('.arcane-uptime-tracker').forEach(function(tracker) {
      if (tracker.dataset.arcaneInteractive === 'true') return;
      tracker.dataset.arcaneInteractive = 'true';

      var bars = tracker.querySelectorAll('.arcane-uptime-bar');

      bars.forEach(function(bar) {
        bar.addEventListener('mouseenter', function() {
          bar.style.opacity = '0.8';
        });

        bar.addEventListener('mouseleave', function() {
          bar.style.opacity = '1';
        });
      });
    });
  }

function bindDocsToc() {
  var tocContainer = document.querySelector('.toc-content');
  if (!tocContainer) return;

  var tocLinks = Array.prototype.slice.call(tocContainer.querySelectorAll('a'));

  function normalizeHeadingLabel(raw) {
    if (!raw) return '';
    var label = raw.trim();
    label = label.replace(/^#\s*/, '');
    label = label.replace(/\s*#$/, '');
    label = label.replace(/\s+/g, ' ').trim();
    return label;
  }

  function sanitizeExistingLabels() {
    tocLinks.forEach(function(link) {
      var text = link.textContent || '';
      var normalized = normalizeHeadingLabel(text);
      if (normalized) {
        link.textContent = normalized;
      }
    });
  }

  function getHeadingId(link) {
    var href = link.getAttribute('href');
    if (!href) return '';
    var hashIndex = href.indexOf('#');
    if (hashIndex === -1) return '';
    var rawId = href.slice(hashIndex + 1);
    try {
      return decodeURIComponent(rawId);
    } catch (e) {
      return rawId;
    }
  }

  sanitizeExistingLabels();

  if (!tocLinks.length) {
    var headingNodes = Array.prototype.slice.call(
      document.querySelectorAll(
        '.prose h1[id], .prose h2[id], .prose h3[id]'
      )
    );
    if (!headingNodes.length) return;

    var list = tocContainer.querySelector('ul');
    if (!list) {
      list = document.createElement('ul');
      tocContainer.innerHTML = '';
      tocContainer.appendChild(list);
    } else {
      list.innerHTML = '';
    }

    headingNodes.forEach(function(heading) {
      var id = heading.id;
      if (!id) return;
      if (heading.closest('.arcane-demo-docs')) return;
      var headingClass = heading.className || '';
      if (headingClass.indexOf('no_toc') !== -1) return;

      var label = heading.textContent ? normalizeHeadingLabel(heading.textContent) : id;
      if (!label) return;

      var item = document.createElement('li');
      var link = document.createElement('a');
      link.setAttribute('href', window.location.pathname + '#' + id);
      link.textContent = label;
      item.appendChild(link);
      list.appendChild(item);
    });

    tocLinks = Array.prototype.slice.call(tocContainer.querySelectorAll('a'));
    if (!tocLinks.length) return;
  }

  sanitizeExistingLabels();

  var headings = tocLinks.map(function(link) {
    var id = getHeadingId(link);
    if (!id) return null;
    var heading = document.getElementById(id);
    if (!heading) return null;
    return { id: id, element: heading };
  }).filter(function(entry) {
    return entry !== null;
  });

  if (!headings.length) return;

  var currentActive = '';

  function updateActiveLink(activeId) {
    if (!activeId || currentActive === activeId) return;
    currentActive = activeId;

    tocLinks.forEach(function(link) {
      var id = getHeadingId(link);
      link.classList.toggle('toc-active', id === activeId);
    });
  }

  function updateFromScroll() {
    var scrollOffset = 140;
    var activeId = headings[0].id;

    headings.forEach(function(entry) {
      if (entry.element.getBoundingClientRect().top <= scrollOffset) {
        activeId = entry.id;
      }
    });

    updateActiveLink(activeId);
  }

  tocLinks.forEach(function(link) {
    link.addEventListener('click', function() {
      var id = getHeadingId(link);
      if (id) updateActiveLink(id);
    });
  });

  window.addEventListener('scroll', updateFromScroll, { passive: true });
  window.addEventListener('resize', updateFromScroll);
  updateFromScroll();
}


    // Track if global click listener is already added
  var _popoverGlobalListenerAdded = false;
  var _activePopovers = new Set();

  function bindPopovers() {
    // Only target actual floating containers, not generic position:relative elements
    document.querySelectorAll('.arcane-floating-container, .neon-floating-container').forEach(function(container) {
      // Use the trigger class to find the trigger, not firstElementChild
      var trigger = container.querySelector('.arcane-floating-trigger') || container.firstElementChild;
      var popup = container.querySelector('.arcane-floating-content, .arcane-floating-tooltip, [style*="position: absolute"][style*="z-index"]');
      if (!trigger || !popup || container.dataset.arcanePopoverInteractive) return;
      // Hover cards, tooltips and popovers rendered as runtime surfaces open
      // through the interaction runtime and their own Dart state; binding
      // them here as well swallowed their clicks.
      if (popup.hasAttribute('data-arcane-surface')) return;
      container.dataset.arcanePopoverInteractive = 'true';

      var isClick = !popup.closest('.arcane-hovercard');

      if (isClick) {
        trigger.addEventListener('click', function(e) {
          e.stopPropagation();
          var isVisible = popup.style.opacity === '1';
          popup.style.opacity = isVisible ? '0' : '1';
          popup.style.visibility = isVisible ? 'hidden' : 'visible';
          popup.style.pointerEvents = isVisible ? 'none' : 'auto';
          if (!isVisible) {
            _activePopovers.add(container);
          } else {
            _activePopovers.delete(container);
          }
        });

        // Add global listener only once
        if (!_popoverGlobalListenerAdded) {
          _popoverGlobalListenerAdded = true;
          document.addEventListener('click', function(e) {
            _activePopovers.forEach(function(cont) {
              if (!cont.contains(e.target)) {
                var pop = cont.querySelector('.arcane-floating-content, .arcane-floating-tooltip, [style*="position: absolute"][style*="z-index"]');
                if (pop) {
                  pop.style.opacity = '0';
                  pop.style.visibility = 'hidden';
                  pop.style.pointerEvents = 'none';
                }
                _activePopovers.delete(cont);
              }
            });
          });
        }
      } else {
        container.addEventListener('mouseenter', function() {
          popup.style.opacity = '1';
          popup.style.visibility = 'visible';
        });
        container.addEventListener('mouseleave', function() {
          popup.style.opacity = '0';
          popup.style.visibility = 'hidden';
        });
      }
    });
  }

  var arcaneTooltipHideTimers = new WeakMap();

  function showArcaneTooltip(trigger, tooltip) {
    var pendingHide = arcaneTooltipHideTimers.get(tooltip);
    if (pendingHide) {
      clearTimeout(pendingHide);
      arcaneTooltipHideTimers.delete(tooltip);
    }

    trigger.dataset.arcaneTooltipOpen = 'true';
    tooltip.style.display = 'block';
    tooltip.style.opacity = '0';
    tooltip.style.visibility = 'hidden';
    tooltip.style.translate = '0px 0px';

    var position = trigger.dataset.tooltipPosition || 'top';
    if (position === 'top' || position === 'bottom') {
      tooltip.style.transform = 'translateX(-50%) translateY(0)';
    } else {
      tooltip.style.transform = 'translateY(-50%) translateX(0)';
    }

    // Keep the complete tooltip inside the visual viewport. Hidden absolutely
    // positioned tooltips otherwise expand the document scroll width when a
    // trigger sits near an edge.
    var viewportPadding = 8;
    var viewportWidth = document.documentElement.clientWidth;
    var viewportHeight = document.documentElement.clientHeight;
    var rect = tooltip.getBoundingClientRect();
    var shiftX = Math.max(
      viewportPadding - rect.left,
      Math.min(0, viewportWidth - viewportPadding - rect.right)
    );
    var shiftY = Math.max(
      viewportPadding - rect.top,
      Math.min(0, viewportHeight - viewportPadding - rect.bottom)
    );
    tooltip.style.translate =
      Math.round(shiftX) + 'px ' + Math.round(shiftY) + 'px';
    tooltip.style.visibility = 'visible';

    requestAnimationFrame(function() {
      if (trigger.dataset.arcaneTooltipOpen === 'true') {
        tooltip.style.opacity = '1';
      }
    });
  }

  function hideArcaneTooltip(trigger, tooltip) {
    trigger.dataset.arcaneTooltipOpen = 'false';
    tooltip.style.opacity = '0';
    tooltip.style.visibility = 'hidden';

    var pendingHide = setTimeout(function() {
      if (trigger.dataset.arcaneTooltipOpen !== 'true') {
        tooltip.style.display = 'none';
        tooltip.style.translate = '0px 0px';
      }
      arcaneTooltipHideTimers.delete(tooltip);
    }, 160);
    arcaneTooltipHideTimers.set(tooltip, pendingHide);
  }

  // One shared tooltip on <body> with delegated events: a node a renderer
  // owns is never wrapped or moved (Jaspr cannot update a moved node).
  var arcaneTip = {
    element: null,
    trigger: null,
    own: null,
    title: null,
    side: 'top',
    frame: 0,
    hideTimer: 0
  };

  // The element whose tooltip the pointer or focus at [node] asks for. Like
  // the browser, the nearest title wins; `data-no-tooltip` keeps it native.
  function arcaneTooltipTriggerOf(node) {
    var el = node && node.nodeType === 1 ? node : node && node.parentElement;
    for (; el && el !== document.documentElement; el = el.parentElement) {
      if (el === arcaneTip.trigger) return el;
      if (el.hasAttribute('title') || el.classList.contains('arcane-tooltip-trigger')) {
        return el.hasAttribute('data-no-tooltip') ? null : el;
      }
    }
    return null;
  }

  function arcaneTooltipElement() {
    if (arcaneTip.element && arcaneTip.element.isConnected) return arcaneTip.element;
    var tooltip = document.createElement('div');
    tooltip.className = 'arcane-tooltip arcane-tooltip-shared';
    tooltip.setAttribute('role', 'tooltip');
    tooltip.style.cssText = 'display: none; position: fixed; top: 0; left: 0; z-index: 9999; width: max-content; max-width: min(320px, calc(100vw - 16px)); padding: 6px 12px; font-size: 12px; font-weight: 500; line-height: 1.4; color: var(--arcane-on-surface, #f8fafc); background: var(--arcane-surface, #1e1e2e); border-radius: 8px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); white-space: normal; overflow-wrap: anywhere; pointer-events: none; opacity: 0; visibility: hidden; transition: opacity 150ms ease, visibility 150ms ease;';
    tooltip.appendChild(document.createTextNode(''));
    var arrow = document.createElement('div');
    arrow.className = 'arcane-tooltip-arrow';
    tooltip.appendChild(arrow);
    document.body.appendChild(tooltip);
    arcaneTip.element = tooltip;
    return tooltip;
  }

  // Beside the trigger on its side (flipped when that side has no room),
  // 8px away and inside the viewport, the arrow pointing at its middle.
  function arcaneTooltipPlace() {
    var tooltip = arcaneTip.element;
    var box = arcaneTip.trigger.getBoundingClientRect();
    var gap = 8;
    var pad = 8;
    var width = tooltip.offsetWidth;
    var height = tooltip.offsetHeight;
    var viewWidth = document.documentElement.clientWidth;
    var viewHeight = document.documentElement.clientHeight;
    var side = arcaneTip.side;
    if (side === 'top' && box.top - gap - height < pad && box.bottom + gap + height <= viewHeight - pad) side = 'bottom';
    else if (side === 'bottom' && box.bottom + gap + height > viewHeight - pad && box.top - gap - height >= pad) side = 'top';
    else if (side === 'left' && box.left - gap - width < pad) side = 'right';
    else if (side === 'right' && box.right + gap + width > viewWidth - pad) side = 'left';
    var across = side === 'top' || side === 'bottom';
    var x = across ? box.left + box.width / 2 - width / 2 : side === 'left' ? box.left - gap - width : box.right + gap;
    var y = across ? (side === 'top' ? box.top - gap - height : box.bottom + gap) : box.top + box.height / 2 - height / 2;
    x = Math.round(Math.max(pad, Math.min(x, viewWidth - pad - width)));
    y = Math.round(Math.max(pad, Math.min(y, viewHeight - pad - height)));
    tooltip.style.left = x + 'px';
    tooltip.style.top = y + 'px';
    var arrowAt = across
      ? Math.max(8, Math.min(box.left + box.width / 2 - x, width - 8)) - 4
      : Math.max(8, Math.min(box.top + box.height / 2 - y, height - 8)) - 4;
    var edge = { top: 'bottom', bottom: 'top', left: 'right', right: 'left' }[side];
    tooltip.lastChild.style.cssText = 'position: absolute; width: 8px; height: 8px; background: var(--arcane-surface, #1e1e2e); transform: rotate(45deg); ' +
      edge + ': -4px; ' + (across ? 'left: ' : 'top: ') + Math.round(arrowAt) + 'px;';
  }

  // Follows the trigger each frame; a title the renderer writes back is the
  // new text, one it drops stays dropped.
  function arcaneTooltipFollow() {
    arcaneTip.frame = 0;
    var trigger = arcaneTip.trigger;
    if (!trigger || arcaneTip.own) return;
    if (!trigger.isConnected || trigger.hasAttribute('data-no-tooltip')) {
      arcaneTooltipHide();
      return;
    }
    if (arcaneTip.title !== null) {
      var title = trigger.getAttribute('title');
      if (title === null) {
        arcaneTip.title = null;
        arcaneTooltipHide();
        return;
      }
      if (title !== '') {
        arcaneTip.title = title;
        trigger.setAttribute('title', '');
        arcaneTip.element.firstChild.nodeValue = title;
      }
    }
    arcaneTooltipPlace();
    arcaneTip.element.style.opacity = '1';
    arcaneTip.frame = requestAnimationFrame(arcaneTooltipFollow);
  }

  function arcaneTooltipShow(trigger) {
    if (arcaneTip.trigger === trigger) return;
    arcaneTooltipHide();
    var marked = trigger.classList.contains('arcane-tooltip-trigger');
    var own = marked ? trigger.querySelector('.arcane-tooltip') : null;
    if (own) {
      arcaneTip.trigger = trigger;
      arcaneTip.own = own;
      showArcaneTooltip(trigger, own);
      return;
    }
    var text = marked ? trigger.dataset.tooltip : trigger.getAttribute('title');
    if (!text) return;
    var tooltip = arcaneTooltipElement();
    // An empty title keeps the browser's own tip away until this one closes.
    if (trigger.hasAttribute('title')) {
      arcaneTip.title = trigger.getAttribute('title');
      trigger.setAttribute('title', '');
    }
    arcaneTip.trigger = trigger;
    arcaneTip.side = trigger.dataset.tooltipPosition || 'top';
    clearTimeout(arcaneTip.hideTimer);
    tooltip.firstChild.nodeValue = text;
    tooltip.style.display = 'block';
    tooltip.style.visibility = 'visible';
    arcaneTooltipPlace();
    arcaneTip.frame = requestAnimationFrame(arcaneTooltipFollow);
  }

  function arcaneTooltipHide() {
    var trigger = arcaneTip.trigger;
    if (!trigger) return;
    arcaneTip.trigger = null;
    if (arcaneTip.own) {
      hideArcaneTooltip(trigger, arcaneTip.own);
      arcaneTip.own = null;
      return;
    }
    if (arcaneTip.frame) cancelAnimationFrame(arcaneTip.frame);
    arcaneTip.frame = 0;
    if (arcaneTip.title !== null) {
      if (trigger.getAttribute('title') === '') trigger.setAttribute('title', arcaneTip.title);
      arcaneTip.title = null;
    }
    var tooltip = arcaneTip.element;
    tooltip.style.opacity = '0';
    tooltip.style.visibility = 'hidden';
    clearTimeout(arcaneTip.hideTimer);
    arcaneTip.hideTimer = setTimeout(function() {
      if (!arcaneTip.trigger || arcaneTip.own) tooltip.style.display = 'none';
    }, 160);
  }

  function bindTooltips() {
    if (window.arcaneTooltipsBound) return;
    window.arcaneTooltipsBound = true;

    // Pointers only: a tap is not a hover.
    document.addEventListener('pointerover', function(event) {
      if (event.pointerType === 'touch') return;
      var trigger = arcaneTooltipTriggerOf(event.target);
      if (trigger) arcaneTooltipShow(trigger);
      else if (arcaneTip.trigger && !arcaneTip.trigger.contains(document.activeElement)) arcaneTooltipHide();
    });

    document.addEventListener('pointerout', function(event) {
      var trigger = arcaneTip.trigger;
      if (!trigger || event.pointerType === 'touch') return;
      if (event.relatedTarget && trigger.contains(event.relatedTarget)) return;
      if (trigger.contains(document.activeElement) && document.activeElement.matches(':focus-visible')) return;
      arcaneTooltipHide();
    });

    document.addEventListener('focusin', function(event) {
      var target = event.target;
      if (!target || !target.matches || !target.matches(':focus-visible')) return;
      var trigger = arcaneTooltipTriggerOf(target);
      if (trigger) arcaneTooltipShow(trigger);
    });

    document.addEventListener('focusout', function(event) {
      var trigger = arcaneTip.trigger;
      if (trigger && trigger.contains(event.target) && !trigger.contains(event.relatedTarget)) arcaneTooltipHide();
    });

    document.addEventListener('keydown', function(event) {
      if (event.key === 'Escape') arcaneTooltipHide();
    });
  }

  function bindDialogs() {
    document.querySelectorAll('.arcane-dialog-overlay, [role="dialog"]').forEach(function(overlay) {
      if (overlay.closest('[data-arcane-surface]')) return;
      if (overlay.dataset.arcaneInteractive === 'true') return;
      overlay.dataset.arcaneInteractive = 'true';

      var dialog = overlay.querySelector('.arcane-dialog') || overlay;
      var closeBtn = overlay.querySelector('.arcane-dialog-close, [aria-label="Close"]');

      function closeDialog() {
        overlay.style.opacity = '0';
        if (dialog !== overlay) {
          dialog.style.transform = 'scale(0.95)';
        }
        setTimeout(function() {
          overlay.style.display = 'none';
          document.body.style.overflow = '';
        }, 150);
      }

      if (closeBtn) {
        closeBtn.addEventListener('click', closeDialog);
      }

      overlay.addEventListener('click', function(e) {
        if (e.target === overlay) {
          closeDialog();
        }
      });

      document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && overlay.style.display !== 'none') {
          closeDialog();
        }
      });
    });

    document.querySelectorAll('button').forEach(function(btn) {
      var text = (btn.textContent || '').toLowerCase();
      if (text.includes('open dialog') || text.includes('show dialog')) {
        if (btn.dataset.arcaneDialogBound) return;
        btn.dataset.arcaneDialogBound = 'true';

        btn.addEventListener('click', function() {
          var overlay = document.querySelector('.arcane-dialog-overlay');
          if (overlay) {
            document.body.style.overflow = 'hidden';
            overlay.style.display = 'flex';
            overlay.style.opacity = '1';
            var dialog = overlay.querySelector('.arcane-dialog');
            if (dialog) {
              dialog.style.transform = 'scale(1)';
            }
          }
        });
      }
    });
  }

  function bindDrawers() {
    document.querySelectorAll('.arcane-drawer-overlay').forEach(function(overlay) {
      if (overlay.dataset.arcaneInteractive === 'true') return;
      overlay.dataset.arcaneInteractive = 'true';

      var drawer = overlay.querySelector('.arcane-drawer');
      var closeBtn = overlay.querySelector('.arcane-drawer-close');

      function closeDrawer() {
        overlay.style.opacity = '0';
        if (drawer) {
          var position = drawer.dataset.position || 'right';
          if (position === 'left') {
            drawer.style.transform = 'translateX(-100%)';
          } else if (position === 'right') {
            drawer.style.transform = 'translateX(100%)';
          } else if (position === 'top') {
            drawer.style.transform = 'translateY(-100%)';
          } else if (position === 'bottom') {
            drawer.style.transform = 'translateY(100%)';
          }
        }
        setTimeout(function() {
          overlay.style.display = 'none';
          document.body.style.overflow = '';
        }, 200);
      }

      if (closeBtn) {
        closeBtn.addEventListener('click', closeDrawer);
      }

      overlay.addEventListener('click', function(e) {
        if (e.target === overlay) {
          closeDrawer();
        }
      });
    });

    document.querySelectorAll('button').forEach(function(btn) {
      var text = (btn.textContent || '').toLowerCase();
      if (text.includes('open drawer') || text.includes('show drawer')) {
        if (btn.dataset.arcaneDrawerBound) return;
        btn.dataset.arcaneDrawerBound = 'true';

        btn.addEventListener('click', function() {
          var overlay = document.querySelector('.arcane-drawer-overlay');
          if (overlay) {
            document.body.style.overflow = 'hidden';
            overlay.style.display = 'flex';
            requestAnimationFrame(function() {
              overlay.style.opacity = '1';
              var drawer = overlay.querySelector('.arcane-drawer');
              if (drawer) {
                drawer.style.transform = 'translateX(0)';
              }
            });
          }
        });
      }
    });
  }

  function bindMobileMenus() {
    document.querySelectorAll('.arcane-mobile-menu-trigger, [aria-label*="Menu"]').forEach(function(trigger) {
      if (trigger.dataset.arcaneInteractive === 'true') return;
      trigger.dataset.arcaneInteractive = 'true';

      trigger.addEventListener('click', function() {
        var overlay = document.querySelector('.arcane-mobile-menu-overlay');
        var menu = document.querySelector('.arcane-mobile-menu');

        if (overlay && menu) {
          var isOpen = overlay.style.display === 'flex';

          if (isOpen) {
            overlay.style.opacity = '0';
            menu.style.transform = 'translateX(-100%)';
            setTimeout(function() {
              overlay.style.display = 'none';
              document.body.style.overflow = '';
            }, 200);
          } else {
            document.body.style.overflow = 'hidden';
            overlay.style.display = 'flex';
            requestAnimationFrame(function() {
              overlay.style.opacity = '1';
              menu.style.transform = 'translateX(0)';
            });
          }
        }
      });
    });

    var overlay = document.querySelector('.arcane-mobile-menu-overlay');
    if (overlay && !overlay.dataset.arcaneInteractive) {
      overlay.dataset.arcaneInteractive = 'true';

      overlay.addEventListener('click', function(e) {
        var menu = overlay.querySelector('.arcane-mobile-menu');
        if (e.target === overlay) {
          overlay.style.opacity = '0';
          if (menu) menu.style.transform = 'translateX(-100%)';
          setTimeout(function() {
            overlay.style.display = 'none';
            document.body.style.overflow = '';
          }, 200);
        }
      });

      var closeBtn = overlay.querySelector('.arcane-mobile-menu-close');
      if (closeBtn) {
        closeBtn.addEventListener('click', function() {
          var menu = overlay.querySelector('.arcane-mobile-menu');
          overlay.style.opacity = '0';
          if (menu) menu.style.transform = 'translateX(-100%)';
          setTimeout(function() {
            overlay.style.display = 'none';
            document.body.style.overflow = '';
          }, 200);
        });
      }
    }
  }

  function bindSheets() {
    // Close buttons on sheets
    document.querySelectorAll('.arcane-sheet .arcane-sheet-close').forEach(function(closeBtn) {
      if (closeBtn.dataset.arcaneInteractive === 'true') return;
      closeBtn.dataset.arcaneInteractive = 'true';

      closeBtn.addEventListener('click', function() {
        var sheet = closeBtn.closest('.arcane-sheet-overlay');
        if (sheet) {
          sheet.remove();
        }
      });
    });

    // Backdrop dismiss for sheets
    document.querySelectorAll('.arcane-sheet-overlay').forEach(function(overlay) {
      if (overlay.dataset.arcaneInteractive === 'true') return;
      overlay.dataset.arcaneInteractive = 'true';

      overlay.addEventListener('click', function(e) {
        if (e.target === overlay && overlay.dataset.barrierDismissible !== 'false') {
          overlay.remove();
        }
      });
    });

    // Action sheet items
    document.querySelectorAll('.arcane-action-sheet-item').forEach(function(item) {
      if (item.dataset.arcaneInteractive === 'true') return;
      if (item.dataset.disabled === 'true') return;
      item.dataset.arcaneInteractive = 'true';

      item.addEventListener('click', function() {
        var actionSheet = item.closest('.arcane-sheet-overlay');
        if (actionSheet) {
          actionSheet.remove();
        }
      });
    });
  }

  function openSheet(position, content, options) {
    options = options || {};
    var overlay = document.createElement('div');
    overlay.className = 'arcane-sheet-overlay';
    overlay.dataset.barrierDismissible = options.barrierDismissible !== false ? 'true' : 'false';
    overlay.style.cssText = 'position:fixed;inset:0;z-index:1050;display:flex;align-items:' +
      (position === 'bottom' ? 'flex-end' : position === 'top' ? 'flex-start' : 'stretch') +
      ';justify-content:' + (position === 'end' ? 'flex-end' : position === 'start' ? 'flex-start' : 'center') +
      ';background:var(--arcane-scrim);';

    var sheet = document.createElement('div');
    sheet.className = 'arcane-sheet arcane-sheet-' + position;

    var baseStyles = 'background:var(--arcane-surface);display:flex;flex-direction:column;overflow:hidden;';
    if (position === 'bottom' || position === 'top') {
      sheet.style.cssText = baseStyles + 'width:100%;max-height:' + (options.height || 400) + 'px;border-radius:var(--arcane-radius-md) var(--arcane-radius-md) 0 0;';
    } else {
      sheet.style.cssText = baseStyles + 'height:100%;width:' + (options.width || 400) + 'px;max-width:100%;';
    }

    sheet.innerHTML = content;
    overlay.appendChild(sheet);
    document.body.appendChild(overlay);

    bindSheets();
    return overlay;
  }

  window.ArcaneSheet = { open: openSheet };

  function bindEmailDialogs() {
    document.querySelectorAll('.arcane-email-dialog').forEach(function(dialog) {
      if (dialog.dataset.arcaneInteractive === 'true') return;
      dialog.dataset.arcaneInteractive = 'true';

      var input = dialog.querySelector('.arcane-email-dialog-input input');
      var errorEl = dialog.querySelector('.arcane-email-dialog-error');
      var submitBtn = dialog.querySelector('.arcane-email-dialog-submit');
      var blockedDomains = (dialog.dataset.blockedDomains || '').split(',').filter(Boolean);
      var requireWork = dialog.dataset.requireWork === 'true';

      if (input) {
        input.addEventListener('input', function() {
          validateEmail();
        });

        input.addEventListener('blur', function() {
          validateEmail();
        });
      }

      if (submitBtn) {
        submitBtn.addEventListener('click', function() {
          if (validateEmail()) {
            dialog.dispatchEvent(new CustomEvent('emailsubmit', {
              detail: { email: input.value }
            }));
          }
        });
      }

      function validateEmail() {
        var email = input.value.trim();
        var error = null;

        if (!email) {
          error = null; // No error for empty
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
          error = 'Please enter a valid email address';
        } else {
          var domain = email.split('@')[1].toLowerCase();

          // Check blocked domains
          for (var i = 0; i < blockedDomains.length; i++) {
            if (domain === blockedDomains[i].toLowerCase()) {
              error = 'This email domain is not allowed';
              break;
            }
          }

          // Check work email requirement
          if (!error && requireWork) {
            var personalDomains = ['gmail.com', 'yahoo.com', 'hotmail.com', 'outlook.com', 'aol.com', 'icloud.com', 'mail.com'];
            for (var j = 0; j < personalDomains.length; j++) {
              if (domain === personalDomains[j]) {
                error = 'Please use a work email address';
                break;
              }
            }
          }
        }

        if (errorEl) {
          errorEl.textContent = error || '';
          errorEl.style.display = error ? 'block' : 'none';
        }

        if (submitBtn) {
          submitBtn.disabled = !!error || !email;
        }

        input.style.borderColor = error ? 'var(--arcane-destructive)' : 'var(--arcane-border)';

        return !error && email;
      }
    });
  }

  function bindTimeDialogs() {
    document.querySelectorAll('.arcane-time-dialog').forEach(function(dialog) {
      if (dialog.dataset.arcaneInteractive === 'true') return;
      dialog.dataset.arcaneInteractive = 'true';

      var hourCol = dialog.querySelector('.arcane-time-dialog-hour');
      var minuteCol = dialog.querySelector('.arcane-time-dialog-minute');
      var periodCol = dialog.querySelector('.arcane-time-dialog-period');

      var selectedHour = parseInt(dialog.dataset.hour || '12');
      var selectedMinute = parseInt(dialog.dataset.minute || '0');
      var selectedPeriod = dialog.dataset.period || 'AM';

      // Bind column selections
      bindTimeColumn(hourCol, 'hour', function(val) {
        selectedHour = parseInt(val);
        dialog.dataset.hour = val;
        updateDisplay();
      });

      bindTimeColumn(minuteCol, 'minute', function(val) {
        selectedMinute = parseInt(val);
        dialog.dataset.minute = val;
        updateDisplay();
      });

      bindTimeColumn(periodCol, 'period', function(val) {
        selectedPeriod = val;
        dialog.dataset.period = val;
        updateDisplay();
      });

      function updateDisplay() {
        var displayEl = dialog.querySelector('.arcane-time-dialog-display');
        if (displayEl) {
          var minStr = selectedMinute.toString().padStart(2, '0');
          displayEl.textContent = selectedHour + ':' + minStr + ' ' + selectedPeriod;
        }

        dialog.dispatchEvent(new CustomEvent('timechange', {
          detail: {
            hour: selectedHour,
            minute: selectedMinute,
            period: selectedPeriod
          }
        }));
      }

      function bindTimeColumn(col, type, onChange) {
        if (!col) return;
        var items = col.querySelectorAll('.arcane-time-dialog-item');
        items.forEach(function(item) {
          item.addEventListener('click', function() {
            items.forEach(function(i) {
              i.classList.remove('selected');
              i.style.background = 'transparent';
              i.style.color = 'var(--arcane-on-surface)';
            });
            item.classList.add('selected');
            item.style.background = 'var(--arcane-accent)';
            item.style.color = 'var(--arcane-accent-foreground)';
            onChange(item.dataset.value);
          });
        });
      }
    });
  }

  function bindItemPickers() {
    document.querySelectorAll('.arcane-item-picker-content').forEach(function(picker) {
      if (picker.dataset.arcaneInteractive === 'true') return;
      picker.dataset.arcaneInteractive = 'true';

      var searchInput = picker.querySelector('.arcane-item-picker-search input');
      var items = picker.querySelectorAll('.arcane-item-picker-item');
      var isMultiSelect = picker.dataset.multiSelect === 'true';
      var selectedItems = new Set();

      // Search filtering
      if (searchInput) {
        searchInput.addEventListener('input', function() {
          var query = searchInput.value.toLowerCase();
          items.forEach(function(item) {
            var text = item.textContent.toLowerCase();
            item.style.display = text.includes(query) ? 'flex' : 'none';
          });
        });
      }

      // Item selection
      items.forEach(function(item) {
        item.addEventListener('click', function() {
          var itemId = item.dataset.itemId || item.textContent;

          if (isMultiSelect) {
            if (selectedItems.has(itemId)) {
              selectedItems.delete(itemId);
              item.classList.remove('selected');
              item.style.background = 'transparent';
              item.style.borderColor = 'var(--arcane-border)';
            } else {
              selectedItems.add(itemId);
              item.classList.add('selected');
              item.style.background = 'var(--arcane-accent-container)';
              item.style.borderColor = 'var(--arcane-accent)';
            }

            // Update selection count
            var counter = picker.querySelector('.arcane-item-picker-count');
            if (counter) {
              counter.textContent = selectedItems.size + ' item(s) selected';
              counter.style.display = selectedItems.size > 0 ? 'block' : 'none';
            }
          } else {
            // Single select - deselect others
            items.forEach(function(i) {
              i.classList.remove('selected');
              i.style.background = 'transparent';
              i.style.borderColor = 'var(--arcane-border)';
            });
            item.classList.add('selected');
            item.style.background = 'var(--arcane-accent-container)';
            item.style.borderColor = 'var(--arcane-accent)';
          }

          picker.dispatchEvent(new CustomEvent('itemselect', {
            detail: {
              item: itemId,
              selected: isMultiSelect ? Array.from(selectedItems) : itemId
            }
          }));
        });
      });
    });
  }

  function bindChatScreens() {
    document.querySelectorAll('.arcane-chat-screen').forEach(function(chat) {
      if (chat.dataset.arcaneInteractive === 'true') return;
      chat.dataset.arcaneInteractive = 'true';

      var inputArea = chat.querySelector('.arcane-chat-input');
      var sendBtn = chat.querySelector('.arcane-chat-send');
      var messagesArea = chat.querySelector('.arcane-chat-messages');

      if (inputArea && sendBtn) {
        // Auto-resize textarea
        inputArea.addEventListener('input', function() {
          inputArea.style.height = 'auto';
          inputArea.style.height = Math.min(inputArea.scrollHeight, 120) + 'px';
        });

        // Send on enter (without shift)
        inputArea.addEventListener('keydown', function(e) {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
          }
        });

        // Send button click
        sendBtn.addEventListener('click', function() {
          sendMessage();
        });
      }

      function sendMessage() {
        var text = inputArea.value.trim();
        if (!text) return;

        // Dispatch event for handling
        chat.dispatchEvent(new CustomEvent('sendmessage', {
          detail: { text: text }
        }));

        // Clear input
        inputArea.value = '';
        inputArea.style.height = 'auto';

        // Scroll to bottom
        if (messagesArea) {
          messagesArea.scrollTop = messagesArea.scrollHeight;
        }
      }

      // Auto-scroll when new messages arrive
      if (messagesArea) {
        var observer = new MutationObserver(function(mutations) {
          mutations.forEach(function(mutation) {
            if (mutation.addedNodes.length) {
              messagesArea.scrollTop = messagesArea.scrollHeight;
            }
          });
        });

        observer.observe(messagesArea, { childList: true });
      }
    });
  }


      // ===== MAP SHIFT+HOVER COORDINATE MODE =====
  var _shiftKeyHeld = false;
  var _mapCoordTooltips = {};
  var _mapCoordCounter = 0;

  function bindMapCoordinateMode() {
    // Track Shift key globally (only bind once)
    if (!window._arcaneShiftKeyBound) {
      window._arcaneShiftKeyBound = true;

      document.addEventListener('keydown', function(e) {
        if (e.key === 'Shift' && !_shiftKeyHeld) {
          _shiftKeyHeld = true;
          document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {
            map.classList.add('arcane-map-picking');
          });
        }
      });

      document.addEventListener('keyup', function(e) {
        if (e.key === 'Shift') {
          _shiftKeyHeld = false;
          document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {
            map.classList.remove('arcane-map-picking');
          });
          // Hide all coord tooltips
          Object.values(_mapCoordTooltips).forEach(function(tooltip) {
            if (tooltip) {
              tooltip.style.opacity = '0';
              tooltip.style.visibility = 'hidden';
            }
          });
        }
      });

      // Watch for new maps being added (for tabs, etc.)
      var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
          mutation.addedNodes.forEach(function(node) {
            if (node.nodeType === 1) {
              // Check if the added node is a map or contains maps
              if (node.classList && (node.classList.contains('arcane-world-map') || node.classList.contains('arcane-usa-map'))) {
                bindSingleMap(node);
              }
              // Also check children
              if (node.querySelectorAll) {
                node.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {
                  bindSingleMap(map);
                });
              }
            }
          });
        });
      });
      observer.observe(document.body, { childList: true, subtree: true });
    }

    // Bind to all existing maps
    bindAllMaps();
  }

  function bindAllMaps() {
    document.querySelectorAll('.arcane-world-map, .arcane-usa-map').forEach(function(map) {
      bindSingleMap(map);
    });
  }

  function bindSingleMap(map) {
    if (map.dataset.arcaneCoordModeBound) return;
    map.dataset.arcaneCoordModeBound = 'true';
    bindMapCoordinates(map);
  }

  function bindMapCoordinates(map) {
    // Create tooltip and append to body (not map) to avoid Jaspr DOM conflicts
    var tooltip = createMapCoordTooltip();
    var mapId = 'coord-map-' + (_mapCoordCounter++);
    map.dataset.coordMapId = mapId;
    _mapCoordTooltips[mapId] = tooltip;
    document.body.appendChild(tooltip);

    // Helper to get map dimensions based on class at event time
    function getMapDimensions(mapEl) {
      var isUsa = mapEl.classList.contains('arcane-usa-map');
      return {
        width: isUsa ? 1000 : 2000,
        height: isUsa ? 589 : 857,
        latMax: isUsa ? 50 : 83,
        latRange: isUsa ? 25 : 143,
        isUsa: isUsa
      };
    }

    function calculateCoords(relX, relY, dims) {
      var svgX = relX * dims.width;
      var svgY = relY * dims.height;

      var lat, lng;
      if (dims.isUsa) {
        // USA map projection inverse
        lng = ((svgX - 50) / 900) * 58 - 125;
        lat = 50 - ((svgY - 50) / 450) * 25;
      } else {
        // World map projection (equirectangular)
        lng = (svgX / dims.width) * 360 - 180;
        lat = dims.latMax - (svgY / dims.height) * dims.latRange;
      }

      return { lat: lat, lng: lng, svgX: svgX, svgY: svgY };
    }

    map.addEventListener('mousemove', function(e) {
      if (!_shiftKeyHeld) {
        tooltip.style.opacity = '0';
        tooltip.style.visibility = 'hidden';
        return;
      }

      var dims = getMapDimensions(map);
      var rect = map.getBoundingClientRect();
      var mouseX = e.clientX - rect.left;
      var mouseY = e.clientY - rect.top;
      var relX = Math.max(0, Math.min(1, mouseX / rect.width));
      var relY = Math.max(0, Math.min(1, mouseY / rect.height));

      var coords = calculateCoords(relX, relY, dims);

      // Position tooltip fixed to viewport
      tooltip.style.position = 'fixed';
      tooltip.style.left = (e.clientX) + 'px';
      tooltip.style.top = (e.clientY + 20) + 'px';
      tooltip.style.transform = 'translateX(-50%)';
      tooltip.style.opacity = '1';
      tooltip.style.visibility = 'visible';

      // Update content
      var latEl = tooltip.querySelector('.coord-lat');
      var lngEl = tooltip.querySelector('.coord-lng');
      var svgEl = tooltip.querySelector('.coord-svg');

      if (latEl) latEl.textContent = 'Lat: ' + coords.lat.toFixed(4);
      if (lngEl) lngEl.textContent = 'Lng: ' + coords.lng.toFixed(4);
      if (svgEl) svgEl.textContent = 'SVG: ' + Math.round(coords.svgX) + ', ' + Math.round(coords.svgY);
    });

    map.addEventListener('mouseleave', function() {
      tooltip.style.opacity = '0';
      tooltip.style.visibility = 'hidden';
    });

    map.addEventListener('click', function(e) {
      // Always stop propagation and prevent default to prevent Jaspr DOM reconciliation issues
      e.stopPropagation();
      e.preventDefault();

      if (!_shiftKeyHeld) return;

      var dims = getMapDimensions(map);
      var rect = map.getBoundingClientRect();
      var relX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      var relY = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));

      var coords = calculateCoords(relX, relY, dims);

      var coordText = 'Lat: ' + coords.lat.toFixed(4) + ', Lng: ' + coords.lng.toFixed(4) + ', SVG: (' + Math.round(coords.svgX) + ', ' + Math.round(coords.svgY) + ')';

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(coordText).then(function() {
          showCoordCopiedFeedback(tooltip);
        }).catch(function(err) {
          console.warn('[Arcane] Clipboard write failed:', err);
          fallbackCopyToClipboard(coordText, tooltip);
        });
      } else {
        fallbackCopyToClipboard(coordText, tooltip);
      }
    }, true);
  }

  function fallbackCopyToClipboard(text, tooltip) {
    var textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px';
    document.body.appendChild(textArea);
    textArea.select();
    try {
      document.execCommand('copy');
      showCoordCopiedFeedback(tooltip);
    } catch (err) {
      console.warn('[Arcane] Fallback copy failed:', err);
    }
    document.body.removeChild(textArea);
  }

  function createMapCoordTooltip() {
    var tooltip = document.createElement('div');
    tooltip.className = 'arcane-map-coord-tooltip';
    tooltip.style.cssText = 'position: fixed; z-index: 9999; pointer-events: none; opacity: 0; visibility: hidden; transition: opacity 150ms ease;';

    var inner = document.createElement('div');
    inner.style.cssText = 'background: var(--popover); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.32); padding: 10px 14px;';

    var latDiv = document.createElement('div');
    latDiv.className = 'coord-lat';
    latDiv.style.cssText = 'font-size: 13px; font-family: var(--font-mono); color: var(--popover-foreground); white-space: nowrap; font-weight: 500;';
    latDiv.textContent = 'Lat: 0.0000';

    var lngDiv = document.createElement('div');
    lngDiv.className = 'coord-lng';
    lngDiv.style.cssText = 'font-size: 13px; font-family: var(--font-mono); color: var(--popover-foreground); white-space: nowrap; font-weight: 500;';
    lngDiv.textContent = 'Lng: 0.0000';

    var svgDiv = document.createElement('div');
    svgDiv.className = 'coord-svg';
    svgDiv.style.cssText = 'font-size: 11px; font-family: var(--font-mono); color: var(--muted-foreground); white-space: nowrap; margin-top: 6px;';
    svgDiv.textContent = 'SVG: 0, 0';

    var hintDiv = document.createElement('div');
    hintDiv.className = 'coord-hint';
    hintDiv.style.cssText = 'font-size: 11px; color: var(--primary); margin-top: 6px; font-weight: 500;';
    hintDiv.textContent = 'Click to copy';

    inner.appendChild(latDiv);
    inner.appendChild(lngDiv);
    inner.appendChild(svgDiv);
    inner.appendChild(hintDiv);
    tooltip.appendChild(inner);

    return tooltip;
  }

  function showCoordCopiedFeedback(tooltip) {
    var hint = tooltip.querySelector('.coord-hint');
    if (hint) {
      var original = hint.textContent;
      hint.textContent = 'Copied!';
      hint.style.color = '#22c55e';
      setTimeout(function() {
        hint.textContent = original;
        hint.style.color = '#60a5fa';
      }, 1500);
    }
  }

  // Legacy function name for backward compatibility
  function bindMapDebugMode() {
    bindMapCoordinateMode();
  }

  // ===== MAP PIN TOOLTIPS =====
  function bindMapPinTooltips() {
    // Set up observer for dynamically added maps (only once)
    if (!window._arcanePinTooltipsObserver) {
      window._arcanePinTooltipsObserver = true;
      var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
          mutation.addedNodes.forEach(function(node) {
            if (node.nodeType === 1 && node.querySelectorAll) {
              node.querySelectorAll('.arcane-world-map[data-has-tooltips="true"], .arcane-usa-map[data-has-tooltips="true"]').forEach(function(map) {
                if (!map.dataset.arcaneMapTooltipsBound) {
                  map.dataset.arcaneMapTooltipsBound = 'true';
                  bindPinTooltips(map);
                }
              });
              // Also bind location list items
              node.querySelectorAll('.location-list-item').forEach(function(item) {
                bindLocationListItem(item);
              });
            }
          });
        });
      });
      observer.observe(document.body, { childList: true, subtree: true });
    }

    document.querySelectorAll('.arcane-world-map[data-has-tooltips="true"], .arcane-usa-map[data-has-tooltips="true"]').forEach(function(map) {
      if (map.dataset.arcaneMapTooltipsBound) return;
      map.dataset.arcaneMapTooltipsBound = 'true';
      bindPinTooltips(map);
    });
  }

  function bindPinTooltips(map) {
    var pins = map.querySelectorAll('.arcane-map-pin[data-location]');
    pins.forEach(function(pin) {
      var locationId = pin.getAttribute('data-location');
      if (!locationId) return;

      pin.addEventListener('mouseenter', function() {
        if (_shiftKeyHeld) return;
        highlightMapPin(map, locationId);
      });

      pin.addEventListener('mouseleave', function() {
        unhighlightMapPin(map, locationId);
      });
    });
  }

  function highlightMapPin(map, locationId) {
    var pin = map.querySelector('.arcane-map-pin[data-location="' + locationId + '"]');
    var tooltip = map.querySelector('.arcane-map-tooltip[data-for-location="' + locationId + '"]');

    if (pin) {
      pin.style.transform = 'translate(-50%, -50%) scale(1.5)';
      pin.style.boxShadow = '0 0 20px 10px rgba(34, 197, 94, 0.6)';
    }
    if (tooltip) {
      tooltip.style.opacity = '1';
      tooltip.style.visibility = 'visible';
    }
  }

  function unhighlightMapPin(map, locationId) {
    var pin = map.querySelector('.arcane-map-pin[data-location="' + locationId + '"]');
    var tooltip = map.querySelector('.arcane-map-tooltip[data-for-location="' + locationId + '"]');

    if (pin) {
      pin.style.transform = 'translate(-50%, -50%) scale(1)';
      pin.style.boxShadow = '';
    }
    if (tooltip) {
      tooltip.style.opacity = '0';
      tooltip.style.visibility = 'hidden';
    }
  }

  // ===== LOCATION LIST HOVER =====
  function bindLocationListHover() {
    document.querySelectorAll('.location-list-item').forEach(function(item) {
      bindLocationListItem(item);
    });
  }

  function bindLocationListItem(item) {
    if (item.dataset.arcaneListHoverBound) return;
    item.dataset.arcaneListHoverBound = 'true';

    var itemId = item.getAttribute('id');
    var locationId = itemId ? itemId.replace('location-item-', '') : null;
    if (!locationId) return;

    item.addEventListener('mouseenter', function() {
      if (_shiftKeyHeld) return;
      // Find the closest visible map
      var mapContainer = document.querySelector('.arcane-world-map, .arcane-usa-map');
      if (!mapContainer) return;

      item.style.background = 'var(--arcane-surface-variant, rgba(255,255,255,0.05))';
      highlightMapPin(mapContainer, locationId);
    });

    item.addEventListener('mouseleave', function() {
      var mapContainer = document.querySelector('.arcane-world-map, .arcane-usa-map');
      item.style.background = '';
      if (mapContainer) {
        unhighlightMapPin(mapContainer, locationId);
      }
    });
  }


    // Rainbow theme animation - smooth hue rotation
  // ONLY activates if .neon-rainbow class is already present
  function bindRainbowTheme() {
    let root = document.querySelector('.neon-rainbow');

    // Only proceed if .neon-rainbow class is explicitly set
    if (!root) {
      return; // Do nothing if rainbow theme is not enabled
    }

    if (root.dataset.rainbowBound) return;
    root.dataset.rainbowBound = 'true';

    const docRoot = document.documentElement;
    const duration = 15000; // 15 seconds for very smooth cycle
    let startTime = null;

    // HSL to RGB values (0-255)
    function hslToRgb(h, s, l) {
      s /= 100; l /= 100;
      const a = s * Math.min(l, 1 - l);
      const f = n => {
        const k = (n + h / 30) % 12;
        return Math.round(255 * (l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1)));
      };
      return [f(0), f(8), f(4)];
    }

    function rgbToHex(r, g, b) {
      return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
    }

    function animate(timestamp) {
      if (!startTime) startTime = timestamp;
      const progress = ((timestamp - startTime) % duration) / duration;
      const hue = progress * 360; // Smooth float, not integer

      const isDark = root.classList.contains('dark');
      const s = isDark ? 85 : 75;
      const l = isDark ? 55 : 50;

      const [r, g, b] = hslToRgb(hue, s, l);
      const primary = rgbToHex(r, g, b);
      const [rr, rg, rb] = hslToRgb(hue, s, l - 10);
      const ring = rgbToHex(rr, rg, rb);

      // Set all color variables at once
      docRoot.style.setProperty('--primary', primary);
      docRoot.style.setProperty('--ring', ring);
      docRoot.style.setProperty('--primary-rgb', r + ',' + g + ',' + b);
      requestAnimationFrame(animate);
    }

    requestAnimationFrame(animate);
  }

    function bindCarousels() {
    document.querySelectorAll('.arcane-carousel').forEach(function(carousel) {
      // The duplicate track is visual continuity only. `aria-hidden` on an
      // ancestor does not remove interactive descendants from keyboard focus,
      // and older browsers do not consistently enforce the inert attribute.
      // Neutralize every focus path both at initial bind and after mutations.
      var clone = carousel.querySelector('[data-arcane-carousel-content="clone"]');
      if (clone) {
        clone.setAttribute('aria-hidden', 'true');
        clone.setAttribute('inert', 'true');
        clone.inert = true;
        clone.style.pointerEvents = 'none';
        clone.querySelectorAll(
          'a[href], area[href], button, input, select, textarea, iframe, object, embed, audio[controls], video[controls], summary, [contenteditable], [tabindex]'
        ).forEach(function(element) {
          element.setAttribute('tabindex', '-1');
          element.setAttribute('aria-hidden', 'true');
          element.removeAttribute('autofocus');
          if (element.hasAttribute('contenteditable')) {
            element.setAttribute('contenteditable', 'false');
          }
          if ('disabled' in element) element.disabled = true;
        });
        clone.querySelectorAll('[id]').forEach(function(element) {
          element.removeAttribute('id');
        });
      }

      if (carousel.dataset.arcaneCarouselBound) return;
      carousel.dataset.arcaneCarouselBound = 'true';

      var track = carousel.querySelector('.arcane-carousel-track');
      if (!track) return;

      var isDragging = false;
      var startX = 0;
      var currentTranslateX = 0;
      var dragStartTranslateX = 0;
      var hasInteracted = false;
      var resumeTimer = null;
      var trackWidth = 0;
      var animationDuration = parseInt(carousel.dataset.animationDuration) || 60;
      var resumeDelay = parseInt(carousel.dataset.resumeDelay) || 5000;

      // Velocity tracking for momentum
      var lastX = 0;
      var lastTime = 0;
      var velocity = 0;
      var momentumAnimationId = null;

      function getTrackWidth() {
        return track.scrollWidth / 2;
      }

      function parseTranslateX(transform) {
        if (transform === 'none' || !transform) return 0;
        var match = transform.match(/matrix\(([^)]+)\)/);
        if (match) {
          var values = match[1].split(',');
          if (values.length >= 5) {
            return parseFloat(values[4].trim()) || 0;
          }
        }
        return 0;
      }

      function applyTransform() {
        track.style.transform = 'translateX(' + currentTranslateX + 'px)';
      }

      function wrapPosition() {
        if (trackWidth <= 0) return;
        while (currentTranslateX > 0) {
          currentTranslateX -= trackWidth;
          dragStartTranslateX -= trackWidth;
        }
        while (currentTranslateX < -trackWidth) {
          currentTranslateX += trackWidth;
          dragStartTranslateX += trackWidth;
        }
      }

      function stopMomentum() {
        if (momentumAnimationId) {
          cancelAnimationFrame(momentumAnimationId);
          momentumAnimationId = null;
        }
      }

      function startDrag(clientX) {
        if (resumeTimer) {
          clearTimeout(resumeTimer);
          resumeTimer = null;
        }

        stopMomentum();

        trackWidth = getTrackWidth();

        if (!hasInteracted) {
          var computedStyle = window.getComputedStyle(track);
          currentTranslateX = parseTranslateX(computedStyle.transform);
          hasInteracted = true;
        }

        isDragging = true;
        startX = clientX;
        lastX = clientX;
        lastTime = performance.now();
        velocity = 0;
        dragStartTranslateX = currentTranslateX;

        track.classList.add('dragging');
        track.classList.remove('resuming');
        applyTransform();
      }

      function updateDrag(clientX) {
        if (!isDragging) return;

        var now = performance.now();
        var deltaTime = now - lastTime;

        if (deltaTime > 0) {
          // Calculate velocity (pixels per millisecond)
          velocity = (clientX - lastX) / deltaTime;
        }

        lastX = clientX;
        lastTime = now;

        var deltaX = clientX - startX;
        currentTranslateX = dragStartTranslateX + deltaX;
        wrapPosition();
        applyTransform();
      }

      function animateMomentum() {
        // Apply friction to slow down
        var friction = 0.95;
        velocity *= friction;

        // Stop when velocity is negligible
        if (Math.abs(velocity) < 0.01) {
          stopMomentum();
          scheduleResume();
          return;
        }

        // Move based on velocity (multiply by ~16ms for smooth 60fps)
        currentTranslateX += velocity * 16;
        wrapPosition();
        applyTransform();

        momentumAnimationId = requestAnimationFrame(animateMomentum);
      }

      function scheduleResume() {
        if (resumeTimer) clearTimeout(resumeTimer);
        resumeTimer = setTimeout(resumeAnimation, resumeDelay);
      }

      function endDrag() {
        if (!isDragging) return;
        isDragging = false;

        // If there's significant velocity, start momentum animation
        if (Math.abs(velocity) > 0.1) {
          momentumAnimationId = requestAnimationFrame(animateMomentum);
        } else {
          scheduleResume();
        }
      }

      // Track the current style element for cleanup
      var currentStyleEl = null;

      function resumeAnimation() {
        if (!track) return;

        trackWidth = getTrackWidth();
        if (trackWidth <= 0) return;

        // Normalize position to be within valid range
        wrapPosition();

        // Clean up previous dynamic style if exists
        if (currentStyleEl && currentStyleEl.parentNode) {
          currentStyleEl.parentNode.removeChild(currentStyleEl);
        }

        // Create a unique animation name for this carousel instance
        var animName = 'scroll-carousel-resume-' + Date.now();

        // Inject a keyframe animation that starts from the current position
        currentStyleEl = document.createElement('style');
        currentStyleEl.textContent = '@keyframes ' + animName + ' { from { transform: translateX(' + currentTranslateX + 'px); } to { transform: translateX(' + (currentTranslateX - trackWidth) + 'px); } }';
        document.head.appendChild(currentStyleEl);

        // Apply the animation in a single frame to avoid snap
        // First set the animation, which will immediately start from currentTranslateX
        track.style.animation = 'none';
        track.offsetHeight; // Force reflow
        track.style.transform = '';
        track.style.animation = animName + ' ' + animationDuration + 's linear infinite';

        track.classList.remove('dragging');
        track.classList.remove('resuming');
        hasInteracted = false;
      }

      // Mouse events
      track.addEventListener('mousedown', function(e) {
        e.preventDefault();
        startDrag(e.clientX);
      });

      track.addEventListener('mousemove', function(e) {
        if (isDragging) {
          e.preventDefault();
          updateDrag(e.clientX);
        }
      });

      document.addEventListener('mouseup', function() {
        endDrag();
      });

      // Touch events
      track.addEventListener('touchstart', function(e) {
        if (e.touches.length > 0) {
          startDrag(e.touches[0].clientX);
        }
      }, { passive: true });

      track.addEventListener('touchmove', function(e) {
        if (isDragging && e.touches.length > 0) {
          updateDrag(e.touches[0].clientX);
        }
      }, { passive: true });

      document.addEventListener('touchend', function() {
        endDrag();
      });

      document.addEventListener('touchcancel', function() {
        endDrag();
      });
    });
  }

  // Initial binding
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindCarousels);
  } else {
    bindCarousels();
  }

  // Re-bind on dynamic content changes
  var carouselObserver = new MutationObserver(function() {
    bindCarousels();
  });
  carouselObserver.observe(document.body, { childList: true, subtree: true });

  (function() {
  'use strict';

  const DEFAULT_MAX_COLUMN_SPAN = 4;
  const DEFAULT_MAX_ROW_SPAN = 5;
  const DEFAULT_ROW_DELAY_PENALTY = 0.32;
  const DEFAULT_COLUMN_DRIFT_PENALTY = 0.025;
  const DEFAULT_SEAL_MAX_RATIO_MISMATCH = Math.log(2.4);
  const DEFAULT_BOTTOM_MAX_RATIO_MISMATCH = Math.log(2.1);

  function safeNumber(value, fallback) {
    const parsed = typeof value === 'string'
      ? Number.parseFloat(value)
      : Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  }

  function optionalPositiveNumber(value) {
    const parsed = typeof value === 'string'
      ? Number.parseFloat(value)
      : Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }

  function trackCount(gridTemplateColumns) {
    if (!gridTemplateColumns || gridTemplateColumns === 'none') {
      return 1;
    }
    return gridTemplateColumns
      .trim()
      .split(/\s+/)
      .filter((track) => track && track !== '/')
      .length || 1;
  }

  function firstTrackWidth(gridTemplateColumns, fallback) {
    const firstTrack = (gridTemplateColumns || '').trim().split(/\s+/)[0] || '';
    const parsed = Number.parseFloat(firstTrack);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  }

  function spanDisplayRatio(columnSpan, rowSpan, columnWidth, rowHeight, gap, item = {}) {
    const width = columnSpan * columnWidth + Math.max(0, columnSpan - 1) * gap
      - safeNumber(item.chromeWidth, 0);
    const height = rowSpan * rowHeight + Math.max(0, rowSpan - 1) * gap
      - safeNumber(item.chromeHeight, 0);
    if (width <= 0 || height <= 0) return Infinity;
    return width / Math.max(1, height);
  }

  function layoutOptions(options, columns) {
    const safeColumns = Math.max(
      1,
      Math.floor(safeNumber(columns ?? options.columns, 1)),
    );
    return {
      columns: safeColumns,
      columnWidth: safeNumber(options.columnWidth, 160),
      rowHeight: safeNumber(options.rowHeight, 80),
      gap: safeNumber(options.gap, 0),
      maxColumnSpan: Math.min(
        safeColumns,
        Math.max(1, Math.floor(safeNumber(options.maxColumnSpan, DEFAULT_MAX_COLUMN_SPAN))),
      ),
      maxRowSpan: Math.max(
        1,
        Math.floor(safeNumber(options.maxRowSpan, DEFAULT_MAX_ROW_SPAN)),
      ),
      rowDelayPenalty: safeNumber(options.rowDelayPenalty, DEFAULT_ROW_DELAY_PENALTY),
      columnDriftPenalty: safeNumber(options.columnDriftPenalty, DEFAULT_COLUMN_DRIFT_PENALTY),
      sealMaxRatioMismatch: safeNumber(
        options.sealMaxRatioMismatch,
        DEFAULT_SEAL_MAX_RATIO_MISMATCH,
      ),
      bottomMaxRatioMismatch: safeNumber(
        options.bottomMaxRatioMismatch,
        DEFAULT_BOTTOM_MAX_RATIO_MISMATCH,
      ),
      minimumTileArea: optionalPositiveNumber(options.minimumTileArea),
      targetTileArea: optionalPositiveNumber(options.targetTileArea),
    };
  }

  function spanRatioScore(aspectRatio, columnSpan, rowSpan, layout, item = {}) {
    const displayRatio = spanDisplayRatio(
      columnSpan,
      rowSpan,
      layout.columnWidth,
      layout.rowHeight,
      layout.gap,
      item,
    );
    return Math.abs(Math.log(displayRatio / aspectRatio));
  }

  function targetAreaFor(aspectRatio) {
    if (aspectRatio >= 1.9 || aspectRatio <= 0.7) {
      return 6;
    }
    if (aspectRatio >= 1.35 || aspectRatio <= 0.9) {
      return 5;
    }
    return 4;
  }

  function galleryCandidateSpans(item, options = {}) {
    const layout = layoutOptions(options, options.columns);
    const aspectRatio = safeNumber(item.aspectRatio, 1);
    const targetArea = layout.targetTileArea ?? targetAreaFor(aspectRatio);
    const candidates = [];
    const undersizedCandidates = [];

    for (let columnSpan = 1; columnSpan <= layout.maxColumnSpan; columnSpan++) {
      for (let rowSpan = 1; rowSpan <= layout.maxRowSpan; rowSpan++) {
        const displayRatio = spanDisplayRatio(
          columnSpan,
          rowSpan,
          layout.columnWidth,
          layout.rowHeight,
          layout.gap,
          item,
        );
        if (!Number.isFinite(displayRatio)) continue;
        const ratioScore = Math.abs(Math.log(displayRatio / aspectRatio));
        const area = columnSpan * rowSpan;
        let score = ratioScore * 4 + Math.abs(area - targetArea) * 0.12;
        if (columnSpan === 1 && rowSpan === 1) {
          score += 1.2;
        } else if (columnSpan === 1 && layout.columns > 3) {
          score += 0.25;
        }
        if (columnSpan === layout.columns && layout.columns > 3) {
          score += 0.5;
        }
        const candidate = {
          columnSpan,
          rowSpan,
          score,
          ratioScore,
          displayRatio,
        };
        if (layout.minimumTileArea !== null && area < layout.minimumTileArea) {
          undersizedCandidates.push(candidate);
        } else {
          candidates.push(candidate);
        }
      }
    }

    // A narrow layout may make the requested floor impossible. Prefer the
    // largest attainable spans instead of letting placement fall back to 1×1.
    if (candidates.length === 0 && undersizedCandidates.length > 0) {
      const largestArea = Math.max(
        ...undersizedCandidates.map(
          (candidate) => candidate.columnSpan * candidate.rowSpan,
        ),
      );
      candidates.push(
        ...undersizedCandidates.filter(
          (candidate) =>
            candidate.columnSpan * candidate.rowSpan === largestArea,
        ),
      );
    }

    candidates.sort((a, b) =>
      a.score - b.score ||
      a.ratioScore - b.ratioScore ||
      b.columnSpan - a.columnSpan ||
      a.rowSpan - b.rowSpan,
    );
    return candidates;
  }

  function galleryJustifiedRows(items, options = {}) {
    const width = safeNumber(options.width, 1);
    const gap = safeNumber(options.gap, 0);
    const targetHeight = safeNumber(options.targetHeight, 280);
    const maxHeight = safeNumber(options.maxHeight, 380);
    const rows = [];
    for (let start = 0; start < items.length;) {
      if (width <= 600) {
        const item = items[start];
        rows.push({
          start, count: 1,
          height: Math.max(1, width - safeNumber(item.chromeWidth, 0)) /
            safeNumber(item.aspectRatio, 1),
          widths: [width],
        });
        start++;
        continue;
      }

      let best = null;
      let ratioSum = 0;
      let chromeSum = 0;
      let minimumHeight = 0;
      for (let count = 1; count <= Math.min(6, items.length - start); count++) {
        const item = items[start + count - 1];
        ratioSum += safeNumber(item.aspectRatio, 1);
        chromeSum += safeNumber(item.chromeWidth, 0);
        minimumHeight = Math.max(minimumHeight,
          (safeNumber(item.minimumWidth, 0) - safeNumber(item.chromeWidth, 0)) /
            safeNumber(item.aspectRatio, 1));
        const availableWidth = width - chromeSum - (count - 1) * gap;
        if (availableWidth <= 0) break;
        const lastRow = start + count === items.length;
        const height = Math.min(
          availableWidth / ratioSum,
          lastRow ? Math.min(maxHeight, Math.max(targetHeight, minimumHeight)) : maxHeight,
        );
        const usedWidth = height * ratioSum + chromeSum + (count - 1) * gap;
        const score = Math.abs(Math.log(height / targetHeight)) +
          Math.max(0, 1 - usedWidth / width) * 0.5;
        const fitsMinimumWidth = height >= minimumHeight;
        if (!best || (fitsMinimumWidth && !best.fitsMinimumWidth) ||
            (fitsMinimumWidth === best.fitsMinimumWidth && score < best.score)) {
          best = { start, count, height, score, fitsMinimumWidth };
        }
      }
      if (!best) break;
      rows.push({
        start, count: best.count, height: best.height,
        widths: items.slice(start, start + best.count).map((item) =>
          best.height * safeNumber(item.aspectRatio, 1) + safeNumber(item.chromeWidth, 0)),
      });
      start += best.count;
    }
    return rows;
  }

  function makeRow(columns) {
    return Array.from({ length: columns }, () => false);
  }

  function ensureRow(occupied, row, columns) {
    while (occupied.length <= row) {
      occupied.push(makeRow(columns));
    }
  }

  function isFree(occupied, row, column, columnSpan, rowSpan, columns) {
    if (column + columnSpan > columns) {
      return false;
    }
    for (let rowOffset = 0; rowOffset < rowSpan; rowOffset++) {
      ensureRow(occupied, row + rowOffset, columns);
      for (let columnOffset = 0; columnOffset < columnSpan; columnOffset++) {
        if (occupied[row + rowOffset][column + columnOffset]) {
          return false;
        }
      }
    }
    return true;
  }

  function occupy(occupied, placement, columns) {
    for (let rowOffset = 0; rowOffset < placement.rowSpan; rowOffset++) {
      ensureRow(occupied, placement.row + rowOffset, columns);
      for (let columnOffset = 0; columnOffset < placement.columnSpan; columnOffset++) {
        occupied[placement.row + rowOffset][placement.column + columnOffset] = true;
      }
    }
  }

  function ownerRowsFor(placements, columns) {
    const rows = [];
    for (let index = 0; index < placements.length; index++) {
      const placement = placements[index];
      for (let rowOffset = 0; rowOffset < placement.rowSpan; rowOffset++) {
        const row = placement.row + rowOffset;
        rows[row] ??= Array.from({ length: columns }, () => -1);
        for (let columnOffset = 0; columnOffset < placement.columnSpan; columnOffset++) {
          rows[row][placement.column + columnOffset] = index;
        }
      }
    }
    return rows.map((row) => row ?? Array.from({ length: columns }, () => -1));
  }

  function canExtendDown(ownerRows, placement, row) {
    const targetRow = ownerRows[row];
    if (!targetRow) {
      return false;
    }
    for (let column = placement.column; column < placement.column + placement.columnSpan; column++) {
      if (targetRow[column] !== -1) {
        return false;
      }
    }
    return true;
  }

  function canExpandRight(ownerRows, placement, column) {
    for (let row = placement.row; row < placement.row + placement.rowSpan; row++) {
      if (ownerRows[row]?.[column] !== -1) {
        return false;
      }
    }
    return true;
  }

  function canExpandLeft(ownerRows, placement, column) {
    for (let row = placement.row; row < placement.row + placement.rowSpan; row++) {
      if (ownerRows[row]?.[column] !== -1) {
        return false;
      }
    }
    return true;
  }

  function canExtendUp(ownerRows, placement, row) {
    const targetRow = ownerRows[row];
    if (!targetRow) {
      return false;
    }
    for (let column = placement.column; column < placement.column + placement.columnSpan; column++) {
      if (targetRow[column] !== -1) {
        return false;
      }
    }
    return true;
  }

  function expansionCandidate(placements, items, index, nextPlacement, layout, maxRatioMismatch) {
    if (index < 0 || index >= placements.length) {
      return null;
    }
    if (
      nextPlacement.column < 0 ||
      nextPlacement.column + nextPlacement.columnSpan > layout.columns ||
      nextPlacement.columnSpan > layout.maxColumnSpan ||
      nextPlacement.rowSpan > layout.maxRowSpan + 3
    ) {
      return null;
    }

    const item = items[index] ?? {};
    const aspectRatio = safeNumber(item.aspectRatio, 1);
    const current = placements[index];
    const currentScore = spanRatioScore(
      aspectRatio,
      current.columnSpan,
      current.rowSpan,
      layout,
      item,
    );
    const nextScore = spanRatioScore(
      aspectRatio,
      nextPlacement.columnSpan,
      nextPlacement.rowSpan,
      layout,
      item,
    );
    const allowedMismatch = Math.max(currentScore + 0.08, maxRatioMismatch);
    if (nextScore > allowedMismatch) {
      return null;
    }

    const sizeDelta =
      Math.max(0, nextPlacement.columnSpan - current.columnSpan) +
      Math.max(0, nextPlacement.rowSpan - current.rowSpan);
    return {
      index,
      placement: nextPlacement,
      score: nextScore + sizeDelta * 0.02,
    };
  }

  function bestExpansionForHole(
    ownerRows,
    placements,
    items,
    row,
    column,
    layout,
    maxRatioMismatch,
    allowBelow,
  ) {
    const candidates = [];
    const columns = layout.columns;

    const aboveIndex = row > 0 ? ownerRows[row - 1]?.[column] : -1;
    if (aboveIndex >= 0) {
      const placement = placements[aboveIndex];
      if (
        placement.row + placement.rowSpan === row &&
        canExtendDown(ownerRows, placement, row)
      ) {
        const candidate = expansionCandidate(
          placements,
          items,
          aboveIndex,
          { ...placement, rowSpan: placement.rowSpan + 1 },
          layout,
          maxRatioMismatch,
        );
        if (candidate) candidates.push(candidate);
      }
    }

    const leftIndex = column > 0 ? ownerRows[row][column - 1] : -1;
    if (leftIndex >= 0) {
      const placement = placements[leftIndex];
      if (
        placement.column + placement.columnSpan === column &&
        canExpandRight(ownerRows, placement, column)
      ) {
        const candidate = expansionCandidate(
          placements,
          items,
          leftIndex,
          { ...placement, columnSpan: placement.columnSpan + 1 },
          layout,
          maxRatioMismatch,
        );
        if (candidate) candidates.push(candidate);
      }
    }

    const rightIndex = column + 1 < columns ? ownerRows[row][column + 1] : -1;
    if (rightIndex >= 0) {
      const placement = placements[rightIndex];
      if (
        placement.column === column + 1 &&
        canExpandLeft(ownerRows, placement, column)
      ) {
        const candidate = expansionCandidate(
          placements,
          items,
          rightIndex,
          {
            ...placement,
            column,
            columnSpan: placement.columnSpan + 1,
          },
          layout,
          maxRatioMismatch,
        );
        if (candidate) candidates.push(candidate);
      }
    }

    const belowIndex = allowBelow && row + 1 < ownerRows.length
      ? ownerRows[row + 1][column]
      : -1;
    if (belowIndex >= 0) {
      const placement = placements[belowIndex];
      if (
        placement.row === row + 1 &&
        canExtendUp(ownerRows, placement, row)
      ) {
        const candidate = expansionCandidate(
          placements,
          items,
          belowIndex,
          {
            ...placement,
            row,
            rowSpan: placement.rowSpan + 1,
          },
          layout,
          maxRatioMismatch,
        );
        if (candidate) candidates.push(candidate);
      }
    }

    candidates.sort((a, b) => a.score - b.score);
    return candidates[0] ?? null;
  }

  function applyExpansion(placements, expansion) {
    const placement = placements[expansion.index];
    placement.row = expansion.placement.row;
    placement.column = expansion.placement.column;
    placement.columnSpan = expansion.placement.columnSpan;
    placement.rowSpan = expansion.placement.rowSpan;
  }

  function sealInteriorHoles(placements, items, layout) {
    let changed = true;
    let guard = placements.length * layout.columns * 8;

    while (changed && guard-- > 0) {
      changed = false;
      const ownerRows = ownerRowsFor(placements, layout.columns);
      const lastRow = ownerRows.length - 1;

      for (let row = 0; row < lastRow && !changed; row++) {
        for (let column = 0; column < layout.columns; column++) {
          if (ownerRows[row][column] !== -1) {
            continue;
          }

          const expansion = bestExpansionForHole(
            ownerRows,
            placements,
            items,
            row,
            column,
            layout,
            layout.sealMaxRatioMismatch,
            true,
          );
          if (!expansion) {
            continue;
          }

          applyExpansion(placements, expansion);
          changed = true;
          break;
        }
      }
    }

    return placements;
  }

  function squareOffBottom(placements, items, layout) {
    let changed = true;
    let guard = placements.length * layout.columns * 2;

    while (changed && guard-- > 0) {
      changed = false;
      const ownerRows = ownerRowsFor(placements, layout.columns);
      const lastRow = ownerRows.length - 1;

      for (let column = 0; column < layout.columns; column++) {
        if (ownerRows[lastRow]?.[column] !== -1) {
          continue;
        }

        const expansion = bestExpansionForHole(
          ownerRows,
          placements,
          items,
          lastRow,
          column,
          layout,
          layout.bottomMaxRatioMismatch,
          false,
        );
        if (!expansion) {
          continue;
        }

        applyExpansion(placements, expansion);
        changed = true;
        break;
      }
    }

    return placements;
  }

  function firstEmptyCell(occupied, columns) {
    for (let row = 0; row < occupied.length; row++) {
      for (let column = 0; column < columns; column++) {
        if (!occupied[row][column]) {
          return { row, column };
        }
      }
    }
    return { row: occupied.length, column: 0 };
  }

  function lastOccupiedRow(occupied) {
    for (let row = occupied.length - 1; row >= 0; row--) {
      if (occupied[row].some(Boolean)) {
        return row;
      }
    }
    return -1;
  }

  function galleryPack(items, options = {}) {
    const layout = layoutOptions(options, options.columns);
    const usesOwnerSizing =
      layout.minimumTileArea !== null ||
      layout.targetTileArea !== null;
    const columns = layout.columns;
    const occupied = [];
    const placements = [];
    for (const item of items) {
      const candidates = galleryCandidateSpans(
        item,
        { ...options, columns },
      );
      const placement = usesOwnerSizing
        ? bestFrontierPlacementForItem(occupied, candidates, layout)
        : bestPlacementForItem(occupied, candidates, layout);
      occupy(occupied, placement, columns);
      placements.push(placement);
    }

    if (usesOwnerSizing) {
      return placements;
    }

    return squareOffBottom(
      sealInteriorHoles(placements, items, layout),
      items,
      layout,
    );
  }

  function bestFrontierPlacementForItem(occupied, candidates, layout) {
    const firstCell = firstEmptyCell(occupied, layout.columns);

    for (const candidate of candidates) {
      const area = candidate.columnSpan * candidate.rowSpan;
      if (
        layout.minimumTileArea !== null &&
        area < layout.minimumTileArea
      ) {
        continue;
      }
      if (firstCell.column + candidate.columnSpan > layout.columns) {
        continue;
      }
      if (
        !isFree(
          occupied,
          firstCell.row,
          firstCell.column,
          candidate.columnSpan,
          candidate.rowSpan,
          layout.columns,
        )
      ) {
        continue;
      }

      return {
        row: firstCell.row,
        column: firstCell.column,
        columnSpan: candidate.columnSpan,
        rowSpan: candidate.rowSpan,
      };
    }

    return {
      row: firstCell.row,
      column: firstCell.column,
      columnSpan: 1,
      rowSpan: Math.max(1, Math.ceil(layout.minimumTileArea ?? 1)),
    };
  }

  function bestPlacementForItem(occupied, candidates, layout) {
    const firstCell = firstEmptyCell(occupied, layout.columns);
    const maxSearchRow = Math.max(
      firstCell.row + layout.maxRowSpan + layout.columns + 4,
      lastOccupiedRow(occupied) + 1,
    );
    let best = null;

    for (const candidate of candidates) {
      const maxColumn = layout.columns - candidate.columnSpan;
      for (let row = firstCell.row; row <= maxSearchRow; row++) {
        const startColumn = row === firstCell.row ? firstCell.column : 0;
        for (let column = startColumn; column <= maxColumn; column++) {
          if (
            !isFree(
              occupied,
              row,
              column,
              candidate.columnSpan,
              candidate.rowSpan,
              layout.columns,
            )
          ) {
            continue;
          }

          const rowDelay = row - firstCell.row;
          const columnDrift = row === firstCell.row
            ? Math.abs(column - firstCell.column)
            : column;
          const score =
            candidate.score +
            rowDelay * layout.rowDelayPenalty +
            columnDrift * layout.columnDriftPenalty;
          if (!best || score < best.score) {
            best = {
              row,
              column,
              columnSpan: candidate.columnSpan,
              rowSpan: candidate.rowSpan,
              score,
            };
          }
        }
      }
    }

    if (best) {
      return {
        row: best.row,
        column: best.column,
        columnSpan: best.columnSpan,
        rowSpan: best.rowSpan,
      };
    }

    const fallback = candidates[0] ?? { columnSpan: 1, rowSpan: 1 };
    return {
      // The scored search always includes the first wholly empty row, so this
      // branch is only defensive. Keep it below every occupied cell instead of
      // forcing a multi-cell tile into a fragmented first gap.
      row: lastOccupiedRow(occupied) + 1,
      column: 0,
      columnSpan: Math.min(fallback.columnSpan, layout.columns),
      rowSpan: fallback.rowSpan,
    };
  }

  // Pure mapping from a placement to its CSS grid-line values.
  function galleryPlacementStyle(placement) {
    return {
      gridColumn: `${placement.column + 1} / span ${placement.columnSpan}`,
      gridRow: `${placement.row + 1} / span ${placement.rowSpan}`,
    };
  }

  // The grid-column value galleryPlacementStyle writes.
  const PACKED_COLUMN = /^\d+ \/ span \d+$/;

  function resetPlacementsForMeasurement(
    elements,
    previousWidth,
    currentWidth,
  ) {
    const isFirstMeasurement = !Number.isFinite(previousWidth);
    const widthShrank = currentWidth < previousWidth - 0.5;
    // A framework re-sync (Jaspr diffing) can rewrite an item's inline style
    // back to its rendered span-only form at an unchanged width. Those spans
    // can also address implicit tracks, so treat any item without a packed
    // `N / span M` placement like a first measurement.
    const placementReplaced = elements.some(
      (element) => !PACKED_COLUMN.test(element.style.gridColumn || ''),
    );
    if (!isFirstMeasurement && !widthShrank && !placementReplaced) {
      return false;
    }

    for (const element of elements) {
      // A placement written for a wider grid can address a column that no longer
      // exists. CSS then creates an implicit sliver track, and the resolved
      // gridTemplateColumns value includes that track. Neutralize our placements
      // while measuring so the packer sees only the current explicit grid.
      element.style.gridColumn = 'auto';
      element.style.gridRow = 'auto';
    }
    return true;
  }

  const lastMeasuredWidth = typeof WeakMap === 'function' ? new WeakMap() : null;

  function setLayoutProperty(element, property, value) {
    if (element.style.getPropertyValue(property) === value) return;
    if (value) element.style.setProperty(property, value);
    else element.style.removeProperty(property);
  }

  function styleNumber(style, property) {
    return Number.parseFloat(style.getPropertyValue(property)) || 0;
  }

  function boxInsets(style) {
    return {
      horizontal: styleNumber(style, 'border-left-width') + styleNumber(style, 'border-right-width') +
        styleNumber(style, 'padding-left') + styleNumber(style, 'padding-right'),
      vertical: styleNumber(style, 'border-top-width') + styleNumber(style, 'border-bottom-width') +
        styleNumber(style, 'padding-top') + styleNumber(style, 'padding-bottom'),
    };
  }

  function layoutHeight(element, style) {
    const height = Number.parseFloat(style.height);
    if (!Number.isFinite(height)) return element.offsetHeight;
    return height + (style.boxSizing === 'border-box' ? 0 : boxInsets(style).vertical);
  }

  function applyJustifiedRows(container) {
    const style = window.getComputedStyle(container);
    // Computed CSS widths retain fractions without inheriting visual transforms.
    const width = styleNumber(style, 'width') -
      (style.boxSizing === 'border-box' ? boxInsets(style).horizontal : 0);
    if (width <= 0) return;
    const items = Array.from(container.children)
      .filter((element) => element.hasAttribute('data-arcane-gallery-item'))
      .map((element) => {
        const media = element.querySelector('[data-arcane-gallery-media]');
        const frameStyle = window.getComputedStyle(element);
        const frameInsets = boxInsets(frameStyle);
        const mediaInsets = media ? boxInsets(window.getComputedStyle(media)) : { horizontal: 0, vertical: 0 };
        return {
          element, media, frameStyle, frameInsets, mediaInsets,
          aspectRatio: safeNumber(element.dataset.ratio, 1),
          chromeWidth: frameInsets.horizontal + mediaInsets.horizontal,
          minimumWidth: element.querySelector('[data-arcane-gallery-caption]') ? 180 : 0,
        };
      });
    const gap = styleNumber(style, 'column-gap');
    const rowGap = styleNumber(style, 'row-gap');
    const clearance = styleNumber(style, '--arcane-gallery-decoration-clearance');
    const horizontalRoom = Math.max(0, Math.min(gap / 2,
      styleNumber(style, 'padding-left'), styleNumber(style, 'padding-right')) - clearance - .5);
    const verticalRoom = Math.max(0, Math.min(rowGap / 2,
      styleNumber(style, 'padding-top'), styleNumber(style, 'padding-bottom')) - clearance - .5);
    const maxHeight = items.some((item) => item.minimumWidth > 0) ? 600 : 380;
    const rows = galleryJustifiedRows(items, { width, gap, maxHeight });

    for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
      const row = rows[rowIndex];
      const imageHeight = Math.max(1 / 64, Math.floor(row.height * 64) / 64);
      const rowItems = items.slice(row.start, row.start + row.count);
      const panelWidths = row.widths.map((panelWidth) => Math.floor(panelWidth * 64) / 64);
      const rowWidth = panelWidths.reduce((sum, panelWidth) => sum + panelWidth, 0) +
        (row.count - 1) * gap;
      for (let index = 0; index < rowItems.length; index++) {
        const item = rowItems[index];
        // Round down to the browser's layout unit so a full row cannot wrap
        // its last panel because individual flex widths rounded upward.
        setLayoutProperty(item.element, 'width', panelWidths[index] + 'px');
        // Reserve a capped row's unused space without adding nodes that would
        // conflict with framework reconciliation or change the reading order.
        setLayoutProperty(item.element, 'margin-inline-end', index === rowItems.length - 1
          ? 'max(0px, calc(100% - ' + rowWidth + 'px))'
          : '0px');
        if (item.media) {
          setLayoutProperty(item.media, 'height', (imageHeight + item.mediaInsets.vertical) + 'px');
          setLayoutProperty(item.media, 'aspect-ratio', 'auto');
        }
      }

      let chromeHeight = 0;
      for (const item of rowItems) {
        let extra = item.frameInsets.vertical + item.mediaInsets.vertical;
        const children = Array.from(item.element.children);
        extra += Math.max(0, children.length - 1) * styleNumber(item.frameStyle, 'row-gap');
        for (const child of children) {
          const childStyle = window.getComputedStyle(child);
          extra += styleNumber(childStyle, 'margin-top') + styleNumber(childStyle, 'margin-bottom');
          if (child !== item.media) extra += layoutHeight(child, childStyle);
        }
        chromeHeight = Math.max(chromeHeight, extra);
      }
      for (let index = 0; index < rowItems.length; index++) {
        const item = rowItems[index];
        const panelHeight = imageHeight + chromeHeight;
        setLayoutProperty(item.element, 'height', panelHeight + 'px');
        const desiredAngle = styleNumber(item.frameStyle, '--arcane-gallery-panel-angle');
        let lower = 0;
        let upper = Math.abs(desiredAngle);
        // Reserve the rotated ink and focus bounds without scaling the artwork.
        for (let step = 0; step < 16; step++) {
          const angle = (lower + upper) / 2;
          const radians = angle * Math.PI / 180;
          const expansionX = (panelWidths[index] * (Math.cos(radians) - 1) +
            panelHeight * Math.sin(radians)) / 2;
          const expansionY = (panelHeight * (Math.cos(radians) - 1) +
            panelWidths[index] * Math.sin(radians)) / 2;
          if (expansionX <= horizontalRoom && expansionY <= verticalRoom) lower = angle;
          else upper = angle;
        }
        const rotation = Math.sign(desiredAngle) * Math.floor(lower * 1000) / 1000;
        setLayoutProperty(item.element, '--arcane-gallery-panel-rotation', rotation + 'deg');
        item.element.dataset.arcaneGalleryRow = String(rowIndex);
        item.element.dataset.arcaneGalleryDisplayRatio = String(item.aspectRatio);
        item.element.dataset.arcaneGalleryRatioMismatch = '0';
      }
    }
    container.classList.add('is-arcane-gallery-ready');
  }

  function applyMasonry(container) {
    if (!container || container.clientWidth <= 0) {
      return;
    }
    if (container.dataset.arcaneGalleryComposition === 'justified') {
      applyJustifiedRows(container);
      return;
    }

    const elements = Array.from(container.children).filter((element) =>
      element.hasAttribute('data-arcane-gallery-item'),
    );
    const currentWidth = container.clientWidth;
    resetPlacementsForMeasurement(
      elements,
      lastMeasuredWidth?.get(container),
      currentWidth,
    );
    lastMeasuredWidth?.set(container, currentWidth);

    const styles = window.getComputedStyle(container);
    const columns = trackCount(styles.gridTemplateColumns);
    const columnWidth = firstTrackWidth(
      styles.gridTemplateColumns,
      container.clientWidth / columns,
    );
    const rowHeight = safeNumber(
      styles.getPropertyValue('--arcane-gallery-row') || styles.gridAutoRows,
      80,
    );
    const gap = safeNumber(styles.rowGap, 0);
    const maxColumnSpan = safeNumber(
      container.dataset.arcaneGalleryMaxColumnSpan,
      DEFAULT_MAX_COLUMN_SPAN,
    );
    const maxRowSpan = safeNumber(
      container.dataset.arcaneGalleryMaxRowSpan,
      DEFAULT_MAX_ROW_SPAN,
    );
    const minimumTileArea = optionalPositiveNumber(
      container.dataset.arcaneGalleryMinimumTileArea,
    );
    const targetTileArea = optionalPositiveNumber(
      container.dataset.arcaneGalleryTargetTileArea,
    );
    const layout = layoutOptions(
      {
        columns,
        columnWidth,
        rowHeight,
        gap,
        maxColumnSpan,
        maxRowSpan,
        minimumTileArea,
        targetTileArea,
      },
      columns,
    );
    const items = elements.map((element) => {
      const item = { aspectRatio: safeNumber(element.dataset.ratio, 1) };
      if (element.dataset.arcaneGalleryMeasureMedia === 'true') {
        const media = element.querySelector('[data-arcane-gallery-media]');
        if (media) {
          const frameBounds = element.getBoundingClientRect();
          const mediaBounds = media.getBoundingClientRect();
          item.chromeHeight = Math.max(0, frameBounds.height - mediaBounds.height);
          item.chromeWidth = Math.max(0, frameBounds.width - mediaBounds.width);
        }
      }
      return item;
    });
    const placements = galleryPack(items, {
      columns,
      columnWidth,
      rowHeight,
      gap,
      maxColumnSpan,
      maxRowSpan,
      minimumTileArea: layout.minimumTileArea,
      targetTileArea: layout.targetTileArea,
    });

    for (let index = 0; index < elements.length; index++) {
      const placement = placements[index];
      const element = elements[index];
      const { gridColumn, gridRow } = galleryPlacementStyle(placement);
      // Skip-detection MUST read the live inline style, not a cache: framework
      // re-renders (Jaspr diffing) wipe the inline styles between repacks, and
      // a stale "already written" cache leaves those cells permanently unplaced
      // (1x1 auto-flow tiles + holes after infinite-scroll appends). This live
      // comparison is also the loop-breaker for the document MutationObserver:
      // our own style writes fire style mutations, but the echo repack finds the
      // styles already correct and writes nothing, so the observer settles.
      const displayRatio = spanDisplayRatio(
        placement.columnSpan,
        placement.rowSpan,
        columnWidth,
        rowHeight,
        gap,
        items[index],
      );
      const ratioMismatch = Math.abs(
        Math.log(displayRatio / safeNumber(items[index]?.aspectRatio, 1)),
      );
      if (element.style.gridColumn !== gridColumn) {
        element.style.gridColumn = gridColumn;
      }
      if (element.style.gridRow !== gridRow) {
        element.style.gridRow = gridRow;
      }
      element.dataset.arcaneGalleryColumnSpan = String(placement.columnSpan);
      element.dataset.arcaneGalleryRowSpan = String(placement.rowSpan);
      element.dataset.arcaneGalleryDisplayRatio = displayRatio.toFixed(4);
      element.dataset.arcaneGalleryRatioMismatch = ratioMismatch.toFixed(4);
      element.dataset.arcaneGalleryFit ||= 'cover';
    }

    container.classList.add('is-arcane-gallery-ready');
  }

  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONTAINER_SELECTOR = '[data-arcane-gallery][data-packing="true"], [data-arcane-gallery][data-arcane-gallery-composition="justified"]';
  const CONTENT_SELECTOR = '[data-arcane-gallery-caption], [data-arcane-gallery-footer]';
  const scheduled = new WeakSet();
  const observed = new WeakSet();
  const resizeObserver = 'ResizeObserver' in window
    ? new ResizeObserver((entries) => {
        for (const entry of entries) {
          schedule(containerFor(entry.target));
        }
      })
    : null;

  function schedule(container) {
    if (!container || scheduled.has(container)) {
      return;
    }
    scheduled.add(container);
    window.requestAnimationFrame(() => {
      scheduled.delete(container);
      applyMasonry(container);
    });
  }

  function observeSize(element) {
    if (observed.has(element)) return;
    observed.add(element);
    resizeObserver?.observe(element);
  }

  function observeContent(node) {
    if (node.matches?.(CONTENT_SELECTOR) && containerFor(node)) observeSize(node);
    node.querySelectorAll?.(CONTENT_SELECTOR).forEach((element) => {
      if (containerFor(element)) observeSize(element);
    });
  }

  function unobserveRemoved(node) {
    const elements = [node, ...node.querySelectorAll(CONTAINER_SELECTOR + ', ' + CONTENT_SELECTOR)];
    for (const element of elements) {
      if (!observed.has(element) || (element.isConnected && containerFor(element))) continue;
      resizeObserver?.unobserve(element);
      observed.delete(element);
      if (element.matches?.(CONTAINER_SELECTOR)) delete element.dataset.arcaneGalleryObserved;
    }
  }

  function observe(container) {
    if (!container) return;
    observeContent(container);
    observeSize(container);
    container.dataset.arcaneGalleryObserved = 'true';
    schedule(container);
  }

  function containerFor(node) {
    if (!node) {
      return null;
    }
    const element = node.nodeType === 1 ? node : node.parentElement;
    return element && element.closest
      ? element.closest(CONTAINER_SELECTOR)
      : null;
  }

  function refreshAll() {
    document
      .querySelectorAll(CONTAINER_SELECTOR)
      .forEach((container) => observe(container));
  }

  // Single document-level observer. childList mutations surface newly-added
  // packing containers and newly-added items (infinite-scroll appends);
  // attribute mutations surface Jaspr re-syncing inline styles (which wipes our
  // placements). Both are coalesced through schedule() -> rAF. Our own writes
  // are neutralized by the live-style skip-detection in applyMasonry, so the
  // observer settles instead of looping.
  function handleMutations(mutations) {
    for (const mutation of mutations) {
      if (mutation.type === 'childList') {
        for (const node of mutation.removedNodes) {
          if (node?.nodeType === 1) unobserveRemoved(node);
        }
        for (const node of mutation.addedNodes) {
          if (!node || node.nodeType !== 1) {
            continue;
          }
          if (node.matches && node.matches(CONTAINER_SELECTOR)) {
            observe(node);
          }
          if (node.querySelectorAll) {
            node
              .querySelectorAll(CONTAINER_SELECTOR)
              .forEach((container) => observe(container));
          }
          observeContent(node);
        }
        const container = containerFor(mutation.target);
        if (container) {
          schedule(container);
        }
      } else if (mutation.type === 'attributes') {
        const container = containerFor(mutation.target);
        if (container) {
          // Drag offsets are CSS custom properties on gallery items. Repacking
          // the complete masonry grid on every pointermove causes avoidable
          // layout work and visible jank. The drag runtime explicitly requests
          // one final pack when the gesture finishes.
          if (
            mutation.attributeName === 'style' &&
            container.classList.contains('is-arcane-gallery-drag-active') &&
            mutation.target.matches?.('[data-arcane-draggable-item="true"]')
          ) {
            continue;
          }
          schedule(container);
        }
      }
    }
  }

  const documentObserver = 'MutationObserver' in window
    ? new MutationObserver(handleMutations)
    : null;

  function startDocumentObserver() {
    documentObserver?.observe(document.documentElement || document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: [
        'style',
        'data-ratio',
        'data-packing',
        'data-arcane-gallery-composition',
        'data-arcane-gallery-minimum-tile-area',
        'data-arcane-gallery-target-tile-area',
      ],
    });
  }

  window.ArcaneGallery = {
    observe,
    refresh: refreshAll,
    refreshAll,
    pack: galleryPack,
    justifiedRows: galleryJustifiedRows,
    candidateSpans: galleryCandidateSpans,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      startDocumentObserver();
      refreshAll();
    }, { once: true });
  } else {
    startDocumentObserver();
    refreshAll();
  }
})();

  (function() {
  'use strict';

  const GALLERY_SELECTOR =
    '[data-arcane-gallery][data-arcane-gallery-draggable="true"]';
  const ITEM_SELECTOR = '[data-arcane-draggable-item="true"]';
  const HANDLE_SELECTOR = '[data-arcane-drag-handle="true"]';
  const DRAG_THRESHOLD_PX = 6;
  const CLICK_SUPPRESSION_MS = 500;
  // Windows 95 dragged windows in OUTLINE mode by default: the window itself
  // stayed put while a single wireframe of its bounds tracked the pointer, and
  // the move committed once, on release. Galleries inside the win95 theme use
  // that model; every other theme keeps the live full-content translate.
  const OUTLINE_THEME_SELECTOR = '.arcane-theme-win95';
  const OUTLINE_CLASS = 'arcane-gallery-drag-outline';
  // Win95's SM_CXDRAG / SM_CYDRAG: 4 pixels, compared per axis rather than as
  // a radius, so a straight nudge starts the drag as readily as a diagonal one.
  const OUTLINE_DRAG_THRESHOLD_PX = 4;
  // Pressing a caption ACTIVATED that window, flipping its caption to the
  // active color and every other window's to the inactive one.
  const OUTLINE_ACTIVE_ATTRIBUTE = 'data-w95-active';
  const INTERACTIVE_SELECTOR =
    'a[href],button,input,textarea,select,option,summary,[contenteditable="true"],' +
    '[role="button"],[role="link"],[tabindex]';
  const offsetsByGallery = new WeakMap();
  const offsetsByGalleryId = new Map();

  function finiteNumber(value, fallback) {
    const parsed = Number.parseFloat(String(value ?? ''));
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function crossedThreshold(deltaX, deltaY) {
    return Math.hypot(deltaX, deltaY) >= DRAG_THRESHOLD_PX;
  }

  function crossedOutlineThreshold(deltaX, deltaY) {
    return (
      Math.abs(deltaX) >= OUTLINE_DRAG_THRESHOLD_PX ||
      Math.abs(deltaY) >= OUTLINE_DRAG_THRESHOLD_PX
    );
  }

  function usesOutlineDrag(gallery) {
    return !!gallery?.closest?.(OUTLINE_THEME_SELECTOR);
  }

  function clamp(value, minimum, maximum) {
    if (minimum > maximum) {
      return (minimum + maximum) / 2;
    }
    return Math.min(maximum, Math.max(minimum, value));
  }

  function clampOffset({
    originX,
    originY,
    deltaX,
    deltaY,
    itemRect,
    galleryRect,
    inset = 4,
  }) {
    const minDeltaX = galleryRect.left + inset - itemRect.left;
    const maxDeltaX = galleryRect.right - inset - itemRect.right;
    const minDeltaY = galleryRect.top + inset - itemRect.top;
    const maxDeltaY = galleryRect.bottom - inset - itemRect.bottom;
    return {
      x: originX + clamp(deltaX, minDeltaX, maxDeltaX),
      y: originY + clamp(deltaY, minDeltaY, maxDeltaY),
    };
  }

  function offsetStoreFor(gallery) {
    const stableId = gallery?.dataset?.arcaneGalleryDragId;
    if (stableId) {
      if (!offsetsByGalleryId.has(stableId)) {
        offsetsByGalleryId.set(stableId, new Map());
      }
      return offsetsByGalleryId.get(stableId);
    }
    if (!offsetsByGallery.has(gallery)) {
      offsetsByGallery.set(gallery, new Map());
    }
    return offsetsByGallery.get(gallery);
  }

  function storedOffsetFor(item) {
    const gallery = item.closest(GALLERY_SELECTOR);
    const key = item.dataset.arcaneDragKey;
    if (!(gallery && key)) {
      return null;
    }
    return offsetStoreFor(gallery).get(key) ?? null;
  }

  function offsetFor(item) {
    const stored = storedOffsetFor(item);
    if (stored) {
      return { x: stored.x, y: stored.y };
    }
    return {
      x: finiteNumber(item.dataset.arcaneDragX, 0),
      y: finiteNumber(item.dataset.arcaneDragY, 0),
    };
  }

  function writeOffset(item, offset) {
    const x = Math.round(offset.x);
    const y = Math.round(offset.y);
    const xText = String(x);
    const yText = String(y);
    const xCss = `${x}px`;
    const yCss = `${y}px`;
    if (item.dataset.arcaneDragX !== xText) {
      item.dataset.arcaneDragX = xText;
    }
    if (item.dataset.arcaneDragY !== yText) {
      item.dataset.arcaneDragY = yText;
    }
    if (item.style.getPropertyValue('--arcane-drag-x') !== xCss) {
      item.style.setProperty('--arcane-drag-x', xCss);
    }
    if (item.style.getPropertyValue('--arcane-drag-y') !== yCss) {
      item.style.setProperty('--arcane-drag-y', yCss);
    }
    const gallery = item.closest(GALLERY_SELECTOR);
    const key = item.dataset.arcaneDragKey;
    if (gallery && key) {
      offsetStoreFor(gallery).set(key, { x, y });
    }
  }

  // The outline lives inside the gallery so theme CSS can reach it, is fixed to
  // the viewport (the bounds it mirrors are viewport coordinates), and carries
  // no attribute the packer looks for, so layout ignores it.
  function createDragOutline(gallery) {
    const outline = document.createElement('div');
    outline.className = OUTLINE_CLASS;
    outline.setAttribute('aria-hidden', 'true');
    gallery.appendChild(outline);
    return outline;
  }

  function positionDragOutline(outline, rect, deltaX, deltaY) {
    if (!outline) {
      return;
    }
    outline.style.left = `${Math.round(rect.left + deltaX)}px`;
    outline.style.top = `${Math.round(rect.top + deltaY)}px`;
    outline.style.width = `${Math.round(rect.width)}px`;
    outline.style.height = `${Math.round(rect.height)}px`;
  }

  function activateItem(gallery, item) {
    gallery.querySelectorAll(ITEM_SELECTOR).forEach((candidate) => {
      candidate.setAttribute(
        OUTLINE_ACTIVE_ATTRIBUTE,
        candidate === item ? 'true' : 'false',
      );
    });
  }

  function dragInsetFor(gallery) {
    return Math.max(0, finiteNumber(gallery.dataset.arcaneDragInset, 4));
  }

  function keyboardStepFor(gallery) {
    return Math.max(
      1,
      finiteNumber(gallery.dataset.arcaneDragKeyboardStep, 16),
    );
  }

  function pointerDragIsAvailable() {
    return !window.matchMedia?.('(pointer: coarse)').matches;
  }

  function dragTargetFor(target) {
    if (!(target instanceof Element)) {
      return null;
    }
    const handle = target.closest(HANDLE_SELECTOR);
    const item = handle?.closest(ITEM_SELECTOR);
    const gallery = item?.closest(GALLERY_SELECTOR);
    if (!(handle && item && gallery)) {
      return null;
    }
    const interactive = target.closest(INTERACTIVE_SELECTOR);
    if (interactive && interactive !== item && interactive !== handle) {
      return null;
    }
    return { handle, item, gallery };
  }

  function keyboardTargetFor(target) {
    if (!(target instanceof Element)) {
      return null;
    }
    const item = target.closest(ITEM_SELECTOR);
    const gallery = item?.closest(GALLERY_SELECTOR);
    if (!(item && gallery)) {
      return null;
    }
    const interactive = target.closest(INTERACTIVE_SELECTOR);
    if (interactive && interactive !== item) {
      return null;
    }
    return { item, gallery };
  }

  function createAnnouncer() {
    const existing = document.querySelector('.arcane-gallery-drag-announcer');
    if (existing) {
      return existing;
    }
    const announcer = document.createElement('div');
    announcer.className = 'arcane-gallery-drag-announcer';
    announcer.setAttribute('role', 'status');
    announcer.setAttribute('aria-live', 'polite');
    announcer.setAttribute('aria-atomic', 'true');
    document.body.appendChild(announcer);
    return announcer;
  }

  function announce(message) {
    const announcer = createAnnouncer();
    announcer.textContent = '';
    window.setTimeout(() => {
      announcer.textContent = message;
    }, 0);
  }

  function restoreOffsets(gallery) {
    if (!gallery?.matches?.(GALLERY_SELECTOR)) {
      return;
    }
    const store = offsetStoreFor(gallery);
    gallery.querySelectorAll(ITEM_SELECTOR).forEach((item) => {
      const key = item.dataset.arcaneDragKey;
      const stored = key ? store.get(key) : null;
      if (stored) {
        writeOffset(item, stored);
      }
    });
  }

  function reclampGallery(gallery) {
    if (
      !gallery?.matches?.(GALLERY_SELECTOR) ||
      gallery.clientWidth <= 0 ||
      gallery.clientHeight <= 0
    ) {
      return;
    }
    const galleryRect = gallery.getBoundingClientRect();
    const inset = dragInsetFor(gallery);
    gallery.querySelectorAll(ITEM_SELECTOR).forEach((item) => {
      const origin = offsetFor(item);
      const next = clampOffset({
        originX: origin.x,
        originY: origin.y,
        deltaX: 0,
        deltaY: 0,
        itemRect: item.getBoundingClientRect(),
        galleryRect,
        inset,
      });
      if (next.x !== origin.x || next.y !== origin.y) {
        writeOffset(item, next);
      }
    });
  }

  if (typeof window === 'undefined') {
    return;
  }

  window.ArcaneGalleryDrag = {
    crossedThreshold,
    clampOffset,
    refresh: () => {
      if (typeof document !== 'undefined') {
        document.querySelectorAll(GALLERY_SELECTOR).forEach(observeGallery);
      }
    },
  };

  if (typeof document === 'undefined') {
    return;
  }

  let active = null;
  let suppressedClick = null;
  let zOrder = 10;
  const restoreScheduled = new WeakSet();

  const resizeObserver = 'ResizeObserver' in window
    ? new ResizeObserver((entries) => {
        for (const entry of entries) {
          reclampGallery(entry.target);
        }
      })
    : null;

  function scheduleRestore(gallery) {
    if (!gallery || restoreScheduled.has(gallery)) {
      return;
    }
    restoreScheduled.add(gallery);
    window.requestAnimationFrame(() => {
      restoreScheduled.delete(gallery);
      restoreOffsets(gallery);
      reclampGallery(gallery);
    });
  }

  function observeGallery(gallery) {
    if (!gallery) {
      return;
    }
    if (gallery.dataset.arcaneDragObserved !== 'true') {
      gallery.dataset.arcaneDragObserved = 'true';
      resizeObserver?.observe(gallery);
    }
    scheduleRestore(gallery);
  }

  function refreshAll() {
    document.querySelectorAll(GALLERY_SELECTOR).forEach(observeGallery);
  }

  function finishDrag(pointerId, cancelled = false) {
    if (!active || pointerId !== active.pointerId) {
      return;
    }
    const current = active;
    active = null;
    const {
      item,
      gallery,
      dragging,
      origin,
      outline,
      outlineDrag,
      pending,
    } = current;
    outline?.remove?.();
    if (dragging && outlineDrag) {
      // The window never left its place: the drop is the only repaint, and a
      // cancelled drag simply discards the wireframe.
      if (!cancelled && pending) {
        writeOffset(item, pending);
      }
    } else if (cancelled && dragging) {
      writeOffset(item, origin);
    }
    try {
      if (item.hasPointerCapture(pointerId)) {
        item.releasePointerCapture(pointerId);
      }
    } catch (_) {
      // Browsers can release pointer capture automatically.
    }
    item.classList.remove('is-arcane-gallery-dragging');
    gallery.classList.remove('is-arcane-gallery-drag-active');
    if (dragging && !cancelled) {
      suppressedClick = {
        item,
        until: performance.now() + CLICK_SUPPRESSION_MS,
      };
      announce(
        'Item repositioned. Use Shift and the arrow keys to fine tune it.',
      );
    }
    if (gallery.dataset.packing === 'true') {
      window.ArcaneGallery?.observe?.(gallery);
    }
  }

  document.addEventListener(
    'pointerdown',
    (event) => {
      if (
        active ||
        !pointerDragIsAvailable() ||
        event.button !== 0 ||
        event.isPrimary === false ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        event.shiftKey
      ) {
        return;
      }
      const target = dragTargetFor(event.target);
      if (!target) {
        return;
      }
      const origin = offsetFor(target.item);
      const outlineDrag = usesOutlineDrag(target.gallery);
      target.item.setAttribute('draggable', 'false');
      // Activation is a mousedown effect, independent of any drag following.
      target.item.style.zIndex = String(++zOrder);
      if (outlineDrag) {
        activateItem(target.gallery, target.item);
      }
      active = {
        ...target,
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        startRect: target.item.getBoundingClientRect(),
        galleryRect: target.gallery.getBoundingClientRect(),
        origin,
        dragging: false,
        outlineDrag,
        outline: null,
        pending: null,
      };
      try {
        target.item.setPointerCapture(event.pointerId);
      } catch (_) {
        // Dragging still works while the pointer remains over the document.
      }
    },
    true,
  );

  document.addEventListener(
    'pointermove',
    (event) => {
      if (!active || event.pointerId !== active.pointerId) {
        return;
      }
      const deltaX = event.clientX - active.startX;
      const deltaY = event.clientY - active.startY;
      if (!active.dragging) {
        const crossed = active.outlineDrag
          ? crossedOutlineThreshold(deltaX, deltaY)
          : crossedThreshold(deltaX, deltaY);
        if (!crossed) {
          return;
        }
        active.dragging = true;
        active.item.classList.add('is-arcane-gallery-dragging');
        active.gallery.classList.add('is-arcane-gallery-drag-active');
        if (active.outlineDrag) {
          active.outline = createDragOutline(active.gallery);
        }
      }
      event.preventDefault();
      const next = clampOffset({
        originX: active.origin.x,
        originY: active.origin.y,
        deltaX,
        deltaY,
        itemRect: active.startRect,
        galleryRect: active.galleryRect,
        inset: dragInsetFor(active.gallery),
      });
      if (active.outlineDrag) {
        // Only the wireframe moves; the tile is repositioned by finishDrag.
        active.pending = next;
        positionDragOutline(
          active.outline,
          active.startRect,
          next.x - active.origin.x,
          next.y - active.origin.y,
        );
        return;
      }
      writeOffset(active.item, next);
    },
    true,
  );

  document.addEventListener(
    'pointerup',
    (event) => finishDrag(event.pointerId),
    true,
  );
  document.addEventListener(
    'pointercancel',
    (event) => finishDrag(event.pointerId, true),
    true,
  );
  document.addEventListener(
    'lostpointercapture',
    (event) => {
      if (
        active &&
        event.pointerId === active.pointerId &&
        event.target === active.item
      ) {
        finishDrag(event.pointerId, true);
      }
    },
    true,
  );

  document.addEventListener(
    'click',
    (event) => {
      if (!suppressedClick) {
        return;
      }
      if (performance.now() > suppressedClick.until) {
        suppressedClick = null;
        return;
      }
      if (event.composedPath().includes(suppressedClick.item)) {
        event.preventDefault();
        event.stopImmediatePropagation();
        suppressedClick = null;
      }
    },
    true,
  );

  document.addEventListener(
    'keydown',
    (event) => {
      if (active && event.key === 'Escape') {
        if (!active.outlineDrag) {
          writeOffset(active.item, active.origin);
        }
        finishDrag(active.pointerId, true);
        event.preventDefault();
        return;
      }
      if (!event.shiftKey || !event.key.startsWith('Arrow')) {
        return;
      }
      const target = keyboardTargetFor(event.target);
      if (!target) {
        return;
      }
      const step = keyboardStepFor(target.gallery);
      const direction = {
        ArrowLeft: [-step, 0],
        ArrowRight: [step, 0],
        ArrowUp: [0, -step],
        ArrowDown: [0, step],
      }[event.key];
      if (!direction) {
        return;
      }
      event.preventDefault();
      const origin = offsetFor(target.item);
      const next = clampOffset({
        originX: origin.x,
        originY: origin.y,
        deltaX: direction[0],
        deltaY: direction[1],
        itemRect: target.item.getBoundingClientRect(),
        galleryRect: target.gallery.getBoundingClientRect(),
        inset: dragInsetFor(target.gallery),
      });
      if (
        Math.round(next.x) === Math.round(origin.x) &&
        Math.round(next.y) === Math.round(origin.y)
      ) {
        announce('Item is already at the edge of the gallery.');
        return;
      }
      writeOffset(target.item, next);
      target.item.style.zIndex = String(++zOrder);
      announce(`Item moved ${event.key.replace('Arrow', '').toLowerCase()}.`);
    },
    true,
  );

  const mutationObserver = 'MutationObserver' in window
    ? new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          if (mutation.type === 'attributes') {
            const gallery = mutation.target.closest?.(GALLERY_SELECTOR);
            if (gallery) {
              observeGallery(gallery);
            }
            continue;
          }
          for (const node of mutation.addedNodes) {
            if (!node || node.nodeType !== 1) {
              continue;
            }
            if (node.matches?.(GALLERY_SELECTOR)) {
              observeGallery(node);
            }
            node.querySelectorAll?.(GALLERY_SELECTOR).forEach(observeGallery);
          }
        }
      })
    : null;

  function start() {
    refreshAll();
    mutationObserver?.observe(document.documentElement || document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: [
        'style',
        'data-arcane-drag-x',
        'data-arcane-drag-y',
      ],
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
})();

})();
