library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/role_info.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../state/world_overrides_controller.dart';
import '../ui/reactor_ui.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

class WorldOverridesView extends StatelessWidget {
  final List<WorldSettings> worlds;
  final void Function(String name, {double? budgetMs, double? panicMs, double? releaseMs})? onSetBudget;
  final bool readOnly;
  final Widget? roleBadge;

  const WorldOverridesView({
    required this.worlds,
    this.onSetBudget,
    this.readOnly = false,
    this.roleBadge,
    super.key,
  });

  Widget _pressureBadge(PressureMode mode) {
    return switch (mode) {
      PressureMode.normal => const ArcaneStatusBadge.success(
          'NORMAL',
          showPulse: false,
        ),
      PressureMode.pressure => const ArcaneStatusBadge.warning('PRESSURE'),
      PressureMode.panic => const ArcaneStatusBadge.error('PANIC'),
    };
  }

  @override
  Widget build(BuildContext context) {
    if (worlds.isEmpty) {
      return ReactorPage(
        title: 'World Overrides',
        subtitle: 'Per-world tick budgets',
        leading: roleBadge,
        children: <Widget>[
          ArcaneEmptyState.noData(
            title: 'No worlds',
            description: 'No worlds reported by the server.',
          ),
        ],
      );
    }
    return ReactorPage(
      title: 'World Overrides',
      subtitle: 'Per-world tick budgets',
      leading: roleBadge,
      children: <Widget>[
        for (final WorldSettings world in worlds)
          Card.elevated(
            fillWidth: true,
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'padding': '0.75rem',
                'display': 'flex',
                'flex-direction': 'column',
                'gap': '0.5rem',
              }),
              <Widget>[
                Text.heading3(world.name),
                _pressureBadge(world.pressureMode),
                TextInput(
                  label: 'Budget (ms)',
                  type: TextInputType.number,
                  value: world.budgetMs.toString(),
                  disabled: readOnly,
                  onChange: readOnly
                      ? null
                      : (String s) {
                          final double? v = double.tryParse(s.trim());
                          if (v != null) onSetBudget?.call(world.name, budgetMs: v);
                        },
                ),
                TextInput(
                  label: 'Panic (ms)',
                  type: TextInputType.number,
                  value: world.panicMs.toString(),
                  disabled: readOnly,
                  onChange: readOnly
                      ? null
                      : (String s) {
                          final double? v = double.tryParse(s.trim());
                          if (v != null) onSetBudget?.call(world.name, panicMs: v);
                        },
                ),
                TextInput(
                  label: 'Release (ms)',
                  type: TextInputType.number,
                  value: world.releaseMs.toString(),
                  disabled: readOnly,
                  onChange: readOnly
                      ? null
                      : (String s) {
                          final double? v = double.tryParse(s.trim());
                          if (v != null) onSetBudget?.call(world.name, releaseMs: v);
                        },
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class WorldOverridesScreen extends StatefulWidget {
  const WorldOverridesScreen({super.key});

  @override
  State<WorldOverridesScreen> createState() => _WorldOverridesScreenState();
}

class _WorldOverridesScreenState extends State<WorldOverridesScreen> {
  IControlClient? _client;
  WorldOverridesController? _controller;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IControlClient? c = ControlScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _controller = WorldOverridesController(
        c,
        onChange: () => setState(() {}),
        onError: (Object e) =>
            ArcaneSonner.error('Update failed', description: e.toString()),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_client == null) {
      return ReactorPage(
        title: 'World Overrides',
        subtitle: 'Per-world tick budgets',
        children: <Widget>[
          sectionCard(
            label: 'Per-World Overrides',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[
                Component.text(
                  'Per-world overrides require a live connection.',
                ),
              ],
            ),
          ),
        ],
      );
    }

    final WorldOverridesController controller = _controller!;

    if (controller.loading && controller.worlds.isEmpty) {
      return ReactorPage(
        title: 'World Overrides',
        subtitle: 'Per-world tick budgets',
        children: <Widget>[
          sectionCard(
            label: 'Per-World Overrides',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[Component.text('Loading worlds...')],
            ),
          ),
        ],
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool readOnly = readOnlyFor(role);

    return WorldOverridesView(
      worlds: controller.worlds,
      readOnly: readOnly,
      roleBadge: RoleBadge(role: role),
      onSetBudget: readOnly ? null : controller.setBudget,
    );
  }
}
