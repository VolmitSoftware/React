import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/identity_info.dart';
import '../service/react_client.dart';

/// The server's `/identity`, asked once when the first page reads or
/// listens. Until
/// it answers, and on a server that does not say, add-ons are plugins.
class ServerIdentity extends ChangeNotifier {
  ServerIdentity(this._client);

  final IReactClient _client;
  IdentityInfo? _info;
  bool _requested = false;
  bool _disposed = false;

  IdentityInfo? get info => _info;

  /// True on Fabric, Forge and NeoForge, where React says "mod".
  bool get modded => _info?.modded ?? false;

  /// True on a Velocity or BungeeCord proxy.
  bool get proxy => _info?.proxy ?? false;

  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    load();
  }

  /// Asks the server once; later calls do nothing.
  void load() {
    if (_requested) return;
    _requested = true;
    unawaited(_load());
  }

  Future<void> _load() async {
    try {
      final IdentityInfo info = await _client.identity();
      if (_disposed) return;
      _info = info;
      notifyListeners();
    } on Object {
      // Without an answer the page keeps saying "plugin".
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
