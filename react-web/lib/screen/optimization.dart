library;

import 'dart:collection' show LinkedHashMap;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/config_sheet.dart';
import '../widget/role_badge.dart';

class OptimizationGridView extends StatelessWidget {
  final List<ControlItem> items;
  final int total;
  final int enabledCount;
  final void Function(String id, bool enabled)? onToggle;
  final void Function(String id)? onConfigure;
  final void Function(bool enabled)? onSetAll;
  final bool readOnly;
  final Widget? roleBadge;
  final Widget? notice;

  const OptimizationGridView({
    required this.items,
    required this.total,
    required this.enabledCount,
    this.onToggle,
    this.onConfigure,
    this.onSetAll,
    this.readOnly = false,
    this.roleBadge,
    this.notice,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final LinkedHashMap<String, List<ControlItem>> byCategory =
        LinkedHashMap<String, List<ControlItem>>();
    for (final ControlItem item in items) {
      byCategory.putIfAbsent(item.category, () => <ControlItem>[]).add(item);
    }

    return ReactorPage(
      title: reactorText(ReactorText.optimizationTitle),
      subtitle: reactorText(
        ReactorText.optimizationEnabledCount,
        <String, Object?>{'enabled': enabledCount, 'total': total},
      ),
      leading: roleBadge,
      actions: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'align-items': 'center',
            'gap': '0.5rem',
          },
        ),
        <Widget>[
          Button.secondary(
            label: reactorText(ReactorText.optimizationEnableAll),
            size: ButtonSize.small,
            disabled: readOnly,
            onPressed: readOnly ? null : () => onSetAll?.call(true),
          ),
          Button.outline(
            label: reactorText(ReactorText.optimizationDisableAll),
            size: ButtonSize.small,
            disabled: readOnly,
            onPressed: readOnly ? null : () => onSetAll?.call(false),
          ),
        ],
      ),
      children: <Widget>[
        ?notice,
        if (items.isEmpty)
          ReactorEmptyState(
            title: 'No optimization features',
            description: 'React did not return any configurable features.',
            icon: ArcaneIcon.slidersHorizontal(size: IconSize.sm),
          ),
        for (final MapEntry<String, List<ControlItem>> entry
            in byCategory.entries)
          SectionPanel(
            label: entry.key,
            description: reactorText(ReactorText.optimizationCategoryCount, <
              String,
              Object?
            >{
              'enabled': entry.value.where((ControlItem i) => i.enabled).length,
              'total': entry.value.length,
            }),
            flush: true,
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                },
              ),
              <Widget>[
                for (final ControlItem item in entry.value)
                  _FeatureCard(
                    item: item,
                    readOnly: readOnly,
                    onToggle: onToggle,
                    onConfigure: onConfigure,
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class _FeatureCard extends StatelessWidget {
  final ControlItem item;
  final bool readOnly;
  final void Function(String id, bool enabled)? onToggle;
  final void Function(String id)? onConfigure;

  const _FeatureCard({
    required this.item,
    required this.readOnly,
    this.onToggle,
    this.onConfigure,
  });

  @override
  Widget build(BuildContext context) {
    final bool enabled = item.enabled;
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.5rem',
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
                  'flex-direction': 'column',
                  'gap': '0.2rem',
                  'min-width': '0',
                },
              ),
              <Widget>[
                dom.span(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '0.82rem',
                      'font-weight': '600',
                      'color': 'var(--foreground)',
                      'line-height': '1.25',
                    },
                  ),
                  <Widget>[Component.text(item.name)],
                ),
                dom.code(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '0.66rem',
                      'color': kReactorMuted,
                    },
                  ),
                  <Widget>[Component.text(item.id)],
                ),
              ],
            ),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'align-items': 'center',
                  'gap': '0.5rem',
                },
              ),
              <Widget>[
                enabled
                    ? reactorBadge(
                        reactorText(ReactorText.commonEnabled),
                        ReactorStatus.healthy,
                      )
                    : reactorBadge(
                        reactorText(ReactorText.commonDisabled),
                        ReactorStatus.neutral,
                      ),
                ArcaneToggleSwitch(
                  value: enabled,
                  disabled: readOnly,
                  onChanged: readOnly
                      ? null
                      : (bool value) => onToggle?.call(item.id, value),
                ),
              ],
            ),
          ],
        ),
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
            dom.span(
              styles: const dom.Styles(
                raw: <String, String>{
                  'min-width': '0',
                  'color': kReactorMuted,
                  'font-size': '0.75rem',
                  'line-height': '1.4',
                },
              ),
              <Widget>[Component.text(item.description)],
            ),
            Button.ghost(
              label: reactorText(ReactorText.optimizationConfigure),
              size: ButtonSize.small,
              disabled: readOnly,
              onPressed: readOnly ? null : () => onConfigure?.call(item.id),
            ),
          ],
        ),
      ],
    );
  }
}

class OptimizationScreen extends StatefulWidget {
  const OptimizationScreen({super.key});

  @override
  State<OptimizationScreen> createState() => _OptimizationScreenState();
}

class _OptimizationScreenState extends State<OptimizationScreen> {
  IControlClient? _client;
  ControlListController? _controller;
  bool _started = false;
  String? _selectedId;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IControlClient? c = ControlScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _controller = ControlListController(
        c,
        isTweaks: false,
        onChange: () => setState(() {}),
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.commonUpdateFailed),
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
        ReactorLoadingState(
          label: reactorText(ReactorText.optimizationLoading),
        ),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: 'Optimization unavailable',
          message: reactorText(ReactorText.optimizationLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final ControlListController controller = _controller!;

    if (controller.loading && controller.items.isEmpty) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.optimizationLoading),
        ),
      );
    }

    final Object? error = controller.error;
    if (error != null && controller.items.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.commonUpdateFailed),
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
    final bool readOnly = readOnlyFor(role) || server?.state != ConnState.live;

    ControlItem? selectedItem;
    if (_selectedId != null) {
      final int idx = controller.items.indexWhere(
        (ControlItem i) => i.id == _selectedId,
      );
      selectedItem = idx >= 0 ? controller.items[idx] : null;
    }

    final String? capturedId = _selectedId;
    final Widget? notice = error != null
        ? ReactorNotice(
            title: reactorText(ReactorText.commonUpdateFailed),
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          )
        : null;

    return Component.fragment(<Widget>[
      OptimizationGridView(
        items: controller.items,
        total: controller.items.length,
        enabledCount: controller.items
            .where((ControlItem i) => i.enabled)
            .length,
        readOnly: readOnly,
        roleBadge: RoleBadge(role: role),
        notice: notice,
        onToggle: readOnly
            ? null
            : (String id, bool enabled) {
                if (ServerScope.of(context)?.state == ConnState.live) {
                  controller.toggle(id, enabled);
                }
              },
        onSetAll: readOnly
            ? null
            : (bool enabled) {
                if (ServerScope.of(context)?.state == ConnState.live) {
                  controller.setAll(enabled);
                }
              },
        onConfigure: readOnly
            ? null
            : (String id) => setState(() => _selectedId = id),
      ),
      ConfigSheet(
        isOpen: _selectedId != null,
        item: selectedItem,
        disabled: readOnly,
        onKnobChanged: (readOnly || capturedId == null)
            ? null
            : (String key, Object? value) {
                if (ServerScope.of(context)?.state == ConnState.live) {
                  controller.setKnob(capturedId, key, value);
                }
              },
        onClose: () => setState(() => _selectedId = null),
      ),
    ]);
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.optimizationTitle),
      subtitle: reactorText(ReactorText.optimizationRuntimeControl),
      children: <Widget>[state],
    );
  }
}
