library;

import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:react_web_relay/src/agent_session.dart';
import 'package:react_web_relay/src/handshake_verifier.dart';
import 'package:react_web_relay/src/relay_broker.dart';
import 'package:react_web_relay/src/relay_frame.dart';
import 'package:react_web_relay/src/relay_limits.dart';
import 'package:react_web_relay/src/relay_sink.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_web_socket/shelf_web_socket.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

final RegExp _serverIdPattern = RegExp(r'^[0-9a-f]{64}$');

final class _ChannelSink implements RelaySink {
  final WebSocketChannel _channel;
  final int _maximumQueuedMessages;
  final Queue<String> _queue = Queue<String>();
  bool _draining = false;
  bool _closing = false;
  bool _closed = false;

  _ChannelSink(this._channel, this._maximumQueuedMessages);

  @override
  void send(RelayFrame frame) {
    if (_closing || _closed) return;
    if (_queue.length >= _maximumQueuedMessages) {
      closeWithError('outbound queue limit exceeded');
      return;
    }
    _queue.add(frame.encode());
    _startDrain();
  }

  @override
  void closeWithError(String message) {
    if (_closing || _closed) return;
    _closing = true;
    if (_queue.length >= _maximumQueuedMessages) _queue.clear();
    _queue.add(
      RelayFrame(
        type: RelayFrameType.error,
        payload: <String, dynamic>{'message': message},
      ).encode(),
    );
    _startDrain();
  }

  void markClosed() {
    _closed = true;
    _closing = true;
    _queue.clear();
  }

  void _startDrain() {
    if (_draining) return;
    unawaited(_drain());
  }

  Future<void> _drain() async {
    if (_draining || _closed) return;
    _draining = true;
    try {
      while (_queue.isNotEmpty && !_closed) {
        final String message = _queue.removeFirst();
        await _channel.sink.addStream(Stream<String>.value(message));
      }
    } on Object {
      _closed = true;
      _queue.clear();
    } finally {
      _draining = false;
    }
    if (_closing && !_closed && _queue.isEmpty) {
      _closed = true;
      await _channel.sink.close(4008, 'relay policy violation');
      return;
    }
    if (_queue.isNotEmpty) _startDrain();
  }
}

final class _MessageRateLimiter {
  final int _maximumMessages;
  final Duration _windowDuration;
  final Stopwatch _window = Stopwatch()..start();
  int _messageCount = 0;

  _MessageRateLimiter(this._maximumMessages, this._windowDuration);

  bool allow() {
    if (_window.elapsed >= _windowDuration) {
      _window
        ..reset()
        ..start();
      _messageCount = 0;
    }
    if (_messageCount >= _maximumMessages) return false;
    _messageCount++;
    return true;
  }
}

final class _ConnectionGuard {
  final RelayLimits _limits;
  final RelaySink _sink;
  late final _MessageRateLimiter _rateLimiter;

  _ConnectionGuard(this._limits, this._sink) {
    _rateLimiter = _MessageRateLimiter(
      _limits.maxMessagesPerWindow,
      _limits.messageRateWindow,
    );
  }

  RelayFrame? decode(Object? message) {
    if (message is! String) {
      _sink.closeWithError('binary frames are not supported');
      return null;
    }
    if (utf8.encode(message).length > _limits.maxFrameBytes) {
      _sink.closeWithError('frame size limit exceeded');
      return null;
    }
    if (!_rateLimiter.allow()) {
      _sink.closeWithError('message rate limit exceeded');
      return null;
    }
    final RelayFrame? frame = RelayFrame.decode(message);
    if (frame == null) {
      _sink.closeWithError('malformed relay frame');
      return null;
    }
    return frame;
  }
}

final class _AppSocketSession {
  final RelayBroker _broker;
  final RelaySink _sink;
  late final Timer _subscriptionTimer;
  String? _serverId;
  bool _disconnected = false;

  _AppSocketSession({
    required RelayBroker broker,
    required RelaySink sink,
    required Duration subscriptionTimeout,
  }) : _broker = broker,
       _sink = sink {
    _subscriptionTimer = Timer(
      subscriptionTimeout,
      () => _fail('app subscription timed out'),
    );
  }

  void onFrame(RelayFrame frame) {
    if (_disconnected) return;
    final String? serverId = _serverId;
    if (serverId == null) {
      if (frame.type != RelayFrameType.subscribe ||
          frame.serverId == null ||
          !_serverIdPattern.hasMatch(frame.serverId!)) {
        _fail('app must subscribe with a valid serverId');
        return;
      }
      _subscriptionTimer.cancel();
      _serverId = frame.serverId;
      _broker.subscribeApp(frame.serverId!, _sink);
      return;
    }
    if (frame.type != RelayFrameType.route ||
        frame.requestId == null ||
        (frame.serverId != null && frame.serverId != serverId)) {
      _fail('app sent an invalid routed request');
      return;
    }
    _broker.routeFromApp(serverId, _sink, frame);
  }

  void onDisconnect() {
    if (_disconnected) return;
    _disconnected = true;
    _subscriptionTimer.cancel();
    _broker.unregisterApp(_sink);
  }

  void _fail(String message) {
    if (_disconnected) return;
    onDisconnect();
    _sink.closeWithError(message);
  }
}

Future<HttpServer> startRelayServer({
  required int port,
  String address = '0.0.0.0',
  RelayBroker? broker,
  HandshakeVerifier? verifier,
  RelayLimits limits = const RelayLimits(),
  Iterable<String>? allowedAppOrigins,
}) async {
  final RelayBroker effectiveBroker = broker ?? RelayBroker(limits: limits);
  final RelayLimits effectiveLimits = effectiveBroker.limits;
  final HandshakeVerifier effectiveVerifier = verifier ?? HandshakeVerifier();
  final List<String>? effectiveAllowedOrigins = _normalizeAllowedOrigins(
    allowedAppOrigins,
  );
  effectiveLimits.validate();

  final Handler agentWsHandler = webSocketHandler((WebSocketChannel channel) {
    final _ChannelSink sink = _ChannelSink(
      channel,
      effectiveLimits.maxOutboundQueueMessages,
    );
    final _ConnectionGuard guard = _ConnectionGuard(effectiveLimits, sink);
    final AgentSession session = AgentSession(
      broker: effectiveBroker,
      verifier: effectiveVerifier,
      sink: sink,
      handshakeTimeout: effectiveLimits.handshakeTimeout,
    );
    Future<void> processing = Future<void>.value();
    channel.stream.listen(
      (Object? message) {
        final RelayFrame? frame = guard.decode(message);
        if (frame == null) {
          session.onDisconnect();
          return;
        }
        processing = processing.then((_) => session.onFrame(frame)).onError((
          Object error,
          StackTrace stackTrace,
        ) {
          session.onDisconnect();
          sink.closeWithError('agent frame processing failed');
        });
      },
      onDone: () {
        sink.markClosed();
        session.onDisconnect();
      },
      onError: (Object error, StackTrace stackTrace) {
        sink.markClosed();
        session.onDisconnect();
      },
    );
  }, pingInterval: effectiveLimits.pingInterval);

  final Handler appWsHandler = webSocketHandler(
    (WebSocketChannel channel) {
      final _ChannelSink sink = _ChannelSink(
        channel,
        effectiveLimits.maxOutboundQueueMessages,
      );
      final _ConnectionGuard guard = _ConnectionGuard(effectiveLimits, sink);
      final _AppSocketSession session = _AppSocketSession(
        broker: effectiveBroker,
        sink: sink,
        subscriptionTimeout: effectiveLimits.subscriptionTimeout,
      );
      channel.stream.listen(
        (Object? message) {
          final RelayFrame? frame = guard.decode(message);
          if (frame == null) {
            session.onDisconnect();
            return;
          }
          session.onFrame(frame);
        },
        onDone: () {
          sink.markClosed();
          session.onDisconnect();
        },
        onError: (Object error, StackTrace stackTrace) {
          sink.markClosed();
          session.onDisconnect();
        },
      );
    },
    allowedOrigins: effectiveAllowedOrigins,
    pingInterval: effectiveLimits.pingInterval,
  );

  Future<Response> handler(Request request) async {
    final String path = request.url.path;
    if (path == 'agent') return agentWsHandler(request);
    if (path == 'app') return appWsHandler(request);
    if (path == 'healthz' && request.method == 'GET') {
      return Response.ok(
        jsonEncode(<String, dynamic>{
          'status': 'ok',
          'onlineAgents': effectiveBroker.onlineCount,
          'appSessions': effectiveBroker.appSessionCount,
          'pendingRequests': effectiveBroker.pendingRequestCount,
        }),
        headers: <String, String>{
          HttpHeaders.contentTypeHeader: 'application/json; charset=utf-8',
          HttpHeaders.cacheControlHeader: 'no-store',
        },
      );
    }
    return Response.notFound(
      jsonEncode(<String, dynamic>{'error': 'not found'}),
      headers: <String, String>{
        HttpHeaders.contentTypeHeader: 'application/json; charset=utf-8',
        HttpHeaders.cacheControlHeader: 'no-store',
      },
    );
  }

  return shelf_io.serve(handler, address, port, poweredByHeader: null);
}

List<String>? _normalizeAllowedOrigins(Iterable<String>? origins) {
  if (origins == null) return null;
  final List<String> normalized = <String>[];
  for (final String rawOrigin in origins) {
    final String origin = rawOrigin.trim();
    if (origin.isEmpty) continue;
    final Uri uri = Uri.parse(origin);
    if (!uri.hasAuthority ||
        (uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.userInfo.isNotEmpty ||
        (uri.path.isNotEmpty && uri.path != '/') ||
        uri.query.isNotEmpty ||
        uri.fragment.isNotEmpty) {
      throw ArgumentError.value(rawOrigin, 'allowedAppOrigins');
    }
    normalized.add(
      '${uri.scheme.toLowerCase()}://${uri.authority.toLowerCase()}',
    );
  }
  return normalized.isEmpty ? null : List<String>.unmodifiable(normalized);
}
