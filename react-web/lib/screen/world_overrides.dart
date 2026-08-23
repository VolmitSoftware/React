library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/role_info.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/world_overrides_controller.dart';
import '../ui/reactor_ui.dart';
import '../widget/role_badge.dart';

class WorldOverridesView extends StatelessWidget {
  final List<WorldSettings> worlds;
  final void Function(
    String name, {
    double? budgetMs,
    double? panicMs,
    double? releaseMs,
  })?
  onSetBudget;
  final bool readOnly;
  final Widget? roleBadge;
  final Widget? notice;

  const WorldOverridesView({
    required this.worlds,
    this.onSetBudget,
    this.readOnly = false,
    this.roleBadge,
    this.notice,
    super.key,
  });

  Widget _pressureBadge(PressureMode mode) {
    return switch (mode) {
      PressureMode.normal => reactorBadge(
        reactorText(ReactorText.pressureNormal),
        ReactorStatus.healthy,
      ),
      PressureMode.pressure => reactorBadge(
        reactorText(ReactorText.pressurePressure),
        ReactorStatus.warning,
      ),
      PressureMode.panic => reactorBadge(
        reactorText(ReactorText.pressurePanic),
        ReactorStatus.critical,
      ),
    };
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    if (worlds.isEmpty) {
      return ReactorPage(
        title: reactorText(ReactorText.worldOverridesTitle),
        subtitle: reactorText(ReactorText.worldOverridesSubtitle),
        leading: roleBadge,
        children: <Widget>[
          ?notice,
          ReactorEmptyState(
            title: reactorText(ReactorText.worldOverridesNoWorlds),
            description: reactorText(
              ReactorText.worldOverridesNoWorldsDescription,
            ),
            icon: ArcaneIcon.globe(size: IconSize.sm),
          ),
        ],
      );
    }
    return ReactorPage(
      title: reactorText(ReactorText.worldOverridesTitle),
      subtitle: reactorText(ReactorText.worldOverridesSubtitle),
      leading: roleBadge,
      children: <Widget>[
        ?notice,
        SectionPanel(
          label: reactorText(ReactorText.worldOverridesSection),
          flush: true,
          child: dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'flex-direction': 'column',
              },
            ),
            <Widget>[
              for (final WorldSettings world in worlds) _worldRow(world),
            ],
          ),
        ),
      ],
    );
  }

  Widget _worldRow(WorldSettings world) {
    return dom.section(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.65rem',
          'padding': '0.7rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'justify-content': 'space-between',
              'gap': '0.75rem',
            },
          ),
          <Widget>[
            dom.strong(
              styles: const dom.Styles(
                raw: <String, String>{'font-size': '0.82rem'},
              ),
              <Widget>[Component.text(world.name)],
            ),
            _pressureBadge(world.pressureMode),
          ],
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'grid',
              'grid-template-columns': 'repeat(auto-fit, minmax(180px, 1fr))',
              'gap': '0.65rem',
            },
          ),
          <Widget>[
            _numberField(
              label: reactorText(ReactorText.worldOverridesBudgetMs),
              value: world.budgetMs,
              onValue: (double value) =>
                  onSetBudget?.call(world.name, budgetMs: value),
            ),
            _numberField(
              label: reactorText(ReactorText.worldOverridesPanicMs),
              value: world.panicMs,
              onValue: (double value) =>
                  onSetBudget?.call(world.name, panicMs: value),
            ),
            _numberField(
              label: reactorText(ReactorText.worldOverridesReleaseMs),
              value: world.releaseMs,
              onValue: (double value) =>
                  onSetBudget?.call(world.name, releaseMs: value),
            ),
          ],
        ),
      ],
    );
  }

  Widget _numberField({
    required String label,
    required double value,
    required void Function(double value) onValue,
  }) {
    return TextInput(
      label: label,
      type: TextInputType.number,
      value: value.toString(),
      disabled: readOnly,
      onChange: readOnly
          ? null
          : (String input) {
              final double? parsed = double.tryParse(input.trim());
              if (parsed != null) onValue(parsed);
            },
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
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.commonUpdateFailed),
          description: localizedReactorError(e),
        ),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _client == null) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.commonLoadingWorlds),
        ),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.worldOverridesUnavailable),
          message: reactorText(ReactorText.worldOverridesLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final WorldOverridesController controller = _controller!;

    if (controller.loading && controller.worlds.isEmpty) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.commonLoadingWorlds),
        ),
      );
    }

    final Object? error = controller.error;
    if (error != null && controller.worlds.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.commonUpdateFailed),
          message: localizedReactorError(error),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
            size: ButtonSize.small,
            onPressed: controller.load,
          ),
        ),
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool readOnly = readOnlyFor(role) || server?.state != ConnState.live;

    final Widget? notice = error != null
        ? ReactorNotice(
            title: reactorText(ReactorText.commonUpdateFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          )
        : null;
    return WorldOverridesView(
      worlds: controller.worlds,
      readOnly: readOnly,
      roleBadge: RoleBadge(role: role),
      notice: notice,
      onSetBudget: readOnly
          ? null
          : (
              String name, {
              double? budgetMs,
              double? panicMs,
              double? releaseMs,
            }) {
              if (ServerScope.of(context)?.state != ConnState.live) return;
              controller.setBudget(
                name,
                budgetMs: budgetMs,
                panicMs: panicMs,
                releaseMs: releaseMs,
              );
            },
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.worldOverridesTitle),
      subtitle: reactorText(ReactorText.worldOverridesSubtitle),
      children: <Widget>[state],
    );
  }
}
