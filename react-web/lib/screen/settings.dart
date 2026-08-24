library;

import 'dart:async' show unawaited;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/alert_thresholds.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../model/server_credential.dart';
import '../service/react_client.dart';
import '../state/fleet_download.dart';
import '../state/fleet_export.dart';
import '../state/fleet_import_picker.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../theme/reactor_theme.dart';
import '../ui/reactor_ui.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

enum _RoleFailure { endpointUnavailable, requestFailed }

class _SettingsScreenState extends State<SettingsScreen> {
  bool _initialized = false;

  String _tpsWarn = '';
  String _tpsCrit = '';
  String _msptWarn = '';
  String _incidentScoreWarn = '';
  String _gcPercentWarn = '';
  String _pingP95Warn = '';
  String _memoryPressureWarn = '';

  final Map<String, RoleInfo?> _roles = <String, RoleInfo?>{};
  final Map<String, _RoleFailure> _roleErrors = <String, _RoleFailure>{};
  final Set<String> _roleFetchStarted = <String>{};

  final Map<String, String> _renameLabels = <String, String>{};
  final Map<String, String> _tagInputs = <String, String>{};

  String? _confirmRemoveId;
  bool _confirmClearFleet = false;
  bool _pickerOpen = false;

  FleetParseResult? _pendingImport;
  bool _importFailed = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_initialized) return;
    _initialized = true;
    final FleetController? ctrl = FleetScope.of(context);
    if (ctrl == null) return;

    final AlertThresholds t = ctrl.alertStore.thresholds;
    _tpsWarn = _fmt(t.tpsWarn);
    _tpsCrit = _fmt(t.tpsCrit);
    _msptWarn = _fmt(t.msptWarn);
    _incidentScoreWarn = _fmt(t.incidentScoreWarn);
    _gcPercentWarn = _fmt(t.gcPercentWarn);
    _pingP95Warn = _fmt(t.pingP95Warn);
    _memoryPressureWarn = _fmt(t.memoryPressureWarn);

    for (final ServerCredential cred in ctrl.fleetManager.servers) {
      _renameLabels[cred.id] = cred.label;
      _tagInputs[cred.id] = '';
      if (_roleFetchStarted.add(cred.id)) {
        unawaited(_fetchRole(ctrl.fleetManager, cred.id));
      }
    }
  }

  String _fmt(double v) {
    if (v == v.truncateToDouble()) return v.toInt().toString();
    return v.toString();
  }

  Future<void> _fetchRole(FleetManager fm, String id) async {
    final IRoleClient? client = fm.roleClientFor(id);
    if (client == null) {
      _roles.remove(id);
      _roleErrors[id] = _RoleFailure.endpointUnavailable;
      return;
    }
    try {
      final RoleInfo info = await client.whoami();
      if (!mounted) return;
      setState(() {
        _roles[id] = info;
        _roleErrors.remove(id);
      });
    } on Object {
      if (!mounted) return;
      setState(() {
        _roles.remove(id);
        _roleErrors[id] = _RoleFailure.requestFailed;
      });
    }
  }

  void _saveThresholds(FleetController ctrl) {
    final AlertThresholds updated = AlertThresholds(
      tpsWarn: double.tryParse(_tpsWarn) ?? AlertThresholds.defaults.tpsWarn,
      tpsCrit: double.tryParse(_tpsCrit) ?? AlertThresholds.defaults.tpsCrit,
      msptWarn: double.tryParse(_msptWarn) ?? AlertThresholds.defaults.msptWarn,
      incidentScoreWarn:
          double.tryParse(_incidentScoreWarn) ??
          AlertThresholds.defaults.incidentScoreWarn,
      gcPercentWarn:
          double.tryParse(_gcPercentWarn) ??
          AlertThresholds.defaults.gcPercentWarn,
      pingP95Warn:
          double.tryParse(_pingP95Warn) ?? AlertThresholds.defaults.pingP95Warn,
      memoryPressureWarn:
          double.tryParse(_memoryPressureWarn) ??
          AlertThresholds.defaults.memoryPressureWarn,
    );
    ctrl.alertStore.thresholds = updated;
    ArcaneSonner.success(reactorText(ReactorText.settingsThresholdsSaved));
  }

  void _resetThresholds(FleetController ctrl) {
    final AlertThresholds t = AlertThresholds.defaults;
    ctrl.alertStore.thresholds = t;
    setState(() {
      _tpsWarn = _fmt(t.tpsWarn);
      _tpsCrit = _fmt(t.tpsCrit);
      _msptWarn = _fmt(t.msptWarn);
      _incidentScoreWarn = _fmt(t.incidentScoreWarn);
      _gcPercentWarn = _fmt(t.gcPercentWarn);
      _pingP95Warn = _fmt(t.pingP95Warn);
      _memoryPressureWarn = _fmt(t.memoryPressureWarn);
    });
  }

  void _doRename(FleetController ctrl, String id) {
    final String label = _renameLabels[id] ?? '';
    if (label.isEmpty) return;
    ctrl.fleetManager.rename(id, label);
    setState(() {});
  }

  void _removeServer(FleetController ctrl, String id) {
    ctrl.removeServer(id);
    setState(() {
      _confirmRemoveId = null;
      _confirmClearFleet = false;
      _renameLabels.remove(id);
      _tagInputs.remove(id);
      _roles.remove(id);
      _roleErrors.remove(id);
      _roleFetchStarted.remove(id);
    });
  }

  void _clearFleet(FleetController ctrl) {
    if (!_confirmClearFleet && ctrl.fleetManager.servers.isNotEmpty) {
      setState(() => _confirmClearFleet = true);
      return;
    }
    ctrl.clearFleet();
    setState(() {
      _confirmClearFleet = false;
      _confirmRemoveId = null;
      _renameLabels.clear();
      _tagInputs.clear();
      _roles.clear();
      _roleErrors.clear();
      _roleFetchStarted.clear();
    });
    ArcaneSonner.success(reactorText(ReactorText.settingsFleetCleared));
  }

  void _addTag(FleetController ctrl, String id) {
    final String tag = (_tagInputs[id] ?? '').trim();
    if (tag.isEmpty) return;
    final List<String> current = ctrl.tagsStore.tagsFor(id).toList();
    if (!current.contains(tag)) {
      current.add(tag);
    }
    ctrl.tagsStore.setTags(id, current);
    setState(() {
      _tagInputs[id] = '';
    });
  }

  void _removeTag(FleetController ctrl, String id, String tag) {
    final List<String> current = ctrl.tagsStore.tagsFor(id).toList();
    current.remove(tag);
    ctrl.tagsStore.setTags(id, current);
    setState(() {});
  }

  void _exportConnections(FleetController ctrl) {
    final List<ServerCredential> servers = ctrl.fleetManager.servers;
    if (servers.isEmpty) {
      ArcaneSonner.error(
        reactorText(ReactorText.settingsNothingToExport),
        description: reactorText(ReactorText.settingsNoServersConfigured),
      );
      return;
    }
    final String json = buildFleetExportJson(servers);
    final String filename = 'reactor-fleet-${servers.length}-servers.json';
    downloadFleetJson(json, filename);
  }

  void _triggerImportPicker() {
    if (_pickerOpen) return;
    _pickerOpen = true;
    setState(() {
      _pendingImport = null;
      _importFailed = false;
    });
    pickFleetImportFile((String raw) {
      if (!mounted) {
        _pickerOpen = false;
        return;
      }
      final FleetParseResult result = parseFleetImportJson(raw);
      if (!result.ok) {
        setState(() {
          _pickerOpen = false;
          _importFailed = true;
          _pendingImport = null;
        });
        return;
      }
      setState(() {
        _pickerOpen = false;
        _pendingImport = result;
        _importFailed = false;
      });
    });
  }

  void _confirmImport(FleetController ctrl) {
    final FleetParseResult? pending = _pendingImport;
    if (pending == null || !pending.ok) return;
    ctrl.importFleet(pending.servers);
    final List<String> roleIds = <String>[];
    setState(() {
      _pendingImport = null;
      _importFailed = false;
      _renameLabels.clear();
      _tagInputs.clear();
      _roles.clear();
      _roleErrors.clear();
      _roleFetchStarted.clear();
      _confirmRemoveId = null;
      _confirmClearFleet = false;
      for (final ServerCredential cred in ctrl.fleetManager.servers) {
        _renameLabels[cred.id] = cred.label;
        _tagInputs[cred.id] = '';
        if (_roleFetchStarted.add(cred.id)) {
          roleIds.add(cred.id);
        }
      }
    });
    for (final String id in roleIds) {
      unawaited(_fetchRole(ctrl.fleetManager, id));
    }
    ArcaneSonner.success(
      reactorText(ReactorText.settingsFleetImported),
      description: reactorText(
        ReactorText.settingsServersLoaded,
        <String, Object?>{'count': pending.servers.length},
      ),
    );
  }

  void _cancelImport() {
    setState(() {
      _pendingImport = null;
      _importFailed = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ReactorThemeScope theme = ReactorThemeScope.of(context);
    final FleetController? ctrl = FleetScope.of(context);
    if (ctrl == null) {
      return ReactorPage(
        title: reactorText(ReactorText.settingsTitle),
        subtitle: reactorText(ReactorText.settingsSubtitle),
        children: <Widget>[
          _appearanceSection(theme),
          SectionPanel(
            child: ReactorEmptyState(
              title: reactorText(ReactorText.settingsFleetUnavailable),
              description: reactorText(ReactorText.settingsFleetNotInitialized),
              icon: ArcaneIcon.serverOff(size: IconSize.sm),
            ),
          ),
        ],
      );
    }

    final List<ServerCredential> servers = ctrl.fleetManager.servers;

    return ReactorPage(
      title: reactorText(ReactorText.settingsTitle),
      subtitle: reactorText(ReactorText.settingsSubtitle),
      children: <Widget>[
        _appearanceSection(theme),
        _rolesSection(servers),
        _thresholdsSection(ctrl),
        _serversSection(ctrl, servers),
      ],
    );
  }

  Widget _appearanceSection(ReactorThemeScope theme) {
    return SectionPanel(
      label: reactorText(ReactorText.settingsAppearance),
      description: reactorText(ReactorText.settingsAppearanceDescription),
      flush: true,
      child: dom.div(classes: 'reactor-theme-options', <Widget>[
        _themeOption(
          theme: theme,
          brightness: Brightness.dark,
          title: reactorText(ReactorText.settingsDarkTheme),
          description: reactorText(ReactorText.settingsDarkThemeDescription),
          icon: ArcaneIcon.moon(size: IconSize.sm),
        ),
        _themeOption(
          theme: theme,
          brightness: Brightness.light,
          title: reactorText(ReactorText.settingsLightTheme),
          description: reactorText(ReactorText.settingsLightThemeDescription),
          icon: ArcaneIcon.sun(size: IconSize.sm),
        ),
      ]),
    );
  }

  Widget _themeOption({
    required ReactorThemeScope theme,
    required Brightness brightness,
    required String title,
    required String description,
    required Widget icon,
  }) {
    final bool selected = theme.brightness == brightness;
    return dom.button(
      classes: selected
          ? 'reactor-theme-option is-selected'
          : 'reactor-theme-option',
      attributes: <String, String>{
        'type': 'button',
        'aria-pressed': selected ? 'true' : 'false',
        'data-theme-choice': brightness == Brightness.light ? 'light' : 'dark',
      },
      events: <String, void Function(Object)>{
        'click': (Object _) => theme.onChanged(brightness),
      },
      <Widget>[
        dom.span(
          classes: 'reactor-theme-option-icon',
          attributes: const <String, String>{'aria-hidden': 'true'},
          <Widget>[icon],
        ),
        dom.span(classes: 'reactor-theme-option-copy', <Widget>[
          dom.strong(<Widget>[Component.text(title)]),
          dom.span(<Widget>[Component.text(description)]),
        ]),
        if (selected)
          dom.span(classes: 'reactor-theme-option-state', <Widget>[
            ArcaneIcon.check(size: IconSize.sm),
            Component.text(reactorText(ReactorText.commonActive)),
          ]),
      ],
    );
  }

  Widget _rolesSection(List<ServerCredential> servers) {
    final List<ServerCredential> failed = servers
        .where((ServerCredential cred) => _roleErrors.containsKey(cred.id))
        .toList();
    final bool loading = servers.any(
      (ServerCredential cred) =>
          _roleFetchStarted.contains(cred.id) &&
          !_roles.containsKey(cred.id) &&
          !_roleErrors.containsKey(cred.id),
    );
    return SectionPanel(
      label: reactorText(ReactorText.settingsAccountRoles),
      description: reactorText(ReactorText.settingsEffectiveAccess),
      flush: true,
      child: servers.isEmpty
          ? _inset(
              ReactorEmptyState(
                title: reactorText(ReactorText.settingsNoServersConfigured),
                description: reactorText(ReactorText.settingsPairForRole),
                icon: ArcaneIcon.shieldCheck(size: IconSize.sm),
              ),
            )
          : dom.div(<Widget>[
              if (loading)
                _inset(
                  ReactorLoadingState(
                    label: reactorText(ReactorText.settingsLoadingRoles),
                  ),
                ),
              if (failed.isNotEmpty)
                _inset(
                  ReactorNotice(
                    title: reactorText(
                      ReactorText.settingsSomeRolesUnavailable,
                    ),
                    message: reactorText(
                      ReactorText.settingsRoleLookupFailed,
                      <String, Object?>{
                        'servers': failed
                            .map((ServerCredential cred) => cred.label)
                            .join(', '),
                      },
                    ),
                    status: ReactorStatus.warning,
                  ),
                ),
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'border-top': '1px solid var(--border)',
                  },
                ),
                <Widget>[
                  for (final ServerCredential cred in servers) _roleRow(cred),
                ],
              ),
            ]),
    );
  }

  Widget _roleRow(ServerCredential cred) {
    final RoleInfo? role = _roles[cred.id];
    final _RoleFailure? failure = _roleErrors[cred.id];
    final String? error = switch (failure) {
      _RoleFailure.endpointUnavailable => reactorText(
        ReactorText.settingsRoleEndpointUnavailable,
      ),
      _RoleFailure.requestFailed => reactorText(ReactorText.errorUnavailable),
      null => null,
    };
    final (String, ReactorStatus) state = error != null
        ? (reactorText(ReactorText.commonUnavailable), ReactorStatus.warning)
        : role == null
        ? (reactorText(ReactorText.settingsLoadingRoles), ReactorStatus.info)
        : switch (role.role) {
            'admin' => (
              reactorText(ReactorText.roleAdmin),
              ReactorStatus.healthy,
            ),
            'operator' => (
              reactorText(ReactorText.roleOperator),
              ReactorStatus.info,
            ),
            _ => (reactorText(ReactorText.roleViewer), ReactorStatus.warning),
          };
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '1rem',
          'padding': '0.7rem 1rem',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'gap': '0.15rem',
              'min-width': '0',
              'flex': '1',
            },
          ),
          <Widget>[
            dom.strong(<Widget>[Component.text(cred.label)]),
            dom.span(
              classes: 'reactor-ltr',
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-family': 'monospace',
                  'font-size': '0.75rem',
                  'overflow': 'hidden',
                  'text-overflow': 'ellipsis',
                  'white-space': 'nowrap',
                },
              ),
              <Widget>[Component.text(_endpoint(cred))],
            ),
          ],
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.4rem',
              'font-size': '0.8125rem',
            },
          ),
          attributes: error == null ? null : <String, String>{'title': error},
          <Widget>[
            reactorStatusDot(state.$2, size: 7, label: state.$1),
            Component.text(state.$1),
          ],
        ),
      ],
    );
  }

  Widget _thresholdsSection(FleetController ctrl) {
    return SectionPanel(
      label: reactorText(ReactorText.settingsAlertThresholds),
      description: reactorText(ReactorText.settingsThresholdDescription),
      trailing: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'align-items': 'center',
            'gap': '0.5rem',
            'flex-wrap': 'wrap',
          },
        ),
        <Widget>[
          Button.secondary(
            label: reactorText(ReactorText.settingsResetDefaults),
            size: ButtonSize.small,
            onPressed: () => _resetThresholds(ctrl),
          ),
          Button.primary(
            label: reactorText(ReactorText.settingsSaveThresholds),
            size: ButtonSize.small,
            onPressed: () => _saveThresholds(ctrl),
          ),
        ],
      ),
      flush: true,
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{'border-top': '1px solid var(--border)'},
        ),
        <Widget>[
          _thresholdRow(
            label: reactorText(ReactorText.settingsTpsWarn),
            value: _tpsWarn,
            onChanged: (String v) => setState(() => _tpsWarn = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsTpsCritical),
            value: _tpsCrit,
            onChanged: (String v) => setState(() => _tpsCrit = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsMsptWarn),
            value: _msptWarn,
            onChanged: (String v) => setState(() => _msptWarn = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsIncidentScoreWarn),
            value: _incidentScoreWarn,
            onChanged: (String v) => setState(() => _incidentScoreWarn = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsGcPercentWarn),
            value: _gcPercentWarn,
            onChanged: (String v) => setState(() => _gcPercentWarn = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsPingP95Warn),
            value: _pingP95Warn,
            onChanged: (String v) => setState(() => _pingP95Warn = v),
          ),
          _thresholdRow(
            label: reactorText(ReactorText.settingsMemoryPressureWarn),
            value: _memoryPressureWarn,
            onChanged: (String v) => setState(() => _memoryPressureWarn = v),
          ),
        ],
      ),
    );
  }

  Widget _thresholdRow({
    required String label,
    required String value,
    required void Function(String) onChanged,
  }) {
    return dom.div(classes: 'reactor-settings-threshold-row', <Widget>[
      dom.div(classes: 'reactor-settings-threshold-label', <Widget>[
        Component.text(label),
      ]),
      TextInput(
        value: value,
        type: TextInputType.number,
        onChange: onChanged,
        fullWidth: true,
      ),
    ]);
  }

  Widget _serversSection(FleetController ctrl, List<ServerCredential> servers) {
    return SectionPanel(
      label: reactorText(ReactorText.settingsSavedServers),
      description: reactorText(
        ReactorText.settingsSavedConnectionsCount,
        <String, Object?>{'count': servers.length},
      ),
      trailing: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'align-items': 'center',
            'gap': '0.5rem',
            'flex-wrap': 'wrap',
          },
        ),
        <Widget>[
          Button.secondary(
            label: reactorText(ReactorText.settingsExportConnections),
            size: ButtonSize.small,
            disabled: servers.isEmpty,
            onPressed: servers.isEmpty ? null : () => _exportConnections(ctrl),
          ),
          Button.secondary(
            label: reactorText(ReactorText.settingsImportConnections),
            size: ButtonSize.small,
            disabled: _pickerOpen,
            onPressed: _pickerOpen ? null : _triggerImportPicker,
          ),
          Button.destructive(
            label: _confirmClearFleet
                ? reactorText(ReactorText.settingsConfirmClearAll)
                : reactorText(ReactorText.settingsClearAll),
            size: ButtonSize.small,
            disabled: servers.isEmpty,
            onPressed: servers.isEmpty ? null : () => _clearFleet(ctrl),
          ),
        ],
      ),
      flush: true,
      child: dom.div(<Widget>[
        _inset(
          ReactorNotice(
            title: reactorText(ReactorText.settingsExportConnections),
            message: reactorText(ReactorText.settingsExportSecurity),
            status: ReactorStatus.info,
          ),
        ),
        if (_pickerOpen)
          _inset(
            ReactorLoadingState(
              label: reactorText(ReactorText.settingsWaitingForFile),
            ),
          ),
        if (_importFailed)
          _inset(
            ReactorNotice(
              title: reactorText(ReactorText.settingsImportConnections),
              message: reactorText(ReactorText.settingsImportFailed),
              status: ReactorStatus.critical,
            ),
          ),
        if (_pendingImport != null && _pendingImport!.ok)
          _inset(
            ArcaneConfirmDialog(
              title: reactorText(ReactorText.settingsReplaceFleet),
              message:
                  reactorText(
                    ReactorText.settingsReplaceFleetMessage,
                    <String, Object?>{
                      'current': servers.length,
                      'incoming': _pendingImport!.servers.length,
                    },
                  ) +
                  (_pendingImport!.skipped > 0
                      ? reactorText(
                          ReactorText.settingsMalformedSkipped,
                          <String, Object?>{'count': _pendingImport!.skipped},
                        )
                      : ''),
              destructive: true,
              confirmText: reactorText(ReactorText.settingsImport),
              onConfirm: () => _confirmImport(ctrl),
              onCancel: _cancelImport,
            ),
          ),
        if (servers.isEmpty)
          _inset(
            ReactorEmptyState(
              title: reactorText(ReactorText.settingsNoServersConfigured),
              description: reactorText(ReactorText.settingsPairToManage),
              icon: ArcaneIcon.serverOff(size: IconSize.sm),
            ),
          ),
        if (servers.isNotEmpty)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{'border-top': '1px solid var(--border)'},
            ),
            <Widget>[
              for (final ServerCredential cred in servers)
                _serverRow(ctrl, cred),
            ],
          ),
      ]),
    );
  }

  Widget _serverRow(FleetController ctrl, ServerCredential cred) {
    final List<String> tags = ctrl.tagsStore.tagsFor(cred.id);
    final String tagInput = _tagInputs[cred.id] ?? '';
    final String renameLabel = _renameLabels[cred.id] ?? cred.label;

    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'padding': '0.8rem 1rem',
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.65rem',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.5rem',
              'flex-wrap': 'wrap',
            },
          ),
          <Widget>[
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.15rem',
                  'min-width': '180px',
                  'flex': '0 1 240px',
                },
              ),
              <Widget>[
                dom.strong(<Widget>[Component.text(cred.label)]),
                dom.span(
                  classes: 'reactor-ltr',
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'color': 'var(--muted-foreground)',
                      'font-family': 'monospace',
                      'font-size': '0.72rem',
                      'overflow': 'hidden',
                      'text-overflow': 'ellipsis',
                      'white-space': 'nowrap',
                    },
                  ),
                  <Widget>[Component.text(_endpoint(cred))],
                ),
              ],
            ),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'min-width': '210px',
                  'flex': '1 1 280px',
                },
              ),
              <Widget>[
                TextInput(
                  value: renameLabel,
                  placeholder: reactorText(ReactorText.settingsServerLabel),
                  onChange: (String value) =>
                      setState(() => _renameLabels[cred.id] = value),
                  fullWidth: true,
                ),
              ],
            ),
            Button.secondary(
              label: reactorText(ReactorText.settingsRename),
              size: ButtonSize.small,
              onPressed: () => _doRename(ctrl, cred.id),
            ),
            Button.destructive(
              label: reactorText(ReactorText.settingsRemove),
              size: ButtonSize.small,
              onPressed: () => setState(() => _confirmRemoveId = cred.id),
            ),
          ],
        ),
        if (_confirmRemoveId == cred.id)
          ArcaneConfirmDialog(
            title: reactorText(
              ReactorText.settingsRemoveServer,
              <String, Object?>{'server': cred.label},
            ),
            message: reactorText(ReactorText.settingsRemoveServerMessage),
            destructive: true,
            confirmText: reactorText(ReactorText.settingsRemove),
            onConfirm: () => _removeServer(ctrl, cred.id),
            onCancel: () => setState(() => _confirmRemoveId = null),
          ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'flex-wrap': 'wrap',
              'align-items': 'center',
              'gap': '0.4rem',
              'padding-top': '0.55rem',
              'border-top': '1px solid var(--border)',
            },
          ),
          <Widget>[
            dom.span(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.72rem',
                  'font-weight': '600',
                  'text-transform': 'uppercase',
                  'letter-spacing': '0.06em',
                },
              ),
              <Widget>[Component.text(reactorText(ReactorText.settingsTags))],
            ),
            if (tags.isEmpty)
              dom.span(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'color': 'var(--muted-foreground)',
                    'font-size': '0.8rem',
                  },
                ),
                <Widget>[
                  Component.text(reactorText(ReactorText.settingsNoTags)),
                ],
              ),
            for (final String tag in tags) _tagChip(ctrl, cred.id, tag),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'width': '140px',
                  'margin-inline-start': 'auto',
                },
              ),
              <Widget>[
                TextInput(
                  value: tagInput,
                  placeholder: reactorText(ReactorText.settingsAddTag),
                  onChange: (String value) =>
                      setState(() => _tagInputs[cred.id] = value),
                  fullWidth: true,
                ),
              ],
            ),
            Button.secondary(
              label: reactorText(ReactorText.settingsAddTag),
              size: ButtonSize.small,
              onPressed: () => _addTag(ctrl, cred.id),
            ),
          ],
        ),
      ],
    );
  }

  Widget _tagChip(FleetController ctrl, String id, String tag) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'inline-flex',
          'align-items': 'center',
          'border': '1px solid var(--border)',
          'border-radius': '0',
          'padding-inline-start': '0.5rem',
          'font-size': '0.75rem',
        },
      ),
      <Widget>[
        Component.text(tag),
        Button.ghost(
          label: '×',
          attributes: <String, String>{
            'aria-label': reactorText(
              ReactorText.settingsRemoveTag,
              <String, Object?>{'tag': tag},
            ),
            'title': reactorText(
              ReactorText.settingsRemoveTag,
              <String, Object?>{'tag': tag},
            ),
          },
          size: ButtonSize.small,
          onPressed: () => _removeTag(ctrl, id, tag),
        ),
      ],
    );
  }

  Widget _inset(Widget child) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{'padding': '0.75rem 1rem'},
      ),
      <Widget>[child],
    );
  }

  String _endpoint(ServerCredential cred) {
    final Uri? direct = cred.directBaseUri;
    if (direct == null) {
      return cred.relayUrl ?? reactorText(ReactorText.settingsRelayConnection);
    }
    return direct.toString();
  }
}
