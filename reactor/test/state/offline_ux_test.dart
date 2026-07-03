library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';
import 'package:reactor/state/connection_manager.dart';

// ignore_for_file: avoid_redundant_argument_values

// ---------------------------------------------------------------------------
// Fake client
// ---------------------------------------------------------------------------

class _FakeMetricsClient implements IMetricsClient {
  final List<Object> _responses;
  int _index = 0;

  _FakeMetricsClient(this._responses);

  @override
  Future<ServerSnapshot> metrics() async {
    final Object resp = _index < _responses.length
        ? _responses[_index++]
        : _responses.last;
    if (resp is Future) return await (resp as Future<ServerSnapshot>);
    if (resp is ServerSnapshot) return resp;
    throw resp as Exception;
  }
}

ServerSnapshot _makeSnapshot() => ServerSnapshot(
      byId: <String, SamplerSample>{
        'cpu': SamplerSample(
          id: 'cpu',
          name: 'CPU',
          suffix: '%',
          value: 50.0,
          display: '50.0',
          min: 0.0,
          max: 100.0,
          history: <double>[],
        ),
      },
      at: DateTime.now(),
      seq: 0,
    );

Future<void> pump([int count = 20]) async {
  for (int i = 0; i < count; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  // -------------------------------------------------------------------------
  // ConnectionManager → offline/recovery logic
  // -------------------------------------------------------------------------
  group('ConnectionManager offline transitions', () {
    test('reaches offline state after two consecutive failures', () async {
      final Completer<ServerSnapshot> blocker = Completer<ServerSnapshot>();
      addTearDown(() {
        if (!blocker.isCompleted) blocker.complete(_makeSnapshot());
      });
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          blocker.future,
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(60);
      expect(manager.state, equals(ConnState.offline));
      manager.stop();
    });

    test('recovers to live after successful poll following offline', () async {
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      manager.start();
      await pump(80);
      expect(manager.state, equals(ConnState.live));
      manager.stop();
    });

    test('stateChanges emits degraded then offline on consecutive failures',
        () async {
      final List<ConnState> emitted = <ConnState>[];
      final Completer<ServerSnapshot> blocker = Completer<ServerSnapshot>();
      addTearDown(() {
        if (!blocker.isCompleted) blocker.complete(_makeSnapshot());
      });
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          blocker.future,
        ]),
        pollInterval: Duration.zero,
      );
      final StreamSubscription<ConnState> sub =
          manager.stateChanges.listen(emitted.add);
      manager.start();
      await pump(80);
      manager.stop();
      await sub.cancel();
      expect(emitted, containsAllInOrder(<ConnState>[ConnState.degraded, ConnState.offline]));
    });

    test('stateChanges emits live on recovery from offline', () async {
      final List<ConnState> emitted = <ConnState>[];
      final ConnectionManager manager = ConnectionManager(
        _FakeMetricsClient(<Object>[
          const ReactUnavailable('fail1'),
          const ReactUnavailable('fail2'),
          _makeSnapshot(),
        ]),
        pollInterval: Duration.zero,
      );
      final StreamSubscription<ConnState> sub =
          manager.stateChanges.listen(emitted.add);
      manager.start();
      await pump(100);
      manager.stop();
      await sub.cancel();
      expect(emitted.last, equals(ConnState.live));
    });
  });

  // -------------------------------------------------------------------------
  // ReactorShell offline UX rendering
  // -------------------------------------------------------------------------
  group('ReactorShell offline UX', () {
    testServer(
      'shows offline banner text when server state is offline',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.offline),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Connection lost'),
          isTrue,
          reason: '_OfflineBanner must render "Connection lost" text when a server is offline',
        );
      },
    );

    testServer(
      'shows Reconnect button when any server is offline',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.offline),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Reconnect'),
          isTrue,
          reason: 'shell must render Reconnect button when a server is offline',
        );
      },
    );

    testServer(
      'shows Reconnect button when any server is degraded',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.degraded),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Reconnect'),
          isTrue,
          reason:
              'shell must render Reconnect button when a server is degraded',
        );
      },
    );

    testServer(
      'does not show Reconnect button when all servers are live',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.live),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Reconnect'),
          isFalse,
          reason:
              'shell must not render Reconnect button when all servers are live',
        );
      },
    );

    testServer(
      'does not show Reconnect button when no servers are present',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(const ReactorShell()));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Reconnect'),
          isFalse,
          reason: 'shell must not render Reconnect button with no servers',
        );
      },
    );

    testServer(
      'applies dim style to offline server sidebar section',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'DimServer', state: ConnState.offline),
          ],
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('opacity'),
          isTrue,
          reason:
              'offline server section must carry an opacity style for visual dimming',
        );
      },
    );

    testServer(
      'ReactorShell renders Reconnect button when onReconnect is wired (SSR render presence only)',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ReactorShell(
          servers: const <ServerEntry>[
            ServerEntry(id: 'srv1', name: 'Alpha', state: ConnState.offline),
          ],
          onReconnect: () {},
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, 200);
        expect(
          res.body.contains('Reconnect'),
          isTrue,
          reason: 'Reconnect button must be present when onReconnect is wired',
        );
      },
    );
  });
}
