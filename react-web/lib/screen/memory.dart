/// Performance > Memory: heap use, pressure and garbage collection, then
/// the JVM's own heap figures.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/range_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/fleet_scope.dart';
import '../state/server_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/section.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

/// The JVM's heap figures (the rest of the JVM is under Internals).
const List<String> kJvmHeapIds = <String>[
  'jvm-heap-max',
  'jvm-heap-committed',
  'jvm-nonheap-used',
  'jvm-direct-buffer-bytes',
  'jvm-direct-buffer-count',
];

class MemoryScreen extends StatelessWidget {
  const MemoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    final AlertThresholds thresholds =
        FleetScope.of(context)?.alertStore.thresholds ??
        AlertThresholds.defaults;
    final SamplerSample? used = snapshot.sampler('memory-used');
    final SamplerSample? free = snapshot.sampler('memory-free');
    final SamplerSample? afterGc = snapshot.sampler('memory-used-after-gc');
    final SamplerSample? pressure = snapshot.sampler(
      HealthMetric.memoryPressure.samplerId,
    );
    final SamplerSample? gcTime = snapshot.sampler('gc-time-percent');
    final SamplerSample? gcPause = snapshot.sampler('gc-pause-p95');
    final List<SamplerSample> jvmHeap = samplersOf(snapshot, kJvmHeapIds);

    final String usedLabel = reactorText(ReactorText.memoryUsed);
    final String freeLabel = reactorText(ReactorText.memoryFree);
    final String afterGcLabel = reactorText(ReactorText.memoryAfterGc);

    // Four tiles a row: the heap, then the collector. The heap's history
    // is the chart below; the other readings keep their trend line. The
    // plugin's "memory-pressure" is the allocation rate (bytes per
    // second); heap in use against the maximum is what the state is.
    // Simple: the heap in use of its maximum and the heap chart; Advanced
    // adds the collector and the allocation rate.
    final bool advanced = ModeScope.advanced(context);
    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            if (advanced) ...<Widget>[
              samplerTile(usedLabel, used),
              samplerTile(afterGcLabel, afterGc),
              samplerTile(freeLabel, free),
              samplerTile(
                reactorText(ReactorText.memoryPressure),
                pressure,
                thresholds: thresholds,
              ),
              samplerTile(
                reactorText(ReactorText.commonGcTime),
                gcTime,
                thresholds: thresholds,
              ),
              samplerTile(reactorText(ReactorText.memoryGcPauseP95), gcPause),
              samplerTile(
                reactorText(ReactorText.memoryAllocation),
                snapshot.sampler('memory-pressure'),
              ),
            ] else
              memoryTile(
                usedLabel,
                used,
                pressure: pressure,
                max: snapshot.sampler('jvm-heap-max'),
              ),
          ],
        ),
        RangeChartCard(
          title: reactorText(ReactorText.memoryHeapUsage),
          height: 200,
          sizes: true,
          series: <RangeSeries>[
            RangeSeries(usedLabel, 'memory-used', used),
            if (free != null) RangeSeries(freeLabel, 'memory-free', free),
            if (afterGc != null)
              RangeSeries(afterGcLabel, 'memory-used-after-gc', afterGc),
          ],
        ),
        if (advanced && jvmHeap.isNotEmpty)
          PageSection(
            title: reactorText(ReactorText.memoryJvmHeap),
            children: <Widget>[MetricGrid(children: namedTiles(jvmHeap))],
          ),
      ],
    );
  }
}
