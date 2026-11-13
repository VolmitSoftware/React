/// Actions: the server's operational commands, most used first, and the
/// runs started from this page. Destructive actions ask first, naming the
/// action and the server.
library;

import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;
import 'package:jaspr_router/jaspr_router.dart' show RouteState;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/action_descriptor.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/actions_controller.dart';
import '../state/connection_manager.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/browser.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/info_tip.dart';
import '../ui/kit/dialog.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import '../widget/knob_editor.dart';

/// The name of the server the page belongs to, for questions that name it.
String serverNameOf(BuildContext context) {
  final RouteState? route = RouteState.maybeOf(context);
  final String? id = route == null ? null : serverIdOf(route.location);
  if (id == null) return reactorText(ReactorText.commonServer);
  return FleetScope.of(context)?.labelFor(id) ?? id;
}

/// The id of an action's Run button, for links that point at one.
String actionRunId(String actionId) =>
    'action-run-${actionId.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '-')}';

/// The first sentence of [text]: an action's effect in one line.
String firstSentence(String text) {
  final int end = text.indexOf('. ');
  return end < 0 ? text.trim() : text.substring(0, end + 1).trim();
}

/// The plugin's config comment ("Configuration for Collect Garbage action.
/// Requests JVM garbage collection ...") without its opening sentence, which
/// only repeats the name; kept when nothing follows it.
String whatItDoes(String description) {
  final RegExpMatch? preamble = RegExp(r'^Configuration for [^.]+\.\s+')
      .firstMatch(description);
  return preamble == null ? description : description.substring(preamble.end);
}

/// How often this browser ran each action (any server), so the list leads
/// with the ones used most.
class ActionUsage {
  ActionUsage(this._storage);

  static const String storageKey = 'reactor.actions.used';

  final FleetStorage? _storage;

  Map<String, int> read() {
    try {
      final Object? decoded = jsonDecode(_storage?.read(storageKey) ?? '{}');
      if (decoded is! Map<String, dynamic>) return <String, int>{};
      return <String, int>{
        for (final MapEntry<String, dynamic> entry in decoded.entries)
          if (entry.value is int) entry.key: entry.value as int,
      };
    } on Object {
      return <String, int>{};
    }
  }

  void count(String actionId) {
    final FleetStorage? storage = _storage;
    if (storage == null) return;
    final Map<String, int> counts = read();
    counts[actionId] = (counts[actionId] ?? 0) + 1;
    try {
      storage.write(storageKey, jsonEncode(counts));
    } on Object {
      // Storage switched off: the order falls back to the server's.
    }
  }

  /// [actions] most used first; ties keep the server's order.
  static List<ActionDescriptor> order(
    List<ActionDescriptor> actions,
    Map<String, int> counts,
  ) {
    final List<(int, ActionDescriptor)> indexed = <(int, ActionDescriptor)>[
      for (final (int index, ActionDescriptor action) in actions.indexed)
        (index, action),
    ];
    indexed.sort(((int, ActionDescriptor) a, (int, ActionDescriptor) b) {
      final int used = (counts[b.$2.id] ?? 0).compareTo(counts[a.$2.id] ?? 0);
      return used != 0 ? used : a.$1.compareTo(b.$1);
    });
    return <ActionDescriptor>[
      for (final (int, ActionDescriptor) e in indexed) e.$2,
    ];
  }
}

/// What a run's ticket says: queued until the server reports more.
(String, BadgeTone) runState(String status) => switch (status.toLowerCase()) {
  'running' => (reactorText(ReactorText.actionsStateRunning), BadgeTone.info),
  'done' => (reactorText(ReactorText.actionsStateDone), BadgeTone.ok),
  'failed' => (reactorText(ReactorText.actionsStateFailed), BadgeTone.error),
  _ => (reactorText(ReactorText.actionsStateQueued), BadgeTone.neutral),
};

String _ago(DateTime at) {
  final int seconds = DateTime.now().difference(at).inSeconds.clamp(0, 1 << 30);
  if (seconds < 60) {
    return reactorText(ReactorText.commonSecondsAgo, <String, Object?>{
      'value': seconds,
    });
  }
  if (seconds < 3600) {
    return reactorText(ReactorText.commonMinutesAgo, <String, Object?>{
      'value': seconds ~/ 60,
    });
  }
  return reactorText(ReactorText.commonHoursAgo, <String, Object?>{
    'value': seconds ~/ 3600,
  });
}

/// The action list and the recent runs. [onRun] is null while the page may
/// not run anything (not live, or a viewer).
class ActionsView extends StatelessWidget {
  const ActionsView({
    required this.actions,
    required this.recent,
    required this.onRun,
    this.role,
    super.key,
  });

  final List<ActionDescriptor> actions;
  final List<ActionExecution> recent;
  final RoleInfo? role;
  final void Function(ActionDescriptor action)? onRun;

  RowAction _runButton(ActionDescriptor action) {
    final bool needsAdmin = action.destructive && adminGatedDisabled(role);
    final void Function(ActionDescriptor)? run = onRun;
    return RowAction(
      reactorText(ReactorText.actionsExecute),
      run == null || needsAdmin ? null : () => run(action),
      destructive: action.destructive,
      id: actionRunId(action.id),
      attributes: <String, String>{
        'aria-label':
            '${reactorText(ReactorText.actionsExecute)} ${action.name}',
        if (needsAdmin)
          'title': reactorText(ReactorText.commonRequiresAdminRole),
      },
    );
  }

  /// The name as the server sends it (wrapping, never cut), what it does
  /// behind its (i), and under it a red badge when it cannot be undone.
  Widget _name(ActionDescriptor action) =>
      dom.div(classes: 'reactor-row-name', <Widget>[
        dom.div(classes: 'reactor-row-title', <Widget>[
          dom.span(<Widget>[Component.text(action.name)]),
          if (action.description.isNotEmpty)
            InfoTip(tip: whatItDoes(action.description)),
        ]),
        if (action.destructive)
          dom.div(<Widget>[
            StatusBadge(
              reactorText(ReactorText.actionsDestructive),
              tone: BadgeTone.error,
            ),
          ]),
      ]);

  Widget _state(ActionExecution run) {
    final (String label, BadgeTone tone) = runState(run.status);
    return dom.div(classes: 'reactor-row-name', <Widget>[
      dom.div(classes: 'reactor-row-title', <Widget>[
        StatusBadge(label, tone: tone),
      ]),
      if (run.message.isNotEmpty)
        dom.p(
          classes: 'ui-caption reactor-one-line',
          attributes: <String, String>{'title': run.message},
          <Widget>[Component.text(run.message)],
        ),
    ]);
  }

  String _actionName(String id) {
    for (final ActionDescriptor action in actions) {
      if (action.id == id) return action.name;
    }
    return id;
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return Component.fragment(<Widget>[
      DataList<ActionDescriptor>(
        // The page's title names the list; only the runs get a heading.
        label: reactorText(ReactorText.actionsTitle),
        items: actions,
        emptyMessage: reactorText(ReactorText.actionsNoneAvailable),
        header: false,
        columns: <ListColumn<ActionDescriptor>>[
          ListColumn<ActionDescriptor>(
            reactorText(ReactorText.actionsColumnAction),
            _name,
            width: 'minmax(0, 1fr)',
          ),
        ],
        actions: (ActionDescriptor action) => <RowAction>[_runButton(action)],
      ),
      DataList<ActionExecution>(
        label: reactorText(ReactorText.actionsRecentRuns),
        title: reactorText(ReactorText.actionsRecentRuns),
        items: recent,
        emptyMessage: reactorText(ReactorText.actionsNoneExecuted),
        columns: <ListColumn<ActionExecution>>[
          ListColumn<ActionExecution>(
            reactorText(ReactorText.actionsColumnAction),
            (ActionExecution run) => Component.text(_actionName(run.actionId)),
            width: 'minmax(0, 1fr)',
          ),
          ListColumn<ActionExecution>(
            reactorText(ReactorText.commonState),
            _state,
            width: 'minmax(0, 16rem)',
          ),
          ListColumn<ActionExecution>(
            reactorText(ReactorText.actionsColumnStarted),
            (ActionExecution run) => Component.text(_ago(run.at)),
            numeric: true,
            width: '8rem',
          ),
        ],
      ),
    ]);
  }
}

/// The parameters of one run, in a dialog, committed on blur or Enter. For
/// an action that cannot be undone it is also the question: titled "Run X
/// on Y?", what it does, and a red Run, so one dialog asks once.
class ActionParamsDialog extends StatelessWidget {
  const ActionParamsDialog({
    required this.action,
    required this.values,
    required this.onChanged,
    required this.onRun,
    required this.onCancel,
    super.key,
  });

  final ActionDescriptor? action;
  final Map<String, Object?> values;
  final void Function(String key, Object? value) onChanged;
  final void Function() onRun;
  final void Function() onCancel;

  @override
  Widget build(BuildContext context) {
    final ActionDescriptor? shown = action;
    final bool destructive = shown?.destructive ?? false;
    return AppDialog(
      open: shown != null,
      title: shown == null
          ? ''
          : destructive
          ? reactorText(ReactorText.actionsConfirmRun, <String, Object?>{
              'action': shown.name,
              'server': serverNameOf(context),
            })
          : shown.name,
      onClose: onCancel,
      footer: DialogFooter(
        cancel: RowAction(reactorText(ReactorText.commonCancel), onCancel),
        primary: RowAction(
          reactorText(ReactorText.actionsExecute),
          onRun,
          destructive: shown?.destructive ?? false,
        ),
      ),
      children: <Widget>[
        if (shown != null && destructive && shown.description.isNotEmpty)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(firstSentence(whatItDoes(shown.description))),
          ]),
        if (shown != null)
          ArcaneFieldGroup(
            children: <Widget>[
              for (final ActionParam param in shown.params)
                KnobEditor(
                  knob: Knob(
                    key: param.key,
                    label: param.label,
                    type: param.type,
                    value: values[param.key] ?? param.defaultValue,
                    options: param.options,
                  ),
                  onChanged: (Object? value) => onChanged(param.key, value),
                ),
            ],
          ),
      ],
    );
  }
}

/// The actions of the server the page belongs to, from its session.
/// `?action=<id>` opens that action's settings, or focuses its Run button.
class ActionsScreen extends StatefulWidget {
  const ActionsScreen({super.key});

  @override
  State<ActionsScreen> createState() => _ActionsScreenState();
}

class _ActionsScreenState extends State<ActionsScreen> {
  Map<String, int>? _usage;
  ActionDescriptor? _editing;
  Map<String, Object?> _params = <String, Object?>{};

  /// The `?action=` already acted on, so it opens once per visit.
  String? _linked;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _editing = null;
    _usage = null;
    _linked = null;
  }

  ActionUsage get _usageStore =>
      ActionUsage(FleetScope.of(context)?.fleetManager.storage);

  bool get _live => ServerScope.of(context)?.state == ConnState.live;

  void _run(ActionsController controller, ActionDescriptor action) {
    if (action.params.isEmpty) {
      _confirmAndExecute(controller, action, const <String, Object?>{});
      return;
    }
    setState(() {
      _editing = action;
      _params = <String, Object?>{
        for (final ActionParam param in action.params)
          param.key: param.defaultValue,
      };
    });
  }

  void _runEdited(ActionsController controller) {
    final ActionDescriptor? action = _editing;
    final Map<String, Object?> params = Map<String, Object?>.of(_params);
    setState(() => _editing = null);
    // The parameters dialog already asked.
    if (action != null) {
      _confirmAndExecute(controller, action, params, asked: true);
    }
  }

  Future<void> _confirmAndExecute(
    ActionsController controller,
    ActionDescriptor action,
    Map<String, Object?> params, {
    bool asked = false,
  }) async {
    if (action.destructive && !asked) {
      final bool yes = await askToConfirm(
        title: reactorText(ReactorText.actionsConfirmRun, <String, Object?>{
          'action': action.name,
          'server': serverNameOf(context),
        }),
        message: firstSentence(whatItDoes(action.description)),
        confirmLabel: reactorText(ReactorText.actionsExecute),
        destructive: true,
      );
      if (!yes || !mounted || controller.disposed) return;
    }
    if (!_live) return;
    _usageStore.count(action.id);
    final Object? error = await controller.execute(
      action.id,
      params,
      action.destructive,
    );
    if (!mounted) return;
    if (error == null) {
      ArcaneSonner.success(reactorText(ReactorText.actionsQueued));
    } else {
      ArcaneSonner.error(
        reactorText(ReactorText.actionsFailed),
        description: localizedReactorError(error),
      );
    }
  }

  /// Opens the action `?action=` names, once its list is in.
  void _follow(ActionsController controller, bool canRun) {
    final RouteState? route = RouteState.maybeOf(context);
    final String? wanted = route?.queryParams['action'];
    if (wanted == null || wanted == _linked || controller.actions.isEmpty) {
      return;
    }
    _linked = wanted;
    for (final ActionDescriptor action in controller.actions) {
      if (action.id != wanted) continue;
      if (canRun && action.params.isNotEmpty) {
        afterFrame(() {
          if (mounted) _run(controller, action);
        });
      } else {
        focusById(actionRunId(action.id));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    final Widget body;
    if (session == null || !session.supports<IActionClient>()) {
      body = ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.actionsLoading))
          : EmptyState(
              message: reactorText(ReactorText.actionsUnavailable),
              description: reactorText(ReactorText.actionsLiveRequired),
            );
    } else {
      final ActionsController controller = session.actions;
      body = ListenableBuilder(
        listenable: controller,
        builder: (BuildContext context) => _body(context, controller),
      );
    }
    return AppPage(
      title: reactorText(ReactorText.actionsTitle),
      tip: reactorText(ReactorText.actionsSubtitle),
      children: <Widget>[body],
    );
  }

  Widget _body(BuildContext context, ActionsController controller) {
    if (controller.loading && controller.actions.isEmpty) {
      return LoadingState(label: reactorText(ReactorText.actionsLoading));
    }
    final Object? error = controller.error;
    if (error != null && controller.actions.isEmpty) {
      return ErrorState(
        title: reactorText(ReactorText.actionsFailed),
        message: localizedReactorError(error),
        onRetry: controller.load,
      );
    }
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool canRun = _live && !readOnlyFor(role);
    _follow(controller, canRun);
    final Map<String, int> usage = _usage ??= _usageStore.read();
    return PageStack(
      children: <Widget>[
        ActionsView(
          actions: ActionUsage.order(controller.actions, usage),
          recent: controller.recent,
          role: role,
          onRun: canRun
              ? (ActionDescriptor action) => _run(controller, action)
              : null,
        ),
        ActionParamsDialog(
          action: _editing,
          values: _params,
          onChanged: (String key, Object? value) => setState(
            () => _params = <String, Object?>{..._params, key: value},
          ),
          onRun: () => _runEdited(controller),
          onCancel: () => setState(() => _editing = null),
        ),
      ],
    );
  }
}
