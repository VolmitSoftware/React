import 'dart:convert';

import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/pairing_link.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/screen/pages/start_page.dart';
import 'package:react_web/screen/pairing/continue_checks.dart';
import 'package:react_web/screen/pairing/pair_words.dart';
import 'package:react_web/screen/pairing/pairing_ended.dart';
import 'package:react_web/screen/pairing/pairing_card.dart';
import 'package:react_web/service/pairing_redeemer.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/pair_server.dart';
import 'package:react_web/ui/kit/mode.dart';

import '../support/test_server_key.dart';
import 'fleet_test_support.dart';

const String _origin = 'http://127.0.0.1:8823';

/// The opening tag of the first [tag] element carrying [attribute].
String _openingTag(String html, String tag, String attribute) =>
    RegExp('<$tag\\b[^>]*$attribute[^>]*>').firstMatch(html)?.group(0) ?? '';

PairedToken _token({String? role = 'operator'}) => PairedToken(
  bearer: 'tok_AbCdEfGhIjKlMnOp.${'B' * 43}',
  tokenId: 'tok_AbCdEfGhIjKlMnOp',
  role: role,
  scopes: const <String>['read', 'op:execute'],
  expiresAt: DateTime.utc(2026, 11, 20),
  lifetime: const Duration(days: 7),
  serverName: 'Lobby',
  path: PairPath.direct,
);

void main() {
  late TestServerKey key;

  setUpAll(() async => key = await TestServerKey.create());

  group('pair words', () {
    test('a spent link says so and asks for a new one', () {
      for (final (PairingFailure failure, ReactorText title)
          in <(PairingFailure, ReactorText)>[
            (PairingFailure.used, ReactorText.pairProblemUsed),
            (PairingFailure.expired, ReactorText.pairProblemExpired),
            (PairingFailure.revoked, ReactorText.pairProblemRevoked),
            (PairingFailure.unknown, ReactorText.pairProblemUnknown),
            (PairingFailure.wrongServer, ReactorText.pairProblemWrongServer),
          ]) {
        final PairProblem problem = pairProblemFor(
          PairingRefused(failure),
          pageOrigin: _origin,
        );
        expect(problem.title, title.english, reason: failure.name);
        expect(problem.next, ReactorText.pairNextNewLink.english);
        expect(problem.newLink, isTrue);
      }
    });

    test('a refused origin names the origin and the config key', () {
      final PairProblem told = pairProblemFor(
        const PairingRefused(
          PairingFailure.originNotAllowed,
          origin: 'https://react.example.com',
          configKey: 'allowedOrigins',
          configFile: 'plugins/React/web.toml',
        ),
        pageOrigin: _origin,
      );
      expect(
        told.next,
        'Add https://react.example.com to allowedOrigins in plugins/React/web.toml, then press Pair again.',
      );
      expect(told.newLink, isFalse);
      expect(
        pairProblemFor(
          const PairingRefused(PairingFailure.originNotAllowed),
          pageOrigin: _origin,
        ).next,
        contains(_origin),
      );
    });

    test(
      'silence, a blocked local address and a busy server keep the link',
      () {
        for (final (PairingFailure failure, ReactorText title, ReactorText next)
            in <(PairingFailure, ReactorText, ReactorText)>[
              (
                PairingFailure.unreachable,
                ReactorText.pairProblemUnreachable,
                ReactorText.pairNextUnreachable,
              ),
              (
                PairingFailure.localNetworkBlocked,
                ReactorText.pairProblemBlocked,
                ReactorText.pairNextBlocked,
              ),
              (
                PairingFailure.busy,
                ReactorText.pairProblemBusy,
                ReactorText.pairNextBusy,
              ),
              (
                PairingFailure.other,
                ReactorText.pairProblemOther,
                ReactorText.pairNextOther,
              ),
            ]) {
          final PairProblem problem = pairProblemFor(
            PairingRefused(failure),
            pageOrigin: _origin,
          );
          expect(problem.title, title.english);
          expect(problem.next, next.english);
          expect(problem.newLink, isFalse);
        }
      },
    );

    test('Pairing ended asks for a new link', () {
      expect(pairingEndedProblem().title, 'Pairing ended.');
      expect(pairingEndedProblem().newLink, isTrue);
    });

    test('what a pairing allows follows its scopes, in the server order', () {
      expect(scopeWords(const <String>['read']), <String>[
        ReactorText.pairScopeRead.english,
      ]);
      expect(
        scopeWords(const <String>[
          'console:execute',
          'admin',
          'read',
          'op:execute',
          'console:read',
        ]),
        <String>[
          ReactorText.pairScopeRead.english,
          ReactorText.pairScopeOperate.english,
          ReactorText.pairScopeAdmin.english,
          ReactorText.pairScopeConsoleRead.english,
          ReactorText.pairScopeConsoleRun.english,
        ],
      );
    });

    test('the end of a pairing shows within three days of it', () {
      final DateTime now = DateTime.utc(2026, 11, 13, 12);
      expect(pairingEndsSoon(null, now), isNull);
      expect(pairingEndsSoon(now.add(const Duration(days: 4)), now), isNull);
      expect(
        pairingEndsSoon(now.add(const Duration(days: 2, hours: 3)), now),
        'Pairing ends in 2 days unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(days: 1, hours: 1)), now),
        'Pairing ends in 1 day unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(hours: 5)), now),
        'Pairing ends in 5 h unless used.',
      );
      expect(
        pairingEndsSoon(now.subtract(const Duration(hours: 1)), now),
        isNull,
      );
    });

    test('days left round to the nearest day, hours under a day', () {
      final DateTime now = DateTime.utc(2026, 11, 13, 12);
      // A 2-day pairing made a moment ago.
      expect(
        pairingEndsSoon(
          now.add(const Duration(days: 2) - const Duration(seconds: 3)),
          now,
        ),
        'Pairing ends in 2 days unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(days: 1, hours: 13)), now),
        'Pairing ends in 2 days unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(days: 2, hours: 13)), now),
        'Pairing ends in 3 days unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(hours: 23, minutes: 40)), now),
        'Pairing ends in 24 h unless used.',
      );
      expect(
        pairingEndsSoon(now.add(const Duration(minutes: 20)), now),
        'Pairing ends in 1 h unless used.',
      );
    });
  });

  group('Continue rows', () {
    test('the live connection says Online at once; an ended pairing wins', () {
      expect(
        reachOf(ended: false, live: ConnState.live, answered: null),
        ServerReach.online,
      );
      expect(
        reachOf(ended: false, live: ConnState.connecting, answered: null),
        ServerReach.checking,
      );
      expect(
        reachOf(
          ended: false,
          live: ConnState.offline,
          answered: ServerReach.unreachable,
        ),
        ServerReach.unreachable,
      );
      expect(
        reachOf(
          ended: true,
          live: ConnState.live,
          answered: ServerReach.online,
        ),
        ServerReach.ended,
      );
    });
  });

  group('credentialFor', () {
    test(
      'keeps both paths, the base path, the key and the remember choice',
      () {
        final ServerCredential cred = credentialFor(
          key.link(
            directUrl: 'http://127.0.0.1:8824/s1',
            relayUrl: 'ws://127.0.0.1:9821',
          ),
          _token(),
          remember: false,
          id: 'id-1',
        );
        expect(cred.label, 'Lobby');
        expect(cred.host, '127.0.0.1');
        expect(cred.port, 8824);
        expect(cred.basePath, '/s1');
        expect(cred.relayUrl, 'ws://127.0.0.1:9821');
        expect(cred.transport, ServerTransport.dual);
        expect(cred.serverKey, key.serverKey);
        expect(cred.fingerprint, key.fingerprint);
        expect(cred.tokenId, 'tok_AbCdEfGhIjKlMnOp');
        expect(cred.role, 'operator');
        expect(cred.remember, isFalse);
        expect(cred.linked, isTrue);
        expect(
          ServerCredential.fromJson(
            jsonDecode(jsonEncode(cred.toJson())) as Map<String, dynamic>,
          ).linked,
          isTrue,
        );
      },
    );

    test('a relay-only link and an https address on its default port', () {
      final ServerCredential relayOnly = credentialFor(
        key.link(directUrl: null, relayUrl: 'wss://relay.example.com'),
        _token(),
        remember: true,
        id: 'r',
      );
      expect(relayOnly.transport, ServerTransport.relay);
      final ServerCredential https = credentialFor(
        key.link(directUrl: 'https://play.example.com/react'),
        _token(role: null),
        remember: true,
        id: 'h',
      );
      expect(https.port, 443);
      expect(https.secure, isTrue);
      expect(https.role, isNull);
    });
  });

  group('PairingCard', () {
    testServer(
      'one card: name, until when, what it allows, browser name, remember, Pair',
      (ServerTester tester) async {
        final PairingLink link = key.link(
          role: 'viewer',
          scopes: const <String>['read'],
        );
        tester.pumpComponent(
          fleetHarness(
            PairingCard(link: link, onCancel: () {}),
            fleet: TestFleet(),
          ),
        );
        final String body = (await tester.request('/')).body;
        expect(body, contains('Lobby'));
        expect(body, contains(RegExp(r'Link works until \d\d:\d\d, once\.')));
        expect(body, contains(ReactorText.pairScopeRead.english));
        expect(body, isNot(contains(ReactorText.pairScopeConsoleRun.english)));
        expect(body, contains(ReactorText.pairBrowserName.english));
        expect(body, contains(ReactorText.pairRemember.english));
        expect(body, contains('data-testid="pair-submit"'));
        expect(
          body,
          isNot(contains(ReactorText.pairFingerprint.english)),
          reason: 'Details are Advanced only',
        );
      },
    );

    testServer('browser name and remember are library fields', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          PairingCard(link: key.link(), onCancel: () {}),
          fleet: TestFleet(),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(_openingTag(body, 'label', 'for="pair-browser-name"'), isNotEmpty);
      expect(_openingTag(body, 'input', 'id="pair-browser-name"'), isNotEmpty);
      expect(_openingTag(body, 'label', 'for="pair-remember"'), isNotEmpty);
      expect(
        _openingTag(body, 'button', 'role="switch"'),
        contains('id="pair-remember"'),
      );
      expect(body, contains('data-orientation="horizontal"'));
      expect(body, contains('class="arcane-field-title"'), reason: 'Can');
      expect(body, isNot(contains('ui-field')));
    });

    testServer('Details show the fingerprint and both addresses in Advanced', (
      ServerTester tester,
    ) async {
      final PairingLink link = key.link(relayUrl: 'ws://127.0.0.1:9821');
      tester.pumpComponent(
        fleetHarness(
          PairingCard(link: link, onCancel: () {}),
          fleet: TestFleet(),
          mode: ReactorMode.advanced,
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains(key.fingerprint));
      expect(body, contains('http://127.0.0.1:9822'));
      expect(body, contains('ws://127.0.0.1:9821'));
    });

    testServer('a link past its ten minutes says so and offers New link', (
      ServerTester tester,
    ) async {
      final PairingLink link = key.link(
        expiresAt: DateTime.now().toUtc().subtract(const Duration(minutes: 1)),
      );
      tester.pumpComponent(
        fleetHarness(
          PairingCard(link: link, onCancel: () {}),
          fleet: TestFleet(),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, isNot(contains('Link works until')));
      expect(body, contains(ReactorText.pairProblemExpired.english));
      expect(body, contains(ReactorText.pairNewLink.english));
      expect(body, isNot(contains('data-testid="pair-submit"')));
    });

    testServer('no link: not a pairing link, and the New link field', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          PairingCard(link: null, onCancel: () {}),
          fleet: TestFleet(),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains(ReactorText.pairProblemNotALink.english));
      expect(body, contains(ReactorText.pairNewLink.english));
    });
  });

  group('PairingEndedNotice', () {
    testServer('says since when there is no data, then the New link field', (
      ServerTester tester,
    ) async {
      final DateTime at = DateTime(2026, 11, 13, 7, 58);
      tester.pumpComponent(
        fleetHarness(
          PairingEndedNotice(serverId: 'alpha', since: at),
          fleet: TestFleet(),
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('No data since 07:58.'));
      expect(body, contains('id="new-link-alpha"'));
    });
  });

  group('StartPage', () {
    testServer(
      'with nothing paired: the Pairing link field and the command, no Continue',
      (ServerTester tester) async {
        tester.pumpComponent(
          fleetHarness(const StartPage(), fleet: TestFleet()),
        );
        final String body = (await tester.request('/')).body;
        expect(body, contains(ReactorText.pairLink.english));
        expect(body, contains(pairCommand));
        expect(body, isNot(contains(ReactorText.startContinue.english)));
        expect(_openingTag(body, 'label', 'for="pair-link"'), isNotEmpty);
        expect(_openingTag(body, 'input', 'id="pair-link"'), isNotEmpty);
        expect(_openingTag(body, 'label', 'for="pair-command"'), isNotEmpty);
        expect(_openingTag(body, 'input', 'id="pair-command"'), isNotEmpty);
      },
    );

    testServer(
      'with servers: Continue first, each server once, an ended one with New link',
      (ServerTester tester) async {
        final TestFleet fleet = TestFleet(
          servers: <ServerCredential>[testCredential('alpha', 'Alpha')],
        );
        fleet.storage.write(
          FleetManager.storageKey,
          jsonEncode(<Map<String, dynamic>>[
            testCredential('alpha', 'Alpha').toJson(),
            <String, dynamic>{
              'id': 'old',
              'label': 'Old paper',
              'host': '127.0.0.1',
              'port': 9820,
              'bearer': 'x.y',
              'fingerprint': 'fp-old',
            },
          ]),
        );
        final TestFleet loaded = TestFleet();
        loaded.storage.write(
          FleetManager.storageKey,
          fleet.storage.read(FleetManager.storageKey)!,
        );
        await loaded.fleetManager.load();
        tester.pumpComponent(fleetHarness(const StartPage(), fleet: loaded));
        final String body = (await tester.request('/')).body;
        expect(
          body.indexOf(ReactorText.startContinue.english),
          lessThan(body.indexOf(ReactorText.pairLink.english)),
        );
        expect(body, contains('Alpha'));
        expect(body, contains('Old paper'));
        expect(body, contains('data-reach="ended"'));
        expect(body, contains('id="new-link-old"'));
        expect(body, contains('href="/server/alpha"'));
        // A long name ("Paper » react-ux-paper-26-3 (Plugin)") stays on one
        // line and ends in an ellipsis, the full name on hover.
        expect(
          RegExp(
            r'<a[^>]*href="/server/alpha"[^>]*>\s*<span[^>]*'
            r'class="reactor-one-line"[^>]*title="Alpha"',
          ).hasMatch(body),
          isTrue,
        );
        expect(
          RegExp(r'<span[^>]*class="reactor-one-line"[^>]*title="Old paper"')
              .hasMatch(body),
          isTrue,
        );
      },
    );
  });
}
