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

    testServer('is empty while offline', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const ChunksScreen(), state: ConnState.offline),
      );
      expect((await tester.request('/')).body, contains('ui-empty'));
    });
  });
}
