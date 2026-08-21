import 'dart:convert';
import 'dart:developer' as developer;

import 'package:jaspr/jaspr.dart' show kIsWeb;

import '../model/server_credential.dart';
import '../service/happy_eyeballs_client.dart';
import '../service/react_client.dart';
import '../service/react_log_socket.dart';
import '../service/react_socket.dart';
import 'connection_manager.dart';

typedef ReactClientFactory = IReactClient Function(ServerCredential cred);

typedef RelayClientFactory = IReactClient? Function(ServerCredential cred);

abstract class FleetStorage {
  String? read(String key);
  void write(String key, String value);
  void remove(String key);
}

class FleetManager {
  static const String storageKey = 'reactor.fleet';

  final FleetStorage storage;
  final ReactClientFactory clientFactory;
  final RelayClientFactory? relayClientFactory;

  final List<ServerCredential> _servers = <ServerCredential>[];
  final Map<String, ConnectionManager> _managers =
      <String, ConnectionManager>{};
  final Map<String, IReactClient> _clients = <String, IReactClient>{};
  String? _activeId;

  FleetManager({
    required this.storage,
    required this.clientFactory,
    this.relayClientFactory,
  }) {
    _tryLoadSync();
  }

  List<ServerCredential> get servers =>
      List<ServerCredential>.unmodifiable(_servers);

  ServerCredential? get activeServer {
    if (_activeId == null) return null;
    try {
      return _servers.firstWhere((ServerCredential c) => c.id == _activeId);
    } on StateError {
      return null;
    }
  }

  ConnectionManager? get activeManager {
    if (_activeId == null) return null;
    return _managers[_activeId];
  }

  void setActive(String id) {
    _activeId = id;
  }

  Future<void> load() async {
    _disposeAll();
    _servers.clear();
    _managers.clear();
    _clients.clear();
    _tryLoadSync();
  }

  Future<void> add(ServerCredential cred) async {
    final IReactClient client = _resolveClient(cred);
    await client.identity();
    _clients[cred.id] = client;
    _servers.add(cred);
    _managers[cred.id] = _buildManager(cred, client);
    _persist();
  }

  void rename(String id, String label) {
    final int index = _servers.indexWhere((ServerCredential c) => c.id == id);
    if (index < 0) return;
    _servers[index] = _servers[index].copyWith(label: label);
    _persist();
  }

  void remove(String id) {
    _servers.removeWhere((ServerCredential c) => c.id == id);
    final ConnectionManager? manager = _managers.remove(id);
    manager?.dispose();
    _clients.remove(id);
    if (_activeId == id) _activeId = null;
    _persist();
  }

  void clearAll() {
    _disposeAll();
    _servers.clear();
    _managers.clear();
    _clients.clear();
    _activeId = null;
    storage.remove(storageKey);
  }

  void importReplace(List<ServerCredential> creds) {
    _disposeAll();
    _servers.clear();
    _managers.clear();
    _clients.clear();
    for (final ServerCredential cred in creds) {
      final IReactClient client = _resolveClient(cred);
      _clients[cred.id] = client;
      _servers.add(cred);
      _managers[cred.id] = _buildManager(cred, client);
    }
    _activeId = creds.isEmpty ? null : creds.first.id;
    _persist();
  }

  ConnectionManager? managerFor(String id) => _managers[id];

  IReactClient? resolvedClientFor(String id) => _clients[id];

  IHeatmapClient? heatmapClientFor(String id) {
    final IReactClient? c = _clients[id];
    return c is IHeatmapClient ? c as IHeatmapClient : null;
  }

  IControlClient? controlClientFor(String id) {
    final IReactClient? c = _clients[id];
    return c is IControlClient ? c as IControlClient : null;
  }

  IRoleClient? roleClientFor(String id) {
    final IReactClient? c = _clients[id];
    return c is IRoleClient ? c as IRoleClient : null;
  }

  IOperateClient? operateClientFor(String id) {
    final IReactClient? c = _clients[id];
    return c is IOperateClient ? c as IOperateClient : null;
  }

  IConsoleClient? consoleClientFor(String id) {
    final IReactClient? c = _clients[id];
    return c is IConsoleClient ? c as IConsoleClient : null;
  }

  ILogSocket Function()? logSocketFactoryFor(String id) {
    if (!kIsWeb) return null;
    ServerCredential? cred;
    for (final ServerCredential s in _servers) {
      if (s.id == id) {
        cred = s;
        break;
      }
    }
    if (cred == null) return null;
    if (cred.relayUrl != null && cred.relayUrl!.isNotEmpty) return null;
    final ServerCredential resolved = cred;
    return () => createLogSocket(resolved);
  }

  void dispose() {
    _disposeAll();
    _managers.clear();
  }

  void _disposeAll() {
    for (final ConnectionManager manager in _managers.values) {
      manager.dispose();
    }
  }

  void _tryLoadSync() {
    final String? raw = storage.read(storageKey);
    if (raw == null) return;
    try {
      final List<dynamic> list = jsonDecode(raw) as List<dynamic>;
      for (final dynamic item in list) {
        final ServerCredential cred = ServerCredential.fromJson(
          item as Map<String, dynamic>,
        );
        final IReactClient client = _resolveClient(cred);
        _clients[cred.id] = client;
        _servers.add(cred);
        _managers[cred.id] = _buildManager(cred, client);
      }
    } on Object catch (e) {
      developer.log(
        'corrupt fleet storage, starting fresh: $e',
        name: 'FleetManager',
        level: 500,
      );
    }
  }

  IReactClient _resolveClient(ServerCredential cred) {
    if (cred.relayUrl == null || cred.relayUrl!.isEmpty) {
      return clientFactory(cred);
    }
    final IReactClient? direct = (cred.host.isNotEmpty && cred.port > 0)
        ? clientFactory(cred)
        : null;
    final IReactClient? relay = relayClientFactory?.call(cred);
    if (direct == null && relay == null) {
      return clientFactory(cred);
    }
    return HappyEyeballsClient(
      direct: direct,
      relay: relay,
      pinnedFingerprint: cred.fingerprint,
    );
  }

  ConnectionManager _buildManager(ServerCredential cred, IReactClient client) {
    if (cred.relayUrl != null && cred.relayUrl!.isNotEmpty) {
      return ConnectionManager(client);
    }
    if (!kIsWeb) {
      return ConnectionManager(client);
    }
    return ConnectionManager(
      client,
      socket: createMetricsSocket(cred),
      socketFactory: () => createMetricsSocket(cred),
    );
  }

  void _persist() {
    final String encoded = jsonEncode(
      _servers.map((ServerCredential c) => c.toJson()).toList(),
    );
    storage.write(storageKey, encoded);
  }
}
