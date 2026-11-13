/// Performance > Internals (Advanced): React's own tick time, job queue and
/// CPU share, its telemetry pipeline and the JVM runtime. Every sampler row
/// opens its history in Metrics.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/range_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/health.dart';
import '../model/sampler_sample.dart';
import '../model/reading.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

const List<String> kTelemetryPipelineIds = <String>[
  'react-history-writer-queue',
  'react-history-writer-capacity',
  'react-history-dropped-snapshots',
  'react-history-drop-rate',
  'react-history-persist-lag',
  'react-history-storage-operational',
  'react-history-storage-error',
  'react-history-capture-ms',
  'react-history-write-ms',
  'react-history-disk-bytes',
  'react-history-wal-bytes',
  'react-samplers-registered',
  'react-samplers-available',
  'react-samplers-unavailable',
  'react-samplers-failed',
  'react-published-metrics-accepted',
  'react-published-metrics-dropped',
];

const List<String> kWebTelemetryIds = <String>[
  'react-websocket-sessions',
  'react-websocket-coalesced-frames',
];

/// The JVM beyond its heap (the heap is under Memory).
const List<String> kJvmRuntimeIds = <String>[
  'jvm-threads',
  'jvm-loaded-classes',
  'jvm-gc-collections-rate',
  'jvm-process-uptime',
];

/// CPU load (process or system, percent) at which a tile reads Warning,
/// then Critical.
const (double, double) kProcessLoadLevels = (60.0, 85.0);

/// A CPU load tile, coloured and named by [kProcessLoadLevels].
MetricTile _loadTile(ReactorText label, SamplerSample? load) {
  final HealthLevel level = load == null
      ? HealthLevel.unknown
      : shownValue(load.value) >= kProcessLoadLevels.$2
      ? HealthLevel.critical
      : shownValue(load.value) >= kProcessLoadLevels.$1
      ? HealthLevel.warning
      : HealthLevel.healthy;
  return samplerTile(
    reactorText(label),
    load,
    tone: levelTone(level),
    caption: levelWord(level),
  );
}

class InternalsScreen extends StatelessWidget {
  const InternalsScreen({this.serverId, super.key});

  /// The open server, so sampler rows can open their history.
  final String? serverId;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final SamplerSample? asyncTick = snapshot.sampler('react-async-tick-time');
    final SamplerSample? syncTick = snapshot.sampler('react-sync-tick-time');
    final SamplerSample? processLoad = snapshot.sampler(
      'processor-process-load',
    );
    final SamplerSample? systemLoad = snapshot.sampler('processor-system-load');

    return PageStack(
      children: <Widget>[
        PageSection(
          title: reactorText(ReactorText.internalsJobs),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                samplerTile(
                  reactorText(ReactorText.commonQueue),
                  snapshot.sampler('react-jobs-queue'),
                ),
                samplerTile(
                  reactorText(ReactorText.commonQueueTime),
                  snapshot.sampler('react-job-queue-time'),
                ),
                samplerTile(
                  reactorText(ReactorText.commonBudget),
                  snapshot.sampler('react-job-budget'),
                ),
              ],
            ),
          ],
        ),
        PageSection(
          title: reactorText(ReactorText.internalsCpuLoad),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                _loadTile(ReactorText.commonProcessLoad, processLoad),
                _loadTile(ReactorText.internalsSystemLoad, systemLoad),
                samplerTile(
                  reactorText(ReactorText.internalsOutsideLoad),
                  snapshot.sampler('processor-outside'),
                ),
              ],
            ),
          ],
        ),
        RangeChartCard(
          title: reactorText(ReactorText.internalsReactTickTime),
          unit: 'ms',
          series: <RangeSeries>[
            RangeSeries(
              reactorText(ReactorText.internalsAsync),
              'react-async-tick-time',
              asyncTick,
            ),
            RangeSeries(
              reactorText(ReactorText.internalsSync),
              'react-sync-tick-time',
              syncTick,
            ),
          ],
        ),
        _samplerList(
          reactorText(ReactorText.internalsTelemetryPipeline),
          samplersOf(snapshot, kTelemetryPipelineIds),
        ),
        _samplerList(
          reactorText(ReactorText.internalsWebTelemetry),
          samplersOf(snapshot, kWebTelemetryIds),
        ),
        _samplerList(
          reactorText(ReactorText.internalsJvmRuntime),
          samplersOf(snapshot, kJvmRuntimeIds),
        ),
      ],
    );
  }

  Widget _samplerList(String title, List<SamplerSample> samples) {
    if (samples.isEmpty) return const Component.fragment(<Widget>[]);
    final String? id = serverId;
    return DataList<SamplerSample>(
      label: title,
      title: title,
      items: samples,
      href: id == null
          ? null
          : (SamplerSample sample) => serverPagePath(
              id,
              'metrics',
              <String, String>{'metric': sample.id},
            ),
      columns: <ListColumn<SamplerSample>>[
        ListColumn<SamplerSample>(
          reactorText(ReactorText.metricsSampler),
          (SamplerSample sample) => Component.text(sample.name),
        ),
        ListColumn<SamplerSample>(
          reactorText(ReactorText.metricsCurrent),
          (SamplerSample sample) => Component.text(readingOf(sample).text),
          numeric: true,
          width: '10rem',
        ),
      ],
    );
  }
}
