/// The frame around every page: the sidebar, the top bar (breadcrumbs, the
/// "..." menu with theme, mode and language) and the page. From 760 px down
/// the sidebar is a drawer opened from the top bar, which then also holds
/// the server switcher and the alerts button. Only the page scrolls.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/pairing_link.dart';
import '../screen/pairing/pair_dialog.dart';
import '../state/connection_manager.dart';
import '../state/fleet_live_scope.dart';
import '../state/fleet_rollup.dart';
import '../state/fleet_scope.dart';
import '../theme/reactor_theme.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/browser.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/density.dart';
import '../ui/kit/dialog.dart';
import '../ui/kit/icon_button.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/sheet.dart';
import 'fleet_observer.dart';
import 'paths.dart';
import 'shell_nav.dart';

/// Widths at which the sidebar is an icon rail.
const String railQuery = '(max-width: 1100px)';

/// The id of the page's `<main>`.
const String mainId = 'reactor-main';

class AppShell extends StatefulWidget {
  const AppShell({
    this.body = const Component.fragment(<Widget>[]),
    this.currentPath = '/',
    super.key,
  });

  final Widget body;
  final String currentPath;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  bool _compact = false;
  bool _rail = false;
  bool _navOpen = false;
  bool _languageOpen = false;
  bool _pairOpen = false;

  /// A link pasted into a New link field before the dialog opened.
  PairingLink? _pairLink;
  final List<void Function()> _stops = <void Function()>[];

  @override
  void initState() {
    super.initState();
    final (bool compact, void Function() stopCompact) = watchMedia(
      compactQuery,
      (bool compact) {
        if (!mounted) return;
        setState(() {
          _compact = compact;
          if (!compact) _navOpen = false;
        });
      },
    );
    final (bool rail, void Function() stopRail) = watchMedia(railQuery, (
      bool rail,
    ) {
      if (mounted) setState(() => _rail = rail);
    });
    _compact = compact;
    _rail = rail;
    _stops
      ..add(stopCompact)
      ..add(stopRail)
      ..add(routeLinks(_navigate));
  }

  @override
  void didUpdateWidget(AppShell oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (oldComponent.currentPath != component.currentPath) _navOpen = false;
  }

  @override
  void dispose() {
    for (final void Function() stop in _stops) {
      stop();
    }
    super.dispose();
  }

  void _navigate(String path) {
    if (_navOpen) setState(() => _navOpen = false);
    Router.maybeOf(context)?.push(path);
  }

  /// The paired servers, read from FleetLiveScope so the route table never
  /// captures them (rebuilding the Router per fleet change broke pairing).
  List<ServerEntry> get servers => <ServerEntry>[
    for (final FleetServerLive server
        in FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[])
      ServerEntry(id: server.id, name: server.name, state: server.state),
  ];

  /// Entries a reconnect could bring back: not the ones whose token the
  /// server revoked.
  Iterable<FleetServerLive> get _reconnectable =>
      (FleetLiveScope.of(context)?.servers ?? const <FleetServerLive>[]).where(
        (FleetServerLive s) => s.access != ConnAccess.revoked,
      );

  ServerEntry? get _active {
    final String? id = serverIdOf(component.currentPath);
    for (final ServerEntry server in servers) {
      if (server.id == id) return server;
    }
    return null;
  }

  bool _unhealthy(ConnState state) =>
      state == ConnState.offline || state == ConnState.degraded;

  /// On the server group's pages: a line when a server is down, with
  /// Reconnect. A server's own page says it in its live frame.
  Widget? _fleetAlert() {
    // A server's page says it in its live frame; Home in its Continue rows.
    final String bare = Uri.parse(component.currentPath).path;
    if (serverIdOf(component.currentPath) != null ||
        bare == kRouteRoot ||
        bare == kRoutePair) {
      return null;
    }
    if (!_reconnectable.any((FleetServerLive s) => _unhealthy(s.state))) {
      return null;
    }
    final bool offline = _reconnectable.any(
      (FleetServerLive s) => s.state == ConnState.offline,
    );
    return AppAlert(
      tone: offline ? AlertTone.error : AlertTone.warning,
      message: offline
          ? reactorText(ReactorText.shellConnectionLost)
          : reactorText(ReactorText.shellConnectionDegraded),
      live: true,
      action: ActionRow.footer(
        dense: true,
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.shellReconnect),
            FleetScope.of(context)?.fleetManager.reconnectAll,
          ),
        ],
      ),
    );
  }

  Widget _topbar(BuildContext context) {
    final ReactorThemeScope? theme = ReactorThemeScope.maybeOf(context);
    final ModeScope? mode = ModeScope.maybeOf(context);
    final int alerts = openAlertCount(context);
    final String navLabel = _navOpen
        ? reactorText(ReactorText.shellCloseNavigation)
        : reactorText(ReactorText.shellOpenNavigation);
    return dom.header(classes: 'ui-topbar', <Widget>[
      DensityScope.dense(
        child: dom.div(classes: 'ui-topbar-row', <Widget>[
          if (_compact) ...<Widget>[
            AppIconButton(
              id: 'ui-nav-button',
              icon: ArcaneIcon.menu(),
              label: navLabel,
              expanded: _navOpen,
              attributes: const <String, String>{
                'aria-controls': 'ui-nav-drawer',
              },
              tipAtStart: true,
              ghost: true,
              onPressed: () => setState(() => _navOpen = !_navOpen),
            ),
            dom.div(classes: 'ui-topbar-switcher', <Widget>[
              ServerSwitcher(
                servers: servers,
                currentPath: component.currentPath,
                wide: false,
              ),
            ]),
          ] else
            dom.div(classes: 'ui-crumbs', <Widget>[
              ArcaneBreadcrumbs(
                size: BreadcrumbSize.sm,
                items: breadcrumbsFor(component.currentPath, servers),
              ),
            ]),
          const dom.span(classes: 'ui-topbar-gap', <Widget>[]),
          if (_compact && alerts == 0)
            AppIconButton(
              icon: ArcaneIcon.bell(),
              label: reactorText(ReactorText.alertsTitle),
              ghost: true,
              onPressed: () => _navigate(kRouteAlerts),
            )
          else if (_compact)
            Button.ghost(
              label: '$alerts',
              icon: ArcaneIcon.bell(size: IconSize.sm),
              size: ButtonSize.sm,
              attributes: <String, String>{
                'aria-label':
                    '${reactorText(ReactorText.alertsTitle)}: $alerts',
              },
              onPressed: () => _navigate(kRouteAlerts),
            ),
          AppMenu(
            id: 'ui-more',
            icon: ArcaneIcon.ellipsis(),
            iconLabel: reactorText(ReactorText.shellMoreOptions),
            alignEnd: true,
            ghost: true,
            entries: <AppMenuEntry>[
              if (theme != null)
                AppMenuEntry(
                  theme.isDark
                      ? reactorText(ReactorText.themeSwitchToLight)
                      : reactorText(ReactorText.themeSwitchToDark),
                  theme.toggle,
                  icon: theme.isDark ? ArcaneIcon.sun() : ArcaneIcon.moon(),
                ),
              if (mode != null)
                AppMenuEntry(
                  mode.mode == ReactorMode.advanced
                      ? reactorText(ReactorText.modeSwitchToSimple)
                      : reactorText(ReactorText.modeSwitchToAdvanced),
                  () => mode.onChanged(
                    mode.mode == ReactorMode.advanced
                        ? ReactorMode.simple
                        : ReactorMode.advanced,
                  ),
                  icon: ArcaneIcon.layers(),
                ),
              AppMenuEntry(
                reactorText(ReactorText.languageSelect),
                () => setState(() => _languageOpen = true),
                icon: ArcaneIcon.languages(),
                hint: dependOnReactorLocale(context)?.definition.nativeName,
              ),
            ],
          ),
        ]),
      ),
    ]);
  }

  void _openPair([PairingLink? link]) => setState(() {
    _navOpen = false;
    _pairOpen = true;
    _pairLink = link;
  });

  @override
  Widget build(BuildContext context) => PairDialogScope(
    open: _openPair,
    child: Builder(builder: _frame),
  );

  Widget _frame(BuildContext context) {
    dependOnReactorLocale(context);
    final Widget? fleetAlert = _fleetAlert();
    return dom.div(
      classes: _compact ? 'ui-app is-compact' : 'ui-app',
      attributes: <String, String>{
        if (_active != null) 'data-server': _active!.id,
      },
      <Widget>[
        dom.div(classes: 'ui-skip', <Widget>[
          Button.outline(
            label: reactorText(ReactorText.shellSkipToContent),
            size: ButtonSize.sm,
            onPressed: () => focusById(pageHeadingId),
          ),
        ]),
        if (!_compact)
          dom.div(classes: 'ui-nav', <Widget>[
            ShellNav(
              servers: servers,
              currentPath: component.currentPath,
              rail: _rail,
            ),
          ]),
        _topbar(context),
        dom.main_(id: mainId, classes: 'ui-page', <Widget>[
          ?fleetAlert,
          component.body,
        ]),
        if (_compact)
          Sheet(
            id: 'ui-nav-drawer',
            open: _navOpen,
            fromStart: true,
            title: reactorText(ReactorText.shellMenu),
            onClose: () {
              setState(() => _navOpen = false);
              focusById('ui-nav-button');
            },
            child: ShellNav(
              servers: servers,
              currentPath: component.currentPath,
              switcher: false,
            ),
          ),
        LanguageDialog(
          open: _languageOpen,
          onClose: () => setState(() => _languageOpen = false),
        ),
        PairDialog(
          open: _pairOpen,
          link: _pairLink,
          onClose: () => setState(() {
            _pairOpen = false;
            _pairLink = null;
          }),
        ),
        const ConfirmHost(),
      ],
    );
  }
}

/// Picks the interface language; the pick applies at once.
class LanguageDialog extends StatelessWidget {
  const LanguageDialog({required this.open, required this.onClose, super.key});

  final bool open;
  final void Function() onClose;

  @override
  Widget build(BuildContext context) {
    final ReactorLocaleScope? locale = dependOnReactorLocale(context);
    return AppDialog(
      id: 'ui-language',
      open: open,
      title: reactorText(ReactorText.languageSelect),
      onClose: onClose,
      children: <Widget>[
        if (open)
          ArcaneRadioGroup<String>(
            name: 'reactor-language',
            // Room between rows for a finger.
            gap: '0.875rem',
            value: locale?.locale,
            options: <RadioOption<String>>[
              for (final ReactorLocaleDefinition definition in reactorLocales)
                RadioOption<String>(
                  value: definition.code,
                  label: definition.nativeName,
                ),
            ],
            onChanged: (String code) {
              locale?.onChanged(code);
              onClose();
            },
          ),
      ],
    );
  }
}
