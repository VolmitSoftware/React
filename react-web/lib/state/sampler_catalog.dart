import 'dart:async';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/metric_history.dart';
import '../service/react_client.dart';

/// The samplers a server says it cannot report (Folia has no tick time),
/// from its metrics catalog. Live frames leave such samplers out, so
/// without this a page could only wait for them. Loaded once, when the
/// first page listens.
class SamplerCatalog extends ChangeNotifier {
  SamplerCatalog(this._client);

  final IHistoryClient? _client;
  Set<String> _notReported = const <String>{};
  bool _requested = false;
  bool _disposed = false;

  /// True when the server reported [samplerId] as one it cannot read.
  bool notReported(String samplerId) => _notReported.contains(samplerId);

  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    if (_requested) return;
    _requested = true;
    unawaited(_load());
  }

  Future<void> _load() async {
    final IHistoryClient? client = _client;
    if (client == null) return;
    try {
      final List<MetricHistoryDescriptor> catalog = await client
          .historyCatalog();
      if (_disposed) return;
      _notReported = <String>{
        for (final MetricHistoryDescriptor entry in catalog)
          if (entry.notReported) entry.id,
      };
      notifyListeners();
    } on Object {
      // Without a catalog nothing is marked; samplers simply wait.
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
