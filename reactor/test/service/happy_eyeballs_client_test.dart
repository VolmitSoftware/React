library;

import 'package:test/test.dart';

import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/sampler_sample.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/happy_eyeballs_client.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';

class _FakeClient implements IReactClient {
  String serverId;
  Duration delay;
  bool shouldThrow;

  _FakeClient({
    required this.serverId,
    this.delay = Duration.zero,
    this.shouldThrow = false,
  });

  @override
  Future<IdentityInfo> identity() async {
    if (delay > Duration.zero) await Future<void>.delayed(delay);
    if (shouldThrow) throw const ReactUnavailable();
    return IdentityInfo(
      serverName: 'test',
      version: '1.0',
      folia: false,
      serverId: serverId,
    );
  }

  @override
  Future<ServerSnapshot> metrics() async {
    if (delay > Duration.zero) await Future<void>.delayed(delay);
    if (shouldThrow) throw const ReactUnavailable();
    return ServerSnapshot(byId: <String, SamplerSample>{}, at: DateTime.now());
  }
}

void main() {
  group('HappyEyeballsClient', () {
    test('(1) both succeed, direct returns first -> activePath==direct', () async {
      final _FakeClient direct = _FakeClient(serverId: 'fp:direct');
      final _FakeClient relay = _FakeClient(
        serverId: 'fp:relay',
        delay: const Duration(milliseconds: 20),
      );
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
      );

      await client.identity();

      expect(client.activePath, equals(RelayPath.direct));
    });

    test('(2) direct throws ReactUnavailable, relay succeeds -> activePath==relay', () async {
      final _FakeClient direct = _FakeClient(
        serverId: 'fp:direct',
        shouldThrow: true,
      );
      final _FakeClient relay = _FakeClient(serverId: 'fp:relay');
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
      );

      await client.identity();

      expect(client.activePath, equals(RelayPath.relay));
    });

    test('(3) pin enforcement: direct serverId bad, relay matches -> relay chosen', () async {
      const String pin = 'good:fp:aa:bb:cc:dd';
      final _FakeClient direct = _FakeClient(serverId: 'bad:fp:wrong');
      final _FakeClient relay = _FakeClient(
        serverId: pin,
        delay: const Duration(milliseconds: 5),
      );
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
        pinnedFingerprint: pin,
      );

      await client.identity();

      expect(client.activePath, equals(RelayPath.relay));
    });

    test('(4) both mismatch pin -> throws ReactAuthException', () async {
      const String pin = 'expected:fp';
      final _FakeClient direct = _FakeClient(serverId: 'wrong1');
      final _FakeClient relay = _FakeClient(serverId: 'wrong2');
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
        pinnedFingerprint: pin,
      );

      await expectLater(
        client.identity(),
        throwsA(isA<ReactAuthException>()),
      );
    });

    test('(5) pinnedFingerprint null -> first success wins regardless of serverId', () async {
      final _FakeClient direct = _FakeClient(serverId: 'any-id');
      final _FakeClient relay = _FakeClient(
        serverId: 'other-id',
        delay: const Duration(milliseconds: 20),
      );
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
      );

      await client.identity();

      expect(client.activePath, equals(RelayPath.direct));
    });

    test('(6) metrics ReactUnavailable marks stale, re-probe picks direct when healthy', () async {
      final _FakeClient direct = _FakeClient(
        serverId: 'fp:direct',
        shouldThrow: true,
      );
      final _FakeClient relay = _FakeClient(serverId: 'fp:relay');
      final HappyEyeballsClient client = HappyEyeballsClient(
        direct: direct,
        relay: relay,
      );

      await client.identity();
      expect(client.activePath, equals(RelayPath.relay));

      relay.shouldThrow = true;
      direct.shouldThrow = false;

      await expectLater(
        client.metrics(),
        throwsA(isA<ReactUnavailable>()),
      );
      expect(client.activePath, equals(RelayPath.none));

      await client.identity();
      expect(client.activePath, equals(RelayPath.direct));
    });

    test('(7a) relay-only (direct==null) uses relay', () async {
      final _FakeClient relay = _FakeClient(serverId: 'fp:relay');
      final HappyEyeballsClient client = HappyEyeballsClient(relay: relay);

      await client.identity();

      expect(client.activePath, equals(RelayPath.relay));
    });

    test('(7b) direct-only (relay==null) uses direct', () async {
      final _FakeClient direct = _FakeClient(serverId: 'fp:direct');
      final HappyEyeballsClient client = HappyEyeballsClient(direct: direct);

      await client.identity();

      expect(client.activePath, equals(RelayPath.direct));
    });
  });
}
