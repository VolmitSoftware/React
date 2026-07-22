library;

import 'dart:collection' show LinkedHashMap;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/config_sheet.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

class OptimizationGridView extends StatelessWidget {
  final List<ControlItem> items;
  final int total;
  final int enabledCount;
  final void Function(String id, bool enabled)? onToggle;
  final void Function(String id)? onConfigure;
  final void Function(bool enabled)? onSetAll;
  final bool readOnly;
  final Widget? roleBadge;

  const OptimizationGridView({
    required this.items,
    required this.total,
    required this.enabledCount,
    this.onToggle,
    this.onConfigure,
    this.onSetAll,
    this.readOnly = false,
    this.roleBadge,
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
        for (final MapEntry<String, List<ControlItem>> entry
            in byCategory.entries)
          sectionCard(
            label: entry.key,
            description: reactorText(ReactorText.optimizationCategoryCount, <
              String,
              Object?
            >{
              'enabled': entry.value.where((ControlItem i) => i.enabled).length,
              'total': entry.value.length,
            }),
            child: reactorGrid(
              minWidth: '260px',
              children: <Widget>[
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
    return Card.flat(
      fillWidth: true,
      padding: '0',
      borderRadius: kReactorRadius,
      child: dom.div(
        styles: dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '0.75rem',
            'padding': '0.85rem 0.95rem',
            'overflow': 'hidden',
            'border-radius': kReactorRadius,
            'box-shadow': enabled ? 'inset 3px 0 0 $kReactorSuccess' : 'none',
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
                    'gap': '0.3rem',
                    'min-width': '0',
                  },
                ),
                <Widget>[
                  dom.span(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'font-size': '0.9rem',
                        'font-weight': '600',
                        'color': 'var(--foreground)',
                        'line-height': '1.25',
                      },
                    ),
                    <Widget>[Component.text(item.name)],
                  ),
                  dom.span(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'font-size': '0.8rem',
                        'color': kReactorMuted,
                        'line-height': '1.4',
                        'display': '-webkit-box',
                        '-webkit-line-clamp': '2',
                        '-webkit-box-orient': 'vertical',
                        'overflow': 'hidden',
                      },
                    ),
                    <Widget>[Component.text(item.description)],
                  ),
                ],
              ),
              ArcaneToggleSwitch(
                value: enabled,
                disabled: readOnly,
                onChanged: readOnly
                    ? null
                    : (bool b) => onToggle?.call(item.id, b),
              ),
            ],
          ),
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'flex',
                'justify-content': 'flex-end',
                'border-top': '1px solid $kReactorHairline',
                'padding-top': '0.6rem',
              },
            ),
            <Widget>[
              Button.ghost(
                label: reactorText(ReactorText.optimizationConfigure),
                size: ButtonSize.small,
                disabled: readOnly,
                onPressed: readOnly ? null : () => onConfigure?.call(item.id),
              ),
            ],
          ),
        ],
      ),
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
    if (_client == null) {
      return ReactorPage(
        title: reactorText(ReactorText.optimizationTitle),
        subtitle: reactorText(ReactorText.optimizationRuntimeControl),
        children: <Widget>[
          sectionCard(
            label: reactorText(ReactorText.optimizationFeatureControl),
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.875rem',
                },
              ),
              <Widget>[
                Component.text(
                  reactorText(ReactorText.optimizationLiveRequired),
                ),
              ],
            ),
          ),
        ],
      );
    }

    final ControlListController controller = _controller!;

    if (controller.loading && controller.items.isEmpty) {
      return ReactorPage(
        title: reactorText(ReactorText.optimizationTitle),
        subtitle: reactorText(ReactorText.optimizationRuntimeControl),
        children: <Widget>[
          sectionCard(
            label: reactorText(ReactorText.optimizationFeatureControl),
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.875rem',
                },
              ),
              <Widget>[
                Component.text(reactorText(ReactorText.optimizationLoading)),
              ],
            ),
          ),
        ],
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool readOnly = readOnlyFor(role);

    ControlItem? selectedItem;
    if (_selectedId != null) {
      final int idx = controller.items.indexWhere(
        (ControlItem i) => i.id == _selectedId,
      );
      selectedItem = idx >= 0 ? controller.items[idx] : null;
    }

    final String? capturedId = _selectedId;

    return Component.fragment(<Widget>[
      OptimizationGridView(
        items: controller.items,
        total: controller.items.length,
        enabledCount: controller.items
            .where((ControlItem i) => i.enabled)
            .length,
        readOnly: readOnly,
        roleBadge: RoleBadge(role: role),
        onToggle: readOnly ? null : controller.toggle,
        onSetAll: readOnly ? null : controller.setAll,
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
            : (String k, Object? v) => controller.setKnob(capturedId, k, v),
        onClose: () => setState(() => _selectedId = null),
      ),
    ]);
  }
}
