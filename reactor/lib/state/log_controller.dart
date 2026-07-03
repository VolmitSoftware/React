import 'dart:async';

import '../service/react_client.dart';
import '../service/react_log_socket.dart';

class LogController {
  final ILogClient _client;
  final ILogSocket? _socket;
  void Function()? onChange;
  void Function(Object error)? onError;

  static const int maxLines = 1000;

  final List<String> _buffer = <String>[];
  bool _paused = false;
  String _levelFilter = 'ALL';
  bool _loading = false;
  bool _started = false;
  StreamSubscription<String>? _sub;

  LogController(
    ILogClient client, {
    ILogSocket? socket,
    this.onChange,
    this.onError,
  })  : _client = client,
        _socket = socket;

  List<String> get lines => List<String>.unmodifiable(_buffer);

  bool get paused => _paused;

  String get levelFilter => _levelFilter;

  bool get loading => _loading;

  List<String> get visible {
    if (_levelFilter == 'ALL') {
      return List<String>.unmodifiable(_buffer);
    }
    final String token = _levelFilter.toUpperCase();
    return _buffer
        .where((String l) => l.toUpperCase().contains(token))
        .toList();
  }

  void _notify() => onChange?.call();

  Future<void> load({int limit = 200}) async {
    _loading = true;
    _notify();
    try {
      final List<String> seeded = await _client.logs(limit: limit);
      _buffer.clear();
      _buffer.addAll(seeded);
      _trimBuffer();
    } catch (e) {
      onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  void start() {
    final ILogSocket? socket = _socket;
    if (_started || socket == null) return;
    _started = true;
    _sub = socket.lines.listen((String line) {
      if (_paused) return;
      _buffer.add(line);
      _trimBuffer();
      _notify();
    });
  }

  void _trimBuffer() {
    while (_buffer.length > maxLines) {
      _buffer.removeAt(0);
    }
  }

  void setPaused(bool v) {
    _paused = v;
    _notify();
  }

  void clear() {
    _buffer.clear();
    _notify();
  }

  void setLevelFilter(String level) {
    _levelFilter = level;
    _notify();
  }

  void dispose() {
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
  }
}
