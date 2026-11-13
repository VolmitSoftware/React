/// The one way to lay out buttons. Actions sit at the end in a fixed order:
/// the "..." menu, secondary actions, Cancel, then the primary action last.
/// Content controls (filters, a view switch, a title) go in [leading] on
/// the left. Every button is the row's size: regular (36 px) or [dense]
/// (32 px), icons 16 px, 8 px between buttons and 16 px between groups.
///
/// A toolbar never wraps. When it does not fit, measured after layout,
/// secondary actions with an icon first drop their text, then move into the
/// "..." menu one by one from the left. A footer ([ActionRow.footer]) keeps
/// its buttons and, at 760 px and below, stacks them full width with the
/// primary on top. Never lay out buttons any other way.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show kIsWeb;
import 'package:jaspr_router/jaspr_router.dart' show Router;
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

import 'browser.dart';
import 'density.dart';
import 'icon_button.dart';
import 'menu.dart';

/// One button of an [ActionRow]. A null [onPressed] shows it disabled,
/// unless it has an [href].
class RowAction {
  const RowAction(
    this.label,
    this.onPressed, {
    this.href,
    this.icon,
    this.iconOnly = false,
    this.destructive = false,
    this.loading = false,
    this.pressed,
    this.expanded,
    this.id,
    this.attributes = const <String, String>{},
  });

  /// Sentence case, one to three words. An icon-only button is named by it.
  final String label;
  final void Function()? onPressed;

  /// Navigation: the button is a link to this address (the shell routes
  /// same-site links without a reload).
  final String? href;
  final ArcaneGlyph? icon;

  /// Shows the icon alone, with [label] as its name and tooltip.
  final bool iconOnly;

  /// Red: a destructive primary confirms in red; elsewhere it marks the menu
  /// entry when the action folds.
  final bool destructive;
  final bool loading;

  /// For a toggle: `aria-pressed`.
  final bool? pressed;

  /// For a disclosure: `aria-expanded`.
  final bool? expanded;
  final String? id;
  final Map<String, String> attributes;
}

int _nextRowId = 0;

class ActionRow extends StatefulWidget {
  const ActionRow({
    this.leading = const <Widget>[],
    this.more = const <AppMenuEntry>[],
    this.secondary = const <RowAction>[],
    this.cancel,
    this.primary,
    this.dense = false,
    this.start = false,
    this.label,
    this.moreId,
    super.key,
  }) : footer = false;

  /// A dialog, sheet or form footer: no [leading], no folding.
  const ActionRow.footer({
    this.secondary = const <RowAction>[],
    this.cancel,
    this.primary,
    this.dense = false,
    this.label,
    super.key,
  }) : leading = const <Widget>[],
       more = const <AppMenuEntry>[],
       moreId = null,
       start = false,
       footer = true;

  /// Controls that act on the content (filters, a view switch) or the
  /// row's title, on the left.
  final List<Widget> leading;

  /// Entries of the "..." menu, which comes first among the actions.
  final List<AppMenuEntry> more;
  final List<RowAction> secondary;

  /// Closes without doing anything; before the primary action.
  final RowAction? cancel;

  /// The one filled button, always last.
  final RowAction? primary;

  /// 32 px controls (toolbars, list bars, stage headers) instead of 36 px.
  final bool dense;
  final bool footer;

  /// The buttons start on the left: shortcuts that are a card's content,
  /// not a toolbar.
  final bool start;

  /// Names the row for assistive technology (a toolbar's purpose).
  final String? label;

  /// The "..." menu's id; its button is `<moreId>-button`.
  final String? moreId;

  @override
  State<ActionRow> createState() => _ActionRowState();
}

class _ActionRowState extends State<ActionRow> {
  final String _id = 'ui-actions-${_nextRowId++}';

  /// 0 shows everything; 1 drops the text of secondary actions that have an
  /// icon; each step after that moves one more secondary into the menu.
  int _fold = 0;
  double _width = 0;
  web.ResizeObserver? _observer;

  bool get _hasIcons => component.secondary.any(
    (RowAction action) => action.icon != null && !action.iconOnly,
  );

  int get _maxFold => component.secondary.length + 1;

  String _signature(ActionRow row) => <String>[
    for (final RowAction each in row.secondary) each.label,
    row.primary?.label ?? '',
    row.cancel?.label ?? '',
    '${row.leading.length}',
  ].join('|');

  @override
  void didUpdateWidget(ActionRow oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (_signature(oldComponent) != _signature(component)) _fold = 0;
  }

  @override
  void dispose() {
    _observer?.disconnect();
    super.dispose();
  }

  bool get _foldable => !component.footer && component.secondary.isNotEmpty;

  void _observe() {
    if (!kIsWeb || !mounted || !_foldable || _observer != null) return;
    final web.Element? row = web.document.getElementById(_id);
    if (row == null) return;
    _observer = web.ResizeObserver(
      ((
        JSArray<web.ResizeObserverEntry> _,
        web.ResizeObserver _,
      ) => _resized()).toJS,
    );
    _observer!.observe(row);
  }

  void _resized() {
    final web.Element? row = web.document.getElementById(_id);
    if (row == null || !mounted) return;
    final double width = row.getBoundingClientRect().width;
    final bool grew = width > _width + 1;
    _width = width;
    if (grew && _fold > 0) {
      setState(() => _fold = 0);
      return;
    }
    _measure();
  }

  /// Folds one more step while anything sticks out past either end of the
  /// row: the end group is right-aligned, so it overflows to the start.
  void _measure() {
    if (!kIsWeb || !mounted || !_foldable) return;
    final web.Element? row = web.document.getElementById(_id);
    if (row == null) return;
    final web.DOMRect box = row.getBoundingClientRect();
    final double start = box.left - 1;
    final double end = box.right + 1;
    bool over = false;
    final web.HTMLCollection groups = row.children;
    for (int index = 0; index < groups.length && !over; index++) {
      final web.Element group = groups.item(index)!;
      final web.DOMRect groupBox = group.getBoundingClientRect();
      if (groupBox.right + 1 > end || groupBox.left - 1 < start) over = true;
      final web.HTMLCollection items = group.children;
      for (int item = 0; item < items.length && !over; item++) {
        final web.Element element = items.item(item)!;
        final web.DOMRect itemBox = element.getBoundingClientRect();
        if (itemBox.right > groupBox.right + 1 ||
            itemBox.left < groupBox.left - 1) {
          over = true;
        }
        // A leading switch that scrolls sideways (a view switch) is squeezed too.
        final String scroll = web.window.getComputedStyle(element).overflowX;
        if ((scroll == 'auto' || scroll == 'scroll') &&
            element.scrollWidth > element.clientWidth + 1) {
          over = true;
        }
      }
    }
    if (!over) return;
    int next = _fold + 1;
    if (next == 1 && !_hasIcons) next = 2;
    if (next <= _maxFold) setState(() => _fold = next);
  }

  Widget _button(
    RowAction action, {
    required _Slot slot,
    required Density density,
    bool iconOnly = false,
  }) {
    final ArcaneGlyph? icon = action.icon?.sized(controlIconSize);
    final Map<String, String> attributes = <String, String>{
      ...action.attributes,
      'data-action-slot': slot.name,
      if (action.pressed != null) 'aria-pressed': '${action.pressed}',
      if (action.expanded != null) 'aria-expanded': '${action.expanded}',
    };
    if ((iconOnly || action.iconOnly) && icon != null) {
      return AppIconButton(
        id: action.id,
        icon: icon,
        label: action.label,
        onPressed: action.onPressed,
        pressed: action.pressed,
        expanded: action.expanded,
        attributes: attributes,
        tipAtEnd: true,
      );
    }
    final ButtonVariant variant = switch (slot) {
      _Slot.primary =>
        action.destructive ? ButtonVariant.destructive : ButtonVariant.primary,
      _Slot.cancel || _Slot.secondary => ButtonVariant.outline,
    };
    return Button(
      id: action.id,
      label: action.label,
      icon: icon,
      size: buttonSize(density),
      variant: variant,
      loading: action.loading,
      disabled: action.onPressed == null && action.href == null,
      href: action.href,
      attributes: attributes,
      onPressed: action.onPressed,
    );
  }

  @override
  Widget build(BuildContext context) {
    final ActionRow row = component;
    final Density density = row.dense ? Density.dense : Density.regular;
    final int folded = _fold <= 1
        ? 0
        : (_fold - 1).clamp(0, row.secondary.length);
    final bool textless = _fold >= 1;
    final bool hasEnd =
        row.more.isNotEmpty ||
        row.secondary.isNotEmpty ||
        row.cancel != null ||
        row.primary != null;
    final List<AppMenuEntry> menu = <AppMenuEntry>[
      for (final RowAction each in row.secondary.take(folded))
        AppMenuEntry(
          each.label,
          each.onPressed ??
              (each.href == null
                  ? null
                  : () => Router.maybeOf(context)?.push(each.href!)),
          destructive: each.destructive,
          icon: each.icon?.sized(controlIconSize),
        ),
      if (folded > 0 && row.more.isNotEmpty) const AppMenuEntry.separator(),
      ...row.more,
    ];
    if (_foldable) {
      afterFrame(() {
        _observe();
        _measure();
      });
    }
    return DensityScope(
      density: density,
      child: dom.div(
        id: _id,
        classes:
            'ui-actions${row.dense ? ' is-dense ui-dense' : ''}${row.footer ? ' is-footer' : ''}${row.start ? ' is-start' : ''}',
        attributes: <String, String>{
          'data-action-row': row.footer ? 'footer' : 'toolbar',
          if (!row.footer && row.label != null) 'role': 'toolbar',
          if (row.label != null) 'aria-label': row.label!,
        },
        <Widget>[
          if (row.leading.isNotEmpty)
            dom.div(classes: 'ui-actions-lead', row.leading),
          if (hasEnd)
            dom.div(classes: 'ui-actions-end', <Widget>[
              if (menu.isNotEmpty)
                AppMenu(
                  id: row.moreId,
                  icon: ArcaneIcon.ellipsis(),
                  entries: menu,
                  alignEnd: true,
                  attributes: const <String, String>{
                    'data-action-slot': 'more',
                  },
                ),
              for (final RowAction each in row.secondary.skip(folded))
                _button(
                  each,
                  slot: _Slot.secondary,
                  density: density,
                  iconOnly: textless && each.icon != null,
                ),
              if (row.cancel != null)
                _button(row.cancel!, slot: _Slot.cancel, density: density),
              if (row.primary != null)
                _button(row.primary!, slot: _Slot.primary, density: density),
            ]),
        ],
      ),
    );
  }
}

enum _Slot { secondary, cancel, primary }

/// The buttons under a dialog or sheet: Cancel, then the primary action.
class DialogFooter extends StatelessWidget {
  const DialogFooter({
    this.primary,
    this.cancel,
    this.secondary = const <RowAction>[],
    super.key,
  });

  final RowAction? primary;
  final RowAction? cancel;
  final List<RowAction> secondary;

  @override
  Widget build(BuildContext context) =>
      ActionRow.footer(secondary: secondary, cancel: cancel, primary: primary);
}

/// The buttons that end a form or an editor pane (Apply, Discard).
class FormFooter extends StatelessWidget {
  const FormFooter({
    this.primary,
    this.cancel,
    this.secondary = const <RowAction>[],
    this.dense = false,
    super.key,
  });

  final RowAction? primary;
  final RowAction? cancel;
  final List<RowAction> secondary;
  final bool dense;

  @override
  Widget build(BuildContext context) => ActionRow.footer(
    secondary: secondary,
    cancel: cancel,
    primary: primary,
    dense: dense,
  );
}
