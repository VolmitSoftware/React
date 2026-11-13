/// Tuning > Worlds, below the chart: each world's tick budget and pressure.
/// Advanced edits a world's budget, panic and release times in a sheet and
/// sends them with Save.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../model/world_settings.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../state/world_overrides_controller.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/sheet.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import '../widget/knob_editor.dart';

/// Which of a world's times an edit sets.
enum WorldBudget { budget, panic, release }

String _ms(double value) =>
    value == value.roundToDouble() ? value.toInt().toString() : '$value';

Widget pressureBadge(PressureMode mode) => switch (mode) {
  PressureMode.normal => StatusBadge(
    reactorText(ReactorText.pressureNormal),
    tone: BadgeTone.ok,
  ),
  PressureMode.pressure => StatusBadge(
    reactorText(ReactorText.pressurePressure),
    tone: BadgeTone.warning,
  ),
  PressureMode.panic => StatusBadge(
    reactorText(ReactorText.pressurePanic),
    tone: BadgeTone.error,
  ),
};

/// The worlds as rows. [onEdit] adds an Edit button per row (Advanced);
/// null leaves the list read only.
class WorldOverridesView extends StatelessWidget {
  const WorldOverridesView({
    required this.worlds,
    this.onEdit,
    this.budgets = true,
    super.key,
  });

  final List<WorldSettings> worlds;
  final void Function(String name)? onEdit;

  /// The three times per world (Advanced, where they can be edited);
  /// Simple lists each world and its state.
  final bool budgets;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final void Function(String)? edit = onEdit;
    return DataList<WorldSettings>(
      label: reactorText(ReactorText.worldOverridesTitle),
      title: reactorText(ReactorText.worldOverridesTitle),
      items: worlds,
      emptyMessage: reactorText(ReactorText.worldOverridesNoWorlds),
      columns: <ListColumn<WorldSettings>>[
        ListColumn<WorldSettings>.text(
          reactorText(ReactorText.worldOverridesColumnWorld),
          (WorldSettings world) => world.name,
          width: 'minmax(8rem, 1fr)',
        ),
        ListColumn<WorldSettings>(
          reactorText(ReactorText.commonState),
          (WorldSettings world) => pressureBadge(world.pressureMode),
          width: '7rem',
        ),
        if (budgets) ...<ListColumn<WorldSettings>>[
          ListColumn<WorldSettings>(
            reactorText(ReactorText.worldOverridesBudgetMs),
            (WorldSettings world) => Component.text(_ms(world.budgetMs)),
            numeric: true,
            width: '7rem',
          ),
          ListColumn<WorldSettings>(
            reactorText(ReactorText.worldOverridesPanicMs),
            (WorldSettings world) => Component.text(_ms(world.panicMs)),
            numeric: true,
            width: '7rem',
          ),
          ListColumn<WorldSettings>(
            reactorText(ReactorText.worldOverridesReleaseMs),
            (WorldSettings world) => Component.text(_ms(world.releaseMs)),
            numeric: true,
            width: '7rem',
          ),
        ],
      ],
      actions: edit != null
          ? (WorldSettings world) => <RowAction>[
              RowAction(
                reactorText(ReactorText.worldOverridesEdit),
                () => edit(world.name),
                attributes: <String, String>{
                  'aria-label':
                      '${reactorText(ReactorText.worldOverridesEdit)} ${world.name}',
                },
              ),
            ]
          : null,
    );
  }
}

/// One world's three times, staged as they are typed and sent together by
/// Save; Cancel drops them. A world the server changed meanwhile says so.
class WorldBudgetSheet extends StatelessWidget {
  const WorldBudgetSheet({
    required this.world,
    required this.onCancel,
    this.onStage,
    this.onSave,
    this.changedOnServer = false,
    super.key,
  });

  final WorldSettings? world;
  final void Function() onCancel;

  /// Null shows the fields disabled.
  final void Function(String name, WorldBudget which, double value)? onStage;

  /// Null while nothing is staged (or the role may not write).
  final void Function()? onSave;
  final bool changedOnServer;

  Widget _field(WorldSettings world, WorldBudget which) {
    final (String key, ReactorText label, double value) = switch (which) {
      WorldBudget.budget => (
        'budget',
        ReactorText.worldOverridesBudgetMs,
        world.budgetMs,
      ),
      WorldBudget.panic => (
        'panic',
        ReactorText.worldOverridesPanicMs,
        world.panicMs,
      ),
      WorldBudget.release => (
        'release',
        ReactorText.worldOverridesReleaseMs,
        world.releaseMs,
      ),
    };
    final void Function(String, WorldBudget, double)? stage = onStage;
    return KnobEditor(
      knob: Knob(
        key: '${world.name}-$key',
        label: reactorText(label),
        type: KnobType.doubleType,
        value: value,
      ),
      disabled: stage == null,
      onChanged: stage == null
          ? null
          : (Object? next) {
              if (next is num && next >= 0) {
                stage(world.name, which, next.toDouble());
              }
            },
    );
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final WorldSettings? shown = world;
    return Sheet(
      open: shown != null,
      title: shown?.name ?? '',
      onClose: onCancel,
      footer: DialogFooter(
        cancel: RowAction(reactorText(ReactorText.commonCancel), onCancel),
        primary: RowAction(reactorText(ReactorText.configEditorSave), onSave),
      ),
      child: shown == null
          ? const Component.fragment(<Widget>[])
          : dom.div(classes: 'ui-stack', <Widget>[
              if (changedOnServer)
                AppAlert(
                  tone: AlertTone.warning,
                  message: reactorText(ReactorText.optimizationChangedOnServer),
                ),
              ArcaneFieldGroup(
                children: <Widget>[
                  for (final WorldBudget which in WorldBudget.values)
                    _field(shown, which),
                ],
              ),
            ]),
    );
  }
}

/// The worlds of the server the page belongs to, from its session.
class WorldOverridesScreen extends StatefulWidget {
  const WorldOverridesScreen({super.key});

  @override
  State<WorldOverridesScreen> createState() => _WorldOverridesScreenState();
}

class _WorldOverridesScreenState extends State<WorldOverridesScreen> {
  String? _editing;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _editing = null;
  }

  bool get _live => ServerScope.of(context)?.state == ConnState.live;

  void _stage(
    WorldOverridesController controller,
    String name,
    WorldBudget which,
    double value,
  ) => switch (which) {
    WorldBudget.budget => controller.stageBudget(name, budgetMs: value),
    WorldBudget.panic => controller.stageBudget(name, panicMs: value),
    WorldBudget.release => controller.stageBudget(name, releaseMs: value),
  };

  Future<void> _save(WorldOverridesController controller, String name) async {
    setState(() => _editing = null);
    if (!_live) return;
    final Object? error = await controller.commitBudget(name);
    if (error != null && mounted) {
      ArcaneSonner.error(
        reactorText(ReactorText.commonUpdateFailed),
        description: localizedReactorError(error),
      );
    }
  }

  void _cancel(WorldOverridesController controller, String name) {
    controller.discardBudget(name);
    setState(() => _editing = null);
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IControlClient>()) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.commonLoadingWorlds))
          : EmptyState(
              message: reactorText(ReactorText.worldOverridesUnavailable),
              description: reactorText(ReactorText.worldOverridesLiveRequired),
            );
    }
    final WorldOverridesController controller = session.worlds;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, WorldOverridesController controller) {
    if (controller.loading && controller.worlds.isEmpty) {
      return LoadingState(label: reactorText(ReactorText.commonLoadingWorlds));
    }
    final Object? error = controller.error;
    if (error != null && controller.worlds.isEmpty) {
      return ErrorState(
        title: reactorText(ReactorText.worldOverridesUnavailable),
        message: localizedReactorError(error),
        onRetry: controller.load,
      );
    }
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool writable = _live && !readOnlyFor(role);
    final bool advanced = ModeScope.advanced(context);
    WorldSettings? editing;
    for (final WorldSettings world in controller.worlds) {
      if (world.name == _editing) editing = world;
    }
    final String? name = editing?.name;
    return PageStack(
      children: <Widget>[
        WorldOverridesView(
          worlds: controller.worlds,
          budgets: advanced,
          onEdit: advanced
              ? (String name) => setState(() => _editing = name)
              : null,
        ),
        WorldBudgetSheet(
          world: advanced ? editing : null,
          changedOnServer: name != null && controller.changedOnServer(name),
          onCancel: name == null
              ? () => setState(() => _editing = null)
              : () => _cancel(controller, name),
          onStage: writable
              ? (String world, WorldBudget which, double value) =>
                    _stage(controller, world, which, value)
              : null,
          onSave: writable && name != null && controller.hasStaged(name)
              ? () => _save(controller, name)
              : null,
        ),
      ],
    );
  }
}
