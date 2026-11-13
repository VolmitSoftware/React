import '../model/role_info.dart';
import 'react_client.dart';

/// `/whoami` asked once per server session: every page of the server reads
/// the same answer instead of asking again. A failed answer is not kept,
/// so the next page asks again.
class CachedRoleClient implements IRoleClient {
  CachedRoleClient(this._client);

  final IRoleClient _client;
  Future<RoleInfo>? _request;

  /// The answer once it has arrived, for a page that opens after it.
  RoleInfo? known;

  @override
  Future<RoleInfo> whoami() => _request ??= _ask();

  Future<RoleInfo> _ask() async {
    try {
      final RoleInfo info = await _client.whoami();
      known = info;
      return info;
    } on Object {
      _request = null;
      rethrow;
    }
  }
}
