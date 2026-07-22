library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:jaspr_test/server_test.dart';

import 'package:reactor/localization/reactor_localizations.dart';

void main() {
  group('ReactorLocalizations', () {
    test('mirrors the exact shared non-English locale manifest', () {
      expect(reactorNonEnglishLocales, <String>[
        'de_DE',
        'es_ES',
        'fi_FI',
        'fr_FR',
        'he_IL',
        'it_IT',
        'ja-JP',
        'ko_KR',
        'lt_LT',
        'nl_NL',
        'pl_PL',
        'pt_PT',
        'ru_RU',
        'tr_TR',
        'vi_VI',
        'zh_CN',
        'zh_TW',
      ]);
      expect(canonicalReactorLocale('ja_JP'), equals('ja-JP'));
      expect(canonicalReactorLocale('ZH-tw'), equals('zh_TW'));
      expect(() => canonicalReactorLocale('en_GB'), throwsArgumentError);
    });

    test('every bundled locale covers and validates every ReactorText key', () {
      final Set<String> expectedKeys = ReactorText.values
          .map((ReactorText key) => key.id)
          .toSet();
      for (final String locale in reactorNonEnglishLocales) {
        final File file = File('web/languages/$locale.json');
        expect(file.existsSync(), isTrue, reason: locale);
        final Object? decoded = jsonDecode(file.readAsStringSync());
        expect(decoded, isA<Map<String, dynamic>>(), reason: locale);
        final Map<String, dynamic> messages = decoded! as Map<String, dynamic>;
        expect(messages.keys.toSet(), equals(expectedKeys), reason: locale);

        final ReactorLocalizations localizations = ReactorLocalizations();
        final ReactorOverlayResult result = localizations.installOverlayJson(
          jsonEncode(messages),
        );
        expect(result.applied, isTrue, reason: '$locale: ${result.error}');
        expect(result.messageCount, equals(ReactorText.values.length));
        int changed = 0;
        for (final ReactorText key in ReactorText.values) {
          final String translated = messages[key.id]! as String;
          expect(
            translated,
            isNot(contains('\uFFFD')),
            reason: '$locale: ${key.id}',
          );
          expect(
            translated,
            isNot(contains('⟬')),
            reason: '$locale: ${key.id}',
          );
          expect(
            translated,
            isNot(contains('⟭')),
            reason: '$locale: ${key.id}',
          );
          expect(
            _forbiddenTranslationArtifact.hasMatch(translated),
            isFalse,
            reason: '$locale: known translation artifact in ${key.id}',
          );
          expect(
            _hasUnexpectedScript(locale, translated),
            isFalse,
            reason: '$locale: unexpected writing system in ${key.id}',
          );
          expect(
            _protocolCounts(translated),
            equals(_protocolCounts(key.english)),
            reason: '$locale: protocol drift in ${key.id}',
          );
          expect(
            _hasBalancedStructuralDelimiters(translated),
            isTrue,
            reason: '$locale: unbalanced structural delimiters in ${key.id}',
          );
          expect(
            translated.length,
            lessThanOrEqualTo(
              key.english.length * 4 + 80 > 300
                  ? key.english.length * 4 + 80
                  : 300,
            ),
            reason: '$locale: overlong translation in ${key.id}',
          );
          expect(
            _addsRepeatedNgram(key.english, translated),
            isFalse,
            reason: '$locale: repeated translation in ${key.id}',
          );
          if (translated != key.english) {
            changed++;
          }
        }
        expect(
          changed,
          greaterThanOrEqualTo(ReactorText.values.length * 3 ~/ 4),
          reason: '$locale is mostly English',
        );
      }
    });

    test('code-first English definitions are valid', () {
      expect(ReactorLocalizations.validateDefinitions(), isNull);
    });

    test('applies a partial JSON overlay', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorOverlayResult result = localizations.installOverlayJson(
        '{"app.title":"Reaktor"}',
      );

      expect(result.applied, isTrue);
      expect(result.messageCount, equals(1));
      expect(localizations.text(ReactorText.appTitle), equals('Reaktor'));
      expect(
        localizations.text(ReactorText.appDescription),
        equals('React plugin monitoring dashboard'),
      );
    });

    test('keeps the last good snapshot after invalid JSON', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations.installOverlayJson('{"app.title":"Reaktor"}').applied,
        isTrue,
      );

      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{broken',
      );

      expect(rejected.applied, isFalse);
      expect(rejected.error, isNotNull);
      expect(localizations.text(ReactorText.appTitle), equals('Reaktor'));
    });

    test('rejects unknown keys without replacing the snapshot', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{"app.title":"Partially applied","unknown.key":"value"}',
      );

      expect(rejected.applied, isFalse);
      expect(localizations.text(ReactorText.appTitle), equals('Reactor'));
    });

    test('published snapshots remain immutable after a later install', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorLocaleSnapshot english = localizations.snapshot;

      expect(
        localizations.installOverlayJson('{"app.title":"Reaktor"}').applied,
        isTrue,
      );

      expect(english.template(ReactorText.appTitle), equals('Reactor'));
      expect(localizations.snapshot.template(ReactorText.appTitle), 'Reaktor');
    });

    test('rejects placeholder mismatch without replacing the snapshot', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations
            .installOverlayJson(
              '{"screen.actions.confirm_title":"Run {action}?"}',
            )
            .applied,
        isTrue,
      );

      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{"screen.actions.confirm_title":"Run {name}?"}',
      );

      expect(rejected.applied, isFalse);
      expect(rejected.error, contains('placeholders'));
      expect(
        localizations.text(ReactorText.actionsConfirmTitle, <String, Object?>{
          'action': 'GC',
        }),
        equals('Run GC?'),
      );
    });

    test('rejects malformed placeholder braces', () {
      final ReactorLocalizations localizations = ReactorLocalizations();

      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{"screen.actions.confirm_title":"Run {{action}}?"}',
      );

      expect(rejected.applied, isFalse);
      expect(rejected.error, contains('malformed placeholders'));
    });

    test('substitutes untrusted values exactly once', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations
            .installOverlayJson(
              '{"screen.actions.confirm_title":"Ausführen: {action}"}',
            )
            .applied,
        isTrue,
      );

      expect(
        localizations.text(ReactorText.actionsConfirmTitle, <String, Object?>{
          'action': '{action}<script>',
        }),
        equals('Ausführen: {action}<script>'),
      );
    });

    test('requires the exact named arguments at lookup time', () {
      final ReactorLocalizations localizations = ReactorLocalizations();

      expect(
        () => localizations.text(ReactorText.actionsConfirmTitle),
        throwsArgumentError,
      );
      expect(
        () => localizations.text(
          ReactorText.actionsConfirmTitle,
          <String, Object?>{'action': 'GC', 'extra': 'ignored'},
        ),
        throwsArgumentError,
      );
    });

    test('loads an external overlay only once', () async {
      final ReactorLocalizations localizations = ReactorLocalizations();
      int loads = 0;
      Future<String?> loader() async {
        loads++;
        return '{"app.title":"Reaktor"}';
      }

      final Future<ReactorOverlayResult> first = localizations.loadOverlayOnce(
        loader,
      );
      final Future<ReactorOverlayResult> second = localizations.loadOverlayOnce(
        loader,
      );

      expect(identical(first, second), isTrue);
      expect((await first).applied, isTrue);
      expect((await second).applied, isTrue);
      expect(loads, equals(1));
    });
  });
}

final RegExp _protocolToken = RegExp(
  r'''(?:<=|>=|==|!=|<|>)\s*-?\d+(?:\.\d+)?|\{[a-z][a-zA-Z0-9_]*\}|<[^>\n]+>|\b(?:https?|[a-z][a-z0-9+.-]*)://\S+|\[(?:SKIP|PASS|FAIL|INFO|WARN)\]|\[customCommands\.day\]|\["value-a", "value-b"\]|/give\s+<item>\s+<amount>|/re\s+map|/(?:react|reload|give|more|gms|gmc|gmsp|rl)\b|\b(?:lazy-gravity|nms-bridge|nms-hooks|crop-fast-forward)\b|\b[A-Za-z][A-Za-z0-9_]*[a-z][A-Z][A-Za-z0-9_]*\b|\b[A-Za-z][A-Za-z0-9_]*\s*=\s*(?:"[^"\n]*"|'[^'\n]*'|true|false|-?\d+(?:\.\d+)?)|\bNORMAL/PRESSURE/PANIC\b|\btrue/false\b|\b(?:sample|reset)\(\)''',
);
final RegExp _wordSeparator = RegExp(
  r'[^A-Za-z0-9_\u00C0-\uFFFF]+',
  unicode: true,
);
final RegExp _forbiddenTranslationArtifact = RegExp(
  r'garrapat|refrigeraci|proyector|artículo 1|governacion|Estados Unidos|unregistered|for switching|anfitrión|guardia de marea|guardia del horizonte|teléfono|atún|paracaid|제품\s*정보|뚱|의논|자주 묻는|이름\s*\*|관련 기사|지원하다|연락처|회사연혁|내 계정|여행\s*일정|기타\s*제품|문의\s*사항|东道主|東道主|调值|調值|电话|電話|警卫|警衛|投手|鑄造|铸造|短手|裸體|裸体|跳伞|跳傘|金枪鱼|金槍魚|死刑|代币|代幣|萍萍|ưμ㼯A|无t|無t|预源|預源|女士:',
  caseSensitive: false,
  unicode: true,
);
final RegExp _hangul = RegExp(
  r'[\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]',
  unicode: true,
);
final RegExp _hebrew = RegExp(r'[\u0590-\u05FF]', unicode: true);
final RegExp _cyrillic = RegExp(r'[\u0400-\u052F]', unicode: true);
final RegExp _japaneseKana = RegExp(r'[\u3040-\u30FF]', unicode: true);

Map<String, int> _protocolCounts(String value) {
  final Map<String, int> counts = <String, int>{};
  for (final RegExpMatch match in _protocolToken.allMatches(value)) {
    final String token = match.group(0)!;
    counts[token] = (counts[token] ?? 0) + 1;
  }
  return counts;
}

bool _hasBalancedStructuralDelimiters(String value) {
  final List<int> expected = <int>[];
  for (final int codeUnit in value.codeUnits) {
    if (codeUnit == 0x28) {
      expected.add(0x29);
    } else if (codeUnit == 0x5B) {
      expected.add(0x5D);
    } else if (codeUnit == 0x29 || codeUnit == 0x5D) {
      if (expected.isEmpty || expected.removeLast() != codeUnit) {
        return false;
      }
    }
  }
  return expected.isEmpty;
}

bool _addsRepeatedNgram(String english, String translated) {
  return _hasPathologicalRepetition(translated) &&
      !_hasPathologicalRepetition(english);
}

bool _hasPathologicalRepetition(String value) {
  final List<String> words = _words(value);
  for (int index = 0; index + 2 < words.length; index++) {
    if (words[index] == words[index + 1] && words[index] == words[index + 2]) {
      return true;
    }
  }
  for (int size = 2; size <= 4; size++) {
    for (int index = 0; index + size * 3 <= words.length; index++) {
      bool repeated = true;
      for (int offset = 0; offset < size; offset++) {
        final String word = words[index + offset];
        if (word != words[index + size + offset] ||
            word != words[index + size * 2 + offset]) {
          repeated = false;
          break;
        }
      }
      if (repeated) {
        return true;
      }
    }
  }
  return false;
}

bool _hasUnexpectedScript(String locale, String value) {
  return locale != 'ko_KR' && _hangul.hasMatch(value) ||
      locale != 'he_IL' && _hebrew.hasMatch(value) ||
      locale != 'ru_RU' && _cyrillic.hasMatch(value) ||
      locale != 'ja-JP' && _japaneseKana.hasMatch(value);
}

List<String> _words(String value) => value
    .toLowerCase()
    .split(_wordSeparator)
    .where((String word) => word.isNotEmpty)
    .toList();
