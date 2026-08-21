library;

import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:math';

import 'package:react_web_relay/src/relay_frame.dart';
import 'package:react_web_relay/src/relay_limits.dart';
import 'package:react_web_relay/src/relay_sink.dart';

final class RelayBroker {
  final RelayLimits limits;
  final Map<String, RelaySink> _servers = <String, RelaySink>{};
  final HashMap<RelaySink, _AppRegistration> _apps =
      HashMap<RelaySink, _AppRegistration>.identity();
  final Map<String, Set<RelaySink>> _appsByServer = <String, Set<RelaySink>>{};
  final Map<String, _PendingRequest> _pending = <String, _PendingRequest>{};
  final String _requestPrefix;
  int _nextRequestId = 0;

  RelayBroker({this.limits = const RelayLimits(), Random? random})
    : _requestPrefix = _randomToken(random ?? Random.secure()) {
    limits.validate();
  }

  void registerServer(String serverId, RelaySink agent) {
    final RelaySink? existing = _servers[serverId];
    if (existing != null && !identical(existing, agent)) {
      _failPendingForServer(serverId, 'server connection was replaced');
      existing.closeWithError('replaced by new registration');
    }
    _servers[serverId] = agent;
  }

  void unregisterServer(String serverId, RelaySink agent) {
    if (!identical(_servers[serverId], agent)) return;
    _servers.remove(serverId);
    _failPendingForServer(serverId, 'server disconnected');
  }

  void subscribeApp(String serverId, RelaySink app) {
    final _AppRegistration? existing = _apps[app];
    if (existing != null && existing.serverId != serverId) {
      _removeApp(app, existing);
    }
    if (!_apps.containsKey(app)) {
      final _AppRegistration registration = _AppRegistration(serverId);
      _apps[app] = registration;
      _appsByServer.putIfAbsent(serverId, () => <RelaySink>{}).add(app);
    }
    if (!_servers.containsKey(serverId)) {
      app.send(
        RelayFrame(
          type: RelayFrameType.error,
          serverId: serverId,
          payload: <String, dynamic>{'message': 'server offline'},
        ),
      );
    }
  }

  void unsubscribeApp(String serverId, RelaySink app) {
    final _AppRegistration? registration = _apps[app];
    if (registration == null || registration.serverId != serverId) return;
    _removeApp(app, registration);
  }

  void unregisterApp(RelaySink app) {
    final _AppRegistration? registration = _apps[app];
    if (registration != null) _removeApp(app, registration);
  }

  bool routeFromApp(String serverId, RelaySink app, RelayFrame frame) {
    final String? clientRequestId = frame.requestId;
    final _AppRegistration? registration = _apps[app];
    if (registration == null || registration.serverId != serverId) {
      _sendFailure(
        app,
        serverId,
        clientRequestId,
        400,
        'app is not subscribed to this server',
      );
      return false;
    }
    if (frame.type != RelayFrameType.route || clientRequestId == null) {
      _sendFailure(
        app,
        serverId,
        clientRequestId,
        400,
        'route frame requires a requestId',
      );
      return false;
    }
    final String? payloadError = _validateRoutePayload(frame.payload);
    if (payloadError != null) {
      _sendFailure(app, serverId, clientRequestId, 400, payloadError);
      return false;
    }
    if (registration.pendingByClientId.containsKey(clientRequestId)) {
      _sendFailure(
        app,
        serverId,
        clientRequestId,
        409,
        'requestId is already pending',
      );
      return false;
    }
    if (registration.pendingByClientId.length >=
        limits.maxPendingRequestsPerApp) {
      _sendFailure(
        app,
        serverId,
        clientRequestId,
        429,
        'too many pending requests for this app connection',
      );
      return false;
    }
    if (_pending.length >= limits.maxPendingRequestsGlobal) {
      _sendFailure(
        app,
        serverId,
        clientRequestId,
        503,
        'relay pending request capacity reached',
      );
      return false;
    }
    final RelaySink? agent = _servers[serverId];
    if (agent == null) {
      _sendFailure(app, serverId, clientRequestId, 503, 'server offline');
      return false;
    }

    final String brokerRequestId = _newBrokerRequestId();
    final _PendingRequest pending = _PendingRequest(
      app: app,
      serverId: serverId,
      clientRequestId: clientRequestId,
    );
    pending.timeout = Timer(
      limits.requestTimeout,
      () => _expireRequest(brokerRequestId),
    );
    _pending[brokerRequestId] = pending;
    registration.pendingByClientId[clientRequestId] = brokerRequestId;

    agent.send(
      RelayFrame(
        type: RelayFrameType.route,
        serverId: serverId,
        requestId: brokerRequestId,
        payload: frame.payload,
      ),
    );
    return true;
  }

  bool routeFromServer(String serverId, RelaySink agent, RelayFrame frame) {
    if (!identical(_servers[serverId], agent)) return false;
    if (frame.type != RelayFrameType.data &&
        frame.type != RelayFrameType.error) {
      return false;
    }
    final String? brokerRequestId = frame.requestId;
    if (brokerRequestId == null) return false;
    final _PendingRequest? pending = _pending[brokerRequestId];
    if (pending == null || pending.serverId != serverId) return false;

    final String? responseError = _validateResponse(frame);
    final _PendingRequest completed = _takePending(brokerRequestId)!;
    if (responseError != null) {
      _sendFailure(
        completed.app,
        serverId,
        completed.clientRequestId,
        502,
        responseError,
      );
      return false;
    }
    completed.app.send(
      RelayFrame(
        type: frame.type,
        serverId: serverId,
        requestId: completed.clientRequestId,
        payload: frame.payload,
      ),
    );
    return true;
  }

  bool isOnline(String serverId) => _servers.containsKey(serverId);

  int get onlineCount => _servers.length;

  int get appSessionCount => _apps.length;

  int get pendingRequestCount => _pending.length;

  void _removeApp(RelaySink app, _AppRegistration registration) {
    _apps.remove(app);
    final Set<RelaySink>? subscriptions = _appsByServer[registration.serverId];
    subscriptions?.remove(app);
    if (subscriptions != null && subscriptions.isEmpty) {
      _appsByServer.remove(registration.serverId);
    }
    final List<String> brokerRequestIds = List<String>.from(
      registration.pendingByClientId.values,
    );
    for (final String brokerRequestId in brokerRequestIds) {
      _takePending(brokerRequestId);
    }
  }

  void _failPendingForServer(String serverId, String message) {
    final List<String> brokerRequestIds = <String>[
      for (final MapEntry<String, _PendingRequest> entry in _pending.entries)
        if (entry.value.serverId == serverId) entry.key,
    ];
    for (final String brokerRequestId in brokerRequestIds) {
      final _PendingRequest? pending = _takePending(brokerRequestId);
      if (pending != null) {
        _sendFailure(
          pending.app,
          pending.serverId,
          pending.clientRequestId,
          503,
          message,
        );
      }
    }
  }

  void _expireRequest(String brokerRequestId) {
    final _PendingRequest? pending = _takePending(brokerRequestId);
    if (pending == null) return;
    _sendFailure(
      pending.app,
      pending.serverId,
      pending.clientRequestId,
      504,
      'relay request timed out',
    );
  }

  _PendingRequest? _takePending(String brokerRequestId) {
    final _PendingRequest? pending = _pending.remove(brokerRequestId);
    if (pending == null) return null;
    pending.timeout?.cancel();
    final _AppRegistration? registration = _apps[pending.app];
    registration?.pendingByClientId.remove(pending.clientRequestId);
    return pending;
  }

  void _sendFailure(
    RelaySink app,
    String serverId,
    String? requestId,
    int status,
    String message,
  ) {
    if (requestId == null) {
      app.send(
        RelayFrame(
          type: RelayFrameType.error,
          serverId: serverId,
          payload: <String, dynamic>{'message': message},
        ),
      );
      return;
    }
    app.send(
      RelayFrame(
        type: RelayFrameType.data,
        serverId: serverId,
        requestId: requestId,
        payload: <String, dynamic>{
          'status': status,
          'body': <String, dynamic>{
            'error': <String, dynamic>{'message': message},
          },
        },
      ),
    );
  }

  String _newBrokerRequestId() {
    final int requestNumber = _nextRequestId++;
    return '$_requestPrefix-${requestNumber.toRadixString(36)}';
  }

  static String _randomToken(Random random) {
    final List<int> bytes = List<int>.generate(
      18,
      (int index) => random.nextInt(256),
      growable: false,
    );
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  static String? _validateRoutePayload(Map<String, dynamic>? payload) {
    if (payload == null) return 'route frame requires a payload';
    if (payload.keys.any(
      (String key) =>
          !const <String>{'method', 'path', 'headers', 'body'}.contains(key),
    )) {
      return 'route payload contains an unknown field';
    }
    final Object? methodRaw = payload['method'];
    final Object? pathRaw = payload['path'];
    final Object? headersRaw = payload['headers'];
    if (methodRaw is! String ||
        !const <String>{
          'GET',
          'POST',
          'PUT',
          'PATCH',
          'DELETE',
        }.contains(methodRaw.toUpperCase())) {
      return 'route method is invalid';
    }
    if (pathRaw is! String ||
        !pathRaw.startsWith('/api/v1/') ||
        pathRaw.length > 2048 ||
        pathRaw.contains('\\')) {
      return 'route path is invalid';
    }
    if (headersRaw is! Map<String, dynamic> ||
        headersRaw.keys.any((String key) => key.length > 256) ||
        headersRaw.values.any(
          (Object? value) => value is! String || value.length > 8192,
        )) {
      return 'route headers must contain bounded string values';
    }
    final Object? body = payload['body'];
    if (body != null && body is! String) {
      return 'route body must be a JSON-encoded string';
    }
    final String method = methodRaw.toUpperCase();
    if ((method == 'GET' || method == 'DELETE') &&
        body is String &&
        body.isNotEmpty) {
      return '$method requests do not carry a body in the React relay contract';
    }
    return null;
  }

  static String? _validateResponse(RelayFrame frame) {
    final Map<String, dynamic>? payload = frame.payload;
    if (payload == null) return 'server returned an empty response payload';
    if (frame.type == RelayFrameType.error) {
      return payload['message'] is String
          ? null
          : 'server returned a malformed error payload';
    }
    final Object? status = payload['status'];
    final Object? body = payload['body'];
    if (status is! int || status < 100 || status > 599) {
      return 'server returned an invalid HTTP status';
    }
    if (body is! Map<String, dynamic>) {
      return 'server returned a non-object response body';
    }
    return null;
  }
}

final class _AppRegistration {
  final String serverId;
  final Map<String, String> pendingByClientId = <String, String>{};

  _AppRegistration(this.serverId);
}

final class _PendingRequest {
  final RelaySink app;
  final String serverId;
  final String clientRequestId;
  Timer? timeout;

  _PendingRequest({
    required this.app,
    required this.serverId,
    required this.clientRequestId,
  });
}
