import '../model/owner_info.dart';
import '../model/server_revisions.dart';
import '../service/react_client.dart';
import 'live_controller.dart';

/// The plugins or mods loaded on one server. Refetches when the server bumps
/// `owners` (a plugin enabled or disabled).
class OwnersController extends LiveController {
  final IOwnerClient _client;

  List<OwnerInfo>? _owners;

  OwnersController(
    IOwnerClient client, {
    super.serverId,
    super.revisions,
    super.refetchDelay,
  }) : _client = client,
       super(kind: RevisionKind.owners);

  List<OwnerInfo>? get owners => _owners;

  @override
  Future<void> fetch() async {
    final List<OwnerInfo> fresh = await _client.owners();
    if (!disposed) _owners = fresh;
  }
}
