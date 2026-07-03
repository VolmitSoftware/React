library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart' show sectionCard, statGrid;
import '../widget/stat_tile.dart';

const List<String> _kAdaptIds = <String>[
  'adapt-ability-checks-per-tick',
  'adapt-ability-ops',
  'adapt-session-load',
  'adapt-world-policy-latency',
];

const List<String> _kIrisIds = <String>[
  'iris-biome-cache-hit-rate',
  'iris-chunk-stream-ms',
  'iris-pregen-queue',
];

const List<String> _kWormholesIds = <String>[
  'wormholes-block-changes',
  'wormholes-packets',
  'wormholes-portals',
  'wormholes-projection-observers',
  'wormholes-projection-render-ms',
  'wormholes-projections-active',
  'wormholes-spoofed-entities',
  'wormholes-traversals',
];

bool _hasAny(ServerSnapshot? s, List<String> ids) {
  if (s == null) return false;
  for (final String id in ids) {
    if (s.byId.containsKey(id)) return true;
  }
  return false;
}

class IntegrationsScreen extends StatelessWidget {
  const IntegrationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;

    final bool hasAdapt = _hasAny(snapshot, _kAdaptIds);
    final bool hasIris = _hasAny(snapshot, _kIrisIds);
    final bool hasWormholes = _hasAny(snapshot, _kWormholesIds);

    if (!hasAdapt && !hasIris && !hasWormholes) {
      return ReactorPage(
        title: 'Integrations',
        subtitle: 'Detected plugin integrations',
        children: <Widget>[
          ArcaneEmptyState.noData(
            title: 'No integrations detected',
            description:
                'No Adapt, Iris, or Wormholes metrics are being reported by this server.',
          ),
        ],
      );
    }

    final List<Widget> sections = <Widget>[];

    if (hasAdapt) {
      sections.add(_AdaptSection(snapshot: snapshot));
    }
    if (hasIris) {
      sections.add(_IrisSection(snapshot: snapshot));
    }
    if (hasWormholes) {
      sections.add(_WormholesSection(snapshot: snapshot));
    }

    return ReactorPage(
      title: 'Integrations',
      subtitle: 'Detected plugin integrations',
      children: sections,
    );
  }
}

class _AdaptSection extends StatelessWidget {
  final ServerSnapshot? snapshot;

  const _AdaptSection({required this.snapshot});

  @override
  Widget build(BuildContext context) {
    final List<Widget> tiles = <Widget>[];
    for (final String id in _kAdaptIds) {
      final SamplerSample? s = snapshot?.sampler(id);
      if (s != null) {
        tiles.add(StatTile(label: _labelFor(id), sample: s));
      }
    }

    final SamplerSample? headline = snapshot?.sampler('adapt-world-policy-latency');

    return sectionCard(
      label: 'Adapt',
      child: Collection(
        gap: 12,
        children: <Widget>[
          statGrid(tiles),
          if (headline != null)
            TimeseriesChart(
              series: <(String, List<double>)>[
                ('Policy Latency', headline.history),
              ],
              height: 100,
            ),
        ],
      ),
    );
  }
}

class _IrisSection extends StatelessWidget {
  final ServerSnapshot? snapshot;

  const _IrisSection({required this.snapshot});

  @override
  Widget build(BuildContext context) {
    final List<Widget> tiles = <Widget>[];
    for (final String id in _kIrisIds) {
      final SamplerSample? s = snapshot?.sampler(id);
      if (s != null) {
        tiles.add(StatTile(label: _labelFor(id), sample: s));
      }
    }

    final SamplerSample? headline = snapshot?.sampler('iris-chunk-stream-ms');

    return sectionCard(
      label: 'Iris',
      child: Collection(
        gap: 12,
        children: <Widget>[
          statGrid(tiles),
          if (headline != null)
            TimeseriesChart(
              series: <(String, List<double>)>[
                ('Chunk Stream ms', headline.history),
              ],
              height: 100,
            ),
        ],
      ),
    );
  }
}

class _WormholesSection extends StatelessWidget {
  final ServerSnapshot? snapshot;

  const _WormholesSection({required this.snapshot});

  @override
  Widget build(BuildContext context) {
    final List<Widget> tiles = <Widget>[];
    for (final String id in _kWormholesIds) {
      final SamplerSample? s = snapshot?.sampler(id);
      if (s != null) {
        tiles.add(StatTile(label: _labelFor(id), sample: s));
      }
    }

    final SamplerSample? headline =
        snapshot?.sampler('wormholes-projection-render-ms');

    return sectionCard(
      label: 'Wormholes',
      child: Collection(
        gap: 12,
        children: <Widget>[
          statGrid(tiles),
          if (headline != null)
            TimeseriesChart(
              series: <(String, List<double>)>[
                ('Projection Render ms', headline.history),
              ],
              height: 100,
            ),
        ],
      ),
    );
  }
}

String _labelFor(String id) {
  final String stripped = id
      .replaceFirst('adapt-', '')
      .replaceFirst('iris-', '')
      .replaceFirst('wormholes-', '');
  final List<String> words = stripped.split('-');
  return words.map((String w) {
    if (w.isEmpty) return w;
    return w[0].toUpperCase() + w.substring(1);
  }).join(' ');
}
