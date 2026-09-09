package art.arcane.react.localization;

import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class ReactLanguageReferenceTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-zA-Z0-9_]*)}");
  private static final Pattern REFERENCE_TOKEN = Pattern.compile(
      "\\{[a-z][a-zA-Z0-9_]*}|\\{\\{|}}|<[^>]+>|/react language server edit"
          + "|[a-z]+(?:\\.[a-z]+)*\\.\\*|\\\\n|&[0-9a-f]"
          + "|(?<![A-Za-z])(?:languages|MiniMessage|TOML|RGB|React|CPU|TPS|NMS|API|URL|MB)"
          + "|(?<![A-Za-z])(?:viewer|operator|admin)(?![A-Za-z])"
  );
  private static final Path LANGUAGE_ROOT = Path.of("src/main/resources/languages");

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

    Assertions.assertTrue(rendered.startsWith("# React language: en_US\n"));
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
        List<String> english = ReactLanguageReference.header(locale);
        List<String> translated = content.lines().takeWhile(line -> line.startsWith("#"))
            .map(line -> line.substring(1).strip()).toList();

        Assertions.assertEquals(english.size(), translated.size(), filename);
        Assertions.assertTrue(translated.getFirst().contains(locale), filename);
        for (int index = 0; index < english.size(); index++) {
          String source = english.get(index).strip();
          String target = translated.get(index);
          String context = filename + ": header line " + (index + 1);
          if (source.isEmpty()) {
            Assertions.assertTrue(target.isEmpty(), context);
            continue;
          }
          Assertions.assertFalse(target.isEmpty(), context);
          Assertions.assertNotEquals(source, target, context + " is still English");
          Assertions.assertEquals(referenceTokens(source), referenceTokens(target), context);
        }
        Assertions.assertEquals(expectedPlaceholders, headerPlaceholders(content), filename);
      }
    }
  }

  private Set<String> catalogPlaceholders() {
    Set<String> placeholders = new HashSet<>();
    for (MessageKey key : ReactMessages.catalog().keys()) {
      placeholders.addAll(key.placeholders());
    }
    return placeholders;
  }

  private Map<String, Integer> referenceTokens(String line) {
    Map<String, Integer> tokens = new HashMap<>();
    Matcher matcher = REFERENCE_TOKEN.matcher(line);
    while (matcher.find()) {
      tokens.merge(matcher.group(), 1, Integer::sum);
    }
    return tokens;
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
