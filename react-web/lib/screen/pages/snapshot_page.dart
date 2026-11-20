/// One snapshot, read only: a profile in the profiler's views or a heatmap
/// in the map's plane, with export, PNG for a heatmap and delete.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../../app/paths.dart';
import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/heatmap.dart';
import '../../model/profiler.dart';
import '../../model/snapshot.dart';
import '../../service/react_client.dart';
import '../../state/connection_manager.dart';
import '../../state/server_scope.dart';
import '../../state/server_session.dart';
import '../../state/server_session_scope.dart';
import '../../state/snapshot_files.dart';
import '../../ui/error_text.dart';
import '../../ui/kit/action_row.dart';
import '../../ui/kit/card.dart';
import '../../ui/kit/metric_tile.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/role_gate.dart';
import '../../ui/kit/states.dart';
import '../../widget/heatmap_grid_view.dart';
import '../profile_view.dart';
import '../snapshots.dart';

class SnapshotPage extends StatefulWidget {
  const SnapshotPage({required this.snapshotId, super.key});

  final String snapshotId;

  @override
  State<SnapshotPage> createState() => _SnapshotPageState();
}

class _SnapshotPageState extends State<SnapshotPage> {
  ISnapshotClient? _client;
  SnapshotDetail? _detail;
  Object? _error;
  int _generation = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ISnapshotClient? client = ServerSessionScope.of(context)
        ?.clientAs<ISnapshotClient>();
    if (identical(client, _client)) return;
    _client = client;
    unawaited(_load());
  }

  @override
  void didUpdateWidget(SnapshotPage oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (oldComponent.snapshotId != component.snapshotId) unawaited(_load());
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load() async {
    final ISnapshotClient? client = _client;
    if (client == null) return;
    final int generation = ++_generation;
    setState(() {
      _detail = null;
      _error = null;
    });
    try {
      final SnapshotDetail detail = await client.snapshot(component.snapshotId);
      if (!mounted || generation != _generation) return;
      setState(() => _detail = detail);
    } on Object catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() => _error = error);
    }
  }

  Future<void> _savePng(SnapshotDetail detail, HeatmapGrid grid) async {
    if (!await saveHeatmapPng(grid, heatmapPngName(detail.meta))) {
      ArcaneSonner.error(reactorText(ReactorText.snapshotPngFailed));
    }
  }

  Future<void> _delete(SnapshotMeta meta) async {
    final ISnapshotClient? client = _client;
    final ServerSession? session = ServerSessionScope.of(context);
    if (client == null || session == null) return;
    if (!await deleteSnapshot(client, meta) || !mounted) return;
    Router.of(context)
        .push('${serverPath(session.serverId, 'profiler')}?tab=snapshots');
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final SnapshotDetail? detail = _detail;
    final ISnapshotClient? client = _client;
    final bool admin = RoleGate.allows(context, RoleNeed.admin);
    final HeatmapGrid? grid = detail?.heatmap;
    return AppPage(
      title: detail?.meta.label ?? reactorText(ReactorText.profilerSnapshots),
      actions: detail == null || client == null
          ? null
          : ActionRow(
              label: detail.meta.label,
              secondary: <RowAction>[
                if (grid != null)
                  RowAction(
                    reactorText(ReactorText.snapshotSavePng),
                    () => unawaited(_savePng(detail, grid)),
                    icon: ArcaneIcon.image(),
                    id: 'snapshot-png',
                  ),
                RowAction(
                  reactorText(ReactorText.snapshotDelete),
                  admin ? () => unawaited(_delete(detail.meta)) : null,
                  icon: ArcaneIcon.trash2(),
                  destructive: true,
                ),
              ],
              primary: RowAction(
                reactorText(ReactorText.snapshotExport),
                () => unawaited(exportSnapshot(client, detail.meta)),
                icon: ArcaneIcon.download(),
                id: 'snapshot-export',
              ),
            ),
      children: <Widget>[_body(context, detail)],
    );
  }

  Widget _body(BuildContext context, SnapshotDetail? detail) {
    if (_client == null) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.commonLoading))
          : EmptyState(
              message: reactorText(ReactorText.profilerUnsupported),
              icon: ArcaneIcon.archive(),
            );
    }
    final Object? error = _error;
    if (error != null) {
      return ErrorState(
        title: reactorText(ReactorText.snapshotsLoadFailed),
        message: localizedReactorError(error),
        onRetry: () => unawaited(_load()),
      );
    }
    if (detail == null) {
      return LoadingState(label: reactorText(ReactorText.commonLoading));
    }
    final SnapshotMeta meta = detail.meta;
    final ServerSession? session = ServerSessionScope.of(context);
    final ProfileData? profile = detail.profile;
    final HeatmapGrid? grid = detail.heatmap;
    return PageStack(
      children: <Widget>[
        MetricGrid(
          children: <Widget>[
            MetricTile(
              label: reactorText(ReactorText.snapshotKind),
              value: snapshotKindText(meta.kind),
              note: meta.summary.isEmpty ? null : meta.summary,
            ),
            MetricTile(
              label: reactorText(ReactorText.snapshotTakenAt),
              value: snapshotTimeText(meta.createdAt),
            ),
            MetricTile(
              label: reactorText(ReactorText.snapshotSize),
              value: snapshotSizeText(meta.bytes),
            ),
          ],
        ),
        if (profile != null)
          ProfileView(
            data: profile,
            serverId: session?.serverId ?? '',
            modded: session?.identity.modded ?? false,
          )
        else if (grid != null)
          AppCard(
            title: grid.label,
            children: <Widget>[
              HeatmapGridView(
                grid: grid,
                unit: ServerScope.of(context)?.snapshot?.byId[grid.id]?.suffix,
              ),
            ],
          )
        else
          EmptyState(message: reactorText(ReactorText.snapshotsNone)),
      ],
    );
  }
}
