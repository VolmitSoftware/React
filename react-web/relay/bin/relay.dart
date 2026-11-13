import 'dart:async';
import 'dart:io';

import 'package:react_web_relay/reactor_relay.dart';

Future<void> main(List<String> args) async {
  final Map<String, String> environment = Platform.environment;
  final int port = _integerEnvironment(environment, 'PORT', 8080, 1, 65535);
  final String address = environment['BIND_ADDRESS']?.trim().isNotEmpty == true
      ? environment['BIND_ADDRESS']!.trim()
      : '0.0.0.0';
  final List<String>? allowedOrigins = _commaSeparatedEnvironment(
    environment,
    'ALLOWED_APP_ORIGINS',
  );
  final RelayLimits limits = RelayLimits(
    maxFrameBytes: _integerEnvironment(
      environment,
      'MAX_FRAME_BYTES',
      RelayLimits.defaultMaxFrameBytes,
      1024,
      16 * 1024 * 1024,
    ),
    maxAppMessagesPerWindow: _integerEnvironment(
      environment,
      'MAX_APP_MESSAGES_PER_WINDOW',
      240,
      1,
      10000,
    ),
    maxAgentMessagesPerWindow: _integerEnvironment(
      environment,
      'MAX_AGENT_MESSAGES_PER_WINDOW',
      5000,
      1,
      100000,
    ),
    maxPendingRequestsPerApp: _integerEnvironment(
      environment,
      'MAX_PENDING_PER_APP',
      64,
      1,
      4096,
    ),
    maxPendingRequestsGlobal: _integerEnvironment(
      environment,
      'MAX_PENDING_GLOBAL',
      4096,
      1,
      100000,
    ),
    maxOutboundQueueMessages: _integerEnvironment(
      environment,
      'MAX_OUTBOUND_QUEUE_MESSAGES',
      128,
      1,
      4096,
    ),
    maxAgentOutboundQueueMessages: _integerEnvironment(
      environment,
      'MAX_AGENT_OUTBOUND_QUEUE_MESSAGES',
      4096,
      1,
      65536,
    ),
    handshakeTimeout: Duration(
      seconds: _integerEnvironment(
        environment,
        'HANDSHAKE_TIMEOUT_SECONDS',
        10,
        1,
        3600,
      ),
    ),
    subscriptionTimeout: Duration(
      seconds: _integerEnvironment(
        environment,
        'SUBSCRIPTION_TIMEOUT_SECONDS',
        10,
        1,
        3600,
      ),
    ),
    requestTimeout: Duration(
      seconds: _integerEnvironment(
        environment,
        'REQUEST_TIMEOUT_SECONDS',
        10,
        1,
        3600,
      ),
    ),
    pingInterval: Duration(
      seconds: _integerEnvironment(
        environment,
        'PING_INTERVAL_SECONDS',
        30,
        1,
        3600,
      ),
    ),
  );
  limits.validate();

  final RelayConnections connections = RelayConnections();
  final HttpServer server = await startRelayServer(
    port: port,
    address: address,
    limits: limits,
    allowedAppOrigins: allowedOrigins,
    connections: connections,
  );
  stdout.writeln(
    'react-web-relay listening on ${server.address.host}:${server.port}',
  );

  final Completer<void> shutdown = Completer<void>();
  final StreamSubscription<ProcessSignal> terminateSubscription = ProcessSignal
      .sigterm
      .watch()
      .listen((ProcessSignal signal) {
        if (!shutdown.isCompleted) shutdown.complete();
      });
  final StreamSubscription<ProcessSignal> interruptSubscription = ProcessSignal
      .sigint
      .watch()
      .listen((ProcessSignal signal) {
        if (!shutdown.isCompleted) shutdown.complete();
      });
  await shutdown.future;
  await terminateSubscription.cancel();
  await interruptSubscription.cancel();
  // Close code 4012 tells every server and browser the relay is restarting,
  // so each reconnects at once. Upgraded sockets are detached from the
  // server and would otherwise keep this process serving them.
  await connections.closeAll();
  await server.close(force: true);
  exit(0);
}

int _integerEnvironment(
  Map<String, String> environment,
  String name,
  int defaultValue,
  int minimum,
  int maximum,
) {
  final String? raw = environment[name];
  if (raw == null || raw.trim().isEmpty) return defaultValue;
  final int? parsed = int.tryParse(raw.trim());
  if (parsed == null || parsed < minimum || parsed > maximum) {
    throw FormatException(
      '$name must be an integer from $minimum through $maximum',
    );
  }
  return parsed;
}

List<String>? _commaSeparatedEnvironment(
  Map<String, String> environment,
  String name,
) {
  final String? raw = environment[name];
  if (raw == null || raw.trim().isEmpty) return null;
  final List<String> values = raw
      .split(',')
      .map((String value) => value.trim())
      .where((String value) => value.isNotEmpty)
      .toList(growable: false);
  return values.isEmpty ? null : values;
}
