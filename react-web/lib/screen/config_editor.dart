/// Tuning > Config: presets that set many values at once and, in Advanced,
/// every section of react.toml with Save and Discard. Edits are held until
/// Save; a preset asks first when it would drop them.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/config_tree.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/config_controller.dart';
import '../state/connection_manager.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../ui/kit/states.dart';
import '../ui/kit/toggle_choice.dart';
import '../ui/error_text.dart';
import '../widget/knob_editor.dart';

/// The presets the server offers, by wire name.
const List<(String, ReactorText)> kConfigPresets = <(String, ReactorText)>[
  ('off', ReactorText.configEditorPresetOff),
  ('light', ReactorText.configEditorPresetLight),
  ('balanced', ReactorText.configEditorPresetBalanced),
  ('high', ReactorText.configEditorPresetHigh),
];

/// The presets as one choice. The server keeps no current preset (a preset
/// writes many values once), so [applied] is the one applied from here
/// since the page opened, until a setting is edited; nothing is picked
/// before that. [onPreset] null disables them.
class ConfigPresetsCard extends StatelessWidget {
  const ConfigPresetsCard({this.onPreset, this.applied, super.key});

  final void Function(String preset)? onPreset;
  final String? applied;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final void Function(String)? apply = onPreset;
    return AppCard(
      title: reactorText(ReactorText.configEditorPresets),
      tip: reactorText(ReactorText.configEditorPresetsTip),
      children: <Widget>[
        ActionRow(
          dense: true,
          label: reactorText(ReactorText.configEditorPresets),
          leading: <Widget>[
            ToggleChoice<String?>(
              label: reactorText(ReactorText.configEditorPresets),
              options: <ChoiceOption<String?>>[
                for (final (String name, ReactorText label) in kConfigPresets)
                  ChoiceOption<String?>(name, reactorText(label)),
              ],
              value: applied,
              disabled: apply == null,
              onChanged: (String? name) {
                if (apply != null && name != null) apply(name);
              },
            ),
          ],
        ),
      ],
    );
  }
}

/// Every section of the config file with its settings, then Save and
/// Discard. [onEdit] null shows the settings disabled.
class ConfigSectionsCard extends StatelessWidget {
  const ConfigSectionsCard({
    required this.tree,
    required this.pending,
    this.saving = false,
    this.onEdit,
    this.onSave,
    this.onDiscard,
    this.changedOnServer,
    super.key,
  });

  final ConfigTree tree;

  /// Unsaved values by key; shown instead of the server's.
  final Map<String, Object?> pending;
  final bool saving;
  final void Function(String key, Object? value)? onEdit;
  final void Function()? onSave;
  final void Function()? onDiscard;

  /// Whether the server changed a setting that has an unsaved edit here.
  final bool Function(String key)? changedOnServer;

  Widget _section(ConfigSection section) {
    final void Function(String, Object?)? edit = onEdit;
    final String name = section.name;
    return PageSection(
      // react.toml names its tables in lower case ("general").
      title: name.isEmpty ? name : name[0].toUpperCase() + name.substring(1),
      // Boxes and selects first, then the switches in a block of their
      // own, so each grid keeps one row height.
      children: <Widget>[
        for (final bool switches in <bool>[false, true])
          if (section.nodes.any(
            (Knob node) => (node.type == KnobType.boolType) == switches,
          ))
            dom.div(classes: 'reactor-field-grid', <Widget>[
              for (final Knob node in section.nodes)
                if ((node.type == KnobType.boolType) == switches)
                  KnobEditor(
                    knob: pending.containsKey(node.key)
                        ? node.copyWith(value: pending[node.key])
                        : node,
                    disabled: edit == null,
                    changedOnServer: changedOnServer?.call(node.key) ?? false,
                    onChanged: edit == null
                        ? null
                        : (Object? value) => edit(node.key, value),
                  ),
            ]),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppCard(
      children: <Widget>[
        if (tree.sections.isEmpty)
          EmptyState(message: reactorText(ReactorText.configNoSections))
        else ...<Widget>[
          for (final ConfigSection section in tree.sections) _section(section),
          FormFooter(
            cancel: RowAction(
              reactorText(ReactorText.commonCancel),
              pending.isEmpty || saving ? null : onDiscard,
            ),
            primary: RowAction(
              reactorText(ReactorText.configEditorSave),
              pending.isEmpty || saving ? null : onSave,
              loading: saving,
            ),
          ),
        ],
      ],
    );
  }
}

/// The config of the server the page belongs to, from its session.
class ConfigEditorScreen extends StatefulWidget {
  const ConfigEditorScreen({super.key});

  @override
  State<ConfigEditorScreen> createState() => _ConfigEditorScreenState();
}

class _ConfigEditorScreenState extends State<ConfigEditorScreen> {
  bool get _live => ServerScope.of(context)?.state == ConnState.live;

  /// The preset applied from here, until a setting is edited.
  String? _applied;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _applied = null;
  }

  void _report(Object? error) {
    if (!mounted) return;
    if (error == null) {
      ArcaneSonner.success(reactorText(ReactorText.configEditorApplied));
    } else {
      ArcaneSonner.error(
        reactorText(ReactorText.configEditorFailed),
        description: localizedReactorError(error),
      );
    }
  }

  Future<void> _preset(ConfigController controller, String name) async {
    if (!_live) return;
    final bool pending = controller.hasPendingEdits;
    if (pending) {
      final bool yes = await askToConfirm(
        title: reactorText(
          ReactorText.configEditorConfirmPreset,
          <String, Object?>{'preset': _presetLabel(name)},
        ),
        message: reactorText(
          ReactorText.configEditorConfirmPresetMessage,
          <String, Object?>{'count': controller.pending.length},
        ),
        confirmLabel: reactorText(ReactorText.configEditorApplyPreset),
        destructive: true,
      );
      if (!yes || !mounted || controller.disposed) return;
    }
    final Object? error = await controller.applyPreset(
      name,
      discardPending: pending,
    );
    if (error == null && mounted) setState(() => _applied = name);
    _report(error);
  }

  Future<void> _save(ConfigController controller) async {
    if (!_live) return;
    _report(await controller.apply());
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IConfigClient>()) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.configEditorLoading))
          : EmptyState(
              message: reactorText(ReactorText.configUnavailable),
              description: reactorText(ReactorText.configEditorLiveRequired),
            );
    }
    final ConfigController controller = session.config;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, ConfigController controller) {
    if (controller.loading && controller.tree.sections.isEmpty) {
      return LoadingState(label: reactorText(ReactorText.configEditorLoading));
    }
    final Object? error = controller.error;
    if (error != null && controller.tree.sections.isEmpty) {
      return ErrorState(
        title: reactorText(ReactorText.configEditorFailed),
        message: localizedReactorError(error),
        onRetry: controller.load,
      );
    }
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool writable = _live && !adminGatedDisabled(role);
    return PageStack(
      children: <Widget>[
        if (error != null &&
            reactErrorKind(error) != ReactErrorKind.pendingEdits)
          ErrorState(
            title: reactorText(ReactorText.configEditorFailed),
            message: localizedReactorError(error),
            onRetry: controller.load,
          ),
        ConfigPresetsCard(
          applied: controller.pending.isEmpty ? _applied : null,
          onPreset: writable && !controller.saving
              ? (String name) => _preset(controller, name)
              : null,
        ),
        ModeGate(
          child: ConfigSectionsCard(
            tree: controller.tree,
            pending: controller.pending,
            saving: controller.saving,
            onEdit: writable
                ? (String key, Object? value) {
                    if (_live) controller.edit(key, value);
                  }
                : null,
            onSave: writable ? () => _save(controller) : null,
            onDiscard: controller.discard,
            changedOnServer: controller.changedOnServer,
          ),
        ),
      ],
    );
  }

  String _presetLabel(String name) {
    for (final (String wire, ReactorText label) in kConfigPresets) {
      if (wire == name) return reactorText(label);
    }
    return name;
  }
}
