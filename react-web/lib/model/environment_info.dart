import 'dart:collection';

class EnvironmentInfo {
  final Map<String, Map<String, Object?>> sections;
  final List<EnvironmentDisk> disks;
  final List<EnvironmentMount> mounts;
  final List<EnvironmentNetworkInterface> network;

  const EnvironmentInfo({
    required this.sections,
    this.disks = const <EnvironmentDisk>[],
    this.mounts = const <EnvironmentMount>[],
    this.network = const <EnvironmentNetworkInterface>[],
  });

  factory EnvironmentInfo.fromJson(Map<String, dynamic> j) {
    final LinkedHashMap<String, Map<String, Object?>> result =
        LinkedHashMap<String, Map<String, Object?>>();
    for (final MapEntry<String, dynamic> entry in j.entries) {
      if (entry.value is Map) {
        result[entry.key] = Map<String, Object?>.from(
          entry.value as Map<dynamic, dynamic>,
        );
      }
    }
    return EnvironmentInfo(
      sections: result,
      disks: _objects(
        j['disks'],
      ).map(EnvironmentDisk.fromJson).toList(growable: false),
      mounts: _objects(
        j['mounts'],
      ).map(EnvironmentMount.fromJson).toList(growable: false),
      network: _objects(
        j['network'],
      ).map(EnvironmentNetworkInterface.fromJson).toList(growable: false),
    );
  }

  Map<String, Object?> get cpu => sections['cpu'] ?? const <String, Object?>{};
  Map<String, Object?> get memory =>
      sections['memory'] ?? const <String, Object?>{};
  Map<String, Object?> get jvm => sections['jvm'] ?? const <String, Object?>{};
  Map<String, Object?> get server =>
      sections['server'] ?? const <String, Object?>{};

  List<MapEntry<String, Object?>> entriesOf(String section) =>
      (sections[section] ?? const <String, Object?>{}).entries.toList();

  List<String> get sectionNames => sections.keys.toList();

  int get totalDiskReadBytes => disks.fold(
    0,
    (int total, EnvironmentDisk disk) => total + disk.readBytes,
  );

  int get totalDiskWriteBytes => disks.fold(
    0,
    (int total, EnvironmentDisk disk) => total + disk.writeBytes,
  );

  int get totalNetworkReceivedBytes => network.fold(
    0,
    (int total, EnvironmentNetworkInterface item) => total + item.receivedBytes,
  );

  int get totalNetworkSentBytes => network.fold(
    0,
    (int total, EnvironmentNetworkInterface item) => total + item.sentBytes,
  );

  Map<String, Object?> toJson() => <String, Object?>{
    ...sections,
    'disks': disks.map((EnvironmentDisk item) => item.toJson()).toList(),
    'mounts': mounts.map((EnvironmentMount item) => item.toJson()).toList(),
    'network': network
        .map((EnvironmentNetworkInterface item) => item.toJson())
        .toList(),
  };

  static Iterable<Map<String, dynamic>> _objects(Object? raw) sync* {
    if (raw is! List) return;
    for (final Object? item in raw) {
      if (item is Map) {
        yield Map<String, dynamic>.from(item);
      }
    }
  }
}

class EnvironmentDisk {
  final String name;
  final String model;
  final int sizeBytes;
  final int readBytes;
  final int writeBytes;
  final int reads;
  final int writes;
  final int queueLength;
  final int transferTimeMillis;
  final int timestampMillis;

  const EnvironmentDisk({
    required this.name,
    required this.model,
    required this.sizeBytes,
    required this.readBytes,
    required this.writeBytes,
    required this.reads,
    required this.writes,
    required this.queueLength,
    required this.transferTimeMillis,
    required this.timestampMillis,
  });

  factory EnvironmentDisk.fromJson(Map<String, dynamic> j) => EnvironmentDisk(
    name: j['name']?.toString() ?? '',
    model: j['model']?.toString() ?? '',
    sizeBytes: _integer(j['sizeBytes']),
    readBytes: _integer(j['readBytes']),
    writeBytes: _integer(j['writeBytes']),
    reads: _integer(j['reads']),
    writes: _integer(j['writes']),
    queueLength: _integer(j['queueLength']),
    transferTimeMillis: _integer(j['transferTimeMillis']),
    timestampMillis: _integer(j['timestampMillis']),
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'name': name,
    'model': model,
    'sizeBytes': sizeBytes,
    'readBytes': readBytes,
    'writeBytes': writeBytes,
    'reads': reads,
    'writes': writes,
    'queueLength': queueLength,
    'transferTimeMillis': transferTimeMillis,
    'timestampMillis': timestampMillis,
  };
}

class EnvironmentMount {
  final String name;
  final String mount;
  final String description;
  final String type;
  final int totalBytes;
  final int freeBytes;
  final int usableBytes;

  const EnvironmentMount({
    required this.name,
    required this.mount,
    required this.description,
    required this.type,
    required this.totalBytes,
    required this.freeBytes,
    required this.usableBytes,
  });

  factory EnvironmentMount.fromJson(Map<String, dynamic> j) => EnvironmentMount(
    name: j['name']?.toString() ?? '',
    mount: j['mount']?.toString() ?? '',
    description: j['description']?.toString() ?? '',
    type: j['type']?.toString() ?? '',
    totalBytes: _integer(j['totalBytes']),
    freeBytes: _integer(j['freeBytes']),
    usableBytes: _integer(j['usableBytes']),
  );

  int get usedBytes => (totalBytes - freeBytes).clamp(0, totalBytes);

  double get usedFraction => totalBytes <= 0 ? 0 : usedBytes / totalBytes;

  Map<String, Object?> toJson() => <String, Object?>{
    'name': name,
    'mount': mount,
    'description': description,
    'type': type,
    'totalBytes': totalBytes,
    'freeBytes': freeBytes,
    'usableBytes': usableBytes,
  };
}

class EnvironmentNetworkInterface {
  final String name;
  final String displayName;
  final int mtu;
  final String macAddress;
  final List<String> ipv4Addresses;
  final List<String> ipv6Addresses;
  final int speedBitsPerSecond;
  final int receivedBytes;
  final int sentBytes;
  final int receivedPackets;
  final int sentPackets;
  final int receiveErrors;
  final int sendErrors;
  final int receiveDrops;
  final int collisions;
  final int timestampMillis;

  const EnvironmentNetworkInterface({
    required this.name,
    required this.displayName,
    required this.mtu,
    required this.macAddress,
    required this.ipv4Addresses,
    required this.ipv6Addresses,
    required this.speedBitsPerSecond,
    required this.receivedBytes,
    required this.sentBytes,
    required this.receivedPackets,
    required this.sentPackets,
    required this.receiveErrors,
    required this.sendErrors,
    required this.receiveDrops,
    required this.collisions,
    required this.timestampMillis,
  });

  factory EnvironmentNetworkInterface.fromJson(Map<String, dynamic> j) =>
      EnvironmentNetworkInterface(
        name: j['name']?.toString() ?? '',
        displayName: j['displayName']?.toString() ?? '',
        mtu: _integer(j['mtu']),
        macAddress: j['macAddress']?.toString() ?? '',
        ipv4Addresses: _strings(j['ipv4Addresses']),
        ipv6Addresses: _strings(j['ipv6Addresses']),
        speedBitsPerSecond: _integer(j['speedBitsPerSecond']),
        receivedBytes: _integer(j['receivedBytes']),
        sentBytes: _integer(j['sentBytes']),
        receivedPackets: _integer(j['receivedPackets']),
        sentPackets: _integer(j['sentPackets']),
        receiveErrors: _integer(j['receiveErrors']),
        sendErrors: _integer(j['sendErrors']),
        receiveDrops: _integer(j['receiveDrops']),
        collisions: _integer(j['collisions']),
        timestampMillis: _integer(j['timestampMillis']),
      );

  Map<String, Object?> toJson() => <String, Object?>{
    'name': name,
    'displayName': displayName,
    'mtu': mtu,
    'macAddress': macAddress,
    'ipv4Addresses': ipv4Addresses,
    'ipv6Addresses': ipv6Addresses,
    'speedBitsPerSecond': speedBitsPerSecond,
    'receivedBytes': receivedBytes,
    'sentBytes': sentBytes,
    'receivedPackets': receivedPackets,
    'sentPackets': sentPackets,
    'receiveErrors': receiveErrors,
    'sendErrors': sendErrors,
    'receiveDrops': receiveDrops,
    'collisions': collisions,
    'timestampMillis': timestampMillis,
  };
}

int _integer(Object? value) => value is num ? value.toInt() : 0;

List<String> _strings(Object? value) => value is List
    ? value
          .map((Object? item) => item?.toString() ?? '')
          .toList(growable: false)
    : const <String>[];
