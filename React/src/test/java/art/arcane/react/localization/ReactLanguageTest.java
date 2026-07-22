package art.arcane.react.localization;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.localization.catalog.ShorthandMessages;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ReactLanguageTest {
  private static final Path LANGUAGE_ROOT = Path.of("src/main/resources/languages");
  private static final Pattern PROTOCOL_TOKEN = Pattern.compile(
      "(?:<=|>=|==|!=|<|>)\\s*-?\\d+(?:\\.\\d+)?"
          + "|\\{[a-z][a-zA-Z0-9_]*}|<[^>\\n]+>|\\b(?:https?|[a-z][a-z0-9+.-]*)://\\S+"
          + "|\\[(?:SKIP|PASS|FAIL|INFO|WARN)\\]"
          + "|\\[customCommands\\.day\\]"
          + "|\\[\\\"value-a\\\", \\\"value-b\\\"\\]"
          + "|/give\\s+<item>\\s+<amount>"
          + "|/re\\s+map"
          + "|/(?:react|reload|give|more|gms|gmc|gmsp|rl)\\b"
          + "|\\b(?:lazy-gravity|nms-bridge|nms-hooks|crop-fast-forward)\\b"
          + "|\\b[A-Za-z][A-Za-z0-9_]*[a-z][A-Z][A-Za-z0-9_]*\\b"
          + "|\\b[A-Za-z][A-Za-z0-9_]*\\s*=\\s*(?:\\\"[^\\\"\\n]*\\\"|'[^'\\n]*'|true|false|-?\\d+(?:\\.\\d+)?)"
          + "|\\bNORMAL/PRESSURE/PANIC\\b"
          + "|\\btrue/false\\b"
          + "|\\b(?:sample|reset)\\(\\)"
  );
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
  private static final Pattern HANGUL = Pattern.compile("[\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]");
  private static final Pattern HEBREW = Pattern.compile("[\u0590-\u05FF]");
  private static final Pattern CYRILLIC = Pattern.compile("[\u0400-\u052F]");
  private static final Pattern JAPANESE_KANA = Pattern.compile("[\u3040-\u30FF]");
  private static final Pattern FORBIDDEN_TRANSLATION_ARTIFACT = Pattern.compile(
      "(?iu)(?:garrapat|refrigeraci|proyector|artículo 1|governacion|Estados Unidos|unregistered"
          + "|for switching|anfitrión|guardia de marea|guardia del horizonte|teléfono|atún|paracaid"
          + "|제품\\s*정보|뚱|의논|자주 묻는|이름\\s*\\*|관련 기사|지원하다|연락처|회사연혁|내 계정"
          + "|여행\\s*일정|기타\\s*제품|문의\\s*사항|东道主|東道主|调值|調值|电话|電話|警卫|警衛|投手|鑄造|铸造"
          + "|短手|裸體|裸体|跳伞|跳傘|金枪鱼|金槍魚|死刑|代币|代幣|萍萍|ưμ㼯A|无t|無t"
          + "|预源|預源|女士:)"
  );

  @AfterEach
  public void restoreEnglish() {
    LocalizationCandidate english = LocalizationCandidate.english(
        ReactMessages.catalog(),
        PluralSelector.oneOther()
    );
    Assertions.assertTrue(ReactLanguage.reloadCandidate(english).applied());
  }

  @Test
  public void codeOwnedEnglishRendersWithoutExternalFiles() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "1.2.3")
    );

    Assertions.assertEquals("React 1.2.3", rendered);
  }

  @Test
  public void everySharedNonEnglishLocaleHasOneBundledResource() throws Exception {
    Set<String> bundled = new HashSet<>();
    try (Stream<Path> files = Files.list(LANGUAGE_ROOT)) {
      files.filter(path -> path.getFileName().toString().endsWith(".toml"))
          .map(path -> path.getFileName().toString().replaceFirst("\\.toml$", ""))
          .forEach(bundled::add);
    }

    Assertions.assertEquals(new HashSet<>(VolmitLocales.nonEnglish()), bundled);
  }

  @Test
  public void everyBundledLocaleCoversAndValidatesTheEntireCatalog() throws Exception {
    MessageCatalog catalog = ReactMessages.catalog();
    for (String locale : VolmitLocales.nonEnglish()) {
      Path localeFile = LANGUAGE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = ReactLanguage.parseOverlay(
          localeFile.toString(),
          locale,
          Files.readString(localeFile)
      );
      LocalizationValidationResult validation = LocalizationValidator.validate(catalog, List.of(overlay));

      Assertions.assertTrue(validation.errors().isEmpty(), localeFile + ": " + validation.errors());
      Assertions.assertEquals(catalog.byId().keySet(), overlay.values().keySet(), localeFile.toString());
      int changed = 0;
      for (MessageKey key : catalog.keys()) {
        MessageValue translated = overlay.value(key.id());
        Assertions.assertInstanceOf(TextValue.class, translated, key.id());
        String template = ((TextValue) translated).template();
        Assertions.assertFalse(template.contains("\uFFFD"), localeFile + ": " + key.id());
        Assertions.assertFalse(template.contains("⟬"), localeFile + ": " + key.id());
        Assertions.assertFalse(template.contains("⟭"), localeFile + ": " + key.id());
        Assertions.assertFalse(
            FORBIDDEN_TRANSLATION_ARTIFACT.matcher(template).find(),
            localeFile + ": known translation artifact in " + key.id()
        );
        Assertions.assertFalse(
            hasUnexpectedScript(locale, template),
            localeFile + ": unexpected writing system in " + key.id()
        );
        String english = ((TextValue) key.englishValue()).template();
        Assertions.assertEquals(
            tokenCounts(english),
            tokenCounts(template),
            localeFile + ": protocol drift in " + key.id()
        );
        Assertions.assertTrue(
            hasBalancedStructuralDelimiters(template),
            localeFile + ": unbalanced structural delimiters in " + key.id()
        );
        Assertions.assertTrue(
            template.length() <= Math.max(300, english.length() * 4 + 80),
            localeFile + ": overlong translation in " + key.id()
        );
        Assertions.assertFalse(
            addsRepeatedNgram(english, template),
            localeFile + ": repeated translation in " + key.id()
        );
        if (!translated.equals(key.englishValue())) {
          changed++;
        }
      }
      Assertions.assertTrue(changed >= catalog.keys().size() * 3 / 4, localeFile + " is mostly English");
    }
  }

  @Test
  public void shorthandCatalogRetainsCodeOwnedEnglishDefaults() {
    Assertions.assertEquals("Switches the caster to {mode} mode.", ShorthandMessages.GAME_MODE_DESCRIPTION.english());
    Assertions.assertEquals("Gives the caster a Minecraft item.", ShorthandMessages.GIVE_DESCRIPTION.english());
    Assertions.assertEquals("Gives one maximum-size copy of the exact held item.", ShorthandMessages.MORE_DESCRIPTION.english());
    Assertions.assertEquals("Invokes the server's bare reload command.", ShorthandMessages.RELOAD_DESCRIPTION.english());
    Assertions.assertEquals("<red>This command can only be used by a player.</red>", ShorthandMessages.PLAYER_ONLY.english());
    Assertions.assertEquals("<red>Usage: {usage}</red>", ShorthandMessages.USAGE.english());
    Assertions.assertEquals("<green>Game mode set to {mode}.</green>", ShorthandMessages.GAME_MODE_SET.english());
    Assertions.assertEquals("<red>Unknown or unavailable item: {item}</red>", ShorthandMessages.ITEM_UNAVAILABLE.english());
    Assertions.assertEquals("<red>Amount must be between 1 and {maximum}.</red>", ShorthandMessages.AMOUNT_OUT_OF_RANGE.english());
    Assertions.assertEquals("<green>Gave {amount} {item}.</green>", ShorthandMessages.ITEM_GIVEN.english());
    Assertions.assertEquals("<red>Hold an item before using /more.</red>", ShorthandMessages.MORE_EMPTY_HAND.english());
    Assertions.assertEquals("<green>Gave one exact stack of the held item.</green>", ShorthandMessages.MORE_GIVEN.english());
    Assertions.assertEquals("<red>The server's /reload command is unavailable.</red>", ShorthandMessages.RELOAD_UNAVAILABLE.english());
    Assertions.assertEquals("Stopped recursive shorthand /{label}.", ShorthandMessages.RECURSIVE_STOPPED.english());
    Assertions.assertEquals("The configured command for /{label} is unavailable.", ShorthandMessages.CONFIGURED_UNAVAILABLE.english());
    Assertions.assertEquals("The configured command for /{label} failed.", ShorthandMessages.CONFIGURED_FAILED.english());
  }

  @Test
  public void visibleRuntimeLabelsRetainCodeOwnedEnglishDefaults() {
    Assertions.assertEquals("UNIQUE", RuntimeMessages.MOB_STACKING_UNIQUE.english());
    Assertions.assertEquals("{seconds}s", RuntimeMessages.ENTITY_KILLER_COUNTDOWN.english());
  }

  @Test
  public void validOverlayReplacesEnglishTemplate() {
    LocaleOverlay overlay = LocaleOverlay.builder("test", "de_DE")
        .text(CommandMessages.VERSION.id(), "<aqua>React-Version {version}</aqua>")
        .build();
    LocalizationCandidate candidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(overlay),
        PluralSelector.oneOther()
    );

    LocalizationReloadResult result = ReactLanguage.reloadCandidate(candidate);

    Assertions.assertTrue(result.applied());
    Assertions.assertEquals(
        "React-Version 2.0",
        ReactLanguage.plain(CommandMessages.VERSION, MessageArgument.untrusted("version", "2.0"))
    );
  }

  @Test
  public void shorthandFeedbackUsesOverlayAndEscapesUntrustedValues() {
    LocaleOverlay overlay = LocaleOverlay.builder("shorthand-test", "de_DE")
        .text(ShorthandMessages.GAME_MODE_SET.id(), "<green>Spielmodus auf {mode} gesetzt.</green>")
        .text(ShorthandMessages.ITEM_UNAVAILABLE.id(), "<red>Unbekannter Gegenstand: {item}</red>")
        .build();
    LocalizationCandidate candidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(overlay),
        PluralSelector.oneOther()
    );

    Assertions.assertTrue(ReactLanguage.reloadCandidate(candidate).applied());
    Assertions.assertEquals(
        "Spielmodus auf creative gesetzt.",
        ReactLanguage.plain(
            ShorthandMessages.GAME_MODE_SET,
            MessageArgument.untrusted("mode", "creative")
        )
    );
    Assertions.assertEquals(
        "Unbekannter Gegenstand: <green>unsafe</green>",
        ReactLanguage.plain(
            ShorthandMessages.ITEM_UNAVAILABLE,
            MessageArgument.untrusted("item", "<green>unsafe</green>")
        )
    );
  }

  @Test
  public void untrustedArgumentsCannotInjectMiniMessageFormatting() {
    String rendered = ReactLanguage.plain(
        CommandMessages.VERSION,
        MessageArgument.untrusted("version", "<red>unsafe</red>")
    );

    Assertions.assertEquals("React <red>unsafe</red>", rendered);
  }

  @Test
  public void rejectedOverlayRetainsLastGoodSnapshot() {
    LocaleOverlay goodOverlay = LocaleOverlay.builder("good", "fr_FR")
        .text(CommandMessages.VERSION.id(), "<aqua>Version React {version}</aqua>")
        .build();
    LocalizationCandidate goodCandidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(goodOverlay),
        PluralSelector.oneOther()
    );
    Assertions.assertTrue(ReactLanguage.reloadCandidate(goodCandidate).applied());

    LocaleOverlay invalidOverlay = LocaleOverlay.builder("invalid", "fr_FR")
        .text(CommandMessages.VERSION.id(), "<aqua>Version React</aqua>")
        .build();
    LocalizationCandidate invalidCandidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(invalidOverlay),
        PluralSelector.oneOther()
    );

    LocalizationReloadResult rejected = ReactLanguage.reloadCandidate(invalidCandidate);

    Assertions.assertFalse(rejected.applied());
    Assertions.assertEquals(
        "Version React 3.0",
        ReactLanguage.plain(CommandMessages.VERSION, MessageArgument.untrusted("version", "3.0"))
    );
  }

  @Test
  public void rawRendererTemplatesReloadWithoutCachingEnglish() {
    LocaleOverlay overlay = LocaleOverlay.builder("renderer-test", "test_TEST")
        .text(RendererMessages.METRIC_LINE.id(), "{value} — {label}")
        .text(RendererMessages.METRIC_TPS.id(), "Ticks/s")
        .text("gui.map.name.sampler.tick_time", "Tickdauer")
        .build();
    LocalizationCandidate candidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(overlay),
        PluralSelector.oneOther()
    );

    Assertions.assertTrue(ReactLanguage.reloadCandidate(candidate).applied());
    Assertions.assertEquals("19.9 — Ticks/s", RendererMessages.metricLine(RendererMessages.METRIC_TPS, "19.9"));
    Assertions.assertEquals("Tickdauer", MapMessages.localizedSamplerName("tick-time", "Tick Time"));
  }

  @Test
  public void developerCheckLabelsLocalizeWithoutChangingReportIdentifiers() {
    LocaleOverlay overlay = LocaleOverlay.builder("test-labels", "test_TEST")
        .text("test.label.subsystem.monitoring", "Überwachung")
        .text("test.label.name.sampler_finiteness", "Endliche Messwerte")
        .build();
    LocalizationCandidate candidate = new LocalizationCandidate(
        ReactMessages.catalog(),
        List.of(overlay),
        PluralSelector.oneOther()
    );

    Assertions.assertTrue(ReactLanguage.reloadCandidate(candidate).applied());
    Assertions.assertEquals("Überwachung", TestMessages.subsystemLabel("monitoring"));
    Assertions.assertEquals("Endliche Messwerte", TestMessages.nameLabel("Sampler finiteness"));
    Assertions.assertEquals("custom-check-id", TestMessages.nameLabel("custom-check-id"));
  }

  private Map<String, Integer> tokenCounts(String value) {
    Map<String, Integer> counts = new HashMap<>();
    Matcher matcher = PROTOCOL_TOKEN.matcher(value);
    while (matcher.find()) {
      counts.merge(matcher.group(), 1, Integer::sum);
    }
    return counts;
  }

  private boolean hasBalancedStructuralDelimiters(String value) {
    ArrayDeque<Character> expected = new ArrayDeque<>();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '(') {
        expected.push(')');
      } else if (character == '[') {
        expected.push(']');
      } else if (character == ')' || character == ']') {
        if (expected.isEmpty() || expected.pop() != character) {
          return false;
        }
      }
    }
    return expected.isEmpty();
  }

  private boolean addsRepeatedNgram(String english, String translated) {
    return hasPathologicalRepetition(translated) && !hasPathologicalRepetition(english);
  }

  private boolean hasPathologicalRepetition(String value) {
    List<String> valueWords = words(value);
    for (int index = 0; index + 2 < valueWords.size(); index++) {
      if (valueWords.get(index).equals(valueWords.get(index + 1))
          && valueWords.get(index).equals(valueWords.get(index + 2))) {
        return true;
      }
    }
    for (int size = 2; size <= 4; size++) {
      for (int index = 0; index + size * 3 <= valueWords.size(); index++) {
        List<String> first = valueWords.subList(index, index + size);
        List<String> second = valueWords.subList(index + size, index + size * 2);
        List<String> third = valueWords.subList(index + size * 2, index + size * 3);
        if (first.equals(second) && first.equals(third)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasUnexpectedScript(String locale, String value) {
    return !"ko_KR".equals(locale) && HANGUL.matcher(value).find()
        || !"he_IL".equals(locale) && HEBREW.matcher(value).find()
        || !"ru_RU".equals(locale) && CYRILLIC.matcher(value).find()
        || !"ja-JP".equals(locale) && JAPANESE_KANA.matcher(value).find();
  }

  private List<String> words(String value) {
    List<String> words = new ArrayList<>();
    Matcher matcher = WORD.matcher(value.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      words.add(matcher.group());
    }
    return words;
  }
}
