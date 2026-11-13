/// Plugins > API packs: the metric packs installed on the server, one row
/// each, with their load errors. Admins write a pack in a sheet, validate
/// and install it; Delete asks first. Advanced lists every pack metric with
/// its availability.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/plugin_api_pack.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/plugin_api_packs_controller.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/role_gate.dart';
import '../ui/kit/sheet.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import 'actions.dart' show serverNameOf;

const String _kNewPackTemplate = '''schema = "react.plugin-api/v1"
id = "community.example"
version = "1.0.0"
name = "Community Example"
authors = ["Your Name"]
enabled = true
trusted = false
targetPlugin = "ExamplePlugin"
targetVersions = ["*"]

[[metrics]]
id = "example-value"
displayName = "Example Value"
kind = "gauge"
unit = " value"
icon = "CLOCK"
sampleEveryMs = 1000
staleAfterMs = 15000

[metrics.source]
type = "integration"
pluginId = "example"
key = "example.metric"
foliaSafe = true
''';

/// A pack's state as a badge: the plugin's word in sentence case.
StatusBadge packStateBadge(String state) {
  final String words = state.toLowerCase().replaceAll('_', ' ');
  final String label = words.isEmpty
      ? state
      : '${words[0].toUpperCase()}${words.substring(1)}';
  final BadgeTone tone = switch (state) {
    'HEALTHY' => BadgeTone.ok,
    'DEGRADED' || 'LOADING' || 'TARGET_MISSING' => BadgeTone.warning,
    'QUARANTINED' || 'INVALID' || 'INCOMPATIBLE' => BadgeTone.error,
    _ => BadgeTone.neutral,
  };
  return StatusBadge(label, tone: tone);
}

/// A row's name over its caption. A caption that is one id (a sampler id)
/// stays on one line with an ellipsis instead of breaking at its hyphens.
Widget _nameCell(String name, String caption, {bool oneLine = false}) =>
    dom.div(classes: 'reactor-row-name', <Widget>[
      dom.div(classes: 'reactor-row-title', <Widget>[
        dom.span(<Widget>[Component.text(name)]),
      ]),
      dom.p(classes: 'ui-caption', <Widget>[
        oneLine ? oneLineText(caption) : Component.text(caption),
      ]),
    ]);

/// The packs, their folder and load errors. [onNew], [onEdit] and
/// [onDelete] are null for a role or connection that may not change packs.
class PluginApiCatalogView extends StatelessWidget {
  const PluginApiCatalogView({
    required this.catalog,
    this.loading = false,
    this.onRefresh,
    this.onNew,
    this.onEdit,
    this.onDelete,
    super.key,
  });

  final PluginApiCatalog catalog;
  final bool loading;
  final void Function()? onRefresh;
  final void Function()? onNew;
  final void Function(PluginApiPack pack)? onEdit;
  final void Function(PluginApiPack pack)? onDelete;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final void Function(PluginApiPack)? edit = onEdit;
    final void Function(PluginApiPack)? delete = onDelete;
    return AppCard(
      title: reactorText(ReactorText.pluginApiCatalog),
      tip: reactorText(ReactorText.pluginApiSubtitle),
      actions: ActionRow(
        dense: true,
        label: reactorText(ReactorText.pluginApiCatalog),
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.pluginApiRefresh),
            loading ? null : onRefresh,
            icon: ArcaneIcon.refreshCw(),
          ),
        ],
        primary: RowAction(reactorText(ReactorText.pluginApiNew), onNew),
      ),
      children: <Widget>[
        dom.p(classes: 'ui-caption', <Widget>[
          Component.text('${reactorText(ReactorText.pluginApiFolder)}: '),
          dom.code(breakablePath(serverFolder(catalog.folder))),
        ]),
        DataList<PluginApiPack>(
          label: reactorText(ReactorText.pluginApiCatalog),
          items: catalog.packs,
          emptyMessage: reactorText(ReactorText.pluginApiNone),
          columns: <ListColumn<PluginApiPack>>[
            ListColumn<PluginApiPack>(
              reactorText(ReactorText.pluginApiColumnPack),
              (PluginApiPack pack) => _nameCell(
                pack.name,
                <String>[
                  pack.id,
                  pack.version,
                  if (pack.trusted) reactorText(ReactorText.pluginApiTrusted),
                ].join(' · '),
              ),
              width: 'minmax(0, 1fr)',
            ),
            ListColumn<PluginApiPack>(
              reactorText(ReactorText.pluginApiTarget),
              (PluginApiPack pack) => Component.text(
                pack.targetVersion.isEmpty
                    ? pack.targetPlugin
                    : '${pack.targetPlugin} ${pack.targetVersion}',
              ),
              width: 'minmax(0, 12rem)',
            ),
            ListColumn<PluginApiPack>(
              reactorText(ReactorText.commonState),
              (PluginApiPack pack) => packStateBadge(pack.state),
              width: '8rem',
            ),
            ListColumn<PluginApiPack>(
              reactorText(ReactorText.pluginApiMetrics),
              (PluginApiPack pack) => Component.text(
                '${pack.metrics.where((PluginApiMetric m) => m.available).length}/${pack.metrics.length}',
              ),
              numeric: true,
              width: '5rem',
            ),
          ],
          // One row pattern: the main action as a button, the rest (the
          // destructive one asks first) in the row's "..." menu.
          actions: (PluginApiPack pack) => <RowAction>[
            RowAction(
              reactorText(ReactorText.pluginApiEdit),
              edit == null ? null : () => edit(pack),
              attributes: <String, String>{
                'aria-label':
                    '${reactorText(ReactorText.pluginApiEdit)} ${pack.name}',
              },
            ),
          ],
          more: (PluginApiPack pack) => <AppMenuEntry>[
            AppMenuEntry(
              reactorText(ReactorText.pluginApiDelete),
              delete == null ? null : () => delete(pack),
              icon: ArcaneIcon.trash2(),
              destructive: true,
            ),
          ],
        ),
      ],
    );
  }
}

/// Every metric of every pack and whether it reports (Advanced).
class PluginApiMetricsList extends StatelessWidget {
  const PluginApiMetricsList({required this.packs, super.key});

  final List<PluginApiPack> packs;

  static StatusBadge _state(PluginApiMetric metric) => metric.available
      ? StatusBadge(
          reactorText(ReactorText.pluginApiAvailable),
          tone: BadgeTone.ok,
        )
      : StatusBadge(
          reactorText(ReactorText.pluginApiUnavailable),
          tone: metric.quarantined ? BadgeTone.error : BadgeTone.warning,
        );

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final List<(PluginApiPack, PluginApiMetric)> rows =
        <(PluginApiPack, PluginApiMetric)>[
          for (final PluginApiPack pack in packs)
            for (final PluginApiMetric metric in pack.metrics) (pack, metric),
        ];
    return DataList<(PluginApiPack, PluginApiMetric)>(
      label: reactorText(ReactorText.pluginApiMetrics),
      title: reactorText(ReactorText.pluginApiMetrics),
      items: rows,
      emptyMessage: reactorText(ReactorText.pluginApiNone),
      columns: <ListColumn<(PluginApiPack, PluginApiMetric)>>[
        ListColumn<(PluginApiPack, PluginApiMetric)>(
          reactorText(ReactorText.pluginApiColumnMetric),
          ((PluginApiPack, PluginApiMetric) row) =>
              _nameCell(row.$2.displayName, row.$2.samplerId, oneLine: true),
          width: 'minmax(0, 1fr)',
        ),
        ListColumn<(PluginApiPack, PluginApiMetric)>(
          reactorText(ReactorText.pluginApiColumnPack),
          ((PluginApiPack, PluginApiMetric) row) => Component.text(row.$1.name),
          width: 'minmax(0, 12rem)',
        ),
        ListColumn<(PluginApiPack, PluginApiMetric)>(
          reactorText(ReactorText.commonState),
          ((PluginApiPack, PluginApiMetric) row) => _state(row.$2),
          width: '8rem',
        ),
        ListColumn<(PluginApiPack, PluginApiMetric)>(
          reactorText(ReactorText.pluginApiColumnSource),
          // The reason it is not available is the State column's.
          ((PluginApiPack, PluginApiMetric) row) =>
              Component.text(row.$2.available ? row.$2.sourceType : '--'),
          width: 'minmax(0, 10rem)',
        ),
      ],
    );
  }
}

/// The pack being written: its TOML, the last check, Validate and Install.
class PluginApiEditorSheet extends StatelessWidget {
  const PluginApiEditorSheet({
    required this.draft,
    required this.title,
    required this.onChanged,
    required this.onClose,
    this.validation,
    this.saving = false,
    this.editing = false,
    this.onValidate,
    this.onInstall,
    super.key,
  });

  /// The TOML; null closes the sheet.
  final String? draft;
  final String title;
  final PluginApiValidationResult? validation;
  final bool saving;

  /// An installed pack is edited (Save), not a new one (Install).
  final bool editing;
  final void Function(String text) onChanged;
  final void Function() onClose;
  final void Function()? onValidate;
  final void Function()? onInstall;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final PluginApiValidationResult? result = validation;
    return Sheet(
      open: draft != null,
      title: title,
      onClose: onClose,
      footer: DialogFooter(
        secondary: <RowAction>[
          RowAction(
            reactorText(ReactorText.pluginApiValidate),
            saving ? null : onValidate,
          ),
        ],
        cancel: RowAction(reactorText(ReactorText.commonCancel), onClose),
        primary: RowAction(
          reactorText(
            editing
                ? ReactorText.configEditorSave
                : ReactorText.pluginApiInstall,
          ),
          saving ? null : onInstall,
          loading: saving,
        ),
      ),
      child: dom.div(classes: 'ui-stack', <Widget>[
        // Code: monospace, as tall as the sheet allows, no grip.
        TextArea(
          label: reactorText(ReactorText.pluginApiContent),
          value: draft ?? '',
          rows: 18,
          resize: TextAreaResize.none,
          minHeight: 'max(18rem, calc(100dvh - 12.5rem))',
          styles: const ArcaneStyleData(fontFamily: FontFamily.mono),
          fullWidth: true,
          onChanged: onChanged,
        ),
        if (result != null)
          AppAlert(
            tone: result.valid ? AlertTone.ok : AlertTone.error,
            title: reactorText(
              result.valid
                  ? ReactorText.pluginApiValid
                  : ReactorText.pluginApiInvalid,
            ),
            message: result.valid
                ? '${result.id} · ${result.metricCount} ${reactorText(ReactorText.pluginApiMetrics).toLowerCase()}'
                : result.message,
            live: true,
          ),
      ]),
    );
  }
}

/// The packs of the server the page belongs to, from its session; the
/// draft being written lives here until Install.
class PluginApiPacksScreen extends StatefulWidget {
  const PluginApiPacksScreen({super.key});

  @override
  State<PluginApiPacksScreen> createState() => _PluginApiPacksScreenState();
}

class _PluginApiPacksScreenState extends State<PluginApiPacksScreen> {
  PluginApiValidationResult? _validation;
  String? _draft;
  String? _editingName;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _draft = null;
    _editingName = null;
    _validation = null;
  }

  bool get _canMutate =>
      RoleGate.allows(context, RoleNeed.admin) &&
      ServerScope.of(context)?.state == ConnState.live;

  void _open(String content, String? name) => setState(() {
    _draft = content;
    _editingName = name;
    _validation = null;
  });

  void _close() => setState(() {
    _draft = null;
    _editingName = null;
    _validation = null;
  });

  void _failed(ReactorText title, Object error) {
    if (!mounted) return;
    ArcaneSonner.error(
      reactorText(title),
      description: localizedReactorError(error),
    );
  }

  Future<PluginApiValidationResult?> _validate(
    PluginApiPacksController controller,
  ) async {
    final String? draft = _draft;
    if (draft == null || controller.saving) return null;
    try {
      final PluginApiValidationResult result = await controller.validate(draft);
      if (mounted) setState(() => _validation = result);
      return result;
    } on Object catch (error) {
      _failed(ReactorText.pluginApiInvalid, error);
      return null;
    }
  }

  Future<void> _install(PluginApiPacksController controller) async {
    if (!_canMutate) return;
    final PluginApiValidationResult? result = await _validate(controller);
    final String? draft = _draft;
    if (result == null || !result.valid || draft == null || !mounted) return;
    try {
      await controller.install(result.id, draft);
      if (!mounted) return;
      _close();
      ArcaneSonner.success(reactorText(ReactorText.pluginApiInstalled));
    } on Object catch (error) {
      _failed(ReactorText.pluginApiLoadFailed, error);
    }
  }

  Future<void> _delete(
    PluginApiPacksController controller,
    PluginApiPack pack,
  ) async {
    if (!_canMutate) return;
    final bool yes = await askToConfirm(
      title: reactorText(
        ReactorText.pluginApiConfirmDeleteTitle,
        <String, Object?>{'pack': pack.name, 'server': serverNameOf(context)},
      ),
      confirmLabel: reactorText(ReactorText.pluginApiDelete),
      destructive: true,
    );
    if (!yes || !mounted || controller.disposed) return;
    try {
      await controller.remove(pack.id);
      if (mounted) {
        ArcaneSonner.success(reactorText(ReactorText.pluginApiRemoved));
      }
    } on Object catch (error) {
      _failed(ReactorText.pluginApiLoadFailed, error);
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IPluginApiPackClient>()) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.pluginApiLoading))
          : EmptyState(
              message: reactorText(ReactorText.pluginApiLoadFailed),
              description: reactorText(ReactorText.pluginApiLiveRequired),
            );
    }
    final PluginApiPacksController controller = session.packs;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, PluginApiPacksController controller) {
    final PluginApiCatalog? catalog = controller.catalog;
    final Object? error = controller.error;
    if (catalog == null) {
      return error == null
          ? LoadingState(label: reactorText(ReactorText.pluginApiLoading))
          : ErrorState(
              title: reactorText(ReactorText.pluginApiLoadFailed),
              message: localizedReactorError(error),
              onRetry: controller.load,
            );
    }
    final bool canMutate = _canMutate && !controller.saving;
    return PageStack(
      children: <Widget>[
        if (error != null)
          ErrorState(
            title: reactorText(ReactorText.pluginApiLoadFailed),
            message: localizedReactorError(error),
            onRetry: controller.load,
          ),
        // A file that does not load: the stock destructive alert, on the
        // page above the card (never a box inside it).
        for (final PluginApiValidationError broken in catalog.errors)
          AppAlert(
            tone: AlertTone.error,
            title: broken.fileName,
            message: broken.message,
          ),
        PluginApiCatalogView(
          catalog: catalog,
          loading: controller.loading,
          onRefresh: controller.load,
          onNew: canMutate ? () => _open(_kNewPackTemplate, null) : null,
          onEdit: canMutate
              ? (PluginApiPack pack) => _open(pack.rawContent, pack.name)
              : null,
          onDelete: canMutate
              ? (PluginApiPack pack) => _delete(controller, pack)
              : null,
        ),
        ModeGate(child: PluginApiMetricsList(packs: catalog.packs)),
        PluginApiEditorSheet(
          draft: _draft,
          title: _editingName ?? reactorText(ReactorText.pluginApiNew),
          editing: _editingName != null,
          validation: _validation,
          saving: controller.saving,
          onChanged: (String text) => setState(() {
            _draft = text;
            _validation = null;
          }),
          onClose: _close,
          onValidate: () => _validate(controller),
          onInstall: _canMutate ? () => _install(controller) : null,
        ),
      ],
    );
  }
}

/// [path] from the server's own folder on ("plugins/React/plugin-apis"),
/// so the page shows where the packs live without the host's directories.
String serverFolder(String path) {
  final String slashed = path.replaceAll(r'\', '/');
  final int at = slashed.lastIndexOf('/plugins/');
  return at < 0 ? slashed : slashed.substring(at + 1);
}

/// [path] with a line-break opportunity after every "/": a long folder
/// wraps on a phone instead of widening the page, and copies unchanged.
List<Widget> breakablePath(String path) {
  final List<String> parts = path.split('/');
  return <Widget>[
    for (int i = 0; i < parts.length; i++) ...<Widget>[
      Component.text(i < parts.length - 1 ? '${parts[i]}/' : parts[i]),
      if (i < parts.length - 1) Component.element(tag: 'wbr'),
    ],
  ];
}
