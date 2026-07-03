library;

import 'dart:async';
import 'dart:io';

import 'package:reactor_relay/src/agent_session.dart';
import 'package:reactor_relay/src/handshake_verifier.dart';
import 'package:reactor_relay/src/relay_broker.dart';
import 'package:reactor_relay/src/relay_frame.dart';
import 'package:reactor_relay/src/relay_sink.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_web_socket/shelf_web_socket.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

class _ChannelSink implements RelaySink {
  final WebSocketChannel _channel;

  _ChannelSink(this._channel);

  @override
  void send(RelayFrame frame) {
    _channel.sink.add(frame.encode());
  }

  @override
  void closeWithError(String message) {
    _channel.sink.add(RelayFrame(
      type: RelayFrameType.error,
      payload: <String, dynamic>{'message': message},
    ).encode());
    _channel.sink.close();
  }
}

Future<HttpServer> startRelayServer({
  required int port,
  String address = '0.0.0.0',
  RelayBroker? broker,
  HandshakeVerifier? verifier,
}) async {
  final RelayBroker effectiveBroker = broker ?? RelayBroker();
  final HandshakeVerifier effectiveVerifier = verifier ?? HandshakeVerifier();

  final Handler agentWsHandler =
      webSocketHandler((WebSocketChannel channel) {
    final _ChannelSink sink = _ChannelSink(channel);
    final AgentSession session = AgentSession(
      broker: effectiveBroker,
      verifier: effectiveVerifier,
      sink: sink,
    );
    channel.stream.listen(
      (Object? msg) {
        if (msg is! String) return;
        final RelayFrame? frame = RelayFrame.decode(msg);
        if (frame == null) return;
        session.onFrame(frame);
      },
      onDone: () => session.onDisconnect(),
    );
  });

  final Handler appWsHandler =
      webSocketHandler((WebSocketChannel channel) {
    final _ChannelSink sink = _ChannelSink(channel);
    String? subscribedServerId;
    channel.stream.listen(
      (Object? msg) {
        if (msg is! String) return;
        final RelayFrame? frame = RelayFrame.decode(msg);
        if (frame == null) return;
        if (frame.type == RelayFrameType.subscribe) {
          final String? sid = frame.serverId;
          if (sid == null) return;
          subscribedServerId = sid;
          effectiveBroker.subscribeApp(sid, sink);
        } else if (frame.type == RelayFrameType.route) {
          final String? sid = subscribedServerId;
          if (sid == null) return;
          effectiveBroker.routeFromApp(sid, frame);
        }
      },
      onDone: () {
        final String? sid = subscribedServerId;
        if (sid != null) {
          effectiveBroker.unsubscribeApp(sid, sink);
        }
      },
    );
  });

  Future<Response> handler(Request request) async {
    final String path = request.url.path;
    if (path == 'agent') {
      return agentWsHandler(request);
    }
    if (path == 'app') {
      return appWsHandler(request);
    }
    return Response.notFound('not found');
  }

  return shelf_io.serve(handler, address, port);
}
