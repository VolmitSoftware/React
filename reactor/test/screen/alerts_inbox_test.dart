library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:reactor/localization/reactor_localizations.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/alerts_inbox.dart';
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
        child: const AlertsInboxScreen(),
      ),
    ),
  );
}

void main() {
  group('AlertsInboxScreen', () {
    testServer('shows critical alert with server name and Low TPS', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 1.0},
          ),
          _liveServer(
            'srv-b',
            'ServerBeta',
            samplers: <String, double>{'ticks-per-second': 19.9},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('ServerAlpha'),
        isTrue,
        reason:
            'Server name ServerAlpha must appear for the critical alert row',
      );
      expect(
        res.body.contains(ReactorText.alertLowTps.english),
        isTrue,
        reason: 'Low TPS title must appear for the critical alert row',
      );
      final bool hasCriticalSignal =
          res.body.contains('var(--destructive)') ||
          res.body.toLowerCase().contains('critical');
      expect(
        hasCriticalSignal,
        isTrue,
        reason:
            'Critical severity must produce a destructive/critical badge indicator',
      );
    });

    testServer('healthy server contributes no row', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 1.0},
          ),
          _liveServer(
            'srv-b',
            'ServerBeta',
            samplers: <String, double>{'ticks-per-second': 19.9},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('ServerBeta') &&
            res.body.contains(ReactorText.alertLowTps.english),
        isTrue,
        reason:
            'ServerBeta is healthy so there should be no Low TPS for it but it may appear in filters; main check: critical row for ServerAlpha is present',
      );
      final bool serverBetaHasAlert = _serverHasAlert(
        res.body,
        'ServerBeta',
        ReactorText.alertLowTps.english,
      );
      expect(
        serverBetaHasAlert,
        isFalse,
        reason: 'Healthy ServerBeta must not appear as an alert row',
      );
    });

    testServer('shows No open alerts when all servers are healthy', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 19.9},
          ),
        ]),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.alertsNoneOpen.english),
        isTrue,
        reason: 'Empty alert feed must show No open alerts message',
      );
    });

    testServer('resolved alert is absent from feed', (
      ServerTester tester,
    ) async {
      final _FakeFleetController ctrl = _FakeFleetController();
      ctrl.alertStore.resolve('srv-a/ticks-per-second');
      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 1.0},
          ),
        ], ctrl: ctrl),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.alertsNoneOpen.english),
        isTrue,
        reason:
            'Pre-resolved alert must not appear; feed must show No open alerts',
      );
    });
  });
}

bool _serverHasAlert(String body, String serverName, String alertTitle) {
  final int serverIdx = body.indexOf(serverName);
  if (serverIdx < 0) return false;
  final int titleIdx = body.indexOf(alertTitle);
  if (titleIdx < 0) return false;
  final int closeProximity = 500;
  return (titleIdx - serverIdx).abs() < closeProximity;
}
