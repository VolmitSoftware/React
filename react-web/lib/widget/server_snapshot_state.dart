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
    ConnState.live => const ReactorLoadingState(
      label: 'Waiting for the first telemetry snapshot',
    ),
    ConnState.degraded => ReactorEmptyState(
      title: 'No telemetry snapshot',
      description:
          'This view will populate after the connection recovers and React publishes a snapshot.',
      icon: icon,
    ),
    ConnState.offline => ReactorEmptyState(
      title: 'No telemetry snapshot',
      description:
          'This view will populate after React reconnects and publishes a snapshot.',
      icon: icon,
    ),
    null => const ReactorNotice(
      title: 'Telemetry unavailable',
      message: 'This view requires a server connection.',
      status: ReactorStatus.critical,
    ),
  };
}
