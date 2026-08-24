import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/identity_info.dart';
import 'package:react_web/model/server_capabilities.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/screen/add_server.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/service/relay_identity.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';

class _SuccessClient implements IReactClient, IPingClient {
  final String serverFingerprint;
  final Object? pingFailure;
  int identityCallCount = 0;
  int pingCallCount = 0;

  _SuccessClient({String? serverFingerprint, this.pingFailure})
    : serverFingerprint = serverFingerprint ?? _fingerprint;

  @override
  Future<IdentityInfo> identity() async {
    identityCallCount++;
    return IdentityInfo(
      serverName: 'TestServer',
      version: '1.0.0',
      folia: false,
      serverId: serverFingerprint,
    );
  }

  @override
  Future<ServerCapabilities> ping() async {
    pingCallCount++;
    final Object? failure = pingFailure;
    if (failure != null) throw failure;
    return ServerCapabilities(
      protocolVersion: 2,
      serverFingerprint: serverFingerprint,
      relayAvailable: false,
    );
  }

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

class _FailClient implements IReactClient, IPingClient {
  @override
  Future<IdentityInfo> identity() async =>
      throw Exception('connection refused');

  @override
  Future<ServerCapabilities> ping() async => ServerCapabilities(
    protocolVersion: 2,
    serverFingerprint: _fingerprint,
    relayAvailable: false,
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

class _MemStorage implements FleetStorage {
  final Map<String, String> _data = <String, String>{};

  @override
  String? read(String key) => _data[key];

  @override
  void write(String key, String value) => _data[key] = value;

  @override
  void remove(String key) => _data.remove(key);
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);
final String _serverPubKey = base64Url
    .encode(List<int>.generate(48, (int index) => index + 1))
    .replaceAll('=', '');
final String _fingerprint = RelayIdentity.fingerprint(_serverPubKey);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

String _validCode({
  String directUrl = 'http://localhost:7979',
  String relayUrl = '',
  String? serverPubKey,
  String? fingerprint,
  String tokenId = 'tid1',
  String tokenSig = 'tsig1',
}) => PairingCode.encode(
  directUrl: directUrl,
  relayUrl: relayUrl,
  serverPubKey: serverPubKey ?? _serverPubKey,
  fingerprint: fingerprint ?? _fingerprint,
  tokenId: tokenId,
  tokenSig: tokenSig,
);

void main() {
  group('PairingCode', () {
    test('decodes an exact RCT2 payload', () {
      final PairingCode? code = PairingCode.decode(
        _validCode(
          directUrl: 'https://server.example.com:9443',
          relayUrl: 'wss://relay.example.com',
          tokenId: 'token-id',
          tokenSig: 'token-signature',
        ),
      );

      expect(code, isNotNull);
      expect(code!.directUrl, equals('https://server.example.com:9443'));
      expect(code.relayUrl, equals('wss://relay.example.com'));
      expect(code.serverPubKey, equals(_serverPubKey));
      expect(code.fingerprint, equals(_fingerprint));
      expect(code.tokenId, equals('token-id'));
      expect(code.tokenSig, equals('token-signature'));
    });

    test('extracts the code from a complete console line', () {
      final String encoded = _validCode();
      final PairingCode? code = PairingCode.decode(
        'React Web pairing code: $encoded\nExpires in 10 minutes',
      );

      expect(code, isNotNull);
      expect(code!.directUrl, equals('http://localhost:7979'));
    });

    test('rejects RCT1 and malformed RCT2 payloads', () {
      expect(PairingCode.decode('RCT1.dGVzdA'), isNull);
      expect(PairingCode.decode('RCT2.!!!'), isNull);
      expect(PairingCode.decode('RCT2.bm90anNvbg'), isNull);
      final String partial = PairingCode.encodeRaw('{"directUrl":""}');
      expect(PairingCode.decode('RCT2.$partial'), isNull);
    });

    test('reports an incomplete payload', () {
      expect(
        PairingCode.validationMessage('RCT2.A'),
        ReactorText.addServerCodeIncomplete,
      );
    });

    test('rejects an inconsistent public key fingerprint', () {
      expect(
        PairingCode.decode(
          _validCode(fingerprint: List<String>.filled(64, '0').join()),
        ),
        isNull,
      );
    });

    test('rejects unsafe transport URLs', () {
      expect(
        PairingCode.decode(_validCode(directUrl: 'javascript:alert(1)')),
        isNull,
      );
      expect(
        PairingCode.decode(_validCode(relayUrl: 'https://relay.example.com')),
        isNull,
      );
    });

    test('requires at least one transport', () {
      expect(
        PairingCode.decode(_validCode(directUrl: '', relayUrl: '')),
        isNull,
      );
    });

    test('creates a secure direct credential from the advertised URL', () {
      final PairingCode code = PairingCode.decode(
        _validCode(directUrl: 'https://server.example.com'),
      )!;
      final ServerCredential credential = code.toCredential(
        id: 'server-1',
        label: 'Production',
      );

      expect(credential.host, equals('server.example.com'));
      expect(credential.port, equals(443));
      expect(credential.secure, isTrue);
      expect(credential.bearer, equals('tid1.tsig1'));
      expect(credential.fingerprint, equals(_fingerprint));
    });

    test('preserves a reverse-proxy path and bracketed IPv6 host', () {
      final PairingCode code = PairingCode.decode(
        _validCode(directUrl: 'https://[2001:db8::1]:9443/proxy/react'),
      )!;
      final ServerCredential credential = code.toCredential(
        id: 'server-1',
        label: 'IPv6 proxy',
      );

      expect(credential.host, equals('2001:db8::1'));
      expect(credential.port, equals(9443));
      expect(credential.basePath, equals('/proxy/react'));
      expect(
        credential.directEndpoint('api/v1/identity').toString(),
        equals('https://[2001:db8::1]:9443/proxy/react/api/v1/identity'),
      );
    });

    test('creates a relay-only credential', () {
      final PairingCode code = PairingCode.decode(
        _validCode(directUrl: '', relayUrl: 'wss://relay.example.com'),
      )!;
      final ServerCredential credential = code.toCredential(
        id: 'server-1',
        label: 'Relay server',
      );

      expect(credential.host, isEmpty);
      expect(credential.port, equals(0));
      expect(credential.relayUrl, equals('wss://relay.example.com'));
      expect(credential.serverPubKey, equals(_serverPubKey));
    });
  });

  group('FleetManager pairing', () {
    late _MemStorage storage;

    setUp(() {
      storage = _MemStorage();
    });

    test('adds, persists, selects, and removes multiple servers', () async {
      final FleetManager fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );
      final PairingCode first = PairingCode.decode(
        _validCode(directUrl: 'http://server-one:7979'),
      )!;
      final PairingCode second = PairingCode.decode(
        _validCode(directUrl: 'http://server-two:7980'),
      )!;

      await fleet.add(first.toCredential(id: 'one', label: 'One'));
      await fleet.add(second.toCredential(id: 'two', label: 'Two'));
      fleet.setActive('two');

      expect(fleet.servers, hasLength(2));
      expect(fleet.servers.map((ServerCredential c) => c.label), <String>[
        'TestServer',
        'TestServer',
      ]);
      expect(fleet.activeManager, same(fleet.managerFor('two')));
      expect(storage.read(FleetManager.storageKey), isNotNull);

      fleet.remove('two');
      expect(fleet.servers, hasLength(1));
      expect(fleet.activeManager, isNull);
    });

    test('does not retain a server when identity verification fails', () async {
      final FleetManager fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => _FailClient(),
      );
      final PairingCode code = PairingCode.decode(_validCode())!;

      await expectLater(
        fleet.add(code.toCredential(id: 'server-1', label: 'Server')),
        throwsException,
      );
      expect(fleet.servers, isEmpty);
    });

    test('restores persisted credentials', () async {
      final FleetManager first = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );
      final PairingCode code = PairingCode.decode(_validCode())!;
      await first.add(code.toCredential(id: 'server-1', label: 'Server'));

      final FleetManager restored = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );
      await restored.load();

      expect(restored.servers, hasLength(1));
      expect(restored.managerFor('server-1'), isA<ConnectionManager>());
    });
  });

  group('AddServerScreen', () {
    testServer('renders the RCT2 pairing console and controls', (
      ServerTester tester,
    ) async {
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );
      tester.pumpComponent(_wrap(AddServerScreen(fleetManager: fleet)));
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, equals(200));
      expect(response.body, contains('RCT2'));
      expect(response.body, contains(ReactorText.addServerPair.english));
      expect(response.body, contains(ReactorText.addServerClearCode.english));
      expect(response.body, contains(ReactorText.addServerResetFleet.english));
      expect(response.body, contains('reactor-pane-state is-empty'));
      expect(
        response.body,
        contains(ReactorText.addServerConnectionFlow.english),
      );
      expect(response.body, contains(ReactorText.addServerSecurity.english));
      expect(response.body, contains('TCP 9696 -&gt; server'));
      expect(response.body, contains('advertisedUrl = public URL'));
      expect(response.body, contains('/react web pair my-server viewer'));
      expect(response.body, contains('react.use'));
      expect(response.body, contains('viewer, operator, or admin'));
      expect(response.body, contains('RCT2.…'));
      expect(response.body, contains('/react web revoke &lt;token-id&gt;'));
      expect(response.body, contains('role="list"'));
      expect(response.body, contains('role="listitem"'));
    });

    test('pairs a valid direct server', () async {
      final _SuccessClient client = _SuccessClient();
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => client,
      );

      final ServerCredential credential = await AddServerScreen.pairServer(
        _validCode(directUrl: 'http://server.example.com:7979'),
        fleet,
      );

      expect(fleet.servers, hasLength(1));
      expect(credential.label, equals('server.example.com'));
      expect(client.pingCallCount, equals(1));
      expect(client.identityCallCount, equals(1));
    });

    test(
      'rejects a mismatched direct fingerprint without persisting',
      () async {
        final _MemStorage storage = _MemStorage();
        final _SuccessClient client = _SuccessClient(
          serverFingerprint: List<String>.filled(64, '0').join(),
        );
        final FleetManager fleet = FleetManager(
          storage: storage,
          clientFactory: (ServerCredential _) => client,
        );

        await expectLater(
          AddServerScreen.pairServer(
            _validCode(directUrl: 'http://server.example.com:7979'),
            fleet,
          ),
          throwsA(isA<ReactAuthException>()),
        );

        expect(client.pingCallCount, equals(1));
        expect(client.identityCallCount, equals(0));
        expect(fleet.servers, isEmpty);
        expect(storage.read(FleetManager.storageKey), isNull);
      },
    );

    test('does not persist when the direct ping is unreachable', () async {
      final _MemStorage storage = _MemStorage();
      final _SuccessClient client = _SuccessClient(
        pingFailure: const ReactUnavailable('connection refused'),
      );
      final FleetManager fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => client,
      );

      await expectLater(
        AddServerScreen.pairServer(_validCode(), fleet),
        throwsA(isA<ReactUnavailable>()),
      );

      expect(client.identityCallCount, equals(0));
      expect(fleet.servers, isEmpty);
      expect(storage.read(FleetManager.storageKey), isNull);
    });

    test('does not persist when the direct ping is malformed', () async {
      final _MemStorage storage = _MemStorage();
      final _SuccessClient client = _SuccessClient(
        pingFailure: const FormatException('malformed ping'),
      );
      final FleetManager fleet = FleetManager(
        storage: storage,
        clientFactory: (ServerCredential _) => client,
      );

      await expectLater(
        AddServerScreen.pairServer(_validCode(), fleet),
        throwsA(isA<FormatException>()),
      );

      expect(client.identityCallCount, equals(0));
      expect(fleet.servers, isEmpty);
      expect(storage.read(FleetManager.storageKey), isNull);
    });

    test('overrides an unusable loopback pairing address', () async {
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );

      final ServerCredential credential = await AddServerScreen.pairServer(
        _validCode(directUrl: 'http://127.0.0.1:9696'),
        fleet,
        directUrlOverride: 'https://panel.example.net/react',
      );

      expect(credential.host, equals('panel.example.net'));
      expect(credential.secure, isTrue);
      expect(credential.basePath, equals('/react'));
      expect(credential.bearer, equals('tid1.tsig1'));
      expect(credential.fingerprint, equals(_fingerprint));
    });

    test('gives relay-only servers a fingerprint label', () async {
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );

      final ServerCredential credential = await AddServerScreen.pairServer(
        _validCode(directUrl: '', relayUrl: 'wss://relay.example.com'),
        fleet,
      );

      expect(credential.label, startsWith('React '));
      expect(credential.relayUrl, equals('wss://relay.example.com'));
    });

    test('rejects invalid pairing input without changing the fleet', () async {
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );

      await expectLater(
        AddServerScreen.pairServer('NOT_A_VALID_CODE', fleet),
        throwsA(isA<ArgumentError>()),
      );
      expect(fleet.servers, isEmpty);
    });

    testComponents('submitting an empty code leaves the fleet empty', (
      ComponentTester tester,
    ) async {
      final FleetManager fleet = FleetManager(
        storage: _MemStorage(),
        clientFactory: (ServerCredential _) => _SuccessClient(),
      );
      tester.pumpComponent(_wrap(AddServerScreen(fleetManager: fleet)));
      await tester.pump();

      tester.dispatchEvent(find.byType(TextInput), 'submit');
      await tester.pump();

      expect(fleet.servers, isEmpty);
    });
  });
}
