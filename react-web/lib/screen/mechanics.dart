/// Activity > Mechanics: redstone, hoppers, physics and fluids, and what
/// React's optimizations saved.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

class MechanicsScreen extends StatelessWidget {
  const MechanicsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);

    MetricTile tile(ReactorText label, String id) =>
        samplerTile(reactorText(label), snapshot.sampler(id));

    final SamplerSample? redstoneSpan = snapshot.sampler('redstone-event-span');
    final String redstoneSpanLabel = reactorText(
      ReactorText.mechanicsRedstoneEventSpan,
    );

    return PageStack(
      children: <Widget>[
        PageSection(
          title: reactorText(ReactorText.commonRedstone),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                tile(ReactorText.mechanicsUpdates, 'redstone'),
                tile(ReactorText.mechanicsBurstRate, 'redstone-burst-rate'),
                samplerTile(redstoneSpanLabel, redstoneSpan),
              ],
            ),
          ],
        ),
        PageSection(
          title: reactorText(ReactorText.commonHoppers),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                tile(ReactorText.mechanicsUpdates, 'hopper'),
                tile(ReactorText.mechanicsHopperEventSpan, 'hopper-event-span'),
                tile(
                  ReactorText.mechanicsChainCoalescing,
                  'hopper-chain-coalescing',
                ),
              ],
            ),
          ],
        ),
        PageSection(
          title: reactorText(ReactorText.mechanicsPhysicsFluids),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                tile(ReactorText.commonPhysics, 'physics'),
                tile(
                  ReactorText.mechanicsPhysicsEventSpan,
                  'physics-event-span',
                ),
                tile(ReactorText.commonFluid, 'fluid'),
                tile(ReactorText.mechanicsFluidEventSpan, 'fluid-event-span'),
              ],
            ),
          ],
        ),
        PageSection(
          title: reactorText(ReactorText.commonOptimizations),
          children: <Widget>[
            MetricGrid(
              children: <Widget>[
                tile(ReactorText.mechanicsCropFastForward, 'crop-fast-forward'),
                tile(
                  ReactorText.mechanicsLazyGravitySkipped,
                  'lazy-gravity-skipped',
                ),
                tile(
                  ReactorText.mechanicsSpawnerLightCacheSkipped,
                  'spawner-light-cache-skipped',
                ),
                tile(
                  ReactorText.mechanicsExplosionPacketReduction,
                  'explosion-packet-reduction',
                ),
              ],
            ),
          ],
        ),
      ],
    );
  }
}
