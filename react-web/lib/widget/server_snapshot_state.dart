/// What a server page shows before its first snapshot: loading while the
/// connection comes up, empty while it is down, an error without one.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_localizations.dart';
import '../state/connection_manager.dart';
import '../state/server_scope.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/states.dart';

Widget serverSnapshotState({required ServerScope? scope, Widget? icon}) {
  final ArcaneGlyph? glyph = icon is ArcaneGlyph ? icon : null;
  return switch (scope?.state) {
    ConnState.connecting => LoadingState(
      label: reactorText(ReactorText.metricsWaiting),
    ),
    ConnState.live => LoadingState(
      label: reactorText(ReactorText.snapshotWaitingFirst),
    ),
    ConnState.degraded => EmptyState(
      message: reactorText(ReactorText.snapshotNone),
      description: reactorText(ReactorText.snapshotAfterRecovery),
      icon: glyph,
    ),
    ConnState.offline => EmptyState(
      message: reactorText(ReactorText.snapshotNone),
      description: reactorText(ReactorText.snapshotAfterReconnect),
      icon: glyph,
    ),
    null => AppAlert(
      tone: AlertTone.error,
      title: reactorText(ReactorText.snapshotUnavailable),
      message: reactorText(ReactorText.snapshotConnectionRequired),
      live: true,
    ),
  };
}
