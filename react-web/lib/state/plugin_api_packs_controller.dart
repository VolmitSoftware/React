import 'dart:async';

import '../model/plugin_api_pack.dart';
import '../model/server_revisions.dart';
import '../service/react_client.dart';
import 'live_controller.dart';

/// Plugin API packs of one server. Refetches when the server bumps `packs`
/// (an install, a removal or a hotload of the packs folder).
class PluginApiPacksController extends LiveController {
  final IPluginApiPackClient _client;

  PluginApiCatalog? _catalog;
  bool _saving = false;

  PluginApiPacksController(
    IPluginApiPackClient client, {
    super.serverId,
    super.revisions,
    super.refetchDelay,
  }) : _client = client,
       super(kind: RevisionKind.packs);

  PluginApiCatalog? get catalog => _catalog;
  bool get saving => _saving;

  @override
  Future<void> fetch() async {
    final PluginApiCatalog fresh = await _client.pluginApiPacks();
    if (!disposed) _catalog = fresh;
  }

  Future<T> _mutate<T>(Future<T> Function() send) async {
    ensureOpen();
    _saving = true;
    notify();
    try {
      return await send();
    } finally {
      _saving = false;
      notify();
    }
  }

  Future<PluginApiValidationResult> validate(String content) =>
      _mutate(() => _client.validatePluginApiPack(content));

  Future<PluginApiPack> install(String id, String content) async {
    final PluginApiPack pack = await _mutate(
      () => _client.installPluginApiPack(id, content),
    );
    unawaited(load());
    return pack;
  }

  Future<void> remove(String id) async {
    final PluginApiCatalog next = await _mutate(
      () => _client.removePluginApiPack(id),
    );
    if (disposed) return;
    _catalog = next;
    notify();
  }
}
