library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart' show statGrid;
import '../widget/server_snapshot_state.dart';
import '../widget/stat_tile.dart';

const Map<String, String> _kIntegrations = <String, String>{
  'adapt-': 'Adapt',
  'biletools-': 'BileTools',
  'gloss-': 'Gloss',
  'hiddenore-': 'HiddenOre',
  'iris-': 'Iris',
  'wormholes-': 'Wormholes',
};

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
    values.sort((SamplerSample left, SamplerSample right) {
      return left.name.compareTo(right.name);
    });
    return values.isEmpty;
  });
  return samples;
}

class IntegrationsScreen extends StatelessWidget {
  const IntegrationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.integrationsTitle),
      subtitle: reactorText(ReactorText.integrationsSubtitle),
    );
    if (snapshot == null) return statePage!;

    final Map<String, List<SamplerSample>> samples =
        availableIntegrationSamples(snapshot);
    if (samples.isEmpty) {
      return ReactorPage(
        title: reactorText(ReactorText.integrationsTitle),
        subtitle: reactorText(ReactorText.integrationsSubtitle),
        children: <Widget>[
          ReactorEmptyState(
            title: reactorText(ReactorText.integrationsNone),
            description: reactorText(ReactorText.integrationsNoneDescription),
          ),
        ],
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.integrationsTitle),
      subtitle: reactorText(ReactorText.integrationsSubtitle),
      children: samples.entries.map(_integrationSection).toList(),
    );
  }

  Widget _integrationSection(
    MapEntry<String, List<SamplerSample>> integration,
  ) {
    return SectionPanel(
      label: integration.key,
      child: statGrid(
        integration.value.map((SamplerSample sample) {
          return StatTile(label: sample.name, sample: sample);
        }).toList(),
      ),
    );
  }
}
