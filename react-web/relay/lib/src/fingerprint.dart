import 'dart:convert';

import 'package:crypto/crypto.dart';

String fingerprint(List<int> pubKeyBytes) {
  final List<int> hash = sha256.convert(pubKeyBytes).bytes;
  return hash.map((int b) => b.toRadixString(16).padLeft(2, '0')).join();
}

List<int>? decodeServerPubKey(String base64UrlNoPad) {
  if (base64UrlNoPad.isEmpty) return null;
  try {
    final int paddingNeeded = (4 - base64UrlNoPad.length % 4) % 4;
    final String padded = base64UrlNoPad + '=' * paddingNeeded;
    return base64Url.decode(padded);
  } on FormatException {
    return null;
  }
}

String fingerprintOfBase64(String base64UrlPubKey) {
  return fingerprint(decodeServerPubKey(base64UrlPubKey)!);
}
