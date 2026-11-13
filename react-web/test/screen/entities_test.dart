library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/entities.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

void main() {
  group('EntitiesScreen', () {
    testServer('entities, players and ping, then the breakdown', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const EntitiesScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('entities', 6613),
            sample('entity-ai-active-count', 1200),
            sample('entities-spawns', 4),
            sample('players', 40),
            sample('player-ping-p95', 80, suffix: 'ms'),
            sample('ping-jitter', 6, suffix: 'ms'),
            sample('villagers', 75, name: 'Villagers'),
            sample('player-joins-rate', 1, name: 'Joins per minute'),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'data-chart-card'), 2);
      for (final String label in <String>[
        ReactorText.commonEntities.english,
        ReactorText.entitiesAiActive.english,
        ReactorText.commonPlayers.english,
        ReactorText.commonPingP95.english,
        ReactorText.entitiesPingJitter.english,
        ReactorText.entitiesCount.english,
        ReactorText.entitiesPlayerPing.english,
        ReactorText.entitiesBreakdown.english,
        ReactorText.entitiesPlayerActivity.english,
        'Villagers',
        'Joins per minute',
      ]) {
        expect(body, contains(label));
      }
      expect(body, contains('6613.0'));
    });

    testServer('no breakdown heading without breakdown samplers', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const EntitiesScreen(),
          snapshot: snapshotOf(<SamplerSample>[sample('entities', 1)]),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains(ReactorText.entitiesBreakdown.english)));
      expect(body, isNot(contains(ReactorText.entitiesPlayerActivity.english)));
    });

    testServer('loads while connecting', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const EntitiesScreen(), state: ConnState.connecting),
      );
      expect((await tester.request('/')).body, contains('ui-loading'));
    });
  });
}
