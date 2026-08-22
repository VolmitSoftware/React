package art.arcane.react.content.PAPI;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PapiExpansionTest {
  private static final Path SOURCE_DIR = Path.of("src/main/java/art/arcane/react/content/PAPI");
  private static final Path PLUGIN_SOURCE = Path.of("src/main/java/art/arcane/react/React.java");
  private static final Path PLUGIN_METADATA = Path.of("src/main/resources/plugin.yml");
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

  private static final class FakeSamplers implements ReactPlaceholderSource.SamplerReader {
    @Override
    public Set<String> ids() {
      return Set.of("ticks-per-second", "tick-time", "entities", "wormholes-portals");
    }

    @Override
    public double sample(String samplerId) {
      return switch (samplerId) {
        case "ticks-per-second" -> 19.5D;
        case "tick-time" -> 42.0D;
        case "entities" -> 900.0D;
        case "wormholes-portals" -> 3.0D;
        default -> Double.NaN;
      };
    }
  }

  private static OfflinePlayer player(UUID id) {
    return (OfflinePlayer) Proxy.newProxyInstance(
        PapiExpansionTest.class.getClassLoader(),
        new Class<?>[]{OfflinePlayer.class},
        (proxy, method, args) -> {
          if ("getUniqueId".equals(method.getName())) {
            return id;
          }

          throw new UnsupportedOperationException("placeholder resolution touched " + method.getName());
        }
    );
  }

  private static ReactPlaceholderSource warmedSource(String... paths) {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    FakeSamplers samplers = new FakeSamplers();
    source.publish(samplers, Map.of());

    for (String path : paths) {
      source.registry().resolve(PLAYER, path);
    }

    source.publish(samplers, Map.of());
    return source;
  }

  private static PapiExpansion expansion(ReactPlaceholderSource source) {
    return new PapiExpansion(source, Logger.getLogger("react-papi-test"));
  }

  @Test
  void shouldExposeStableMetadataWithoutTouchingThePluginInstance() {
    PapiExpansion expansion = expansion(new ReactPlaceholderSource());

    assertEquals("react", expansion.getIdentifier());
    assertEquals("Volmit Software", expansion.getAuthor());
    assertEquals("2.0.0", expansion.getVersion());
    assertEquals("React", expansion.getRequiredPlugin());
    assertTrue(expansion.persist());
  }

  @Test
  void shouldResolveAGlobalKeyThroughTheSharedBarrier() {
    PapiExpansion expansion = expansion(warmedSource("tps"));

    assertEquals("19.50", expansion.onRequest(player(PLAYER), "tps"));
  }

  @Test
  void shouldLowercaseTheWholeParamsStringBeforeDispatch() {
    PapiExpansion expansion = expansion(warmedSource("tps", "sampler.wormholes-portals"));

    assertEquals("19.50", expansion.onRequest(player(PLAYER), "TPS"));
    assertEquals("3", expansion.onRequest(player(PLAYER), "Sampler.Wormholes-Portals"));
  }

  @Test
  void shouldReturnNullForBlankOrUnknownPathsSoTheLiteralStaysVisible() {
    PapiExpansion expansion = expansion(warmedSource("tps"));

    assertNull(expansion.onRequest(player(PLAYER), ""));
    assertNull(expansion.onRequest(player(PLAYER), " "));
    assertNull(expansion.onRequest(player(PLAYER), "tpz"));
    assertNull(expansion.onRequest(player(PLAYER), "stat.tps"));
    assertNull(expansion.onRequest(player(PLAYER), "tweak.enabled"));
    assertNull(expansion.onRequest(player(PLAYER), "feature.enabled"));
    assertNull(expansion.onRequest(player(PLAYER), "sampler.not-a-sampler"));
  }

  @Test
  void shouldRejectTheLegacyUnderscoreGrammarOutright() {
    PapiExpansion expansion = expansion(warmedSource("tps", "sampler.tick-time"));

    assertNull(expansion.onRequest(player(PLAYER), "sampler_tick-time"));
    assertNull(expansion.onRequest(player(PLAYER), "stat_tps"));
    assertNull(expansion.onRequest(player(PLAYER), "tweak_enabled_hopper"));
  }

  @Test
  void shouldAnswerTheReservedAvailableKey() {
    assertEquals("false", expansion(new ReactPlaceholderSource()).onRequest(player(PLAYER), "available"));
    assertEquals("true", expansion(warmedSource()).onRequest(player(PLAYER), "available"));
  }

  @Test
  void shouldTouchNothingOnThePlayerBeyondGetUniqueId() {
    ReactPlaceholderSource source = new ReactPlaceholderSource();
    UUID world = UUID.randomUUID();
    source.trackPlayerWorld(PLAYER, world);
    source.publish(new FakeSamplers(), Map.of(world, 6.5D));
    PapiExpansion expansion = expansion(source);

    assertEquals("6.50", expansion.onRequest(player(PLAYER), "world.mspt"));
    assertEquals("---", expansion.onRequest(player(UUID.randomUUID()), "world.mspt"));
  }

  @Test
  void shouldSelfDocumentEveryKeyThroughGetPlaceholders() {
    List<String> keys = expansion(new ReactPlaceholderSource()).getPlaceholders();

    assertTrue(keys.contains("available"), "the reserved availability key must be listed");
    assertTrue(keys.contains("sampler.*"), "the generic sampler passthrough must be listed");
    assertTrue(keys.contains("world.mspt"));
    assertEquals(13, keys.size(), "the published key surface changed, and got: " + keys);
  }

  @Test
  void shouldNotLogAnythingOnTheResolutionPath() throws Exception {
    for (Path source : Files.list(SOURCE_DIR).toList()) {
      String body = Files.readString(source).replace("\r\n", "\n");
      assertFalse(body.contains("React.info("), source + " must never log on the placeholder resolution path");
      assertFalse(body.contains("React.debug("), source + " must never log on the placeholder resolution path");
      assertFalse(body.contains("React.warn("), source + " must never log on the placeholder resolution path");
    }
  }

  @Test
  void shouldKeepTheResolutionPathFreeOfBukkitAndPluginStatics() throws Exception {
    String body = Files.readString(SOURCE_DIR.resolve("ReactPlaceholderSource.java")).replace("\r\n", "\n");

    assertFalse(body.contains("org.bukkit"), "the resolver must never import a Bukkit type");
    assertFalse(body.contains("React.instance"), "the resolver must never read a plugin static");
    assertFalse(body.contains("React.controller"), "the resolver must never reach a controller");
    assertFalse(body.contains("synchronized"), "the resolver must never take a lock");
  }

  @Test
  void shouldRetainAndUnregisterThePlaceholderRuntimeAcrossTheDisablePath() throws Exception {
    String source = Files.readString(PLUGIN_SOURCE).replace("\r\n", "\n");

    assertTrue(source.contains("private volatile ReactPlaceholders papiExpansion;"),
        "the placeholder runtime must be retained on a plugin field");
    assertTrue(source.contains("placeholders.stop();"),
        "the retained placeholder runtime must be stopped on disable");
    assertTrue(source.contains("ready = false;\n    unregisterPapiExpansion();"),
        "stop() must unregister the retained expansion");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldOrderReactAfterPlaceholderApiWheneverRegistrationGatesOnTheEnabledState() throws Exception {
    String source = Files.readString(PLUGIN_SOURCE).replace("\r\n", "\n");
    assertTrue(source.contains("isPluginEnabled(\"PlaceholderAPI\")"),
        "registration must gate on the enabled state of PlaceholderAPI");

    Map<String, Object> metadata;
    try (InputStream stream = Files.newInputStream(PLUGIN_METADATA)) {
      metadata = new Yaml().load(stream);
    }

    Object declared = metadata.get("softdepend");
    assertTrue(declared instanceof List,
        "plugin.yml must declare a softdepend list so Bukkit orders React after PlaceholderAPI; "
            + "an enable-time gate without a declared ordering is skipped outright on servers that "
            + "enable React first, and got: " + declared);
    assertTrue(((List<Object>) declared).contains("PlaceholderAPI"),
        "softdepend must contain PlaceholderAPI, and got: " + declared);
    assertFalse(metadata.containsKey("depend")
            && ((List<Object>) metadata.get("depend")).contains("PlaceholderAPI"),
        "PlaceholderAPI must stay optional, so it may never appear under depend");
  }
}
