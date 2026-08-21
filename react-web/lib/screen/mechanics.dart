library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class MechanicsScreen extends StatelessWidget {
  const MechanicsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) {
      return ReactorPage(
        title: reactorText(ReactorText.mechanicsTitle),
        subtitle: reactorText(ReactorText.mechanicsSubtitle),
        children: <Widget>[
          ReactorLoadingState(label: reactorText(ReactorText.metricsWaiting)),
        ],
      );
    }

    final SamplerSample? redstone = snapshot.sampler('redstone');
    final SamplerSample? redstoneBurstRate = snapshot.sampler(
      'redstone-burst-rate',
    );
    final SamplerSample? redstoneTickTime = snapshot.sampler(
      'redstone-tick-time',
    );

    final SamplerSample? hopper = snapshot.sampler('hopper');
    final SamplerSample? hopperTickTime = snapshot.sampler('hopper-tick-time');
    final SamplerSample? hopperChainCoalescing = snapshot.sampler(
      'hopper-chain-coalescing',
    );

    final SamplerSample? physics = snapshot.sampler('physics');
    final SamplerSample? physicsTickTime = snapshot.sampler(
      'physics-tick-time',
    );
    final SamplerSample? fluid = snapshot.sampler('fluid');
    final SamplerSample? fluidTickTime = snapshot.sampler('fluid-tick-time');

    final SamplerSample? cropFastForward = snapshot.sampler(
      'crop-fast-forward',
    );
    final SamplerSample? lazyGravitySkipped = snapshot.sampler(
      'lazy-gravity-skipped',
    );
    final SamplerSample? spawnerLightCacheSkipped = snapshot.sampler(
      'spawner-light-cache-skipped',
    );
    final SamplerSample? explosionPacketReduction = snapshot.sampler(
      'explosion-packet-reduction',
    );

    final List<(String, List<double>)> redstoneTickSeries =
        <(String, List<double>)>[
          (
            reactorText(ReactorText.mechanicsRedstoneTickTime),
            redstoneTickTime?.history ?? const <double>[],
          ),
        ];

    return ReactorPage(
      title: reactorText(ReactorText.mechanicsTitle),
      subtitle: reactorText(ReactorText.mechanicsSubtitle),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.commonRedstone),
          children: <Widget>[
            TimeseriesChart(series: redstoneTickSeries, height: 120),
            statGrid(<Widget>[
              StatTile(
                label: reactorText(ReactorText.commonRedstone),
                sample: redstone,
              ),
              StatTile(
                label: reactorText(ReactorText.mechanicsBurstRate),
                sample: redstoneBurstRate,
              ),
              StatTile(
                label: reactorText(ReactorText.commonTickTime),
                sample: redstoneTickTime,
              ),
            ]),
          ],
        ),
        SectionPanel(
          label: reactorText(ReactorText.commonHoppers),
          flush: true,
          child: statGrid(<Widget>[
            StatTile(
              label: reactorText(ReactorText.commonHoppers),
              sample: hopper,
            ),
            StatTile(
              label: reactorText(ReactorText.commonTickTime),
              sample: hopperTickTime,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsChainCoalescing),
              sample: hopperChainCoalescing,
            ),
          ]),
        ),
        SectionPanel(
          label: reactorText(ReactorText.mechanicsPhysicsFluids),
          flush: true,
          child: statGrid(<Widget>[
            StatTile(
              label: reactorText(ReactorText.commonPhysics),
              sample: physics,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsPhysicsTickTime),
              sample: physicsTickTime,
            ),
            StatTile(
              label: reactorText(ReactorText.commonFluid),
              sample: fluid,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsFluidTickTime),
              sample: fluidTickTime,
            ),
          ]),
        ),
        SectionPanel(
          label: reactorText(ReactorText.commonOptimizations),
          flush: true,
          child: statGrid(<Widget>[
            StatTile(
              label: reactorText(ReactorText.mechanicsCropFastForward),
              sample: cropFastForward,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsLazyGravitySkipped),
              sample: lazyGravitySkipped,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsSpawnerLightCacheSkipped),
              sample: spawnerLightCacheSkipped,
            ),
            StatTile(
              label: reactorText(ReactorText.mechanicsExplosionPacketReduction),
              sample: explosionPacketReduction,
            ),
          ]),
        ),
      ],
    );
  }
}
