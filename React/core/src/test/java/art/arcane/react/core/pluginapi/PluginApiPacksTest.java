package art.arcane.react.core.pluginapi;

import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.api.metric.internal.HostMetrics;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackState;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginApiPacksTest {
  @TempDir
  Path dataFolder;

  private final SampleController samples = mock(SampleController.class);
  private final List<String> errors = new ArrayList<>();
  private PluginApiPacks packs;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void bindPlatform() {
    when(samples.getSamplers()).thenReturn(mock(Registry.class));
    when(samples.registerSampler(any())).thenReturn(true);
    ReactPlatforms.unbind();
    ReactPlatforms.bind(new TestPlatform(samples, errors));
  }

  @AfterEach
  void unbindPlatform() {
    if (packs != null) {
      packs.stop();
    }
    HostMetrics.uninstall();
    ReactPlatforms.unbind();
  }

  @Test
  void packWaitsWithTheHostsMissingTargetDetail() throws IOException {
    TestHost host = new TestHost();
    load(host, pack("community.iris", "iris", "CLOCK"));

    packs.collectOnServerThread();

    PackStatus status = packs.status("community.iris");
    assertEquals(PackState.TARGET_MISSING, status.state());
    assertEquals("target-mod-not-loaded", status.detail());
    assertTrue(packs.validationErrors().isEmpty());
  }

  @Test
  void integrationMetricIsHealthyOnceTheTargetIsLoaded() throws IOException {
    TestHost host = new TestHost();
    host.loadedVersion = "1.4.0";
    host.integrationValue = 42D;
    load(host, pack("community.iris", "iris", "CLOCK"));

    packs.collectOnServerThread();

    PackStatus status = packs.status("community.iris");
    assertEquals(PackState.HEALTHY, status.state());
    assertEquals("1.4.0", status.targetVersion());
    assertEquals("iris", host.lastIntegrationId);
  }

  @Test
  void unknownIconIsAValidationError() throws IOException {
    TestHost host = new TestHost();
    load(host, pack("community.iris", "iris", "not an item"));

    assertNull(packs.status("community.iris"));
    assertEquals("metrics[0].icon must be a material", packs.validationErrors().get("community.iris.toml"));
  }

  @Test
  void eventCounterWithoutPlatformSupportStaysUnavailableWithoutQuarantine() throws IOException {
    TestHost host = new TestHost();
    host.loadedVersion = "1.0.0";
    load(host, pack("community.events", "iris", "CLOCK")
        .replace("type = \"integration\"\npluginId = \"iris\"\nkey = \"metric.value\"",
            "type = \"event-counter\"\neventClass = \"example.SomeEvent\""));

    for (int round = 0; round < 8; round++) {
      packs.collectOnServerThread();
    }

    PackStatus status = packs.status("community.events");
    assertEquals(PackState.DEGRADED, status.state());
    assertEquals("event-counters-unsupported", status.metrics().get(0).availabilityReason());
  }

  @Test
  void oneFailingPackDoesNotStopTheRemainingPacks() {
    packs = new PluginApiPacks(new TestHost());
    packs.start(dataFolder);
    PluginApiPackRuntime failing = mock(PluginApiPackRuntime.class);
    PluginApiPackRuntime healthy = mock(PluginApiPackRuntime.class);
    doThrow(new IllegalStateException("collect failed")).when(failing).collect(anyLong());
    packs.put("failing", failing);
    packs.put("healthy", healthy);

    packs.collectOnServerThread();
    packs.collectOnServerThread();

    verify(healthy, times(2)).collect(anyLong());
    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Mod API pack failing"));
  }

  @Test
  void activeCatalogFollowsStartAndStop() {
    packs = new PluginApiPacks(new TestHost());
    assertNull(PluginApiPacks.active());

    packs.start(dataFolder);
    assertSame(packs, PluginApiPacks.active());
    assertEquals(dataFolder.resolve("mod-apis").toAbsolutePath().normalize(), packs.packFolder());

    packs.stop();
    assertNull(PluginApiPacks.active());
  }

  @Test
  void parserNamesTheModPackOnModdedServers() {
    String oversized = "x".repeat(PluginApiPackParser.MAX_FILE_BYTES + 1);

    IOException failure = assertThrows(IOException.class,
        () -> PluginApiPackParser.parse(Path.of("big.toml"), oversized, raw -> raw));

    assertTrue(failure.getMessage().startsWith("Mod API pack exceeds"));
  }

  @Test
  void hostMetricsReadReactsOwnSamplers() {
    Sampler tps = mock(Sampler.class);
    when(tps.sample()).thenReturn(19.5D);
    when(tps.isSampleAvailable()).thenReturn(true);
    when(samples.getSampler("tps")).thenReturn(tps);

    assertTrue(Double.isNaN(ReactMetrics.readHostMetric("tps")));
    HostMetrics.install();

    assertEquals(19.5D, ReactMetrics.readHostMetric("tps"));
    assertTrue(Double.isNaN(ReactMetrics.readHostMetric("missing")));
  }

  private void load(TestHost host, String content) throws IOException {
    packs = new PluginApiPacks(host);
    packs.start(dataFolder);
    Files.createDirectories(packs.packFolder());
    String id = content.lines().filter(line -> line.startsWith("id = ")).findFirst().orElseThrow()
        .substring(6).replace("\"", "");
    Files.writeString(packs.packFolder().resolve(id + ".toml"), content, StandardCharsets.UTF_8);
    packs.applyCatalog(packs.scan());
  }

  private static String pack(String id, String target, String icon) {
    return "schema = \"react.plugin-api/v1\"\n"
        + "id = \"" + id + "\"\n"
        + "version = \"1.0.0\"\n"
        + "name = \"Test\"\n"
        + "authors = [\"Tests\"]\n"
        + "enabled = true\n"
        + "trusted = false\n"
        + "targetPlugin = \"" + target + "\"\n"
        + "targetVersions = [\"1.*\"]\n"
        + "\n"
        + "[[metrics]]\n"
        + "id = \"value\"\n"
        + "displayName = \"Value\"\n"
        + "kind = \"gauge\"\n"
        + "icon = \"" + icon + "\"\n"
        + "sampleEveryMs = 1000\n"
        + "staleAfterMs = 15000\n"
        + "\n"
        + "[metrics.source]\n"
        + "type = \"integration\"\n"
        + "pluginId = \"iris\"\n"
        + "key = \"metric.value\"\n"
        + "foliaSafe = true\n";
  }

  private static final class TestHost implements PluginApiHost {
    private String loadedVersion;
    private double integrationValue = Double.NaN;
    private String lastIntegrationId;

    @Override
    public String folderName() {
      return "mod-apis";
    }

    @Override
    public String label() {
      return "Mod API";
    }

    @Override
    public String targetMissingDetail() {
      return "target-mod-not-loaded";
    }

    @Override
    public List<String> bundledExamples() {
      return List.of();
    }

    @Override
    public InputStream bundledExample(String name) {
      return null;
    }

    @Override
    public String icon(String raw) {
      String id = raw.toLowerCase(Locale.ROOT);
      return id.matches("[a-z_]+") ? "minecraft:" + id : null;
    }

    @Override
    public Target target(String id) {
      return loadedVersion == null ? null : new Target(loadedVersion, getClass().getClassLoader());
    }

    @Override
    public double integration(String integrationId, String key) {
      lastIntegrationId = integrationId;
      return integrationValue;
    }

    @Override
    public double placeholder(String placeholder, boolean foliaSafe) {
      return Double.NaN;
    }

    @Override
    public Runnable eventCounter(Target target, String eventClass, Runnable increment) {
      return null;
    }
  }

  private static final class TestPlatform implements ReactPlatform {
    private final SamplerHost samplerHost;
    private final List<String> errors;

    private TestPlatform(SampleController samples, List<String> errors) {
      this.samplerHost = mock(SamplerHost.class);
      when(samplerHost.sampleController()).thenReturn(Optional.of(samples));
      this.errors = errors;
    }

    @Override
    public PlatformKind kind() {
      return PlatformKind.MODDED;
    }

    @Override
    public String platformName() {
      return "NeoForge";
    }

    @Override
    public String minecraftVersion() {
      return "26.3";
    }

    @Override
    public File dataFolder() {
      return null;
    }

    @Override
    public PlatformScheduler scheduler() {
      return mock(PlatformScheduler.class);
    }

    @Override
    public ExecutorService asyncExecutor() {
      return mock(ExecutorService.class);
    }

    @Override
    public Optional<SamplerHost> samplerHost() {
      return Optional.of(samplerHost);
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void verbose(String message) {
    }

    @Override
    public void reportError(String context, Throwable failure) {
      errors.add(context);
    }
  }
}
