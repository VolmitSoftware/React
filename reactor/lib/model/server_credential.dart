class ServerCredential {
  final String id;
  final String label;
  final String host;
  final int port;
  final String bearer;
  final bool secure;
  final String? relayUrl;
  final String? serverPubKey;
  final String? fingerprint;

  const ServerCredential({
    required this.id,
    required this.label,
    required this.host,
    required this.port,
    required this.bearer,
    this.secure = false,
    this.relayUrl,
    this.serverPubKey,
    this.fingerprint,
  });

  factory ServerCredential.fromJson(Map<String, dynamic> json) {
    return ServerCredential(
      id: json['id'] as String,
      label: json['label'] as String,
      host: json['host'] as String,
      port: json['port'] as int,
      bearer: json['bearer'] as String,
      secure: json['secure'] as bool? ?? false,
      relayUrl: json['relayUrl'] as String?,
      serverPubKey: json['serverPubKey'] as String?,
      fingerprint: json['fingerprint'] as String?,
    );
  }

  ServerCredential copyWith({
    String? label,
    String? host,
    int? port,
    String? bearer,
    bool? secure,
    String? relayUrl,
    String? serverPubKey,
    String? fingerprint,
  }) =>
      ServerCredential(
        id: id,
        label: label ?? this.label,
        host: host ?? this.host,
        port: port ?? this.port,
        bearer: bearer ?? this.bearer,
        secure: secure ?? this.secure,
        relayUrl: relayUrl ?? this.relayUrl,
        serverPubKey: serverPubKey ?? this.serverPubKey,
        fingerprint: fingerprint ?? this.fingerprint,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'label': label,
        'host': host,
        'port': port,
        'bearer': bearer,
        'secure': secure,
        'relayUrl': relayUrl,
        'serverPubKey': serverPubKey,
        'fingerprint': fingerprint,
      };
}
