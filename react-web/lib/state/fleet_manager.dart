import 'dart:convert';
import 'dart:developer' as developer;

import 'package:jaspr/jaspr.dart' show ChangeNotifier, kIsWeb;

import '../model/server_capabilities.dart';
import '../model/server_credential.dart';
import '../service/happy_eyeballs_client.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../service/react_log_socket.dart';
import '../service/react_socket.dart';
import 'connection_manager.dart';
import 'fleet_live_model.dart';
import 'memory_fleet_storage.dart';
import 'server_session.dart';

typedef ReactClientFactory = IReactClient Function(ServerCredential cred);

typedef RelayClientFactory = IReactClient? Function(ServerCredential cred);

abstract class FleetStorage {
  String? read(String key);
  void write(String key, String value);
  void remove(String key);
}

/// The paired entries and the one [ServerSession] each, one entry per
/// server. Notifies listeners when entries are added, removed, renamed or
/// paired again.
///
/// Entries live in localStorage ([storage]). The bearer of an entry paired
/// with Remember this browser off lives in sessionStorage ([session]), so
/// the pairing ends with the tab, as in Animus.
class FleetManager extends ChangeNotifier {
  static const String storageKey = 'reactor.fleet';

  /// sessionStorage key of a tab-only entry's bearer.
  static String tokenKey(String id) => 'reactor.token.$id';

  final FleetStorage storage;
  final FleetStorage session;
  final ReactClientFactory clientFactory;
  final RelayClientFactory? relayClientFactory;

  final List<ServerCredential> _servers = <ServerCredential>[];
  final Map<String, ServerSession> _sessions = <String, ServerSession>{};

  /// Entries whose pairing ended before this page: paired with an RCT2
  /// code, or kept for a tab that closed.
  final Set<String> _ended = <String>{};
  String? _activeId;
  bool _disposed = false;

  FleetManager({
    required this.storage,
    required this.clientFactory,
    this.relayClientFactory,
    FleetStorage? session,
  }) : session = session ?? InMemoryFleetStorage() {
    _tryLoadSync();
  }

  List<ServerCredential> get servers =>
      List<ServerCredential>.unmodifiable(_servers);

  /// Sessions in fleet order.
  List<ServerSession> get sessions => <ServerSession>[
    for (final ServerCredential cred in _servers) ?_sessions[cred.id],
  ];

  ServerSession? sessionFor(String id) => _sessions[id];

  ServerCredential? get activeServer {
    if (_activeId == null) return null;
    for (final ServerCredential c in _servers) {
      if (c.id == _activeId) return c;
    }
    return null;
  }

  ConnectionManager? get activeManager =>
      _activeId == null ? null : _sessions[_activeId]?.connection;

  void setActive(String id) {
    _activeId = id;
  }

  Future<void> load() async {
    _disposeAll();
    _servers.clear();
    _ended.clear();
    _tryLoadSync();
    _changed();
  }

  /// The entry for the same server as [cred], if any.
  ServerCredential? entryFor(ServerCredential cred) {
    for (final ServerCredential existing in _servers) {
      if (existing.sameServerAs(cred)) return existing;
    }
    return null;
  }

  /// Saves a pairing and opens its session. A server already listed keeps
  /// its entry (id, tags and alerts) and takes the new pairing. Returns the
  /// id the entry is saved under.
  String add(ServerCredential cred) {
    if (_disposed) throw const ReactUnavailable('Fleet closed');
    final ServerCredential? existing = entryFor(cred);
    final ServerCredential saved = existing == null
        ? cred
        : cred.withId(existing.id);
    final int index = _servers.indexWhere(
      (ServerCredential c) => c.id == saved.id,
    );
    if (index >= 0) {
      _sessions.remove(saved.id)?.dispose();
      _servers[index] = saved;
    } else {
      _servers.add(saved);
    }
    _ended.remove(saved.id);
    _sessions[saved.id] = _buildSession(saved, _resolveClient(saved));
    _persist();
    _changed();
    return saved.id;
  }

  /// Whether the server no longer accepts [id]'s pairing, or it ended in
  /// this browser.
  bool pairingEnded(String id) =>
      _ended.contains(id) ||
      _sessions[id]?.connection.access == ConnAccess.revoked;

  /// Takes the pairing's end the server reports (it moves with every use).
  /// Stored only once it moved by an hour, so storage is not rewritten on
  /// every answer.
  void pairingEndsAt(String id, DateTime expiresAt) {
    final int index = _servers.indexWhere((ServerCredential c) => c.id == id);
    if (index < 0) return;
    final ServerCredential cred = _servers[index];
    final DateTime? known = cred.expiresAt;
    if (known != null &&
        expiresAt.difference(known).abs() < const Duration(hours: 1)) {
      return;
    }
    _servers[index] = cred.copyWith(expiresAt: expiresAt.toUtc());
    _sessions[id]?.updateCredential(_servers[index]);
    _persist();
    _changed();
  }

  void rename(String id, String label) {
    final int index = _servers.indexWhere((ServerCredential c) => c.id == id);
    if (index < 0) return;
    _servers[index] = _servers[index].copyWith(label: label);
    _sessions[id]?.updateCredential(_servers[index]);
    _persist();
    _changed();
  }

  /// Forgets [id] in this browser: its entry and its bearer.
  void remove(String id) {
    _servers.removeWhere((ServerCredential c) => c.id == id);
    _sessions.remove(id)?.dispose();
    _ended.remove(id);
    session.remove(tokenKey(id));
    if (_activeId == id) _activeId = null;
    _persist();
    _changed();
  }

  void clearAll() {
    for (final ServerCredential cred in _servers) {
      session.remove(tokenKey(cred.id));
    }
    _disposeAll();
    _servers.clear();
    _ended.clear();
    _activeId = null;
    storage.remove(storageKey);
    _changed();
  }

  void importReplace(List<ServerCredential> creds) {
    _disposeAll();
    _servers.clear();
    _ended.clear();
    for (final ServerCredential cred in creds) {
      _open(cred);
    }
    _activeId = creds.isEmpty ? null : creds.first.id;
    _persist();
    _changed();
  }

  /// Restarts every entry's connection (the shell's Reconnect action).
  /// Revoked entries stay revoked.
  void reconnectAll() {
    for (final ServerSession session in _sessions.values) {
      session.connection.start();
    }
  }

  /// Live sources for `FleetLiveObserver`, one per entry, carrying the
  /// fingerprint so rollups count a server paired twice once.
  List<FleetLiveSource> liveSources() => <FleetLiveSource>[
    for (final ServerSession session in sessions)
      FleetLiveSource.forSession(session),
  ];

  ConnectionManager? managerFor(String id) => _sessions[id]?.connection;

  IReactClient? resolvedClientFor(String id) => _sessions[id]?.client;

  T? _clientAs<T>(String id) {
    final IReactClient? client = _sessions[id]?.client;
    return client is T ? client as T : null;
  }

  IHistoryClient? historyClientFor(String id) => _clientAs<IHistoryClient>(id);

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _disposeAll();
    super.dispose();
  }

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  void _disposeAll() {
    for (final ServerSession session in _sessions.values) {
      session.dispose();
    }
    _sessions.clear();
  }

  /// Lists [cred] with its session. An entry without a bearer, or paired
  /// with an RCT2 code, is listed as ended: its session never connects.
  void _open(ServerCredential cred) {
    final ServerCredential entry = cred.linked ? cred : cred.withoutBearer();
    _servers.add(entry);
    final ServerSession opened = _buildSession(entry, _resolveClient(entry));
    _sessions[entry.id] = opened;
    if (entry.bearer.isEmpty) {
      _ended.add(entry.id);
      opened.connection.endPairing();
    }
  }

  void _tryLoadSync() {
    final String? raw = storage.read(storageKey);
    if (raw == null) return;
    try {
      final List<dynamic> list = jsonDecode(raw) as List<dynamic>;
      for (final dynamic item in list) {
        final ServerCredential stored = ServerCredential.fromJson(
          item as Map<String, dynamic>,
        );
        final String? kept = stored.bearer.isEmpty
            ? session.read(tokenKey(stored.id))
            : null;
        _open(kept == null ? stored : stored.copyWith(bearer: kept));
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
    if (!cred.hasRelay) return clientFactory(cred);
    final IReactClient? direct = cred.hasDirect ? clientFactory(cred) : null;
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

  ServerSession _buildSession(ServerCredential cred, IReactClient client) =>
      ServerSession(
        credential: cred,
        client: client,
        connection: _buildManager(cred, client),
        logSocketFactory: kIsWeb && cred.hasDirect
            ? () => createLogSocket(cred)
            : null,
        storage: storage,
      );

  ConnectionManager _buildManager(ServerCredential cred, IReactClient client) {
    Future<bool> identityCheck() => _sameServer(cred, client);
    final Duration maxBackoff = cred.hasRelay
        ? const Duration(seconds: 5)
        : const Duration(seconds: 30);
    if (!kIsWeb || !cred.hasDirect) {
      return ConnectionManager(
        client,
        maxBackoff: maxBackoff,
        identityCheck: identityCheck,
      );
    }
    return ConnectionManager(
      client,
      maxBackoff: maxBackoff,
      identityCheck: identityCheck,
      socketFactory: () {
        if (client is HappyEyeballsClient &&
            client.activePath == RelayPath.relay) {
          client.reprobe();
          return null;
        }
        return createMetricsSocket(cred);
      },
    );
  }

  /// Unauthenticated ping: does the address still serve the paired server?
  static Future<bool> _sameServer(
    ServerCredential cred,
    IReactClient client,
  ) async {
    final String? pinned = cred.fingerprint;
    if (pinned == null || pinned.isEmpty || client is! IPingClient) {
      return true;
    }
    try {
      final ServerCapabilities capabilities = await (client as IPingClient)
          .ping();
      return capabilities.serverFingerprint == pinned;
    } on ReactWrongServer {
      return false;
    }
  }

  /// Writes the entries to localStorage; a tab-only entry's bearer goes to
  /// sessionStorage instead.
  void _persist() {
    final String encoded = jsonEncode(<Map<String, dynamic>>[
      for (final ServerCredential cred in _servers)
        (cred.remember ? cred : cred.withoutBearer()).toJson(),
    ]);
    storage.write(storageKey, encoded);
    for (final ServerCredential cred in _servers) {
      if (!cred.remember && cred.bearer.isNotEmpty) {
        session.write(tokenKey(cred.id), cred.bearer);
      } else {
        session.remove(tokenKey(cred.id));
      }
    }
  }
}
