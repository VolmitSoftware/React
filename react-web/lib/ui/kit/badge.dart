/// State labels on shadcn's status badge: Live, Critical, Admin. A badge is
/// text first; colour only repeats what it says.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

enum BadgeTone { neutral, ok, warning, error, info }

class StatusBadge extends StatelessWidget {
  const StatusBadge(
    this.label, {
    this.tone = BadgeTone.neutral,
    this.dot = false,
    this.attributes = const <String, String>{},
    super.key,
  });

  final String label;
  final BadgeTone tone;

  /// A leading dot for live state (a connection); off for labels.
  final bool dot;

  /// Kept on a wrapper (the library badge takes none): `data-*`, `title`.
  final Map<String, String> attributes;

  @override
  Widget build(BuildContext context) => dom.span(
    classes: 'ui-badge',
    attributes: <String, String>{'data-tone': tone.name, ...attributes},
    <Widget>[
      ArcaneStatusBadge(
        label: label,
        size: ComponentSize.sm,
        variant: tone == BadgeTone.neutral
            ? BadgeVariant.outline
            : BadgeVariant.status,
        status: switch (tone) {
          BadgeTone.ok => StatusType.success,
          BadgeTone.warning => StatusType.warning,
          BadgeTone.error => StatusType.error,
          BadgeTone.info || BadgeTone.neutral => StatusType.info,
        },
        showDot: dot,
      ),
    ],
  );
}
