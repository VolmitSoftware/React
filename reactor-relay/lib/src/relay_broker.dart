library;

import 'package:reactor_relay/src/relay_frame.dart';
import 'package:reactor_relay/src/relay_sink.dart';

class RelayBroker {
  final Map<String, RelaySink> _servers = <String, RelaySink>{};
  final Map<String, Set<RelaySink>> _appSubs = <String, Set<RelaySink>>{};

  void registerServer(String serverId, RelaySink agent) {
    final RelaySink? existing = _servers[serverId];
    if (existing != null && !identical(existing, agent)) {
      existing.closeWithError('replaced by new registration');
    }
    _servers[serverId] = agent;
  }

  void unregisterServer(String serverId, RelaySink agent) {
    if (identical(_servers[serverId], agent)) {
      _servers.remove(serverId);
    }
  }

  void subscribeApp(String serverId, RelaySink app) {
    _appSubs.putIfAbsent(serverId, () => <RelaySink>{}).add(app);
    if (!_servers.containsKey(serverId)) {
      app.send(RelayFrame(
        type: RelayFrameType.error,
        serverId: serverId,
        payload: <String, dynamic>{'message': 'server offline'},
      ));
    }
  }

  void unsubscribeApp(String serverId, RelaySink app) {
    _appSubs[serverId]?.remove(app);
  }

  void routeFromApp(String serverId, RelayFrame frame) {
    final RelaySink? agent = _servers[serverId];
    if (agent == null) return;
    agent.send(frame);
  }

  void routeFromServer(String serverId, RelayFrame frame) {
    final Set<RelaySink>? subs = _appSubs[serverId];
    if (subs == null) return;
    for (final RelaySink app in subs) {
      app.send(frame);
    }
  }

  bool isOnline(String serverId) => _servers.containsKey(serverId);

  int get onlineCount => _servers.length;
}
