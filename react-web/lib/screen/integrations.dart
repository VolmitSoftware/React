/// Plugins > Integrations: the live numbers Volmit plugins report through
/// React, one group of tiles per plugin.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/units.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../ui/kit/states.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

const Map<String, String> _kIntegrations = <String, String>{
  'adapt-': 'Adapt',
  'animus-': 'Animus',
  'biletools-': 'BileTools',
  'gloss-': 'Gloss',
  'hiddenore-': 'HiddenOre',
  'iris-': 'Iris',
  'wormholes-': 'Wormholes',
};

/// The available samplers of each detected plugin, by plugin name, sorted
/// by sampler name; plugins without a reading are left out.
Map<String, List<SamplerSample>> availableIntegrationSamples(
  ServerSnapshot snapshot,
) {
  final Map<String, List<SamplerSample>> samples =
      <String, List<SamplerSample>>{
        for (final String name in _kIntegrations.values)
          name: <SamplerSample>[],
      };
  for (final SamplerSample sample in snapshot.byId.values) {
    if (!sample.available) continue;
    for (final MapEntry<String, String> integration in _kIntegrations.entries) {
      if (sample.id.startsWith(integration.key)) {
        samples[integration.value]!.add(sample);
        break;
      }
    }
  }
  samples.removeWhere((String name, List<SamplerSample> values) {
    values.sort(
      (SamplerSample left, SamplerSample right) =>
          left.name.compareTo(right.name),
    );
    return values.isEmpty;
  });
  return samples;
}

/// [name] without a leading [plugin] ("Iris Pregen Queue" under Iris reads
/// "Pregen Queue").
String withoutPluginName(String name, String plugin) {
  final String prefix = '${plugin.toLowerCase()} ';
  return name.toLowerCase().startsWith(prefix) && name.length > prefix.length
      ? name.substring(prefix.length)
      : name;
}

class IntegrationsScreen extends StatelessWidget {
  const IntegrationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);
    final Map<String, List<SamplerSample>> samples =
        availableIntegrationSamples(snapshot);
    if (samples.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.integrationsNone),
        description: reactorText(ReactorText.integrationsNoneDescription),
      );
    }
    return PageStack(
      children: <Widget>[
        for (final MapEntry<String, List<SamplerSample>> plugin
            in samples.entries)
          PageSection(
            title: plugin.key,
            children: <Widget>[
              MetricGrid(
                children: <Widget>[
                  for (final SamplerSample sample in plugin.value)
                    samplerTile(
                      labelWithoutUnit(
                        withoutPluginName(sample.name, plugin.key),
                        sample.suffix,
                      ),
                      sample,
                    ),
                ],
              ),
            ],
          ),
      ],
    );
  }
}
