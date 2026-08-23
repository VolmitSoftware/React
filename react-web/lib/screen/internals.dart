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

class InternalsScreen extends StatelessWidget {
  const InternalsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.internalsTitle),
      subtitle: reactorText(ReactorText.internalsSubtitle),
    );
    if (snapshot == null) return statePage!;

    final SamplerSample? asyncTickTime = snapshot.sampler(
      'react-async-tick-time',
    );
    final SamplerSample? syncTickTime = snapshot.sampler(
      'react-sync-tick-time',
    );
    final SamplerSample? jobsQueue = snapshot.sampler('react-jobs-queue');
    final SamplerSample? jobQueueTime = snapshot.sampler(
      'react-job-queue-time',
    );
    final SamplerSample? jobBudget = snapshot.sampler('react-job-budget');
    final SamplerSample? systemLoad = snapshot.sampler('processor-system-load');
    final SamplerSample? processLoad = snapshot.sampler(
      'processor-process-load',
    );
    final SamplerSample? outsideLoad = snapshot.sampler(
      'processor-outside-load',
    );

    final List<(String, List<double>)> tickSeries = <(String, List<double>)>[
      (
        reactorText(ReactorText.internalsAsync),
        asyncTickTime?.history ?? const <double>[],
      ),
      (
        reactorText(ReactorText.internalsSync),
        syncTickTime?.history ?? const <double>[],
      ),
    ];

    return ReactorPage(
      title: reactorText(ReactorText.internalsTitle),
      subtitle: reactorText(ReactorText.internalsSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.internalsReactTickTime),
          child: TimeseriesChart(series: tickSeries, height: 160),
        ),
        SectionPanel(
          label: reactorText(ReactorText.internalsJobs),
          flush: true,
          child: statGrid(<Widget>[
            StatTile(
              label: reactorText(ReactorText.commonQueue),
              sample: jobsQueue,
            ),
            StatTile(
              label: reactorText(ReactorText.commonQueueTime),
              sample: jobQueueTime,
            ),
            StatTile(
              label: reactorText(ReactorText.commonBudget),
              sample: jobBudget,
            ),
          ]),
        ),
        SectionPanel(
          label: reactorText(ReactorText.internalsCpuLoad),
          flush: true,
          child: reactorGrid(
            children: <Widget>[
              if (processLoad == null)
                StatTile(
                  label: reactorText(ReactorText.commonProcessLoad),
                  sample: null,
                )
              else
                Gauge(
                  label: reactorText(ReactorText.commonProcessLoad),
                  value: processLoad.value,
                  display: processLoad.display,
                  max: 100.0,
                  thresholds: (60.0, 85.0),
                ),
              StatTile(
                label: reactorText(ReactorText.internalsSystemLoad),
                sample: systemLoad,
              ),
              StatTile(
                label: reactorText(ReactorText.internalsOutsideLoad),
                sample: outsideLoad,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
