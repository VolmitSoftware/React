import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:reactor/localization/reactor_localizations.dart';
import 'package:reactor/model/identity_info.dart';
import 'package:reactor/model/server_credential.dart';
import 'package:reactor/model/server_snapshot.dart';
import 'package:reactor/screen/add_server.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/state/connection_manager.dart';
import 'package:reactor/state/fleet_manager.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class _SuccessClient implements IReactClient {
  final String serverName;
  _SuccessClient({this.serverName = 'TestServer'});

  @override
  Future<IdentityInfo> identity() async => IdentityInfo(
    serverName: serverName,
    version: '1.0.0',
    folia: false,
    serverId: '127.0.0.1:9696',
  );

  @override
  Future<ServerSnapshot> metrics() async => throw UnimplementedError();
}

class _FailClient implements IReactClient {
  @override
  Future<IdentityInfo> identity() async =>
      throw Exception('connection refused');

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

// ---------------------------------------------------------------------------
// Theme wrapper for server-side component tests
// ---------------------------------------------------------------------------

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

String _validCode({
  String host = 'localhost',
  int port = 7979,
  String tokenId = 'tid1',
  String tokenSig = 'tsig1',
  String confirmWord = 'alpha',
  String? relayUrl,
  String? serverPubKey,
  String? fingerprint,
}) => PairingCode.encode(
  host: host,
  port: port,
  tokenId: tokenId,
  tokenSig: tokenSig,
  confirmWord: confirmWord,
  relayUrl: relayUrl,
  serverPubKey: serverPubKey,
  fingerprint: fingerprint,
);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  // -------------------------------------------------------------------------
  group('PairingCode.decode', () {
    test('returns non-null for a valid RCT1 code', () {
      final String code = _validCode();
      expect(PairingCode.decode(code), isNotNull);
    });

    test('accepts a full console line containing the RCT1 code', () {
      final String code = _validCode(host: '127.0.0.1', port: 9696);
      final PairingCode? pc = PairingCode.decode(
        'Pairing code: $code\\nConfirm: 123456',
      );
      expect(pc, isNotNull);
      expect(pc!.host, equals('127.0.0.1'));
      expect(pc.port, equals(9696));
    });

    test('validation reports incomplete base64 payloads clearly', () {
      expect(PairingCode.validationMessage('RCT1.A'), contains('incomplete'));
    });

    test('decoded host matches original', () {
      final PairingCode? pc = PairingCode.decode(_validCode(host: '10.0.0.1'));
      expect(pc!.host, equals('10.0.0.1'));
    });

    test('decoded port matches original', () {
      final PairingCode? pc = PairingCode.decode(_validCode(port: 9000));
      expect(pc!.port, equals(9000));
    });

    test('decoded tokenId matches original', () {
      final PairingCode? pc = PairingCode.decode(_validCode(tokenId: 'myTid'));
      expect(pc!.tokenId, equals('myTid'));
    });

    test('decoded tokenSig matches original', () {
      final PairingCode? pc = PairingCode.decode(_validCode(tokenSig: 'mySig'));
      expect(pc!.tokenSig, equals('mySig'));
    });

    test('decoded confirmWord matches original', () {
      final PairingCode? pc = PairingCode.decode(
        _validCode(confirmWord: 'bravo'),
      );
      expect(pc!.confirmWord, equals('bravo'));
    });

    test('returns null for wrong prefix', () {
      expect(PairingCode.decode('RCT2.dGVzdA=='), isNull);
    });

    test('returns null for non-RCT1 garbage', () {
      expect(PairingCode.decode('GARBAGE'), isNull);
    });

    test('returns null for RCT1. prefix with invalid base64', () {
      expect(PairingCode.decode('RCT1.!!!'), isNull);
    });

    test('returns null for RCT1. prefix with valid base64 but not JSON', () {
      expect(PairingCode.decode('RCT1.bm90anNvbg=='), isNull);
    });

    test('returns null for RCT1. prefix with JSON missing required fields', () {
      // JSON {"host":"x"} is missing port/tokenId/tokenSig/confirmWord
      // base64url of {"host":"x"}
      final String partial = PairingCode.encodeRaw('{"host":"x"}');
      expect(PairingCode.decode('RCT1.$partial'), isNull);
    });
  });

  // -------------------------------------------------------------------------
  group('PairingCode.toCredential', () {
    test('produces credential with correct host and port', () {
      final PairingCode pc = PairingCode.decode(
        _validCode(host: '192.168.1.5', port: 7979),
      )!;
      final ServerCredential cred = pc.toCredential(
        id: 'srv-1',
        label: 'MyServer',
      );
      expect(cred.host, equals('192.168.1.5'));
      expect(cred.port, equals(7979));
    });

    test('produces credential bearer = tokenId + "." + tokenSig', () {
      final PairingCode pc = PairingCode.decode(
        _validCode(tokenId: 'a', tokenSig: 'b'),
      )!;
      final ServerCredential cred = pc.toCredential(id: 'x', label: 'L');
      expect(cred.bearer, equals('a.b'));
    });
  });

  // -------------------------------------------------------------------------
  group('PairingCode relay fields', () {
    test(
      'code with relay fields decodes with matching relayUrl/serverPubKey/fingerprint',
      () {
        final String code = _validCode(
          relayUrl: 'wss://relay.example.com:4443',
          serverPubKey: 'abc123pubkey',
          fingerprint: 'aa:bb:cc:dd',
        );
        final PairingCode? pc = PairingCode.decode(code);
        expect(pc, isNotNull);
        expect(pc!.relayUrl, equals('wss://relay.example.com:4443'));
        expect(pc.serverPubKey, equals('abc123pubkey'));
        expect(pc.fingerprint, equals('aa:bb:cc:dd'));
      },
    );

    test(
      'code without relay fields decodes with null relayUrl/serverPubKey/fingerprint',
      () {
        final PairingCode? pc = PairingCode.decode(_validCode());
        expect(pc, isNotNull);
        expect(pc!.relayUrl, isNull);
        expect(pc.serverPubKey, isNull);
        expect(pc.fingerprint, isNull);
      },
    );

    test('toCredential carries relay fields onto the credential', () {
      final String code = _validCode(
        relayUrl: 'wss://relay.example.com:4443',
        serverPubKey: 'abc123pubkey',
        fingerprint: 'aa:bb:cc:dd',
      );
      final PairingCode pc = PairingCode.decode(code)!;
      final ServerCredential cred = pc.toCredential(id: 'x', label: 'L');
      expect(cred.relayUrl, equals('wss://relay.example.com:4443'));
      expect(cred.serverPubKey, equals('abc123pubkey'));
      expect(cred.fingerprint, equals('aa:bb:cc:dd'));
    });

    test(
      'relay-only code decodes non-null and toCredential yields host==\'\' with relayUrl set',
      () {
        final String code = PairingCode.encode(
          host: '',
          port: 0,
          tokenId: 'tid',
          tokenSig: 'tsig',
          confirmWord: 'word',
          relayUrl: 'wss://relay.example.com',
          serverPubKey: 'mypubkey',
        );
        final PairingCode? pc = PairingCode.decode(code);
        expect(pc, isNotNull);
        final ServerCredential cred = pc!.toCredential(id: 'x', label: 'R');
        expect(cred.host, equals(''));
        expect(cred.relayUrl, equals('wss://relay.example.com'));
      },
    );
  });

  // -------------------------------------------------------------------------
  group('FleetManager', () {
    late _MemStorage storage;

    setUp(() {
      storage = _MemStorage();
    });

    test('starts with 0 servers', () {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _SuccessClient(),
      );
      expect(fm.servers, isEmpty);
    });

    test(
      'add() succeeds when identity() succeeds — server list grows to 1',
      () async {
        final FleetManager fm = FleetManager(
          storage: storage,
          clientFactory: (_) => _SuccessClient(serverName: 'Prod'),
        );
        final PairingCode pc = PairingCode.decode(_validCode())!;
        final ServerCredential cred = pc.toCredential(
          id: 'srv-1',
          label: 'Prod',
        );
        await fm.add(cred);
        expect(fm.servers.length, equals(1));
      },
    );

    test('add() stores credential host and port', () async {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _SuccessClient(),
      );
      final PairingCode pc = PairingCode.decode(
        _validCode(host: '10.1.2.3', port: 1234),
      )!;
      final ServerCredential cred = pc.toCredential(id: 'x', label: 'S');
      await fm.add(cred);
      expect(fm.servers.first.host, equals('10.1.2.3'));
      expect(fm.servers.first.port, equals(1234));
    });

    test('add() persists to storage', () async {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _SuccessClient(),
      );
      final PairingCode pc = PairingCode.decode(_validCode())!;
      await fm.add(pc.toCredential(id: 'srv-1', label: 'X'));
      expect(storage.read(FleetManager.storageKey), isNotNull);
    });

    test('add() when identity() fails — server list stays empty', () async {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _FailClient(),
      );
      final PairingCode pc = PairingCode.decode(_validCode())!;
      await expectLater(
        fm.add(pc.toCredential(id: 'srv-1', label: 'X')),
        throwsException,
      );
      expect(fm.servers, isEmpty);
    });

    test('add() two different servers reaches length 2', () async {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _SuccessClient(),
      );
      final PairingCode pc1 = PairingCode.decode(
        _validCode(host: 'host1', port: 7979),
      )!;
      final PairingCode pc2 = PairingCode.decode(
        _validCode(host: 'host2', port: 7980),
      )!;
      await fm.add(pc1.toCredential(id: 'a', label: 'A'));
      await fm.add(pc2.toCredential(id: 'b', label: 'B'));
      expect(fm.servers.length, equals(2));
    });

    test('FleetManager.load() restores servers from storage', () async {
      final _MemStorage sharedStorage = _MemStorage();

      final FleetManager fm1 = FleetManager(
        storage: sharedStorage,
        clientFactory: (_) => _SuccessClient(),
      );
      final PairingCode pc = PairingCode.decode(_validCode())!;
      await fm1.add(pc.toCredential(id: 'srv-1', label: 'X'));

      final FleetManager fm2 = FleetManager(
        storage: sharedStorage,
        clientFactory: (_) => _SuccessClient(),
      );
      await fm2.load();
      expect(fm2.servers.length, equals(1));
    });

    test(
      'add() creates a ConnectionManager retrievable via managerFor',
      () async {
        final FleetManager fm = FleetManager(
          storage: storage,
          clientFactory: (_) => _SuccessClient(),
        );
        final PairingCode pc = PairingCode.decode(_validCode())!;
        await fm.add(pc.toCredential(id: 'srv-1', label: 'X'));
        final ConnectionManager? manager = fm.managerFor('srv-1');
        expect(manager, isNotNull);
      },
    );

    test(
      'setActive() exposes that server\'s ConnectionManager via activeManager',
      () async {
        final FleetManager fm = FleetManager(
          storage: storage,
          clientFactory: (_) => _SuccessClient(),
        );
        final PairingCode pc = PairingCode.decode(_validCode())!;
        await fm.add(pc.toCredential(id: 'srv-1', label: 'X'));
        expect(fm.activeManager, isNull);
        fm.setActive('srv-1');
        expect(fm.activeManager, isNotNull);
        expect(fm.activeManager, same(fm.managerFor('srv-1')));
      },
    );

    test(
      'remove() drops the ConnectionManager and clears activeManager',
      () async {
        final FleetManager fm = FleetManager(
          storage: storage,
          clientFactory: (_) => _SuccessClient(),
        );
        final PairingCode pc = PairingCode.decode(_validCode())!;
        await fm.add(pc.toCredential(id: 'srv-1', label: 'X'));
        fm.setActive('srv-1');
        fm.remove('srv-1');
        expect(fm.managerFor('srv-1'), isNull);
        expect(fm.activeManager, isNull);
      },
    );

    test('clearAll() removes persisted servers and active manager', () async {
      final FleetManager fm = FleetManager(
        storage: storage,
        clientFactory: (_) => _SuccessClient(),
      );
      final PairingCode pc1 = PairingCode.decode(
        _validCode(host: 'host1', port: 7979),
      )!;
      final PairingCode pc2 = PairingCode.decode(
        _validCode(host: 'host2', port: 7980),
      )!;
      await fm.add(pc1.toCredential(id: 'a', label: 'A'));
      await fm.add(pc2.toCredential(id: 'b', label: 'B'));
      fm.setActive('a');

      fm.clearAll();

      expect(fm.servers, isEmpty);
      expect(fm.activeManager, isNull);
      expect(fm.managerFor('a'), isNull);
      expect(storage.read(FleetManager.storageKey), isNull);
    });
  });

  group('AddServerScreen widget', () {
    testServer('renders paste input with RCT1 placeholder text', (
      ServerTester tester,
    ) async {
      final FleetManager fm = FleetManager(
        storage: _MemStorage(),
        clientFactory: (_) => _SuccessClient(),
      );
      tester.pumpComponent(_wrap(AddServerScreen(fleetManager: fm)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('RCT1'),
        isTrue,
        reason: 'paste input must show RCT1 in its placeholder',
      );
    });

    testServer('renders Pair button text', (ServerTester tester) async {
      final FleetManager fm = FleetManager(
        storage: _MemStorage(),
        clientFactory: (_) => _SuccessClient(),
      );
      tester.pumpComponent(_wrap(AddServerScreen(fleetManager: fm)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains(ReactorText.addServerPair.english),
        isTrue,
        reason: 'Pair button must be present in the rendered output',
      );
      expect(
        res.body.contains(ReactorText.addServerClearCode.english),
        isTrue,
        reason: 'Clear code control must be present near the pair action',
      );
      expect(
        res.body.contains(ReactorText.addServerResetFleet.english),
        isTrue,
        reason: 'Reset fleet control must be present for stuck pairings',
      );
    });
  });

  group('AddServerScreen pairing flow', () {
    test(
      'pairServer: valid code + successful identity() → FleetManager has 1 server',
      () async {
        final FleetManager fm = FleetManager(
          storage: _MemStorage(),
          clientFactory: (_) => _SuccessClient(serverName: 'ProdServer'),
        );
        await AddServerScreen.pairServer(_validCode(), fm);
        expect(
          fm.servers.length,
          equals(1),
          reason: 'successful pairServer must add server to FleetManager',
        );
      },
    );

    test(
      'pairServer: invalid code → throws ArgumentError, FleetManager stays empty',
      () async {
        final FleetManager fm = FleetManager(
          storage: _MemStorage(),
          clientFactory: (_) => _SuccessClient(),
        );
        await expectLater(
          AddServerScreen.pairServer('NOT_A_VALID_CODE', fm),
          throwsA(isA<ArgumentError>()),
        );
        expect(
          fm.servers,
          isEmpty,
          reason: 'invalid code must leave FleetManager empty',
        );
      },
    );

    test(
      'pairServer: valid code + failed identity() → throws, FleetManager stays empty',
      () async {
        final FleetManager fm = FleetManager(
          storage: _MemStorage(),
          clientFactory: (_) => _FailClient(),
        );
        await expectLater(
          AddServerScreen.pairServer(_validCode(), fm),
          throwsException,
        );
        expect(
          fm.servers,
          isEmpty,
          reason: 'failed identity() probe must leave FleetManager empty',
        );
      },
    );

    test('pairServer: relay-only RCT1 code → credential has non-empty label '
        'and relayUrl/serverPubKey/fingerprint are set', () async {
      final FleetManager fm = FleetManager(
        storage: _MemStorage(),
        clientFactory: (_) => _SuccessClient(),
      );
      final String code = PairingCode.encode(
        host: '',
        port: 0,
        tokenId: 'tid',
        tokenSig: 'tsig',
        confirmWord: 'word',
        relayUrl: 'wss://relay.example.com',
        serverPubKey: 'mypubkey',
        fingerprint: 'aa:bb:cc',
      );
      final ServerCredential cred = await AddServerScreen.pairServer(code, fm);
      expect(
        cred.relayUrl,
        equals('wss://relay.example.com'),
        reason: 'credential must carry relayUrl from the pairing code',
      );
      expect(
        cred.serverPubKey,
        equals('mypubkey'),
        reason: 'credential must carry serverPubKey from the pairing code',
      );
      expect(
        cred.fingerprint,
        equals('aa:bb:cc'),
        reason: 'credential must carry fingerprint from the pairing code',
      );
      expect(
        cred.label,
        isNotEmpty,
        reason: 'relay-only credential must have a non-empty label',
      );
    });
  });

  group('AddServerScreen._onPair handler', () {
    testComponents('submitting an empty code leaves the fleet empty', (
      ComponentTester tester,
    ) async {
      final FleetManager fm = FleetManager(
        storage: _MemStorage(),
        clientFactory: (_) => _SuccessClient(),
      );
      tester.pumpComponent(_wrap(AddServerScreen(fleetManager: fm)));
      await tester.pump();

      tester.dispatchEvent(find.byType(TextInput), 'submit');
      await tester.pump();

      expect(
        fm.servers,
        isEmpty,
        reason:
            'the Pair button handler must reject an empty/invalid code and '
            'add no server',
      );
    });
  });
}
