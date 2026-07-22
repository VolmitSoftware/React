library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

class MechanicsScreen extends StatelessWidget {
  const MechanicsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);

    final SamplerSample? redstone = scope?.snapshot?.sampler('redstone');
    final SamplerSample? redstoneBurstRate = scope?.snapshot?.sampler(
      'redstone-burst-rate',
    );
    final SamplerSample? redstoneTickTime = scope?.snapshot?.sampler(
      'redstone-tick-time',
    );

    final SamplerSample? hopper = scope?.snapshot?.sampler('hopper');
    final SamplerSample? hopperTickTime = scope?.snapshot?.sampler(
      'hopper-tick-time',
    );
    final SamplerSample? hopperChainCoalescing = scope?.snapshot?.sampler(
      'hopper-chain-coalescing',
    );

    final SamplerSample? physics = scope?.snapshot?.sampler('physics');
    final SamplerSample? physicsTickTime = scope?.snapshot?.sampler(
      'physics-tick-time',
    );
    final SamplerSample? fluid = scope?.snapshot?.sampler('fluid');
    final SamplerSample? fluidTickTime = scope?.snapshot?.sampler(
      'fluid-tick-time',
    );

    final SamplerSample? cropFastForward = scope?.snapshot?.sampler(
      'crop-fast-forward',
    );
    final SamplerSample? lazyGravitySkipped = scope?.snapshot?.sampler(
      'lazy-gravity-skipped',
    );
    final SamplerSample? spawnerLightCacheSkipped = scope?.snapshot?.sampler(
      'spawner-light-cache-skipped',
    );
    final SamplerSample? explosionPacketReduction = scope?.snapshot?.sampler(
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
        sectionCard(
          label: reactorText(ReactorText.commonRedstone),
          child: Collection(
            gap: 12,
            children: <Widget>[
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
              TimeseriesChart(series: redstoneTickSeries, height: 120),
            ],
          ),
        ),
        sectionCard(
          label: reactorText(ReactorText.commonHoppers),
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
        sectionCard(
          label: reactorText(ReactorText.mechanicsPhysicsFluids),
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
        sectionCard(
          label: reactorText(ReactorText.commonOptimizations),
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
