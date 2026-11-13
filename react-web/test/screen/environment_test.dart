library;

import 'dart:async';
import 'dart:collection';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' as jaspr show Component, Text;
import 'package:jaspr/jaspr.dart' show StatefulBuilder;
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/environment_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/environment.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/server_session.dart';
import 'package:react_web/state/server_session_scope.dart';
import 'package:react_web/ui/kit/mode.dart';

import '../state/support/fake_react_client.dart';
import 'monitor_support.dart';

EnvironmentInfo _fakeInfo() => EnvironmentInfo(
  sections: LinkedHashMap<String, Map<String, Object?>>.from(
    <String, Map<String, Object?>>{
      'cpu': <String, Object?>{'model': 'Apple M3', 'cores': 8},
      'memory': <String, Object?>{'usedMb': 18000},
      'jvm': <String, Object?>{'version': '25'},
      'server': <String, Object?>{'brand': 'Purpur', 'react': '9.9.9-qa'},
      'react': <String, Object?>{'version': '9.9.9-qa'},
    },
  ),
  disks: const <EnvironmentDisk>[
    EnvironmentDisk(
      name: 'disk0',
      model: 'Fast Disk',
      sizeBytes: 1000,
      readBytes: 400,
      writeBytes: 250,
      reads: 4,
      writes: 2,
      queueLength: 0,
      transferTimeMillis: 10,
      timestampMillis: 100,
    ),
  ],
  mounts: const <EnvironmentMount>[
    EnvironmentMount(
      name: 'root',
      mount: '/',
      description: 'Root',
      type: 'apfs',
      totalBytes: 1000,
      freeBytes: 300,
      usableBytes: 250,
    ),
    // Read-only and near full by design: never a full-disk warning.
    EnvironmentMount(
      name: 'Preboot',
      mount: '/System/Volumes/Preboot',
      description: 'Local Disk',
      type: 'apfs',
      totalBytes: 1000,
      freeBytes: 30,
      usableBytes: 30,
    ),
  ],
  network: const <EnvironmentNetworkInterface>[
    EnvironmentNetworkInterface(
      name: 'en0',
      displayName: 'Primary network',
      mtu: 1500,
      macAddress: '',
      ipv4Addresses: <String>['127.0.0.1'],
      ipv6Addresses: <String>[],
      speedBitsPerSecond: 1000000000,
      receivedBytes: 800,
      sentBytes: 500,
      receivedPackets: 8,
      sentPackets: 5,
      receiveErrors: 0,
      sendErrors: 0,
      receiveDrops: 0,
      collisions: 0,
      timestampMillis: 100,
    ),
  ],
);

ServerSnapshot _hostSnapshot({double errors = 0}) => snapshotOf(<SamplerSample>[
  sample(
    'physical-memory-used',
    1024,
    suffix: 'KB',
    name: 'Physical memory used',
  ),
  sample('network-receive-errors', errors, name: 'Receive errors'),
  sample('disk-read-rate', 5, name: 'Disk read rate'),
]);

Widget _view({ReactorMode mode = ReactorMode.simple, double errors = 0}) =>
    onServer(
      EnvironmentView(
        info: _fakeInfo(),
        snapshot: _hostSnapshot(errors: errors),
        diskWriteHistory: const <double>[0, 64],
        networkReceiveHistory: const <double>[0, 256],
        networkSendHistory: const <double>[0, 96],
      ),
      snapshot: _hostSnapshot(errors: errors),
      mode: mode,
    );

class _HostClient extends FakeReactClient {
  _HostClient(String id, this.reply) : super(fingerprint: 'fp-$id');

  final Future<EnvironmentInfo> Function() reply;
  int environmentCalls = 0;

  @override
  Future<EnvironmentInfo> environment() {
    environmentCalls++;
    return reply();
  }
}

Widget _screen(ServerSession? session) => onServer(
  session == null
      ? const EnvironmentScreen()
      : ServerSessionScope(session: session, child: const EnvironmentScreen()),
  snapshot: _hostSnapshot(),
);

void main() {
  group('EnvironmentView system mounts', () {
    testServer('stay out of the tiles in Simple', (ServerTester tester) async {
      tester.pumpComponent(_view());
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains('/System/Volumes/Preboot')));
      expect(body, isNot(contains('data-tone="error"')));
      expect(
        body,
        isNot(contains(ReactorText.environmentSystemMounts.english)),
      );
    });

    testServer('are listed, folded and uncoloured, in Advanced', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view(mode: ReactorMode.advanced));
      final String body = (await tester.request('/')).body;
      expect(
        body,
        contains('${ReactorText.environmentSystemMounts.english} (1)'),
      );
      expect(body, contains('/System/Volumes/Preboot'));
      expect(body, isNot(contains('data-tone="error"')));
    });
  });

  group('EnvironmentView in Simple', () {
    testServer('host tiles, mount capacity and both rate charts', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view());
      final String body = (await tester.request('/')).body;

      expect(body, contains('Physical memory used'));
      expect(body, contains('role="meter"'));
      expect(body, contains('of 1,000 B'));
      expect(occurrences(body, 'data-chart-card'), 2);
      expect(body, contains(ReactorText.environmentDisk.english));
      expect(body, contains(ReactorText.environmentNetwork.english));
      expect(body, contains(ReactorText.environmentRead.english));
      expect(body, contains(ReactorText.environmentReceived.english));
    });

    testServer('raw sections, device lists and rate tiles stay Advanced', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view());
      final String body = (await tester.request('/')).body;

      expect(body, isNot(contains('Apple M3')));
      expect(body, isNot(contains('Purpur')));
      expect(body, isNot(contains('Primary network (en0)')));
      expect(body, isNot(contains('Disk read rate')));
      expect(body, isNot(contains('role="table"')));
    });

    testServer('network errors show once there are any', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view());
      expect(
        (await tester.request('/')).body,
        isNot(contains('Receive errors')),
      );
    });

    testServer('network errors above zero do show', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view(errors: 3));
      expect((await tester.request('/')).body, contains('Receive errors'));
    });
  });

  group('EnvironmentView rate charts', () {
    testServer('one reading is not a line yet', (ServerTester tester) async {
      tester.pumpComponent(
        onServer(
          EnvironmentView(
            info: _fakeInfo(),
            diskReadHistory: const <double>[5],
            diskWriteHistory: const <double>[3],
            networkReceiveHistory: const <double>[2],
            networkSendHistory: const <double>[1],
          ),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains('<polyline')));
      expect(occurrences(body, ReactorText.chartAwaitingSamples.english), 2);
    });
  });

  group('EnvironmentView in Advanced', () {
    testServer('adds the raw sections and the device lists', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view(mode: ReactorMode.advanced));
      final String body = (await tester.request('/')).body;

      for (final String text in <String>[
        'Apple M3',
        'Purpur',
        'CPU',
        'JVM',
        ReactorText.memoryTitle.english,
        ReactorText.commonServer.english,
        'Primary network (en0)',
        'disk0 (Fast Disk)',
        ReactorText.environmentValue.english,
      ]) {
        expect(body, contains(text));
      }
      // The rate is the Disk chart's; no tile repeats it.
      expect(body, isNot(contains('Disk read rate')));
    });

    testServer('never shows React facts such as its version', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_view(mode: ReactorMode.advanced));
      expect((await tester.request('/')).body, isNot(contains('9.9.9-qa')));
    });
  });

  group('EnvironmentScreen states', () {
    testServer('says a live connection is needed without a client', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_screen(null));
      final String body = (await tester.request('/')).body;
      expect(body, contains(ReactorText.environmentLiveRequired.english));
      expect(body, contains('ui-empty'));
    });

    testComponents('a failed first load offers Retry', (
      ComponentTester tester,
    ) async {
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        'a': _HostClient('a', () async => throw const ReactForbidden()),
      });
      addTearDown(fleet.dispose);
      tester.pumpComponent(_screen(fleet.sessionFor('a')));
      await tester.pump();
      await tester.pump();

      expect(find.text(ReactorText.errorForbidden.english), findsOneComponent);
      expect(find.text(ReactorText.commonRetry.english), findsOneComponent);
    });

    testComponents('another server starts from nothing', (
      ComponentTester tester,
    ) async {
      final _HostClient second = _HostClient(
        'b',
        () => Completer<EnvironmentInfo>().future,
      );
      final FleetManager fleet = await fleetOf(<String, IReactClient>{
        'a': _HostClient('a', () async => _fakeInfo()),
        'b': second,
      });
      addTearDown(fleet.dispose);
      late void Function(void Function()) rebuild;
      ServerSession current = fleet.sessionFor('a')!;
      tester.pumpComponent(
        StatefulBuilder(
          builder: (BuildContext context, void Function(void Function()) set) {
            rebuild = set;
            return _screen(current);
          },
        ),
      );
      await tester.pump();
      await tester.pump();
      Finder ofTotal() => find.byComponentPredicate(
        (jaspr.Component c) => c is jaspr.Text && c.text.endsWith('of 1,000 B'),
      );
      expect(ofTotal(), findsOneComponent);

      rebuild(() => current = fleet.sessionFor('b')!);
      await tester.pump();
      expect(second.environmentCalls, 1);
      expect(ofTotal(), findsNothing);
    });
  });
}
