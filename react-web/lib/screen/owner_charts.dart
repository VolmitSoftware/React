/// The cost charts of Plugins or Mods > Installed and of one owner's page:
/// each owner's share of the cost and the costliest owners split by kind
/// of work, or one owner's cost by kind. Without `/owners/cost` (older
/// servers) the share comes from the live cost samplers and the kinds are
/// left out.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../chart/share_charts.dart';
import '../chart/share_layout.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/owner_cost.dart';
import '../service/react_client.dart';
import '../state/owner_cost_controller.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/card.dart';
import 'owners.dart';
import 'profile_view.dart';

/// How many owners the bars show.
const int kCostBarRows = 8;

/// The colour of cost kind [kind], fixed per kind.
String costKindColor(String kind) => switch (kind) {
  'event' => 'var(--info)',
  'tick' => 'var(--warning)',
  'entity' => 'var(--success)',
  'block_entity' => 'color-mix(in oklab, var(--info) 50%, var(--destructive))',
  'worldgen' => 'color-mix(in oklab, var(--destructive) 75%, var(--warning))',
  'chunk_loader' => 'color-mix(in oklab, var(--info) 45%, var(--success))',
  'network' => 'color-mix(in oklab, var(--foreground) 55%, var(--card))',
  _ => kSeriesColors.last,
};

/// The kinds of [data] in the server's order, as bar kinds.
List<BarKind> costKinds(OwnerCostBreakdown data) => <BarKind>[
  for (final OwnerCostKind kind in data.kinds)
    BarKind(kind.kind, kind.label, costKindColor(kind.kind)),
];

class OwnerCostCharts extends StatefulWidget {
  const OwnerCostCharts({this.owner, this.fallback, super.key});

  /// The owner whose cost by kind is drawn; every owner's share when null.
  final String? owner;

  /// The share drawn when the server has no `/owners/cost`.
  final List<ShareItem>? fallback;

  @override
  State<OwnerCostCharts> createState() => _OwnerCostChartsState();
}

class _OwnerCostChartsState extends State<OwnerCostCharts> {
  IOwnerClient? _client;
  OwnerCostController? _controller;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IOwnerClient? client = ServerSessionScope.of(context)
        ?.clientAs<IOwnerClient>();
    if (identical(client, _client)) return;
    _controller?.dispose();
    _client = client;
    _controller = client == null ? null : OwnerCostController(client);
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final OwnerCostController? controller = _controller;
    if (controller == null) return const Component.fragment(<Widget>[]);
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _charts(context, controller.data),
    );
  }

  Widget _charts(BuildContext context, OwnerCostBreakdown? data) {
    final ServerSession? session = ServerSessionScope.of(context);
    final String serverId = session?.serverId ?? '';
    final String? owner = component.owner;
    if (owner != null) return _ownerKinds(data?.byId(owner), data);
    final bool measured = data != null && data.totalMsPerSecond > 0;
    final List<ShareItem> items = measured
        ? <ShareItem>[
            for (final OwnerCostEntry entry in data.owners)
              ShareItem(entry.id, entry.name, entry.msPerSecond),
          ]
        : component.fallback ?? const <ShareItem>[];
    final List<ShareSlice> slices = shareSlices(
      items,
      otherLabel: reactorText(ReactorText.chartOther),
    );
    if (slices.isEmpty) return const Component.fragment(<Widget>[]);
    final double total = items.fold(
      0,
      (double sum, ShareItem item) => sum + (item.value > 0 ? item.value : 0),
    );
    final List<OwnerCostEntry> costliest = measured
        ? <OwnerCostEntry>[
            for (final OwnerCostEntry entry in data.owners.take(kCostBarRows))
              if (entry.msPerSecond > 0) entry,
          ]
        : const <OwnerCostEntry>[];
    final List<BarKind> kinds = measured ? costKinds(data) : const <BarKind>[];
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'grid-template-columns':
              'repeat(auto-fit, minmax(min(100%, 22rem), 1fr))',
          'gap': '1rem',
          'min-width': '0',
        },
      ),
      attributes: const <String, String>{'data-owner-charts': ''},
      <Widget>[
        AppCard(
          title: reactorText(ReactorText.ownersCostShare),
          children: <Widget>[
            DonutChart(
              slices: slices,
              label: reactorText(ReactorText.ownersCostShare),
              total: ownerCostText(total),
              valueText: ownerCostText,
              href: (String id) => ownerPath(serverId, id),
            ),
          ],
        ),
        if (costliest.isNotEmpty && kinds.isNotEmpty)
          AppCard(
            title: reactorText(ReactorText.ownersCostByKind),
            children: <Widget>[
              StackedBars(
                label: reactorText(ReactorText.ownersCostByKind),
                kinds: kinds,
                valueText: ownerCostText,
                rows: <BarRow>[
                  for (final OwnerCostEntry entry in costliest)
                    BarRow(
                      id: entry.id,
                      label: entry.name,
                      values: entry.kinds,
                      href: ownerPath(serverId, entry.id),
                    ),
                ],
              ),
            ],
          ),
      ],
    );
  }

  Widget _ownerKinds(OwnerCostEntry? entry, OwnerCostBreakdown? data) {
    if (entry == null || data == null || entry.kinds.isEmpty) {
      return const Component.fragment(<Widget>[]);
    }
    final List<String> order = <String>[
      for (final OwnerCostKind kind in data.kinds) kind.kind,
      for (final String kind in entry.kinds.keys)
        if (!data.kinds.any((OwnerCostKind each) => each.kind == kind)) kind,
    ];
    final List<ShareSlice> slices = orderedSlices(<ShareItem>[
      for (final String kind in order)
        if (entry.kinds[kind] case final double value)
          ShareItem(kind, data.labelOf(kind), value),
    ]);
    return AppCard(
      title: reactorText(ReactorText.ownersCostByKind),
      attributes: const <String, String>{'data-owner-kinds': ''},
      children: <Widget>[
        DonutChart(
          slices: slices,
          label: reactorText(ReactorText.ownersCostByKind),
          total: ownerCostText(entry.msPerSecond),
          valueText: ownerCostText,
          colorOf: (ShareSlice slice) => costKindColor(slice.id ?? ''),
        ),
      ],
    );
  }
}
