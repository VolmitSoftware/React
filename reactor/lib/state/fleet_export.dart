library;

import 'dart:convert';

import '../localization/reactor_localizations.dart';
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
    'servers': servers.map((ServerCredential c) => c.toJson()).toList(),
  };
  return jsonEncode(envelope);
}

FleetParseResult parseFleetImportJson(String raw) {
  Object? decoded;
  try {
    decoded = jsonDecode(raw);
  } on Object {
    return FleetParseResult(
      servers: const <ServerCredential>[],
      skipped: 0,
      error: reactorText(ReactorText.fleetImportInvalidJson),
    );
  }
  if (decoded is! Map<String, dynamic>) {
    return FleetParseResult(
      servers: const <ServerCredential>[],
      skipped: 0,
      error: reactorText(ReactorText.fleetImportInvalidFile),
    );
  }
  if (decoded['kind'] != 'reactor-fleet') {
    return FleetParseResult(
      servers: const <ServerCredential>[],
      skipped: 0,
      error: reactorText(ReactorText.fleetImportWrongKind),
    );
  }
  final Object? rawList = decoded['servers'];
  if (rawList is! List<dynamic>) {
    return FleetParseResult(
      servers: const <ServerCredential>[],
      skipped: 0,
      error: reactorText(ReactorText.fleetImportInvalidServerList),
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
          ? reactorText(
              skipped == 1
                  ? ReactorText.fleetImportNoValidServer
                  : ReactorText.fleetImportNoValidServers,
              <String, Object?>{'count': skipped},
            )
          : reactorText(ReactorText.fleetImportNoServers),
    );
  }
  return FleetParseResult(servers: valid, skipped: skipped);
}
