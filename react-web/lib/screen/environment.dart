/// Performance > Host: the machine and JVM the server runs on. Memory, disk
/// capacity and throughput for everyone; device lists and the raw key/value
/// sections in Advanced. React's own facts (its version) are never shown.
library;

import 'dart:async' show Timer;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/range_chart.dart';
import '../chart/timeseries_chart.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/environment_info.dart';
import '../model/ring_buffer.dart';
import '../model/health.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../model/units.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/server_scope.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/card.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import 'monitor_parts.dart';

/// Host samplers shown to everyone.
const List<String> kHostEverydayIds = <String>[
  'physical-memory-used',
  'physical-memory-free',
  'disk-usable',
];

/// Host samplers shown when they are above zero, or always in Advanced.
const List<String> kHostProblemIds = <String>[
  'network-receive-drops',
  'network-receive-errors',
  'network-send-errors',
];

const List<String> _kPreferredOrder = <String>[
  'cpu',
  'memory',
  'jvm',
  'server',
];

/// Disk use from which a mount reads as nearly full, then full.
const (double, double) kMountLevels = (0.8, 0.9);

final RegExp _kSectionCode = RegExp('§.');

/// Whether a raw entry is about React itself rather than the server or JVM.
bool isReactFact(String key) => key.toLowerCase().contains('react');

String _sectionLabel(String key) {
  if (key == 'cpu' || key == 'jvm') return key.toUpperCase();
  if (key == 'memory') return reactorText(ReactorText.memoryTitle);
  if (key == 'server') return reactorText(ReactorText.commonServer);
  if (key.isEmpty) return key;
  return key[0].toUpperCase() + key.substring(1);
}

String _humanRate(double bytesPerSecond) => '${humanBytes(bytesPerSecond)}/s';

/// A rate history worth drawing: one reading draws no line, so the chart
/// waits for the second.
List<double> _drawable(List<double> history) =>
    history.length < 2 ? const <double>[] : history;

bool _looksLikeBytes(String section, String key) {
  final String k = key.toLowerCase().replaceAll(' ', '');
  if (k.endsWith('mb') ||
      k.endsWith('kb') ||
      k.endsWith('gb') ||
      k.endsWith('tb') ||
      k.contains('percent') ||
      k.contains('pct')) {
    return false;
  }
  if (k.contains('byte')) return true;
  final String s = section.toLowerCase();
  final bool byteSection =
      s.contains('mem') ||
      s.contains('disk') ||
      s.contains('storage') ||
      s.contains('gpu');
  if (!byteSection) return false;
  if (k.startsWith('physical') ||
      k.startsWith('virtual') ||
      k.startsWith('swap') ||
      k.startsWith('heap')) {
    return true;
  }
  if (k.contains('vram')) return true;
  return k.endsWith('total') ||
      k.endsWith('free') ||
      k.endsWith('used') ||
      k.endsWith('max') ||
      k.endsWith('size') ||
      k.endsWith('capacity');
}

String _formatEnvValue(String section, String key, Object? value) {
  if (value == null) return '';
  if (value is num && _looksLikeBytes(section, key)) {
    return humanBytes(value.toDouble());
  }
  if (value is List) return value.join(', ');
  return value.toString().replaceAll(_kSectionCode, '');
}

class EnvironmentView extends StatelessWidget {
  final EnvironmentInfo info;
  final ServerSnapshot? snapshot;
  final List<double> diskReadHistory;
  final List<double> diskWriteHistory;
  final List<double> networkReceiveHistory;
  final List<double> networkSendHistory;

  const EnvironmentView({
    required this.info,
    this.snapshot,
    this.diskReadHistory = const <double>[],
    this.diskWriteHistory = const <double>[],
    this.networkReceiveHistory = const <double>[],
    this.networkSendHistory = const <double>[],
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final bool advanced = ModeScope.advanced(context);
    final ServerSnapshot? snap = snapshot;
    // The disk and network rates are the charts' (no tile repeats them);
    // with mounts listed, their tiles say what "disk usable" would.
    final bool mountTiles = info.mounts.any(
      (EnvironmentMount mount) => !mount.isSystem,
    );
    final List<SamplerSample> hostSamples = snap == null
        ? const <SamplerSample>[]
        : <SamplerSample>[
            for (final SamplerSample sample in samplersOf(
              snap,
              kHostEverydayIds,
            ))
              if (!(mountTiles && sample.id == 'disk-usable')) sample,
            for (final SamplerSample sample in samplersOf(
              snap,
              kHostProblemIds,
            ))
              if (advanced || sample.value > 0) sample,
          ];
    // System and read-only pseudo mounts stay out of the tiles: they are
    // near full by design. Advanced lists them, folded, without a state.
    final List<EnvironmentMount> systemMounts = <EnvironmentMount>[
      for (final EnvironmentMount mount in info.mounts)
        if (mount.isSystem) mount,
    ];
    final List<Widget> tiles = <Widget>[
      ...namedTiles(hostSamples),
      for (final EnvironmentMount mount in info.mounts)
        if (!mount.isSystem) _mountTile(mount),
    ];

    return PageStack(
      children: <Widget>[
        if (tiles.isNotEmpty) MetricGrid(children: tiles),
        if (info.disks.isNotEmpty || info.mounts.isNotEmpty) _diskChart(),
        if (info.network.isNotEmpty) _networkChart(),
        ModeGate(
          child: PageStack(
            children: <Widget>[
              if (info.disks.isNotEmpty) _diskList(),
              if (info.network.isNotEmpty) _networkList(),
              if (systemMounts.isNotEmpty) _systemMounts(systemMounts),
              for (final String section in _orderedSections())
                _section(section),
            ],
          ),
        ),
      ],
    );
  }

  List<String> _orderedSections() {
    final List<String> ordered = <String>[
      for (final String key in _kPreferredOrder)
        if (info.sectionNames.contains(key)) key,
    ];
    for (final String key in info.sectionNames) {
      if (!ordered.contains(key) && !isReactFact(key)) ordered.add(key);
    }
    return ordered;
  }

  /// A mount as "92%" used, its path and size in a muted line (paths are
  /// case-sensitive, so not the tile's label), the state as a badge.
  Widget _mountTile(EnvironmentMount mount) {
    final String path = mount.mount.isEmpty ? mount.name : mount.mount;
    final double used = mount.usedFraction;
    final HealthLevel level = used >= kMountLevels.$2
        ? HealthLevel.critical
        : used >= kMountLevels.$1
        ? HealthLevel.warning
        : HealthLevel.healthy;
    return MetricTile(
      label: reactorText(ReactorText.environmentDisk),
      value: (used * 100).toStringAsFixed(0),
      unit: '%',
      note:
          '$path · ${humanBytes(mount.usedBytes.toDouble())} '
          '${reactorText(ReactorText.environmentOfTotal, <String, Object?>{'total': humanBytes(mount.totalBytes.toDouble())})}',
      caption: levelWord(level),
      tone: levelTone(level),
      meter: used,
    );
  }

  /// System mounts, folded: path and use, never coloured.
  Widget _systemMounts(List<EnvironmentMount> mounts) {
    final String title = reactorText(ReactorText.environmentSystemMounts);
    return ArcaneDisclosure.minimal(
      showTreeLines: false,
      summary: dom.span(classes: 'ui-summary', <Widget>[
        Component.text('$title (${mounts.length})'),
      ]),
      child: DataList<EnvironmentMount>(
        label: title,
        items: mounts,
        columns: <ListColumn<EnvironmentMount>>[
          ListColumn<EnvironmentMount>.text(
            reactorText(ReactorText.environmentDisk),
            (EnvironmentMount mount) =>
                mount.mount.isEmpty ? mount.name : mount.mount,
            width: 'minmax(0, 3fr)',
          ),
          ListColumn<EnvironmentMount>(
            reactorText(ReactorText.memoryUsed),
            (EnvironmentMount mount) => Component.text(
              '${(mount.usedFraction * 100).toStringAsFixed(0)}% '
              '${reactorText(ReactorText.environmentOfTotal, <String, Object?>{'total': humanBytes(mount.totalBytes.toDouble())})}',
            ),
            numeric: true,
          ),
        ],
      ),
    );
  }

  Widget _diskChart() => _rateChart(
    reactorText(ReactorText.environmentDisk),
    (
      reactorText(ReactorText.environmentRead),
      'disk-read-rate',
      diskReadHistory,
    ),
    (
      reactorText(ReactorText.environmentWrite),
      'disk-write-rate',
      diskWriteHistory,
    ),
  );

  Widget _networkChart() => _rateChart(
    reactorText(ReactorText.environmentNetwork),
    (
      reactorText(ReactorText.environmentReceived),
      'network-receive-rate',
      networkReceiveHistory,
    ),
    (
      reactorText(ReactorText.environmentSent),
      'network-send-rate',
      networkSendHistory,
    ),
  );

  /// Two rates in one unit (the larger reading's: "KB/s"), so the axis
  /// reads round steps in that unit, on the server's chart range; histories
  /// the page keeps itself stand in, live only, while the snapshot has no
  /// sampler.
  Widget _rateChart(
    String title,
    (String, String, List<double>) first,
    (String, String, List<double>) second,
  ) {
    final (String firstName, String aId, List<double> aKept) = first;
    final (String secondName, String bId, List<double> bKept) = second;
    final SamplerSample? a = snapshot?.sampler(aId);
    final SamplerSample? b = snapshot?.sampler(bId);
    if (a == null && b == null) {
      return ChartCard(
        title: title,
        child: TimeseriesChart(
          series: <(String, List<double>)>[
            (firstName, _drawable(aKept)),
            (secondName, _drawable(bKept)),
          ],
          valueFormatter: _humanRate,
        ),
      );
    }
    return RangeChartCard(
      title: title,
      sizes: true,
      series: <RangeSeries>[
        RangeSeries(firstName, aId, a),
        RangeSeries(secondName, bId, b),
      ],
    );
  }

  Widget _diskList() {
    final String title = reactorText(ReactorText.environmentDisk);
    return DataList<EnvironmentDisk>(
      label: title,
      title: title,
      items: info.disks,
      columns: <ListColumn<EnvironmentDisk>>[
        ListColumn<EnvironmentDisk>(
          title,
          (EnvironmentDisk disk) => Component.text(
            disk.model.isEmpty ? disk.name : '${disk.name} (${disk.model})',
          ),
          width: 'minmax(0, 2fr)',
        ),
        ListColumn<EnvironmentDisk>(
          reactorText(ReactorText.environmentRead),
          (EnvironmentDisk disk) =>
              Component.text(humanBytes(disk.readBytes.toDouble())),
          numeric: true,
        ),
        ListColumn<EnvironmentDisk>(
          reactorText(ReactorText.environmentWrite),
          (EnvironmentDisk disk) =>
              Component.text(humanBytes(disk.writeBytes.toDouble())),
          numeric: true,
        ),
      ],
    );
  }

  Widget _networkList() {
    final String title = reactorText(ReactorText.environmentNetwork);
    return DataList<EnvironmentNetworkInterface>(
      label: title,
      title: title,
      items: info.network,
      columns: <ListColumn<EnvironmentNetworkInterface>>[
        ListColumn<EnvironmentNetworkInterface>(
          title,
          (EnvironmentNetworkInterface item) => Component.text(
            item.displayName.isEmpty
                ? item.name
                : '${item.displayName} (${item.name})',
          ),
          width: 'minmax(0, 2fr)',
        ),
        ListColumn<EnvironmentNetworkInterface>(
          reactorText(ReactorText.environmentReceived),
          (EnvironmentNetworkInterface item) =>
              Component.text(humanBytes(item.receivedBytes.toDouble())),
          numeric: true,
        ),
        ListColumn<EnvironmentNetworkInterface>(
          reactorText(ReactorText.environmentSent),
          (EnvironmentNetworkInterface item) =>
              Component.text(humanBytes(item.sentBytes.toDouble())),
          numeric: true,
        ),
      ],
    );
  }

  /// A raw section as rows, without React's own entries; nothing when it
  /// has none left.
  Widget _section(String section) {
    final List<MapEntry<String, Object?>> entries = <MapEntry<String, Object?>>[
      for (final MapEntry<String, Object?> entry in info.entriesOf(section))
        if (!isReactFact(entry.key)) entry,
    ];
    if (entries.isEmpty) return const Component.fragment(<Widget>[]);
    final String title = _sectionLabel(section);
    return DataList<MapEntry<String, Object?>>(
      label: title,
      title: title,
      items: entries,
      columns: <ListColumn<MapEntry<String, Object?>>>[
        ListColumn<MapEntry<String, Object?>>.text(
          reactorText(ReactorText.environmentField),
          (MapEntry<String, Object?> entry) => entry.key,
        ),
        ListColumn<MapEntry<String, Object?>>.text(
          reactorText(ReactorText.environmentValue),
          (MapEntry<String, Object?> entry) =>
              _formatEnvValue(section, entry.key, entry.value),
          numeric: true,
          width: 'minmax(0, 2fr)',
        ),
      ],
    );
  }
}

class EnvironmentScreen extends StatefulWidget {
  const EnvironmentScreen({super.key});

  @override
  State<EnvironmentScreen> createState() => _EnvironmentScreenState();
}

class _EnvironmentScreenState extends State<EnvironmentScreen> {
  static const Duration _pollInterval = Duration(seconds: 5);
  static const int _historyLength = 48;

  IEnvironmentClient? _client;
  EnvironmentInfo? _info;
  Timer? _pollTimer;
  RingBuffer _diskReadHistory = RingBuffer(_historyLength);
  RingBuffer _diskWriteHistory = RingBuffer(_historyLength);
  RingBuffer _networkReceiveHistory = RingBuffer(_historyLength);
  RingBuffer _networkSendHistory = RingBuffer(_historyLength);
  int? _previousDiskReadBytes;
  int? _previousDiskWriteBytes;
  int? _previousNetworkReceiveBytes;
  int? _previousNetworkSendBytes;
  int? _previousSampleMillis;
  Object? _error;
  bool _requesting = false;

  /// Each server gets its own readings: a new session (another server, a
  /// re-pair) starts from nothing instead of showing the last one's host.
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IEnvironmentClient? client = ServerSessionScope.of(context)
        ?.clientAs<IEnvironmentClient>();
    if (identical(client, _client)) return;
    _pollTimer?.cancel();
    _pollTimer = null;
    _client = client;
    _info = null;
    _error = null;
    _requesting = false;
    _previousDiskReadBytes = null;
    _previousDiskWriteBytes = null;
    _previousNetworkReceiveBytes = null;
    _previousNetworkSendBytes = null;
    _previousSampleMillis = null;
    _diskReadHistory = RingBuffer(_historyLength);
    _diskWriteHistory = RingBuffer(_historyLength);
    _networkReceiveHistory = RingBuffer(_historyLength);
    _networkSendHistory = RingBuffer(_historyLength);
    if (client != null) {
      _load(client);
      _pollTimer = Timer.periodic(_pollInterval, (Timer _) {
        _load(client, announce: false);
      });
    }
  }

  Future<void> _load(IEnvironmentClient client, {bool announce = true}) async {
    if (_requesting) return;
    _requesting = true;
    if (announce && _error != null) setState(() => _error = null);
    try {
      final EnvironmentInfo info = await client.environment();
      if (!mounted || !identical(client, _client)) return;
      _recordSample(info);
      setState(() {
        _info = info;
        _error = null;
      });
    } on Object catch (error) {
      if (!mounted || !identical(client, _client)) return;
      setState(() => _error = error);
    } finally {
      if (identical(client, _client)) _requesting = false;
    }
  }

  void _recordSample(EnvironmentInfo info) {
    final int now = DateTime.now().millisecondsSinceEpoch;
    final int? previous = _previousSampleMillis;
    final double seconds = previous == null
        ? 0
        : ((now - previous) / 1000).clamp(0.001, 60.0);
    _appendRate(
      _diskReadHistory,
      info.totalDiskReadBytes,
      _previousDiskReadBytes,
      seconds,
    );
    _appendRate(
      _diskWriteHistory,
      info.totalDiskWriteBytes,
      _previousDiskWriteBytes,
      seconds,
    );
    _appendRate(
      _networkReceiveHistory,
      info.totalNetworkReceivedBytes,
      _previousNetworkReceiveBytes,
      seconds,
    );
    _appendRate(
      _networkSendHistory,
      info.totalNetworkSentBytes,
      _previousNetworkSendBytes,
      seconds,
    );
    _previousDiskReadBytes = info.totalDiskReadBytes;
    _previousDiskWriteBytes = info.totalDiskWriteBytes;
    _previousNetworkReceiveBytes = info.totalNetworkReceivedBytes;
    _previousNetworkSendBytes = info.totalNetworkSentBytes;
    _previousSampleMillis = now;
  }

  /// The first reading only sets the baseline: a rate needs two.
  void _appendRate(
    RingBuffer history,
    int current,
    int? previous,
    double seconds,
  ) {
    if (previous == null || seconds <= 0) return;
    history.add(current < previous ? 0 : (current - previous) / seconds);
  }

  void _refresh() {
    final IEnvironmentClient? client = _client;
    if (client != null) _load(client);
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? server = ServerScope.of(context);
    final IEnvironmentClient? client = _client;

    if (client == null) {
      if (server?.state == ConnState.connecting) {
        return LoadingState(label: reactorText(ReactorText.environmentLoading));
      }
      return EmptyState(
        message: reactorText(ReactorText.environmentUnavailable),
        description: reactorText(ReactorText.environmentLiveRequired),
      );
    }

    final EnvironmentInfo? info = _info;
    final Object? error = _error;
    if (info == null) {
      if (error != null) {
        return ErrorState(
          title: reactorText(ReactorText.environmentNoData),
          message: localizedReactorError(error),
          onRetry: _refresh,
        );
      }
      return LoadingState(label: reactorText(ReactorText.environmentLoading));
    }
    if (info.sectionNames.isEmpty &&
        info.mounts.isEmpty &&
        info.disks.isEmpty &&
        info.network.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.environmentNoData),
        description: reactorText(ReactorText.environmentNoRuntimeData),
        icon: ArcaneIcon.serverCog(size: IconSize.sm),
      );
    }

    return PageStack(
      children: <Widget>[
        if (error != null)
          AppAlert(
            tone: AlertTone.warning,
            title: reactorText(ReactorText.environmentRefreshFailed),
            message: localizedReactorError(error),
            action: ActionRow.footer(
              dense: true,
              secondary: <RowAction>[
                RowAction(reactorText(ReactorText.commonRetry), _refresh),
              ],
            ),
          ),
        EnvironmentView(
          info: info,
          snapshot: server?.snapshot,
          diskReadHistory: _diskReadHistory.toList(),
          diskWriteHistory: _diskWriteHistory.toList(),
          networkReceiveHistory: _networkReceiveHistory.toList(),
          networkSendHistory: _networkSendHistory.toList(),
        ),
      ],
    );
  }
}
