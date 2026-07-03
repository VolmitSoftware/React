class ReactAuthException implements Exception {
  final String message;

  const ReactAuthException([this.message = 'Authentication failed (401)']);

  @override
  String toString() => 'ReactAuthException: $message';
}

class ReactUnavailable implements Exception {
  final String message;

  const ReactUnavailable([this.message = 'Server unavailable']);

  @override
  String toString() => 'ReactUnavailable: $message';
}

class ReactForbidden implements Exception {
  final String message;

  const ReactForbidden(
      [this.message = 'Forbidden (403): missing op:execute scope']);

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

  const ReactConflict(
      [this.message =
          'Counter conflict (409): missing or replayed X-React-Counter']);

  @override
  String toString() => 'ReactConflict: $message';
}
