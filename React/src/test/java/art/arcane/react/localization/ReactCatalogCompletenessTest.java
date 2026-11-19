package art.arcane.react.localization;

import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextValue;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactCatalogCompletenessTest {
  private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/main/java"), Path.of("core/src/main/java"));
  private static final Path CATALOG_PACKAGE = Path.of("art/arcane/react/localization/catalog");
  private static final Pattern LITERAL_RAW_CALL = Pattern.compile(
      "ReactLanguage\\.raw\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")\\s*,\\s*(\"(?:\\\\.|[^\"\\\\])*\")\\s*\\)"
  );
  private static final Gson GSON = new Gson();

  @Test
  void sharedRuntimeMessagesAreRegisteredWithoutChangingTheirDefinitions() {
    MessageCatalog catalog = ReactMessages.catalog();
    List<List<MessageKey>> groups = List.of(
        DirectorMessages.keys(), BukkitLanguageMessages.keys(), BukkitDebugMessages.keys());
    for (List<MessageKey> keys : groups) {
      assertFalse(keys.isEmpty());
      for (MessageKey key : keys) {
        assertEquals(key, catalog.require(key.id()), key.id());
      }
    }
  }

  @Test
  void everyDeclaredMessageKeyIsRegisteredWithoutChangingItsDefinition() throws Exception {
    MessageCatalog catalog = ReactMessages.catalog();
    List<Path> sources = new ArrayList<>();
    for (Path sourceRoot : SOURCE_ROOTS) {
      Path catalogRoot = sourceRoot.resolve(CATALOG_PACKAGE);
      if (!Files.isDirectory(catalogRoot)) {
        continue;
      }
      try (Stream<Path> files = Files.list(catalogRoot)) {
        sources.addAll(files.filter(path -> path.getFileName().toString().endsWith(".java")).toList());
      }
    }
    assertFalse(sources.isEmpty());
    int checked = 0;
    for (Path source : sources) {
      String filename = source.getFileName().toString();
      Class<?> type = Class.forName("art.arcane.react.localization.catalog."
          + filename.substring(0, filename.length() - ".java".length()));
      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) || !MessageKey.class.isAssignableFrom(field.getType())) {
          continue;
        }
        field.setAccessible(true);
        MessageKey key = (MessageKey) field.get(null);
        assertEquals(key, catalog.require(key.id()), type.getSimpleName() + "." + field.getName());
        checked++;
      }
    }
    assertTrue(checked > 0);
  }

  @Test
  void everyLiteralMessageLookupHasItsExactEnglishFallbackInTheCatalog() throws Exception {
    MessageCatalog catalog = ReactMessages.catalog();
    List<Path> sources = new ArrayList<>();
    for (Path sourceRoot : SOURCE_ROOTS) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        sources.addAll(files.filter(path -> path.getFileName().toString().endsWith(".java")).toList());
      }
    }
    int checked = 0;
    for (Path source : sources) {
      Matcher matcher = LITERAL_RAW_CALL.matcher(Files.readString(source));
      while (matcher.find()) {
        String id = GSON.fromJson(matcher.group(1), String.class);
        String english = GSON.fromJson(matcher.group(2), String.class);
        assertEquals(new TextValue(english), catalog.require(id).englishValue(), source + ": " + id);
        checked++;
      }
    }
    assertTrue(checked > 0);
  }
}
