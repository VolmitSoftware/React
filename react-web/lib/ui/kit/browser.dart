/// The few page calls the kit makes. Each one does nothing off the browser
/// (server rendering in tests), so kit widgets render the same markup there.
library;

import 'package:jaspr/jaspr.dart' show kIsWeb;
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

/// Runs [run] after the next frame is laid out.
void afterFrame(void Function() run) {
  if (!kIsWeb) return;
  web.window.requestAnimationFrame(((JSNumber _) => run()).toJS);
}

/// Focuses the element with [id] once it is on the page.
void focusById(String id) {
  if (!kIsWeb) return;
  afterFrame(
    () => (web.document.getElementById(id) as web.HTMLElement?)?.focus(),
  );
}

/// Phone widths: the sidebar is a drawer, footers stack and toolbars give
/// their room to the one control that needs it (the kit's 760 px line).
const String compactQuery = '(max-width: 760px)';

/// Whether [query] matches now, and a stop for the watcher that reports
/// each change to [onChange].
(bool, void Function()) watchMedia(
  String query,
  void Function(bool matches) onChange,
) {
  if (!kIsWeb) return (false, () {});
  final web.MediaQueryList list = web.window.matchMedia(query);
  final JSFunction listener = ((web.MediaQueryListEvent event) => onChange(
    event.matches,
  )).toJS;
  list.addEventListener('change', listener);
  return (list.matches, () => list.removeEventListener('change', listener));
}

/// Sends plain left clicks on same-site links (`<a href="/...">` without a
/// target) to [navigate] instead of loading the page again, so the live
/// connections stay up. Returns the stop.
void Function() routeLinks(void Function(String path) navigate) {
  if (!kIsWeb) return () {};
  final JSFunction listener = ((web.MouseEvent event) {
    if (event.defaultPrevented ||
        event.button != 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey) {
      return;
    }
    final JSAny? target = event.target;
    if (target == null || !target.isA<web.Element>()) return;
    final web.Element? anchor = (target as web.Element).closest('a[href]');
    if (anchor == null ||
        anchor.hasAttribute('target') ||
        anchor.hasAttribute('download')) {
      return;
    }
    final String href = anchor.getAttribute('href') ?? '';
    if (!href.startsWith('/') || href.startsWith('//')) return;
    event.preventDefault();
    navigate(href);
  }).toJS;
  web.document.addEventListener('click', listener);
  return () => web.document.removeEventListener('click', listener);
}

/// Whether the runtime has the surface [id] (menu, popover) open.
bool surfaceOpen(String id) =>
    kIsWeb &&
    web.document.getElementById(id)?.getAttribute('data-arcane-state') ==
        'open';

/// This page's origin ("https://react.volmitsoftware.com"); '' off the
/// browser.
String pageOrigin() => kIsWeb ? web.window.location.origin : '';

/// The browser's user agent; '' off the browser.
String userAgent() => kIsWeb ? web.window.navigator.userAgent : '';

/// The address's fragment, taken out of the address bar and the history
/// entry at once (a pairing link's code lives there). '' when there is
/// none or off the browser.
String takeFragment() {
  if (!kIsWeb) return '';
  final web.Location location = web.window.location;
  final String hash = location.hash;
  if (hash.isEmpty) return '';
  web.window.history.replaceState(
    null,
    '',
    '${location.pathname}${location.search}',
  );
  return hash.substring(1);
}

/// Calls [onFragment] with each new fragment the address gets while the
/// page is open (a pairing link opened again with only its fragment
/// changed loads nothing), taking it out of the address bar first. Returns
/// the stop.
void Function() watchFragment(void Function(String fragment) onFragment) {
  if (!kIsWeb) return () {};
  final JSFunction listener = ((web.Event _) {
    final String fragment = takeFragment();
    if (fragment.isNotEmpty) onFragment(fragment);
  }).toJS;
  web.window.addEventListener('hashchange', listener);
  web.window.addEventListener('popstate', listener);
  return () {
    web.window.removeEventListener('hashchange', listener);
    web.window.removeEventListener('popstate', listener);
  };
}
