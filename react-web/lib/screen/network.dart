/// Network (proxy only): the proxy's own numbers and every server in its
/// list. Servers running React open their full pages through the proxy.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/network_info.dart';
import '../model/server_credential.dart';
import '../model/units.dart';
import '../service/react_client.dart';
import '../state/fleet_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/error_text.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';

const Duration networkRefreshInterval = Duration(seconds: 3);

const String _none = '--';

/// Polls the network of the proxy in scope and lists each of its React
/// servers as an entry reached through it.
class NetworkScreen extends StatefulWidget {
  const NetworkScreen({super.key});

  @override
  State<NetworkScreen> createState() => _NetworkScreenState();
}

class _NetworkScreenState extends State<NetworkScreen> {
  INetworkClient? _client;
  ServerSession? _session;
  NetworkInfo? _info;
  Object? _error;
  bool _loading = false;
  Timer? _timer;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ServerSession? session = ServerSessionScope.of(context);
    final INetworkClient? client = session?.clientAs<INetworkClient>();
    if (identical(client, _client)) return;
    _timer?.cancel();
    _timer = null;
    _client = client;
    _session = session;
    _info = null;
    _error = null;
    _loading = false;
    if (client == null) return;
    unawaited(_load(client));
    _timer = Timer.periodic(networkRefreshInterval, (Timer _) {
      unawaited(_load(client));
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load(INetworkClient client) async {
    if (_loading || !identical(client, _client)) return;
    _loading = true;
    try {
      final NetworkInfo info = await client.network();
      if (!mounted || !identical(client, _client)) return;
      _syncMembers(info);
      setState(() {
        _info = info;
        _error = null;
      });
    } on Object catch (error) {
      if (!mounted || !identical(client, _client)) return;
      setState(() => _error = error);
    } finally {
      if (identical(client, _client)) _loading = false;
    }
  }

  void _syncMembers(NetworkInfo info) {
    final ServerSession? session = _session;
    final FleetController? fleet = FleetScope.of(context);
    if (session == null || fleet == null) return;
    if (session.credential.networkMember) return;
    final ({List<String> opened, List<String> removed}) changed = fleet
        .fleetManager
        .syncNetwork(session.serverId, info.servers);
    for (final String id in changed.removed) {
      fleet.removeServer(id);
    }
    for (final String id in changed.opened) {
      fleet.trackPaired(id);
    }
  }

  void _retry() {
    final INetworkClient? client = _client;
    if (client == null || _loading) return;
    setState(() => _error = null);
    unawaited(_load(client));
  }

  /// The address of [server]'s pages when it is listed as an entry.
  String? _memberPath(NetworkServer server) {
    final ServerSession? session = _session;
    final FleetController? fleet = FleetScope.of(context);
    if (!server.react || session == null || fleet == null) return null;
    final String id = ServerCredential.networkMemberId(
      session.serverId,
      server.name,
    );
    return fleet.fleetManager.sessionFor(id) == null ? null : serverPath(id);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final NetworkInfo? info = _info;
    final Object? error = _error;
    if (_client == null) {
      return EmptyState(
        message: reactorText(ReactorText.networkNotProxy),
        description: reactorText(ReactorText.networkNotProxyDescription),
      );
    }
    if (info == null) {
      if (error != null) {
        return ErrorState(
          message: localizedReactorError(error),
          onRetry: _retry,
        );
      }
      return const LoadingState();
    }
    return NetworkView(info: info, memberPath: _memberPath);
  }
}

/// The Network page body for one network answer.
class NetworkView extends StatelessWidget {
  const NetworkView({required this.info, this.memberPath, super.key});

  final NetworkInfo info;

  /// Where a server's own pages open; null leaves its row without a link.
  final String? Function(NetworkServer server)? memberPath;

  static String _proxySoftware(NetworkProxyInfo proxy) {
    final String text = '${proxy.kind} ${proxy.version}'.trim();
    return text.isEmpty ? _none : text;
  }

  static Widget _status(NetworkServer server) => switch (server.status) {
    NetworkServerStatus.online => StatusBadge(
      reactorText(ReactorText.startOnline),
      tone: BadgeTone.ok,
      dot: true,
    ),
    NetworkServerStatus.offline => StatusBadge(
      reactorText(ReactorText.startNotReachable),
      tone: BadgeTone.error,
      dot: true,
    ),
    NetworkServerStatus.unknown => StatusBadge(
      reactorText(ReactorText.networkUnknown),
      dot: true,
    ),
  };

  static String _ping(NetworkServer server) {
    final int? ping = server.pingMs;
    return ping == null ? _none : withUnit(groupThousands(ping), 'ms');
  }

  static String _tps(NetworkServer server) {
    final double? tps = server.tps;
    return tps == null ? _none : tps.toStringAsFixed(1);
  }

  static String _mspt(NetworkServer server) {
    final double? mspt = server.mspt;
    return mspt == null ? _none : withUnit(mspt.toStringAsFixed(1), 'ms');
  }

  static String _memory(NetworkServer server) {
    final int? used = server.memoryUsedBytes;
    final int? max = server.memoryMaxBytes;
    if (used == null) return _none;
    if (max == null || max <= 0) return humanBytes(used.toDouble());
    return '${humanBytes(used.toDouble())} / ${humanBytes(max.toDouble())}';
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final NetworkProxyInfo proxy = info.proxy;
    final bool anyBasic = info.servers.any((NetworkServer s) => !s.react);
    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            MetricTile(
              label: reactorText(ReactorText.networkProxySoftware),
              value: _proxySoftware(proxy),
            ),
            MetricTile(
              label: reactorText(ReactorText.commonPlayers),
              value: groupThousands(proxy.players),
            ),
            MetricTile(
              label: reactorText(ReactorText.networkServersOnline),
              value: '${proxy.online} / ${proxy.servers}',
              tone: proxy.online < proxy.servers
                  ? BadgeTone.warning
                  : BadgeTone.neutral,
            ),
            MetricTile(
              label: reactorText(ReactorText.networkFullDetails),
              value: groupThousands(proxy.react),
            ),
          ],
        ),
        DataList<NetworkServer>(
          title: reactorText(ReactorText.fleetServers),
          label: reactorText(ReactorText.fleetServers),
          items: info.servers,
          emptyMessage: reactorText(ReactorText.networkEmpty),
          href: memberPath,
          columns: <ListColumn<NetworkServer>>[
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.commonServer),
              (NetworkServer server) => server.name,
              width: 'minmax(0, 2fr)',
            ),
            ListColumn<NetworkServer>(
              reactorText(ReactorText.networkStatus),
              _status,
            ),
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.commonPlayers),
              (NetworkServer server) => groupThousands(server.players),
              numeric: true,
            ),
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.networkLatency),
              _ping,
              numeric: true,
            ),
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.overviewTps),
              _tps,
              numeric: true,
            ),
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.commonTickTime),
              _mspt,
              numeric: true,
            ),
            ListColumn<NetworkServer>.text(
              reactorText(ReactorText.memoryTitle),
              _memory,
              numeric: true,
              width: 'minmax(0, 1.5fr)',
            ),
            ListColumn<NetworkServer>(
              reactorText(ReactorText.networkDetails),
              (NetworkServer server) => server.react
                  ? StatusBadge(
                      reactorText(ReactorText.networkFull),
                      tone: BadgeTone.info,
                    )
                  : StatusBadge(reactorText(ReactorText.networkBasic)),
            ),
          ],
          actions: (NetworkServer server) {
            final String? path = memberPath?.call(server);
            return <RowAction>[
              if (path != null)
                RowAction(reactorText(ReactorText.startOpen), null, href: path),
            ];
          },
        ),
        if (anyBasic)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(reactorText(ReactorText.networkBasicHint)),
          ]),
      ],
    );
  }
}
