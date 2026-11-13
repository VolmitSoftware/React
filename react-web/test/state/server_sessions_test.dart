library;

import 'package:test/test.dart';

import 'package:react_web/model/alert.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_live_model.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/fleet_rollup.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/server_session.dart';

import 'support/fake_react_client.dart';

ServerCredential cred(
  String id, {
  String fingerprint = 'fp-a',
  String token = 'tok-a',
  String host = 'a.example',
}) => ServerCredential(
  id: id,
  label: id,
  host: host,
  port: 9696,
  bearer: '$token.sig',
  fingerprint: fingerprint,
);

void main() {
  late Map<String, FakeReactClient> clients;
  late FleetManager fleet;

  setUp(() {
    clients = <String, FakeReactClient>{};
    fleet = FleetManager(
      storage: InMemoryFleetStorage(),
      clientFactory: (ServerCredential c) => clients[c.id] = FakeReactClient(
        name: c.id,
        fingerprint: c.fingerprint ?? '',
        role: c.id.startsWith('viewer') ? 'viewer' : 'admin',
        tweaks: <ControlItem>[controlItem('fast-snow')],
      ),
    );
  });

  tearDown(() => fleet.dispose());

  group('sessions own controllers per server (C1)', () {
    test(
      'each server has its own controllers and writes reach only it',
      () async {
        fleet.add(cred('paper', fingerprint: 'fp-p', token: 'tp'));
        fleet.add(cred('purpur', fingerprint: 'fp-u', token: 'tu'));
        final ServerSession paper = fleet.sessionFor('paper')!;
        final ServerSession purpur = fleet.sessionFor('purpur')!;

        expect(identical(paper.tweaks, purpur.tweaks), isFalse);
        expect(paper.tweaks.serverId, 'paper');
        expect(purpur.tweaks.serverId, 'purpur');

        await purpur.tweaks.load();
        await paper.tweaks.load();
        await paper.tweaks.toggle('fast-snow', true);

        expect(clients['paper']!.count('PUT /tweaks/fast-snow'), 1);
        expect(clients['purpur']!.count('PUT /tweaks/fast-snow'), 0);
        expect(purpur.tweaks.items.single.enabled, isFalse);
      },
    );

    test('a session hands out the same controller every time', () async {
      fleet.add(cred('paper'));
      final ServerSession session = fleet.sessionFor('paper')!;
      expect(identical(session.tweaks, session.tweaks), isTrue);
      expect(identical(session.logs, session.logs), isTrue);
      expect(session.clientAs<IControlClient>(), same(clients['paper']));
      expect(session.clientAs<IHeatmapClient>(), isNull);
    });

    test(
      'removing an entry disposes its session; stale writes go nowhere',
      () async {
        fleet.add(cred('paper'));
        final ServerSession session = fleet.sessionFor('paper')!;
        await session.tweaks.load();
        fleet.remove('paper');

        expect(session.disposed, isTrue);
        expect(session.tweaks.disposed, isTrue);
        expect(clients['paper']!.closed, isTrue);
        await session.tweaks.toggle('fast-snow', true);
        expect(clients['paper']!.count('PUT /tweaks/fast-snow'), 0);
        expect(fleet.sessionFor('paper'), isNull);
      },
    );

    test('notifies listeners on add, rename and remove', () async {
      int changes = 0;
      fleet.addListener(() => changes++);
      fleet.add(cred('paper'));
      fleet.rename('paper', 'Lobby');
      fleet.remove('paper');
      expect(changes, 3);
    });

    test('rename reaches the live session', () async {
      fleet.add(cred('paper'));
      fleet.rename('paper', 'Lobby');
      expect(fleet.sessionFor('paper')!.label, 'Lobby');
    });
  });

  group('one entry per server', () {
    test('pairing a listed server again takes the new pairing in place', () {
      fleet.add(cred('first'));
      final ServerSession old = fleet.sessionFor('first')!;
      final String id = fleet.add(
        cred('second', host: 'new.example', token: 'tok-new'),
      );
      expect(id, 'first');
      expect(fleet.servers.single.host, 'new.example');
      expect(fleet.servers.single.tokenId, 'tok-new');
      expect(old.disposed, isTrue);
      expect(fleet.sessionFor('first')!.key, isNot(old.key));
    });

    test('another server is another entry', () {
      fleet.add(cred('paper', fingerprint: 'fp-p'));
      fleet.add(cred('purpur', fingerprint: 'fp-u'));
      expect(fleet.servers, hasLength(2));
      expect(
        fleet.liveSources().map((FleetLiveSource s) => s.fingerprint),
        <String>['fp-p', 'fp-u'],
      );
    });

    test('the role comes with the pairing', () {
      fleet.add(
        const ServerCredential(
          id: 'viewer',
          label: 'Lobby',
          host: 'a.example',
          port: 9696,
          bearer: 'tok-v.sig',
          fingerprint: 'fp-v',
          role: 'viewer',
        ),
      );
      expect(fleet.sessionFor('viewer')!.role, 'viewer');
      expect(fleet.sessionFor('viewer')!.transport, ServerTransport.direct);
    });
  });

  group('where pairings are kept (Remember this browser)', () {
    late InMemoryFleetStorage local;
    late InMemoryFleetStorage tab;
    final List<FakeReactClient> made = <FakeReactClient>[];

    FleetManager open(InMemoryFleetStorage session) => FleetManager(
      storage: local,
      session: session,
      clientFactory: (ServerCredential c) {
        final FakeReactClient client = FakeReactClient(
          name: c.id,
          fingerprint: c.fingerprint ?? '',
        );
        made.add(client);
        return client;
      },
    );

    setUp(() {
      local = InMemoryFleetStorage();
      tab = InMemoryFleetStorage();
      made.clear();
    });

    test(
      'remembered: the bearer is in localStorage and every tab is paired',
      () {
        final FleetManager first = open(tab);
        first.add(cred('paper'));
        first.dispose();
        expect(local.read(FleetManager.storageKey), contains('tok-a.sig'));

        final FleetManager next = open(InMemoryFleetStorage());
        expect(next.servers.single.bearer, 'tok-a.sig');
        expect(next.pairingEnded('paper'), isFalse);
        next.dispose();
      },
    );

    test('not remembered: the bearer lives in this tab only', () {
      final FleetManager first = open(tab);
      first.add(
        ServerCredential.fromJson(<String, dynamic>{
          ...cred('paper').toJson(),
          'remember': false,
          'pairing': ServerCredential.pairingVersion,
        }),
      );
      first.dispose();
      expect(local.read(FleetManager.storageKey), isNot(contains('tok-a.sig')));
      expect(tab.read(FleetManager.tokenKey('paper')), 'tok-a.sig');

      final FleetManager reloaded = open(tab);
      expect(reloaded.servers.single.bearer, 'tok-a.sig');
      expect(reloaded.pairingEnded('paper'), isFalse);
      reloaded.dispose();

      final FleetManager closed = open(InMemoryFleetStorage());
      expect(closed.servers.single.label, 'paper');
      expect(closed.pairingEnded('paper'), isTrue);
      closed.sessionFor('paper')!.connection.start();
      expect(
        made.last.calls,
        isEmpty,
        reason: 'an ended pairing sends nothing',
      );
      closed.dispose();
    });

    test(
      'an entry paired with an RCT2 code shows as ended and sends nothing',
      () {
        local.write(
          FleetManager.storageKey,
          '[{"id":"old","label":"Paper","host":"127.0.0.1","port":9820,'
          '"bearer":"abc123.hmac","secure":false,"basePath":"",'
          '"relayUrl":null,"serverPubKey":"k","fingerprint":"fp-old"}]',
        );
        final FleetManager fleet = open(tab);
        expect(fleet.servers.single.label, 'Paper');
        expect(fleet.servers.single.bearer, isEmpty);
        expect(fleet.pairingEnded('old'), isTrue);
        fleet.sessionFor('old')!.connection.start();
        expect(made.single.calls, isEmpty);
        expect(
          local.read(FleetManager.storageKey),
          contains('abc123.hmac'),
          reason: 'loading alone does not rewrite storage',
        );
        fleet.dispose();
      },
    );

    test('a new link for an ended entry pairs it again in place', () {
      local.write(
        FleetManager.storageKey,
        '[{"id":"old","label":"Paper","host":"127.0.0.1","port":9820,'
        '"bearer":"abc123.hmac","fingerprint":"fp-a"}]',
      );
      final FleetManager fleet = open(tab);
      final String id = fleet.add(cred('fresh'));
      expect(id, 'old');
      expect(fleet.pairingEnded('old'), isFalse);
      expect(
        local.read(FleetManager.storageKey),
        isNot(contains('abc123.hmac')),
      );
      fleet.dispose();
    });

    test('forgetting a server drops its bearer from the tab too', () {
      final FleetManager fleet = open(tab);
      fleet.add(
        ServerCredential.fromJson(<String, dynamic>{
          ...cred('paper').toJson(),
          'remember': false,
          'pairing': ServerCredential.pairingVersion,
        }),
      );
      fleet.remove('paper');
      expect(tab.read(FleetManager.tokenKey('paper')), isNull);
      fleet.dispose();
    });

    test('the pairing end is stored once it moved by an hour', () {
      final FleetManager fleet = open(tab);
      fleet.add(cred('paper'));
      final DateTime end = DateTime.utc(2026, 11, 20, 10);
      fleet.pairingEndsAt('paper', end);
      expect(fleet.servers.single.expiresAt, end);
      fleet.pairingEndsAt('paper', end.add(const Duration(minutes: 5)));
      expect(fleet.servers.single.expiresAt, end);
      fleet.pairingEndsAt('paper', end.add(const Duration(hours: 2)));
      expect(fleet.servers.single.expiresAt, end.add(const Duration(hours: 2)));
      fleet.dispose();
    });
  });

  group('FleetRollup by server identity (M8, L7)', () {
    FleetServerLive live(String id, String fp, {double tps = 20}) =>
        FleetServerLive(
          id: id,
          name: id,
          state: ConnState.live,
          snapshot: snapshotWith(tps: tps),
          fingerprint: fp,
        );

    test('a server paired twice counts once', () {
      final FleetRollup rollup = FleetRollup.compute(
        servers: <FleetServerLive>[
          live('paper-admin', 'fp-p', tps: 15),
          live('paper-viewer', 'fp-p', tps: 15),
          live('purpur', 'fp-u'),
        ],
        openAlerts: <FleetAlert>[
          FleetAlert(
            serverId: 'paper-admin',
            serverName: 'paper-admin',
            metricId: 'tick-time',
            title: 'Tick time',
            message: '',
            severity: AlertSeverity.warning,
            value: 60,
            threshold: 50,
            firstSeen: DateTime(2026),
            lastSeen: DateTime(2026),
          ),
          FleetAlert(
            serverId: 'paper-viewer',
            serverName: 'paper-viewer',
            metricId: 'tick-time',
            title: 'Tick time',
            message: '',
            severity: AlertSeverity.warning,
            value: 60,
            threshold: 50,
            firstSeen: DateTime(2026),
            lastSeen: DateTime(2026),
          ),
        ],
      );
      expect(rollup.servers, hasLength(3));
      expect(rollup.serverCount, 2);
      expect(rollup.alertCounts[AlertSeverity.warning], 1);
      expect(rollup.attentionCount, 1);
      expect(rollup.needsAttention.single.id, 'paper-admin');
      expect(rollup.overall, FleetHealth.warning);
    });

    test('a revoked second token of a healthy server needs attention', () {
      final FleetRollup rollup = FleetRollup.compute(
        servers: <FleetServerLive>[
          live('paper-admin', 'fp-p'),
          FleetServerLive(
            id: 'paper-old',
            name: 'paper-old',
            state: ConnState.offline,
            fingerprint: 'fp-p',
            access: ConnAccess.revoked,
          ),
        ],
        openAlerts: const <FleetAlert>[],
      );
      expect(rollup.serverCount, 1);
      expect(rollup.needsAttention.map((FleetServerHealth s) => s.id), <String>[
        'paper-old',
      ]);
    });
  });
}
