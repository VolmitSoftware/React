// A fleet of fake React servers for the browser checks and local work
// without Minecraft: the plugin's /api/v1 REST API, the /ws/metrics and
// /ws/logs sockets, pairing links and token auth, at a real server's scale.
//
//   dart run tool/fake_server.dart --port=8821 --origin=http://127.0.0.1:8820 --servers=3
//       [--app=<url>] [--players=40] [--mutate=<seconds>] [--idle-timeout=<seconds>]
//
// Each server answers under its own path (http://127.0.0.1:8821/s1, /s2, ...).
// --mutate flips one tweak per server every N seconds, as a hotload would;
// --idle-timeout closes log sockets that saw no frame for N seconds.
// Pairing links point at --app (default the first origin), as appUrl does
// in web.toml. GET /__fake/servers lists the servers with a new admin
// pairing link each; the other /__fake routes, among them new links, revoke,
// expire and down, are listed in fake_server/fleet_control.dart.
import 'dart:async';
import 'dart:io';

import 'fake_server/fixtures.dart';
import 'fake_server/fleet.dart';

Future<void> main(List<String> arguments) async {
  int port = FakeFleet.defaultPort;
  int servers = 3;
  int players = 40;
  int mutateSeconds = 0;
  int idleTimeoutSeconds = 0;
  final List<String> origins = <String>[];
  String? app;
  for (final String argument in arguments) {
    final int equals = argument.indexOf('=');
    final String name = equals < 0 ? argument : argument.substring(0, equals);
    final String value = equals < 0 ? '' : argument.substring(equals + 1);
    switch (name) {
      case '--port':
        port = int.parse(value);
      case '--origin':
        origins.addAll(
          value.split(',').where((String each) => each.isNotEmpty),
        );
      case '--app':
        app = value;
      case '--servers':
        servers = int.parse(value);
      case '--players':
        players = int.parse(value);
      case '--mutate':
        mutateSeconds = int.parse(value);
      case '--idle-timeout':
        idleTimeoutSeconds = int.parse(value);
      default:
        stderr.writeln(
          'Usage: dart run tool/fake_server.dart [--port=$port] '
          '[--origin=${FakeFleet.defaultOrigin}[,...]] [--app=<url>] [--servers=1-8] [--players=40] '
          '[--mutate=0] [--idle-timeout=0]',
        );
        exitCode = 64;
        return;
    }
  }
  final FakeFleet fleet = await FakeFleet.start(
    port: port,
    origins: origins.isEmpty
        ? const <String>[FakeFleet.defaultOrigin]
        : origins,
    appUrl: app,
    servers: servers,
    players: players,
    mutateEvery: Duration(seconds: mutateSeconds),
    idleTimeout: Duration(seconds: idleTimeoutSeconds),
  );
  stdout.writeln(
    'Fake React fleet on ${fleet.baseUri} for ${fleet.allowedOrigins.join(', ')}',
  );
  for (final FakeServerProfile profile in fleet.profiles) {
    stdout.writeln(
      '${profile.label} (${profile.health.name}) ${fleet.directUrl(profile)}',
    );
    stdout.writeln('  ${fleet.pair(profile).link}');
  }
  await Future.any(<Future<ProcessSignal>>[
    ProcessSignal.sigint.watch().first,
    ProcessSignal.sigterm.watch().first,
  ]);
  await fleet.close();
  // The signal watchers keep the isolate alive.
  exit(0);
}
