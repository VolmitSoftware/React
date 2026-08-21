library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../model/config_tree.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/config_controller.dart';
import '../state/connection_manager.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';

class ConfigEditorView extends StatelessWidget {
  final ConfigTree tree;
  final Map<String, Object?> pending;
  final void Function(String, Object?)? onEdit;
  final void Function(String)? onPreset;
  final VoidCallback? onApply;
  final bool adminGated;
  final Widget? roleBadge;
  final Widget? notice;
  final bool saving;

  const ConfigEditorView({
    required this.tree,
    required this.pending,
    this.onEdit,
    this.onPreset,
    this.onApply,
    this.adminGated = false,
    this.roleBadge,
    this.notice,
    this.saving = false,
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
                label: saving
                    ? 'Applying…'
                    : reactorText(ReactorText.configEditorApplyChanges),
                size: ButtonSize.small,
                disabled: true,
                onPressed: null,
              ),
            )
          : Button.primary(
              label: saving
                  ? 'Applying…'
                  : reactorText(ReactorText.configEditorApplyChanges),
              size: ButtonSize.small,
              disabled: saving,
              onPressed: saving ? null : onApply,
            ),
      children: <Widget>[
        ?notice,
        SectionPanel(
          label: reactorText(ReactorText.configEditorPresets),
          child: dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'align-items': 'center',
                'gap': '0.35rem',
                'flex-wrap': 'wrap',
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
                        size: ButtonSize.small,
                        onPressed: () => onPreset?.call(key),
                      ),
            ],
          ),
        ),
        if (tree.sections.isEmpty)
          ReactorEmptyState(
            title: 'No configuration sections',
            description: 'React returned an empty configuration tree.',
            icon: ArcaneIcon.braces(size: IconSize.sm),
          ),
        for (final ConfigSection section in tree.sections)
          SectionPanel(
            label: section.name,
            flush: true,
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'grid',
                  'grid-template-columns':
                      'repeat(auto-fit, minmax(220px, 1fr))',
                  'gap': '0.75rem',
                  'padding': '0.7rem 0.75rem',
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
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _controller == null) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.configEditorLoading),
        ),
      );
    }
    if (_controller == null) {
      return _statePage(
        ReactorNotice(
          title: 'Configuration unavailable',
          message: reactorText(ReactorText.configEditorLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final ConfigController controller = _controller!;

    if (controller.loading && controller.tree.sections.isEmpty) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.configEditorLoading),
        ),
      );
    }

    final Object? error = controller.error;
    if (error != null && controller.tree.sections.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.configEditorFailed),
          message: error.toString(),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: 'Retry',
            size: ButtonSize.small,
            onPressed: controller.load,
          ),
        ),
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool adminGated = adminGatedDisabled(role);
    final Widget? notice = error != null
        ? ReactorNotice(
            title: reactorText(ReactorText.configEditorFailed),
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Reload',
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          )
        : controller.saving
        ? const ReactorNotice(
            title: 'Applying configuration',
            message: 'Waiting for React to confirm the updated values.',
            status: ReactorStatus.info,
          )
        : null;

    return ConfigEditorView(
      tree: controller.tree,
      pending: controller.pending,
      adminGated: adminGated,
      roleBadge: RoleBadge(role: role),
      notice: notice,
      saving: controller.saving,
      onEdit: adminGated ? null : controller.edit,
      onPreset: adminGated ? null : _applyPreset,
      onApply: (adminGated || !controller.dirty) ? null : _apply,
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.configEditorTitle),
      subtitle: reactorText(ReactorText.configEditorSubtitle),
      children: <Widget>[state],
    );
  }
}
