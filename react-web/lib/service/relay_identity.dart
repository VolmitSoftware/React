library;

import 'dart:convert';
import 'package:crypto/crypto.dart';

abstract final class RelayIdentity {
  static String fingerprint(String base64UrlPubKey) {
    try {
      final String normalized = base64UrlPubKey
          .replaceAll('+', '-')
          .replaceAll('/', '_')
          .replaceAll('=', '');
      final int padCount = (4 - normalized.length % 4) % 4;
      final String padded = normalized + ('=' * padCount);
      final List<int> bytes = base64Url.decode(padded);
      final List<int> digest = sha256.convert(bytes).bytes;
      return digest
          .map((int byte) => byte.toRadixString(16).padLeft(2, '0'))
          .join();
    } catch (_) {
      return '';
    }
  }

  static bool pinMatches({
    required String? pinnedFingerprint,
    required String identityServerId,
  }) {
    if (pinnedFingerprint == null) return true;
    return pinnedFingerprint == identityServerId;
  }

  static bool codeConsistent({
    required String? serverPubKey,
    required String? fingerprint,
  }) {
    if (serverPubKey == null && fingerprint == null) return true;
    if (serverPubKey == null || fingerprint == null) return false;
    try {
      return RelayIdentity.fingerprint(serverPubKey) == fingerprint;
    } catch (_) {
      return false;
    }
  }
}
