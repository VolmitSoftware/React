library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/action_descriptor.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/actions_controller.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

class ActionsConsoleView extends StatelessWidget {
  final List<ActionDescriptor> actions;
  final List<ActionExecution> recent;
  final String? pendingId;
  final Map<String, Map<String, Object?>> paramValues;
  final RoleInfo? role;
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
    super.key,
  });

  Widget _executeButton(ActionDescriptor action) {
    final bool allDisabled = readOnlyFor(role);
    final bool destructiveBlocked = action.destructive && adminGatedDisabled(role);
    final bool isDisabled = allDisabled || destructiveBlocked;

    final Widget button = Button.primary(
      label: 'Execute',
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
        text: 'Requires admin role',
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
      gap: 20,
      children: <Widget>[
        sectionCard(
          label: 'Actions',
          child: statGrid(<Widget>[
            for (final ActionDescriptor action in actions)
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
                    Text.heading3(action.name),
                    Text(
                      action.description,
                      color: TextColor.muted,
                      size: FontSize.sm,
                    ),
                    if (action.destructive)
                      const ArcaneStatusBadge.warning('Destructive'),
                    for (final ActionParam p in action.params)
                      KnobEditor(
                        knob: Knob(
                          key: p.key,
                          label: p.label,
                          type: p.type,
                          value: (paramValues[action.id] ??
                                  const <String, Object?>{})[p.key] ??
                              p.defaultValue,
                          options: p.options,
                        ),
                        onChanged: (Object? v) =>
                            onParamChanged?.call(action.id, p.key, v),
                      ),
                    if (pendingId == action.id)
                      ArcaneConfirmDialog(
                        title: 'Confirm ${action.name}',
                        message:
                            'This is a destructive action. Are you sure you want to proceed?',
                        destructive: true,
                        confirmText: 'Execute',
                        onConfirm: () {
                          onExecute?.call(
                            action.id,
                            _collectedParams(action),
                            true,
                          );
                          onPendingChanged?.call(null);
                        },
                        onCancel: () => onPendingChanged?.call(null),
                      ),
                    _executeButton(action),
                  ],
                ),
              ),
          ]),
        ),
        sectionCard(
          label: 'Recent Executions',
          child: dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'display': 'flex',
              'flex-direction': 'column',
              'gap': '0.5rem',
            }),
            <Widget>[
              if (recent.isEmpty)
                dom.div(
                  styles: const dom.Styles(raw: <String, String>{
                    'color': 'var(--muted-foreground)',
                    'font-size': '0.875rem',
                  }),
                  <Widget>[Component.text('No actions executed yet.')],
                ),
              for (final ActionExecution exec in recent)
                dom.div(
                  styles: const dom.Styles(raw: <String, String>{
                    'display': 'flex',
                    'gap': '0.5rem',
                    'font-size': '0.875rem',
                    'align-items': 'center',
                  }),
                  <Widget>[
                    Component.text('${exec.actionId} — ${exec.status} — ${exec.ticketId}'),
                  ],
                ),
            ],
          ),
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
                'Action queued',
                description: ctrl.recent.first.ticketId,
              );
            }
          }
        }),
        onError: (Object e) =>
            ArcaneSonner.error('Action failed', description: e.toString()),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_client == null) {
      return ReactorPage(
        title: 'Actions',
        subtitle: 'Operational commands',
        children: <Widget>[
          sectionCard(
            label: 'Actions',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[
                Component.text('Actions require a live connection.'),
              ],
            ),
          ),
        ],
      );
    }

    final ActionsController controller = _controller!;

    if (controller.loading && controller.actions.isEmpty) {
      return ReactorPage(
        title: 'Actions',
        subtitle: 'Operational commands',
        children: <Widget>[
          sectionCard(
            label: 'Actions',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[Component.text('Loading actions...')],
            ),
          ),
        ],
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;

    return ReactorPage(
      title: 'Actions',
      subtitle: 'Operational commands',
      leading: RoleBadge(role: role),
      children: <Widget>[
        ActionsConsoleView(
          actions: controller.actions,
          recent: controller.recent,
          pendingId: _pendingId,
          paramValues: _paramValues,
          role: role,
          onExecute: (String id, Map<String, Object?> params, bool confirm) {
            controller.execute(id, params, confirm);
          },
          onPendingChanged: (String? id) => setState(() => _pendingId = id),
          onParamChanged: (String actionId, String paramKey, Object? value) {
            setState(() {
              _paramValues = Map<String, Map<String, Object?>>.of(_paramValues);
              _paramValues[actionId] =
                  Map<String, Object?>.of(_paramValues[actionId] ?? <String, Object?>{});
              _paramValues[actionId]![paramKey] = value;
            });
          },
        ),
      ],
    );
  }
}
