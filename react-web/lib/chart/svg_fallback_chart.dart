/// The plot of a [TimeseriesChart]: lines on a value axis with round,
/// evenly spaced ticks ([chartScale]) and a time axis. One tab stop: the
/// arrow keys walk the samples and the read-out follows; a pointer does the
/// same by hovering.
library;

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:universal_web/web.dart' as web;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/units.dart';
import 'chart_scale.dart';

/// Most hover targets drawn across the plot; longer series share them.
const int kChartHoverTargets = 160;

class SvgFallbackChart extends StatefulComponent {
  const SvgFallbackChart({
    required this.series,
    this.height = 160,
    this.activeSample,
    this.unit,
    this.valueFormatter,
    this.sampleTitles,
    this.samplePositions,
    this.breakBeforeSamples = const <int>{},
    this.secondarySeries = const <int>{},
    this.timeLabels = const <(int, String)>[],
    this.onSampleHover,
    super.key,
  });

  final List<(String, List<double>)> series;
  final int height;
  final int? activeSample;

  /// The unit of every value ("ms", "%"); on the axis and in the read-out.
  final String? unit;

  /// Writes a value with its unit (byte rates); overrides [unit].
  final String Function(double value)? valueFormatter;

  /// What each sample is called in the read-out (its time).
  final List<String>? sampleTitles;

  /// Each sample's place from 0 to 1 (wall-clock time); even when null.
  final List<double>? samplePositions;
  final Set<int> breakBeforeSamples;

  /// Drawn dashed and fainter (a reference line).
  final Set<int> secondarySeries;

  /// The time axis: (sample index, label), two to four of them.
  final List<(int, String)> timeLabels;
  final void Function(int?)? onSampleHover;

  @override
  State<SvgFallbackChart> createState() => _SvgFallbackChartState();
}

class _SvgFallbackChartState extends State<SvgFallbackChart> {
  List<(String, List<double>)> get series => component.series;
  ChartScale? _scale;
  List<Component>? _lines;
  List<Component>? _targets;

  @override
  void didUpdateComponent(SvgFallbackChart oldComponent) {
    super.didUpdateComponent(oldComponent);
    if (oldComponent.series != series ||
        oldComponent.samplePositions != component.samplePositions ||
        oldComponent.breakBeforeSamples != component.breakBeforeSamples ||
        oldComponent.secondarySeries != component.secondarySeries) {
      _scale = null;
      _lines = null;
      _targets = null;
    }
  }

  int get _sampleCount {
    int count = 0;
    for (final (String _, List<double> values) in series) {
      if (values.length > count) count = values.length;
    }
    return count;
  }

  @override
  Component build(BuildContext context) {
    dependOnReactorLocale(context);
    final ChartScale scale = _scale ??= chartScale(<double>[
      for (final (String _, List<double> values) in series) ...values,
    ]);
    final int? active = component.activeSample;
    final String names = series
        .map(((String, List<double>) item) => item.$1)
        .join(', ');
    return div(classes: 'reactor-chart-plot', <Component>[
      div(classes: 'reactor-chart-area', <Component>[
        svg(
          <Component>[
            for (final double tick in scale.ticks)
              line(
                const <Component>[],
                x1: '0',
                y1: '${_y(scale, tick)}',
                x2: '100',
                y2: '${_y(scale, tick)}',
                classes: 'reactor-chart-grid-line',
                attributes: const <String, String>{
                  'vector-effect': 'non-scaling-stroke',
                },
              ),
            ...(_lines ??= _seriesLines(scale)),
            if (active != null) _activeGuide(scale, active),
            ...(_targets ??= _hoverTargets()),
          ],
          viewBox: '0 0 100 100',
          classes: 'reactor-chart-svg',
          attributes: <String, String>{
            'width': '100%',
            'height': '${component.height}',
            'preserveAspectRatio': 'none',
            'role': 'img',
            'tabindex': '0',
            'aria-label':
                '${reactorText(ReactorText.chartTimeSeriesLabel, <String, Object?>{'series': names})}. '
                '${reactorText(ReactorText.chartKeysHint)}',
          },
          events: <String, EventCallback>{
            'keydown': _keys,
            'focus': (_) {
              if (component.activeSample == null && _sampleCount > 0) {
                component.onSampleHover?.call(_sampleCount - 1);
              }
            },
            'blur': (_) => component.onSampleHover?.call(null),
          },
        ),
        for (final double tick in scale.ticks)
          span(
            classes: 'reactor-chart-tick',
            styles: Styles(raw: <String, String>{'top': '${_y(scale, tick)}%'}),
            <Component>[Component.text(_tickLabel(scale, tick))],
          ),
        if (active != null)
          div(
            classes: 'reactor-chart-tooltip',
            attributes: const <String, String>{'role': 'status'},
            styles: Styles(
              raw: <String, String>{'left': '${_samplePosition(active)}%'},
            ),
            <Component>[
              span(classes: 'reactor-chart-tooltip-index', <Component>[
                Component.text(_sampleTitle(active)),
              ]),
              for (int index = 0; index < series.length; index++)
                if (active < series[index].$2.length)
                  span(classes: 'reactor-chart-tooltip-row', <Component>[
                    i(
                      const <Component>[],
                      classes:
                          'reactor-chart-tooltip-swatch '
                          'reactor-chart-series-${index % 6}',
                    ),
                    span(<Component>[Component.text(series[index].$1)]),
                    strong(<Component>[
                      Component.text(_formatValue(series[index].$2[active])),
                    ]),
                  ]),
            ],
          ),
      ]),
      if (component.timeLabels.isNotEmpty)
        div(classes: 'reactor-chart-time-axis', <Component>[
          for (final (int index, String label) in component.timeLabels)
            span(
              classes: index == 0
                  ? 'is-start'
                  : index >= _sampleCount - 1
                  ? 'is-end'
                  : null,
              styles: Styles(
                raw: <String, String>{'left': '${_samplePosition(index)}%'},
              ),
              <Component>[Component.text(label)],
            ),
        ]),
    ]);
  }

  /// Left and Right step through the samples, Home and End jump to the
  /// first and last.
  void _keys(web.Event raw) {
    final int count = _sampleCount;
    if (count == 0) return;
    final String key = (raw as web.KeyboardEvent).key;
    final int at = component.activeSample ?? count - 1;
    final int? next = switch (key) {
      'ArrowLeft' || 'ArrowDown' => (at - 1).clamp(0, count - 1),
      'ArrowRight' || 'ArrowUp' => (at + 1).clamp(0, count - 1),
      'PageDown' => (at - 10).clamp(0, count - 1),
      'PageUp' => (at + 10).clamp(0, count - 1),
      'Home' => 0,
      'End' => count - 1,
      _ => null,
    };
    if (next == null) return;
    raw.preventDefault();
    component.onSampleHover?.call(next);
  }

  /// Pointer targets, one per sample (or per group of samples on a long
  /// series). Not in the tab order: the plot is one stop.
  List<Component> _hoverTargets() {
    final int count = _sampleCount;
    final List<int> samples = count <= kChartHoverTargets
        ? List<int>.generate(count, (int index) => index)
        : List<int>.generate(
            kChartHoverTargets,
            (int index) =>
                (index * (count - 1) / (kChartHoverTargets - 1)).round(),
          );
    return <Component>[
      for (final (int slot, int index) in samples.indexed)
        rect(
          const <Component>[],
          key: ValueKey<int>(index),
          x: '${slot * (100 / samples.length)}',
          y: '0',
          width: '${100 / samples.length}',
          height: '100',
          classes: 'reactor-chart-hit-target',
          attributes: <String, String>{'data-sample': '$index'},
          events: <String, EventCallback>{
            'mouseenter': (_) => component.onSampleHover?.call(index),
            'mouseleave': (_) => component.onSampleHover?.call(null),
          },
        ),
    ];
  }

  Component _activeGuide(ChartScale scale, int sample) {
    final double x = _samplePosition(sample);
    return Component.fragment(<Component>[
      line(
        const <Component>[],
        x1: '$x',
        y1: '4',
        x2: '$x',
        y2: '96',
        classes: 'reactor-chart-crosshair',
      ),
      for (int index = 0; index < series.length; index++)
        if (sample < series[index].$2.length)
          circle(
            const <Component>[],
            cx: '$x',
            cy: '${_y(scale, series[index].$2[sample])}',
            r: '2.2',
            classes: 'reactor-chart-point reactor-chart-series-${index % 6}',
            attributes: const <String, String>{'fill': 'currentColor'},
          ),
    ]);
  }

  double _samplePosition(int index) {
    if (_sampleCount <= 1) return 50.0;
    final List<double>? positions = component.samplePositions;
    final double normalized = positions != null && index < positions.length
        ? positions[index].clamp(0.0, 1.0)
        : index / (_sampleCount - 1);
    return 2.0 + normalized * 96.0;
  }

  /// The plot keeps 6 units free above and below the axis range.
  double _y(ChartScale scale, double value) =>
      94.0 - scale.fraction(value) * 88.0;

  String _sampleTitle(int index) {
    final List<String>? titles = component.sampleTitles;
    if (titles != null && index < titles.length) return titles[index];
    return reactorText(ReactorText.chartSample, <String, Object?>{
      'number': index + 1,
    });
  }

  List<Component> _seriesLines(ChartScale scale) => <Component>[
    for (int index = 0; index < series.length; index++)
      ..._polylinesFor(series[index].$2, scale, index),
  ];

  List<Component> _polylinesFor(
    List<double> values,
    ChartScale scale,
    int index,
  ) {
    final List<StringBuffer> segments = <StringBuffer>[StringBuffer()];
    for (int point = 0; point < values.length; point++) {
      if (point > 0 && component.breakBeforeSamples.contains(point)) {
        segments.add(StringBuffer());
      }
      final StringBuffer points = segments.last;
      if (points.isNotEmpty) points.write(' ');
      points.write(
        '${_samplePosition(point).toStringAsFixed(2)},'
        '${_y(scale, values[point]).toStringAsFixed(2)}',
      );
    }
    return <Component>[
      for (final StringBuffer points in segments)
        if (points.isNotEmpty)
          polyline(
            const <Component>[],
            points: points.toString(),
            classes: 'reactor-chart-series-${index % 6}',
            attributes: <String, String>{
              'fill': 'none',
              'stroke': 'currentColor',
              'stroke-width': '1.5',
              'stroke-linecap': 'square',
              'stroke-linejoin': 'miter',
              'vector-effect': 'non-scaling-stroke',
              if (component.secondarySeries.contains(
                index,
              )) ...<String, String>{
                'stroke-dasharray': '3 3',
                'opacity': '0.6',
              },
            },
          ),
    ];
  }

  String _tickLabel(ChartScale scale, double tick) {
    final String Function(double value)? formatter = component.valueFormatter;
    if (formatter != null) return formatter(tick);
    return withUnit(scale.label(tick), component.unit);
  }

  String _formatValue(double value) {
    final String Function(double value)? formatter = component.valueFormatter;
    if (formatter != null) return formatter(value);
    final String number = value.abs() >= 100
        ? value.toStringAsFixed(0)
        : value.abs() >= 10
        ? value.toStringAsFixed(1)
        : value.toStringAsFixed(2);
    return withUnit(number, component.unit);
  }
}
