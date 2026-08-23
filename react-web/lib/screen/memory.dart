library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/server_snapshot_state.dart';
import '../widget/stat_tile.dart';

class MemoryScreen extends StatelessWidget {
  const MemoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.memoryTitle),
      subtitle: reactorText(ReactorText.memorySubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? memUsed = snapshot.sampler('memory-used');
    final SamplerSample? memFree = snapshot.sampler('memory-free');
    final SamplerSample? memAfterGc = snapshot.sampler('memory-used-after-gc');
    final SamplerSample? memPressure = snapshot.sampler('memory-pressure');
    final SamplerSample? gcTimePercent = snapshot.sampler('gc-time-percent');
    final SamplerSample? gcPauseP95 = snapshot.sampler('gc-pause-p95');
    final SamplerSample? memGarbage = snapshot.sampler('memory-garbage');

    final List<(String, List<double>)> heapSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.commonMemoryUsed),
        memUsed?.history ?? const <double>[],
      ),
      if (memFree != null)
        (reactorText(ReactorText.memoryFree), memFree.history),
      if (memAfterGc != null)
        (reactorText(ReactorText.memoryAfterGc), memAfterGc.history),
    ];

    final List<(String, List<double>)> pressureSeries =
        <(String, List<double>)>[
          (
            reactorText(ReactorText.memoryPressure),
            memPressure?.history ?? const <double>[],
          ),
        ];

    final List<(String, List<double>)> gcPauseSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.memoryGcPauseP95),
        gcPauseP95?.history ?? const <double>[],
      ),
    ];

    return ReactorPage(
      title: reactorText(ReactorText.memoryTitle),
      subtitle: reactorText(ReactorText.memorySubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.memoryHeapUsage),
          children: <Widget>[
            TimeseriesChart(series: heapSeries, height: 180),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.commonMemoryUsed),
                sample: memUsed,
              ),
              StatTile(
                label: reactorText(ReactorText.memoryFree),
                sample: memFree,
              ),
              StatTile(
                label: reactorText(ReactorText.memoryAfterGc),
                sample: memAfterGc,
              ),
              StatTile(
                label: reactorText(ReactorText.memoryGarbage),
                sample: memGarbage,
              ),
            ]),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.memoryPressure),
          children: <Widget>[
            TimeseriesChart(series: pressureSeries, height: 112),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.memoryPressure),
                sample: memPressure,
              ),
              if (gcTimePercent == null)
                StatTile(
                  label: reactorText(ReactorText.memoryGcTimePercent),
                  sample: null,
                )
              else
                Gauge(
                  label: reactorText(ReactorText.memoryGcTimePercent),
                  value: gcTimePercent.value,
                  display: gcTimePercent.display,
                  max: 100.0,
                  thresholds: (5.0, 15.0),
                ),
            ]),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.memoryGcPauseP95),
          child: TimeseriesChart(series: gcPauseSeries, height: 100),
        ),
      ],
    );
  }
}
