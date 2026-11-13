import 'react_exceptions.dart';

/// The single mapping from an HTTP status and decoded body to a typed error,
/// shared by the direct and relay clients. Returns null for 2xx.
Exception? reactErrorFor(
  int status,
  Map<String, dynamic>? body, {
  Duration? retryAfter,
}) {
  if (status >= 200 && status < 300) return null;
  final String message = reactErrorMessage(body);
  return switch (status) {
    400 => ReactBadRequest(message),
    401 =>
      _pairingEnded.contains(reactErrorCode(body))
          ? const ReactRevoked()
          : ReactAuthException(
              message == _fallbackMessage
                  ? 'Authentication failed (401)'
                  : message,
            ),
    403 => ReactForbidden(message),
    404 => ReactNotFound(message),
    409 => _counterConflict(body) ?? ReactConflict(message),
    429 => ReactRateLimited(message, retryAfter),
    _ => ReactUnavailable(message, status),
  };
}

const String _fallbackMessage = 'Request failed';

/// Codes of a 401 that mean the pairing ended: retrying cannot succeed.
const Set<String> _pairingEnded = <String>{'token_revoked', 'token_expired'};

String reactErrorMessage(Map<String, dynamic>? body) {
  final Object? error = body?['error'];
  if (error is Map<String, dynamic>) {
    final Object? message = error['message'];
    if (message is String) return message;
  }
  if (error is String && error.isNotEmpty) return error;
  return _fallbackMessage;
}

/// The `code` of an error body (`{"error": {"code": ...}}`), or the bare
/// string of the older `{"error": "<code>"}` shape.
String? reactErrorCode(Map<String, dynamic>? body) {
  final Object? error = body?['error'];
  if (error is String) return error;
  if (error is Map<String, dynamic>) {
    final Object? code = error['code'];
    if (code is String) return code;
  }
  return null;
}

ReactCounterConflict? _counterConflict(Map<String, dynamic>? body) {
  if (reactErrorCode(body) != 'counter_conflict') return null;
  final Object? error = body?['error'];
  final Object? last =
      body?['lastCounter'] ??
      (error is Map<String, dynamic> ? error['lastCounter'] : null);
  return ReactCounterConflict(last is num ? last.toInt() : 0);
}

/// `Retry-After` in whole seconds; HTTP dates are ignored.
Duration? parseRetryAfter(String? header) {
  if (header == null) return null;
  final int? seconds = int.tryParse(header.trim());
  if (seconds == null || seconds < 0) return null;
  return Duration(seconds: seconds);
}

/// Bounded wait before retrying a 429: the server's hint, else 1 s, never
/// more than 5 s.
Duration rateLimitDelay(Duration? retryAfter) {
  const Duration cap = Duration(seconds: 5);
  final Duration wanted = retryAfter ?? const Duration(seconds: 1);
  return wanted > cap ? cap : wanted;
}

const int kMaxRateLimitRetries = 2;
