library;

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/screen/internals.dart';
import 'package:react_web/state/connection_manager.dart';

import 'monitor_support.dart';

List<SamplerSample> _samples() => <SamplerSample>[
  sample('react-async-tick-time', 1.2, suffix: 'ms'),
  sample('react-sync-tick-time', 0.4, suffix: 'ms'),
  sample('react-jobs-queue', 3),
  sample('react-job-queue-time', 1, suffix: 'ms'),
  sample('react-job-budget', 5, suffix: 'ms'),
  sample('processor-process-load', 90, suffix: '%'),
  sample('processor-system-load', 40, suffix: '%'),
  sample('processor-outside', 10, suffix: '%'),
  sample('react-history-writer-queue', 7, name: 'History writer queue'),
  sample('react-websocket-sessions', 2, name: 'WebSocket sessions'),
  sample('jvm-threads', 88, name: 'JVM threads'),
  sample('jvm-heap-max', 8192, suffix: 'MB', name: 'JVM heap max'),
];

void main() {
  group('InternalsScreen', () {
    testServer('jobs and CPU tiles, React tick chart, sampler lists', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(const InternalsScreen(), snapshot: snapshotOf(_samples())),
      );
      final String body = (await tester.request('/')).body;

      expect(occurrences(body, 'class="ui-metric"'), 6);
      expect(occurrences(body, 'data-chart-card'), 1);
      expect(occurrences(body, 'role="table"'), 3);
      for (final String label in <String>[
        ReactorText.internalsJobs.english,
        ReactorText.internalsCpuLoad.english,
        ReactorText.internalsReactTickTime.english,
        ReactorText.internalsTelemetryPipeline.english,
        ReactorText.internalsWebTelemetry.english,
        ReactorText.internalsJvmRuntime.english,
        'History writer queue',
        'JVM threads',
      ]) {
        expect(body, contains(label));
      }
      expect(body, contains(ReactorText.statusCritical.english));
    });

    testServer('the JVM heap lives under Memory, not here', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(const InternalsScreen(), snapshot: snapshotOf(_samples())),
      );
      expect((await tester.request('/')).body, isNot(contains('JVM heap max')));
    });

    testServer('sampler rows open their history in Metrics', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        onServer(
          const InternalsScreen(serverId: 's1'),
          snapshot: snapshotOf(_samples()),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('href="/server/s1/metrics?metric=jvm-threads"'));
    });

    testServer('is empty while degraded', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(const InternalsScreen(), state: ConnState.degraded),
      );
      expect((await tester.request('/')).body, contains('ui-empty'));
    });
  });
}
