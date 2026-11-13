/// Settings > Servers: the saved servers with their names and tags, Remove,
/// and (Advanced) import, export and Clear all. Every removal asks first.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../model/server_credential.dart';
import '../state/fleet_download.dart';
import '../state/fleet_export.dart';
import '../state/fleet_import_picker.dart';
import '../state/fleet_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/info_tip.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/sheet.dart';
import '../ui/kit/states.dart';
import 'pairing/pair_dialog.dart';
import 'pairing/pair_words.dart';
import 'server_roles.dart';

/// Where this browser reaches [cred]: its direct address, else its relay.
String serverAddress(ServerCredential cred) =>
    cred.directBaseUri?.toString() ??
    cred.relayUrl ??
    reactorText(ReactorText.settingsRelayConnection);

/// Tags typed as "eu, survival": trimmed, without blanks or repeats.
List<String> parseTags(String text) => <String>{
  for (final String tag in text.split(','))
    if (tag.trim().isNotEmpty) tag.trim(),
}.toList();

class SettingsServersTab extends StatefulWidget {
  const SettingsServersTab({super.key});

  @override
  State<SettingsServersTab> createState() => _SettingsServersTabState();
}

class _SettingsServersTabState extends State<SettingsServersTab> {
  static const String _nameId = 'server-edit-name';
  static const String _tagsId = 'server-edit-tags';

  /// The server in the edit dialog, and what its boxes hold.
  ServerCredential? _editing;
  String _name = '';
  String _tags = '';
  bool _nameMissing = false;
  bool _picking = false;

  /// What this browser may do on each server (Advanced).
  late final ServerRoles _roles = ServerRoles(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _roles.close();
    super.dispose();
  }

  /// What [id]'s role lets this browser do, in the pairing card's words;
  /// null while the role is unknown.
  String? _rights(String id) {
    final RoleInfo? role = _roles.roleOf(id);
    final List<String> words = role == null
        ? const <String>[]
        : scopeWords(role.scopes);
    return words.isEmpty ? null : '${words.join('. ')}.';
  }

  void _edit(FleetController fleet, ServerCredential cred) {
    setState(() {
      _editing = cred;
      _name = cred.label;
      _tags = fleet.tagsStore.tagsFor(cred.id).join(', ');
      _nameMissing = false;
    });
  }

  void _save(FleetController fleet) {
    final ServerCredential? cred = _editing;
    if (cred == null) return;
    final String name = _name.trim();
    if (name.isEmpty) {
      setState(() => _nameMissing = true);
      return;
    }
    if (name != cred.label) fleet.fleetManager.rename(cred.id, name);
    fleet.tagsStore.setTags(cred.id, parseTags(_tags));
    setState(() => _editing = null);
  }

  Future<void> _remove(FleetController fleet, ServerCredential cred) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.settingsRemoveServer, <String, Object?>{
        'server': cred.label,
      }),
      message: reactorText(ReactorText.settingsRemoveServerMessage),
      confirmLabel: reactorText(ReactorText.settingsRemove),
      destructive: true,
    );
    if (!yes || !mounted) return;
    fleet.removeServer(cred.id);
    setState(() {});
  }

  Future<void> _clearAll(FleetController fleet) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.settingsClearAllTitle),
      message: reactorText(ReactorText.settingsClearAllMessage),
      confirmLabel: reactorText(ReactorText.settingsClearAll),
      destructive: true,
    );
    if (!yes || !mounted) return;
    fleet.clearFleet();
    setState(() {});
    ArcaneSonner.success(reactorText(ReactorText.settingsFleetCleared));
  }

  void _export(List<ServerCredential> servers) => downloadFleetJson(
    buildFleetExportJson(servers),
    'reactor-fleet-${servers.length}-servers.json',
  );

  void _import(FleetController fleet) {
    if (_picking) return;
    _picking = true;
    pickFleetImportFile((String raw) async {
      _picking = false;
      if (!mounted) return;
      final FleetParseResult result = parseFleetImportJson(raw);
      if (!result.ok) {
        ArcaneSonner.error(
          reactorText(ReactorText.settingsImportFailed),
          description: result.error,
        );
        return;
      }
      final int current = fleet.fleetManager.servers.length;
      final bool yes = await askToConfirm(
        title: reactorText(ReactorText.settingsReplaceFleet),
        message:
            reactorText(
              ReactorText.settingsReplaceFleetMessage,
              <String, Object?>{
                'current': current,
                'incoming': result.servers.length,
              },
            ) +
            (result.skipped > 0
                ? reactorText(
                    ReactorText.settingsMalformedSkipped,
                    <String, Object?>{'count': result.skipped},
                  )
                : ''),
        confirmLabel: reactorText(ReactorText.settingsImport),
        destructive: current > 0,
      );
      if (!yes || !mounted) return;
      fleet.importFleet(result.servers);
      setState(() {});
      ArcaneSonner.success(
        reactorText(ReactorText.settingsFleetImported),
        description: reactorText(
          ReactorText.settingsServersLoaded,
          <String, Object?>{'count': result.servers.length},
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetController? fleet = FleetScope.of(context);
    if (fleet == null) {
      return EmptyState(
        message: reactorText(ReactorText.settingsFleetUnavailable),
      );
    }
    final List<ServerCredential> servers = fleet.fleetManager.servers;
    final bool advanced = ModeScope.advanced(context);
    final ServerCredential? editing = _editing;
    // Roles arrive later; a page rendered on the server cannot redraw.
    if (advanced && context.binding.isClient) {
      _roles.ask(fleet.fleetManager, <String>[
        for (final ServerCredential cred in servers) cred.id,
      ]);
    }
    return dom.div(classes: 'ui-stack', <Widget>[
      AppCard(
        title: reactorText(ReactorText.settingsSavedServers),
        actions: ActionRow(
          dense: true,
          label: reactorText(ReactorText.settingsSavedServers),
          more: <AppMenuEntry>[
            if (advanced) ...<AppMenuEntry>[
              AppMenuEntry(
                reactorText(ReactorText.settingsImportConnections),
                () => _import(fleet),
                icon: ArcaneIcon.upload(),
              ),
              AppMenuEntry(
                reactorText(ReactorText.settingsExportConnections),
                servers.isEmpty ? null : () => _export(servers),
                icon: ArcaneIcon.download(),
                description: reactorText(ReactorText.settingsExportSecurity),
              ),
              const AppMenuEntry.separator(),
              AppMenuEntry(
                reactorText(ReactorText.settingsClearAll),
                servers.isEmpty ? null : () => _clearAll(fleet),
                icon: ArcaneIcon.trash2(),
                destructive: true,
              ),
            ],
          ],
          primary: RowAction(
            reactorText(ReactorText.pairTitle),
            PairDialogScope.of(context),
            icon: ArcaneIcon.plus(),
          ),
        ),
        children: <Widget>[
          DataList<ServerCredential>(
            label: reactorText(ReactorText.settingsSavedServers),
            items: servers,
            emptyMessage: reactorText(ReactorText.fleetNoServersPaired),
            href: (ServerCredential cred) => serverPath(cred.id),
            columns: <ListColumn<ServerCredential>>[
              ListColumn<ServerCredential>.text(
                reactorText(ReactorText.commonServer),
                (ServerCredential cred) => cred.label,
                width: 'minmax(0, 1.5fr)',
              ),
              ListColumn<ServerCredential>(
                reactorText(ReactorText.settingsAddress),
                (ServerCredential cred) => dom.span(
                  classes: 'reactor-ltr',
                  <Widget>[Component.text(serverAddress(cred))],
                ),
                width: 'minmax(0, 2fr)',
              ),
              if (advanced)
                ListColumn<ServerCredential>(
                  reactorText(ReactorText.fleetRole),
                  (ServerCredential cred) => TipLabel(
                    label: _roles.badge(cred.id),
                    tip: _rights(cred.id),
                  ),
                  width: 'minmax(0, 1fr)',
                ),
              // Tags only once a server has some.
              if (servers.any(
                (ServerCredential cred) =>
                    fleet.tagsStore.tagsFor(cred.id).isNotEmpty,
              ))
                ListColumn<ServerCredential>(
                  reactorText(ReactorText.settingsTags),
                  (ServerCredential cred) => _tagBadges(fleet, cred.id),
                  width: 'minmax(0, 1.5fr)',
                ),
            ],
            // One row pattern: Edit as the button, Remove (it asks first)
            // in the row's "..." menu.
            actions: (ServerCredential cred) => <RowAction>[
              RowAction(
                reactorText(ReactorText.settingsEdit),
                () => _edit(fleet, cred),
                attributes: <String, String>{
                  'aria-label': reactorText(
                    ReactorText.settingsEditServer,
                    <String, Object?>{'server': cred.label},
                  ),
                },
              ),
            ],
            more: (ServerCredential cred) => <AppMenuEntry>[
              AppMenuEntry(
                reactorText(ReactorText.settingsRemove),
                () => _remove(fleet, cred),
                icon: ArcaneIcon.trash2(),
                destructive: true,
              ),
            ],
          ),
        ],
      ),
      Sheet(
        open: editing != null,
        title: editing == null
            ? ''
            : reactorText(ReactorText.settingsEditServer, <String, Object?>{
                'server': editing.label,
              }),
        onClose: () => setState(() => _editing = null),
        footer: DialogFooter(
          cancel: RowAction(
            reactorText(ReactorText.commonCancel),
            () => setState(() => _editing = null),
          ),
          primary: RowAction(
            reactorText(ReactorText.settingsSave),
            () => _save(fleet),
          ),
        ),
        child: dom.div(classes: 'ui-stack', <Widget>[
          if (editing != null)
            ArcaneFieldGroup(
              children: <Widget>[
                ArcaneFormField(
                  controlId: _nameId,
                  invalid: _nameMissing,
                  children: <Widget>[
                    ArcaneFieldLabel(
                      reactorText(ReactorText.settingsServerLabel),
                    ),
                    TextInput(
                      value: _name,
                      fullWidth: true,
                      onChanged: (String text) => _name = text,
                      onSubmitted: (String text) {
                        _name = text;
                        _save(fleet);
                      },
                    ),
                    ArcaneFieldError(
                      _nameMissing
                          ? reactorText(ReactorText.settingsNameRequired)
                          : null,
                    ),
                  ],
                ),
                ArcaneFormField(
                  controlId: _tagsId,
                  children: <Widget>[
                    ArcaneFieldLabel(
                      reactorText(ReactorText.settingsTags),
                      trailing: <Widget>[
                        InfoTip(tip: reactorText(ReactorText.settingsTagsTip)),
                      ],
                    ),
                    TextInput(
                      value: _tags,
                      fullWidth: true,
                      onChanged: (String text) => _tags = text,
                      onSubmitted: (String text) {
                        _tags = text;
                        _save(fleet);
                      },
                    ),
                  ],
                ),
              ],
            ),
        ]),
      ),
    ]);
  }

  static Widget _tagBadges(FleetController fleet, String id) {
    final List<String> tags = fleet.tagsStore.tagsFor(id);
    if (tags.isEmpty) {
      return dom.span(classes: 'ui-caption', <Widget>[
        Component.text(reactorText(ReactorText.settingsNoTags)),
      ]);
    }
    return dom.span(classes: 'ui-tip-label', <Widget>[
      for (final String tag in tags) StatusBadge(tag),
    ]);
  }
}
