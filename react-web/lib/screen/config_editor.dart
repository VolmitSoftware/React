library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/config_tree.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/config_controller.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

class ConfigEditorView extends StatelessWidget {
  final ConfigTree tree;
  final Map<String, Object?> pending;
  final void Function(String, Object?)? onEdit;
  final void Function(String)? onPreset;
  final VoidCallback? onApply;
  final bool adminGated;
  final Widget? roleBadge;

  const ConfigEditorView({
    required this.tree,
    required this.pending,
    this.onEdit,
    this.onPreset,
    this.onApply,
    this.adminGated = false,
    this.roleBadge,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return ReactorPage(
      title: reactorText(ReactorText.configEditorTitle),
      subtitle: reactorText(ReactorText.configEditorSubtitle),
      leading: roleBadge,
      actions: adminGated
          ? ArcaneTooltip(
              text: reactorText(ReactorText.commonRequiresAdminRole),
              child: Button.primary(
                label: reactorText(ReactorText.configEditorApplyChanges),
                size: ButtonSize.small,
                disabled: true,
                onPressed: null,
              ),
            )
          : Button.primary(
              label: reactorText(ReactorText.configEditorApplyChanges),
              size: ButtonSize.small,
              onPressed: onApply,
            ),
      children: <Widget>[
        sectionCard(
          label: reactorText(ReactorText.configEditorPresets),
          child: dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
                'gap': '0.5rem',
              },
            ),
            <Widget>[
              for (final (ReactorText label, String key)
                  in const <(ReactorText, String)>[
                    (ReactorText.configEditorPresetOff, 'off'),
                    (ReactorText.configEditorPresetLight, 'light'),
                    (ReactorText.configEditorPresetBalanced, 'balanced'),
                    (ReactorText.configEditorPresetHigh, 'high'),
                  ])
                adminGated
                    ? ArcaneTooltip(
                        text: reactorText(ReactorText.commonRequiresAdminRole),
                        child: Button.secondary(
                          label: reactorText(label),
                          disabled: true,
                          onPressed: null,
                        ),
                      )
                    : Button.secondary(
                        label: reactorText(label),
                        onPressed: () => onPreset?.call(key),
                      ),
            ],
          ),
        ),
        for (final ConfigSection section in tree.sections)
          sectionCard(
            label: section.name,
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.75rem',
                },
              ),
              <Widget>[
                for (final Knob node in section.nodes)
                  KnobEditor(
                    knob: node.copyWith(
                      value: pending.containsKey(node.key)
                          ? pending[node.key]
                          : node.value,
                    ),
                    disabled: adminGated,
                    onChanged: adminGated
                        ? null
                        : (Object? v) => onEdit?.call(node.key, v),
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class ConfigEditorScreen extends StatefulWidget {
  const ConfigEditorScreen({super.key});

  @override
  State<ConfigEditorScreen> createState() => _ConfigEditorScreenState();
}

class _ConfigEditorScreenState extends State<ConfigEditorScreen> {
  ConfigController? _controller;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IConfigClient? c = OperateScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _controller = ConfigController(
        c,
        onChange: () => setState(() {}),
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.configEditorFailed),
          description: e.toString(),
        ),
      );
      _controller!.load();
    }
  }

  Future<void> _applyPreset(String name) async {
    await _controller?.applyPreset(name);
    if (_controller?.error == null) {
      ArcaneSonner.success(reactorText(ReactorText.configEditorApplied));
    }
  }

  Future<void> _apply() async {
    await _controller?.apply();
    if (_controller?.error == null) {
      ArcaneSonner.success(reactorText(ReactorText.configEditorApplied));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_controller == null) {
      return ReactorPage(
        title: reactorText(ReactorText.configEditorTitle),
        subtitle: reactorText(ReactorText.configEditorSubtitle),
        children: <Widget>[
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              },
            ),
            <Widget>[
              Component.text(reactorText(ReactorText.configEditorLiveRequired)),
            ],
          ),
        ],
      );
    }

    final ConfigController controller = _controller!;

    if (controller.loading && controller.tree.sections.isEmpty) {
      return ReactorPage(
        title: reactorText(ReactorText.configEditorTitle),
        subtitle: reactorText(ReactorText.configEditorSubtitle),
        children: <Widget>[
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              },
            ),
            <Widget>[
              Component.text(reactorText(ReactorText.configEditorLoading)),
            ],
          ),
        ],
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool adminGated = adminGatedDisabled(role);

    return ConfigEditorView(
      tree: controller.tree,
      pending: controller.pending,
      adminGated: adminGated,
      roleBadge: RoleBadge(role: role),
      onEdit: adminGated ? null : controller.edit,
      onPreset: adminGated ? null : _applyPreset,
      onApply: (adminGated || !controller.dirty) ? null : _apply,
    );
  }
}
