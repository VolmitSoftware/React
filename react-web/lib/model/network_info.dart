/// `GET /api/v1/network` on a Velocity or BungeeCord proxy: the proxy's own
/// numbers and every server in its list. A server running React carries its
/// live readings and the path under the proxy that serves its whole API.
library;

enum NetworkServerStatus { online, offline, unknown }

NetworkServerStatus _status(Object? value) => switch (value) {
  'online' => NetworkServerStatus.online,
  'offline' => NetworkServerStatus.offline,
  _ => NetworkServerStatus.unknown,
};

int? _int(Object? value) => value is num ? value.toInt() : null;

double? _double(Object? value) => value is num ? value.toDouble() : null;

String? _text(Object? value) =>
    value is String && value.isNotEmpty ? value : null;

class NetworkProxyInfo {
  const NetworkProxyInfo({
    required this.name,
    required this.kind,
    required this.version,
    required this.players,
    required this.servers,
    required this.online,
    required this.react,
  });

  final String name;

  /// Velocity or BungeeCord.
  final String kind;
  final String version;
  final int players;
  final int servers;
  final int online;

  /// Servers that run React and stream their readings to the proxy.
  final int react;

  factory NetworkProxyInfo.fromJson(Map<String, dynamic> json) =>
      NetworkProxyInfo(
        name: json['name'] as String? ?? '',
        kind: json['kind'] as String? ?? '',
        version: json['version'] as String? ?? '',
        players: _int(json['players']) ?? 0,
        servers: _int(json['servers']) ?? 0,
        online: _int(json['online']) ?? 0,
        react: _int(json['react']) ?? 0,
      );
}

class NetworkServer {
  const NetworkServer({
    required this.name,
    required this.address,
    required this.status,
    required this.players,
    required this.react,
    required this.basePath,
    this.pingMs,
    this.tps,
    this.mspt,
    this.memoryUsedBytes,
    this.memoryMaxBytes,
    this.platform,
    this.loader,
    this.version,
    this.lastSeenMs,
  });

  /// The server's name in the proxy's list.
  final String name;
  final String address;
  final NetworkServerStatus status;
  final int players;
  final int? pingMs;

  /// Runs React and is reachable under [basePath].
  final bool react;
  final double? tps;
  final double? mspt;
  final int? memoryUsedBytes;
  final int? memoryMaxBytes;
  final String? platform;
  final String? loader;
  final String? version;
  final int? lastSeenMs;

  /// The path under the proxy's base that serves this server's API.
  final String basePath;

  bool get online => status == NetworkServerStatus.online;

  factory NetworkServer.fromJson(Map<String, dynamic> json) {
    final String name = json['name'] as String? ?? '';
    return NetworkServer(
      name: name,
      address: json['address'] as String? ?? '',
      status: _status(json['status']),
      players: _int(json['players']) ?? 0,
      pingMs: _int(json['pingMs']),
      react: json['react'] == true,
      tps: _double(json['tps']),
      mspt: _double(json['mspt']),
      memoryUsedBytes: _int(json['memoryUsedBytes']),
      memoryMaxBytes: _int(json['memoryMaxBytes']),
      platform: _text(json['platform']),
      loader: _text(json['loader']),
      version: _text(json['version']),
      lastSeenMs: _int(json['lastSeenMs']),
      basePath:
          _text(json['basePath']) ?? '/network/${Uri.encodeComponent(name)}',
    );
  }
}

class NetworkInfo {
  const NetworkInfo({required this.proxy, required this.servers});

  final NetworkProxyInfo proxy;
  final List<NetworkServer> servers;

  factory NetworkInfo.fromJson(Map<String, dynamic> json) {
    final Object? proxy = json['proxy'];
    final Object? servers = json['servers'];
    if (proxy is! Map<String, dynamic>) {
      throw const FormatException('Network answer has no proxy');
    }
    return NetworkInfo(
      proxy: NetworkProxyInfo.fromJson(proxy),
      servers: <NetworkServer>[
        if (servers is List<dynamic>)
          for (final dynamic server in servers)
            if (server is Map<String, dynamic>) NetworkServer.fromJson(server),
      ],
    );
  }
}
