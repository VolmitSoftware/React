library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/plugin_api_pack.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/plugin_api_pack_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/role_badge.dart';

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

class PluginApiPacksScreen extends StatefulWidget {
  const PluginApiPacksScreen({super.key});

  @override
  State<PluginApiPacksScreen> createState() => _PluginApiPacksScreenState();
}

class _PluginApiPacksScreenState extends State<PluginApiPacksScreen> {
  IPluginApiPackClient? _client;
  PluginApiCatalog? _catalog;
  PluginApiValidationResult? _validation;
  Object? _error;
  String? _draft;
  String? _editingId;
  String? _pendingDeleteId;
  bool _loading = false;
  bool _saving = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IPluginApiPackClient? client = PluginApiPackScope.of(context)?.client;
    if (client != null && client != _client) {
      _client = client;
      unawaited(_load());
    }
  }

  Future<void> _load() async {
    final IPluginApiPackClient? client = _client;
    if (client == null || _loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final PluginApiCatalog catalog = await client.pluginApiPacks();
      if (!mounted) return;
      setState(() => _catalog = catalog);
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _validate() async {
    final IPluginApiPackClient? client = _client;
    final String? draft = _draft;
    if (client == null || draft == null || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final PluginApiValidationResult result = await client
          .validatePluginApiPack(draft);
      if (!mounted) return;
      setState(() => _validation = result);
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _install() async {
    if (!_canMutate) return;
    await _validate();
    final PluginApiValidationResult? validation = _validation;
    final String? draft = _draft;
    final IPluginApiPackClient? client = _client;
    if (validation == null ||
        !validation.valid ||
        draft == null ||
        client == null) {
      return;
    }
    setState(() => _saving = true);
    try {
      await client.installPluginApiPack(validation.id, draft);
      if (!mounted) return;
      setState(() {
        _draft = null;
        _editingId = null;
        _validation = null;
      });
      ArcaneSonner.success(reactorText(ReactorText.pluginApiInstalled));
      await Future<void>.delayed(const Duration(milliseconds: 500));
      await _load();
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _remove(String id) async {
    if (!_canMutate) return;
    if (_pendingDeleteId != id) {
      setState(() => _pendingDeleteId = id);
      return;
    }
    final IPluginApiPackClient? client = _client;
    if (client == null) return;
    setState(() => _saving = true);
    try {
      final PluginApiCatalog catalog = await client.removePluginApiPack(id);
      if (!mounted) return;
      setState(() {
        _catalog = catalog;
        _pendingDeleteId = null;
        if (_editingId == id) {
          _editingId = null;
          _draft = null;
        }
      });
      ArcaneSonner.success(reactorText(ReactorText.pluginApiRemoved));
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  bool get _canMutate {
    final RoleInfo? role = RoleScope.of(context)?.role;
    return role != null &&
        role.isAdmin &&
        ServerScope.of(context)?.state == ConnState.live;
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool adminGated = role != null && !role.isAdmin;
    final bool connectionReadOnly =
        ServerScope.of(context)?.state != ConnState.live;
    final PluginApiCatalog? catalog = _catalog;
    final Object? error = _error;
    return ReactorPage(
      title: reactorText(ReactorText.pluginApiTitle),
      subtitle: reactorText(ReactorText.pluginApiSubtitle),
      leading: RoleBadge(role: role),
      actions: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{'display': 'flex', 'gap': '0.5rem'},
        ),
        <Widget>[
          Button.secondary(
            label: reactorText(ReactorText.pluginApiRefresh),
            size: ButtonSize.small,
            disabled: _loading,
            onPressed: _loading ? null : _load,
          ),
          Button.primary(
            label: reactorText(ReactorText.pluginApiNew),
            size: ButtonSize.small,
            disabled: adminGated || connectionReadOnly,
            onPressed: adminGated || connectionReadOnly
                ? null
                : () => setState(() {
                    _draft = _kNewPackTemplate;
                    _editingId = null;
                    _validation = null;
                  }),
          ),
        ],
      ),
      children: <Widget>[
        if (adminGated)
          ReactorNotice(
            title: reactorText(ReactorText.commonRequiresAdminRole),
            message: reactorText(ReactorText.pluginApiAdminRequired),
            status: ReactorStatus.warning,
          )
        else if (connectionReadOnly)
          ReactorNotice(
            title: reactorText(ReactorText.commonUnavailable),
            message: reactorText(ReactorText.pluginApiLiveRequired),
            status: ReactorStatus.warning,
          ),
        if (error != null)
          ReactorNotice(
            title: reactorText(ReactorText.pluginApiLoadFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.critical,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: _load,
            ),
          ),
        if (_draft != null) _editor(),
        if (_loading && catalog == null)
          ReactorLoadingState(label: reactorText(ReactorText.pluginApiLoading))
        else if (catalog != null)
          ..._catalogSections(catalog),
      ],
    );
  }

  Widget _editor() {
    final PluginApiValidationResult? validation = _validation;
    return SectionPanel(
      label: reactorText(ReactorText.pluginApiEditor),
      description: reactorText(ReactorText.pluginApiEditorDescription),
      trailing: Button.ghost(
        label: reactorText(ReactorText.pluginApiCancel),
        size: ButtonSize.small,
        onPressed: () => setState(() {
          _draft = null;
          _editingId = null;
          _validation = null;
        }),
      ),
      children: <Widget>[
        TextArea(
          label: reactorText(ReactorText.pluginApiContent),
          value: _draft,
          rows: 22,
          minHeight: '28rem',
          onChange: (String value) => setState(() {
            _draft = value;
            _validation = null;
          }),
        ),
        if (validation != null)
          ReactorNotice(
            title: reactorText(
              validation.valid
                  ? ReactorText.pluginApiValid
                  : ReactorText.pluginApiInvalid,
            ),
            message: validation.valid
                ? '${validation.id} · ${validation.metricCount} ${reactorText(ReactorText.pluginApiMetrics).toLowerCase()}'
                : validation.message,
            status: validation.valid
                ? ReactorStatus.healthy
                : ReactorStatus.critical,
          ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{'display': 'flex', 'gap': '0.5rem'},
          ),
          <Widget>[
            Button.secondary(
              label: reactorText(ReactorText.pluginApiValidate),
              size: ButtonSize.small,
              disabled: _saving,
              onPressed: _saving ? null : _validate,
            ),
            Button.primary(
              label: reactorText(ReactorText.pluginApiInstall),
              size: ButtonSize.small,
              disabled: _saving || !_canMutate,
              onPressed: _saving || !_canMutate ? null : _install,
            ),
          ],
        ),
      ],
    );
  }

  List<Widget> _catalogSections(PluginApiCatalog catalog) => <Widget>[
    SectionPanel(
      label: reactorText(ReactorText.pluginApiFolder),
      child: dom.code(<Widget>[Component.text(catalog.folder)]),
    ),
    if (catalog.errors.isNotEmpty)
      SectionPanel(
        label: reactorText(ReactorText.pluginApiErrors),
        children: catalog.errors
            .map(
              (PluginApiValidationError error) => ReactorNotice(
                title: error.fileName,
                message: error.message,
                status: ReactorStatus.critical,
              ),
            )
            .toList(growable: false),
      ),
    SectionPanel(
      label: reactorText(ReactorText.pluginApiCatalog),
      children: catalog.packs.isEmpty
          ? <Widget>[
              ReactorEmptyState(
                title: reactorText(ReactorText.pluginApiNone),
                description: reactorText(ReactorText.pluginApiNoneDescription),
                icon: ArcaneIcon.packageOpen(size: IconSize.sm),
              ),
            ]
          : catalog.packs.map(_pack).toList(growable: false),
    ),
  ];

  Widget _pack(PluginApiPack pack) {
    final ReactorStatus status = switch (pack.state) {
      'HEALTHY' => ReactorStatus.healthy,
      'DEGRADED' || 'LOADING' || 'TARGET_MISSING' => ReactorStatus.warning,
      'QUARANTINED' || 'INVALID' || 'INCOMPATIBLE' => ReactorStatus.critical,
      _ => ReactorStatus.neutral,
    };
    return SectionPanel(
      label: pack.name,
      description: '${pack.id} · v${pack.version}',
      trailing: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'gap': '0.4rem',
            'align-items': 'center',
          },
        ),
        <Widget>[
          reactorBadge(pack.state.toLowerCase().replaceAll('_', ' '), status),
          Button.secondary(
            label: reactorText(ReactorText.pluginApiEdit),
            size: ButtonSize.small,
            disabled: !_canMutate,
            onPressed: !_canMutate
                ? null
                : () => setState(() {
                    _editingId = pack.id;
                    _draft = pack.rawContent;
                    _validation = null;
                  }),
          ),
          Button.destructive(
            label: reactorText(
              _pendingDeleteId == pack.id
                  ? ReactorText.pluginApiConfirmDelete
                  : ReactorText.pluginApiDelete,
            ),
            size: ButtonSize.small,
            disabled: !_canMutate || _saving,
            onPressed: !_canMutate || _saving ? null : () => _remove(pack.id),
          ),
        ],
      ),
      children: <Widget>[
        dom.div(<Widget>[
          Component.text(
            '${reactorText(ReactorText.pluginApiTarget)}: ${pack.targetPlugin}'
            '${pack.targetVersion.isEmpty ? '' : ' ${pack.targetVersion}'} · '
            '${reactorText(ReactorText.pluginApiMetrics)}: ${pack.metrics.length}'
            '${pack.trusted ? ' · ${reactorText(ReactorText.pluginApiTrusted)}' : ''}',
          ),
        ]),
        dom.div(<Widget>[Component.text(pack.detail)]),
        if (pack.metrics.isNotEmpty)
          reactorGrid(
            minWidth: '220px',
            gap: '0.5rem',
            children: pack.metrics
                .map((PluginApiMetric metric) {
                  final ReactorStatus metricStatus = metric.available
                      ? ReactorStatus.healthy
                      : metric.quarantined
                      ? ReactorStatus.critical
                      : ReactorStatus.warning;
                  return MetricCard(
                    title: metric.displayName,
                    status: metricStatus,
                    badge: reactorBadge(
                      reactorText(
                        metric.available
                            ? ReactorText.pluginApiAvailable
                            : ReactorText.pluginApiUnavailable,
                      ),
                      metricStatus,
                    ),
                    child: dom.div(<Widget>[
                      dom.code(<Widget>[Component.text(metric.samplerId)]),
                      dom.div(<Widget>[
                        Component.text(
                          metric.available
                              ? metric.sourceType
                              : metric.availabilityReason,
                        ),
                      ]),
                    ]),
                  );
                })
                .toList(growable: false),
          ),
      ],
    );
  }
}
