/// A line chart of one or more series over time, in its [ChartCard]. The
/// value axis starts where [chartScale] says, labelled in [unit]; the time
/// axis comes from the samples' timestamps (a live chart reads the server's
/// capture times). A chart with nothing moving draws no line: it says the
/// value instead. Two or more series get a plain legend: a dot and a name.
library;

import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/units.dart';
import '../state/server_scope.dart';
import 'chart_scale.dart';
import 'chart_timeline.dart';
import 'svg_fallback_chart.dart';

class TimeseriesChart extends StatefulComponent {
  const TimeseriesChart({
    required this.series,
    this.height = 160,
    this.unit,
    this.valueFormatter,
    this.sampleLabels,
    this.sampleTimestamps,
    this.secondarySeries = const <int>{},
    super.key,
  });

  final List<(String, List<double>)> series;
  final int height;

  /// The unit of the values ("ms", "%"), shown on the axis and read-out.
  final String? unit;

  /// Writes a value with its unit (byte rates); overrides [unit].
  final String Function(double value)? valueFormatter;

  /// What each sample is called in the read-out; its time when null.
  final List<String>? sampleLabels;

  /// When each sample was taken. Null on a live chart: the open server's
  /// capture times, aligned to the newest sample.
  final List<DateTime>? sampleTimestamps;
  final Set<int> secondarySeries;

  @override
  State<TimeseriesChart> createState() => _TimeseriesChartState();
}

class _TimeseriesChartState extends State<TimeseriesChart> {
  int? _activeSample;
  List<DateTime>? _stamps;
  ChartTimeline? _timeline;

  int get _sampleCount {
    int count = 0;
    for (final (String _, List<double> values) in component.series) {
      if (values.length > count) count = values.length;
    }
    return count;
  }

  /// The given timestamps, else the live capture times of the open server
  /// for the last [count] samples; null when neither covers them.
  List<DateTime>? _timestamps(BuildContext context, int count) {
    final List<DateTime>? given = component.sampleTimestamps;
    if (given != null) return given.length >= count ? given : null;
    final List<double> times =
        ServerScope.of(context)?.snapshot?.times ?? const <double>[];
    if (times.length < count || count == 0) return null;
    return <DateTime>[
      for (int index = times.length - count; index < times.length; index++)
        DateTime.fromMillisecondsSinceEpoch(times[index].round()),
    ];
  }

  @override
  Component build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<(String, List<double>)> series = component.series;
    final int count = _sampleCount;
    if (series.every(((String, List<double>) item) => item.$2.isEmpty)) {
      // No samples yet: one muted line, not a chart-sized box.
      return dom.p(classes: 'ui-caption', <Component>[
        Component.text(reactorText(ReactorText.chartAwaitingSamples)),
      ]);
    }
    if (series.every(((String, List<double>) item) => isFlat(item.$2))) {
      return _steady(series);
    }
    final List<DateTime>? stamps = _timestamps(context, count);
    if (!identical(stamps, _stamps)) {
      _stamps = stamps;
      _timeline = stamps == null ? null : ChartTimeline.fromTimestamps(stamps);
    }
    final ChartTimeline? timeline = _timeline;
    final Duration span = stamps == null || stamps.length < 2
        ? Duration.zero
        : stamps.last.difference(stamps.first);
    final List<String>? titles =
        component.sampleLabels ??
        stamps
            ?.map((DateTime time) => axisTime(time, Duration.zero))
            .toList(growable: false);
    final int? active = _activeSample != null && _activeSample! < count
        ? _activeSample
        : null;
    return dom.div(classes: 'reactor-chart', <Component>[
      SvgFallbackChart(
        series: series,
        height: component.height,
        activeSample: active,
        unit: component.unit,
        valueFormatter: component.valueFormatter,
        sampleTitles: titles,
        samplePositions: timeline?.positions,
        breakBeforeSamples: timeline?.breakBefore ?? const <int>{},
        secondarySeries: component.secondarySeries,
        timeLabels: _distinct(<(int, String)>[
          if (stamps != null)
            for (final int index in axisSamples(count))
              (index, axisTime(stamps[index], span))
          else if (component.sampleLabels case final List<String> labels)
            for (final int index in axisSamples(count))
              if (index < labels.length) (index, labels[index]),
        ]),
        onSampleHover: (int? sample) {
          if (_activeSample != sample) setState(() => _activeSample = sample);
        },
      ),
      if (series.length > 1)
        dom.ul(
          classes: 'reactor-chart-legend',
          attributes: const <String, String>{'data-legend': ''},
          <Component>[
            for (int index = 0; index < series.length; index++)
              dom.li(<Component>[
                dom.i(
                  classes:
                      'reactor-chart-tooltip-swatch '
                      'reactor-chart-series-${index % 6}',
                  const <Component>[],
                ),
                Component.text(series[index].$1),
              ]),
          ],
        ),
    ]);
  }

  /// Labels that say something new: a repeat of the one before is dropped
  /// (a few seconds of samples name the same minute).
  static List<(int, String)> _distinct(
    List<(int, String)> labels,
  ) => <(int, String)>[
    for (int index = 0; index < labels.length; index++)
      if (index == 0 || labels[index].$2 != labels[index - 1].$2) labels[index],
  ];

  /// Nothing moves: each series' value in words instead of a flat line.
  Component _steady(List<(String, List<double>)> series) =>
      dom.div(classes: 'reactor-chart is-steady', <Component>[
        for (final (String name, List<double> values) in series)
          if (values.isNotEmpty)
            dom.p(classes: 'ui-note', <Component>[
              Component.text(
                series.length > 1
                    ? '$name: ${_steadyText(values.last)}'
                    : _steadyText(values.last),
              ),
            ]),
      ]);

  String _steadyText(double value) {
    final String Function(double value)? formatter = component.valueFormatter;
    final String shown = formatter != null
        ? formatter(value)
        : withUnit(compactNumber(value), component.unit);
    return reactorText(ReactorText.chartSteady, <String, Object?>{
      'value': shown,
    });
  }
}
