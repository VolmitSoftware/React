library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/alert_thresholds.dart';
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
    ArcaneSonner.success('Thresholds saved');
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
    ArcaneSonner.success('Saved fleet cleared');
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
      ArcaneSonner.error('Nothing to export', description: 'No servers configured.');
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
    ArcaneSonner.success('Fleet imported', description: '${pending.servers.length} server${pending.servers.length == 1 ? '' : 's'} loaded.');
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
        title: 'Settings',
        subtitle: 'Alert thresholds and server tags',
        children: <Widget>[
          ArcaneEmptyState.noData(
            title: 'Fleet unavailable',
            description: 'No fleet has been initialized.',
          ),
        ],
      );
    }

    final List<ServerCredential> servers = ctrl.fleetManager.servers;

    return ReactorPage(
      title: 'Settings',
      subtitle: 'Alert thresholds and server tags',
      children: <Widget>[
        _rolesSection(servers),
        _thresholdsSection(ctrl),
        _serversSection(ctrl, servers),
      ],
    );
  }

  Widget _rolesSection(List<ServerCredential> servers) {
    return sectionCard(
      label: 'Account / Roles',
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
              <Widget>[Component.text('No servers configured.')],
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
      label: 'Alert Thresholds',
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
            label: 'TPS Warn',
            value: _tpsWarn,
            onChanged: (String v) => setState(() => _tpsWarn = v),
          ),
          _thresholdRow(
            label: 'TPS Critical',
            value: _tpsCrit,
            onChanged: (String v) => setState(() => _tpsCrit = v),
          ),
          _thresholdRow(
            label: 'MSPT Warn',
            value: _msptWarn,
            onChanged: (String v) => setState(() => _msptWarn = v),
          ),
          _thresholdRow(
            label: 'Incident Score Warn',
            value: _incidentScoreWarn,
            onChanged: (String v) => setState(() => _incidentScoreWarn = v),
          ),
          _thresholdRow(
            label: 'GC Percent Warn',
            value: _gcPercentWarn,
            onChanged: (String v) => setState(() => _gcPercentWarn = v),
          ),
          _thresholdRow(
            label: 'Ping P95 Warn',
            value: _pingP95Warn,
            onChanged: (String v) => setState(() => _pingP95Warn = v),
          ),
          _thresholdRow(
            label: 'Memory Pressure Warn',
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
                label: 'Save thresholds',
                onPressed: () => _saveThresholds(ctrl),
              ),
              Button.secondary(
                label: 'Reset to defaults',
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
      label: 'Saved Servers',
      trailing: Button.destructive(
        label: _confirmClearFleet ? 'Confirm clear all' : 'Clear all',
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
                label: 'Export connections',
                size: ButtonSize.small,
                onPressed: () => _exportConnections(ctrl),
              ),
              Button.secondary(
                label: 'Import connections',
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
              Component.text(
                'Export files contain bearer tokens and credentials. Store them securely.',
              ),
            ],
          ),
          if (_importError != null)
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'padding': '0.6rem 0.75rem',
                  'border-radius': '0.375rem',
                  'border': '1px solid color-mix(in srgb, var(--destructive) 40%, transparent)',
                  'background': 'color-mix(in srgb, var(--destructive) 12%, transparent)',
                  'color': 'var(--destructive)',
                  'font-size': '0.8rem',
                },
              ),
              <Widget>[Component.text('Import failed: $_importError')],
            ),
          if (_pendingImport != null && _pendingImport!.ok)
            ArcaneConfirmDialog(
              title: 'Replace fleet?',
              message:
                  'This will replace your ${servers.length} current server${servers.length == 1 ? '' : 's'} with ${_pendingImport!.servers.length} from the file${_pendingImport!.skipped > 0 ? ' (${_pendingImport!.skipped} malformed ${_pendingImport!.skipped == 1 ? 'entry' : 'entries'} skipped)' : ''}.',
              destructive: true,
              confirmText: 'Import',
              onConfirm: () => _confirmImport(ctrl),
              onCancel: _cancelImport,
            ),
          if (servers.isEmpty)
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{'color': 'var(--muted-foreground)'},
              ),
              <Widget>[Component.text('No servers configured.')],
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
                    placeholder: 'Server label',
                    onChange: (String v) =>
                        setState(() => _renameLabels[cred.id] = v),
                    fullWidth: true,
                  ),
                ],
              ),
              Button.secondary(
                label: 'Rename',
                size: ButtonSize.small,
                onPressed: () => _doRename(ctrl, cred.id),
              ),
              Button.destructive(
                label: 'Remove',
                size: ButtonSize.small,
                onPressed: () => setState(() => _confirmRemoveId = cred.id),
              ),
            ],
          ),
          if (_confirmRemoveId == cred.id)
            ArcaneConfirmDialog(
              title: 'Remove ${cred.label}?',
              message:
                  'This will disconnect and remove the server from your fleet.',
              destructive: true,
              confirmText: 'Remove',
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
                    placeholder: 'Add tag',
                    onChange: (String v) =>
                        setState(() => _tagInputs[cred.id] = v),
                    fullWidth: true,
                  ),
                ],
              ),
              Button.secondary(
                label: 'Add tag',
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
