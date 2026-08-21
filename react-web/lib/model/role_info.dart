class RoleInfo {
  final String role;
  final List<String> scopes;

  const RoleInfo({required this.role, required this.scopes});

  factory RoleInfo.fromJson(Map<String, dynamic> j) {
    final Object? rawScopes = j['scopes'];
    final List<String> parsedScopes = rawScopes is List<dynamic>
        ? rawScopes.map((dynamic e) => e as String).toList()
        : const <String>[];
    return RoleInfo(role: j['role'] as String, scopes: parsedScopes);
  }

  bool get canRead => scopes.contains('read');
  bool get canExecute => scopes.contains('op:execute');
  bool get canExecuteConsole => scopes.contains('console:execute');
  bool get isAdmin => scopes.contains('admin');
  bool get isViewer => role == 'viewer';
}

bool readOnlyFor(RoleInfo? r) => r != null && !r.canExecute;
bool adminGatedDisabled(RoleInfo? r) => r != null && !r.isAdmin;
