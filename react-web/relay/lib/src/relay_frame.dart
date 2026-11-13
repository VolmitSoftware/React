import 'dart:convert';

abstract final class RelayFrameType {
  static const String register = 'register';
  static const String subscribe = 'subscribe';
  static const String route = 'route';
  static const String data = 'data';
  static const String error = 'error';
  static const String challenge = 'challenge';
  static const String registered = 'registered';
  static const String status = 'status';
}

class RelayFrame {
  static const int maxTypeCharacters = 32;
  static const int maxServerIdCharacters = 128;
  static const int maxRequestIdCharacters = 128;

  final String type;
  final String? serverId;
  final String? requestId;
  final bool? online;
  final Map<String, dynamic>? payload;

  const RelayFrame({
    required this.type,
    this.serverId,
    this.requestId,
    this.online,
    this.payload,
  });

  factory RelayFrame.fromJson(Map<String, dynamic> json) {
    if (json.keys.any(
      (String key) => !const <String>{
        'type',
        'serverId',
        'requestId',
        'online',
        'payload',
      }.contains(key),
    )) {
      throw const FormatException('frame contains an unknown field');
    }
    final Object? typeRaw = json['type'];
    if (typeRaw is! String ||
        typeRaw.isEmpty ||
        typeRaw.length > maxTypeCharacters) {
      throw const FormatException('type must be a non-empty bounded string');
    }

    final Object? serverIdRaw = json['serverId'];
    if (serverIdRaw != null &&
        (serverIdRaw is! String ||
            serverIdRaw.isEmpty ||
            serverIdRaw.length > maxServerIdCharacters)) {
      throw const FormatException(
        'serverId must be a non-empty bounded string',
      );
    }

    final Object? requestIdRaw = json['requestId'];
    if (requestIdRaw != null &&
        (requestIdRaw is! String ||
            requestIdRaw.isEmpty ||
            requestIdRaw.length > maxRequestIdCharacters)) {
      throw const FormatException(
        'requestId must be a non-empty bounded string',
      );
    }

    final Object? onlineRaw = json['online'];
    if (onlineRaw != null && onlineRaw is! bool) {
      throw const FormatException('online must be a boolean');
    }

    final Object? payloadRaw = json['payload'];
    if (payloadRaw != null && payloadRaw is! Map<String, dynamic>) {
      throw const FormatException('payload must be a map');
    }

    return RelayFrame(
      type: typeRaw,
      serverId: serverIdRaw as String?,
      requestId: requestIdRaw as String?,
      online: onlineRaw as bool?,
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
    if (online != null) {
      result['online'] = online;
    }
    if (payload != null) {
      result['payload'] = payload;
    }
    return result;
  }

  static RelayFrame? decode(String jsonText, {int? maximumBytes}) {
    try {
      if (maximumBytes != null && utf8.encode(jsonText).length > maximumBytes) {
        return null;
      }
      final Object? raw = jsonDecode(jsonText);
      if (raw is! Map<String, dynamic>) return null;
      return RelayFrame.fromJson(raw);
    } on Object {
      return null;
    }
  }

  String encode() => jsonEncode(toJson());
}
