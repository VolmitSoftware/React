/// The sampler card: its state, start settings and start or stop, the live
/// view's refresh pick, a manual refresh and Snapshot. Starting, stopping
/// and snapshots need an operator or admin pairing; a viewer sees them
/// disabled. A refused command says why in a toast.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/profiler.dart';
import '../model/snapshot.dart';
import '../model/units.dart';
import '../service/react_client.dart';
import '../state/profiler_view.dart';
import '../ui/error_text.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/density.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/role_gate.dart';
import '../ui/kit/badge.dart';
import 'form_parts.dart';

const List<int> _kIntervals = <int>[1, 2, 5, 10, 20, 50, 100];
const List<int> _kMinutes = <int>[1, 5, 10, 30, 60];

/// "1m 05s", "42.0 s".
String profilerDurationText(int ms) {
  if (ms < 60000) return '${(ms / 1000).toStringAsFixed(1)} s';
  final int seconds = ms ~/ 1000;
  final int hours = seconds ~/ 3600;
  final String mm = (seconds ~/ 60 % 60).toString().padLeft(2, '0');
  final String ss = (seconds % 60).toString().padLeft(2, '0');
  return hours > 0 ? '${hours}h ${mm}m ${ss}s' : '${seconds ~/ 60}m ${ss}s';
}

/// "Server thread" or "All threads".
String profilerThreadsText(ProfilerThreads threads) => reactorText(
  threads == ProfilerThreads.all
      ? ReactorText.profilerThreadsAll
      : ReactorText.profilerThreadsServer,
);

/// Takes a profile snapshot and says how it went.
Future<SnapshotMeta?> takeProfileSnapshot(
  ISnapshotClient client, {
  String? label,
}) async {
  try {
    final SnapshotMeta meta = await client.createSnapshot(
      SnapshotRequest.profile(label: label),
    );
    ArcaneSonner.success(
      reactorText(ReactorText.snapshotTaken),
      description: meta.label,
    );
    return meta;
  } on Object catch (error) {
    ArcaneSonner.error(
      reactorText(ReactorText.snapshotFailed),
      description: localizedReactorError(error),
    );
    return null;
  }
}

class ProfilerControls extends StatefulWidget {
  const ProfilerControls({
    required this.controller,
    this.snapshots,
    this.snapshotLabel,
    super.key,
  });

  final ProfilerViewController controller;

  /// Where Snapshot saves to; no Snapshot button when null.
  final ISnapshotClient? snapshots;

  /// The label a snapshot taken here gets; the server names it otherwise.
  final String? snapshotLabel;

  @override
  State<ProfilerControls> createState() => _ProfilerControlsState();
}

class _ProfilerControlsState extends State<ProfilerControls> {
  int? _interval;
  ProfilerThreads? _threads;
  int _minutes = 10;
  bool _snapshotting = false;

  ProfilerViewController get _controller => component.controller;

  Future<void> _start() async {
    final ProfilerStatus status = _controller.status ?? ProfilerStatus.idle;
    await _run(
      ProfilerStart(
        intervalMs: _interval ?? status.intervalMs,
        threads: _threads ?? status.threads,
        maxSeconds: _minutes * 60,
      ),
    );
  }

  Future<void> _run(ProfilerCommand command) async {
    if (await _controller.command(command)) return;
    final Object? error = _controller.commandError;
    if (error == null) return;
    ArcaneSonner.error(
      reactorText(ReactorText.profilerCommandFailed),
      description: localizedReactorError(error),
    );
  }

  Future<void> _snapshot() async {
    final ISnapshotClient? client = component.snapshots;
    if (client == null || _snapshotting) return;
    setState(() => _snapshotting = true);
    await takeProfileSnapshot(client, label: component.snapshotLabel);
    if (mounted) setState(() => _snapshotting = false);
  }

  Widget _select<T>({
    required String id,
    required ReactorText label,
    required T value,
    required List<(T, String)> options,
    required void Function(T value) onChanged,
    bool disabled = false,
  }) => ArcaneFormField(
    controlId: id,
    disabled: disabled,
    children: <Widget>[
      ArcaneFieldLabel(reactorText(label)),
      ArcaneSelect(
        id: id,
        size: controlSize(DensityScope.of(context)),
        value: '$value',
        fullWidth: true,
        options: <ArcaneSelectOption>[
          for (final (T option, String text) in options)
            ArcaneSelectOption(label: text, value: '$option'),
        ],
        onChanged: disabled
            ? null
            : (String picked) {
                for (final (T option, String _) in options) {
                  if ('$option' == picked) onChanged(option);
                }
              },
      ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ProfilerViewController controller = _controller;
    final ProfilerStatus status = controller.status ?? ProfilerStatus.idle;
    final bool allowed = RoleGate.allows(context, RoleNeed.operator);
    final bool known = controller.status != null;
    final bool running = status.running;
    final int interval = _interval ?? status.intervalMs;
    final ProfilerThreads threads = _threads ?? status.threads;
    final DateTime? stopsAt = status.stopsAt;
    return AppCard(
      title: reactorText(ReactorText.profilerSampler),
      actions: ActionRow(
        dense: true,
        label: reactorText(ReactorText.profilerSampler),
        leading: <Widget>[
          FilterSelect(
            id: 'profiler-refresh',
            label: reactorText(ReactorText.profilerRefreshEvery),
            value: controller.refresh.name,
            options: <ArcaneSelectOption>[
              for (final ProfilerRefresh refresh in ProfilerRefresh.values)
                ArcaneSelectOption(
                  label: refresh == ProfilerRefresh.paused
                      ? reactorText(ReactorText.profilerPaused)
                      : '${refresh.seconds} s',
                  value: refresh.name,
                ),
            ],
            onChanged: (String value) =>
                controller.setRefresh(ProfilerRefresh.parse(value)),
          ),
        ],
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.profilerRefreshNow),
            () => unawaited(controller.load(force: true)),
            icon: ArcaneIcon.refreshCw(),
            iconOnly: true,
            loading: controller.loading,
            id: 'profiler-refresh-now',
          ),
          if (component.snapshots != null)
            RowAction(
              reactorText(ReactorText.snapshotTake),
              allowed && status.samples > 0 && !_snapshotting
                  ? () => unawaited(_snapshot())
                  : null,
              icon: ArcaneIcon.camera(),
              loading: _snapshotting,
              id: 'profiler-snapshot',
            ),
          if (allowed && !running && status.samples > 0)
            RowAction(
              reactorText(ReactorText.profilerReset),
              controller.busy
                  ? null
                  : () => unawaited(_run(const ProfilerReset())),
              icon: ArcaneIcon.rotateCw(),
              iconOnly: true,
            ),
        ],
        primary: running
            ? RowAction(
                reactorText(ReactorText.profilerStop),
                allowed && !controller.busy
                    ? () => unawaited(_run(const ProfilerStop()))
                    : null,
                icon: ArcaneIcon.square(),
                loading: controller.busy,
                id: 'profiler-stop',
              )
            : RowAction(
                reactorText(ReactorText.profilerStart),
                allowed && known && !controller.busy
                    ? () => unawaited(_start())
                    : null,
                icon: ArcaneIcon.play(),
                loading: controller.busy,
                id: 'profiler-start',
              ),
      ),
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            MetricTile(
              label: reactorText(ReactorText.profilerState),
              value: reactorText(
                running ? ReactorText.profilerRunning : ReactorText.profilerOff,
              ),
              tone: running ? BadgeTone.ok : BadgeTone.neutral,
            ),
            MetricTile(
              label: reactorText(ReactorText.profilerSamples),
              value: groupThousands(status.samples),
            ),
            MetricTile(
              label: reactorText(ReactorText.profilerDuration),
              value: profilerDurationText(status.durationMs),
            ),
            if (running && stopsAt != null)
              MetricTile(
                label: reactorText(ReactorText.profilerStopsIn),
                value: profilerDurationText(
                  stopsAt
                      .difference(DateTime.now())
                      .inMilliseconds
                      .clamp(0, profilerMaxSeconds * 1000),
                ),
              ),
          ],
        ),
        if (!running)
          dom.div(classes: 'reactor-field-grid', <Widget>[
            _select<int>(
              id: 'profiler-interval',
              label: ReactorText.profilerInterval,
              value: _kIntervals.contains(interval)
                  ? interval
                  : profilerDefaultIntervalMs,
              options: <(int, String)>[
                for (final int ms in _kIntervals) (ms, '$ms ms'),
              ],
              onChanged: (int value) => setState(() => _interval = value),
              disabled: !allowed,
            ),
            _select<ProfilerThreads>(
              id: 'profiler-threads',
              label: ReactorText.profilerThreads,
              value: threads,
              options: <(ProfilerThreads, String)>[
                for (final ProfilerThreads each in ProfilerThreads.values)
                  (each, profilerThreadsText(each)),
              ],
              onChanged: (ProfilerThreads value) =>
                  setState(() => _threads = value),
              disabled: !allowed,
            ),
            _select<int>(
              id: 'profiler-max',
              label: ReactorText.profilerStopsAfter,
              value: _minutes,
              options: <(int, String)>[
                for (final int minutes in _kMinutes) (minutes, '$minutes min'),
              ],
              onChanged: (int value) => setState(() => _minutes = value),
              disabled: !allowed,
            ),
          ]),
      ],
    );
  }
}
