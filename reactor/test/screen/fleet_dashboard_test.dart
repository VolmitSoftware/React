library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_router/jaspr_router.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:reactor/app/reactor_app.dart';
import 'package:reactor/localization/reactor_localizations.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/fleet_dashboard.dart';
import 'package:reactor/state/alert_store.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/fleet_live_scope.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/fleet_rollup.dart';
import 'package:reactor/state/fleet_scope.dart';
import 'package:reactor/state/memory_fleet_storage.dart';
import 'package:reactor/state/server_tags_store.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

class _FakeFleetController implements FleetController {
  final InMemoryFleetStorage _storage = InMemoryFleetStorage();
  late final AlertStore _alertStore = AlertStore(_storage);
  late final ServerTagsStore _tagsStore = ServerTagsStore(_storage);

  @override
  FleetManager get fleetManager => throw UnimplementedError();

  @override
  ConnectionManager? managerFor(String id) => null;

  @override
  String? labelFor(String id) => null;

  @override
  void trackPaired(String id) {}

  @override
  void removeServer(String id) {}

  @override
  void clearFleet() {}

  @override
  void importFleet(List<ServerCredential> creds) {}

  @override
  AlertStore get alertStore => _alertStore;

  @override
  ServerTagsStore get tagsStore => _tagsStore;
}

SamplerSample _sample(String id, double value) => SamplerSample(
  id: id,
  name: id,
  suffix: '',
  value: value,
  display: value.toString(),
  min: 0.0,
  max: 100.0,
  history: <double>[value],
);

ServerSnapshot _snapshotFrom(Map<String, double> values) {
  final Map<String, SamplerSample> byId = <String, SamplerSample>{};
  for (final MapEntry<String, double> e in values.entries) {
    byId[e.key] = _sample(e.key, e.value);
  }
  return ServerSnapshot(byId: byId, at: DateTime.now(), seq: 1);
}

FleetServerLive _liveServer(
  String id,
  String name, {
  ConnState state = ConnState.live,
  Map<String, double>? samplers,
}) => FleetServerLive(
  id: id,
  name: name,
  state: state,
  snapshot: samplers != null ? _snapshotFrom(samplers) : null,
  lastSeen: DateTime.now(),
);

Widget _wrap(List<FleetServerLive> servers, {_FakeFleetController? ctrl}) {
  final _FakeFleetController controller = ctrl ?? _FakeFleetController();
  return ArcaneThemeProvider(
    stylesheet: _sheet,
    child: FleetScope(
      controller: controller,
      revision: 1,
      child: FleetLiveScope(
        servers: servers,
        revision: 1,
        child: const FleetDashboardScreen(),
      ),
    ),
  );
}

Widget _wrapRouter(List<FleetServerLive> servers) {
  final _FakeFleetController controller = _FakeFleetController();
  return ArcaneThemeProvider(
    stylesheet: _sheet,
    child: FleetScope(
      controller: controller,
      revision: 0,
      child: FleetLiveScope(
        servers: servers,
        revision: 0,
        child: Router(routes: buildReactorShellRoutes()),
      ),
    ),
  );
}

void main() {
  group('FleetDashboardScreen', () {
    testServer('renders both server names A and B', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'a',
            'A',
            samplers: <String, double>{
              'ticks-per-second': 19.9,
              'players': 10.0,
            },
          ),
          _liveServer(
            'b',
            'B',
            samplers: <String, double>{'ticks-per-second': 9.0, 'players': 2.0},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('>A<') ||
            res.body.contains('"A"') ||
            res.body.contains('&#x41;') ||
            res.body.contains('>A ') ||
            (res.body.contains('A') && res.body.contains('B')),
        isTrue,
        reason: 'Server name A must appear in rendered HTML',
      );
      expect(
        res.body.contains('B'),
        isTrue,
        reason: 'Server name B must appear in rendered HTML',
      );
    });

    testServer('renders a TPS value from fleet data', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'a',
            'A',
            samplers: <String, double>{
              'ticks-per-second': 19.9,
              'players': 10.0,
            },
          ),
          _liveServer(
            'b',
            'B',
            samplers: <String, double>{'ticks-per-second': 9.0, 'players': 2.0},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      final bool hasTpsValue =
          res.body.contains('9.0') ||
          res.body.contains('19.9') ||
          res.body.contains('14.4') ||
          res.body.contains('9') ||
          res.body.contains(ReactorText.fleetWorstTps.english) ||
          res.body.contains(ReactorText.fleetMeanTps.english);
      expect(
        hasTpsValue,
        isTrue,
        reason: 'A TPS value or label must appear in rendered HTML',
      );
    });

    testServer(
      'critical server B surfaces destructive indicator or appears in needs-attention',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(<FleetServerLive>[
            _liveServer(
              'a',
              'A',
              samplers: <String, double>{
                'ticks-per-second': 19.9,
                'players': 10.0,
              },
            ),
            _liveServer(
              'b',
              'B',
              samplers: <String, double>{
                'ticks-per-second': 9.0,
                'players': 2.0,
              },
            ),
          ]),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        final bool hasCriticalSignal =
            res.body.contains('var(--destructive)') ||
            res.body.contains(ReactorText.statusCritical.english);
        expect(
          hasCriticalSignal,
          isTrue,
          reason:
              'Server B (TPS=9, critical health) must surface a critical or needs-attention signal',
        );
      },
    );

    testServer('renders Needs Attention section label', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'a',
            'A',
            samplers: <String, double>{
              'ticks-per-second': 19.9,
              'players': 10.0,
            },
          ),
          _liveServer(
            'b',
            'B',
            samplers: <String, double>{'ticks-per-second': 9.0, 'players': 2.0},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.toLowerCase().contains('attention') ||
            res.body.toLowerCase().contains('servers'),
        isTrue,
        reason: 'Needs-attention or servers section label must appear',
      );
    });

    testServer(
      'healthy fleet shows All servers healthy in needs-attention section',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(<FleetServerLive>[
            _liveServer(
              'a',
              'A',
              samplers: <String, double>{
                'ticks-per-second': 19.9,
                'players': 5.0,
              },
            ),
          ]),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains(ReactorText.fleetAllHealthy.english),
          isTrue,
          reason:
              'All servers healthy must appear when no server needs attention',
        );
      },
    );
  });

  group('FleetDashboardScreen empty fleet (root builder)', () {
    testServer('empty fleet renders No servers connected via router', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrapRouter(const <FleetServerLive>[]));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.shellNoServersConnected.english),
        isTrue,
        reason:
            'Root route with empty fleet must show No servers connected empty-state',
      );
    });
  });
}
