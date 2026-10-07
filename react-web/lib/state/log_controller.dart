import 'dart:async';
import 'dart:collection';

import '../service/react_client.dart';
import '../service/react_log_socket.dart';

class LogController {
  final ILogClient _client;
  final ILogSocket? _socket;
  void Function()? onChange;
  void Function(Object error)? onError;

  static const int maxLines = 1000;

  final ListQueue<String> _buffer = ListQueue<String>(maxLines);
  List<String>? _visible;
  Timer? _notifyTimer;
  bool _disposed = false;
  bool _paused = false;
  String _levelFilter = 'ALL';
  bool _loading = false;
  bool _started = false;
  StreamSubscription<String>? _sub;
  Timer? _pollTimer;

  LogController(
    ILogClient client, {
    ILogSocket? socket,
    this.onChange,
    this.onError,
  }) : _client = client,
       _socket = socket;

  List<String> get lines => List<String>.unmodifiable(_buffer);

  bool get paused => _paused;

  String get levelFilter => _levelFilter;

  bool get loading => _loading;

  List<String> get visible => _visible ??= _filterLines();

  List<String> _filterLines() {
    if (_levelFilter == 'ALL') {
      return List<String>.unmodifiable(_buffer);
    }
    final String token = _levelFilter.toUpperCase();
    return _buffer
        .where((String l) => l.toUpperCase().contains(token))
        .toList();
  }

  void _notify() {
    _notifyTimer?.cancel();
    _notifyTimer = null;
    if (!_disposed) onChange?.call();
  }

  void _scheduleNotify() {
    _notifyTimer ??= Timer(const Duration(milliseconds: 50), _notify);
  }

  Future<void> load({int limit = 200}) async {
    if (_disposed || _loading) return;
    _loading = true;
    _notify();
    try {
      final List<String> seeded = await _client.logs(limit: limit);
      if (_disposed) return;
      _buffer.clear();
      _buffer.addAll(seeded);
      _visible = null;
      _trimBuffer();
    } catch (e) {
      if (!_disposed) onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  void start() {
    final ILogSocket? socket = _socket;
    if (_started) return;
    _started = true;
    if (socket == null) {
      _pollTimer = Timer.periodic(const Duration(seconds: 2), (Timer _) {
        if (_paused || _loading) return;
        unawaited(load());
      });
      return;
    }
    _sub = socket.lines.listen((String line) {
      if (_paused || _disposed) return;
      _buffer.add(line);
      _visible = null;
      _trimBuffer();
      _scheduleNotify();
    });
  }

  void _trimBuffer() {
    while (_buffer.length > maxLines) {
      _buffer.removeFirst();
    }
  }

  void setPaused(bool v) {
    _paused = v;
    _notify();
  }

  void clear() {
    _buffer.clear();
    _visible = null;
    _notify();
  }

  void setLevelFilter(String level) {
    _levelFilter = level;
    _visible = null;
    _notify();
  }

  void dispose() {
    _disposed = true;
    _notifyTimer?.cancel();
    _notifyTimer = null;
    _pollTimer?.cancel();
    _pollTimer = null;
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
  }
}
