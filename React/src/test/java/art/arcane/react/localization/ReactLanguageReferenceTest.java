package art.arcane.react.localization;

import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class ReactLanguageReferenceTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-zA-Z0-9_]*)}");
  private static final Pattern BASIC_FORMATTING_TAG = Pattern.compile(
      "</?(?:(?:colou?r:)?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white)|bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf|reset|r)>",
      Pattern.CASE_INSENSITIVE
  );
  private static final Path LANGUAGE_ROOT = Path.of("src/main/resources/languages");
  private static final Set<String> FORMATTING_TOKENS = Set.of(
      "&c", "&0-&9", "&a-&f", "&l", "&o", "&n", "&m", "&k", "&r", "&3&l", "&#RRGGBB", "&#55AAFF",
      "MiniMessage", "TOML", "\\n", "{{", "}}", "renderer.*", "config.documentation.annotation.*", "test.*", "test.result.*"
  );

  @Test
  void literalEscapeInstructionsRemainDistinctFromActualLineBreaks() {
    LocaleOverlay overlay = ReactLanguage.parseOverlay("en_US.toml", "en_US",
        "\"test.map.clip_fail\" = \"literal \\\\n, newline \\n, backslash \\\\\\\\ end\"\n");

    Assertions.assertEquals(new TextValue("literal \\n, newline \n, backslash \\\\ end"),
        overlay.value("test.map.clip_fail"));
  }

  @Test
  void generatedEnglishHeaderDocumentsEveryCatalogPlaceholder() {
    String rendered = ReactLanguageReference.englishCatalog();

    Assertions.assertTrue(rendered.startsWith("# React — en_US\n"));
    assertFormattingGuidance(rendered, "en_US");
    assertBasicFormattingUsesColorCodes(rendered, "en_US");
    Assertions.assertEquals(catalogPlaceholders(), headerPlaceholders(rendered));
    LocaleOverlay overlay = ReactLanguage.parseOverlay("en_US.toml", "en_US", rendered);
    Assertions.assertEquals(ReactMessages.catalog().byId().keySet(), overlay.values().keySet());
    for (MessageKey key : ReactMessages.catalog().keys()) {
      Assertions.assertEquals(key.englishValue(), overlay.value(key.id()), key.id());
    }
  }

  @Test
  void everyTranslationHasALocalizedCompletePlaceholderReference() throws Exception {
    try (Stream<Path> files = Files.list(LANGUAGE_ROOT)) {
      List<Path> languages = files.filter(path -> path.getFileName().toString().endsWith(".toml")).toList();
      Assertions.assertFalse(languages.isEmpty());
      Set<String> expectedPlaceholders = catalogPlaceholders();
      for (Path language : languages) {
        String filename = language.getFileName().toString();
        String locale = filename.substring(0, filename.length() - ".toml".length());
        String content = Files.readString(language);
        List<String> translated = content.lines().takeWhile(line -> line.startsWith("#"))
            .map(line -> line.substring(1).strip()).toList();
        Assertions.assertEquals("React — " + locale, translated.getFirst(), filename);
        Assertions.assertEquals(4, translated.stream().filter(line -> line.startsWith("=== ")).count(), filename);
        Assertions.assertTrue(translated.contains("plugins/React/languages/" + locale + ".toml"), filename);
        Assertions.assertTrue(translated.contains("/react language server edit " + locale), filename);
        assertFormattingGuidance(content, filename);
        assertBasicFormattingUsesColorCodes(content, filename);
        Assertions.assertTrue(content.contains("runtime.prefix"), filename);
        Assertions.assertTrue(content.contains("renderer.*"), filename);
        Assertions.assertTrue(content.contains("config.documentation.annotation.*"), filename);
        Assertions.assertTrue(content.contains("test.result.*"), filename);
        Assertions.assertFalse(translated.contains("=== File editing ==="), filename);
        Assertions.assertTrue(content.contains("[runtime]"), filename);
        Assertions.assertFalse(content.contains("\"runtime.prefix\" ="), filename);
        Assertions.assertEquals(expectedPlaceholders, headerPlaceholders(content), filename);
      }
    }
  }

  private void assertFormattingGuidance(String content, String source) {
    String header = String.join("\n", content.lines().takeWhile(line -> line.startsWith("#")).toList());
    for (String token : FORMATTING_TOKENS) {
      Assertions.assertTrue(header.contains(token), source + ": missing formatting instruction for " + token);
    }
  }

  private void assertBasicFormattingUsesColorCodes(String content, String source) {
    String body = String.join("\n", content.lines().dropWhile(line -> line.startsWith("#")).toList());
    Assertions.assertFalse(BASIC_FORMATTING_TAG.matcher(body).find(),
        source + ": basic colors and styles should use & codes");
  }

  private Set<String> catalogPlaceholders() {
    Set<String> placeholders = new HashSet<>();
    for (MessageKey key : ReactMessages.catalog().keys()) {
      placeholders.addAll(key.placeholders());
    }
    return placeholders;
  }

  private Set<String> headerPlaceholders(String content) {
    Set<String> placeholders = new HashSet<>();
    for (String line : content.lines().toList()) {
      if (!line.startsWith("#")) {
        break;
      }
      Matcher matcher = PLACEHOLDER.matcher(line);
      while (matcher.find()) {
        placeholders.add(matcher.group(1));
      }
    }
    return placeholders;
  }
}
