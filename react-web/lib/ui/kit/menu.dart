/// A button that opens a short list of actions, on shadcn's dropdown menu.
/// The arcane runtime opens it, moves focus with the arrow keys and types
/// ahead, closes it on Escape, Tab or a pick, and returns focus to the
/// button. Destructive entries are red and confirm in a dialog
/// ([askToConfirm]).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show kIsWeb;
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

import '../../localization/reactor_localizations.dart';
import 'browser.dart';

import 'density.dart';
import 'icon_button.dart';

/// One menu row. A null [onSelect] shows it disabled.
class AppMenuEntry {
  const AppMenuEntry(
    this.label,
    this.onSelect, {
    this.hint,
    this.description,
    this.destructive = false,
    this.icon,
  }) : separator = false;

  /// A line between groups of entries.
  const AppMenuEntry.separator()
    : label = '',
      onSelect = null,
      hint = null,
      description = null,
      destructive = false,
      icon = null,
      separator = true;

  final String label;
  final void Function()? onSelect;

  /// A short note at the end of the row, such as a shortcut.
  final String? hint;

  /// A muted line under the label: what this entry makes or does.
  final String? description;
  final bool destructive;
  final ArcaneGlyph? icon;
  final bool separator;

  /// Whether a menu showing [other] in this row's place looks the same.
  bool looksLike(AppMenuEntry other) =>
      label == other.label &&
      hint == other.hint &&
      description == other.description &&
      destructive == other.destructive &&
      separator == other.separator &&
      (onSelect == null) == (other.onSelect == null);
}

int _nextMenuId = 0;

class AppMenu extends StatefulWidget {
  const AppMenu({
    required this.entries,
    this.label,
    this.icon,
    this.iconLabel,
    this.id,
    this.alignEnd = false,
    this.ghost = false,
    this.primary = false,
    this.disabled = false,
    this.fullWidth = false,
    this.truncate = false,
    this.attributes = const <String, String>{},
    super.key,
  }) : assert(
         label != null || icon != null,
         'A menu button needs a label or an icon.',
       );

  /// The button's text. Without it the button is the [icon] alone and
  /// [iconLabel] names it. The trigger button's id is `<id>-button`.
  final String? label;
  final ArcaneGlyph? icon;

  /// The accessible name and tooltip of an icon-only button: what it acts
  /// on where a page has many. "More actions" when null.
  final String? iconLabel;
  final List<AppMenuEntry> entries;
  final String? id;

  /// Opens to the left of the button's right edge (menus at the end of a row).
  final bool alignEnd;

  /// No border (a lone button in the top bar); outlined otherwise, like
  /// the buttons beside it.
  final bool ghost;

  /// The row's primary action as a menu ("Add component").
  final bool primary;
  final bool disabled;

  /// A labelled trigger as wide as its box (the server switcher).
  final bool fullWidth;

  /// A label too long for the trigger ends in an ellipsis, the full label
  /// on hover (server names).
  final bool truncate;

  /// Extra attributes on the trigger button.
  final Map<String, String> attributes;

  @override
  State<AppMenu> createState() => _AppMenuState();
}

class _AppMenuState extends State<AppMenu> {
  /// Stable across rebuilds: the runtime keeps the open state by this id.
  late final String _id = component.id ?? 'ui-menu-${_nextMenuId++}';

  @override
  void initState() {
    super.initState();
    _catchEscape();
  }

  /// Whether the runtime has this menu open now.
  bool get _open => surfaceOpen(_id);

  /// A rebuild redraws the menu closed (the open state is the runtime's), so
  /// while it is open a rebuild that changes nothing it shows is skipped.
  /// The picks read the newest entries when they run.
  @override
  bool shouldRebuild(AppMenu newComponent) {
    if (!_open) return true;
    final AppMenu old = component;
    if (newComponent.label != old.label ||
        newComponent.iconLabel != old.iconLabel ||
        newComponent.disabled != old.disabled ||
        newComponent.ghost != old.ghost ||
        newComponent.primary != old.primary ||
        newComponent.alignEnd != old.alignEnd ||
        newComponent.entries.length != old.entries.length) {
      return true;
    }
    for (int index = 0; index < old.entries.length; index++) {
      if (!old.entries[index].looksLike(newComponent.entries[index])) {
        return true;
      }
    }
    return false;
  }

  /// Runs entry [index] with focus back on the trigger, so a confirmation it
  /// opens returns focus there instead of to the closed menu.
  void _select(int index) {
    final List<AppMenuEntry> entries = component.entries;
    if (index >= entries.length) return;
    final void Function()? run = entries[index].onSelect;
    if (kIsWeb) {
      (web.document.getElementById('$_id-button') as web.HTMLElement?)?.focus();
    }
    run?.call();
  }

  @override
  Widget build(BuildContext context) {
    final AppMenu menu = component;
    final String trigger = '$_id-button';
    final Map<String, String> attributes = <String, String>{
      ...menu.attributes,
      'aria-haspopup': 'menu',
    };
    return dom.span(
      classes: menu.fullWidth ? 'ui-menu is-wide' : 'ui-menu',
      attributes: <String, String>{'data-ui-menu': _id},
      <Widget>[
        ArcaneDropdownMenu(
          id: _id,
          fullWidth: menu.fullWidth,
          alignment: menu.alignEnd
              ? DropdownAlignment.right
              : DropdownAlignment.left,
          trigger: menu.label == null
              ? AppIconButton(
                  id: trigger,
                  icon: menu.icon!,
                  label:
                      menu.iconLabel ??
                      reactorText(ReactorText.commonMoreActions),
                  disabled: menu.disabled,
                  attributes: attributes,
                  tipAtEnd: menu.alignEnd,
                  ghost: menu.ghost,
                  onPressed: () {},
                )
              : Button(
                  id: trigger,
                  label: menu.label,
                  icon: menu.icon?.sized(controlIconSize),
                  size: buttonSize(DensityScope.of(context)),
                  variant: menu.primary
                      ? ButtonVariant.primary
                      : menu.ghost
                      ? ButtonVariant.ghost
                      : ButtonVariant.outline,
                  disabled: menu.disabled,
                  fullWidth: menu.fullWidth,
                  truncate: menu.truncate,
                  attributes: attributes,
                  onPressed: () {},
                ),
          items: <ArcaneMenuItem>[
            for (final (int index, AppMenuEntry entry) in menu.entries.indexed)
              if (entry.separator)
                const MenuItemSeparator()
              else
                MenuItemAction(
                  label: entry.label,
                  description: entry.description,
                  icon: entry.icon?.sized(controlIconSize),
                  shortcut: entry.hint,
                  destructive: entry.destructive,
                  disabled: entry.onSelect == null,
                  onSelect: entry.onSelect == null
                      ? null
                      : () => _select(index),
                ),
          ],
        ),
      ],
    );
  }
}

bool _escapeCaught = false;

/// Escape on an open menu's button closes the menu. The button's tooltip
/// would otherwise take the key (it swallows Escape to close itself), which
/// left a menu opened by a click open until focus moved into it.
void _catchEscape() {
  if (_escapeCaught || !kIsWeb) return;
  _escapeCaught = true;
  web.window.addEventListener(
    'keydown',
    ((web.KeyboardEvent event) {
      if (event.key != 'Escape') return;
      final JSAny? target = event.target;
      if (target == null || !target.isA<web.Element>()) return;
      final web.Element element = target as web.Element;
      final web.Element? holder = element.closest('[data-ui-menu]');
      final String? id = holder?.getAttribute('data-ui-menu');
      if (id == null) return;
      final web.Element? surface = web.document.getElementById(id);
      if (surface == null ||
          surface.getAttribute('data-arcane-state') != 'open' ||
          surface.contains(element)) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      (web.document.getElementById('$id-button') as web.HTMLElement?)?.click();
    }).toJS,
    web.AddEventListenerOptions(capture: true),
  );
}
