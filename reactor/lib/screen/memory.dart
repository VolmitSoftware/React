library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/timeseries_chart.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class MemoryScreen extends StatelessWidget {
  const MemoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? memUsed = scope?.snapshot?.sampler('memory-used');
    final SamplerSample? memFree = scope?.snapshot?.sampler('memory-free');
    final SamplerSample? memAfterGc =
        scope?.snapshot?.sampler('memory-used-after-gc');
    final SamplerSample? memPressure =
        scope?.snapshot?.sampler('memory-pressure');
    final SamplerSample? gcTimePercent =
        scope?.snapshot?.sampler('gc-time-percent');
    final SamplerSample? gcPauseP95 =
        scope?.snapshot?.sampler('gc-pause-p95');
    final SamplerSample? memGarbage =
        scope?.snapshot?.sampler('memory-garbage');

    final List<(String, List<double>)> heapSeries =
        <(String, List<double>)>[
      ('Memory Used', memUsed?.history ?? const <double>[]),
      if (memFree != null) ('Memory Free', memFree.history),
      if (memAfterGc != null) ('After GC', memAfterGc.history),
    ];

    final List<(String, List<double>)> pressureSeries =
        <(String, List<double>)>[
      ('Memory Pressure', memPressure?.history ?? const <double>[]),
    ];

    final List<(String, List<double>)> gcPauseSeries =
        <(String, List<double>)>[
      ('GC Pause p95', gcPauseP95?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Memory',
      subtitle: 'Heap usage and garbage collection',
      children: <Widget>[
        sectionCard(
          label: 'Heap Usage',
          child: TimeseriesChart(series: heapSeries, height: 180),
        ),
        sectionCard(
          label: 'Memory Pressure',
          child: TimeseriesChart(series: pressureSeries, height: 100),
        ),
        sectionCard(
          label: 'GC Pause p95',
          child: TimeseriesChart(series: gcPauseSeries, height: 100),
        ),
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'grid',
            'grid-template-columns': 'repeat(auto-fill, minmax(200px, 1fr))',
            'gap': '1rem',
          }),
          <Widget>[
            _GcGaugeCard(gcTimePercent: gcTimePercent),
            StatTile(label: 'Memory Used', sample: memUsed),
            StatTile(label: 'Memory Garbage', sample: memGarbage),
          ],
        ),
      ],
    );
  }
}

class _GcGaugeCard extends StatelessWidget {
  final SamplerSample? gcTimePercent;

  const _GcGaugeCard({required this.gcTimePercent});

  @override
  Widget build(BuildContext context) {
    return Card.elevated(
      fillWidth: true,
      child: dom.div(
        styles: const dom.Styles(raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'align-items': 'center',
          'gap': '0.5rem',
          'padding': '1rem',
        }),
        <Widget>[
          dom.div(
            styles: kSectionHeadingStyles,
            <Widget>[Component.text('GC Time')],
          ),
          Gauge(
            label: 'GC Time %',
            value: gcTimePercent?.value ?? 0.0,
            display: gcTimePercent?.display,
            max: 100.0,
            thresholds: (5.0, 15.0),
          ),
        ],
      ),
    );
  }
}
