library;

import 'dart:convert';

abstract final class RelayFrameType {
  static const String register = 'register';
  static const String subscribe = 'subscribe';
  static const String route = 'route';
  static const String data = 'data';
  static const String error = 'error';
  static const String challenge = 'challenge';
}

class RelayFrame {
  final String type;
  final String? serverId;
  final String? requestId;
  final Map<String, dynamic>? payload;

  const RelayFrame({
    required this.type,
    this.serverId,
    this.requestId,
    this.payload,
  });

  factory RelayFrame.fromJson(Map<String, dynamic> json) {
    return RelayFrame(
      type: json['type'] as String,
      serverId: json['serverId'] as String?,
      requestId: json['requestId'] as String?,
      payload: json['payload'] as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> out = <String, dynamic>{'type': type};
    if (serverId != null) out['serverId'] = serverId;
    if (requestId != null) out['requestId'] = requestId;
    if (payload != null) out['payload'] = payload;
    return out;
  }

  static RelayFrame? decode(String raw) {
    try {
      final Object? parsed = jsonDecode(raw);
      if (parsed is! Map<String, dynamic>) return null;
      return RelayFrame.fromJson(parsed);
    } catch (_) {
      return null;
    }
  }

  String encode() => jsonEncode(toJson());
}

Map<String, dynamic> buildRoutePayload({
  required String method,
  required String path,
  required Map<String, String> headers,
  Object? body,
}) {
  final Map<String, dynamic> out = <String, dynamic>{
    'method': method,
    'path': path,
    'headers': headers,
  };
  if (body != null) out['body'] = body;
  return out;
}

class RelayResponse {
  final int status;
  final Map<String, dynamic> body;

  const RelayResponse({required this.status, required this.body});

  factory RelayResponse.fromDataPayload(Map<String, dynamic> payload) {
    return RelayResponse(
      status: payload['status'] as int,
      body: payload['body'] as Map<String, dynamic>,
    );
  }
}
