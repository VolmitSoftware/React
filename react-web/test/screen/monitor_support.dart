// Shared fixtures for the Monitor screen tests (Overview, Performance,
// Activity, Metrics).
import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:react_web/model/metric_history.dart';
import 'package:react_web/model/range_history.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/chart_history.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:test/test.dart' show addTearDown;

const ReactorStylesheet monitorSheet = ReactorStylesheet();

SamplerSample sample(
  String id,
  double value, {
  String suffix = '',
  String? name,
  String? display,
  List<double>? history,
}) => SamplerSample(
  id: id,
  name: name ?? id,
  suffix: suffix,
  value: value,
  display: display ?? value.toString(),
  min: value,
  max: value,
  history: history ?? <double>[value * 0.9, value],
);

ServerSnapshot snapshotOf(List<SamplerSample> samples, {int seq = 1}) =>
    ServerSnapshot(
      byId: <String, SamplerSample>{
        for (final SamplerSample each in samples) each.id: each,
      },
      at: DateTime(2026, 11, 12),
      seq: seq,
    );

/// [child] under the theme, a mode and a server's live snapshot.
Widget onServer(
  Widget child, {
  ServerSnapshot? snapshot,
  ConnState state = ConnState.live,
  IHistoryClient? historyClient,
  ChartHistory? charts,
  ReactorMode mode = ReactorMode.simple,
}) => ArcaneThemeProvider(
  stylesheet: monitorSheet,
  child: ModeScope(
    mode: mode,
    onChanged: (ReactorMode _) {},
    child: ServerScope(
      snapshot: snapshot,
      state: state,
      historyClient: historyClient,
      charts: charts,
      child: child,
    ),
  ),
);

/// How often [needle] occurs in [body].
int occurrences(String body, String needle) => needle.allMatches(body).length;

/// A fleet with one session per entry of [clients] (server id -> client),
/// as the shell has once the servers are paired. Each client must report the
/// fingerprint `fp-<id>`. Dispose the fleet in the test.
Future<FleetManager> fleetOf(Map<String, IReactClient> clients) async {
  final FleetManager fleet = FleetManager(
    storage: InMemoryFleetStorage(),
    clientFactory: (ServerCredential credential) => clients[credential.id]!,
  );
  int index = 0;
  for (final String id in clients.keys) {
    index++;
    fleet.add(
      ServerCredential(
        id: id,
        label: id,
        host: '$id.example',
        port: 9696,
        bearer: 'token-$index.sig',
        fingerprint: 'fp-$id',
      ),
    );
  }
  return fleet;
}

/// A server that stored [values] (sampler id -> the value of every bucket)
/// for every chart range.
class RangeClient implements IHistoryClient {
  RangeClient(this.values);

  final Map<String, double> values;

  @override
  Future<RangeHistory> metricRange(
    ChartRange range, {
    List<String>? ids,
  }) async {
    final int step = range.step.inMilliseconds;
    final int to = 1763000000000 ~/ step * step;
    return RangeHistory.fromJson(<String, dynamic>{
      'range': range.id,
      'stepMs': step,
      'fromMs': to - ChartRange.buckets * step,
      'toMs': to,
      'series': <Map<String, dynamic>>[
        for (final MapEntry<String, double> entry in values.entries)
          <String, dynamic>{
            'id': entry.key,
            'values': List<double>.generate(
              ChartRange.buckets,
              (int index) => entry.value + index % 5,
            ),
          },
      ],
    });
  }

  @override
  Future<List<MetricHistoryDescriptor>> historyCatalog() async =>
      const <MetricHistoryDescriptor>[];

  @override
  Future<MetricHistoryPage> historyPage({
    List<String>? ids,
    DateTime? from,
    DateTime? to,
    int maxPoints = 1200,
    int pageSize = 256,
    String? cursor,
  }) => throw UnimplementedError();
}

/// Chart ranges of a [RangeClient] server with [range] picked and loaded.
Future<ChartHistory> chartsOn(
  ChartRange range,
  Map<String, double> values,
) async {
  final ChartHistory charts = ChartHistory(
    RangeClient(values),
    const Stream<ServerSnapshot>.empty(),
  );
  addTearDown(charts.dispose);
  charts.select(range);
  await Future<void>.delayed(Duration.zero);
  return charts;
}
