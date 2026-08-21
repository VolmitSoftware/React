library;

import 'dart:async';

import 'package:react_web_relay/src/fingerprint.dart';
import 'package:react_web_relay/src/handshake_verifier.dart';
import 'package:react_web_relay/src/relay_broker.dart';
import 'package:react_web_relay/src/relay_frame.dart';
import 'package:react_web_relay/src/relay_sink.dart';

final class AgentSession {
  final RelayBroker _broker;
  final HandshakeVerifier _verifier;
  final RelaySink _sink;
  final String _issuedNonce;
  late final Timer _handshakeTimer;

  bool _nonceConsumed = false;
  bool _registered = false;
  bool _disconnected = false;
  String? _serverId;

  AgentSession({
    required RelayBroker broker,
    required HandshakeVerifier verifier,
    required RelaySink sink,
    Duration handshakeTimeout = const Duration(seconds: 10),
  }) : _broker = broker,
       _verifier = verifier,
       _sink = sink,
       _issuedNonce = verifier.issueNonce() {
    if (handshakeTimeout <= Duration.zero) {
      throw ArgumentError.value(handshakeTimeout, 'handshakeTimeout');
    }
    _handshakeTimer = Timer(
      handshakeTimeout,
      () => _fail('agent handshake timed out'),
    );
    _sink.send(
      RelayFrame(
        type: RelayFrameType.challenge,
        payload: <String, dynamic>{'nonce': _issuedNonce},
      ),
    );
  }

  Future<void> onFrame(RelayFrame frame) async {
    if (_disconnected) return;
    if (!_registered) {
      if (frame.type != RelayFrameType.register) {
        _fail('agent must register before routing frames');
        return;
      }
      await _handleRegister(frame);
      return;
    }
    if (frame.type != RelayFrameType.data &&
        frame.type != RelayFrameType.error) {
      _fail('registered agent sent an unsupported frame type');
      return;
    }
    final String? serverId = _serverId;
    if (serverId != null) {
      _broker.routeFromServer(serverId, _sink, frame);
    }
  }

  Future<void> _handleRegister(RelayFrame frame) async {
    if (_nonceConsumed) {
      _fail('agent registration nonce already consumed');
      return;
    }
    _nonceConsumed = true;

    final Object? pubKeyRaw = frame.payload?['pubKey'];
    final Object? signatureRaw = frame.payload?['sig'];
    if (pubKeyRaw is! String || signatureRaw is! String) {
      _fail('agent registration is missing pubKey or sig');
      return;
    }

    final bool valid = await _verifier.verify(
      nonceBase64Url: _issuedNonce,
      pubKeyBase64Url: pubKeyRaw,
      signatureBase64Url: signatureRaw,
    );
    if (_disconnected) return;
    if (!valid) {
      _fail('agent handshake verification failed');
      return;
    }

    final String serverId = fingerprintOfBase64(pubKeyRaw);
    if (frame.serverId != null && frame.serverId != serverId) {
      _fail('agent registration serverId does not match its public key');
      return;
    }

    _handshakeTimer.cancel();
    _broker.registerServer(serverId, _sink);
    _serverId = serverId;
    _registered = true;
    _sink.send(RelayFrame(type: RelayFrameType.registered, serverId: serverId));
  }

  void onDisconnect() {
    if (_disconnected) return;
    _disconnected = true;
    _handshakeTimer.cancel();
    final String? serverId = _serverId;
    if (_registered && serverId != null) {
      _broker.unregisterServer(serverId, _sink);
    }
    _registered = false;
  }

  void _fail(String message) {
    if (_disconnected) return;
    onDisconnect();
    _sink.closeWithError(message);
  }
}
