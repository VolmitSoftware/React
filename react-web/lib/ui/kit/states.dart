/// The three states of anything that loads: nothing there yet, still
/// loading, failed. None of them draws a box inside a card.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import 'action_row.dart';
import 'alert.dart';

/// Nothing to show: an icon, one line and the one action that fills it.
/// On shadcn's empty state.
class EmptyState extends StatelessWidget {
  const EmptyState({
    required this.message,
    this.description,
    this.icon,
    this.action,
    super.key,
  });

  /// One short line that says why it is empty.
  final String message;

  /// A second line, only when the first cannot say it.
  final String? description;
  final ArcaneGlyph? icon;

  /// The one action that fills it (an [ActionRow.footer] or a button).
  final Widget? action;

  @override
  Widget build(BuildContext context) => dom.div(
    classes: 'ui-empty',
    attributes: const <String, String>{'role': 'status'},
    <Widget>[
      ArcaneEmptyState(
        title: message,
        description: description,
        icon: icon,
        action: action,
        size: EmptyStateSize.sm,
      ),
    ],
  );
}

/// Grey bars where content will be, read out as [label].
class LoadingState extends StatelessWidget {
  const LoadingState({this.label, this.rows = 3, this.height, super.key});

  /// What is loading; "Loading" when null.
  final String? label;

  /// Bars to draw; with [height], one block of that height instead.
  final int rows;

  /// A CSS height for one block (a chart placeholder).
  final String? height;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(
      classes: 'ui-loading',
      attributes: <String, String>{
        'role': 'status',
        'aria-live': 'polite',
        'aria-label': label ?? reactorText(ReactorText.commonLoading),
      },
      <Widget>[
        if (height != null)
          ArcaneSkeleton(width: '100%', height: height)
        else
          for (int row = 0; row < rows; row++)
            ArcaneSkeleton.line(
              width: row == rows - 1 && rows > 1 ? '60%' : '100%',
            ),
      ],
    );
  }
}

/// A failure with Retry. An alert on the page, a plain line in a card.
class ErrorState extends StatelessWidget {
  const ErrorState({
    required this.message,
    this.title,
    this.onRetry,
    super.key,
  });

  final String message;
  final String? title;

  /// Adds a Retry button.
  final void Function()? onRetry;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppAlert(
      tone: AlertTone.error,
      title: title,
      message: message,
      live: true,
      action: onRetry == null
          ? null
          : ActionRow.footer(
              dense: true,
              secondary: <RowAction>[
                RowAction(reactorText(ReactorText.commonRetry), onRetry),
              ],
            ),
    );
  }
}
