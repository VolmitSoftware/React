/// The frame of every page: the title with its (i) tip, the server's
/// connection and role badges, the page's one [ActionRow], then the tabs or
/// the content on the page background. The shell draws the breadcrumbs.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/role_info.dart';
import '../../state/connection_manager.dart';
import '../../state/role_scope.dart';
import '../../state/server_scope.dart';
import 'action_row.dart';
import 'badge.dart';
import 'info_tip.dart';
import 'tabs.dart';

/// The id of the page's `<h1>`, the skip link's target.
const String pageHeadingId = 'reactor-page-heading';

class AppPage extends StatelessWidget {
  const AppPage({
    required this.title,
    this.tip,
    this.actions,
    this.tabs,
    this.children = const <Widget>[],
    super.key,
  });

  /// One to three words; the nav entry's label.
  final String title;

  /// What the page is for, behind the (i) beside the title.
  final String? tip;

  /// The page's actions (filters left, primary last).
  final ActionRow? actions;

  /// The page's tabs; the picked tab's body follows the [children].
  final AppTabs? tabs;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    // An ended pairing has no data and no rights: the title and its state
    // only (the Pairing ended notice follows the page).
    final bool ended = ServerScope.of(context)?.pairingEnded ?? false;
    return dom.div(classes: 'ui-page-frame', <Widget>[
      dom.header(classes: 'ui-page-header', <Widget>[
        dom.div(classes: 'ui-page-title', <Widget>[
          dom.h1(
            id: pageHeadingId,
            attributes: const <String, String>{'tabindex': '-1'},
            <Widget>[Component.text(title)],
          ),
          if (tip != null) InfoTip(tip: tip!),
          ..._badges(context),
        ]),
        if (!ended) ?actions,
      ]),
      if (!ended && children.isNotEmpty)
        dom.div(classes: 'ui-page-body', children),
      if (!ended) ?tabs,
    ]);
  }

  /// The connection and role of the server the page belongs to.
  List<Widget> _badges(BuildContext context) {
    final ServerScope? server = ServerScope.of(context);
    if (server == null) return const <Widget>[];
    if (server.pairingEnded) {
      return <Widget>[
        StatusBadge(
          reactorText(ReactorText.pairEndedState),
          tone: BadgeTone.error,
        ),
      ];
    }
    final RoleInfo? role = RoleScope.of(context)?.role;
    return <Widget>[
      connectionBadge(server.state),
      if (role != null) StatusBadge(roleLabel(role)),
    ];
  }
}

/// A server's connection as a badge with a live dot, in the words Home
/// uses: Online, Checking (connecting, or a request failed and it is asked
/// again), Not reachable.
Widget connectionBadge(ConnState state) => switch (state) {
  ConnState.live => StatusBadge(
    reactorText(ReactorText.statusLive),
    tone: BadgeTone.ok,
    dot: true,
  ),
  ConnState.connecting => StatusBadge(
    reactorText(ReactorText.startChecking),
    tone: BadgeTone.info,
    dot: true,
  ),
  ConnState.degraded => StatusBadge(
    reactorText(ReactorText.startChecking),
    tone: BadgeTone.warning,
    dot: true,
  ),
  ConnState.offline => StatusBadge(
    reactorText(ReactorText.startNotReachable),
    tone: BadgeTone.error,
    dot: true,
  ),
};

/// The paired token's role, as the nav and badges name it.
String roleLabel(RoleInfo role) => switch (role.role) {
  'admin' => reactorText(ReactorText.roleAdmin),
  'operator' => reactorText(ReactorText.roleOperator),
  _ => reactorText(ReactorText.roleViewer),
};

/// Several parts of one tab or page, one under the other with the page's
/// spacing.
class PageStack extends StatelessWidget {
  const PageStack({required this.children, super.key});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => dom.div(classes: 'ui-stack', children);
}
