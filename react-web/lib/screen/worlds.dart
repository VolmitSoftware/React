/// Tuning > Worlds, top: the slowest world's tick time from the live
/// snapshot.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/kit/card.dart';
import '../widget/server_snapshot_state.dart';

class WorldsScreen extends StatelessWidget {
  const WorldsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    if (snapshot == null) return serverSnapshotState(scope: scope);
    // The slowest world's tick time: the plugin reports no series per
    // world, so one line, in ms.
    final SamplerSample? topWorld = snapshot.sampler('top-world-mspt');
    // Titled for what it draws: the slowest world, not every world.
    return ChartCard(
      title: reactorText(ReactorText.commonTopWorldMspt),
      tip: reactorText(ReactorText.worldsSubtitle),
      child: TimeseriesChart(
        unit: 'ms',
        series: <(String, List<double>)>[
          (
            reactorText(ReactorText.commonTopWorldMspt),
            topWorld?.history ?? const <double>[],
          ),
        ],
        height: 160,
      ),
    );
  }
}
