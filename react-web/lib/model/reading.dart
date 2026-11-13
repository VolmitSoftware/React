/// The one path from a plugin's sampler to what a page shows: the figure,
/// its unit and, for a metric with a line, its state. Tiles, the verdict
/// line, alerts, Compare and the metric lists all read samplers here.
///
/// The plugin sends `value` in base units (bytes for sizes, bytes per
/// second for size rates), `display` already formatted for the in-game
/// monitor, and a suffix whose in-game tag [metricUnit] has stripped.
/// Sizes are written from the bytes, so every size reads the same way
/// ("3.0 GB") whatever unit the plugin picked; everything else is the
/// plugin's own `display` with its unit.
library;

import 'alert_thresholds.dart';
import 'health.dart';
import 'sampler_sample.dart';
import 'units.dart';

/// A sampler as the page shows it.
class Reading {
  const Reading({
    required this.value,
    required this.unit,
    this.level = HealthLevel.unknown,
  });

  /// No reading: "--", no unit, no state.
  static const Reading none = Reading(value: '--', unit: '');

  /// The figure ("3.0", "19.9", "--").
  final String value;

  /// Its unit ("GB", "ms", "%", or empty).
  final String unit;

  /// The state from the shared verdict; unknown without a line or
  /// thresholds.
  final HealthLevel level;

  /// The figure with its unit ("3.0 GB", "18.9%").
  String get text => withUnit(value, unit);
}

/// [sample] as the page shows it, with its state when [thresholds] are
/// given and the sampler has a line ([HealthMetric.ofSampler]).
Reading readingOf(SamplerSample? sample, [AlertThresholds? thresholds]) {
  if (sample == null) return Reading.none;
  final HealthMetric? metric = HealthMetric.ofSampler(sample.id);
  final HealthLevel level = metric == null || thresholds == null
      ? HealthLevel.unknown
      : metricHealth(metric, sample.value, thresholds);
  final _Size? size = _sizeOf(sample);
  if (size != null) {
    final (String value, String unit) = byteSize(sample.value * size.factor);
    return Reading(value: value, unit: '$unit${size.rate}', level: level);
  }
  return Reading(value: sample.display, unit: sample.suffix, level: level);
}

/// Another [value] of [sample] (its minimum, its maximum) written like its
/// current reading: a size in the same unit, anything else with as many
/// decimals as the plugin's own figure, so the current reading never falls
/// outside the range beside it.
Reading readingAt(SamplerSample sample, double value) {
  final _Size? size = _sizeOf(sample);
  if (size != null) {
    final int power = bytePower(sample.value * size.factor);
    final double v = value * size.factor / bytesIn(power);
    return Reading(
      value: power == 0 || v.abs() >= 100
          ? groupThousands(v)
          : v.toStringAsFixed(1),
      unit: '${kByteUnits[power]}${size.rate}',
    );
  }
  final String shown = sample.display;
  final int dot = shown.indexOf('.');
  final int digits = dot < 0
      ? 0
      : RegExp(r'[0-9]').allMatches(shown.substring(dot + 1)).length;
  return Reading(
    value: value.toStringAsFixed(digits.clamp(0, 3)),
    unit: sample.suffix,
  );
}

/// The values of a chart of [samples] in one unit: sizes in the unit of
/// the largest current reading ("GB"), everything else as sent.
class ChartValues {
  const ChartValues(this.unit, this.series);

  /// The axis unit.
  final String unit;

  /// Each sample's history in [unit], in the order given; empty for a
  /// missing sample.
  final List<List<double>> series;
}

/// [samples]' histories in one unit for one chart; [values] stands in for
/// the histories when given (a stored range), one list per sample.
ChartValues chartValuesOf(
  List<SamplerSample?> samples, {
  List<List<double>>? values,
}) {
  List<double> series(int index) =>
      values?[index] ?? samples[index]?.history ?? const <double>[];
  final List<_Size?> sizes = <_Size?>[
    for (final SamplerSample? sample in samples)
      sample == null ? null : _sizeOf(sample),
  ];
  double largest = 0;
  String rate = '';
  for (int index = 0; index < samples.length; index++) {
    final _Size? size = sizes[index];
    if (size == null) continue;
    final double bytes = samples[index]!.value.abs() * size.factor;
    if (bytes > largest) largest = bytes;
    rate = size.rate;
  }
  final bool anySize = sizes.any((_Size? size) => size != null);
  if (!anySize) {
    final SamplerSample? first = samples.whereType<SamplerSample>().firstOrNull;
    return ChartValues(first?.suffix ?? '', <List<double>>[
      for (int index = 0; index < samples.length; index++) series(index),
    ]);
  }
  final int power = bytePower(largest);
  final double per = bytesIn(power);
  return ChartValues('${kByteUnits[power]}$rate', <List<double>>[
    for (int index = 0; index < samples.length; index++)
      <double>[
        for (final double value in series(index))
          value * (sizes[index]?.factor ?? 1) / per,
      ],
  ]);
}

/// Writes a stored value of a sampler whose unit is [unit] when that unit
/// is a size: the plugin stores sizes in bytes ("2.5 GB", "79.7 MB/s").
/// Null for any other unit.
String Function(double value)? sizeFormatter(String unit) {
  final RegExpMatch? match = _sizeUnit.firstMatch(unit.trim());
  if (match == null) return null;
  final String rate = match.group(2) ?? '';
  return (double bytes) => '${humanBytes(bytes)}$rate';
}

/// How a size reading's values turn into bytes, and its "/s" when it is a
/// rate.
typedef _Size = ({double factor, String rate});

final RegExp _sizeUnit = RegExp(r'^(B|KB|MB|GB|TB|PB)(/[a-z]+)?$');

/// Null unless [sample] is a size. The plugin sends the bytes with the
/// figure in a larger unit ("3" GB for 3172391016); a source that sends
/// the value in the unit it shows (13357 MB) is scaled up by its unit.
_Size? _sizeOf(SamplerSample sample) {
  final RegExpMatch? match = _sizeUnit.firstMatch(sample.suffix.trim());
  if (match == null) return null;
  final int power = kByteUnits.indexOf(match.group(1)!);
  final String rate = match.group(2) ?? '';
  final double? shown = double.tryParse(sample.display.replaceAll(',', ''));
  final bool inBytes =
      power == 0 ||
      (shown != null && shown > 0 && sample.value / shown >= 512) ||
      (shown == 0 && sample.value >= 512);
  return (factor: inBytes ? 1 : bytesIn(power), rate: rate);
}
