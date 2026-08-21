enum AlertSeverity { info, warning, critical }

int alertSeverityRank(AlertSeverity s) {
  switch (s) {
    case AlertSeverity.critical:
      return 2;
    case AlertSeverity.warning:
      return 1;
    case AlertSeverity.info:
      return 0;
  }
}

class FleetAlert {
  final String serverId;
  final String serverName;
  final String metricId;
  final AlertSeverity severity;
  final String title;
  final String message;
  final double value;
  final double threshold;
  final DateTime firstSeen;
  final DateTime lastSeen;

  const FleetAlert({
    required this.serverId,
    required this.serverName,
    required this.metricId,
    required this.severity,
    required this.title,
    required this.message,
    required this.value,
    required this.threshold,
    required this.firstSeen,
    required this.lastSeen,
  });

  String get key => '$serverId/$metricId';

  FleetAlert copyWith({
    AlertSeverity? severity,
    String? message,
    double? value,
    double? threshold,
    DateTime? firstSeen,
    DateTime? lastSeen,
  }) => FleetAlert(
    serverId: serverId,
    serverName: serverName,
    metricId: metricId,
    severity: severity ?? this.severity,
    title: title,
    message: message ?? this.message,
    value: value ?? this.value,
    threshold: threshold ?? this.threshold,
    firstSeen: firstSeen ?? this.firstSeen,
    lastSeen: lastSeen ?? this.lastSeen,
  );

  @override
  bool operator ==(Object other) => other is FleetAlert && other.key == key;

  @override
  int get hashCode => key.hashCode;
}
