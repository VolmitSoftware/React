class ServerCredential {
  final String id;
  final String label;
  final String host;
  final int port;
  final String bearer;
  final bool secure;
  final String basePath;
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
    this.basePath = '',
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
      basePath: json['basePath'] as String? ?? '',
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
    String? basePath,
    String? relayUrl,
    String? serverPubKey,
    String? fingerprint,
  }) => ServerCredential(
    id: id,
    label: label ?? this.label,
    host: host ?? this.host,
    port: port ?? this.port,
    bearer: bearer ?? this.bearer,
    secure: secure ?? this.secure,
    basePath: basePath ?? this.basePath,
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
    'basePath': basePath,
    'relayUrl': relayUrl,
    'serverPubKey': serverPubKey,
    'fingerprint': fingerprint,
  };

  Uri? get directBaseUri {
    if (host.isEmpty || port <= 0) return null;
    String normalizedPath = basePath.trim();
    if (normalizedPath == '/') normalizedPath = '';
    if (normalizedPath.isNotEmpty && !normalizedPath.startsWith('/')) {
      normalizedPath = '/$normalizedPath';
    }
    return Uri(
      scheme: secure ? 'https' : 'http',
      host: host,
      port: port,
      path: normalizedPath,
    );
  }

  Uri directEndpoint(String path, {Map<String, String>? queryParameters}) {
    final Uri? base = directBaseUri;
    if (base == null) {
      throw StateError('Direct endpoint is unavailable.');
    }
    final Uri reference = Uri.parse(path);
    if (reference.hasScheme ||
        reference.hasAuthority ||
        reference.hasFragment) {
      throw ArgumentError.value(path, 'path', 'Must be a relative endpoint.');
    }
    final String normalizedSuffix = reference.path.startsWith('/')
        ? reference.path.substring(1)
        : reference.path;
    final String normalizedBase = base.path.endsWith('/')
        ? base.path.substring(0, base.path.length - 1)
        : base.path;
    return base.replace(
      path: '$normalizedBase/$normalizedSuffix',
      queryParameters:
          queryParameters ??
          (reference.hasQuery ? reference.queryParameters : null),
    );
  }

  Uri directWebSocketEndpoint(String path) =>
      directEndpoint(path).replace(scheme: secure ? 'wss' : 'ws');
}
