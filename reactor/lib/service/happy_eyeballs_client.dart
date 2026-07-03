library;

import 'dart:async';

import '../model/identity_info.dart';
import '../model/server_snapshot.dart';
import 'react_client.dart';
import 'react_exceptions.dart';
import 'relay_identity.dart';

enum RelayPath { none, direct, relay }

class HappyEyeballsClient implements IReactClient {
  final IReactClient? _direct;
  final IReactClient? _relay;
  final String? _pinnedFingerprint;
  RelayPath _active;

  HappyEyeballsClient({
    IReactClient? direct,
    IReactClient? relay,
    String? pinnedFingerprint,
  })  : _direct = direct,
        _relay = relay,
        _pinnedFingerprint = pinnedFingerprint,
        _active = RelayPath.none {
    if (direct == null && relay == null) {
      throw ArgumentError('At least one of direct or relay must be non-null');
    }
  }

  RelayPath get activePath => _active;

  void _markStale() {
    _active = RelayPath.none;
  }

  Future<IReactClient> _resolve() async {
    if (_active != RelayPath.none) {
      final IReactClient? cached =
          _active == RelayPath.direct ? _direct : _relay;
      if (cached != null) return cached;
    }

    final Completer<IReactClient> winner = Completer<IReactClient>();
    int outstanding = 0;
    Object? lastError;
    bool pinMismatchSeen = false;

    if (_direct != null) outstanding++;
    if (_relay != null) outstanding++;

    void checkDone() {
      if (outstanding <= 0 && !winner.isCompleted) {
        if (pinMismatchSeen) {
          winner.completeError(const ReactAuthException(
              'server identity mismatch (possible relay MITM)'));
        } else {
          winner.completeError(
              lastError ?? const ReactUnavailable('No path available'));
        }
      }
    }

    Future<void> probe(RelayPath path, IReactClient client) async {
      try {
        final IdentityInfo id = await client.identity();
        outstanding--;
        if (RelayIdentity.pinMatches(
            pinnedFingerprint: _pinnedFingerprint,
            identityServerId: id.serverId)) {
          if (!winner.isCompleted) {
            _active = path;
            winner.complete(client);
          }
        } else {
          pinMismatchSeen = true;
          lastError = const ReactAuthException(
              'server identity mismatch (possible relay MITM)');
          checkDone();
        }
      } catch (e) {
        outstanding--;
        lastError = e;
        checkDone();
      }
    }

    if (_direct != null) {
      unawaited(probe(RelayPath.direct, _direct));
    }
    if (_relay != null) {
      unawaited(probe(RelayPath.relay, _relay));
    }

    return winner.future;
  }

  @override
  Future<IdentityInfo> identity() async {
    final IReactClient c = await _resolve();
    return c.identity();
  }

  @override
  Future<ServerSnapshot> metrics() async {
    final IReactClient c = await _resolve();
    try {
      return await c.metrics();
    } on ReactUnavailable {
      _markStale();
      rethrow;
    }
  }
}
