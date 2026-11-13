/// Tuning > Features and Tuning > Tweaks: one searchable list per kind with
/// a switch per row and what the item does behind its (i). Simple groups
/// the features by category, every group closed, so the list opens short;
/// Advanced keeps one flat list with a category filter ("Governors" picks
/// the adaptive load governors among the features) and a Configure sheet
/// for each item's settings. Switches write at once; settings in the sheet
/// are staged and sent together by Save.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/control_item.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../model/knob.dart';
import '../state/control_list_controller.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/density.dart';
import '../ui/kit/info_tip.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/sheet.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import '../widget/knob_editor.dart';
import 'actions.dart' show serverNameOf, whatItDoes;
import 'form_parts.dart';

/// The features that govern load adaptively; the "Governors" filter.
const Set<String> kGovernorFeatureIds = <String>{
  'dynamic-view-distance',
  'dynamic-activation-range',
  'activation-range-governor',
  'tracker-range-governor',
  'random-tick-governor',
  'pathfinder-budget',
  'per-world-tick-budget',
  'afk-view-shedding',
  'adaptive-entity-sleep',
  'incident-mode',
  'feature-trinity-incident-mode',
  'circuit-manager',
  'feature-adapt-runtime-surge-guard',
  'feature-iris-terrain-surge-guard',
};

/// The category filter value that picks the governors.
const String governorsFilter = '#governors';

/// [items] that contain [query] (name, id or description, any case) and
/// sit in [category] (null for all, [governorsFilter] for the governors).
List<ControlItem> filterControlItems(
  List<ControlItem> items, {
  String query = '',
  String? category,
}) {
  final String needle = query.trim().toLowerCase();
  return <ControlItem>[
    for (final ControlItem item in items)
      if ((category == null ||
              (category == governorsFilter
                  ? kGovernorFeatureIds.contains(item.id)
                  : item.category == category)) &&
          (needle.isEmpty ||
              item.name.toLowerCase().contains(needle) ||
              item.id.toLowerCase().contains(needle) ||
              item.description.toLowerCase().contains(needle)))
        item,
  ];
}

/// The list of features or tweaks: a toolbar with search and the category
/// filter, then a row per item. [onToggle] null shows the switches
/// disabled; [onConfigure] null hides Configure (Simple, or no settings).
class ControlListView extends StatelessWidget {
  const ControlListView({
    required this.items,
    required this.itemLabel,
    required this.emptyMessage,
    this.query = '',
    this.category,
    this.governors = false,
    this.grouped = false,
    this.onQuery,
    this.onCategory,
    this.onToggle,
    this.onConfigure,
    this.onSetAll,
    this.changedOnServer,
    super.key,
  });

  final List<ControlItem> items;

  /// The name column's heading ("Feature", "Tweak").
  final String itemLabel;

  /// Shown when the server has none.
  final String emptyMessage;
  final String query;
  final String? category;

  /// Offers the "Governors" filter.
  final bool governors;

  /// Groups by category, each group closed until opened or matched by the
  /// search (Simple), instead of the flat list with a category filter.
  final bool grouped;
  final void Function(String query)? onQuery;
  final void Function(String? category)? onCategory;
  final void Function(String id, bool enabled)? onToggle;
  final void Function(String id)? onConfigure;

  /// Turns every shown item on or off.
  final void Function(bool enabled, List<ControlItem> shown)? onSetAll;

  /// Whether the server changed a setting of the item while it was being
  /// edited here.
  final bool Function(ControlItem item)? changedOnServer;

  List<String> get _categories {
    final List<String> names = <String>{
      for (final ControlItem item in items) item.category,
    }.toList()..sort();
    return names;
  }

  /// The name as the server sends it (wrapping, never cut) with what the
  /// item does behind its (i).
  Widget _name(ControlItem item) =>
      dom.div(classes: 'reactor-row-title', <Widget>[
        dom.span(<Widget>[Component.text(item.name)]),
        if (item.description.isNotEmpty)
          InfoTip(tip: whatItDoes(item.description)),
        if (changedOnServer?.call(item) ?? false)
          StatusBadge(
            reactorText(ReactorText.optimizationChangedOnServer),
            tone: BadgeTone.warning,
          ),
      ]);

  Widget _switch(BuildContext context, ControlItem item) {
    final void Function(String, bool)? toggle = onToggle;
    return ArcaneToggleSwitch(
      value: item.enabled,
      ariaLabel: item.name,
      size: controlSize(Density.dense),
      disabled: toggle == null,
      onChanged: toggle == null ? null : (bool on) => toggle(item.id, on),
    );
  }

  ActionRow _toolbar(List<ControlItem> shown) {
    final void Function(bool, List<ControlItem>)? setAll = onSetAll;
    return ActionRow(
      dense: true,
      label: itemLabel,
      leading: <Widget>[
        dom.div(classes: 'reactor-grow', <Widget>[
          TextInput(
            type: TextInputType.search,
            size: ComponentSize.sm,
            value: query,
            placeholder: reactorText(ReactorText.optimizationSearch),
            attributes: <String, String>{
              'aria-label': reactorText(ReactorText.optimizationSearch),
            },
            fullWidth: true,
            onChanged: onQuery,
          ),
        ]),
        if (!grouped)
          FilterSelect(
            id: 'control-category',
            label: reactorText(ReactorText.optimizationCategory),
            value: category ?? '',
            options: <ArcaneSelectOption>[
              ArcaneSelectOption(
                value: '',
                label: reactorText(ReactorText.optimizationAllCategories),
              ),
              if (governors)
                ArcaneSelectOption(
                  value: governorsFilter,
                  label: reactorText(ReactorText.governorsTitle),
                ),
              for (final String name in _categories)
                ArcaneSelectOption(value: name, label: name),
            ],
            onChanged: (String picked) =>
                onCategory?.call(picked.isEmpty ? null : picked),
          ),
      ],
      more: <AppMenuEntry>[
        AppMenuEntry(
          reactorText(ReactorText.optimizationEnableAll),
          setAll == null || shown.isEmpty ? null : () => setAll(true, shown),
        ),
        AppMenuEntry(
          reactorText(ReactorText.optimizationDisableAll),
          setAll == null || shown.isEmpty ? null : () => setAll(false, shown),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    if (items.isEmpty) {
      return AppCard(children: <Widget>[EmptyState(message: emptyMessage)]);
    }
    final List<ControlItem> shown = filterControlItems(
      items,
      query: query,
      category: category,
    );
    return AppCard(
      actions: _toolbar(shown),
      children: <Widget>[
        if (grouped && shown.isNotEmpty)
          _groups(context, shown)
        else
          _list(context, shown),
      ],
    );
  }

  /// [shown] as rows: the name and its (i), the switch beside it, and
  /// Configure in Advanced.
  Widget _list(BuildContext context, List<ControlItem> shown) {
    final void Function(String)? configure = onConfigure;
    return DataList<ControlItem>(
      label: itemLabel,
      items: shown,
      emptyMessage: reactorText(ReactorText.optimizationNoMatch),
      columns: <ListColumn<ControlItem>>[
        ListColumn<ControlItem>(itemLabel, _name, width: 'minmax(0, 1fr)'),
        ListColumn<ControlItem>(
          reactorText(ReactorText.commonEnabled),
          (ControlItem item) => _switch(context, item),
          numeric: true,
          trailing: true,
          width: '5rem',
        ),
      ],
      actions: configure != null
          ? (ControlItem item) => <RowAction>[
              if (item.knobs.isNotEmpty)
                RowAction(
                  '${reactorText(ReactorText.optimizationConfigure)} ${item.name}',
                  () => configure(item.id),
                  icon: ArcaneIcon.slidersHorizontal(),
                  iconOnly: true,
                ),
            ]
          : null,
    );
  }

  /// One closed group per category; a search opens the groups it matches.
  Widget _groups(BuildContext context, List<ControlItem> shown) {
    final Map<String, List<ControlItem>> byCategory =
        <String, List<ControlItem>>{};
    for (final ControlItem item in shown) {
      byCategory.putIfAbsent(item.category, () => <ControlItem>[]).add(item);
    }
    // Alphabetical, with the catch-all "Other" last.
    final List<String> names = byCategory.keys.toList()
      ..sort(
        (String a, String b) => a == 'Other'
            ? 1
            : b == 'Other'
            ? -1
            : a.compareTo(b),
      );
    final bool searching = query.trim().isNotEmpty;
    return ArcaneAccordion(
      key: ValueKey<String>('groups-$searching'),
      allowMultiple: true,
      items: <ArcaneAccordionItem>[
        for (final String name in names)
          ArcaneAccordionItem(
            title: name,
            customTitle: dom.span(classes: 'reactor-row-title', <Widget>[
              dom.span(<Widget>[Component.text(name)]),
              StatusBadge('${byCategory[name]!.length}'),
            ]),
            customContent: _list(context, byCategory[name]!),
            defaultOpen: searching,
          ),
      ],
    );
  }
}

/// One item's settings, staged as they are typed and sent together by
/// Save; Cancel drops them. A setting the server changed meanwhile says so.
class ControlSettingsSheet extends StatelessWidget {
  const ControlSettingsSheet({
    required this.item,
    required this.onCancel,
    this.onStage,
    this.onSave,
    this.changedOnServer,
    super.key,
  });

  /// The item shown; null closes the sheet.
  final ControlItem? item;
  final void Function() onCancel;

  /// Null shows the settings disabled.
  final void Function(String key, Object? value)? onStage;

  /// Null while nothing is staged (or the role may not write).
  final void Function()? onSave;
  final bool Function(String key)? changedOnServer;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ControlItem? shown = item;
    final void Function(String, Object?)? stage = onStage;
    return Sheet(
      open: shown != null,
      title: shown?.name ?? '',
      tip: shown == null || shown.description.isEmpty
          ? null
          : whatItDoes(shown.description),
      onClose: onCancel,
      footer: DialogFooter(
        cancel: RowAction(reactorText(ReactorText.commonCancel), onCancel),
        primary: RowAction(reactorText(ReactorText.configEditorSave), onSave),
      ),
      child: shown == null
          ? const Component.fragment(<Widget>[])
          : shown.knobs.isEmpty
          ? EmptyState(message: reactorText(ReactorText.configNoOptions))
          : ArcaneFieldGroup(
              children: <Widget>[
                for (final Knob knob in shown.knobs)
                  KnobEditor(
                    knob: knob,
                    disabled: stage == null,
                    changedOnServer: changedOnServer?.call(knob.key) ?? false,
                    onChanged: stage == null
                        ? null
                        : (Object? value) => stage(knob.key, value),
                  ),
              ],
            ),
    );
  }
}

/// Features (or tweaks) of the server the page belongs to, from its session:
/// read in build, so another server always means its own list and writes.
class ControlListScreen extends StatefulWidget {
  const ControlListScreen.features({super.key}) : tweaks = false;
  const ControlListScreen.tweaks({super.key}) : tweaks = true;

  final bool tweaks;

  @override
  State<ControlListScreen> createState() => _ControlListScreenState();
}

class _ControlListScreenState extends State<ControlListScreen> {
  String _query = '';
  String? _category;
  String? _configuring;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _configuring = null;
  }

  bool get _tweaks => component.tweaks;

  bool get _live => ServerScope.of(context)?.state == ConnState.live;

  void _failed(Object? error) {
    if (error == null || !mounted) return;
    ArcaneSonner.error(
      reactorText(ReactorText.commonUpdateFailed),
      description: localizedReactorError(error),
    );
  }

  Future<void> _toggle(
    ControlListController controller,
    String id,
    bool enabled,
  ) async {
    if (!_live) return;
    _failed(await controller.toggle(id, enabled));
  }

  Future<void> _setAll(
    ControlListController controller,
    bool enabled,
    List<ControlItem> shown,
  ) async {
    final List<ControlItem> changing = <ControlItem>[
      for (final ControlItem item in shown)
        if (item.enabled != enabled) item,
    ];
    if (changing.isEmpty) return;
    final bool yes = await askToConfirm(
      title: reactorText(
        enabled
            ? ReactorText.optimizationConfirmEnable
            : ReactorText.optimizationConfirmDisable,
        <String, Object?>{
          'count': changing.length,
          'server': serverNameOf(context),
        },
      ),
      confirmLabel: reactorText(
        enabled
            ? ReactorText.optimizationEnableAll
            : ReactorText.optimizationDisableAll,
      ),
      destructive: !enabled,
    );
    if (!yes || !mounted) return;
    for (final ControlItem item in changing) {
      if (!_live || controller.disposed) return;
      _failed(await controller.toggle(item.id, enabled));
    }
  }

  Future<void> _save(ControlListController controller, String id) async {
    setState(() => _configuring = null);
    if (!_live) return;
    _failed(await controller.commitKnobs(id));
  }

  void _cancel(ControlListController controller, String id) {
    controller.discardKnobs(id);
    setState(() => _configuring = null);
  }

  String get _label => reactorText(
    _tweaks
        ? ReactorText.tweaksColumnTweak
        : ReactorText.optimizationColumnFeature,
  );

  String get _loadingLabel => reactorText(
    _tweaks ? ReactorText.tweaksLoading : ReactorText.optimizationLoading,
  );

  String get _unavailable => reactorText(
    _tweaks
        ? ReactorText.tweaksUnavailable
        : ReactorText.optimizationUnavailable,
  );

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IControlClient>()) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: _loadingLabel)
          : EmptyState(
              message: _unavailable,
              description: reactorText(
                _tweaks
                    ? ReactorText.tweaksLiveRequired
                    : ReactorText.optimizationLiveRequired,
              ),
            );
    }
    final ControlListController controller = _tweaks
        ? session.tweaks
        : session.features;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, ControlListController controller) {
    if (controller.loading && controller.items.isEmpty) {
      return LoadingState(label: _loadingLabel);
    }
    final Object? error = controller.error;
    if (error != null && controller.items.isEmpty) {
      return ErrorState(
        title: _unavailable,
        message: localizedReactorError(error),
        onRetry: controller.load,
      );
    }
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool writable = _live && !readOnlyFor(role);
    final bool advanced = ModeScope.advanced(context);
    ControlItem? configuring;
    for (final ControlItem item in controller.items) {
      if (item.id == _configuring) configuring = item;
    }
    final String? configuringId = configuring?.id;
    return PageStack(
      children: <Widget>[
        if (error != null)
          ErrorState(
            message: localizedReactorError(error),
            onRetry: controller.load,
          ),
        ControlListView(
          items: controller.items,
          itemLabel: _label,
          emptyMessage: reactorText(
            _tweaks
                ? ReactorText.tweaksNoneAvailable
                : ReactorText.optimizationNoneAvailable,
          ),
          query: _query,
          category: _category,
          governors: !_tweaks,
          grouped: !advanced,
          onQuery: (String query) => setState(() => _query = query),
          onCategory: (String? category) =>
              setState(() => _category = category),
          onToggle: writable
              ? (String id, bool enabled) => _toggle(controller, id, enabled)
              : null,
          onConfigure: advanced
              ? (String id) => setState(() => _configuring = id)
              : null,
          onSetAll: writable
              ? (bool enabled, List<ControlItem> shown) =>
                    _setAll(controller, enabled, shown)
              : null,
          changedOnServer: (ControlItem item) => item.knobs.any(
            (Knob knob) => controller.changedOnServer(item.id, knob.key),
          ),
        ),
        ControlSettingsSheet(
          item: advanced ? configuring : null,
          onCancel: configuringId == null
              ? () => setState(() => _configuring = null)
              : () => _cancel(controller, configuringId),
          onStage: !writable || configuringId == null
              ? null
              : (String key, Object? value) =>
                    controller.stageKnob(configuringId, key, value),
          onSave:
              !writable ||
                  configuringId == null ||
                  !controller.hasStaged(configuringId)
              ? null
              : () => _save(controller, configuringId),
          changedOnServer: configuringId == null
              ? null
              : (String key) => controller.changedOnServer(configuringId, key),
        ),
      ],
    );
  }
}
