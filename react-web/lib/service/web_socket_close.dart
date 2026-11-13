import 'react_exceptions.dart';

/// Close code the plugin uses when a token is revoked while its socket is open.
const int kRevokedCloseCode = 4401;

/// The error a socket stream reports for a close code, or null for an
/// ordinary close.
Exception? socketCloseError(int code) =>
    code == kRevokedCloseCode ? const ReactRevoked() : null;
