/// One plugin or mod: its live cost and what the profiler caught it doing
/// (its hot methods, its part of the call tree, its threads).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Listenable, ListenableBuilder;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/owner_info.dart';
import '../../model/sampler_sample.dart';
import '../../service/react_client.dart';
import '../../state/server_identity.dart';
import '../../state/server_scope.dart';
import '../../state/server_session.dart';
import '../../state/server_session_scope.dart';
import '../../ui/kit/badge.dart';
import '../../ui/kit/card.dart';
import '../../ui/kit/metric_tile.dart';
import '../../ui/kit/page.dart';
import '../owner_charts.dart';
import '../owners.dart';
import '../profiler.dart';

class OwnerPage extends StatelessWidget {
  const OwnerPage({required this.ownerId, super.key});

  final String ownerId;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IOwnerClient>()) {
      return _page(context, null);
    }
    final ServerIdentity identity = session.identity;
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable?>[session.owners, identity]),
      builder: (BuildContext context) => _page(context, session.owners.owners),
    );
  }

  Widget _page(BuildContext context, List<OwnerInfo>? owners) {
    OwnerCost? cost;
    if (owners != null) {
      final Map<String, SamplerSample> samples =
          ServerScope.of(context)?.snapshot?.byId ??
          const <String, SamplerSample>{};
      for (final OwnerCost each in ownerCosts(owners, samples)) {
        if (each.owner.id == ownerId) cost = each;
      }
    }
    final ServerSession? session = ServerSessionScope.of(context);
    final String name = cost?.owner.name ?? ownerId;
    final OwnerInfo? info = cost?.owner;
    return AppPage(
      title: name,
      children: <Widget>[
        PageStack(
          children: <Widget>[
            if (cost != null && info != null)
              AppCard(
                title: reactorText(
                  session?.identity.modded ?? false
                      ? ReactorText.ownersMod
                      : ReactorText.ownersPlugin,
                ),
                attributes: const <String, String>{'data-owner-cost': ''},
                children: <Widget>[
                  MetricGrid(
                    children: <Widget>[
                      MetricTile(
                        label: reactorText(ReactorText.ownersCost),
                        value: cost.msPerSecond == null
                            ? '--'
                            : ownerCostText(cost.msPerSecond!),
                      ),
                      MetricTile(
                        label: reactorText(ReactorText.ownersShare),
                        value: cost.msPerSecond == null
                            ? '--'
                            : ownerShareText(cost.share),
                      ),
                      MetricTile(
                        label: reactorText(ReactorText.ownersVersion),
                        value: info.version.isEmpty ? '--' : info.version,
                      ),
                      MetricTile(
                        label: reactorText(ReactorText.commonEnabled),
                        value: reactorText(
                          info.enabled
                              ? ReactorText.commonEnabled
                              : ReactorText.commonDisabled,
                        ),
                        tone: info.enabled ? BadgeTone.ok : BadgeTone.neutral,
                      ),
                    ],
                  ),
                ],
              ),
            OwnerCostCharts(
              key: ValueKey<String>('cost-$ownerId'),
              owner: ownerId,
            ),
            ProfilerScreen(
              key: ValueKey<String>('owner-$ownerId'),
              owner: ownerId,
              snapshotLabel: name,
            ),
          ],
        ),
      ],
    );
  }
}
