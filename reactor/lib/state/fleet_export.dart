library;

import 'dart:convert';

import '../model/server_credential.dart';

class FleetParseResult {
  final List<ServerCredential> servers;
  final int skipped;
  final String? error;

  const FleetParseResult({
    required this.servers,
    required this.skipped,
    this.error,
  });

  bool get ok => error == null;
}

String buildFleetExportJson(List<ServerCredential> servers) {
  final Map<String, Object> envelope = <String, Object>{
    'kind': 'reactor-fleet',
    'version': 1,
    'exportedAt': DateTime.now().millisecondsSinceEpoch,
    'servers':
        servers.map((ServerCredential c) => c.toJson()).toList(),
  };
  return jsonEncode(envelope);
}

FleetParseResult parseFleetImportJson(String raw) {
  Object? decoded;
  try {
    decoded = jsonDecode(raw);
  } on Object {
    return const FleetParseResult(
      servers: <ServerCredential>[],
      skipped: 0,
      error: 'Invalid JSON',
    );
  }
  if (decoded is! Map<String, dynamic>) {
    return const FleetParseResult(
      servers: <ServerCredential>[],
      skipped: 0,
      error: 'Not a valid fleet export file',
    );
  }
  if (decoded['kind'] != 'reactor-fleet') {
    return const FleetParseResult(
      servers: <ServerCredential>[],
      skipped: 0,
      error: 'Not a reactor-fleet export file',
    );
  }
  final Object? rawList = decoded['servers'];
  if (rawList is! List<dynamic>) {
    return const FleetParseResult(
      servers: <ServerCredential>[],
      skipped: 0,
      error: 'Missing or invalid servers list',
    );
  }
  final List<ServerCredential> valid = <ServerCredential>[];
  int skipped = 0;
  for (final dynamic item in rawList) {
    if (item is! Map<String, dynamic>) {
      skipped++;
      continue;
    }
    try {
      valid.add(ServerCredential.fromJson(item));
    } on Object {
      skipped++;
    }
  }
  if (valid.isEmpty) {
    return FleetParseResult(
      servers: const <ServerCredential>[],
      skipped: skipped,
      error: skipped > 0
          ? 'No valid servers found ($skipped malformed ${skipped == 1 ? 'entry' : 'entries'})'
          : 'No servers in file',
    );
  }
  return FleetParseResult(servers: valid, skipped: skipped);
}
