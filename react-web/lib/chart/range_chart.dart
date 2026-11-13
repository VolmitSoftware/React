/// A chart card that follows the server's chart range: Live draws the
/// frames since the page opened; 1h, 6h and 24h draw what the server stored,
/// extended by the live frames. The range toggle sits in the card's head;
/// every chart of the server shares the pick.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/range_history.dart';
import '../model/reading.dart';
import '../model/sampler_sample.dart';
import '../state/chart_history.dart';
import '../state/server_scope.dart';
import '../ui/error_text.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/states.dart';
import '../ui/kit/toggle_choice.dart';
import 'timeseries_chart.dart';

/// One line of a [RangeChartCard]: its name, its sampler and the sampler's
/// live reading (its unit, and the Live history).
class RangeSeries {
  const RangeSeries(this.label, this.samplerId, this.live);

  final String label;
  final String samplerId;
  final SamplerSample? live;
}

class RangeChartCard extends StatelessWidget {
  const RangeChartCard({
    required this.title,
    required this.series,
    this.unit,
    this.sizes = false,
    this.height = 160,
    this.secondary = const <RowAction>[],
    this.replacement,
    this.secondarySeries = const <int>{},
    super.key,
  });

  final String title;
  final List<RangeSeries> series;

  /// The unit of every value ("ms"); ignored when [sizes].
  final String? unit;

  /// The values are sizes or rates in bytes, drawn in the unit of the
  /// largest current reading ("GB", "MB/s").
  final bool sizes;
  final int height;

  /// Links in the card's head after the toggle ("Details").
  final List<RowAction> secondary;

  /// Shown instead of the chart (the server cannot report the sampler).
  final Widget? replacement;
  final Set<int> secondarySeries;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ChartHistory? charts = ServerScope.of(context)?.charts;
    // A card with nothing to draw gets no toggle that would change nothing.
    if (charts == null || replacement != null) {
      return ChartCard(
        title: title,
        actions: secondary.isEmpty
            ? null
            : ActionRow(dense: true, secondary: secondary),
        child: replacement ?? _live(),
      );
    }
    return ListenableBuilder(
      listenable: charts,
      builder: (BuildContext context) => ChartCard(
        title: title,
        actions: ActionRow(
          dense: true,
          label: title,
          leading: <Widget>[
            ToggleChoice<ChartRange>(
              label: reactorText(ReactorText.chartRange),
              options: <ChoiceOption<ChartRange>>[
                for (final ChartRange range in ChartRange.values)
                  ChoiceOption<ChartRange>(
                    range,
                    range.stored
                        ? range.id
                        : reactorText(ReactorText.chartRangeLive),
                  ),
              ],
              value: charts.range,
              onChanged: charts.select,
            ),
          ],
          secondary: secondary,
        ),
        child: _body(charts),
      ),
    );
  }

  Widget _body(ChartHistory charts) {
    if (!charts.range.stored) return _live();
    final Object? error = charts.error;
    if (error != null) {
      return ErrorState(
        message: localizedReactorError(error),
        onRetry: charts.retry,
      );
    }
    final RangeHistory? history = charts.current;
    if (history == null) {
      return LoadingState(
        label: reactorText(ReactorText.metricsLoadingHistory),
        height: '${height}px',
      );
    }
    final RangeView view = history.view(<String>[
      for (final RangeSeries line in series) line.samplerId,
    ]);
    if (view.isEmpty) {
      return EmptyState(message: reactorText(ReactorText.metricsNoSamples));
    }
    final List<RangeSeries> shown = <RangeSeries>[
      for (final RangeSeries line in series)
        if (view.values.containsKey(line.samplerId)) line,
    ];
    return _chart(shown, <List<double>>[
      for (final RangeSeries line in shown) view.values[line.samplerId]!,
    ], timestamps: view.times);
  }

  Widget _live() => _chart(series, <List<double>>[
    for (final RangeSeries line in series)
      line.live?.history ?? const <double>[],
  ]);

  Widget _chart(
    List<RangeSeries> lines,
    List<List<double>> values, {
    List<DateTime>? timestamps,
  }) {
    final ChartValues scaled = sizes
        ? chartValuesOf(<SamplerSample?>[
            for (final RangeSeries line in lines) line.live,
          ], values: values)
        : ChartValues(unit ?? '', values);
    return TimeseriesChart(
      height: height,
      unit: sizes ? scaled.unit : unit,
      sampleTimestamps: timestamps,
      secondarySeries: secondarySeries,
      series: <(String, List<double>)>[
        for (int index = 0; index < lines.length; index++)
          (lines[index].label, scaled.series[index]),
      ],
    );
  }
}
