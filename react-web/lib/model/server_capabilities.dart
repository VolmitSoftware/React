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
    final Object? protocolVersion = json['protocolVersion'];
    final Object? serverFingerprint = json['serverFingerprint'];
    final Object? relayAvailable = json['relayAvailable'];
    if (protocolVersion is! int ||
        serverFingerprint is! String ||
        relayAvailable is! bool) {
      throw const FormatException('Malformed server capabilities');
    }
    return ServerCapabilities(
      protocolVersion: protocolVersion,
      serverFingerprint: serverFingerprint,
      relayAvailable: relayAvailable,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'protocolVersion': protocolVersion,
    'serverFingerprint': serverFingerprint,
    'relayAvailable': relayAvailable,
  };
}
