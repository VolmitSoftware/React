library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/localization/reactor_overlay_loader.dart';

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
      for (final String locale in reactorSupportedLocales) {
        final File file = File('web/languages/$locale.json');
        expect(file.existsSync(), isTrue, reason: locale);
        final Object? decoded = jsonDecode(file.readAsStringSync());
        expect(decoded, isA<Map<String, Object?>>(), reason: locale);
        final Map<String, Object?> messages = decoded! as Map<String, Object?>;
        expect(messages.keys.toSet(), equals(expectedKeys), reason: locale);
        final List<String> terminology = _highRiskTerminology[locale]!;
        for (int index = 0; index < _highRiskKeys.length; index++) {
          expect(
            messages[_highRiskKeys[index]],
            terminology[index],
            reason:
                '$locale: contextual terminology for ${_highRiskKeys[index]}',
          );
        }
        final Map<String, String> forbiddenTranslations =
            _forbiddenContextTranslations[locale] ?? <String, String>{};
        for (final MapEntry<String, String> entry
            in forbiddenTranslations.entries) {
          expect(
            messages[entry.key],
            isNot(entry.value),
            reason: '$locale: false friend in ${entry.key}',
          );
        }

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
          } else if (locale != reactorEnglishLocale) {
            expect(
              _englishInvariantKeys.contains(key.id) ||
                  (_localeEnglishInvariantKeys[locale]?.contains(key.id) ??
                      false),
              isTrue,
              reason: '$locale: untranslated natural-language key ${key.id}',
            );
          }
        }
        if (locale == reactorEnglishLocale) {
          expect(changed, 0, reason: 'English must remain the source template');
        } else {
          expect(
            changed,
            greaterThanOrEqualTo(ReactorText.values.length * 3 ~/ 4),
            reason: '$locale is mostly English',
          );
        }
      }
    });

    test('code-first English definitions are valid', () {
      expect(ReactorLocalizations.validateDefinitions(), isNull);
    });

    test('deployment overlay only applies to its configured locale', () {
      expect(usesConfiguredReactorOverride('de_DE', 'de-DE'), isTrue);
      expect(usesConfiguredReactorOverride('fr_FR', 'de_DE'), isFalse);
      expect(usesConfiguredReactorOverride('en_US', 'invalid'), isFalse);
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
        equals('Local-first React server monitoring and control'),
      );
    });

    test('complete catalogs reject partial input atomically', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations.installOverlayJson('{"app.title":"Reaktor"}').applied,
        isTrue,
      );

      final ReactorOverlayResult rejected = localizations
          .installCompleteCatalogJson('{"app.title":"React Web français"}');

      expect(rejected.applied, isFalse);
      expect(rejected.error, contains('every localization key'));
      expect(localizations.text(ReactorText.appTitle), equals('Reaktor'));
    });

    test('rejects blank overlay and complete-catalog values atomically', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations.installOverlayJson('{"app.title":"Reaktor"}').applied,
        isTrue,
      );

      final ReactorOverlayResult blankOverlay = localizations
          .installOverlayJson('{"app.title":"  \\n\\t"}');
      expect(blankOverlay.applied, isFalse);
      expect(blankOverlay.error, contains('must not be empty'));
      expect(localizations.text(ReactorText.appTitle), equals('Reaktor'));

      final Map<String, String> complete = <String, String>{
        for (final ReactorText key in ReactorText.values) key.id: key.english,
      };
      complete[ReactorText.appDescription.id] = ' \n\t ';
      final ReactorOverlayResult blankCatalog = localizations
          .installCompleteCatalogJson(jsonEncode(complete));
      expect(blankCatalog.applied, isFalse);
      expect(blankCatalog.error, contains('must not be empty'));
      expect(localizations.text(ReactorText.appTitle), equals('Reaktor'));
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
      expect(localizations.text(ReactorText.appTitle), equals('React Web'));
    });

    test('published snapshots remain immutable after a later install', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      final ReactorLocaleSnapshot english = localizations.snapshot;

      expect(
        localizations.installOverlayJson('{"app.title":"Reaktor"}').applied,
        isTrue,
      );

      expect(english.template(ReactorText.appTitle), equals('React Web'));
      expect(localizations.snapshot.template(ReactorText.appTitle), 'Reaktor');
    });

    test('rejects placeholder mismatch without replacing the snapshot', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations
            .installOverlayJson(
              '{"screen.config_editor.confirm_preset":"Run {preset}?"}',
            )
            .applied,
        isTrue,
      );

      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{"screen.config_editor.confirm_preset":"Run {name}?"}',
      );

      expect(rejected.applied, isFalse);
      expect(rejected.error, contains('placeholders'));
      expect(
        localizations.text(
          ReactorText.configEditorConfirmPreset,
          <String, Object?>{'preset': 'GC'},
        ),
        equals('Run GC?'),
      );
    });

    test('rejects malformed placeholder braces', () {
      final ReactorLocalizations localizations = ReactorLocalizations();

      final ReactorOverlayResult rejected = localizations.installOverlayJson(
        '{"screen.config_editor.confirm_preset":"Run {{preset}}?"}',
      );

      expect(rejected.applied, isFalse);
      expect(rejected.error, contains('malformed placeholders'));
    });

    test('substitutes untrusted values exactly once', () {
      final ReactorLocalizations localizations = ReactorLocalizations();
      expect(
        localizations
            .installOverlayJson(
              '{"screen.config_editor.confirm_preset":"Ausführen: {preset}"}',
            )
            .applied,
        isTrue,
      );

      expect(
        localizations.text(
          ReactorText.configEditorConfirmPreset,
          <String, Object?>{'preset': '{preset}<script>'},
        ),
        equals('Ausführen: {preset}<script>'),
      );
    });

    test('requires the exact named arguments at lookup time', () {
      final ReactorLocalizations localizations = ReactorLocalizations();

      expect(
        () => localizations.text(ReactorText.configEditorConfirmPreset),
        throwsArgumentError,
      );
      expect(
        () => localizations.text(
          ReactorText.configEditorConfirmPreset,
          <String, Object?>{'preset': 'GC', 'extra': 'ignored'},
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

const List<String> _highRiskKeys = <String>[
  "status.live",
  "shell.all_servers",
  "screen.internals.jobs",
  "screen.worlds.title",
  "common.players",
  "common.entities",
  "common.chunks",
  "common.hoppers",
  "screen.governors.title",
  "screen.metrics.sampler",
];
const Map<String, List<String>> _highRiskTerminology = <String, List<String>>{
  "en_US": <String>[
    "Online",
    "All servers",
    "Jobs",
    "Worlds",
    "Players",
    "Entities",
    "Chunks",
    "Hoppers",
    "Governors",
    "Sampler",
  ],
  "de_DE": <String>[
    "Online",
    "Alle Server",
    "Hintergrundaufgaben",
    "Welten",
    "Spieler",
    "Entitäten",
    "Chunks",
    "Trichter",
    "Lastregler",
    "Messwertquelle",
  ],
  "es_ES": <String>[
    "En línea",
    "Todos los servidores",
    "Tareas en segundo plano",
    "Mundos",
    "Jugadores",
    "Entidades",
    "Chunks",
    "Tolvas",
    "Reguladores de carga",
    "Muestreador",
  ],
  "fi_FI": <String>[
    "Verkossa",
    "Kaikki palvelimet",
    "Taustatehtävät",
    "Maailmat",
    "Pelaajat",
    "Entiteetit",
    "Lohkot",
    "Suppilot",
    "Kuormansäätimet",
    "Mittauslähde",
  ],
  "fr_FR": <String>[
    "En ligne",
    "Tous les serveurs",
    "Tâches en arrière-plan",
    "Mondes",
    "Joueurs",
    "Entités",
    "Chunks",
    "Entonnoirs",
    "Régulateurs de charge",
    "Échantillonneur",
  ],
  "he_IL": <String>[
    "מקוון",
    "כל השרתים",
    "משימות רקע",
    "עולמות",
    "שחקנים",
    "ישויות",
    "חלקי עולם",
    "משפכים",
    "וסתי עומס",
    "דוגם",
  ],
  "it_IT": <String>[
    "Online",
    "Tutti i server",
    "Attività in background",
    "Mondi",
    "Giocatori",
    "Entità",
    "Chunk",
    "Tramogge",
    "Regolatori di carico",
    "Campionatore",
  ],
  "ja-JP": <String>[
    "オンライン",
    "すべてのサーバー",
    "バックグラウンドタスク",
    "ワールド",
    "プレイヤー",
    "エンティティ",
    "チャンク",
    "ホッパー",
    "負荷ガバナー",
    "サンプラー",
  ],
  "ko_KR": <String>[
    "온라인",
    "모든 서버",
    "백그라운드 작업",
    "세계",
    "플레이어",
    "엔티티",
    "청크",
    "호퍼",
    "부하 조절기",
    "샘플러",
  ],
  "lt_LT": <String>[
    "Veikia",
    "Visi serveriai",
    "Foninės užduotys",
    "Pasauliai",
    "Žaidėjai",
    "Esybės",
    "Pasaulio dalys",
    "Piltuvai",
    "Apkrovos reguliatoriai",
    "Matuoklis",
  ],
  "nl_NL": <String>[
    "Online",
    "Alle servers",
    "Achtergrondtaken",
    "Werelden",
    "Spelers",
    "Entiteiten",
    "Chunks",
    "Trechters",
    "Belastingsregelaars",
    "Meetbron",
  ],
  "pl_PL": <String>[
    "Online",
    "Wszystkie serwery",
    "Zadania w tle",
    "Światy",
    "Gracze",
    "Byty",
    "Chunki",
    "Leje",
    "Regulatory obciążenia",
    "Próbnik",
  ],
  "pt_PT": <String>[
    "Online",
    "Todos os servidores",
    "Tarefas em segundo plano",
    "Mundos",
    "Jogadores",
    "Entidades",
    "Chunks",
    "Funis",
    "Reguladores de carga",
    "Amostrador",
  ],
  "ru_RU": <String>[
    "В сети",
    "Все серверы",
    "Фоновые задачи",
    "Миры",
    "Игроки",
    "Сущности",
    "Чанки",
    "Воронки",
    "Регуляторы нагрузки",
    "Сэмплер",
  ],
  "tr_TR": <String>[
    "Çevrimiçi",
    "Tüm sunucular",
    "Arka plan görevleri",
    "Dünyalar",
    "Oyuncular",
    "Varlıklar",
    "Chunk'lar",
    "Huniler",
    "Yük regülatörleri",
    "Örnekleyici",
  ],
  "vi_VI": <String>[
    "Trực tuyến",
    "Tất cả máy chủ",
    "Tác vụ nền",
    "Thế giới",
    "Người chơi",
    "Thực thể",
    "Chunk",
    "Phễu",
    "Bộ điều chỉnh tải",
    "Bộ lấy mẫu",
  ],
  "zh_CN": <String>[
    "在线",
    "所有服务器",
    "后台任务",
    "世界",
    "玩家",
    "实体",
    "区块",
    "漏斗",
    "负载调节器",
    "采样器",
  ],
  "zh_TW": <String>[
    "線上",
    "所有伺服器",
    "背景任務",
    "世界",
    "玩家",
    "實體",
    "區塊",
    "漏斗",
    "負載調節器",
    "採樣器",
  ],
};

const Map<String, Map<String, String>> _forbiddenContextTranslations =
    <String, Map<String, String>>{
      'de_DE': <String, String>{
        'screen.actions.recent_executions': 'Aktuelle Hinrichtungen',
        'screen.config_editor.applying_short': 'Bewerben…',
        'chart.awaiting_samples': 'Warten auf Proben',
      },
      'es_ES': <String, String>{'screen.environment.title': 'Medio ambiente'},
      'fi_FI': <String, String>{
        'screen.actions.recent_executions': 'Viimeaikaiset teloitukset',
        'screen.config_editor.applying_short': 'Haetaan…',
      },
      'fr_FR': <String, String>{
        'screen.config_editor.applying_short': 'Candidature…',
      },
      'he_IL': <String, String>{
        'screen.actions.recent_executions': 'הוצאות להורג אחרונות',
        'screen.config_editor.applying_short': 'מגיש בקשה...',
      },
      'it_IT': <String, String>{'screen.comparison.metric': 'Metrico'},
      'ja-JP': <String, String>{
        'screen.actions.recent_executions': '最近の執行',
        'screen.config_editor.applying_short': '申請中…',
      },
      'ko_KR': <String, String>{
        'screen.actions.recent_executions': '최근 처형',
        'screen.config_editor.applying_short': '신청 중…',
      },
      'lt_LT': <String, String>{
        'screen.performance.title': 'Spektaklis',
        'screen.logs.title': 'Rąstai',
        'screen.actions.recent_executions': 'Naujausi egzekucijos',
      },
      'nl_NL': <String, String>{
        'chart.awaiting_samples': 'In afwachting van monsters',
        'screen.comparison.metric': 'Metrisch',
      },
      'pl_PL': <String, String>{
        'screen.actions.recent_executions': 'Ostatnie egzekucje',
        'screen.comparison.metric': 'Metryczne',
      },
      'ru_RU': <String, String>{
        'screen.actions.recent_executions': 'Недавние казни',
        'screen.environment.title': 'Окружающая среда',
      },
      'tr_TR': <String, String>{
        'screen.actions.recent_executions': 'Son İnfazlar',
        'screen.config_editor.applying_short': 'Başvuruluyor…',
      },
      'vi_VI': <String, String>{'alert.memory_pressure': 'Áp lực trí nhớ'},
      'zh_CN': <String, String>{
        'screen.actions.recent_executions': '最近的处决',
        'screen.config_editor.applying_short': '正在申请…',
        'screen.comparison.metric': '公制',
        'screen.add_server.direct_host': '直接主办',
        'chart.awaiting_samples': '等待样品',
      },
      'zh_TW': <String, String>{
        'screen.actions.recent_executions': '最近的處決',
        'screen.config_editor.applying_short': '正在申請…',
        'screen.comparison.metric': '公制',
        'screen.add_server.direct_host': '直接主辦',
        'chart.awaiting_samples': '等待樣品',
      },
    };

final RegExp _protocolToken = RegExp(
  r'''(?:<=|>=|==|!=|<|>)\s*-?\d+(?:\.\d+)?|\{[a-z][a-zA-Z0-9_]*\}|<[^>\n]+>|\b(?:https?|[a-z][a-z0-9+.-]*)://\S+|\[(?:SKIP|PASS|FAIL|INFO|WARN)\]|\[customCommands\.day\]|\["value-a", "value-b"\]|/give\s+<item>\s+<amount>|/re\s+map|/(?:react|reload|give|more|gms|gmc|gmsp|rl)\b|\b(?:lazy-gravity|nms-bridge|nms-hooks|crop-fast-forward)\b|\b[A-Za-z][A-Za-z0-9_]*[a-z][A-Z][A-Za-z0-9_]*\b|\b[A-Za-z][A-Za-z0-9_]*\s*=\s*(?:"[^"\n]*"|'[^'\n]*'|true|false|-?\d+(?:\.\d+)?)|\bNORMAL/PRESSURE/PANIC\b|\btrue/false\b|\b(?:sample|reset)\(\)''',
);
const Set<String> _englishInvariantKeys = <String>{
  'app.title',
  'alert.critical_description',
  'common.chunks',
  'common.redstone',
  'screen.chunks.title',
  'screen.overview.tps',
  'heatmap.teleport_confirm_message',
};
const Map<String, Set<String>> _localeEnglishInvariantKeys =
    <String, Set<String>>{
      'de_DE': <String>{'chart.range_live', 'start.online', 'status.live'},
      'es_ES': <String>{'screen.events.listeners'},
      'it_IT': <String>{'chart.range_live', 'start.online', 'status.live'},
      'nl_NL': <String>{
        'chart.range_live',
        'screen.events.listeners',
        'screen.mechanics.updates',
        'start.online',
        'status.live',
      },
      'pl_PL': <String>{'start.online', 'status.live'},
      'pt_PT': <String>{
        'screen.events.listeners',
        'start.online',
        'status.live',
      },
      'zh_CN': <String>{'common.ping_p95'},
      'zh_TW': <String>{'common.ping_p95'},
    };
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
