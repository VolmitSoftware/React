library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../localization/reactor_localizations.dart';
import '../state/connection_manager.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';

Widget? serverSnapshotStatePage({
  required ServerScope? scope,
  required String title,
  String? subtitle,
  Widget? icon,
}) {
  if (scope?.snapshot != null) return null;

  return ReactorPage(
    title: title,
    subtitle: subtitle,
    children: <Widget>[serverSnapshotState(scope: scope, icon: icon)],
  );
}

Widget serverSnapshotState({required ServerScope? scope, Widget? icon}) {
  return switch (scope?.state) {
    ConnState.connecting => ReactorLoadingState(
      label: reactorText(ReactorText.metricsWaiting),
    ),
    ConnState.live => ReactorLoadingState(
      label: reactorText(ReactorText.snapshotWaitingFirst),
    ),
    ConnState.degraded => ReactorEmptyState(
      title: reactorText(ReactorText.snapshotNone),
      description: reactorText(ReactorText.snapshotAfterRecovery),
      icon: icon,
    ),
    ConnState.offline => ReactorEmptyState(
      title: reactorText(ReactorText.snapshotNone),
      description: reactorText(ReactorText.snapshotAfterReconnect),
      icon: icon,
    ),
    null => ReactorNotice(
      title: reactorText(ReactorText.snapshotUnavailable),
      message: reactorText(ReactorText.snapshotConnectionRequired),
      status: ReactorStatus.critical,
    ),
  };
}
