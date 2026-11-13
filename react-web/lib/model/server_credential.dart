enum ServerTransport { direct, relay, dual }

class ServerCredential {
  final String id;
  final String label;
  final String host;
  final int port;

  /// `tok_x.<secret>`; empty once the pairing ended in this browser (a
  /// pairing kept for the tab only, after the tab closed).
  final String bearer;
  final bool secure;
  final String basePath;
  final String? relayUrl;

  /// The server's Ed25519 key from the pairing link, for the relay proof.
  final String? serverKey;
  final String? fingerprint;

  /// Role reported when the entry was paired (viewer, operator, admin);
  /// null for a custom scope set.
  final String? role;

  /// The token's id when the bearer is gone; else read from the bearer.
  final String? pairedTokenId;

  /// What the pairing may do.
  final List<String> scopes;

  /// When the pairing ends unless used; it moves on with every use.
  final DateTime? expiresAt;

  /// Kept in localStorage (every tab, until it ends); else in
  /// sessionStorage, so it ends with the tab.
  final bool remember;

  /// Paired from a pairing link. Entries paired with an RCT2 code before
  /// links existed are false: the server no longer knows their tokens.
  final bool linked;

  const ServerCredential({
    required this.id,
    required this.label,
    required this.host,
    required this.port,
    required this.bearer,
    this.secure = false,
    this.basePath = '',
    this.relayUrl,
    this.serverKey,
    this.fingerprint,
    this.role,
    this.pairedTokenId,
    this.scopes = const <String>[],
    this.expiresAt,
    this.remember = true,
    this.linked = true,
  });

  /// Marks an entry paired from a link in storage.
  static const int pairingVersion = 3;

  factory ServerCredential.fromJson(Map<String, dynamic> json) {
    final Object? scopes = json['scopes'];
    final Object? expires = json['expiresAt'];
    return ServerCredential(
      id: json['id'] as String,
      label: json['label'] as String,
      host: json['host'] as String,
      port: json['port'] as int,
      bearer: json['bearer'] as String? ?? '',
      secure: json['secure'] as bool? ?? false,
      basePath: json['basePath'] as String? ?? '',
      relayUrl: json['relayUrl'] as String?,
      serverKey: json['serverKey'] as String?,
      fingerprint: json['fingerprint'] as String?,
      role: json['role'] as String?,
      pairedTokenId: json['tokenId'] as String?,
      scopes: <String>[
        if (scopes is List<dynamic>)
          for (final dynamic scope in scopes)
            if (scope is String) scope,
      ],
      expiresAt: expires is String ? DateTime.tryParse(expires) : null,
      remember: json['remember'] as bool? ?? true,
      linked: json['pairing'] == pairingVersion,
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
    String? serverKey,
    String? fingerprint,
    String? role,
    DateTime? expiresAt,
    bool? remember,
  }) => ServerCredential(
    id: id,
    label: label ?? this.label,
    host: host ?? this.host,
    port: port ?? this.port,
    bearer: bearer ?? this.bearer,
    secure: secure ?? this.secure,
    basePath: basePath ?? this.basePath,
    relayUrl: relayUrl ?? this.relayUrl,
    serverKey: serverKey ?? this.serverKey,
    fingerprint: fingerprint ?? this.fingerprint,
    role: role ?? this.role,
    pairedTokenId: pairedTokenId,
    scopes: scopes,
    expiresAt: expiresAt ?? this.expiresAt,
    remember: remember ?? this.remember,
    linked: linked,
  );

  /// The same credential under another entry id (a new pairing of a server
  /// already listed keeps its id, tags and alerts).
  ServerCredential withId(String newId) => ServerCredential(
    id: newId,
    label: label,
    host: host,
    port: port,
    bearer: bearer,
    secure: secure,
    basePath: basePath,
    relayUrl: relayUrl,
    serverKey: serverKey,
    fingerprint: fingerprint,
    role: role,
    pairedTokenId: pairedTokenId,
    scopes: scopes,
    expiresAt: expiresAt,
    remember: remember,
    linked: linked,
  );

  /// The entry without its bearer: what is left once the pairing ended here.
  ServerCredential withoutBearer() => ServerCredential(
    id: id,
    label: label,
    host: host,
    port: port,
    bearer: '',
    secure: secure,
    basePath: basePath,
    relayUrl: relayUrl,
    serverKey: serverKey,
    fingerprint: fingerprint,
    role: role,
    pairedTokenId: tokenId,
    scopes: scopes,
    expiresAt: expiresAt,
    remember: remember,
    linked: linked,
  );

  Map<String, dynamic> toJson() => <String, dynamic>{
    'id': id,
    'label': label,
    'host': host,
    'port': port,
    if (bearer.isNotEmpty) 'bearer': bearer,
    'secure': secure,
    'basePath': basePath,
    'relayUrl': relayUrl,
    'serverKey': serverKey,
    'fingerprint': fingerprint,
    'role': ?role,
    'tokenId': tokenId,
    'scopes': scopes,
    if (expiresAt != null) 'expiresAt': expiresAt!.toUtc().toIso8601String(),
    'remember': remember,
    if (linked) 'pairing': pairingVersion,
  };

  /// The token id: the part of `tok_x.<secret>` before the dot.
  String get tokenId {
    final String? stored = pairedTokenId;
    if (stored != null && stored.isNotEmpty) return stored;
    final int dot = bearer.indexOf('.');
    return dot < 0 ? bearer : bearer.substring(0, dot);
  }

  /// Whether this entry is the same server as [other].
  bool sameServerAs(ServerCredential other) =>
      fingerprint != null &&
      fingerprint!.isNotEmpty &&
      fingerprint == other.fingerprint;

  bool get hasDirect => host.isNotEmpty && port > 0;

  bool get hasRelay => relayUrl != null && relayUrl!.isNotEmpty;

  ServerTransport get transport => hasRelay
      ? (hasDirect ? ServerTransport.dual : ServerTransport.relay)
      : ServerTransport.direct;

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
