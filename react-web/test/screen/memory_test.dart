library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/memory.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

void main() {
  group('MemoryScreen', () {
    testServer('Advanced: heap, pressure and GC with the JVM heap below', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MemoryScreen(),
          mode: ReactorMode.advanced,
          // As the plugin sends them: sizes in bytes, the allocation rate
          // ("memory-pressure") in bytes per second.
          snapshot: snapshotOf(<SamplerSample>[
            sample('memory-used', 4294967296, suffix: 'GB', display: '4'),
            sample('memory-free', 2147483648, suffix: 'GB', display: '2'),
            sample(
              'memory-used-after-gc',
              3145728000,
              suffix: 'GB',
              display: '2.9',
            ),
            sample('jvm-heap-utilization', 95, suffix: '%'),
            sample(
              'memory-pressure',
              83614412,
              suffix: 'MB/s',
              display: '79.7',
            ),
            sample('gc-time-percent', 3, suffix: '%'),
            sample('gc-pause-p95', 12, suffix: 'ms'),
            sample(
              'jvm-heap-max',
              8589934592,
              suffix: 'GB',
              display: '8',
              name: 'JVM heap max',
            ),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;

      // The heap over time; pressure and GC pause are tiles only.
      expect(occurrences(body, 'data-chart-card'), 1);
      for (final String label in <String>[
        ReactorText.memoryUsed.english,
        ReactorText.memoryAfterGc.english,
        ReactorText.memoryPressure.english,
        ReactorText.commonGcTime.english,
        ReactorText.memoryGcPauseP95.english,
        ReactorText.memoryHeapUsage.english,
        ReactorText.memoryJvmHeap.english,
        ReactorText.memoryAllocation.english,
        'JVM heap max',
      ]) {
        expect(body, contains(label));
      }
      // Sizes from the bytes, the rate as a rate; no raw byte counts.
      expect(body, contains('>4.0</span>'));
      expect(body, contains('>79.7</span>'));
      expect(body, contains('MB/s'));
      expect(body, contains('>8.0</span>'));
      expect(body, isNot(contains('4294967296')));
      expect(body, contains('data-tone="warning"'));
      expect(body, contains(ReactorText.commonWarning.english));
    });

    testServer('Simple: the heap in use of its maximum and the heap chart', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MemoryScreen(),
          snapshot: snapshotOf(<SamplerSample>[
            sample('memory-used', 4294967296, suffix: 'GB', display: '4'),
            sample('jvm-heap-utilization', 50, suffix: '%'),
            sample('gc-time-percent', 3, suffix: '%'),
            sample('jvm-heap-max', 8589934592, suffix: 'GB', display: '8'),
          ]),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(occurrences(body, 'class="ui-metric"'), 1);
      expect(body, contains('of 8.0 GB'));
      expect(body, isNot(contains(ReactorText.commonGcTime.english)));
      expect(occurrences(body, 'data-chart-card'), 1);
    });

    testServer('heap chart shows plugin byte values as sizes', (
      ServerTester tester,
    ) async {
      // The plugin sends memory in bytes and shows it in a larger unit.
      const SamplerSample used = SamplerSample(
        id: 'memory-used',
        name: 'Memory used',
        suffix: 'GB',
        value: 2726378744,
        display: '2.5',
        min: 1131809056,
        max: 2726378744,
        history: <double>[1131809056, 2726378744],
      );
      tester.pumpComponent(
        onServer(
          const MemoryScreen(),
          snapshot: snapshotOf(<SamplerSample>[used]),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains('2726378744')));
      expect(body, isNot(contains('1131809056')));
      expect(body, contains('>2.5</span>'));
    });

    testServer('no JVM heap heading without JVM heap samplers', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const MemoryScreen(),
          snapshot: snapshotOf(<SamplerSample>[sample('memory-used', 1)]),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains(ReactorText.memoryJvmHeap.english)));
    });

    testServer('is empty while offline', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const MemoryScreen(), state: ConnState.offline),
      );
      expect((await tester.request('/')).body, contains('ui-empty'));
    });
  });
}
