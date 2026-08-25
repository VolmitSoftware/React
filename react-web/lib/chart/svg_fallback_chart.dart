library;

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';

class SvgFallbackChart extends StatelessComponent {
  const SvgFallbackChart({
    required this.series,
    this.height = 160,
    this.hiddenSeries = const <int>{},
    this.activeSample,
    this.valueFormatter,
    this.sampleLabels,
    this.samplePositions,
    this.breakBeforeSamples = const <int>{},
    this.secondarySeries = const <int>{},
    this.onSampleHover,
    super.key,
  });

  final List<(String, List<double>)> series;
  final int height;
  final Set<int> hiddenSeries;
  final int? activeSample;
  final String Function(double value)? valueFormatter;
  final List<String>? sampleLabels;
  final List<double>? samplePositions;
  final Set<int> breakBeforeSamples;
  final Set<int> secondarySeries;
  final void Function(int?)? onSampleHover;

  @override
  Component build(BuildContext context) {
    dependOnReactorLocale(context);
    final (double, double) bounds = _globalBounds();
    final List<int> interactiveSamples = _interactiveSamples;
    final String accessibleLabel = series.isEmpty
        ? reactorText(ReactorText.chartNoTimeSeriesData)
        : reactorText(ReactorText.chartTimeSeriesLabel, <String, Object?>{
            'series': series
                .map(((String, List<double>) item) => item.$1)
                .join(', '),
          });
    return div(classes: 'reactor-chart-plot', <Component>[
      span(classes: 'reactor-chart-scale is-max', <Component>[
        Component.text(_formatBound(bounds.$2)),
      ]),
      svg(
        <Component>[
          for (int index = 0; index < 5; index++)
            line(
              const <Component>[],
              x1: '0',
              y1: '${6 + index * 22}',
              x2: '100',
              y2: '${6 + index * 22}',
              classes: 'reactor-chart-grid-line',
              attributes: const <String, String>{
                'vector-effect': 'non-scaling-stroke',
              },
            ),
          ..._seriesLines(bounds),
          if (activeSample != null) _activeGuide(bounds, activeSample!),
          for (final (int targetIndex, int index) in interactiveSamples.indexed)
            rect(
              const <Component>[],
              x: '${targetIndex * (100 / interactiveSamples.length)}',
              y: '0',
              width: '${100 / interactiveSamples.length}',
              height: '100',
              classes: 'reactor-chart-hit-target',
              attributes: <String, String>{
                'tabindex': '0',
                'aria-label': _sampleLabel(index),
              },
              events: <String, EventCallback>{
                'mouseenter': (_) => onSampleHover?.call(index),
                'focus': (_) => onSampleHover?.call(index),
                'mouseleave': (_) => onSampleHover?.call(null),
                'blur': (_) => onSampleHover?.call(null),
              },
            ),
        ],
        viewBox: '0 0 100 100',
        classes: 'reactor-chart-svg',
        styles: Styles(
          raw: <String, String>{
            'width': '100%',
            'height': '${height}px',
            'display': 'block',
          },
        ),
        attributes: <String, String>{
          'preserveAspectRatio': 'none',
          'role': 'img',
          'aria-label': accessibleLabel,
        },
      ),
      span(classes: 'reactor-chart-scale is-min', <Component>[
        Component.text(_formatBound(bounds.$1)),
      ]),
      if (_sampleCount > 0 && sampleLabels != null)
        div(classes: 'reactor-chart-time-axis', <Component>[
          span(<Component>[Component.text(_sampleTitle(0))]),
          if (_sampleCount > 2)
            span(<Component>[Component.text(_sampleTitle(_sampleCount ~/ 2))]),
          if (_sampleCount > 1)
            span(<Component>[Component.text(_sampleTitle(_sampleCount - 1))]),
        ]),
      if (activeSample != null)
        div(
          classes: 'reactor-chart-tooltip',
          styles: Styles(
            raw: <String, String>{'left': '${_samplePosition(activeSample!)}%'},
          ),
          <Component>[
            span(classes: 'reactor-chart-tooltip-index', <Component>[
              Component.text(_sampleTitle(activeSample!)),
            ]),
            for (int index = 0; index < series.length; index++)
              if (!hiddenSeries.contains(index) &&
                  activeSample! < series[index].$2.length)
                span(classes: 'reactor-chart-tooltip-row', <Component>[
                  i(
                    const <Component>[],
                    classes: 'reactor-chart-tooltip-swatch',
                    styles: Styles(
                      raw: <String, String>{
                        'background': 'var(--reactor-chart-${(index % 6) + 1})',
                      },
                    ),
                  ),
                  span(<Component>[Component.text(series[index].$1)]),
                  strong(<Component>[
                    Component.text(
                      _formatValue(series[index].$2[activeSample!]),
                    ),
                  ]),
                ]),
          ],
        ),
    ]);
  }

  int get _sampleCount {
    int count = 0;
    for (final (String _, List<double> values) in series) {
      if (values.length > count) count = values.length;
    }
    return count;
  }

  List<int> get _interactiveSamples {
    const int maximumTargets = 160;
    if (_sampleCount <= maximumTargets) {
      return List<int>.generate(_sampleCount, (int index) => index);
    }
    return List<int>.generate(
      maximumTargets,
      (int index) =>
          (index * (_sampleCount - 1) / (maximumTargets - 1)).round(),
      growable: false,
    );
  }

  Component _activeGuide((double, double) bounds, int sample) {
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
        if (!hiddenSeries.contains(index) && sample < series[index].$2.length)
          circle(
            const <Component>[],
            cx: '$x',
            cy: '${_pointY(series[index].$2[sample], bounds)}',
            r: '2.2',
            classes: 'reactor-chart-point',
            attributes: <String, String>{
              'fill': 'var(--reactor-chart-${(index % 6) + 1})',
            },
          ),
    ]);
  }

  double _samplePosition(int index) {
    if (_sampleCount <= 1) return 50.0;
    final List<double>? positions = samplePositions;
    final double normalized = positions != null && index < positions.length
        ? positions[index].clamp(0.0, 1.0)
        : index / (_sampleCount - 1);
    return 2.0 + normalized * 96.0;
  }

  double _pointY(double value, (double, double) bounds) =>
      94.0 - ((value - bounds.$1) / (bounds.$2 - bounds.$1)) * 88.0;

  String _sampleLabel(int index) {
    final List<String> values = <String>[];
    for (int seriesIndex = 0; seriesIndex < series.length; seriesIndex++) {
      if (hiddenSeries.contains(seriesIndex) ||
          index >= series[seriesIndex].$2.length) {
        continue;
      }
      values.add(
        '${series[seriesIndex].$1} ${_formatValue(series[seriesIndex].$2[index])}',
      );
    }
    return '${_sampleTitle(index)}: ${values.join(', ')}';
  }

  String _sampleTitle(int index) {
    final List<String>? labels = sampleLabels;
    if (labels != null && index < labels.length) return labels[index];
    return reactorText(ReactorText.chartSample, <String, Object?>{
      'number': index + 1,
    });
  }

  (double, double) _globalBounds() {
    double? minimum;
    double? maximum;
    for (final (String _, List<double> values) in series) {
      for (final double value in values) {
        minimum = minimum == null || value < minimum ? value : minimum;
        maximum = maximum == null || value > maximum ? value : maximum;
      }
    }
    if (minimum == null || maximum == null) return (0.0, 1.0);
    if ((maximum - minimum).abs() < 1e-9) {
      final double padding = maximum.abs() < 1.0 ? 1.0 : maximum.abs() * 0.05;
      return (minimum - padding, maximum + padding);
    }
    final double padding = (maximum - minimum) * 0.05;
    return (minimum - padding, maximum + padding);
  }

  List<Component> _seriesLines((double, double) bounds) {
    final List<Component> lines = <Component>[];
    for (int index = 0; index < series.length; index++) {
      if (hiddenSeries.contains(index)) continue;
      lines.addAll(_polylinesFor(series[index].$2, bounds, index));
    }
    return lines;
  }

  List<Component> _polylinesFor(
    List<double> values,
    (double, double) bounds,
    int index,
  ) {
    if (values.isEmpty) {
      return <Component>[
        polyline(
          const <Component>[],
          points: '',
          classes: _seriesClasses(index),
          attributes: <String, String>{
            'fill': 'none',
            'stroke': 'var(--reactor-chart-${(index % 6) + 1})',
          },
        ),
      ];
    }
    final double minimum = bounds.$1;
    final double range = bounds.$2 - bounds.$1;
    final int count = values.length;
    final List<StringBuffer> segments = <StringBuffer>[StringBuffer()];
    for (int pointIndex = 0; pointIndex < count; pointIndex++) {
      if (pointIndex > 0 && breakBeforeSamples.contains(pointIndex)) {
        segments.add(StringBuffer());
      }
      final double x = _samplePosition(pointIndex);
      final double y = 94.0 - ((values[pointIndex] - minimum) / range) * 88.0;
      final StringBuffer points = segments.last;
      if (points.isNotEmpty) points.write(' ');
      points.write('${x.toStringAsFixed(2)},${y.toStringAsFixed(2)}');
    }
    return segments
        .where((StringBuffer points) => points.isNotEmpty)
        .map(
          (StringBuffer points) => polyline(
            const <Component>[],
            points: points.toString(),
            classes: _seriesClasses(index),
            attributes: <String, String>{
              'fill': 'none',
              'stroke': 'var(--reactor-chart-${(index % 6) + 1})',
              'stroke-width': '1.5',
              'stroke-linecap': 'square',
              'stroke-linejoin': 'miter',
              'vector-effect': 'non-scaling-stroke',
            },
          ),
        )
        .toList(growable: false);
  }

  String _formatBound(double value) {
    final String Function(double value)? formatter = valueFormatter;
    if (formatter != null) return formatter(value);
    if (value.abs() >= 1000.0) return value.toStringAsFixed(0);
    if (value.abs() >= 100.0) return value.toStringAsFixed(1);
    return value.toStringAsFixed(2);
  }

  String _seriesClasses(int index) =>
      'reactor-chart-series reactor-chart-series-$index'
      '${secondarySeries.contains(index) ? ' is-secondary' : ''}';

  String _formatValue(double value) {
    final String Function(double value)? formatter = valueFormatter;
    if (formatter != null) return formatter(value);
    if (value.abs() >= 100.0) return value.toStringAsFixed(1);
    return value.toStringAsFixed(2);
  }
}
