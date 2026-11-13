/// A message in the page: an error with Retry, a warning, a note. On
/// shadcn's alert; inside a card it is a plain line with its icon, so it is
/// not a box in a box.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import 'surface.dart';

enum AlertTone { info, ok, warning, error }

class AppAlert extends StatelessWidget {
  const AppAlert({
    required this.message,
    this.title,
    this.tone = AlertTone.info,
    this.action,
    this.live = false,
    super.key,
  });

  final String? title;
  final String message;
  final AlertTone tone;

  /// One button: Retry, Reconnect.
  final Widget? action;

  /// Read out when it appears (`role=alert` for errors, `status` otherwise).
  final bool live;

  @override
  Widget build(BuildContext context) {
    final Map<String, String> attributes = <String, String>{
      'data-alert': tone.name,
      if (live) 'role': tone == AlertTone.error ? 'alert' : 'status',
    };
    if (SurfaceScope.inCard(context)) {
      return dom.div(classes: 'ui-note', attributes: attributes, <Widget>[
        dom.span(classes: 'ui-note-icon', <Widget>[_icon()]),
        dom.span(classes: 'ui-note-text', <Widget>[
          if (title != null) dom.strong(<Widget>[Component.text(title!)]),
          Component.text(message),
        ]),
        ?action,
      ]);
    }
    final Widget alert = switch (tone) {
      AlertTone.info => ArcaneAlert.info(
        title: title,
        message: message,
        action: action,
      ),
      AlertTone.ok => ArcaneAlert.success(
        title: title,
        message: message,
        action: action,
      ),
      AlertTone.warning => ArcaneAlert.warning(
        title: title,
        message: message,
        action: action,
      ),
      AlertTone.error => ArcaneAlert.error(
        title: title,
        message: message,
        action: action,
      ),
    };
    return dom.div(classes: 'ui-alert', attributes: attributes, <Widget>[
      alert,
    ]);
  }

  Widget _icon() => switch (tone) {
    AlertTone.info => ArcaneIcon.info(size: IconSize.sm),
    AlertTone.ok => ArcaneIcon.circleCheck(size: IconSize.sm),
    AlertTone.warning => ArcaneIcon.triangleAlert(size: IconSize.sm),
    AlertTone.error => ArcaneIcon.circleAlert(size: IconSize.sm),
  };
}
