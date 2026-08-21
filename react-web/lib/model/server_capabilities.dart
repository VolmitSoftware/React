class ServerCapabilities {
  final int protocolVersion;
  final String serverFingerprint;
  final bool relayAvailable;

  const ServerCapabilities({
    required this.protocolVersion,
    required this.serverFingerprint,
    required this.relayAvailable,
  });

  factory ServerCapabilities.fromJson(Map<String, dynamic> json) {
    return ServerCapabilities(
      protocolVersion: json['protocolVersion'] as int,
      serverFingerprint: json['serverFingerprint'] as String,
      relayAvailable: json['relayAvailable'] as bool,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'protocolVersion': protocolVersion,
    'serverFingerprint': serverFingerprint,
    'relayAvailable': relayAvailable,
  };
}
