/// Parts the Monitor pages share: a sampler as a tile, the samplers of a
/// list of ids, and the address of another page of the same server.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../app/paths.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/reading.dart';
import '../model/units.dart';
import '../model/server_snapshot.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/metric_tile.dart';

/// [sample] as a tile with its trend line; "--" while it has no reading,
/// with a note when the server said it cannot report it ([notReported]).
/// Figure and unit come from [readingOf]; with [thresholds] the tile takes
/// the reading's state as its tone and caption unless [tone] is given.
MetricTile samplerTile(
  String label,
  SamplerSample? sample, {
  AlertThresholds? thresholds,
  BadgeTone? tone,
  String? caption,
  double? meter,
  bool notReported = false,
}) {
  final Reading reading = readingOf(sample, thresholds);
  final bool judged = tone == null && reading.level != HealthLevel.unknown;
  return MetricTile(
    label: label,
    value: reading.value,
    unit: reading.unit,
    caption: judged ? levelWord(reading.level) : caption,
    note: sample == null && notReported
        ? reactorText(ReactorText.commonNotReported)
        : null,
    tone: judged ? levelTone(reading.level) : tone ?? BadgeTone.neutral,
    meter: meter,
    history: sample?.history ?? const <double>[],
  );
}

/// Memory in use, with a meter of [pressure] (percent of the heap) when
/// the server reports it. With the heap's [max] the value keeps that unit
/// ("0.9 GB", never "879 MB" a second later) and says "of 16.0 GB".
MetricTile memoryTile(
  String label,
  SamplerSample? sample, {
  SamplerSample? pressure,
  SamplerSample? max,
  BadgeTone? tone,
  String? caption,
}) {
  final MetricTile plain = samplerTile(
    label,
    sample,
    tone: tone,
    caption: caption,
    meter: pressure == null ? null : pressure.value / 100,
  );
  if (sample == null || max == null || max.value <= 0) return plain;
  final Reading used = readingAt(max, sample.value);
  return MetricTile(
    label: label,
    value: used.value,
    unit: used.unit,
    caption: plain.caption,
    note: reactorText(ReactorText.environmentOfTotal, <String, Object?>{
      'total': readingOf(max).text,
    }),
    tone: plain.tone,
    meter: plain.meter,
    history: plain.history,
  );
}

/// The samplers of [ids] that [snapshot] carries, in that order.
List<SamplerSample> samplersOf(ServerSnapshot snapshot, List<String> ids) =>
    <SamplerSample>[for (final String id in ids) ?snapshot.sampler(id)];

/// A tile per sampler, named as the server names it (without a trailing
/// unit word the value already shows).
List<Widget> namedTiles(List<SamplerSample> samples) => <Widget>[
  for (final SamplerSample sample in samples)
    samplerTile(labelWithoutUnit(sample.name, sample.suffix), sample),
];

/// The id of the server whose page is open: its session's, else the
/// address's.
String? currentServerId(BuildContext context) =>
    ServerSessionScope.of(context)?.serverId ??
    RouteState.maybeOf(context)?.params['id'];

/// A query value of the open address (`?metric=`), or null.
String? routeQuery(BuildContext context, String name) =>
    RouteState.maybeOf(context)?.queryParams[name];

/// The address of [page] on server [serverId] with an optional [query].
String serverPagePath(
  String serverId,
  String page, [
  Map<String, String> query = const <String, String>{},
]) {
  final String path = serverPath(serverId, page);
  if (query.isEmpty) return path;
  return Uri(path: path, queryParameters: query).toString();
}
