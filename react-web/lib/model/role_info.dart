class RoleInfo {
  final String role;
  final List<String> scopes;

  /// When the pairing ends unless used (`/whoami` since links).
  final DateTime? expiresAt;

  const RoleInfo({required this.role, required this.scopes, this.expiresAt});

  factory RoleInfo.fromJson(Map<String, dynamic> j) {
    final Object? rawScopes = j['scopes'];
    final List<String> parsedScopes = rawScopes is List<dynamic>
        ? rawScopes.map((dynamic e) => e as String).toList()
        : const <String>[];
    final Object? expires = j['expiresAt'];
    return RoleInfo(
      role: j['role'] as String,
      scopes: parsedScopes,
      expiresAt: expires is String ? DateTime.tryParse(expires)?.toUtc() : null,
    );
  }

  bool get canRead => scopes.contains('read');
  bool get canExecute => scopes.contains('op:execute');
  bool get canExecuteConsole => scopes.contains('console:execute');
  bool get isAdmin => scopes.contains('admin');
  bool get isViewer => role == 'viewer';
}

bool readOnlyFor(RoleInfo? r) => r != null && !r.canExecute;
bool adminGatedDisabled(RoleInfo? r) => r != null && !r.isAdmin;
