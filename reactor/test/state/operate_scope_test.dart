library;

import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/action_descriptor.dart';
import 'package:reactor/model/config_tree.dart';
import 'package:reactor/model/environment_info.dart';
import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/incident_status.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_log_socket.dart';
import 'package:reactor/state/fleet_manager.dart';
import 'package:reactor/state/memory_fleet_storage.dart';
import 'package:reactor/state/operate_scope.dart';

class _FullOpClient implements IReactClient, IOperateClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
        serverName: 'Full',
        version: '1.0.0',
        folia: false,
        serverId: '127.0.0.1:9696',
      );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();

  @override
  Future<List<ActionDescriptor>> actions() async => <ActionDescriptor>[];

  @override
  Future<ActionTicket> executeAction(String id,
          {required Map<String, Object?> params, required bool confirm}) async =>
      throw UnimplementedError();

  @override
  Future<IncidentStatus> incidents() async => throw UnimplementedError();

  @override
  Future<EnvironmentInfo> environment() async => throw UnimplementedError();

  @override
  Future<ConfigTree> config() async => throw UnimplementedError();

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async =>
      throw UnimplementedError();

  @override
  Future<ConfigTree> applyPreset(String name) async =>
      throw UnimplementedError();

  @override
  Future<List<String>> logs({int limit = 200}) async => <String>[];
}

class _MetricsOnlyClient implements IReactClient {
  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
        serverName: 'MetricsOnly',
        version: '1.0.0',
        folia: false,
        serverId: '127.0.0.1:9696',
      );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

Map<String, dynamic> _credJson({
  String id = 'srv-1',
  String label = 'TestServer',
  String host = 'localhost',
  int port = 7979,
  String bearer = 'tid1.tsig1',
}) =>
    <String, dynamic>{
      'id': id,
      'label': label,
      'host': host,
      'port': port,
      'bearer': bearer,
      'secure': false,
    };

FleetManager _seededFleetManager({
  required FleetStorage storage,
  required ReactClientFactory clientFactory,
  String id = 'srv-1',
}) {
  storage.write(
    FleetManager.storageKey,
    jsonEncode(<Map<String, dynamic>>[_credJson(id: id)]),
  );
  return FleetManager(storage: storage, clientFactory: clientFactory);
}

class _FakeOperateClient implements IOperateClient {
  @override
  Future<List<ActionDescriptor>> actions() async => <ActionDescriptor>[];

  @override
  Future<ActionTicket> executeAction(String id,
          {required Map<String, Object?> params, required bool confirm}) async =>
      throw UnimplementedError();

  @override
  Future<IncidentStatus> incidents() async => throw UnimplementedError();

  @override
  Future<EnvironmentInfo> environment() async => throw UnimplementedError();

  @override
  Future<ConfigTree> config() async => throw UnimplementedError();

  @override
  Future<ConfigTree> applyConfig(Map<String, Object?> changes) async =>
      throw UnimplementedError();

  @override
  Future<ConfigTree> applyPreset(String name) async =>
      throw UnimplementedError();

  @override
  Future<List<String>> logs({int limit = 200}) async => <String>[];
}

const String _kMarker = 'operate-client-present';

class _Probe extends StatelessWidget {
  const _Probe();

  @override
  Widget build(BuildContext context) {
    final OperateScope? scope = OperateScope.of(context);
    if (scope?.client != null) {
      return Component.text(_kMarker);
    }
    return Component.fragment(const <Widget>[]);
  }
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('FleetManager.operateClientFor', () {
    test('returns operate-capable client when factory produces IOperateClient',
        () {
      final _FullOpClient full = _FullOpClient();
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => full,
      );
      expect(fm.operateClientFor('srv-1'), same(full));
    });

    test('returns null when factory produces a non-operate client', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _MetricsOnlyClient(),
      );
      expect(fm.operateClientFor('srv-1'), isNull);
    });

    test('returns null for unknown id', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FullOpClient(),
      );
      expect(fm.operateClientFor('no-such-id'), isNull);
    });
  });

  group('FleetManager.logSocketFactoryFor', () {
    test('returns null under dart test (kIsWeb false)', () {
      final FleetManager fm = _seededFleetManager(
        storage: InMemoryFleetStorage(),
        clientFactory: (_) => _FullOpClient(),
      );
      expect(fm.logSocketFactoryFor('srv-1'), isNull);
    });
  });

  group('OperateScope', () {
    testServer(
      'probe renders marker when client is non-null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            OperateScope(
              client: _FakeOperateClient(),
              logSocketFactory: null,
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains(_kMarker),
          isTrue,
          reason:
              'marker must appear when OperateScope carries a non-null client',
        );
      },
    );

    testServer(
      'probe does not render marker when client is null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            OperateScope(
              client: null,
              logSocketFactory: null,
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains(_kMarker),
          isFalse,
          reason: 'marker must not appear when OperateScope carries null client',
        );
      },
    );

    test('updateShouldNotify returns true when client changes', () {
      final _FakeOperateClient c1 = _FakeOperateClient();
      final _FakeOperateClient c2 = _FakeOperateClient();
      final OperateScope s1 = OperateScope(
          client: c1,
          logSocketFactory: null,
          child: Component.fragment(const <Widget>[]));
      final OperateScope s2 = OperateScope(
          client: c2,
          logSocketFactory: null,
          child: Component.fragment(const <Widget>[]));
      expect(s1.updateShouldNotify(s2), isTrue);
    });

    test('updateShouldNotify returns false when client is same instance', () {
      final _FakeOperateClient c = _FakeOperateClient();
      final OperateScope s1 = OperateScope(
          client: c,
          logSocketFactory: null,
          child: Component.fragment(const <Widget>[]));
      final OperateScope s2 = OperateScope(
          client: c,
          logSocketFactory: null,
          child: Component.fragment(const <Widget>[]));
      expect(s1.updateShouldNotify(s2), isFalse);
    });

    test('updateShouldNotify returns true when logSocketFactory changes', () {
      ILogSocket fakeFactory() => throw UnimplementedError();
      final OperateScope s1 = OperateScope(
          client: null,
          logSocketFactory: fakeFactory,
          child: Component.fragment(const <Widget>[]));
      final OperateScope s2 = OperateScope(
          client: null,
          logSocketFactory: null,
          child: Component.fragment(const <Widget>[]));
      expect(s1.updateShouldNotify(s2), isTrue);
    });
  });
}
