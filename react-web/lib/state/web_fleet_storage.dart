import 'package:web/web.dart' as web;

import 'fleet_manager.dart';

/// localStorage ([WebFleetStorage.new]) or sessionStorage
/// ([WebFleetStorage.session], gone with the tab). A browser that refuses
/// storage (a private window, a full quota) keeps values for this page only.
class WebFleetStorage implements FleetStorage {
  WebFleetStorage() : _area = _open(() => web.window.localStorage);

  WebFleetStorage.session() : _area = _open(() => web.window.sessionStorage);

  final web.Storage? _area;
  final Map<String, String> _page = <String, String>{};

  static web.Storage? _open(web.Storage Function() area) {
    try {
      return area();
    } on Object {
      return null;
    }
  }

  @override
  String? read(String key) {
    try {
      return _area?.getItem(key) ?? _page[key];
    } on Object {
      return _page[key];
    }
  }

  @override
  void write(String key, String value) {
    _page[key] = value;
    try {
      _area?.setItem(key, value);
    } on Object {
      // Kept for this page only.
    }
  }

  @override
  void remove(String key) {
    _page.remove(key);
    try {
      _area?.removeItem(key);
    } on Object {
      // Nothing stored.
    }
  }
}
