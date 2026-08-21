library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/action_descriptor.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/actions_controller.dart';
import '../state/connection_manager.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';

class ActionsConsoleView extends StatelessWidget {
  final List<ActionDescriptor> actions;
  final List<ActionExecution> recent;
  final String? pendingId;
  final Map<String, Map<String, Object?>> paramValues;
  final RoleInfo? role;
  final bool connectionReadOnly;
  final void Function(String id, Map<String, Object?> params, bool confirm)?
  onExecute;
  final void Function(String? id)? onPendingChanged;
  final void Function(String actionId, String paramKey, Object? value)?
  onParamChanged;

  const ActionsConsoleView({
    required this.actions,
    required this.recent,
    required this.pendingId,
    required this.paramValues,
    required this.onExecute,
    required this.onPendingChanged,
    required this.onParamChanged,
    this.role,
    this.connectionReadOnly = false,
    super.key,
  });

  Widget _executeButton(ActionDescriptor action) {
    final bool allDisabled = connectionReadOnly || readOnlyFor(role);
    final bool destructiveBlocked =
        action.destructive && adminGatedDisabled(role);
    final bool isDisabled = allDisabled || destructiveBlocked;

    final Widget button = Button.primary(
      label: reactorText(ReactorText.actionsExecute),
      disabled: isDisabled,
      onPressed: isDisabled
          ? null
          : () {
              if (action.destructive) {
                onPendingChanged?.call(action.id);
              } else {
                onExecute?.call(action.id, _collectedParams(action), false);
              }
            },
    );

    if (destructiveBlocked) {
      return ArcaneTooltip(
        text: reactorText(ReactorText.commonRequiresAdminRole),
        child: button,
      );
    }
    return button;
  }

  Map<String, Object?> _collectedParams(ActionDescriptor action) {
    final Map<String, Object?> base = <String, Object?>{};
    for (final ActionParam p in action.params) {
      base[p.key] =
          (paramValues[action.id] ?? const <String, Object?>{})[p.key] ??
          p.defaultValue;
    }
    return base;
  }

  @override
  Widget build(BuildContext context) {
    return Collection(
      gap: 0,
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.actionsTitle),
          flush: true,
          child: actions.isEmpty
              ? ReactorEmptyState(
                  title: 'No actions available',
                  description:
                      'React did not return any executable operations.',
                  icon: ArcaneIcon.play(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                    },
                  ),
                  <Widget>[
                    for (final ActionDescriptor action in actions)
                      _actionRow(action),
                  ],
                ),
        ),
        SectionPanel(
          label: reactorText(ReactorText.actionsRecentExecutions),
          flush: true,
          child: recent.isEmpty
              ? ReactorEmptyState(
                  title: reactorText(ReactorText.actionsNoneExecuted),
                  description:
                      'Executed operations and their tickets will appear here.',
                  icon: ArcaneIcon.history(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                    },
                  ),
                  <Widget>[
                    for (final ActionExecution execution in recent)
                      _executionRow(execution),
                  ],
                ),
        ),
      ],
    );
  }

  Widget _actionRow(ActionDescriptor action) {
    return dom.section(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.55rem',
          'padding': '0.65rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'flex-start',
              'justify-content': 'space-between',
              'gap': '0.75rem',
            },
          ),
          <Widget>[
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'min-width': '0',
                  'flex-direction': 'column',
                  'gap': '0.2rem',
                },
              ),
              <Widget>[
                dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'align-items': 'center',
                      'gap': '0.5rem',
                    },
                  ),
                  <Widget>[
                    dom.strong(
                      styles: const dom.Styles(
                        raw: <String, String>{'font-size': '0.82rem'},
                      ),
                      <Widget>[Component.text(action.name)],
                    ),
                    if (action.destructive)
                      reactorBadge(
                        reactorText(ReactorText.actionsDestructive),
                        ReactorStatus.warning,
                      ),
                  ],
                ),
                dom.code(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'color': kReactorMuted,
                      'font-size': '0.66rem',
                    },
                  ),
                  <Widget>[Component.text(action.id)],
                ),
                dom.span(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'color': kReactorMuted,
                      'font-size': '0.75rem',
                      'line-height': '1.4',
                    },
                  ),
                  <Widget>[Component.text(action.description)],
                ),
              ],
            ),
            _executeButton(action),
          ],
        ),
        if (action.params.isNotEmpty)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'grid',
                'grid-template-columns': 'repeat(auto-fit, minmax(200px, 1fr))',
                'gap': '0.65rem',
              },
            ),
            <Widget>[
              for (final ActionParam param in action.params)
                KnobEditor(
                  knob: Knob(
                    key: param.key,
                    label: param.label,
                    type: param.type,
                    value:
                        (paramValues[action.id] ??
                            const <String, Object?>{})[param.key] ??
                        param.defaultValue,
                    options: param.options,
                  ),
                  disabled: connectionReadOnly || readOnlyFor(role),
                  onChanged: connectionReadOnly || readOnlyFor(role)
                      ? null
                      : (Object? value) =>
                            onParamChanged?.call(action.id, param.key, value),
                ),
            ],
          ),
        if (pendingId == action.id && !connectionReadOnly && !readOnlyFor(role))
          ArcaneConfirmDialog(
            title: reactorText(
              ReactorText.actionsConfirmTitle,
              <String, Object?>{'action': action.name},
            ),
            message: reactorText(ReactorText.actionsConfirmDestructive),
            destructive: true,
            confirmText: reactorText(ReactorText.actionsExecute),
            onConfirm: () {
              onExecute?.call(action.id, _collectedParams(action), true);
              onPendingChanged?.call(null);
            },
            onCancel: () => onPendingChanged?.call(null),
          ),
      ],
    );
  }

  Widget _executionRow(ActionExecution execution) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'padding': '0.5rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
          'font-family': 'var(--font-mono)',
          'font-size': '0.72rem',
        },
      ),
      <Widget>[
        Component.text(
          reactorText(ReactorText.actionsExecutionSummary, <String, Object?>{
            'actionId': execution.actionId,
            'status': execution.status,
            'ticketId': execution.ticketId,
          }),
        ),
      ],
    );
  }
}

class ActionsScreen extends StatefulWidget {
  const ActionsScreen({super.key});

  @override
  State<ActionsScreen> createState() => _ActionsScreenState();
}

class _ActionsScreenState extends State<ActionsScreen> {
  IActionClient? _client;
  ActionsController? _controller;
  bool _started = false;
  String? _pendingId;
  Map<String, Map<String, Object?>> _paramValues =
      <String, Map<String, Object?>>{};
  int _recentCount = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IActionClient? c = OperateScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _controller = ActionsController(
        c,
        onChange: () => setState(() {
          final ActionsController? ctrl = _controller;
          if (ctrl != null && ctrl.recent.length > _recentCount) {
            _recentCount = ctrl.recent.length;
            if (ctrl.recent.isNotEmpty) {
              ArcaneSonner.success(
                reactorText(ReactorText.actionsQueued),
                description: ctrl.recent.first.ticketId,
              );
            }
          }
        }),
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.actionsFailed),
          description: e.toString(),
        ),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _client == null) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.actionsLoading)),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: 'Actions unavailable',
          message: reactorText(ReactorText.actionsLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final ActionsController controller = _controller!;

    if (controller.loading && controller.actions.isEmpty) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.actionsLoading)),
      );
    }

    final Object? error = controller.error;
    if (error != null && controller.actions.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.actionsFailed),
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
    final bool live = server?.state == ConnState.live;

    return ReactorPage(
      title: reactorText(ReactorText.actionsTitle),
      subtitle: reactorText(ReactorText.actionsSubtitle),
      leading: RoleBadge(role: role),
      children: <Widget>[
        if (error != null)
          ReactorNotice(
            title: reactorText(ReactorText.actionsFailed),
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          ),
        ActionsConsoleView(
          actions: controller.actions,
          recent: controller.recent,
          pendingId: _pendingId,
          paramValues: _paramValues,
          role: role,
          connectionReadOnly: !live,
          onExecute: !live
              ? null
              : (String id, Map<String, Object?> params, bool confirm) {
                  if (ServerScope.of(context)?.state == ConnState.live) {
                    controller.execute(id, params, confirm);
                  }
                },
          onPendingChanged: !live
              ? null
              : (String? id) => setState(() => _pendingId = id),
          onParamChanged: !live
              ? null
              : (String actionId, String paramKey, Object? value) {
                  if (ServerScope.of(context)?.state != ConnState.live) return;
                  setState(() {
                    _paramValues = Map<String, Map<String, Object?>>.of(
                      _paramValues,
                    );
                    _paramValues[actionId] = Map<String, Object?>.of(
                      _paramValues[actionId] ?? <String, Object?>{},
                    );
                    _paramValues[actionId]![paramKey] = value;
                  });
                },
        ),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.actionsTitle),
      subtitle: reactorText(ReactorText.actionsSubtitle),
      children: <Widget>[state],
    );
  }
}
