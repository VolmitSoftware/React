/// Tabs on shadcn's tab bar. [AppTabs] is the page's tab row: the picked tab
/// lives in the address (`?tab=<id>`), so a tab can be linked and survives a
/// reload, and only the picked tab's body is built. [AppTabBar] is the bare
/// row for a part that keeps its own state. Both name the tablist, keep one
/// Tab stop, move with Left/Right/Home/End and scroll sideways on a narrow
/// screen instead of wrapping.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, kIsWeb;
import 'package:jaspr_router/jaspr_router.dart';
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

import 'browser.dart';
import 'mode.dart';

/// One tab of [AppTabs].
class AppTab {
  const AppTab(this.id, this.label, this.child, {this.advanced = false});

  /// The `?tab=` value: lowercase, stable, never translated.
  final String id;

  /// One to three words.
  final String label;

  /// The tab's body; built only while the tab is picked.
  final Widget child;

  /// Shown in Advanced mode only.
  final bool advanced;
}

/// The page's tab row and the picked tab's body.
class AppTabs extends StatefulWidget {
  const AppTabs({
    required this.label,
    required this.tabs,
    this.param = 'tab',
    super.key,
  });

  /// The tablist's accessible name (usually the page title).
  final String label;
  final List<AppTab> tabs;

  /// The query parameter that holds the picked tab's id.
  final String param;

  /// The tabs [context] shows: Advanced-only tabs drop out in Simple.
  List<AppTab> visible(BuildContext context) {
    final bool advanced = ModeScope.advanced(context);
    return <AppTab>[
      for (final AppTab tab in tabs)
        if (advanced || !tab.advanced) tab,
    ];
  }

  @override
  State<AppTabs> createState() => _AppTabsState();
}

class _AppTabsState extends State<AppTabs> {
  /// The pick when there is no router (tests, a part outside the routes).
  String? _local;

  String? _picked(BuildContext context) {
    final RouteState? route = RouteState.maybeOf(context);
    return route == null ? _local : route.queryParams[component.param];
  }

  void _select(BuildContext context, String id) {
    final RouterState? router = Router.maybeOf(context);
    final RouteState? route = RouteState.maybeOf(context);
    if (router == null || route == null) {
      setState(() => _local = id);
      return;
    }
    final Uri now = Uri.parse(route.location);
    final Map<String, String> query = <String, String>{
      ...now.queryParameters,
      component.param: id,
    };
    router.replace(now.replace(queryParameters: query).toString());
  }

  @override
  Widget build(BuildContext context) {
    final List<AppTab> tabs = component.visible(context);
    if (tabs.isEmpty) return const Component.fragment(<Widget>[]);
    final String? wanted = _picked(context);
    int selected = tabs.indexWhere((AppTab tab) => tab.id == wanted);
    if (selected < 0) selected = 0;
    final AppTab tab = tabs[selected];
    return dom.div(
      classes: 'ui-tabs-page',
      attributes: <String, String>{
        'data-tabs': <String>[for (final AppTab each in tabs) each.id]
            .join(','),
        'data-tab': tab.id,
      },
      <Widget>[
        AppTabBar(
          label: component.label,
          tabs: <String>[for (final AppTab each in tabs) each.label],
          selected: selected,
          onSelect: (int index) => _select(context, tabs[index].id),
        ),
        dom.div(
          classes: 'ui-tab-panel',
          attributes: <String, String>{
            'role': 'tabpanel',
            'aria-label': tab.label,
          },
          <Widget>[tab.child],
        ),
      ],
    );
  }
}

int _nextTabsId = 0;

/// A bare tab row whose owner keeps the pick.
class AppTabBar extends StatefulWidget {
  const AppTabBar({
    required this.label,
    required this.tabs,
    required this.selected,
    required this.onSelect,
    this.disabled = const <int>{},
    this.id,
    super.key,
  });

  /// The tablist's accessible name.
  final String label;
  final List<String> tabs;
  final int selected;
  final void Function(int index) onSelect;

  /// Tabs shown but not pickable.
  final Set<int> disabled;
  final String? id;

  @override
  State<AppTabBar> createState() => _AppTabBarState();
}

class _AppTabBarState extends State<AppTabBar> {
  late final String _id = component.id ?? 'ui-tabs-${_nextTabsId++}';

  /// The library's bar keeps showing the tab it last drew selected, so a
  /// selection that did not come from a click on it (the address, the arrow
  /// keys) draws a new bar.
  int _bar = 0;
  int? _clicked;

  @override
  void didUpdateWidget(AppTabBar oldComponent) {
    super.didUpdateWidget(oldComponent);
    final bool moved =
        component.selected != oldComponent.selected &&
        component.selected != _clicked;
    final bool enabled =
        component.disabled.length != oldComponent.disabled.length ||
        !component.disabled.containsAll(oldComponent.disabled);
    final bool renamed =
        component.tabs.join('\n') != oldComponent.tabs.join('\n');
    if (moved || enabled || renamed) _bar++;
    _clicked = null;
  }

  List<String> get tabs => component.tabs;
  Set<int> get disabled => component.disabled;
  int get selected => component.selected;

  /// Names the library's tablist, marks tabs that cannot be picked and keeps
  /// one Tab stop (the selected tab). Set after each render.
  void _decorate() {
    final web.Element? row = web.document.getElementById(_id);
    final web.Element? list = row?.querySelector('[role="tablist"]');
    if (list == null) return;
    list.setAttribute('aria-label', component.label);
    final web.NodeList found = list.querySelectorAll('[role="tab"]');
    for (int index = 0; index < found.length; index++) {
      final web.Element tab = found.item(index)! as web.Element;
      tab.setAttribute('tabindex', index == selected ? '0' : '-1');
      if (disabled.contains(index)) {
        tab.setAttribute('aria-disabled', 'true');
      } else {
        tab.removeAttribute('aria-disabled');
      }
    }
  }

  void _pick(int index) {
    if (index < 0 || index >= tabs.length || disabled.contains(index)) return;
    component.onSelect(index);
  }

  void _keys(web.Event raw) {
    final web.KeyboardEvent event = raw as web.KeyboardEvent;
    final web.EventTarget? target = event.target;
    if (target == null ||
        !target.isA<web.Element>() ||
        (target as web.Element).getAttribute('role') != 'tab') {
      return;
    }
    final List<int> open = <int>[
      for (int index = 0; index < tabs.length; index++)
        if (!disabled.contains(index)) index,
    ];
    if (open.isEmpty) return;
    final int at = open.indexOf(selected);
    final int? next = switch (event.key) {
      'ArrowRight' => open[(at + 1) % open.length],
      'ArrowLeft' => open[(at - 1 + open.length) % open.length],
      'Home' => open.first,
      'End' => open.last,
      _ => null,
    };
    if (next == null) return;
    event.preventDefault();
    _pick(next);
    final web.Element? row = target.closest('.ui-tabs');
    web.window.setTimeout(
      (() {
        final web.NodeList? found = row?.querySelectorAll('[role="tab"]');
        if (found != null && next < found.length) {
          (found.item(next)! as web.HTMLElement).focus();
        }
      }).toJS,
      0.toJS,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) afterFrame(_decorate);
    return dom.div(
      id: _id,
      classes: 'ui-tabs',
      events: <String, void Function(web.Event)>{'keydown': _keys},
      <Widget>[
        ArcaneTabBar(
          key: ValueKey<int>(_bar),
          tabs: <ArcaneTabBarItem>[
            for (final String tab in tabs) ArcaneTabBarItem(label: tab),
          ],
          selectedIndex: selected,
          onChanged: (int index) {
            _clicked = index;
            _pick(index);
          },
        ),
      ],
    );
  }
}
