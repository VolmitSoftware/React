/// Pairing a link end to end: redeem it, save the entry and start its
/// connection. The screens call [pairServer]; the redeemer is passed in so
/// tests run it without a server.
library;

import '../model/pairing_link.dart';
import '../model/server_credential.dart';
import '../service/pairing_redeemer.dart';
import 'fleet_scope.dart';

/// The entry for a redeemed [link]. Both paths of the link are kept; the
/// app uses whichever answers.
ServerCredential credentialFor(
  PairingLink link,
  PairedToken token, {
  required bool remember,
  required String id,
}) {
  final String? directUrl = link.directUrl;
  final Uri? direct = directUrl == null ? null : Uri.parse(directUrl);
  return ServerCredential(
    id: id,
    label: token.serverName,
    host: direct?.host ?? '',
    port: switch (direct) {
      null => 0,
      final Uri uri when uri.hasPort => uri.port,
      final Uri uri => uri.scheme == 'https' ? 443 : 80,
    },
    bearer: token.bearer,
    secure: direct?.scheme == 'https',
    basePath: direct == null || direct.path == '/' ? '' : direct.path,
    relayUrl: link.relayUrl,
    serverKey: link.serverKey,
    fingerprint: link.fingerprint,
    role: token.role,
    pairedTokenId: token.tokenId,
    scopes: token.scopes,
    expiresAt: token.expiresAt,
    remember: remember,
  );
}

/// Redeems [link], saves the server (a server already listed takes the new
/// pairing in place) and starts its connection. Returns the entry's id.
/// Throws [PairingRefused] when the link gives no token.
Future<String> pairServer(
  FleetController fleet,
  PairingLink link, {
  required PairingRedeemer redeemer,
  required bool remember,
  String? client,
}) async {
  final PairedToken token = await redeemer.redeem(link, client: client);
  final String id = fleet.fleetManager.add(
    credentialFor(
      link,
      token,
      remember: remember,
      id: DateTime.now().microsecondsSinceEpoch.toRadixString(36),
    ),
  );
  fleet.trackPaired(id);
  return id;
}
