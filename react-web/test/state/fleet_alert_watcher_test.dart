library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/alert.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/state/alert_store.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_alert_watcher.dart';
import 'package:react_web/state/fleet_live_scope.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';
import 'package:react_web/state/fleet_scope.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_tags_store.dart';

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

Widget _wrapWatcher(
  List<FleetServerLive> servers,
  Widget child, {
  _FakeFleetController? ctrl,
  void Function(FleetAlert alert)? notifyCritical,
}) {
  final _FakeFleetController controller = ctrl ?? _FakeFleetController();
  return ArcaneThemeProvider(
    stylesheet: _sheet,
    child: FleetScope(
      controller: controller,
      revision: 1,
      child: FleetLiveScope(
        servers: servers,
        revision: 1,
        child: notifyCritical == null
            ? FleetAlertWatcher(child: child)
            : FleetAlertWatcher(notifyCritical: notifyCritical, child: child),
      ),
    ),
  );
}

class _BuildOrderProbe extends StatelessWidget {
  final List<String> events;

  const _BuildOrderProbe(this.events);

  @override
  Widget build(BuildContext context) {
    events.add('child-build');
    return dom.span(<Widget>[Component.text('ordered-probe')]);
  }
}

void main() {
  group('AlertStore.detectNewCritical contract', () {
    test('detectNewCritical returns new critical keys on first call', () {
      final AlertStore store = AlertStore(InMemoryFleetStorage());
      final FleetAlert critAlert = FleetAlert(
        serverId: 's1',
        serverName: 'S1',
        metricId: 'ticks-per-second',
        severity: AlertSeverity.critical,
        title: 'Low TPS',
        message: '1.0 (< 5.0)',
        value: 1.0,
        threshold: 5.0,
        firstSeen: DateTime(2026, 1, 1),
        lastSeen: DateTime(2026, 1, 1),
      );
      final Set<String> newKeys = store.detectNewCritical(<FleetAlert>[
        critAlert,
      ]);
      expect(newKeys, contains('s1/ticks-per-second'));
    });

    test(
      'detectNewCritical returns empty set on repeated call with same alert',
      () {
        final AlertStore store = AlertStore(InMemoryFleetStorage());
        final FleetAlert critAlert = FleetAlert(
          serverId: 's1',
          serverName: 'S1',
          metricId: 'ticks-per-second',
          severity: AlertSeverity.critical,
          title: 'Low TPS',
          message: '1.0 (< 5.0)',
          value: 1.0,
          threshold: 5.0,
          firstSeen: DateTime(2026, 1, 1),
          lastSeen: DateTime(2026, 1, 1),
        );
        store.detectNewCritical(<FleetAlert>[critAlert]);
        final Set<String> second = store.detectNewCritical(<FleetAlert>[
          critAlert,
        ]);
        expect(second, isEmpty);
      },
    );
  });

  group('FleetAlertWatcher render smoke test', () {
    testServer('defers critical notification until after child rendering', (
      ServerTester tester,
    ) async {
      final List<String> events = <String>[];
      tester.pumpComponent(
        _wrapWatcher(
          <FleetServerLive>[
            _liveServer(
              'srv-a',
              'ServerAlpha',
              samplers: <String, double>{'ticks-per-second': 1.0},
            ),
          ],
          _BuildOrderProbe(events),
          notifyCritical: (FleetAlert alert) {
            events.add('notify:${alert.key}');
          },
        ),
      );

      final DocumentResponse res = await tester.request('/');

      expect(res.statusCode, equals(200));
      expect(res.body.contains('ordered-probe'), isTrue);
      expect(
        events,
        equals(<String>['child-build', 'notify:srv-a/ticks-per-second']),
        reason:
            'Toast dispatch must happen in the post-frame phase, after the child tree is rendered',
      );
    });

    testServer('renders child pass-through with critical alerts present', (
      ServerTester tester,
    ) async {
      final Widget probe = dom.div(id: 'probe-marker', <Widget>[
        Component.text('probe-child-content'),
      ]);
      tester.pumpComponent(
        _wrapWatcher(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 1.0},
          ),
        ], probe),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('probe-child-content'),
        isTrue,
        reason:
            'FleetAlertWatcher must pass through child unchanged in its rendered output',
      );
    });

    testServer('does not notify from an offline cached snapshot', (
      ServerTester tester,
    ) async {
      final List<FleetAlert> notifications = <FleetAlert>[];
      tester.pumpComponent(
        _wrapWatcher(
          <FleetServerLive>[
            _liveServer(
              'srv-a',
              'ServerAlpha',
              state: ConnState.offline,
              samplers: <String, double>{'ticks-per-second': 1.0},
            ),
          ],
          dom.span(<Widget>[Component.text('offline-probe')]),
          notifyCritical: notifications.add,
        ),
      );

      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(res.body, contains('offline-probe'));
      expect(notifications, isEmpty);
    });

    testServer('notifies from a degraded cached snapshot', (
      ServerTester tester,
    ) async {
      final List<FleetAlert> notifications = <FleetAlert>[];
      tester.pumpComponent(
        _wrapWatcher(
          <FleetServerLive>[
            _liveServer(
              'srv-a',
              'ServerAlpha',
              state: ConnState.degraded,
              samplers: <String, double>{'ticks-per-second': 1.0},
            ),
          ],
          dom.span(<Widget>[Component.text('degraded-probe')]),
          notifyCritical: notifications.add,
        ),
      );

      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(notifications, hasLength(1));
      expect(notifications.single.serverId, equals('srv-a'));
    });

    testServer('does not throw with healthy fleet', (
      ServerTester tester,
    ) async {
      final Widget probe = dom.span(<Widget>[Component.text('healthy-probe')]);
      tester.pumpComponent(
        _wrapWatcher(<FleetServerLive>[
          _liveServer(
            'srv-a',
            'ServerAlpha',
            samplers: <String, double>{'ticks-per-second': 19.9},
          ),
        ], probe),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('healthy-probe'),
        isTrue,
        reason: 'Child must be rendered even with a healthy fleet',
      );
    });
  });
}
