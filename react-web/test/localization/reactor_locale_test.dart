library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, DomComponent, EventCallback;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_locale.dart';
import 'package:react_web/localization/reactor_asset_uri.dart';
import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/state/fleet_manager.dart';
import 'package:react_web/state/memory_fleet_storage.dart';

class _UnavailableStorage implements FleetStorage {
  @override
  String? read(String key) => throw StateError('Storage unavailable');

  @override
  void remove(String key) => throw StateError('Storage unavailable');

  @override
  void write(String key, String value) =>
      throw StateError('Storage unavailable');
}

String _completeCatalog({required String appTitle}) =>
    jsonEncode(<String, String>{
      for (final ReactorText key in ReactorText.values)
        key.id: key == ReactorText.appTitle ? appTitle : key.english,
    });

Finder _domId(String id) => find.byComponentPredicate(
  (Component component) => component is DomComponent && component.id == id,
  description: 'DOM element with id $id',
);

class _LocaleSwitchHarness extends StatefulWidget {
  const _LocaleSwitchHarness();

  @override
  State<_LocaleSwitchHarness> createState() => _LocaleSwitchHarnessState();
}

class _LocaleSwitchHarnessState extends State<_LocaleSwitchHarness> {
  String _locale = reactorEnglishLocale;

  void _switchToGerman() {
    final ReactorOverlayResult result = reactorLocalizations.installOverlayJson(
      '{"shell.fleet_control_plane":"Servergruppen-Leitstand"}',
    );
    if (!result.applied) {
      throw StateError(result.error ?? 'Could not install test catalog.');
    }
    setState(() => _locale = 'de_DE');
  }

  @override
  Widget build(BuildContext context) {
    return ReactorLocaleScope(
      locale: _locale,
      loading: false,
      onChanged: (String _) async {},
      child: dom.div(<Widget>[
        dom.button(
          id: 'switch-locale',
          events: <String, EventCallback>{'click': (_) => _switchToGerman()},
          const <Widget>[],
        ),
        const _StatefulLocalizedProbe(),
      ]),
    );
  }
}

class _StatefulLocalizedProbe extends StatefulWidget {
  const _StatefulLocalizedProbe();

  @override
  State<_StatefulLocalizedProbe> createState() =>
      _StatefulLocalizedProbeState();
}

class _StatefulLocalizedProbeState extends State<_StatefulLocalizedProbe> {
  int _count = 0;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(<Widget>[
      dom.button(
        id: 'increment-probe',
        events: <String, EventCallback>{
          'click': (_) => setState(() => _count++),
        },
        const <Widget>[],
      ),
      dom.span(<Widget>[
        Component.text(reactorText(ReactorText.shellFleetControlPlane)),
      ]),
      dom.span(<Widget>[Component.text('probe-count:$_count')]),
    ]);
  }
}

void main() {
  group('React Web locale selection', () {
    test('language menu navigation wraps and supports boundary keys', () {
      expect(
        reactorLanguageMenuTargetIndex(current: 1, count: 4, key: 'ArrowDown'),
        2,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: 3, count: 4, key: 'ArrowDown'),
        0,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: 0, count: 4, key: 'ArrowUp'),
        3,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: 2, count: 4, key: 'Home'),
        0,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: 2, count: 4, key: 'End'),
        3,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: -1, count: 4, key: 'ArrowUp'),
        3,
      );
      expect(
        reactorLanguageMenuTargetIndex(current: 0, count: 0, key: 'ArrowDown'),
        -1,
      );
    });

    test(
      'matches exact, browser-style, language-only, and Chinese locales',
      () {
        expect(matchReactorLocale('ja_JP'), 'ja-JP');
        expect(matchReactorLocale('fr-CA'), 'fr_FR');
        expect(matchReactorLocale('zh-Hant-HK'), 'zh_TW');
        expect(matchReactorLocale('zh-SG'), 'zh_CN');
        expect(matchReactorLocale('en-GB'), 'en_US');
        expect(matchReactorLocale('xx-ZZ'), isNull);
      },
    );

    test('uses persisted, browser, configured, then English precedence', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      storage.write(reactorLocaleStorageKey, 'pl_PL');
      expect(
        resolveInitialReactorLocale(
          storage: storage,
          browserLocales: const <String>['fr-FR'],
          configuredLocale: 'de_DE',
        ),
        'pl_PL',
      );
      storage.write(reactorLocaleStorageKey, 'invalid');
      expect(
        resolveInitialReactorLocale(
          storage: storage,
          browserLocales: const <String>['xx-ZZ', 'fr-CA'],
          configuredLocale: 'de_DE',
        ),
        'fr_FR',
      );
      expect(
        resolveInitialReactorLocale(
          storage: _UnavailableStorage(),
          configuredLocale: 'tr_TR',
        ),
        'tr_TR',
      );
      expect(resolveInitialReactorLocale(configuredLocale: 'invalid'), 'en_US');
    });

    test('persists only a successfully installed locale', () async {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorLocaleManager manager = ReactorLocaleManager(
        initialLocale: 'en_US',
        storage: storage,
        localizations: localizations,
        loader: (String locale) async => locale == 'de_DE'
            ? _completeCatalog(appTitle: 'Reaktor-Web')
            : '{broken',
      );

      final ReactorLocaleSwitchResult german = await manager.switchTo('de_DE');
      expect(german.applied, isTrue);
      expect(manager.locale, 'de_DE');
      expect(storage.read(reactorLocaleStorageKey), 'de_DE');
      expect(localizations.text(ReactorText.appTitle), 'Reaktor-Web');

      final ReactorLocaleSwitchResult failed = await manager.switchTo('fr_FR');
      expect(failed.applied, isFalse);
      expect(manager.locale, 'de_DE');
      expect(storage.read(reactorLocaleStorageKey), 'de_DE');
      expect(localizations.text(ReactorText.appTitle), 'Reaktor-Web');
    });

    test(
      'retains the active locale for missing and partial catalogs',
      () async {
        final InMemoryFleetStorage storage = InMemoryFleetStorage();
        final ReactorLocalizations localizations = ReactorLocalizations();
        final ReactorLocaleManager manager = ReactorLocaleManager(
          initialLocale: 'en_US',
          storage: storage,
          localizations: localizations,
          loader: (String locale) async => switch (locale) {
            'de_DE' => _completeCatalog(appTitle: 'Reaktor-Web'),
            'fr_FR' => null,
            _ => '{"app.title":"React Web español"}',
          },
        );

        expect((await manager.switchTo('de_DE')).applied, isTrue);
        expect((await manager.switchTo('fr_FR')).applied, isFalse);
        expect((await manager.switchTo('es_ES')).applied, isFalse);
        expect(manager.locale, 'de_DE');
        expect(storage.read(reactorLocaleStorageKey), 'de_DE');
        expect(localizations.text(ReactorText.appTitle), 'Reaktor-Web');
      },
    );

    test('ignores a slower superseded locale load', () async {
      final Completer<String?> german = Completer<String?>();
      final Completer<String?> french = Completer<String?>();
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorLocaleManager manager = ReactorLocaleManager(
        initialLocale: 'en_US',
        storage: storage,
        localizations: localizations,
        loader: (String locale) =>
            locale == 'de_DE' ? german.future : french.future,
      );

      final Future<ReactorLocaleSwitchResult> germanResult = manager.switchTo(
        'de_DE',
      );
      final Future<ReactorLocaleSwitchResult> frenchResult = manager.switchTo(
        'fr_FR',
      );
      french.complete(_completeCatalog(appTitle: 'React Web français'));
      expect((await frenchResult).applied, isTrue);
      german.complete(_completeCatalog(appTitle: 'Reaktor-Web'));
      expect((await germanResult).superseded, isTrue);
      expect(manager.locale, 'fr_FR');
      expect(storage.read(reactorLocaleStorageKey), 'fr_FR');
      expect(localizations.text(ReactorText.appTitle), 'React Web français');
    });

    test('only Hebrew is right-to-left', () {
      for (final ReactorLocaleDefinition locale in reactorLocales) {
        expect(locale.rtl, locale.code == 'he_IL', reason: locale.code);
      }
    });

    test('pre-paint bootstrap stamps locale and direction', () {
      final String html = File('web/index.html').readAsStringSync();
      final String bootstrap = File('web/bootstrap.js').readAsStringSync();
      expect(html, contains('<script src="/bootstrap.js"></script>'));
      expect(bootstrap, contains("localStorage.getItem('reactor.locale')"));
      expect(bootstrap, contains("root.setAttribute('lang', locale[0])"));
      expect(
        bootstrap,
        contains("root.setAttribute('dir', locale[1] ? 'rtl' : 'ltr')"),
      );
      expect(bootstrap, contains("'/manifests/' + locale[2] + '.webmanifest'"));
    });

    test('pre-paint locale registry matches the typed locale manifest', () {
      final String bootstrap = File('web/bootstrap.js').readAsStringSync();
      final RegExp entryPattern = RegExp(
        r"'[^']+': \['([^']+)', (true|false), '([^']+)'\]",
      );
      final Map<String, Set<String>> metadataByCode = <String, Set<String>>{};
      for (final RegExpMatch match in entryPattern.allMatches(bootstrap)) {
        final String languageTag = match.group(1)!;
        final String rtl = match.group(2)!;
        final String localeCode = match.group(3)!;
        metadataByCode
            .putIfAbsent(localeCode, () => <String>{})
            .add('$languageTag|$rtl');
      }

      expect(
        metadataByCode.keys.toSet(),
        reactorLocales
            .map((ReactorLocaleDefinition locale) => locale.code)
            .toSet(),
      );
      for (final ReactorLocaleDefinition locale in reactorLocales) {
        expect(metadataByCode[locale.code], <String>{
          '${locale.languageTag}|${locale.rtl}',
        }, reason: locale.code);
      }
    });

    test('locale assets resolve from the site root on deep routes', () {
      final Uri deepRoute = Uri.parse(
        'https://react.example/server/alpha/overview',
      );

      expect(
        resolveReactorWebAssetUri(deepRoute, 'languages/de_DE.json'),
        Uri.parse('https://react.example/languages/de_DE.json'),
      );
      expect(
        resolveReactorWebAssetUri(deepRoute, '/reactor-language.json'),
        Uri.parse('https://react.example/reactor-language.json'),
      );
      expect(
        resolveReactorWebAssetUri(
          deepRoute,
          'https://cdn.example/custom/de_DE.json',
        ),
        Uri.parse('https://cdn.example/custom/de_DE.json'),
      );
    });

    test('the static document exclusively owns title and description', () {
      final String html = File('web/index.html').readAsStringSync();
      final String appSource = File(
        'lib/app/reactor_app.dart',
      ).readAsStringSync();
      final String platformSource = File(
        'lib/localization/reactor_locale_platform_web.dart',
      ).readAsStringSync();
      final int arcaneAppStart = appSource.indexOf('ArcaneApp(');
      final int nextClassStart = appSource.indexOf(
        'class LiveServerScope',
        arcaneAppStart,
      );

      expect(arcaneAppStart, isNonNegative);
      expect(nextClassStart, greaterThan(arcaneAppStart));
      final String arcaneAppSource = appSource.substring(
        arcaneAppStart,
        nextClassStart,
      );

      expect(RegExp(r'<title>').allMatches(html), hasLength(1));
      expect(
        RegExp(
          r'<meta\s+name="description"\s+content="[^"]*">',
        ).allMatches(html),
        hasLength(1),
      );
      expect(
        RegExp(r'^      title:', multiLine: true).hasMatch(arcaneAppSource),
        isFalse,
      );
      expect(
        RegExp(
          r'^      description:',
          multiLine: true,
        ).hasMatch(arcaneAppSource),
        isFalse,
      );
      expect(platformSource, contains('web.document.title = title;'));
      expect(platformSource, contains("_setMeta('meta[name=\"description\"]'"));
    });

    test('command and pairing-code inputs stay left-to-right', () {
      final String logsSource = File('lib/screen/logs.dart').readAsStringSync();
      final String addServerSource = File(
        'lib/screen/add_server.dart',
      ).readAsStringSync();

      expect(
        logsSource,
        contains("attributes: const <String, String>{'dir': 'ltr'}"),
      );
      expect(
        addServerSource,
        contains("attributes: const <String, String>{'dir': 'ltr'}"),
      );
    });

    testComponents(
      'live locale scope rebuilds a const child without resetting its state',
      (ComponentTester tester) async {
        expect(reactorLocalizations.installOverlayJson('{}').applied, isTrue);
        addTearDown(() => reactorLocalizations.installOverlayJson('{}'));
        tester.pumpComponent(const _LocaleSwitchHarness());

        expect(find.text('Server group control console'), findsOneComponent);
        await tester.click(_domId('increment-probe'));
        expect(find.text('probe-count:1'), findsOneComponent);

        await tester.click(_domId('switch-locale'));
        expect(find.text('Servergruppen-Leitstand'), findsOneComponent);
        expect(find.text('Server group control console'), findsNothing);
        expect(find.text('probe-count:1'), findsOneComponent);
      },
    );
  });

  testServer('ArcaneDirection emits an RTL island for Hebrew content', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      const ArcaneThemeProvider(
        stylesheet: ShadcnStylesheet(theme: ShadcnTheme.midnight),
        child: ArcaneDirection(
          value: ArcaneDirectionValue.rtl,
          children: <Widget>[dom.div(<Widget>[])],
        ),
      ),
    );
    final DocumentResponse response = await tester.request('/');
    expect(response.statusCode, 200);
    expect(response.body, contains('dir="rtl"'));
  });
}
