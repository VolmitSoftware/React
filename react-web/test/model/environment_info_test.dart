library;

import 'package:test/test.dart';
import 'package:react_web/model/environment_info.dart';

void main() {
  group('EnvironmentInfo', () {
    final Map<String, dynamic> raw = {
      'cpu': {'model': 'Apple M3', 'cores': 8, 'loadPercent': 23.4},
      'memory': {'totalMb': 32768, 'usedMb': 18000},
      'jvm': {'version': '25', 'heapMb': 8192},
      'server': {'brand': 'Purpur', 'folia': true},
      'disks': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'disk0',
          'model': 'Fast Disk',
          'sizeBytes': 1000,
          'readBytes': 400,
          'writeBytes': 250,
        },
      ],
      'mounts': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'root',
          'mount': '/',
          'totalBytes': 1000,
          'freeBytes': 300,
          'usableBytes': 250,
        },
      ],
      'network': <Map<String, Object?>>[
        <String, Object?>{
          'name': 'eth0',
          'receivedBytes': 800,
          'sentBytes': 500,
        },
      ],
    };

    test('sectionNames contains all four sections', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(
        info.sectionNames,
        containsAll(<String>['cpu', 'memory', 'jvm', 'server']),
      );
    });

    test('cpu accessor returns correct values', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(info.cpu['model'], equals('Apple M3'));
      expect(info.cpu['cores'], equals(8));
    });

    test('memory accessor returns correct usedMb', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(info.memory['usedMb'], equals(18000));
    });

    test('server accessor returns folia flag', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(info.server['folia'], equals(true));
    });

    test('entriesOf jvm has length 2', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(info.entriesOf('jvm').length, equals(2));
    });

    test('typed disk, mount, and network counters are decoded', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      expect(info.disks.single.model, equals('Fast Disk'));
      expect(info.mounts.single.usedBytes, equals(700));
      expect(info.mounts.single.usedFraction, closeTo(0.7, 0.001));
      expect(info.network.single.name, equals('eth0'));
      expect(info.totalDiskReadBytes, equals(400));
      expect(info.totalDiskWriteBytes, equals(250));
      expect(info.totalNetworkReceivedBytes, equals(800));
      expect(info.totalNetworkSentBytes, equals(500));
    });

    test('unknown extra section keys are preserved', () {
      final Map<String, dynamic> withDisk = {
        ...raw,
        'disk': {'totalGb': 512, 'usedGb': 200},
      };
      final EnvironmentInfo info = EnvironmentInfo.fromJson(withDisk);
      expect(info.sectionNames, contains('disk'));
      expect(info.sections['disk']!['totalGb'], equals(512));
    });

    test('non-map top-level values are ignored', () {
      final Map<String, dynamic> withExtra = {
        ...raw,
        'timestamp': 1234567890,
        'version': 'v3',
      };
      final EnvironmentInfo info = EnvironmentInfo.fromJson(withExtra);
      expect(info.sectionNames, isNot(contains('timestamp')));
      expect(info.sectionNames, isNot(contains('version')));
    });

    test('absent section accessor returns empty map without throwing', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(
        <String, dynamic>{},
      );
      expect(info.cpu, equals(const <String, Object?>{}));
      expect(info.memory, equals(const <String, Object?>{}));
      expect(info.jvm, equals(const <String, Object?>{}));
      expect(info.server, equals(const <String, Object?>{}));
    });

    test('entriesOf absent section returns empty list without throwing', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(
        <String, dynamic>{},
      );
      expect(info.entriesOf('cpu'), isEmpty);
    });

    test('toJson returns the sections map', () {
      final EnvironmentInfo info = EnvironmentInfo.fromJson(raw);
      final Map<String, Object?> json = info.toJson();
      final Map<String, Object?> cpu = json['cpu']! as Map<String, Object?>;
      expect(cpu['model'], equals('Apple M3'));
      expect(
        json.keys,
        containsAll(<String>['cpu', 'memory', 'jvm', 'server']),
      );
    });
  });
}
