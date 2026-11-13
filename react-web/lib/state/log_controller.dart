import 'dart:async';
import 'dart:collection';

import 'package:jaspr/jaspr.dart' show ChangeNotifier;

import '../model/log_line.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../service/react_log_socket.dart';

enum LogStreamState { connecting, live, reconnecting, polling, revoked }

/// The console of one server: a live socket when the server offers one,
/// polling otherwise. A dropped socket reconnects with backoff and the gap is
/// filled over REST, so a quiet period or a network blip loses no lines.
/// Lines that arrive while paused are held and shown on resume.
class LogController extends ChangeNotifier {
  final ILogClient _client;
  final ILogSocket Function()? _socketFactory;
  final List<Duration> reconnectDelays;
  final Duration pollInterval;

  /// When set, the first listener loads and starts the stream, and the
  /// stream closes [suspendAfter] after the last listener leaves.
  final bool autoSuspend;
  final Duration suspendAfter;

  static const int maxLines = 1000;

  final List<LogLine> _buffer = <LogLine>[];
  final LinkedHashSet<int> _seenSeqs = LinkedHashSet<int>();
  final List<LogLine> _held = <LogLine>[];
  List<String>? _lines;
  Timer? _notifyTimer;
  Timer? _reconnectTimer;
  Timer? _pollTimer;
  bool _disposed = false;
  bool _paused = false;
  bool _loading = false;
  bool _started = false;
  bool _filling = false;
  bool _suspended = false;
  int _listeners = 0;
  Timer? _suspendTimer;
  int _reconnectAttempt = 0;
  int _epochStart = 0;
  int _dropped = 0;
  int? _lastSeq;
  String? _anchorText;
  LogStreamState _state = LogStreamState.connecting;
  Object? _error;
  ILogSocket? _pendingSocket;
  ILogSocket? _socket;
  StreamSubscription<LogBatch>? _sub;

  /// [socketFactory] opens a fresh socket for each (re)connect. [socket] is a
  /// single already-open socket; when it closes the controller polls.
  LogController(
    ILogClient client, {
    ILogSocket? socket,
    this._socketFactory,
    this.reconnectDelays = const <Duration>[
      Duration(seconds: 1),
      Duration(seconds: 2),
      Duration(seconds: 5),
    ],
    this.pollInterval = const Duration(seconds: 2),
    this.autoSuspend = false,
    this.suspendAfter = const Duration(seconds: 30),
  }) : _client = client,
       _pendingSocket = socket;

  /// The lines on screen, oldest first; the same list until one changes.
  List<String> get lines =>
      _lines ??= List<String>.unmodifiable(_buffer.map((LogLine l) => l.text));

  bool get paused => _paused;

  /// Lines received while paused, shown on resume.
  int get heldCount => _held.length;

  /// Lines the server reported dropping before they reached this page.
  int get droppedCount => _dropped;

  LogStreamState get streamState => _state;

  bool get reconnecting => _state == LogStreamState.reconnecting;

  bool get loading => _loading;

  /// Why the last request for lines failed (a 403, the server down); null
  /// once one succeeds.
  Object? get error => _error;

  void _notify() {
    _notifyTimer?.cancel();
    _notifyTimer = null;
    if (_disposed) return;
    notifyListeners();
  }

  void _scheduleNotify() {
    _notifyTimer ??= Timer(const Duration(milliseconds: 50), _notify);
  }

  void _setState(LogStreamState next) {
    if (_state == next) return;
    _state = next;
    _scheduleNotify();
  }

  /// Replaces the view with the server's latest [limit] lines.
  Future<void> load({int limit = 200}) async {
    if (_disposed || _loading) return;
    _loading = true;
    _notify();
    try {
      final List<LogLine> seeded = await _fetch(limit: limit);
      if (_disposed) return;
      _buffer.clear();
      _lines = null;
      _seenSeqs.clear();
      _held.clear();
      _lastSeq = null;
      _epochStart = 0;
      _error = null;
      _receive(seeded);
    } on Object catch (e) {
      _onRestError(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  Future<List<LogLine>> _fetch({int limit = 200, int? after}) async {
    final ILogClient client = _client;
    if (client is ILogLineClient) {
      return (client as ILogLineClient).logLines(limit: limit, after: after);
    }
    return (await client.logs(limit: limit))
        .map((String line) => LogLine(line))
        .toList();
  }

  @override
  void addListener(void Function() listener) {
    super.addListener(listener);
    _listeners++;
    if (!autoSuspend) return;
    _suspendTimer?.cancel();
    _suspendTimer = null;
    if (!_started || _suspended) scheduleMicrotask(_activate);
  }

  @override
  void removeListener(void Function() listener) {
    super.removeListener(listener);
    if (_listeners > 0) _listeners--;
    if (!autoSuspend || _listeners > 0 || _disposed) return;
    _suspendTimer?.cancel();
    _suspendTimer = Timer(suspendAfter, _suspend);
  }

  void _activate() {
    if (_disposed || _listeners == 0) return;
    if (!_started) {
      unawaited(load());
      start();
      return;
    }
    if (!_suspended) return;
    _suspended = false;
    final ILogSocket Function()? factory = _socketFactory;
    if (factory == null) {
      _startPolling();
    } else {
      _attach(factory());
    }
    unawaited(_fillGap(markLive: factory != null, limit: maxLines));
  }

  void _suspend() {
    _suspendTimer = null;
    if (_disposed || _listeners > 0 || _state == LogStreamState.revoked) {
      return;
    }
    _suspended = true;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _pollTimer?.cancel();
    _pollTimer = null;
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
    _socket = null;
    _state = LogStreamState.connecting;
  }

  void start() {
    if (_started || _disposed) return;
    _started = true;
    final ILogSocket? first = _pendingSocket ?? _socketFactory?.call();
    _pendingSocket = null;
    if (first == null) {
      _startPolling();
      return;
    }
    _attach(first);
    _setState(LogStreamState.live);
  }

  void _attach(ILogSocket socket) {
    _sub?.cancel();
    _socket = socket;
    _sub = socket.batches.listen(
      (LogBatch batch) {
        if (!identical(_socket, socket) || _disposed) return;
        _reconnectAttempt = 0;
        _dropped += batch.dropped;
        final int? beforeBatch = _lastSeq;
        _receive(batch.lines, fromSocket: true);
        if (batch.dropped > 0) {
          unawaited(_fillGap(limit: maxLines, from: beforeBatch));
        }
        _setState(LogStreamState.live);
      },
      onError: (Object error) {
        if (!identical(_socket, socket) || _disposed) return;
        if (error is ReactRevoked) _revoke(error);
      },
      onDone: () {
        if (!identical(_socket, socket) || _disposed) return;
        _onSocketClosed();
      },
    );
  }

  void _onSocketClosed() {
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
    _socket = null;
    if (_state == LogStreamState.revoked) return;
    if (_socketFactory == null) {
      _startPolling();
      return;
    }
    _setState(LogStreamState.reconnecting);
    final Duration delay =
        reconnectDelays[_reconnectAttempt.clamp(0, reconnectDelays.length - 1)];
    _reconnectAttempt++;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(delay, () {
      _reconnectTimer = null;
      if (_disposed || _state == LogStreamState.revoked) return;
      _attach(_socketFactory());
      unawaited(_fillGap(markLive: true, limit: maxLines));
    });
  }

  void _startPolling() {
    _setState(LogStreamState.polling);
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(pollInterval, (Timer _) {
      if (_loading) return;
      unawaited(_fillGap());
    });
  }

  /// Fetches what the page missed: lines after the last seq, or, on servers
  /// without seq, the lines after the last text this page showed.
  Future<void> _fillGap({
    bool markLive = false,
    int limit = 200,
    int? from,
  }) async {
    if (_disposed || _filling) return;
    _filling = true;
    try {
      final int? after = from ?? _lastSeq;
      final List<LogLine> fetched = await _fetch(limit: limit, after: after);
      if (_disposed) return;
      final int? newest = _maxSeq(fetched);
      final int? last = _lastSeq;
      if (from == null && newest != null && last != null && newest < last) {
        _resetSeq();
      }
      if (_error != null) {
        _error = null;
        _scheduleNotify();
      }
      _receive(_afterAnchor(fetched));
      if (markLive && _socket != null) _setState(LogStreamState.live);
    } on Object catch (e) {
      _onRestError(e);
    } finally {
      _filling = false;
    }
  }

  List<LogLine> _afterAnchor(List<LogLine> fetched) {
    if (fetched.any((LogLine l) => l.seq != null)) return fetched;
    final String? anchor = _anchorText;
    if (anchor == null) return fetched;
    final int index = fetched.lastIndexWhere((LogLine l) => l.text == anchor);
    return index < 0 ? fetched : fetched.sublist(index + 1);
  }

  void _onRestError(Object error) {
    if (_disposed) return;
    if (error is ReactAuthException) {
      _revoke(error);
      return;
    }
    if (error is ReactForbidden) {
      _pollTimer?.cancel();
      _pollTimer = null;
    }
    _error = error;
    _scheduleNotify();
  }

  void _revoke(Object error) {
    _state = LogStreamState.revoked;
    _reconnectTimer?.cancel();
    _pollTimer?.cancel();
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
    _socket = null;
    _error = error;
    _scheduleNotify();
  }

  static int? _maxSeq(List<LogLine> lines) {
    int? max;
    for (final LogLine line in lines) {
      final int? seq = line.seq;
      if (seq != null && (max == null || seq > max)) max = seq;
    }
    return max;
  }

  /// The server restarted and numbers lines from 1 again. Lines already shown
  /// stay; ordering starts over after them.
  void _resetSeq() {
    _seenSeqs.clear();
    _lastSeq = null;
    _epochStart = _buffer.length;
  }

  /// Drops lines already seen (by seq), advances the gap-fill anchors, then
  /// shows the rest or holds them while paused. The socket only ever sends
  /// newer lines, so an unseen seq at or below the last one there means the
  /// server restarted.
  void _receive(List<LogLine> incoming, {bool fromSocket = false}) {
    final List<LogLine> fresh = <LogLine>[];
    for (final LogLine line in incoming) {
      final int? seq = line.seq;
      if (seq != null) {
        final int? previous = _lastSeq;
        if (fromSocket &&
            previous != null &&
            seq <= previous &&
            !_seenSeqs.contains(seq)) {
          _resetSeq();
        }
        if (!_seenSeqs.add(seq)) continue;
        if (_seenSeqs.length > maxLines * 2) _seenSeqs.remove(_seenSeqs.first);
        final int? last = _lastSeq;
        if (last == null || seq > last) _lastSeq = seq;
      }
      _anchorText = line.text;
      fresh.add(line);
    }
    if (fresh.isEmpty) return;
    if (_paused) {
      _held.addAll(fresh);
      while (_held.length > maxLines) {
        _held.removeAt(0);
      }
    } else {
      _append(fresh);
    }
    _scheduleNotify();
  }

  void _append(List<LogLine> lines) {
    for (final LogLine line in lines) {
      final int? seq = line.seq;
      final int? tail = _buffer.isEmpty ? null : _buffer.last.seq;
      if (seq != null && tail != null && seq < tail) {
        _insertOrdered(line);
      } else {
        _buffer.add(line);
      }
    }
    _trimBuffer();
    _lines = null;
  }

  /// A late line (gap fill after a drop) goes where its seq belongs.
  void _insertOrdered(LogLine line) {
    final int seq = line.seq!;
    int index = _buffer.length;
    while (index > _epochStart) {
      final int? before = _buffer[index - 1].seq;
      if (before == null || before < seq) break;
      index--;
    }
    if (index == 0 && _buffer.length >= maxLines) return;
    _buffer.insert(index, line);
  }

  void _trimBuffer() {
    if (_buffer.length > maxLines) {
      final int removed = _buffer.length - maxLines;
      _buffer.removeRange(0, removed);
      _epochStart = (_epochStart - removed).clamp(0, _buffer.length);
    }
  }

  void setPaused(bool v) {
    _paused = v;
    if (!v && _held.isNotEmpty) {
      final List<LogLine> held = List<LogLine>.of(_held);
      _held.clear();
      _append(held);
    }
    _notify();
  }

  /// Empties the view. Polling and gap fills continue after the last line
  /// seen, so cleared lines do not come back.
  void clear() {
    _buffer.clear();
    _held.clear();
    _epochStart = 0;
    _lines = null;
    _notify();
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _notifyTimer?.cancel();
    _notifyTimer = null;
    _pollTimer?.cancel();
    _pollTimer = null;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _suspendTimer?.cancel();
    _suspendTimer = null;
    _sub?.cancel();
    _sub = null;
    unawaited(_socket?.close() ?? Future<void>.value());
    unawaited(_pendingSocket?.close() ?? Future<void>.value());
    _socket = null;
    super.dispose();
  }
}
