package art.arcane.react.platform;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ActionBackend;
import art.arcane.react.api.web.ActionDispatcher;
import art.arcane.react.api.web.ActionTicketRegistry;
import art.arcane.react.api.web.AuditLog;
import art.arcane.react.api.web.AuditWebMutationReporter;
import art.arcane.react.api.web.OwnerListing;
import art.arcane.react.api.web.ConsoleCommandDispatcher;
import art.arcane.react.api.web.ControlBackend;
import art.arcane.react.api.web.PlayerBackend;
import art.arcane.react.api.web.PluginApiPackRoutes;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.WorldBackend;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IntegrationStatusDto;
import art.arcane.react.api.web.dto.OnlinePlayerDto;
import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.web.network.NetworkHub;
import art.arcane.react.api.web.pairing.PairingNotices;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.core.telemetry.runtime.RegionalHealth;
import io.javalin.http.NotFoundResponse;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * What the React Web server reads from the platform it runs on. Everything returns plain data; a platform without a
 * subsystem keeps the default, so its routes answer with the usual JSON shape and empty lists.
 */
public interface WebHost {
  /** {@code web.toml}, or empty when there is no configuration source and the defaults apply. */
  Optional<WebConfiguration> loadConfiguration() throws IOException;

  WebMetricsSource metrics();

  /** The server's message of the day; one of the inputs for the server name pairing links show. */
  default String motd() {
    return "";
  }

  /** The server's root folder, whose name is the last fallback for the server name; null uses the data folder. */
  default File serverFolder() {
    return null;
  }

  /** {@code host:port} the game listens on, reported as {@code serverId}. */
  default String serverAddress() {
    return "";
  }

  /** The server software and version line shown on the environment page. */
  default String serverVersion() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? "" : platform.platformName() + " " + platform.minecraftVersion();
  }

  default boolean foliaThreading() {
    return false;
  }

  /** The logger whose lines {@code /api/v1/logs} and {@code /ws/logs} stream, next to the Log4j root capture. */
  default Logger consoleLogger() {
    return Logger.getLogger("");
  }

  default ControlBackend features() {
    return EmptyControls.INSTANCE;
  }

  default ControlBackend tweaks() {
    return EmptyControls.INSTANCE;
  }

  default List<ChunkGridExporter> heatmapExporters() {
    return List.of();
  }

  default HeatmapChunkSampler heatmapSampler() {
    return (exporter, world, centerX, centerZ, radius) -> {
      throw new NotFoundResponse("Heatmaps are not available on this platform");
    };
  }

  default WorldBackend worlds() {
    return new WorldBackend() {
      @Override
      public List<WorldDto> list() {
        return List.of();
      }

      @Override
      public WorldDto update(String worldKey, Double budgetMs, Double panicMs, Double releaseMs) {
        throw new NotFoundResponse("Unknown world: " + worldKey);
      }
    };
  }

  default PlayerBackend players() {
    return new PlayerBackend() {
      @Override
      public List<OnlinePlayerDto> list() {
        return List.of();
      }

      @Override
      public TeleportResult queueTeleport(UUID playerId, String worldKey, int blockX, int blockZ) {
        return new TeleportResult(TeleportStatus.PLAYER_OFFLINE, "");
      }
    };
  }

  default ActionBackend actions() {
    return new ActionBackend() {
      @Override
      public List<ActionDescriptorDto> list() {
        return List.of();
      }

      @Override
      public ActionDescriptorDto get(String id) {
        return null;
      }
    };
  }

  /** Queues an action; {@code tickets} records its progress for {@code /api/v1/actions/tickets/{id}}. */
  default ActionDispatcher actionDispatcher(ActionTicketRegistry tickets) {
    return (id, params) -> {
      throw new NotFoundResponse("Unknown action: " + id);
    };
  }

  default ConsoleCommandDispatcher console() {
    return command -> {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      return platform != null && platform.dispatchConsoleCommand(command);
    };
  }

  default WebMutationReporter mutationReporter(AuditLog auditLog) {
    return new AuditWebMutationReporter(auditLog);
  }

  default IncidentScore incidentScore() {
    return IncidentScore.EMPTY;
  }

  /** UNKNOWN, DISABLED, NORMAL or ACTIVE. */
  default String incidentState() {
    return "UNKNOWN";
  }

  /** Newest first. */
  default List<IncidentRecord> incidents(int limit) {
    return List.of();
  }

  /** Host telemetry (CPU, memory, disks, network); null reports every section empty. */
  default EnvironmentDto hostEnvironment() {
    return null;
  }

  default List<IntegrationStatusDto> integrations() {
    return List.of();
  }

  default List<String> integrationTimeline(int limit) {
    return List.of();
  }

  /** Every loaded plugin or mod, enabled or not. */
  default List<OwnerListing.Owner> owners() {
    return List.of();
  }

  /** The {@code /api/v1/metrics/runtime} body. */
  default Object runtimeMetrics() {
    return RuntimeMetricsUnavailable.INSTANCE;
  }

  default PluginApiPackRoutes pluginApiPacks(WebMutationReporter reporter) {
    return PluginApiPackRoutes.NONE;
  }

  default ConfigSectionDto[] configTree() {
    return new ConfigSectionDto[0];
  }

  /** Applies and saves one config value on the server thread; false when the path is unknown or the value refused. */
  default boolean applyConfig(String path, Object value) {
    return false;
  }

  /** Applies a preset; the number of values it changed, or -1 when the preset is unknown. */
  default int applyPreset(String name) {
    return -1;
  }

  /** On a proxy, the network of servers behind it; React Web then serves the network routes. */
  default Optional<NetworkHub> network() {
    return Optional.empty();
  }

  /** Tells the player who made a pairing link, if online, that a browser could not use it. */
  default void tellPairingIssuer(UUID issuerId, String linkLabel, PairingNotices.Refusal refusal, String origin) {
  }

  record IncidentScore(long sampledAtMs, double score, boolean available, List<IncidentEvidence> evidence) {
    public static final IncidentScore EMPTY = new IncidentScore(0L, 0D, false, List.of());

    public IncidentScore {
      evidence = List.copyOf(evidence);
    }

    public static IncidentScore of(SamplerIncidentScore.IncidentScoreSnapshot snapshot) {
      return snapshot == null ? EMPTY
          : new IncidentScore(snapshot.sampledAtMs(), snapshot.score(), snapshot.available(), snapshot.evidence());
    }

    /** Samples the incident-score sampler registered with {@code samples}; empty when it is not registered. */
    public static IncidentScore sample(SampleController samples) {
      Sampler sampler = samples == null ? null : samples.getSampler(SamplerIncidentScore.ID);
      if (!(sampler instanceof SamplerIncidentScore incident)) {
        return EMPTY;
      }
      incident.sample();
      return of(incident.snapshot());
    }
  }

  record RuntimeMetricsUnavailable(boolean regionalAvailable, String regionalUnavailableReason, boolean jfrAvailable,
                                   String jfrUnavailableReason, RegionalHealth.Snapshot regional, Jfr jfr) {
    public static final RuntimeMetricsUnavailable INSTANCE = new RuntimeMetricsUnavailable(false, "source-unavailable", false,
        "source-unavailable", RegionalHealth.Snapshot.EMPTY,
        new Jfr(0L, 0L, 10L, false, false, Set.of(), Map.of(), List.of()));

    public record Jfr(long sampledAtMs, long windowMs, long slowThresholdMs, boolean available, boolean complete,
                      Set<String> supportedEvents, Map<String, Object> metrics, List<Object> series) {
    }
  }

  final class EmptyControls implements ControlBackend {
    static final EmptyControls INSTANCE = new EmptyControls();

    private EmptyControls() {
    }

    @Override
    public List<ControlItemDto> list() {
      return List.of();
    }

    @Override
    public ControlItemDto get(String id) {
      return null;
    }

    @Override
    public ControlItemDto setEnabled(String id, boolean enabled) {
      return null;
    }

    @Override
    public ControlItemDto setKnobs(String id, Map<String, Object> knobs) {
      return null;
    }
  }
}
