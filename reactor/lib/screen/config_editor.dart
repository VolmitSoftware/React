library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/config_tree.dart';
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
      title: 'Config Editor',
      subtitle: 'Server configuration',
      leading: roleBadge,
      actions: adminGated
          ? ArcaneTooltip(
              text: 'Requires admin role',
              child: Button.primary(
                label: 'Apply Changes',
                size: ButtonSize.small,
                disabled: true,
                onPressed: null,
              ),
            )
          : Button.primary(
              label: 'Apply Changes',
              size: ButtonSize.small,
              onPressed: onApply,
            ),
      children: <Widget>[
        sectionCard(
          label: 'Presets',
          child: dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'gap': '0.5rem',
            }),
            <Widget>[
              for (final (String label, String key) in const <(String, String)>[
                ('Off', 'off'),
                ('Light', 'light'),
                ('Balanced', 'balanced'),
                ('High', 'high'),
              ])
                adminGated
                    ? ArcaneTooltip(
                        text: 'Requires admin role',
                        child: Button.secondary(
                          label: label,
                          disabled: true,
                          onPressed: null,
                        ),
                      )
                    : Button.secondary(
                        label: label,
                        onPressed: () => onPreset?.call(key),
                      ),
            ],
          ),
        ),
        for (final ConfigSection section in tree.sections)
          sectionCard(
            label: section.name,
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
                'gap': '0.75rem',
              }),
              <Widget>[
                for (final Knob node in section.nodes)
                  KnobEditor(
                    knob: node.copyWith(
                      value: pending.containsKey(node.key)
                          ? pending[node.key]
                          : node.value,
                    ),
                    disabled: adminGated,
                    onChanged: adminGated ? null : (Object? v) => onEdit?.call(node.key, v),
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
        onError: (Object e) =>
            ArcaneSonner.error('Config failed', description: e.toString()),
      );
      _controller!.load();
    }
  }

  Future<void> _applyPreset(String name) async {
    await _controller?.applyPreset(name);
    if (_controller?.error == null) {
      ArcaneSonner.success('Configuration applied');
    }
  }

  Future<void> _apply() async {
    await _controller?.apply();
    if (_controller?.error == null) {
      ArcaneSonner.success('Configuration applied');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_controller == null) {
      return ReactorPage(
        title: 'Config Editor',
        subtitle: 'Server configuration',
        children: <Widget>[
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'color': 'var(--muted-foreground)',
              'font-size': '0.875rem',
            }),
            <Widget>[
              Component.text('Config editing requires a live connection.'),
            ],
          ),
        ],
      );
    }

    final ConfigController controller = _controller!;

    if (controller.loading && controller.tree.sections.isEmpty) {
      return ReactorPage(
        title: 'Config Editor',
        subtitle: 'Server configuration',
        children: <Widget>[
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'color': 'var(--muted-foreground)',
              'font-size': '0.875rem',
            }),
            <Widget>[Component.text('Loading configuration...')],
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
