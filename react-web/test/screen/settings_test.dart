library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/localization/reactor_locale.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/alert_thresholds.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/screen/pages/settings_page.dart';
import 'package:react_web/screen/settings.dart';
import 'package:react_web/screen/settings_alerts.dart';
import 'package:react_web/screen/settings_servers.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'fleet_test_support.dart';

/// The opening tag of the first [tag] element carrying [attribute].
String _openingTag(String html, String tag, String attribute) =>
    RegExp('<$tag\\b[^>]*$attribute[^>]*>').firstMatch(html)?.group(0) ?? '';

TestFleet _fleet({StubClient Function(ServerCredential cred)? client}) =>
    TestFleet(
      servers: <ServerCredential>[
        testCredential('alpha', 'Alpha'),
        testCredential(
          'beta',
          'Beta',
          host: '',
          port: 0,
          relayUrl: 'wss://relay.example.com',
        ),
      ],
      client: client,
    );

void main() {
  group('SettingsPage', () {
    testServer('has General, Servers and Alerts tabs in Simple', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(fleetHarness(const SettingsPage(), fleet: _fleet()));
      final String body = (await tester.request('/')).body;

      expect(body, contains('data-tabs="general,servers,alerts"'));
      expect(body, contains(ReactorText.pageSettingsGeneral.english));
    });
  });

  group('SettingsGeneralTab', () {
    testServer('offers theme, language and mode on library controls', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const SettingsGeneralTab(), fleet: _fleet()),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains(ReactorText.settingsTheme.english));
      expect(body, contains(ReactorText.settingsDarkTheme.english));
      expect(body, contains(ReactorText.settingsLightTheme.english));
      expect(body, contains(ReactorText.settingsMode.english));
      expect(body, contains(ReactorText.modeAdvanced.english));
      expect(body, isNot(contains('reactor-theme-option')));
      expect(body, contains('role="group"'), reason: 'toggle groups');
    });

    testServer('lays out theme, language and mode as library fields', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          ReactorLocaleScope(
            locale: 'en_US',
            loading: false,
            onChanged: (String _) async {},
            child: const SettingsGeneralTab(),
          ),
          fleet: _fleet(),
        ),
      );
      final String body = (await tester.request('/')).body;

      expect('data-slot="field"'.allMatches(body), hasLength(3));
      expect(_openingTag(body, 'label', 'for="settings-language"'), isNotEmpty);
      expect(
        'class="arcane-field-title"'.allMatches(body),
        hasLength(2),
        reason: 'theme and mode title their toggle groups',
      );
      expect(body, isNot(contains('ui-field')));
    });

    testComponents('switches the theme and the mode', (
      ComponentTester tester,
    ) async {
      final List<Brightness> themes = <Brightness>[];
      final List<ReactorMode> modes = <ReactorMode>[];
      tester.pumpComponent(
        fleetHarness(
          const SettingsGeneralTab(),
          fleet: _fleet(),
          onTheme: themes.add,
          onMode: modes.add,
        ),
      );
      await tester.pump();

      await tester.click(buttonLabeled(ReactorText.settingsLightTheme.english));
      await tester.click(buttonLabeled(ReactorText.modeAdvanced.english));

      expect(themes, <Brightness>[Brightness.light]);
      expect(modes, <ReactorMode>[ReactorMode.advanced]);
    });
  });

  group('SettingsServersTab', () {
    test('parses tags typed with commas', () {
      expect(parseTags(' eu, survival,,eu , '), <String>['eu', 'survival']);
      expect(parseTags(''), isEmpty);
    });

    testServer('lists every saved server with its address and tags', (
      ServerTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      fleet.tagsStore.setTags('alpha', <String>['prod']);
      tester.pumpComponent(
        fleetHarness(const SettingsServersTab(), fleet: fleet),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('>Alpha<'));
      expect(body, contains('>Beta<'));
      expect(body, contains('http://localhost:7979'));
      expect(body, contains('wss://relay.example.com'));
      expect(body, contains('>prod<'));
      expect(body, contains(ReactorText.settingsNoTags.english));
      expect(body, contains('href="/server/alpha"'));
      expect(body, contains(ReactorText.pairTitle.english));
    });

    testServer('keeps import, export and Remove all for Advanced', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const SettingsServersTab(), fleet: _fleet()),
      );
      final String simple = (await tester.request('/')).body;

      expect(
        simple,
        isNot(contains(ReactorText.settingsImportConnections.english)),
      );
      expect(simple, isNot(contains(ReactorText.settingsClearAll.english)));
    });

    testServer('Advanced shows import, export and Remove all', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const SettingsServersTab(),
          fleet: _fleet(),
          mode: ReactorMode.advanced,
        ),
      );
      final String advanced = (await tester.request('/')).body;

      expect(advanced, contains(ReactorText.settingsImportConnections.english));
      expect(advanced, contains(ReactorText.settingsExportConnections.english));
      expect(advanced, contains(ReactorText.settingsClearAll.english));
    });

    testComponents('Remove in the row menu asks first; Cancel keeps it', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      final String removeAlpha = reactorText(
        ReactorText.settingsRemoveServer,
        <String, Object?>{'server': 'Alpha'},
      );
      tester.pumpComponent(
        fleetHarness(const SettingsServersTab(), fleet: fleet, confirms: true),
      );
      await tester.pump();

      final Finder remove = menuEntry(ReactorText.settingsRemove.english);
      await tester.click(remove.first);
      await tester.pump();
      expect(find.text(removeAlpha), findsOneComponent);
      await tester.click(questionButton(ReactorText.commonCancel.english));
      await tester.pump();
      expect(fleet.removed, isEmpty);

      await tester.click(remove.first);
      await tester.pump();
      await tester.click(confirmButton());
      await tester.pump();
      expect(fleet.removed, <String>['alpha']);
      expect(fleet.fleetManager.servers, hasLength(1));
    });

    testComponents('Edit opens the name and tags and Save keeps them', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      fleet.tagsStore.setTags('alpha', <String>['eu', 'prod']);
      tester.pumpComponent(
        fleetHarness(const SettingsServersTab(), fleet: fleet),
      );
      await tester.pump();

      await tester.click(
        buttonNamed(
          reactorText(ReactorText.settingsEditServer, <String, Object?>{
            'server': 'Alpha',
          }),
        ),
      );
      expect(
        find.text(
          reactorText(ReactorText.settingsEditServer, <String, Object?>{
            'server': 'Alpha',
          }),
        ),
        findsOneComponent,
      );
      await tester.click(buttonLabeled(ReactorText.settingsSave.english));

      expect(fleet.fleetManager.servers.first.label, 'Alpha');
      expect(fleet.tagsStore.tagsFor('alpha'), <String>['eu', 'prod']);
    });

    testComponents('the edit sheet holds a library field per setting', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const SettingsServersTab(), fleet: _fleet()),
      );
      await tester.pump();
      await tester.click(
        buttonNamed(
          reactorText(ReactorText.settingsEditServer, <String, Object?>{
            'server': 'Alpha',
          }),
        ),
      );

      expect(find.byType(ArcaneFormField), findsNComponents(2));
      expect(find.byType(ArcaneFieldLabel), findsNComponents(2));
    });
  });

  group('SettingsAlertsTab', () {
    test('reads typed thresholds', () {
      expect(parseThreshold('18'), 18);
      expect(parseThreshold(' 2.5 '), 2.5);
      expect(parseThreshold('-1'), isNull);
      expect(parseThreshold('fast'), isNull);
      expect(formatThreshold(18), '18');
      expect(formatThreshold(2.5), '2.5');
    });

    test('sets each threshold in its own place', () {
      AlertThresholds t = AlertThresholds.defaults;
      for (final (int index, Threshold threshold) in Threshold.values.indexed) {
        t = threshold.set(t, 100.0 + index);
      }
      for (final (int index, Threshold threshold) in Threshold.values.indexed) {
        expect(threshold.of(t), 100.0 + index, reason: threshold.name);
      }
    });

    testServer('shows the saved thresholds with Save and Reset', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const SettingsAlertsTab(), fleet: _fleet()),
      );
      final String body = (await tester.request('/')).body;

      expect(body, contains('id="threshold-tpsWarn"'));
      expect(body, contains('value="18"'));
      expect(body, contains('value="10"'));
      expect(body, contains(ReactorText.settingsMsptWarn.english));
      expect(body, contains(ReactorText.configEditorSave.english));
      expect(body, contains(ReactorText.settingsResetDefaults.english));
      expect(body, contains('data-action-row="footer"'));
    });

    testServer('each threshold is a library field whose label names its box', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(const SettingsAlertsTab(), fleet: _fleet()),
      );
      final String body = (await tester.request('/')).body;

      expect(_openingTag(body, 'label', 'for="threshold-tpsWarn"'), isNotEmpty);
      // Simple: TPS and tick time; the rest keep their values.
      expect(
        'data-slot="field"'.allMatches(body),
        hasLength(Threshold.values.where((Threshold t) => t.simple).length),
      );
      expect(body, isNot(contains('id="threshold-gcPercentWarn"')));
    });

    testServer('Advanced shows every threshold', (ServerTester tester) async {
      tester.pumpComponent(
        fleetHarness(
          const SettingsAlertsTab(),
          fleet: _fleet(),
          mode: ReactorMode.advanced,
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(
        'data-slot="field"'.allMatches(body),
        hasLength(Threshold.values.length),
      );
    });

    testComponents('Reset asks first, then restores the defaults', (
      ComponentTester tester,
    ) async {
      final TestFleet fleet = _fleet();
      fleet.alertStore.thresholds = AlertThresholds.defaults.copyWith(
        tpsWarn: 15,
      );
      tester.pumpComponent(
        fleetHarness(const SettingsAlertsTab(), fleet: fleet, confirms: true),
      );
      await tester.pump();

      await tester.click(
        buttonLabeled(ReactorText.settingsResetDefaults.english),
      );
      await tester.pump();
      expect(fleet.alertStore.thresholds.tpsWarn, 15);
      await tester.click(confirmButton());
      await tester.pump();
      expect(
        fleet.alertStore.thresholds.tpsWarn,
        AlertThresholds.defaults.tpsWarn,
      );
    });
  });

  group('SettingsServersTab roles', () {
    testComponents('Advanced adds each role, Revoked on a 401, No access on '
        'a 403, with what the role may do in plain words', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const SettingsServersTab(),
          mode: ReactorMode.advanced,
          fleet: TestFleet(
            servers: <ServerCredential>[
              testCredential('a', 'Alpha'),
              testCredential('b', 'Beta'),
              testCredential('c', 'Gamma'),
            ],
            client: (ServerCredential cred) => switch (cred.id) {
              'a' => StubClient(role: viewerRole),
              'b' => StubClient(roleError: const ReactAuthException()),
              _ => StubClient(roleError: const ReactForbidden()),
            },
          ),
        ),
      );
      await tester.pump();

      expect(find.text(ReactorText.roleViewer.english), findsOneComponent);
      expect(
        find.text('${ReactorText.pairScopeRead.english}.'),
        findsOneComponent,
      );
      expect(find.text('read'), findsNothing);
      expect(find.text(ReactorText.pairEndedState.english), findsOneComponent);
      expect(
        find.text(ReactorText.settingsNoAccess.english),
        findsOneComponent,
      );
    });

    testServer('Simple shows no role column and Settings has no Access tab', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        fleetHarness(
          const SettingsPage(),
          fleet: _fleet(),
          mode: ReactorMode.advanced,
        ),
      );
      final String body = (await tester.request('/')).body;
      expect(body, contains('data-tabs="general,servers,alerts"'));
    });
  });
}
