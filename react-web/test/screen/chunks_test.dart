library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/chunks.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

void main() {
  group('ChunksScreen', () {
    testServer('loading tiles, listener chart, persistence and residency', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const ChunksScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('chunks', 2408),
            sample('chunks-loaded', 12),
            sample('chunks-generated', 3),
            sample('chunk-load-listener-ms', 0.8, suffix: 'ms'),
            sample('chunk-gen-listener-ms', 1.6, suffix: 'ms'),
            sample('world-save-event-interval', 300, suffix: 's'),
            sample('pdc-write-batcher', 4),
            sample('chunk-tickets', 900, name: 'Chunk tickets'),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'data-chart-card'), 1);
      for (final String label in <String>[
        ReactorText.commonChunks.english,
        ReactorText.chunksLoadedPerSecond.english,
        ReactorText.chunksGeneratedPerSecond.english,
        ReactorText.chunksLoadListenerTime.english,
        ReactorText.chunksGenListenerTime.english,
        ReactorText.chunksListenerTime.english,
        ReactorText.chunksPersistence.english,
        ReactorText.chunksWorldSaveEventInterval.english,
        ReactorText.chunksPdcBatcher.english,
        ReactorText.chunksResidency.english,
        'Chunk tickets',
      ]) {
        expect(body, contains(label));
      }
      expect(body, contains('2408.0'));
    });

    testServer('ranks worldgen by mod busiest first and lists the steps', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const ChunksScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('chunks', 2408),
            sample(
              'worldgen-mod-terralith',
              390,
              name: 'Terralith Worldgen',
              suffix: 'ms/s',
            ),
            sample(
              'worldgen-mod-minecraft',
              1180,
              name: 'Minecraft Worldgen',
              suffix: 'ms/s',
            ),
            for (int i = 0; i < 9; i++)
              sample(
                'worldgen-mod-quiet$i',
                0,
                name: 'Quiet$i Worldgen',
                suffix: 'ms/s',
              ),
            sample(
              'worldgen-step-noise',
              2560,
              name: 'Worldgen Noise',
              suffix: 'ms/s',
            ),
            sample(
              'worldgen-step-features',
              330,
              name: 'Worldgen Features',
              suffix: 'ms/s',
            ),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.chunksWorldgenByMod.english));
      expect(body, contains(ReactorText.chunksWorldgenSteps.english));
      expect(
        body.indexOf('Minecraft Worldgen'),
        lessThan(body.indexOf('Terralith Worldgen')),
      );
      expect(
        <int>[
          for (int i = 0; i < 9; i++)
            if (body.contains('Quiet$i Worldgen')) i,
        ].length,
        6,
      );
      expect(body, contains('Worldgen Noise'));
      expect(body, contains('Worldgen Features'));
    });

    testServer('leaves worldgen out when the server has none', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const ChunksScreen(),
          snapshot: snapshotOf(<SamplerSample>[sample('chunks', 2408)]),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains(ReactorText.chunksWorldgenByMod.english)));
      expect(body, isNot(contains(ReactorText.chunksWorldgenSteps.english)));
    });

    testServer('is empty while offline', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const ChunksScreen(), state: ConnState.offline),
      );
      expect((await tester.request('/')).body, contains('ui-empty'));
    });
  });
}
