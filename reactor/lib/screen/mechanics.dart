library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
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
    final SamplerSample? redstoneBurstRate =
        scope?.snapshot?.sampler('redstone-burst-rate');
    final SamplerSample? redstoneTickTime =
        scope?.snapshot?.sampler('redstone-tick-time');

    final SamplerSample? hopper = scope?.snapshot?.sampler('hopper');
    final SamplerSample? hopperTickTime =
        scope?.snapshot?.sampler('hopper-tick-time');
    final SamplerSample? hopperChainCoalescing =
        scope?.snapshot?.sampler('hopper-chain-coalescing');

    final SamplerSample? physics = scope?.snapshot?.sampler('physics');
    final SamplerSample? physicsTickTime =
        scope?.snapshot?.sampler('physics-tick-time');
    final SamplerSample? fluid = scope?.snapshot?.sampler('fluid');
    final SamplerSample? fluidTickTime =
        scope?.snapshot?.sampler('fluid-tick-time');

    final SamplerSample? cropFastForward =
        scope?.snapshot?.sampler('crop-fast-forward');
    final SamplerSample? lazyGravitySkipped =
        scope?.snapshot?.sampler('lazy-gravity-skipped');
    final SamplerSample? spawnerLightCacheSkipped =
        scope?.snapshot?.sampler('spawner-light-cache-skipped');
    final SamplerSample? explosionPacketReduction =
        scope?.snapshot?.sampler('explosion-packet-reduction');

    final List<(String, List<double>)> redstoneTickSeries =
        <(String, List<double>)>[
      ('Redstone Tick Time', redstoneTickTime?.history ?? const <double>[]),
    ];

    return ReactorPage(
      title: 'Mechanics',
      subtitle: 'Game mechanic optimizations',
      children: <Widget>[
        sectionCard(
          label: 'Redstone',
          child: Collection(
            gap: 12,
            children: <Widget>[
              statGrid(<Widget>[
                StatTile(label: 'Redstone', sample: redstone),
                StatTile(label: 'Burst Rate', sample: redstoneBurstRate),
                StatTile(label: 'Tick Time', sample: redstoneTickTime),
              ]),
              TimeseriesChart(series: redstoneTickSeries, height: 120),
            ],
          ),
        ),
        sectionCard(
          label: 'Hoppers',
          child: statGrid(<Widget>[
            StatTile(label: 'Hoppers', sample: hopper),
            StatTile(label: 'Tick Time', sample: hopperTickTime),
            StatTile(
              label: 'Chain Coalescing',
              sample: hopperChainCoalescing,
            ),
          ]),
        ),
        sectionCard(
          label: 'Physics & Fluids',
          child: statGrid(<Widget>[
            StatTile(label: 'Physics', sample: physics),
            StatTile(label: 'Physics Tick Time', sample: physicsTickTime),
            StatTile(label: 'Fluid', sample: fluid),
            StatTile(label: 'Fluid Tick Time', sample: fluidTickTime),
          ]),
        ),
        sectionCard(
          label: 'Optimizations',
          child: statGrid(<Widget>[
            StatTile(label: 'Crop Fast-Forward', sample: cropFastForward),
            StatTile(
              label: 'Lazy Gravity Skipped',
              sample: lazyGravitySkipped,
            ),
            StatTile(
              label: 'Spawner Light Cache Skipped',
              sample: spawnerLightCacheSkipped,
            ),
            StatTile(
              label: 'Explosion Packet Reduction',
              sample: explosionPacketReduction,
            ),
          ]),
        ),
      ],
    );
  }
}
