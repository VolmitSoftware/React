import 'dart:convert';

class RelayFrameType {
  static const String register = 'register';
  static const String subscribe = 'subscribe';
  static const String route = 'route';
  static const String data = 'data';
  static const String error = 'error';
  static const String challenge = 'challenge';
  static const String registered = 'registered';
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
    final Object? typeRaw = json['type'];
    if (typeRaw is! String) throw const FormatException('type must be a string');

    final Object? serverIdRaw = json['serverId'];
    if (serverIdRaw != null && serverIdRaw is! String) {
      throw const FormatException('serverId must be a string');
    }

    final Object? requestIdRaw = json['requestId'];
    if (requestIdRaw != null && requestIdRaw is! String) {
      throw const FormatException('requestId must be a string');
    }

    final Object? payloadRaw = json['payload'];
    if (payloadRaw != null && payloadRaw is! Map<String, dynamic>) {
      throw const FormatException('payload must be a map');
    }

    return RelayFrame(
      type: typeRaw,
      serverId: serverIdRaw as String?,
      requestId: requestIdRaw as String?,
      payload: payloadRaw as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> result = <String, dynamic>{'type': type};
    if (serverId != null) {
      result['serverId'] = serverId;
    }
    if (requestId != null) {
      result['requestId'] = requestId;
    }
    if (payload != null) {
      result['payload'] = payload;
    }
    return result;
  }

  static RelayFrame? decode(String jsonText) {
    try {
      final Object? raw = jsonDecode(jsonText);
      if (raw is! Map<String, dynamic>) return null;
      final Object? typeValue = raw['type'];
      if (typeValue is! String || typeValue.isEmpty) return null;
      return RelayFrame.fromJson(raw);
    } on FormatException {
      return null;
    }
  }

  String encode() => jsonEncode(toJson());
}
