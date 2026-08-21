library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/comparison.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_live_scope.dart';
import 'package:react_web/state/fleet_rollup.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

SamplerSample _sample(
  String id,
  double value, {
  String suffix = '',
  List<double>? history,
}) => SamplerSample(
  id: id,
  name: id,
  suffix: suffix,
  value: value,
  display: value.toString(),
  min: 0.0,
  max: 100.0,
  history: history ?? <double>[value],
);

ServerSnapshot _snapshotFrom(Map<String, SamplerSample> samples) =>
    ServerSnapshot(byId: samples, at: DateTime.now(), seq: 1);

FleetServerLive _liveServer(
  String id,
  String name, {
  ConnState state = ConnState.live,
  ServerSnapshot? snapshot,
}) => FleetServerLive(
  id: id,
  name: name,
  state: state,
  snapshot: snapshot,
  lastSeen: DateTime.now(),
);

Widget _wrap(List<FleetServerLive> servers) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: FleetLiveScope(
    servers: servers,
    revision: 1,
    child: const ComparisonScreen(),
  ),
);

void main() {
  group('ComparisonScreen', () {
    testServer(
      'renders both server names A and B with default metric ticks-per-second',
      (ServerTester tester) async {
        final ServerSnapshot snapA = _snapshotFrom(<String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            20.0,
            history: <double>[18.0, 19.0, 20.0],
          ),
        });
        final ServerSnapshot snapB = _snapshotFrom(<String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            10.0,
            history: <double>[12.0, 11.0, 10.0],
          ),
        });

        tester.pumpComponent(
          _wrap(<FleetServerLive>[
            _liveServer('a', 'A', snapshot: snapA),
            _liveServer('b', 'B', snapshot: snapB),
          ]),
        );

        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('A') && res.body.contains('B'),
          isTrue,
          reason: 'Both server names A and B must appear in rendered HTML',
        );
      },
    );

    testServer(
      'leaderboard ranks server A (value 20) above server B (value 10)',
      (ServerTester tester) async {
        final ServerSnapshot snapA = _snapshotFrom(<String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            20.0,
            history: <double>[18.0, 19.0, 20.0],
          ),
        });
        final ServerSnapshot snapB = _snapshotFrom(<String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            10.0,
            history: <double>[12.0, 11.0, 10.0],
          ),
        });

        tester.pumpComponent(
          _wrap(<FleetServerLive>[
            _liveServer('a', 'A', snapshot: snapA),
            _liveServer('b', 'B', snapshot: snapB),
          ]),
        );

        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        final int indexA = res.body.lastIndexOf('A');
        final int indexB = res.body.lastIndexOf('B');
        expect(
          indexA > 0 && indexB > 0,
          isTrue,
          reason: 'Both A and B must appear in rendered HTML',
        );
        expect(
          res.body.contains('20.0'),
          isTrue,
          reason: 'Rank-1 value 20.0 for server A must appear in leaderboard',
        );
        expect(
          res.body.indexOf('20.0') < res.body.indexOf('10.0'),
          isTrue,
          reason:
              'Server A (value 20) must appear before server B (value 10) in leaderboard',
        );
      },
    );

    testServer(
      'renders a polyline for chart series via TimeseriesChart SSR fallback',
      (ServerTester tester) async {
        final ServerSnapshot snapA = _snapshotFrom(<String, SamplerSample>{
          'ticks-per-second': _sample(
            'ticks-per-second',
            20.0,
            history: <double>[18.0, 19.0, 20.0],
          ),
        });

        tester.pumpComponent(
          _wrap(<FleetServerLive>[_liveServer('a', 'A', snapshot: snapA)]),
        );

        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('polyline'),
          isTrue,
          reason:
              'TimeseriesChart must render an SVG polyline in SSR (non-web) mode',
        );
      },
    );

    testServer('shows empty-state text when no server has the selected metric', (
      ServerTester tester,
    ) async {
      final ServerSnapshot snapC = _snapshotFrom(<String, SamplerSample>{
        'players': _sample('players', 5.0),
      });

      tester.pumpComponent(
        _wrap(<FleetServerLive>[_liveServer('c', 'C', snapshot: snapC)]),
      );

      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      final bool hasEmptyText =
          res.body.toLowerCase().contains('no data') ||
          res.body.toLowerCase().contains('no servers') ||
          res.body.toLowerCase().contains('no metric') ||
          res.body.toLowerCase().contains('unavailable');
      expect(
        hasEmptyText,
        isTrue,
        reason:
            'ComparisonScreen must show an empty-state indicator when no server has the selected metric',
      );
    });

    testServer('excludes cached snapshots from offline servers', (
      ServerTester tester,
    ) async {
      final ServerSnapshot liveSnapshot = _snapshotFrom(<String, SamplerSample>{
        'ticks-per-second': _sample('ticks-per-second', 20.0),
      });
      final ServerSnapshot cachedOfflineSnapshot = _snapshotFrom(
        <String, SamplerSample>{
          'ticks-per-second': _sample('ticks-per-second', 999.0),
        },
      );

      tester.pumpComponent(
        _wrap(<FleetServerLive>[
          _liveServer('live', 'Live', snapshot: liveSnapshot),
          _liveServer(
            'offline',
            'Offline cache',
            state: ConnState.offline,
            snapshot: cachedOfflineSnapshot,
          ),
        ]),
      );

      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(res.body, contains('Offline (excluded)'));
      expect(res.body, contains('unavailable excluded'));
      expect(
        res.body,
        isNot(contains('999.0')),
        reason: 'Offline cached telemetry must not enter comparison results',
      );
    });
  });
}
