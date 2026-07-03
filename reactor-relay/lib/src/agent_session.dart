library;

import 'package:reactor_relay/src/fingerprint.dart';
import 'package:reactor_relay/src/handshake_verifier.dart';
import 'package:reactor_relay/src/relay_broker.dart';
import 'package:reactor_relay/src/relay_frame.dart';
import 'package:reactor_relay/src/relay_sink.dart';

class AgentSession {
  final RelayBroker _broker;
  final HandshakeVerifier _verifier;
  final RelaySink _sink;
  final String _issuedNonce;

  bool _nonceConsumed = false;
  bool _registered = false;
  String? _serverId;

  AgentSession({
    required RelayBroker broker,
    required HandshakeVerifier verifier,
    required RelaySink sink,
  })  : _broker = broker,
        _verifier = verifier,
        _sink = sink,
        _issuedNonce = verifier.issueNonce() {
    _sink.send(RelayFrame(
      type: RelayFrameType.challenge,
      payload: <String, dynamic>{'nonce': _issuedNonce},
    ));
  }

  Future<void> onFrame(RelayFrame frame) async {
    if (frame.type == RelayFrameType.register) {
      await _handleRegister(frame);
      return;
    }
    if (_registered && _serverId != null) {
      _broker.routeFromServer(_serverId!, frame);
    }
  }

  Future<void> _handleRegister(RelayFrame frame) async {
    if (_nonceConsumed) {
      _sink.send(RelayFrame(
        type: RelayFrameType.error,
        payload: <String, dynamic>{'message': 'nonce already consumed'},
      ));
      return;
    }

    _nonceConsumed = true;

    final String? pubKey = frame.payload?['pubKey'] as String?;
    final String? sig = frame.payload?['sig'] as String?;

    if (pubKey == null || sig == null) {
      _sink.send(RelayFrame(
        type: RelayFrameType.error,
        payload: <String, dynamic>{'message': 'missing pubKey or sig'},
      ));
      _sink.closeWithError('missing pubKey or sig');
      return;
    }

    final bool valid = await _verifier.verify(
      nonceBase64Url: _issuedNonce,
      pubKeyBase64Url: pubKey,
      signatureBase64Url: sig,
    );

    if (!valid) {
      _sink.send(RelayFrame(
        type: RelayFrameType.error,
        payload: <String, dynamic>{'message': 'handshake verification failed'},
      ));
      _sink.closeWithError('handshake verification failed');
      return;
    }

    final String serverId = fingerprintOfBase64(pubKey);
    _broker.registerServer(serverId, _sink);
    _serverId = serverId;
    _registered = true;
    _sink.send(RelayFrame(
      type: RelayFrameType.registered,
      serverId: serverId,
    ));
  }

  void onDisconnect() {
    if (_registered && _serverId != null) {
      _broker.unregisterServer(_serverId!, _sink);
    }
  }
}
