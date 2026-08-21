import 'dart:convert';

import 'fleet_manager.dart';

class ServerTagsStore {
  static const String _storageKey = 'reactor.fleet.tags';

  final FleetStorage storage;
  Map<String, List<String>>? _cache;

  ServerTagsStore(this.storage);

  Map<String, List<String>> _load() {
    if (_cache != null) return _cache!;
    final String? raw = storage.read(_storageKey);
    if (raw == null) {
      _cache = <String, List<String>>{};
      return _cache!;
    }
    try {
      final Map<String, dynamic> decoded =
          jsonDecode(raw) as Map<String, dynamic>;
      _cache = decoded.map(
        (String k, dynamic v) => MapEntry<String, List<String>>(
          k,
          (v as List<dynamic>).cast<String>(),
        ),
      );
    } on Object {
      _cache = <String, List<String>>{};
    }
    return _cache!;
  }

  void _save() {
    final Map<String, List<String>> data = _load();
    storage.write(_storageKey, jsonEncode(data));
  }

  List<String> tagsFor(String id) =>
      List<String>.unmodifiable(_load()[id] ?? <String>[]);

  void setTags(String id, List<String> tags) {
    final Map<String, List<String>> data = _load();
    final List<String> cleaned =
        tags.where((String t) => t.isNotEmpty).toSet().toList()..sort();
    if (cleaned.isEmpty) {
      data.remove(id);
    } else {
      data[id] = cleaned;
    }
    _save();
  }

  List<String> allTags() {
    final Set<String> all = <String>{};
    for (final List<String> tags in _load().values) {
      all.addAll(tags);
    }
    final List<String> sorted = all.toList()..sort();
    return sorted;
  }

  List<String> serverIdsWithTag(String tag) => _load().entries
      .where((MapEntry<String, List<String>> e) => e.value.contains(tag))
      .map((MapEntry<String, List<String>> e) => e.key)
      .toList();
}
