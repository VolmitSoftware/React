class ReactAuthException implements Exception {
  final String message;

  const ReactAuthException([this.message = 'Authentication failed (401)']);

  @override
  String toString() => 'ReactAuthException: $message';
}

/// A paired credential the server no longer accepts (401 or close code 4401).
/// Terminal for the entry: retrying cannot succeed.
class ReactRevoked extends ReactAuthException {
  const ReactRevoked([super.message = 'Access revoked']);

  @override
  String toString() => 'ReactRevoked: $message';
}

/// The address answered with a different server fingerprint than the pairing.
class ReactWrongServer extends ReactAuthException {
  const ReactWrongServer([
    super.message = 'This code or address belongs to another server',
  ]);

  @override
  String toString() => 'ReactWrongServer: $message';
}

class ReactUnavailable implements Exception {
  final String message;
  final int? status;

  const ReactUnavailable([this.message = 'Server unavailable', this.status]);

  @override
  String toString() => 'ReactUnavailable: $message';
}

class ReactBadRequest implements Exception {
  final String message;

  const ReactBadRequest([this.message = 'Bad request (400)']);

  @override
  String toString() => 'ReactBadRequest: $message';
}

class ReactForbidden implements Exception {
  final String message;

  const ReactForbidden([this.message = 'Forbidden (403): missing scope']);

  @override
  String toString() => 'ReactForbidden: $message';
}

class ReactNotFound implements Exception {
  final String message;

  const ReactNotFound([this.message = 'Not found (404)']);

  @override
  String toString() => 'ReactNotFound: $message';
}

class ReactConflict implements Exception {
  final String message;

  const ReactConflict([
    this.message =
        'Counter conflict (409): missing or replayed X-React-Counter',
  ]);

  @override
  String toString() => 'ReactConflict: $message';
}

/// A 409 caused by a stale `X-React-Counter`; carries the server's last
/// accepted counter so the client can re-base.
class ReactCounterConflict extends ReactConflict {
  final int lastCounter;

  const ReactCounterConflict(this.lastCounter)
    : super('Counter conflict (409): stale X-React-Counter');

  @override
  String toString() => 'ReactCounterConflict: $lastCounter';
}

class ReactRateLimited implements Exception {
  final String message;
  final Duration? retryAfter;

  const ReactRateLimited([this.message = 'Too many requests', this.retryAfter]);

  @override
  String toString() => 'ReactRateLimited: $message';
}

/// A config preset was requested while the editor holds unsaved edits.
class ReactPendingEdits implements Exception {
  final int count;

  const ReactPendingEdits(this.count);

  @override
  String toString() => 'ReactPendingEdits: $count unsaved edits';
}

enum ReactErrorKind {
  revoked,
  wrongServer,
  authentication,
  forbidden,
  notFound,
  badRequest,
  counterConflict,
  conflict,
  rateLimited,
  pendingEdits,
  unavailable,
  unexpected,
}

/// One mapping from any client error to the message a screen should show.
ReactErrorKind reactErrorKind(Object error) => switch (error) {
  ReactRevoked() => ReactErrorKind.revoked,
  ReactWrongServer() => ReactErrorKind.wrongServer,
  ReactAuthException() => ReactErrorKind.authentication,
  ReactForbidden() => ReactErrorKind.forbidden,
  ReactNotFound() => ReactErrorKind.notFound,
  ReactBadRequest() => ReactErrorKind.badRequest,
  ReactCounterConflict() => ReactErrorKind.counterConflict,
  ReactConflict() => ReactErrorKind.conflict,
  ReactRateLimited() => ReactErrorKind.rateLimited,
  ReactPendingEdits() => ReactErrorKind.pendingEdits,
  ReactUnavailable() => ReactErrorKind.unavailable,
  _ => ReactErrorKind.unexpected,
};
