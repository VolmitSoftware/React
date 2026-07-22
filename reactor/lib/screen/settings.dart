library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/alert_thresholds.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../model/server_credential.dart';
import '../service/react_client.dart';
import '../state/fleet_download.dart';
import '../state/fleet_export.dart';
import '../state/fleet_import_picker.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

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
  final Set<String> _roleFetchStarted = <String>{};

  final Map<String, String> _renameLabels = <String, String>{};
  final Map<String, String> _tagInputs = <String, String>{};

  String? _confirmRemoveId;
  bool _confirmClearFleet = false;
  bool _pickerOpen = false;

  FleetParseResult? _pendingImport;
  String? _importError;

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
        _fetchRole(ctrl.fleetManager, cred.id);
      }
    }
  }

  String _fmt(double v) {
    if (v == v.truncateToDouble()) return v.toInt().toString();
    return v.toString();
  }

  Future<void> _fetchRole(FleetManager fm, String id) async {
    final IRoleClient? client = fm.roleClientFor(id);
    if (client == null) return;
    try {
      final RoleInfo info = await client.whoami();
      if (!mounted) return;
      setState(() => _roles[id] = info);
    } on Object {
      return;
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
      _importError = null;
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
          _importError = result.error;
          _pendingImport = null;
        });
        return;
      }
      setState(() {
        _pickerOpen = false;
        _pendingImport = result;
        _importError = null;
      });
    });
  }

  void _confirmImport(FleetController ctrl) {
    final FleetParseResult? pending = _pendingImport;
    if (pending == null || !pending.ok) return;
    ctrl.importFleet(pending.servers);
    setState(() {
      _pendingImport = null;
      _importError = null;
      _renameLabels.clear();
      _tagInputs.clear();
      _roles.clear();
      _roleFetchStarted.clear();
      _confirmRemoveId = null;
      _confirmClearFleet = false;
      for (final ServerCredential cred in ctrl.fleetManager.servers) {
        _renameLabels[cred.id] = cred.label;
        _tagInputs[cred.id] = '';
        if (_roleFetchStarted.add(cred.id)) {
          _fetchRole(ctrl.fleetManager, cred.id);
        }
      }
    });
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
      _importError = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final FleetController? ctrl = FleetScope.of(context);
    if (ctrl == null) {
      return ReactorPage(
        title: reactorText(ReactorText.settingsTitle),
        subtitle: reactorText(ReactorText.settingsSubtitle),
        children: <Widget>[
          ArcaneEmptyState.noData(
            title: reactorText(ReactorText.settingsFleetUnavailable),
            description: reactorText(ReactorText.settingsFleetNotInitialized),
          ),
        ],
      );
    }

    final List<ServerCredential> servers = ctrl.fleetManager.servers;

    return ReactorPage(
      title: reactorText(ReactorText.settingsTitle),
      subtitle: reactorText(ReactorText.settingsSubtitle),
      children: <Widget>[
        _rolesSection(servers),
        _thresholdsSection(ctrl),
        _serversSection(ctrl, servers),
      ],
    );
  }

  Widget _rolesSection(List<ServerCredential> servers) {
    return sectionCard(
      label: reactorText(ReactorText.settingsAccountRoles),
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '0.75rem',
          },
        ),
        <Widget>[
          if (servers.isEmpty)
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{'color': 'var(--muted-foreground)'},
              ),
              <Widget>[
                Component.text(
                  reactorText(ReactorText.settingsNoServersConfigured),
                ),
              ],
            ),
          for (final ServerCredential cred in servers) _roleRow(cred),
        ],
      ),
    );
  }

  Widget _roleRow(ServerCredential cred) {
    final RoleInfo? role = _roles[cred.id];
    final bool resolved = _roles.containsKey(cred.id);
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '0.75rem',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{'flex': '1', 'font-weight': '500'},
          ),
          <Widget>[Component.text(cred.label)],
        ),
        if (resolved && role != null)
          RoleBadge(role: role)
        else
          Component.text('—'),
      ],
    );
  }

  Widget _thresholdsSection(FleetController ctrl) {
    return sectionCard(
      label: reactorText(ReactorText.settingsAlertThresholds),
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '0.75rem',
          },
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
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'gap': '0.5rem',
                'padding-top': '0.25rem',
              },
            ),
            <Widget>[
              Button.primary(
                label: reactorText(ReactorText.settingsSaveThresholds),
                onPressed: () => _saveThresholds(ctrl),
              ),
              Button.secondary(
                label: reactorText(ReactorText.settingsResetDefaults),
                onPressed: () => _resetThresholds(ctrl),
              ),
            ],
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
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '0.75rem',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'flex': '1',
              'font-size': '0.875rem',
              'color': 'var(--muted-foreground)',
            },
          ),
          <Widget>[Component.text(label)],
        ),
        dom.div(
          styles: const dom.Styles(raw: <String, String>{'width': '100px'}),
          <Widget>[
            TextInput(
              value: value,
              type: TextInputType.text,
              onChange: onChanged,
              fullWidth: true,
            ),
          ],
        ),
      ],
    );
  }

  Widget _serversSection(FleetController ctrl, List<ServerCredential> servers) {
    return sectionCard(
      label: reactorText(ReactorText.settingsSavedServers),
      trailing: Button.destructive(
        label: _confirmClearFleet
            ? reactorText(ReactorText.settingsConfirmClearAll)
            : reactorText(ReactorText.settingsClearAll),
        size: ButtonSize.small,
        disabled: servers.isEmpty,
        onPressed: servers.isEmpty ? null : () => _clearFleet(ctrl),
      ),
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '1.25rem',
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
              Button.secondary(
                label: reactorText(ReactorText.settingsExportConnections),
                size: ButtonSize.small,
                onPressed: () => _exportConnections(ctrl),
              ),
              Button.secondary(
                label: reactorText(ReactorText.settingsImportConnections),
                size: ButtonSize.small,
                onPressed: () => _triggerImportPicker(),
              ),
            ],
          ),
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'font-size': '0.75rem',
                'color': 'var(--muted-foreground)',
              },
            ),
            <Widget>[
              Component.text(reactorText(ReactorText.settingsExportSecurity)),
            ],
          ),
          if (_importError != null)
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'padding': '0.6rem 0.75rem',
                  'border-radius': '0.375rem',
                  'border':
                      '1px solid color-mix(in srgb, var(--destructive) 40%, transparent)',
                  'background':
                      'color-mix(in srgb, var(--destructive) 12%, transparent)',
                  'color': 'var(--destructive)',
                  'font-size': '0.8rem',
                },
              ),
              <Widget>[
                Component.text(
                  reactorText(
                    ReactorText.settingsImportFailed,
                    <String, Object?>{'error': _importError},
                  ),
                ),
              ],
            ),
          if (_pendingImport != null && _pendingImport!.ok)
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
          if (servers.isEmpty)
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{'color': 'var(--muted-foreground)'},
              ),
              <Widget>[
                Component.text(
                  reactorText(ReactorText.settingsNoServersConfigured),
                ),
              ],
            ),
          for (final ServerCredential cred in servers) _serverRow(ctrl, cred),
        ],
      ),
    );
  }

  Widget _serverRow(FleetController ctrl, ServerCredential cred) {
    final List<String> tags = ctrl.tagsStore.tagsFor(cred.id);
    final String tagInput = _tagInputs[cred.id] ?? '';
    final String renameLabel = _renameLabels[cred.id] ?? cred.label;

    return Card.elevated(
      fillWidth: true,
      child: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'padding': '0.75rem',
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '0.75rem',
          },
        ),
        <Widget>[
          Text.heading3(cred.label),
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'gap': '0.5rem',
              },
            ),
            <Widget>[
              dom.div(
                styles: const dom.Styles(raw: <String, String>{'flex': '1'}),
                <Widget>[
                  TextInput(
                    value: renameLabel,
                    placeholder: reactorText(ReactorText.settingsServerLabel),
                    onChange: (String v) =>
                        setState(() => _renameLabels[cred.id] = v),
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
                'gap': '0.5rem',
              },
            ),
            <Widget>[
              for (final String tag in tags) _tagChip(ctrl, cred.id, tag),
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{'width': '140px'},
                ),
                <Widget>[
                  TextInput(
                    value: tagInput,
                    placeholder: reactorText(ReactorText.settingsAddTag),
                    onChange: (String v) =>
                        setState(() => _tagInputs[cred.id] = v),
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
      ),
    );
  }

  Widget _tagChip(FleetController ctrl, String id, String tag) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'gap': '0.25rem',
        },
      ),
      <Widget>[
        ArcaneStatusBadge.secondary(tag),
        Button.ghost(
          label: '×',
          size: ButtonSize.small,
          onPressed: () => _removeTag(ctrl, id, tag),
        ),
      ],
    );
  }
}
