/// Profiler > Snapshots: every frozen profile and heatmap on the server,
/// newest first, with export and (for operators) delete.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/snapshot.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../state/snapshot_files.dart';
import '../ui/error_text.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/role_gate.dart';
import '../ui/kit/states.dart';
import 'form_parts.dart';

/// The address of snapshot [id] on server [serverId].
String snapshotPagePath(String serverId, String id) =>
    serverPath(serverId, 'profiler/snapshot/${Uri.encodeComponent(id)}');

/// "2026-11-20 14:05:11" in local time.
String snapshotTimeText(DateTime at) {
  final DateTime local = at.toLocal();
  String two(int value) => value.toString().padLeft(2, '0');
  return '${local.year}-${two(local.month)}-${two(local.day)} '
      '${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
}

/// "Profile" or "Heatmap".
String snapshotKindText(SnapshotKind? kind) => switch (kind) {
  SnapshotKind.profile => reactorText(ReactorText.snapshotKindProfile),
  SnapshotKind.heatmap => reactorText(ReactorText.snapshotKindHeatmap),
  null => '',
};

/// Downloads [meta]'s export and says when it could not.
Future<void> exportSnapshot(ISnapshotClient client, SnapshotMeta meta) async {
  try {
    final SnapshotExport export = await client.exportSnapshot(meta);
    if (!await saveSnapshotExport(export)) {
      throw const ReactSaveFailed();
    }
  } on Object catch (error) {
    ArcaneSonner.error(
      reactorText(ReactorText.snapshotExportFailed),
      description: error is ReactSaveFailed
          ? null
          : localizedReactorError(error),
    );
  }
}

/// Asks, then deletes [meta]; true when it is gone.
Future<bool> deleteSnapshot(ISnapshotClient client, SnapshotMeta meta) async {
  final bool confirmed = await askToConfirm(
    title: reactorText(ReactorText.snapshotDeleteTitle, <String, Object?>{
      'label': meta.label,
    }),
    confirmLabel: reactorText(ReactorText.snapshotDelete),
    destructive: true,
  );
  if (!confirmed) return false;
  try {
    await client.deleteSnapshot(meta.id);
    ArcaneSonner.success(
      reactorText(ReactorText.snapshotDeleted),
      description: meta.label,
    );
    return true;
  } on Object catch (error) {
    ArcaneSonner.error(
      reactorText(ReactorText.snapshotDeleteFailed),
      description: localizedReactorError(error),
    );
    return false;
  }
}

/// The browser could not save the file.
class ReactSaveFailed implements Exception {
  const ReactSaveFailed();
}

class SnapshotsScreen extends StatefulWidget {
  const SnapshotsScreen({super.key});

  @override
  State<SnapshotsScreen> createState() => _SnapshotsScreenState();
}

class _SnapshotsScreenState extends State<SnapshotsScreen> {
  ISnapshotClient? _client;
  List<SnapshotMeta>? _snapshots;
  Object? _error;
  bool _loading = false;
  String _kind = '';
  int _generation = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ISnapshotClient? client = ServerSessionScope.of(context)
        ?.clientAs<ISnapshotClient>();
    if (identical(client, _client)) return;
    _client = client;
    _snapshots = null;
    _error = null;
    unawaited(_load());
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
    setState(() => _loading = true);
    try {
      final List<SnapshotMeta> loaded = await client.snapshots(
        kind: SnapshotKind.parse(_kind),
      );
      if (!mounted || generation != _generation) return;
      setState(() {
        _snapshots = loaded;
        _error = null;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _delete(SnapshotMeta meta) async {
    final ISnapshotClient? client = _client;
    if (client == null) return;
    if (await deleteSnapshot(client, meta)) await _load();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ISnapshotClient? client = _client;
    if (client == null) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.commonLoading))
          : EmptyState(
              message: reactorText(ReactorText.profilerUnsupported),
              icon: ArcaneIcon.archive(),
            );
    }
    final ServerSession? session = ServerSessionScope.of(context);
    final bool allowed = RoleGate.allows(context, RoleNeed.operator);
    final List<SnapshotMeta>? snapshots = _snapshots;
    final Object? error = _error;
    return AppCard(
      title: reactorText(ReactorText.profilerSnapshots),
      actions: ActionRow(
        dense: true,
        label: reactorText(ReactorText.profilerSnapshots),
        leading: <Widget>[
          FilterSelect(
            id: 'snapshot-kind',
            label: reactorText(ReactorText.snapshotKind),
            value: _kind,
            options: <ArcaneSelectOption>[
              ArcaneSelectOption(
                label: reactorText(ReactorText.snapshotKindAll),
                value: '',
              ),
              for (final SnapshotKind kind in SnapshotKind.values)
                ArcaneSelectOption(
                  label: snapshotKindText(kind),
                  value: kind.wire,
                ),
            ],
            onChanged: (String value) {
              setState(() => _kind = value);
              unawaited(_load());
            },
          ),
        ],
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.profilerRefreshNow),
            () => unawaited(_load()),
            icon: ArcaneIcon.refreshCw(),
            iconOnly: true,
            loading: _loading,
          ),
        ],
      ),
      children: <Widget>[
        if (snapshots == null && error != null)
          ErrorState(
            title: reactorText(ReactorText.snapshotsLoadFailed),
            message: localizedReactorError(error),
            onRetry: () => unawaited(_load()),
          )
        else if (snapshots == null)
          LoadingState(label: reactorText(ReactorText.commonLoading))
        else
          DataList<SnapshotMeta>(
            label: reactorText(ReactorText.profilerSnapshots),
            items: snapshots,
            emptyMessage: reactorText(ReactorText.snapshotsNone),
            href: (SnapshotMeta meta) =>
                snapshotPagePath(session?.serverId ?? '', meta.id),
            columns: <ListColumn<SnapshotMeta>>[
              ListColumn<SnapshotMeta>.text(
                reactorText(ReactorText.snapshotLabel),
                (SnapshotMeta meta) => meta.label,
                width: 'minmax(0, 2fr)',
              ),
              ListColumn<SnapshotMeta>.text(
                reactorText(ReactorText.snapshotKind),
                (SnapshotMeta meta) => snapshotKindText(meta.kind),
                width: '6rem',
              ),
              ListColumn<SnapshotMeta>.text(
                reactorText(ReactorText.snapshotTakenAt),
                (SnapshotMeta meta) => snapshotTimeText(meta.createdAt),
                width: '10rem',
              ),
              ListColumn<SnapshotMeta>.text(
                reactorText(ReactorText.snapshotSummary),
                (SnapshotMeta meta) => meta.summary,
                width: 'minmax(0, 2fr)',
              ),
              ListColumn<SnapshotMeta>.text(
                reactorText(ReactorText.snapshotSize),
                (SnapshotMeta meta) => snapshotSizeText(meta.bytes),
                numeric: true,
                width: '5rem',
              ),
            ],
            actions: (SnapshotMeta meta) => <RowAction>[
              RowAction(
                reactorText(ReactorText.snapshotExport),
                () => unawaited(exportSnapshot(client, meta)),
                icon: ArcaneIcon.download(),
                iconOnly: true,
              ),
              RowAction(
                reactorText(ReactorText.snapshotDelete),
                allowed ? () => unawaited(_delete(meta)) : null,
                icon: ArcaneIcon.trash2(),
                iconOnly: true,
                destructive: true,
              ),
            ],
          ),
      ],
    );
  }
}
