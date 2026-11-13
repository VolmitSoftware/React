library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/server_revisions.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/react_socket.dart';
import 'package:react_web/service/relay_connection_interface.dart';
import 'package:react_web/state/connection_manager.dart';

import 'support/fake_react_client.dart';

Future<void> settle([int ms = 20]) =>
    Future<void>.delayed(Duration(milliseconds: ms));

class _Socket implements IMetricsSocket {
  final StreamController<ServerSnapshot> controller =
      StreamController<ServerSnapshot>.broadcast();

  @override
  Stream<ServerSnapshot> get frames => controller.stream;

  @override
  Future<void> close() async {
    if (!controller.isClosed) await controller.close();
  }
}

class _PresenceClient extends FakeReactClient implements IServerPresence {
  final StreamController<bool> presence = StreamController<bool>.broadcast(
    sync: true,
  );

  @override
  Stream<bool> get serverOnline => presence.stream;
}

void main() {
  group('ConnectionManager access', () {
    test(
      'a 401 on a paired entry is terminal: revoked, polling stops',
      () async {
        final FakeReactClient client = FakeReactClient();
        client.metricsError = const ReactAuthException();
        final ConnectionManager manager = ConnectionManager(
          client,
          pollInterval: const Duration(milliseconds: 5),
          identityCheck: () async => true,
        );
        final List<ConnState> states = <ConnState>[];
        manager.stateChanges.listen(states.add);
        manager.start();
        await settle(60);

        expect(manager.access, ConnAccess.revoked);
        expect(manager.state, ConnState.offline);
        expect(client.count('GET /metrics'), 1);
        manager.start();
        await settle();
        expect(
          client.count('GET /metrics'),
          1,
          reason: 'revoked stays revoked',
        );
        manager.dispose();
      },
    );

    test('token_revoked is revoked without an identity check', () async {
      final FakeReactClient client = FakeReactClient();
      client.metricsError = const ReactRevoked();
      bool checked = false;
      final ConnectionManager manager = ConnectionManager(
        client,
        identityCheck: () async => checked = true,
      );
      manager.start();
      await settle();
      expect(manager.access, ConnAccess.revoked);
      expect(checked, isFalse);
      manager.dispose();
    });

    test('a 401 from a different server at the address is wrongServer and '
        'keeps retrying', () async {
      final FakeReactClient client = FakeReactClient();
      client.metricsError = const ReactAuthException();
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(milliseconds: 5),
        maxBackoff: const Duration(milliseconds: 5),
        identityCheck: () async => false,
      );
      manager.start();
      await settle(40);
      expect(manager.access, ConnAccess.wrongServer);
      expect(client.count('GET /metrics'), greaterThan(1));
      client.metricsError = null;
      await settle(40);
      expect(manager.access, ConnAccess.granted);
      expect(manager.state, ConnState.live);
      manager.dispose();
    });

    test('socket close code 4401 revokes the entry', () async {
      final FakeReactClient client = FakeReactClient();
      final _Socket socket = _Socket();
      final ConnectionManager manager = ConnectionManager(
        client,
        socket: socket,
      );
      manager.start();
      socket.controller.add(snapshotWith());
      socket.controller.addError(const ReactRevoked());
      await settle();
      expect(manager.access, ConnAccess.revoked);
      // No polling after it.
      expect(client.count('GET /metrics'), 0);
      manager.dispose();
    });
  });

  group('ConnectionManager transitions', () {
    test('first connect is not a reconnect; a recovery is', () async {
      final FakeReactClient client = FakeReactClient();
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(milliseconds: 5),
        maxBackoff: const Duration(milliseconds: 5),
      );
      final List<ConnTransition> transitions = <ConnTransition>[];
      manager.transitions.listen(transitions.add);
      manager.start();
      await settle();
      client.metricsError = const ReactUnavailable();
      await settle(30);
      client.metricsError = null;
      await settle(30);

      final List<ConnTransition> toLive = transitions
          .where((ConnTransition t) => t.to == ConnState.live)
          .toList();
      expect(toLive.first.reconnected, isFalse);
      expect(toLive.last.reconnected, isTrue);
      expect(toLive, hasLength(2));
      manager.dispose();
    });
  });

  group('ConnectionManager first sight', () {
    test('a server that never answered since the page loaded was not live '
        'before; one that drops after answering was', () async {
      final FakeReactClient client = FakeReactClient()
        ..metricsError = const ReactUnavailable();
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(milliseconds: 5),
        maxBackoff: const Duration(milliseconds: 5),
      );
      final List<ConnTransition> transitions = <ConnTransition>[];
      manager.transitions.listen(transitions.add);
      manager.start();
      await settle(30);
      expect(transitions, isNotEmpty);
      expect(transitions.every((ConnTransition t) => !t.wasLive), isTrue);
      client.metricsError = null;
      await settle(30);
      expect(manager.state, ConnState.live);
      transitions.clear();
      client.metricsError = const ReactUnavailable();
      await settle(30);
      final ConnTransition drop = transitions.firstWhere(
        (ConnTransition t) => t.to != ConnState.live,
      );
      expect(drop.wasLive, isTrue);
      manager.dispose();
    });
  });

  group('ConnectionManager relay presence', () {
    test('online:true skips the remaining backoff and polls at once', () async {
      final _PresenceClient client = _PresenceClient();
      client.metricsError = const ReactUnavailable();
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(seconds: 2),
        maxBackoff: const Duration(seconds: 5),
      );
      manager.start();
      await settle();
      expect(manager.state, ConnState.degraded);
      client.metricsError = null;
      client.presence.add(true);
      await settle();
      expect(manager.state, ConnState.live);
      expect(client.count('GET /metrics'), 2);
      manager.dispose();
    });
  });

  group('ConnectionManager sockets', () {
    test('a new socket makes no plain request; its first frame is the data', () async {
      final FakeReactClient client = FakeReactClient();
      final _Socket socket = _Socket();
      final ConnectionManager manager = ConnectionManager(
        client,
        socket: socket,
      );
      manager.start();
      await settle();
      // The server sends its last captured frame once the socket is in.
      expect(client.count('GET /metrics'), 0);
      expect(manager.state, isNot(ConnState.live));
      socket.controller.add(snapshotWith());
      await settle();
      expect(manager.state, ConnState.live);
      expect(manager.latestSnapshot, isNotNull);
      expect(client.count('GET /metrics'), 0);
      manager.dispose();
    });

    test('a socket that sends nothing falls back to polling', () async {
      final FakeReactClient client = FakeReactClient();
      final ConnectionManager manager = ConnectionManager(
        client,
        socket: _Socket(),
        pollInterval: const Duration(seconds: 5),
        firstFrameTimeout: const Duration(milliseconds: 10),
      );
      manager.start();
      await settle(40);
      // The first poll once the socket stayed quiet.
      expect(client.count('GET /metrics'), 1);
      expect(manager.state, ConnState.live);
      manager.dispose();
    });

    test('a factory returning null polls and asks again later', () async {
      final FakeReactClient client = FakeReactClient();
      int asked = 0;
      final ConnectionManager manager = ConnectionManager(
        client,
        pollInterval: const Duration(seconds: 5),
        socketFactory: () {
          asked++;
          return null;
        },
        socketRetryDelay: const Duration(milliseconds: 5),
      );
      manager.start();
      await settle(40);
      expect(manager.state, ConnState.live);
      expect(asked, greaterThan(2));
      manager.dispose();
    });

    test('a frame that only moves revisions still reaches listeners', () async {
      // A poll would fail here: only frames count.
      final FakeReactClient client = FakeReactClient()
        ..metricsError = const ReactUnavailable('down');
      final _Socket socket = _Socket();
      final ConnectionManager manager = ConnectionManager(
        client,
        socket: socket,
      );
      final List<ServerSnapshot> seen = <ServerSnapshot>[];
      manager.snapshots.listen(seen.add);
      manager.start();
      final Map<RevisionKind, int> first = <RevisionKind, int>{
        RevisionKind.tweaks: 1,
      };
      socket.controller.add(snapshotWith(seq: 3, revisions: first));
      socket.controller.add(snapshotWith(seq: 3, revisions: first));
      socket.controller.add(
        snapshotWith(
          seq: 3,
          revisions: <RevisionKind, int>{RevisionKind.tweaks: 2},
        ),
      );
      await settle();
      expect(seen, hasLength(2));
      expect(seen.last.revisions![RevisionKind.tweaks], 2);
      expect(manager.samplerHistory('ticks-per-second'), hasLength(1));
      manager.dispose();
    });
  });
}
