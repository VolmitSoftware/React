library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class InternalsScreen extends StatelessWidget {
  const InternalsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);

    final SamplerSample? asyncTickTime = scope?.snapshot?.sampler(
      'react-async-tick-time',
    );
    final SamplerSample? syncTickTime = scope?.snapshot?.sampler(
      'react-sync-tick-time',
    );
    final SamplerSample? jobsQueue = scope?.snapshot?.sampler(
      'react-jobs-queue',
    );
    final SamplerSample? jobQueueTime = scope?.snapshot?.sampler(
      'react-job-queue-time',
    );
    final SamplerSample? jobBudget = scope?.snapshot?.sampler(
      'react-job-budget',
    );
    final SamplerSample? systemLoad = scope?.snapshot?.sampler(
      'processor-system-load',
    );
    final SamplerSample? processLoad = scope?.snapshot?.sampler(
      'processor-process-load',
    );
    final SamplerSample? outsideLoad = scope?.snapshot?.sampler(
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
        sectionCard(
          label: reactorText(ReactorText.internalsReactTickTime),
          child: TimeseriesChart(series: tickSeries, height: 160),
        ),
        sectionCard(
          label: reactorText(ReactorText.internalsJobs),
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
        sectionCard(
          label: reactorText(ReactorText.internalsCpuLoad),
          child: dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
                'align-items': 'center',
                'gap': '1rem',
              },
            ),
            <Widget>[
              Gauge(
                label: reactorText(ReactorText.commonProcessLoad),
                value: processLoad?.value ?? 0.0,
                display: processLoad?.display,
                max: 100.0,
                thresholds: (60.0, 85.0),
              ),
              statGrid(<Widget>[
                StatTile(
                  label: reactorText(ReactorText.internalsSystemLoad),
                  sample: systemLoad,
                ),
                StatTile(
                  label: reactorText(ReactorText.commonProcessLoad),
                  sample: processLoad,
                ),
                StatTile(
                  label: reactorText(ReactorText.internalsOutsideLoad),
                  sample: outsideLoad,
                ),
              ]),
            ],
          ),
        ),
      ],
    );
  }
}
