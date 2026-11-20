package art.arcane.react.core.pluginapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApiPackParserTest {
  private static final BukkitPluginApiHost HOST = new BukkitPluginApiHost();

  private static final List<Path> EXAMPLES = List.of(Path.of("src/main/resources/plugin-apis/examples"),
      Path.of("core/src/main/resources/plugin-apis/examples"));

  @Test
  void parsesEveryBundledExample() throws IOException {
    List<Path> examples = new ArrayList<>();
    for (Path folder : EXAMPLES) {
      try (java.util.stream.Stream<Path> stream = Files.list(folder)) {
        examples.addAll(stream.filter(path -> path.toString().endsWith(".toml")).sorted().toList());
      }
    }
    assertEquals(3, examples.size());
    for (Path example : examples) {
      String content = Files.readString(example, StandardCharsets.UTF_8);
      PluginApiPackDefinition definition = PluginApiPackParser.parse(example, content, HOST::icon);
      assertEquals(PluginApiPackParser.SCHEMA, definition.schema());
      assertFalse(definition.metrics().isEmpty());
    }
  }

  @Test
  void rejectsUnknownSchemaKeys() {
    String content = minimalPack(false).replace("targetPlugin = \"Example\"", "targetPlugin = \"Example\"\nlegacyMode = true");
    IOException failure = assertThrows(
        IOException.class,
        () -> PluginApiPackParser.parse(Path.of("unknown.toml"), content, HOST::icon)
    );
    assertTrue(failure.getMessage().contains("Unknown key root.legacyMode"));
  }

  @Test
  void placeholderSourcesRequireExplicitTrust() {
    IOException failure = assertThrows(
        IOException.class,
        () -> PluginApiPackParser.parse(Path.of("placeholder.toml"), minimalPack(true), HOST::icon)
    );
    assertTrue(failure.getMessage().contains("requires trusted = true"));
  }

  @Test
  void matchesOnlyCurrentExplicitVersionPatterns() {
    assertTrue(PluginApiPackParser.versionMatches("1.4.2", List.of("1.4.*")));
    assertTrue(PluginApiPackParser.versionMatches("1.4.2", List.of("1.4.2")));
    assertFalse(PluginApiPackParser.versionMatches("2.0.0", List.of("1.*")));
  }

  private String minimalPack(boolean placeholder) {
    String source = placeholder
        ? "type = \"placeholder\"\nplaceholder = \"%server_online%\"\nfoliaSafe = true"
        : "type = \"integration\"\npluginId = \"example\"\nkey = \"metric.value\"\nfoliaSafe = true";
    return """
        schema = "react.plugin-api/v1"
        id = "test.example"
        version = "1.0.0"
        name = "Example"
        authors = ["Tests"]
        enabled = true
        trusted = false
        targetPlugin = "Example"
        targetVersions = ["1.*"]

        [[metrics]]
        id = "value"
        displayName = "Value"
        kind = "gauge"
        icon = "CLOCK"
        sampleEveryMs = 1000
        staleAfterMs = 15000

        [metrics.source]
        %s
        """.formatted(source);
  }
}
