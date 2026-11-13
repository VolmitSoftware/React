/// A pairing link as `/react web pair` prints it:
/// `<appUrl>/pair#RCT3.<base64url JSON>`. Everything the pairing card shows
/// comes from the link; nothing is asked of the server before Pair. Pure
/// Dart, so the rules are unit tested.
library;

import 'dart:convert';

import 'package:crypto/crypto.dart';

/// The prefix of the code a pairing link carries.
const String pairingPrefix = 'RCT3.';

/// The longest code a link may carry.
const int maxLinkCodeLength = 4096;

const List<String> _fields = <String>[
  'v',
  'directUrl',
  'relayUrl',
  'fingerprint',
  'serverKey',
  'tokenId',
  'secret',
  'expiresAt',
  'serverName',
  'scopes',
  'role',
  'lifetimeSeconds',
  'label',
];

final RegExp _codePattern = RegExp(r'RCT3\.[A-Za-z0-9_-]+');
final RegExp _tokenIdPattern = RegExp(r'^tok_[A-Za-z0-9_-]{16}$');
final RegExp _secretPattern = RegExp(r'^[A-Za-z0-9_-]{43}$');
final RegExp _fingerprintPattern = RegExp(r'^[0-9a-f]{64}$');

/// The `RCT3.` code in [text]: a whole link, a bare code, or either split
/// over lines by a console or chat window. Null when there is none.
String? linkCodeIn(String text) {
  final String joined = text.replaceAll(RegExp(r'\s+'), '');
  return _codePattern.firstMatch(joined)?.group(0);
}

/// One link's contents. The secret is redeemed once for a token.
class PairingLink {
  const PairingLink({
    required this.code,
    required this.fingerprint,
    required this.serverKey,
    required this.tokenId,
    required this.secret,
    required this.expiresAt,
    required this.serverName,
    required this.scopes,
    required this.lifetime,
    this.directUrl,
    this.relayUrl,
    this.role,
    this.label,
  });

  /// The `RCT3.` code the link was read from.
  final String code;
  final String? directUrl;
  final String? relayUrl;

  /// SHA-256 of [serverKey]'s bytes, 64 lowercase hex.
  final String fingerprint;

  /// The server's Ed25519 key, X.509 SubjectPublicKeyInfo in base64url.
  final String serverKey;
  final String tokenId;
  final String secret;

  /// When the link stops working.
  final DateTime expiresAt;
  final String serverName;

  /// What the pairing may do, in the server's order.
  final List<String> scopes;

  /// viewer, operator or admin; null for a custom scope set.
  final String? role;

  /// How long the pairing lasts without use.
  final Duration lifetime;

  /// The link's name, as `/react web tokens` lists it.
  final String? label;

  /// The link in [text], or null when [text] holds no valid link.
  static PairingLink? parse(String text) {
    final String? code = linkCodeIn(text);
    if (code == null || code.length > maxLinkCodeLength) return null;
    try {
      final Object? json = jsonDecode(
        utf8.decode(
          base64Url.decode(
            base64Url.normalize(code.substring(pairingPrefix.length)),
          ),
        ),
      );
      if (json is! Map<String, dynamic>) return null;
      return _read(code, json);
    } on FormatException {
      return null;
    }
  }

  static PairingLink? _read(String code, Map<String, dynamic> json) {
    if (json.keys.any((String key) => !_fields.contains(key))) return null;
    if (json['v'] != 1) return null;
    final Object? direct = json['directUrl'];
    final Object? relay = json['relayUrl'];
    final Object? fingerprint = json['fingerprint'];
    final Object? serverKey = json['serverKey'];
    final Object? tokenId = json['tokenId'];
    final Object? secret = json['secret'];
    final Object? expires = json['expiresAt'];
    final Object? name = json['serverName'];
    final Object? scopes = json['scopes'];
    final Object? role = json['role'];
    final Object? lifetime = json['lifetimeSeconds'];
    final Object? label = json['label'];
    if ((direct != null &&
            (direct is! String ||
                !_validUrl(direct, const <String>{'http', 'https'}))) ||
        (relay != null &&
            (relay is! String ||
                !_validUrl(relay, const <String>{'ws', 'wss'}))) ||
        (direct == null && relay == null)) {
      return null;
    }
    if (fingerprint is! String ||
        !_fingerprintPattern.hasMatch(fingerprint) ||
        serverKey is! String ||
        keyFingerprint(serverKey) != fingerprint) {
      return null;
    }
    if (tokenId is! String ||
        !_tokenIdPattern.hasMatch(tokenId) ||
        secret is! String ||
        !_secretPattern.hasMatch(secret)) {
      return null;
    }
    final DateTime? expiresAt = expires is String
        ? DateTime.tryParse(expires)
        : null;
    if (expiresAt == null || name is! String || name.trim().isEmpty) {
      return null;
    }
    if (scopes is! List<dynamic> ||
        scopes.isEmpty ||
        scopes.any((dynamic scope) => scope is! String)) {
      return null;
    }
    if (role != null && role is! String) return null;
    if (lifetime is! int || lifetime < 60) return null;
    if (label != null && label is! String) return null;
    final String named = (label as String? ?? '').trim();
    return PairingLink(
      code: code,
      directUrl: direct as String?,
      relayUrl: relay as String?,
      fingerprint: fingerprint,
      serverKey: serverKey,
      tokenId: tokenId,
      secret: secret,
      expiresAt: expiresAt.toUtc(),
      serverName: name.trim(),
      scopes: List<String>.unmodifiable(scopes.cast<String>()),
      role: role as String?,
      lifetime: Duration(seconds: lifetime),
      label: named.isEmpty ? null : named,
    );
  }

  static bool _validUrl(String value, Set<String> schemes) {
    final Uri? uri = Uri.tryParse(value);
    return uri != null &&
        schemes.contains(uri.scheme) &&
        uri.host.isNotEmpty &&
        uri.userInfo.isEmpty &&
        !uri.hasQuery &&
        !uri.hasFragment;
  }
}

/// SHA-256 of a base64url key's bytes in lowercase hex; '' when [key] is not
/// base64url.
String keyFingerprint(String key) {
  try {
    return sha256
        .convert(base64Url.decode(base64Url.normalize(key)))
        .toString();
  } on FormatException {
    return '';
  }
}

/// How the server lists this browser in `/react web tokens`, from its user
/// agent: "Chrome on macOS", or "React Web" when it cannot tell.
String browserName(String userAgent) {
  final String browser = switch (userAgent) {
    final String ua when ua.contains('Edg/') => 'Edge',
    final String ua when ua.contains('OPR/') => 'Opera',
    final String ua when ua.contains('Firefox/') => 'Firefox',
    final String ua when ua.contains('Chrome/') => 'Chrome',
    final String ua when ua.contains('Safari/') => 'Safari',
    _ => '',
  };
  final String system = switch (userAgent) {
    final String ua when ua.contains('iPhone') || ua.contains('iPad') => 'iOS',
    final String ua when ua.contains('Android') => 'Android',
    final String ua when ua.contains('Mac OS X') || ua.contains('Macintosh') =>
      'macOS',
    final String ua when ua.contains('Windows') => 'Windows',
    final String ua when ua.contains('Linux') => 'Linux',
    _ => '',
  };
  if (browser.isEmpty) return 'React Web';
  return system.isEmpty ? browser : '$browser on $system';
}

/// Whether [host] is this computer, a private or link-local IPv4 address,
/// or a `.local` name.
bool isLocalNetworkHost(String host) {
  final String name = host.toLowerCase();
  if (name == 'localhost' || name == '::1' || name.endsWith('.local')) {
    return true;
  }
  final List<String> parts = name.split('.');
  if (parts.length != 4) return false;
  final List<int?> octets = <int?>[
    for (final String part in parts) int.tryParse(part),
  ];
  if (octets.any((int? octet) => octet == null || octet < 0 || octet > 255)) {
    return false;
  }
  final int first = octets[0]!;
  final int second = octets[1]!;
  return first == 127 ||
      first == 10 ||
      (first == 172 && second >= 16 && second <= 31) ||
      (first == 192 && second == 168) ||
      (first == 169 && second == 254);
}

/// Whether the browser may stop a page at [pageOrigin] from reaching [url]
/// before any answer: an https page calling plain http on this computer or
/// the local network, which mixed-content rules or an unanswered local
/// network access prompt can refuse or hold.
bool browserMayBlock(String? pageOrigin, String url) {
  if (pageOrigin == null || Uri.tryParse(pageOrigin)?.scheme != 'https') {
    return false;
  }
  final Uri? target = Uri.tryParse(url);
  return target != null &&
      target.scheme == 'http' &&
      isLocalNetworkHost(target.host);
}
