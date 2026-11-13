/// Redeems a pairing link for a token: `POST /api/v1/pair` on the direct
/// path, else through the relay once the server has proved itself there.
/// A refusal says why in a [PairingFailure] the pairing card words.
library;

import 'dart:async' show TimeoutException;
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../model/pairing_link.dart';
import '../model/relay_frame.dart';
import 'react_status.dart';
import 'relay_connection_interface.dart';
import 'server_proof.dart';

/// Why a link did not give a token.
enum PairingFailure {
  /// The text is not a pairing link.
  notALink,
  used,
  expired,

  /// `/react web revoke` cancelled the link before it was opened.
  revoked,

  /// The server does not know the link (wrong secret, or it restarted).
  unknown,

  /// The page's origin is not in `allowedOrigins`.
  originNotAllowed,
  unreachable,

  /// An https page reaching plain http on this computer or the network
  /// got no answer: the browser most likely held or refused it.
  localNetworkBlocked,

  /// Another server answered, or the relay could not prove the server.
  wrongServer,
  busy,
  other,
}

class PairingRefused implements Exception {
  const PairingRefused(
    this.failure, {
    this.origin,
    this.configKey,
    this.configFile,
  });

  final PairingFailure failure;

  /// For [PairingFailure.originNotAllowed]: the refused origin and where
  /// to allow it, as the server names them.
  final String? origin;
  final String? configKey;
  final String? configFile;

  /// The link cannot work again; only a new one helps.
  bool get needsNewLink => const <PairingFailure>{
    PairingFailure.notALink,
    PairingFailure.used,
    PairingFailure.expired,
    PairingFailure.revoked,
    PairingFailure.unknown,
    PairingFailure.wrongServer,
  }.contains(failure);

  @override
  String toString() => 'PairingRefused: ${failure.name}';
}

enum PairPath { direct, relay }

/// What `POST /api/v1/pair` answered.
class PairedToken {
  const PairedToken({
    required this.bearer,
    required this.tokenId,
    required this.scopes,
    required this.expiresAt,
    required this.lifetime,
    required this.serverName,
    required this.path,
    this.role,
    this.label,
  });

  /// `Authorization: Bearer` for every later request. Shown once.
  final String bearer;
  final String tokenId;
  final String? role;
  final List<String> scopes;
  final DateTime expiresAt;
  final Duration lifetime;
  final String serverName;
  final String? label;

  /// The path that answered.
  final PairPath path;
}

typedef RelayOpener = IRelayConnection Function(
  String relayUrl,
  String fingerprint,
);

class PairingRedeemer {
  PairingRedeemer({
    http.Client Function()? httpClient,
    this.openRelay,
    this.pageOrigin,
    this.directTimeout = const Duration(seconds: 3),
    DateTime Function()? now,
  }) : _httpClient = httpClient ?? http.Client.new,
       _now = now ?? DateTime.now;

  final http.Client Function() _httpClient;

  /// Opens a relay connection; null where there is no browser.
  final RelayOpener? openRelay;

  /// This page's origin, to tell a blocked local address from a silent one.
  final String? pageOrigin;
  final Duration directTimeout;
  final DateTime Function() _now;

  /// Exchanges [link]'s secret for a token. [client] is how the server
  /// lists this browser. The direct path goes first; the relay is tried
  /// when the direct path is missing, silent or refuses. A server's own
  /// refusal wins over a silent path.
  Future<PairedToken> redeem(PairingLink link, {String? client}) async {
    if (!link.expiresAt.isAfter(_now().toUtc())) {
      throw const PairingRefused(PairingFailure.expired);
    }
    final Map<String, Object?> body = <String, Object?>{
      'tokenId': link.tokenId,
      'secret': link.secret,
      if (client != null && client.trim().isNotEmpty) 'client': client.trim(),
    };
    PairingRefused? refused;
    bool blocked = false;
    final String? direct = link.directUrl;
    if (direct != null) {
      try {
        return await _direct(link, direct, body);
      } on PairingRefused catch (refusal) {
        refused = refusal;
      } on _NoAnswer {
        blocked = browserMayBlock(pageOrigin, direct);
      }
    }
    final String? relay = link.relayUrl;
    final RelayOpener? open = openRelay;
    if (relay != null && open != null) {
      try {
        return await _relay(link, open(relay, link.fingerprint), body);
      } on PairingRefused catch (refusal) {
        // The relay is not origin-checked, so its answer says more.
        if (refused == null ||
            refused.failure == PairingFailure.originNotAllowed) {
          refused = refusal;
        }
      } on _NoAnswer {
        // Silence on the relay adds nothing to the direct path's answer.
      }
    }
    throw refused ??
        PairingRefused(
          blocked
              ? PairingFailure.localNetworkBlocked
              : PairingFailure.unreachable,
        );
  }

  Future<PairedToken> _direct(
    PairingLink link,
    String directUrl,
    Map<String, Object?> body,
  ) async {
    final Uri base = Uri.parse(directUrl);
    final String path = base.path.endsWith('/')
        ? base.path.substring(0, base.path.length - 1)
        : base.path;
    final http.Client client = _httpClient();
    try {
      final http.Response response = await client
          .post(
            base.replace(path: '$path/api/v1/pair'),
            headers: const <String, String>{'Content-Type': 'application/json'},
            body: jsonEncode(body),
          )
          .timeout(directTimeout);
      return _answer(
        link,
        response.statusCode,
        _decode(response.body),
        PairPath.direct,
      );
    } on PairingRefused {
      rethrow;
    } on http.ClientException {
      throw const _NoAnswer();
    } on TimeoutException {
      throw const _NoAnswer();
    } finally {
      client.close();
    }
  }

  Future<PairedToken> _relay(
    PairingLink link,
    IRelayConnection relay,
    Map<String, Object?> body,
  ) async {
    try {
      final String challenge = newChallenge();
      final RelayResponse hello = await relay.request(
        method: 'GET',
        path: '/api/v1/ping',
        headers: <String, String>{challengeHeader: challenge},
      );
      if (hello.status != 200) _refuse(hello.status, hello.body);
      if (!await _proven(link, hello, challenge)) {
        throw const PairingRefused(PairingFailure.wrongServer);
      }
      final String again = newChallenge();
      final RelayResponse answer = await relay.request(
        method: 'POST',
        path: '/api/v1/pair',
        headers: <String, String>{
          'Content-Type': 'application/json',
          challengeHeader: again,
        },
        body: jsonEncode(body),
      );
      if (answer.status == 200 && !await _proven(link, answer, again)) {
        throw const PairingRefused(PairingFailure.wrongServer);
      }
      return _answer(link, answer.status, answer.body, PairPath.relay);
    } finally {
      await relay.close();
    }
  }

  static Future<bool> _proven(
    PairingLink link,
    RelayResponse response,
    String challenge,
  ) => verifyServerProof(
    response.headers,
    challenge: challenge,
    serverKey: link.serverKey,
    fingerprint: link.fingerprint,
  );

  PairedToken _answer(
    PairingLink link,
    int status,
    Map<String, dynamic>? body,
    PairPath path,
  ) {
    if (status != 200) _refuse(status, body);
    final Object? data = body?['data'];
    if (data is! Map<String, dynamic>) {
      throw const PairingRefused(PairingFailure.other);
    }
    if (data['fingerprint'] != link.fingerprint) {
      throw const PairingRefused(PairingFailure.wrongServer);
    }
    final Object? bearer = data['token'];
    final Object? tokenId = data['tokenId'];
    final Object? scopes = data['scopes'];
    final DateTime? expiresAt = data['expiresAt'] is String
        ? DateTime.tryParse(data['expiresAt'] as String)
        : null;
    final Object? lifetime = data['lifetimeSeconds'];
    if (bearer is! String ||
        tokenId is! String ||
        scopes is! List<dynamic> ||
        expiresAt == null ||
        lifetime is! int) {
      throw const PairingRefused(PairingFailure.other);
    }
    final Object? name = data['serverName'];
    final Object? role = data['role'];
    final Object? label = data['label'];
    return PairedToken(
      bearer: bearer,
      tokenId: tokenId,
      role: role is String && role != 'custom' ? role : null,
      scopes: <String>[
        for (final Object? scope in scopes)
          if (scope is String) scope,
      ],
      expiresAt: expiresAt.toUtc(),
      lifetime: Duration(seconds: lifetime),
      serverName: name is String && name.trim().isNotEmpty
          ? name.trim()
          : link.serverName,
      label: label is String ? label : null,
      path: path,
    );
  }

  /// Throws the failure an error answer stands for: a [PairingRefused]
  /// when the server refused, [_NoAnswer] when the relay could not reach it.
  static Never _refuse(int status, Map<String, dynamic>? body) {
    final String? code = reactErrorCode(body);
    final Object? error = body?['error'];
    String? extra(String key) =>
        error is Map<String, dynamic> && error[key] is String
        ? error[key] as String
        : null;
    switch (code) {
      case 'pairing_used':
        throw const PairingRefused(PairingFailure.used);
      case 'pairing_expired':
        throw const PairingRefused(PairingFailure.expired);
      case 'pairing_revoked':
        throw const PairingRefused(PairingFailure.revoked);
      case 'pairing_unknown':
        throw const PairingRefused(PairingFailure.unknown);
      case 'origin_not_allowed':
        throw PairingRefused(
          PairingFailure.originNotAllowed,
          origin: extra('origin'),
          configKey: extra('configKey'),
          configFile: extra('configFile'),
        );
      case 'rate_limited' || 'relay_rate_limited' || 'relay_busy':
        throw const PairingRefused(PairingFailure.busy);
      case 'server_offline' || 'relay_timeout' || 'bad_gateway':
        throw const _NoAnswer();
    }
    if (code == null && status >= 500) throw const _NoAnswer();
    if (status == 429) throw const PairingRefused(PairingFailure.busy);
    throw const PairingRefused(PairingFailure.other);
  }

  static Map<String, dynamic>? _decode(String text) {
    try {
      final Object? decoded = jsonDecode(text);
      return decoded is Map<String, dynamic> ? decoded : null;
    } on FormatException {
      return null;
    }
  }
}

/// A path that gave no answer of the server's own.
class _NoAnswer implements Exception {
  const _NoAnswer();
}
