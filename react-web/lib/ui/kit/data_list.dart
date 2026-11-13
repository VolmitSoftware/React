/// Rows of records with named columns: a table where its columns fit, and
/// stacked "label value" rows where they do not (measured on the list's
/// own width, so a wide table stacks at 768 px while a short one stays a
/// table). Text cells hold one line and end in an ellipsis with the full
/// text as their title; numbers never wrap. Minor columns drop when the
/// rows stack. On the page it is one card holding separated rows; inside a
/// card it draws flat. The library table does not stack on phones, so the
/// rows are laid out here from plain markup and library parts (badges,
/// buttons in an [ActionRow]).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import 'action_row.dart';
import 'card.dart';
import 'menu.dart';
import 'surface.dart';

/// One column of a [DataList].
class ListColumn<T> {
  const ListColumn(
    this.label,
    this.cell, {
    this.numeric = false,
    this.width = '1fr',
    this.trailing = false,
  }) : text = null;

  /// A column of plain text: one line, an ellipsis and the full text as
  /// its title when it does not fit.
  ListColumn.text(
    this.label,
    String Function(T item) this.text, {
    this.numeric = false,
    this.width = '1fr',
  }) : trailing = false,
       cell = ((T item) => oneLineText(text(item)));

  /// The plain text of a [ListColumn.text] column.
  final String Function(T item)? text;

  /// A control that belongs beside the row's name (a switch): when the
  /// rows stack it stays on the name's line, without a label.
  final bool trailing;

  /// The header on wide screens and the label before the value when rows
  /// stack.
  final String label;
  final Widget Function(T item) cell;

  /// Right-aligned, tabular figures.
  final bool numeric;

  /// The column's CSS grid track ("1fr", "8rem", "minmax(0, 2fr)").
  final String width;

  /// About how wide the column must be to read well, in rem: the first
  /// column 9, numbers 5, other text 7.
  double floorRem(int index) => index == 0 ? 9 : (numeric ? 5 : 7);
}

/// Text on one line that ends in an ellipsis when its cell is too narrow,
/// with the full text as its title.
Widget oneLineText(String value) => dom.span(
  classes: 'reactor-one-line',
  attributes: <String, String>{'title': value},
  <Widget>[Component.text(value)],
);

/// The list widths, in rem, at which rows stack: the smallest that holds
/// every column ([DataList.stackBelow]); base.css has a rule for each.
const List<int> kListStackWidths = <int>[24, 32, 40, 48, 56, 64];

class DataList<T> extends StatelessWidget {
  const DataList({
    required this.label,
    required this.items,
    required this.columns,
    this.title,
    this.href,
    this.actions,
    this.more,
    this.emptyMessage,
    this.header = true,
    super.key,
  });

  /// The table's accessible name.
  final String label;
  final List<T> items;

  /// The first column names the row; it is never labelled when stacked and
  /// carries the row's link.
  final List<ListColumn<T>> columns;

  /// A heading over the rows (the card's title on the page).
  final String? title;

  /// Where a row opens; the first cell becomes the link.
  final String? Function(T item)? href;

  /// The row's own buttons, in a dense [ActionRow] at its end.
  final List<RowAction> Function(T item)? actions;

  /// The row's "..." menu, after its buttons.
  final List<AppMenuEntry> Function(T item)? more;

  bool get _hasActions => actions != null || more != null;

  /// Shown instead of the rows when [items] is empty, as one muted line.
  final String? emptyMessage;

  /// The row of column names; off when one column names itself.
  final bool header;

  /// The list width (rem) below which its rows stack.
  int get stackBelow {
    double need = 0;
    for (final (int index, ListColumn<T> column) in columns.indexed) {
      need += column.floorRem(index) + (index == 0 ? 0 : 1);
    }
    if (_hasActions) need += 7;
    for (final int width in kListStackWidths) {
      if (need <= width) return width;
    }
    return kListStackWidths.last;
  }

  String get _tracks => <String>[
    for (final ListColumn<T> column in columns) column.width,
    if (_hasActions) 'auto',
  ].join(' ');

  Widget _row(T item) {
    final String? link = href?.call(item);
    return dom.div(
      classes: 'ui-list-row',
      attributes: const <String, String>{'role': 'row'},
      <Widget>[
        for (final (int index, ListColumn<T> column) in columns.indexed)
          dom.div(
            classes: <String>[
              'ui-list-cell',
              if (column.numeric) 'is-numeric',
              if (column.trailing) 'is-trailing',
            ].join(' '),
            attributes: <String, String>{
              'role': index == 0 ? 'rowheader' : 'cell',
              if (index > 0 && !column.trailing) 'data-label': column.label,
            },
            <Widget>[
              if (index == 0 && link != null)
                dom.a(href: link, classes: 'ui-list-link', <Widget>[
                  column.cell(item),
                ])
              else
                column.cell(item),
            ],
          ),
        if (_hasActions)
          dom.div(
            classes: 'ui-list-cell is-actions',
            attributes: const <String, String>{'role': 'cell'},
            <Widget>[
              ActionRow(
                dense: true,
                secondary: actions?.call(item) ?? const <RowAction>[],
                more: more?.call(item) ?? const <AppMenuEntry>[],
              ),
            ],
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final Widget body = items.isEmpty
        ? dom.p(classes: 'ui-caption', <Widget>[
            Component.text(emptyMessage ?? ''),
          ])
        : dom.div(classes: 'ui-list-host', <Widget>[
            dom.div(
              classes: 'ui-list',
              attributes: <String, String>{
                'role': 'table',
                'aria-label': label,
                'data-stack': '$stackBelow',
              },
              styles: dom.Styles(
                raw: <String, String>{'--ui-list-cols': _tracks},
              ),
              <Widget>[
                if (header)
                  dom.div(
                    classes: 'ui-list-row is-head',
                    attributes: const <String, String>{'role': 'row'},
                    <Widget>[
                      for (final ListColumn<T> column in columns)
                        dom.div(
                          classes: column.numeric
                              ? 'ui-list-cell is-numeric'
                              : 'ui-list-cell',
                          attributes: <String, String>{'role': 'columnheader'},
                          <Widget>[Component.text(column.label)],
                        ),
                      if (_hasActions)
                        const dom.div(
                          classes: 'ui-list-cell is-actions',
                          attributes: <String, String>{'role': 'columnheader'},
                          <Widget>[],
                        ),
                    ],
                  ),
                for (final T item in items) _row(item),
              ],
            ),
          ]);
    if (SurfaceScope.inCard(context)) return body;
    return AppCard(title: title, children: <Widget>[body]);
  }
}
