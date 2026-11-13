/// The start page's Continue list: each paired server once, with Online,
/// Checking, Not reachable or Pairing ended, its players, TPS and open
/// incidents from `GET /api/v1/summary` (direct or relay, whichever
/// answers; [ContinueChecks] decides what a row shows between answers),
/// Open, and Forget server in its "..." menu. A row whose pairing ended
/// holds the New link field.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../../app/paths.dart';
import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../model/server_summary.dart';
import '../../service/react_client.dart';
import '../../service/react_exceptions.dart';
import '../../state/connection_manager.dart';
import '../../state/fleet_manager.dart';
import '../../state/fleet_scope.dart';
import '../../state/server_session.dart';
import '../../ui/kit/action_row.dart';
import '../../ui/kit/badge.dart';
import '../../ui/kit/confirm_dialog.dart';
import '../../ui/kit/data_list.dart';
import '../../ui/kit/menu.dart';
import 'continue_checks.dart';
import 'pair_dialog.dart';
import 'pair_link_form.dart';
import 'pair_words.dart';

/// One server's row: its last summary and whether it answered.
class ContinueRow {
  const ContinueRow({
    required this.id,
    required this.name,
    required this.reach,
    this.summary,
    this.tokenId,
    this.endsSoon,
    this.stale = false,
  });

  final String id;
  final String name;
  final ServerReach reach;
  final ServerSummary? summary;
  final String? tokenId;

  /// "Pairing ends in n days unless used." within three days of the end.
  final String? endsSoon;

  /// [summary] is from before the last drop or answer: shown dimmed.
  final bool stale;
}

class ContinueList extends StatefulWidget {
  const ContinueList({super.key});

  @override
  State<ContinueList> createState() => _ContinueListState();
}

class _ContinueListState extends State<ContinueList> {
  final ContinueChecks _checks = ContinueChecks();
  final Set<String> _asking = <String>{};
  final Map<String, Timer> _next = <String, Timer>{};
  final Map<String, StreamSubscription<ConnState>> _watching =
      <String, StreamSubscription<ConnState>>{};

  /// The session each row watches; a server paired again gets a new one.
  final Map<String, String> _sessionKeys = <String, String>{};
  FleetManager? _fleet;
  bool _disposed = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final FleetManager? fleet = FleetScope.of(context)?.fleetManager;
    if (!identical(fleet, _fleet)) {
      _fleet?.removeListener(_sync);
      _fleet = fleet;
      fleet?.addListener(_sync);
    }
    _sync(rebuild: false);
  }

  @override
  void dispose() {
    _disposed = true;
    _fleet?.removeListener(_sync);
    for (final Timer timer in _next.values) {
      timer.cancel();
    }
    for (final StreamSubscription<ConnState> sub in _watching.values) {
      unawaited(sub.cancel());
    }
    super.dispose();
  }

  /// Watches every session and asks each new one at once. [rebuild] is
  /// off while the list is being built.
  void _sync({bool rebuild = true}) {
    final FleetManager? fleet = _fleet;
    if (fleet == null || _disposed) return;
    final Map<String, String> current = <String, String>{
      for (final ServerSession session in fleet.sessions)
        session.serverId: session.key,
    };
    for (final String stale
        in _watching.keys
            .where((String id) => current[id] != _sessionKeys[id])
            .toList()) {
      unawaited(_watching.remove(stale)?.cancel());
      _sessionKeys.remove(stale);
      _next.remove(stale)?.cancel();
      _checks.forget(stale);
    }
    for (final ServerSession session in fleet.sessions) {
      final String id = session.serverId;
      if (_watching.containsKey(id)) continue;
      _sessionKeys[id] = session.key;
      ConnState was = session.connection.state;
      _watching[id] = session.connection.stateChanges.listen((ConnState state) {
        if (_disposed) return;
        final bool dropped = was == ConnState.live && state != ConnState.live;
        was = state;
        if (dropped) _checks.dropped(id);
        setState(() {});
        if (state == ConnState.live || dropped) _ask(id);
      });
      _ask(id);
    }
    if (rebuild && mounted) setState(() {});
  }

  Future<void> _ask(String id) async {
    final FleetManager? fleet = _fleet;
    final ServerSession? session = fleet?.sessionFor(id);
    _next.remove(id)?.cancel();
    if (fleet == null || session == null || _disposed || _asking.contains(id)) {
      return;
    }
    if (fleet.pairingEnded(id)) return;
    final IReactClient client = session.client;
    if (client is! ISummaryClient) return;
    _asking.add(id);
    ServerSummary? summary;
    bool ended = false;
    try {
      summary = await (client as ISummaryClient).summary().timeout(
        ContinueChecks.timeout,
      );
    } on ReactAuthException {
      ended = true;
    } on Object {
      summary = null;
    } finally {
      _asking.remove(id);
    }
    if (_disposed || fleet.sessionFor(id) == null) return;
    final DateTime? ends = summary?.pairingEndsAt;
    if (ends != null) fleet.pairingEndsAt(id, ends);
    setState(() {
      if (summary != null) {
        _checks.answered(id, summary);
      } else {
        _checks.failed(id, ended: ended);
      }
    });
    final Duration? next = _checks.next(
      id,
      live: session.connection.state == ConnState.live,
    );
    if (next != null) _next[id] = Timer(next, () => _ask(id));
  }

  ContinueRow _row(FleetManager fleet, ServerSession session) {
    final String id = session.serverId;
    final RowCheck check = _checks.of(id);
    final ServerReach reach = reachOf(
      ended: fleet.pairingEnded(id) || check.answered == ServerReach.ended,
      live: session.connection.state,
      answered: check.answered,
    );
    return ContinueRow(
      id: id,
      name: session.label,
      reach: reach,
      summary: check.summary,
      stale: check.stale || reach == ServerReach.checking,
      tokenId: session.tokenId,
      endsSoon: reach == ServerReach.ended
          ? null
          : pairingEndsSoon(session.credential.expiresAt, DateTime.now()),
    );
  }

  Future<void> _forget(FleetController fleet, ContinueRow row) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.startForgetTitle, <String, Object?>{
        'server': row.name,
      }),
      message: reactorText(ReactorText.startForgetMessage, <String, Object?>{
        'token': row.tokenId ?? '',
      }),
      confirmLabel: reactorText(ReactorText.startForget),
      destructive: true,
    );
    if (!yes || !mounted) return;
    fleet.removeServer(row.id);
    ArcaneSonner.success(
      reactorText(ReactorText.startForgot, <String, Object?>{
        'server': row.name,
      }),
    );
  }

  static Widget _reachBadge(ServerReach reach) => switch (reach) {
    ServerReach.online => StatusBadge(
      reactorText(ReactorText.startOnline),
      tone: BadgeTone.ok,
      dot: true,
      attributes: const <String, String>{'data-reach': 'online'},
    ),
    ServerReach.checking => StatusBadge(
      reactorText(ReactorText.startChecking),
      dot: true,
      attributes: const <String, String>{'data-reach': 'checking'},
    ),
    ServerReach.unreachable => StatusBadge(
      reactorText(ReactorText.startNotReachable),
      tone: BadgeTone.error,
      dot: true,
      attributes: const <String, String>{'data-reach': 'unreachable'},
    ),
    ServerReach.ended => StatusBadge(
      reactorText(ReactorText.pairEndedState),
      tone: BadgeTone.error,
      attributes: const <String, String>{'data-reach': 'ended'},
    ),
  };

  /// "Players 40 · TPS 19.4 · Incidents 0" while the server answers.
  /// Each label stays with its number when the line wraps.
  static String _counts(ServerSummary summary) => <String>[
    '${reactorText(ReactorText.commonPlayers)}\u00a0${summary.players ?? '--'}',
    '${reactorText(ReactorText.overviewTps)}\u00a0${summary.tps?.toStringAsFixed(1) ?? '--'}',
    '${reactorText(ReactorText.incidentsTitle)}\u00a0${summary.openIncidents}',
  ].join(' · ');

  /// The server's name (a link to its Overview), its state and counts on
  /// one line, the pairing's end when it is near, and the New link field
  /// once the pairing ended.
  Widget _server(BuildContext context, ContinueRow row) {
    final PairDialogOpener? open = PairDialogScope.of(context);
    final ServerSummary? summary = row.summary;
    final bool ended = row.reach == ServerReach.ended;
    return dom.div(classes: 'ui-stat', <Widget>[
      if (ended)
        dom.span(classes: 'ui-list-link', <Widget>[oneLineText(row.name)])
      else
        dom.a(href: serverPath(row.id), classes: 'ui-list-link', <Widget>[
          oneLineText(row.name),
        ]),
      dom.div(classes: 'ui-tip-label', <Widget>[
        _reachBadge(row.reach),
        if ((row.reach == ServerReach.online ||
                row.reach == ServerReach.checking) &&
            summary != null)
          dom.span(
            classes: 'ui-caption',
            attributes: <String, String>{
              'data-counts': '',
              if (row.stale) 'data-stale': '',
            },
            <Widget>[Component.text(_counts(summary))],
          ),
      ]),
      if (row.endsSoon != null)
        dom.span(classes: 'ui-caption', <Widget>[
          Component.text(row.endsSoon!),
        ]),
      if (ended)
        PairLinkForm(
          id: 'new-link-${row.id}',
          label: reactorText(ReactorText.pairNewLink),
          onLink: (PairingLink link) => open?.call(link),
        ),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetController? fleet = FleetScope.of(context);
    final FleetManager? manager = fleet?.fleetManager;
    if (fleet == null || manager == null) {
      return const Component.fragment(<Widget>[]);
    }
    final List<ContinueRow> rows = <ContinueRow>[
      for (final ServerSession session in manager.sessions)
        _row(manager, session),
    ];
    return DataList<ContinueRow>(
      label: reactorText(ReactorText.startContinue),
      title: reactorText(ReactorText.startContinue),
      items: rows,
      header: false,
      columns: <ListColumn<ContinueRow>>[
        ListColumn<ContinueRow>(
          reactorText(ReactorText.commonServer),
          (ContinueRow row) => _server(context, row),
          width: 'minmax(0, 1fr)',
        ),
      ],
      actions: (ContinueRow row) => <RowAction>[
        if (row.reach != ServerReach.ended)
          RowAction(
            reactorText(ReactorText.startOpen),
            null,
            href: serverPath(row.id),
            attributes: <String, String>{
              'aria-label': reactorText(
                ReactorText.startOpenServer,
                <String, Object?>{'server': row.name},
              ),
              'data-testid': 'continue-open',
            },
          ),
      ],
      more: (ContinueRow row) => <AppMenuEntry>[
        AppMenuEntry(
          reactorText(ReactorText.startForget),
          () => _forget(fleet, row),
          icon: ArcaneIcon.trash2(),
          destructive: true,
        ),
      ],
    );
  }
}
