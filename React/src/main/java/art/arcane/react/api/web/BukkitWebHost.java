package art.arcane.react.api.web;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IntegrationStatusDto;
import art.arcane.react.api.web.heatmap.BukkitHeatmapChunkSampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapChunkSampler;
import art.arcane.react.api.web.pairing.PairingNotices;
import art.arcane.react.api.web.resource.PluginApiPackResource;
import art.arcane.react.api.web.resource.RuntimeMetricsResource;
import art.arcane.react.content.feature.FeatureIncidentMode;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.controller.RuntimeTelemetryController;
import art.arcane.react.core.controller.TelemetryController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.platform.WebHost;
import art.arcane.react.platform.WebMetricsSource;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import io.javalin.http.NotFoundResponse;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** React Web's view of the Bukkit server: React's controllers and registries, read when a request needs them. */
public final class BukkitWebHost implements WebHost {
  private final WebController controller;
  private final WebMetricsSource metrics;
  private final HeatmapChunkSampler heatmapSampler = new BukkitHeatmapChunkSampler();

  public BukkitWebHost(WebController controller) {
    this.controller = controller;
    this.metrics = new BukkitWebMetricsSource(controller::getSampleController, controller::getHistoryController, new MetricsSerializer());
  }

  @Override
  public Optional<WebConfiguration> loadConfiguration() throws IOException {
    if (React.instance == null) {
      return Optional.empty();
    }
    File canonical = React.instance.getDataFile("web.toml");
    File obsoleteJson = React.instance.getDataFile("web.json");
    WebConfigurationSchema.requireCurrent(canonical, obsoleteJson);
    WebConfigurationSchema.moveCorsOrigins(canonical).ifPresent(move -> React.info("web.toml: corsOrigins moved to allowedOrigins "
        + move.moved() + (move.dropped().isEmpty() ? "" : "; dropped " + move.dropped() + ", which allowedOrigins does not accept")));
    return Optional.of(ConfigFileSupport.load(
        canonical,
        null,
        WebConfiguration.class,
        new WebConfiguration(),
        false,
        "web-config",
        "Created missing config [web.toml] from defaults."
    ));
  }

  @Override
  public WebMetricsSource metrics() {
    return metrics;
  }

  private static Server server() {
    return React.instance == null ? null : React.instance.getServer();
  }

  @Override
  public String motd() {
    Server server = server();
    return server == null ? "" : server.getMotd();
  }

  @Override
  public File serverFolder() {
    Server server = server();
    return server == null ? null : server.getWorldContainer();
  }

  @Override
  public String serverAddress() {
    Server server = server();
    return server == null ? "" : server.getIp() + ":" + server.getPort();
  }

  @Override
  public String serverVersion() {
    Server server = server();
    return server == null ? "" : server.getVersion();
  }

  @Override
  public boolean foliaThreading() {
    return J.isFoliaThreading();
  }

  @Override
  public Logger consoleLogger() {
    Server server = server();
    return server == null ? Logger.getLogger("") : server.getLogger();
  }

  @Override
  public ControlBackend features() {
    return new RegistryControlBackend<Feature>(
        "feature",
        () -> {
          FeatureController fc = controller.getFeatureController();
          return fc == null || fc.getFeatures() == null ? List.of() : fc.getFeatures().all();
        },
        id -> {
          FeatureController fc = controller.getFeatureController();
          return fc == null || fc.getFeatures() == null ? null : fc.getFeatures().get(id);
        },
        Feature::isEnabled,
        new ControlSerializer(),
        WebController::applyConfigValueOnServerThread
    );
  }

  @Override
  public ControlBackend tweaks() {
    return new RegistryControlBackend<Tweak>(
        "tweak",
        () -> {
          TweakController tc = controller.getTweakController();
          return tc == null || tc.getTweaks() == null ? List.of() : tc.getTweaks().all();
        },
        id -> {
          TweakController tc = controller.getTweakController();
          return tc == null || tc.getTweaks() == null ? null : tc.getTweaks().get(id);
        },
        Tweak::isEnabled,
        new ControlSerializer(),
        WebController::applyConfigValueOnServerThread
    );
  }

  @Override
  public List<ChunkGridExporter> heatmapExporters() {
    FeatureController fc = controller.getFeatureController();
    if (fc == null || fc.getFeatures() == null) {
      return List.of();
    }
    List<ChunkGridExporter> out = new ArrayList<>();
    for (Feature feature : fc.getFeatures().all()) {
      if (feature instanceof ChunkGridExporter exporter) {
        out.add(exporter);
      }
    }
    return out;
  }

  @Override
  public HeatmapChunkSampler heatmapSampler() {
    return heatmapSampler;
  }

  @Override
  public WorldBackend worlds() {
    return new FeatureWorldBackend();
  }

  @Override
  public PlayerBackend players() {
    return new BukkitPlayerBackend();
  }

  @Override
  public ActionBackend actions() {
    return new RegistryActionBackend(
        () -> {
          ActionController ac = React.instance != null ? React.controller(ActionController.class) : null;
          return ac == null || ac.getActions() == null ? List.of() : ac.getActions().all();
        },
        id -> {
          ActionController ac = React.instance != null ? React.controller(ActionController.class) : null;
          return ac == null || ac.getActions() == null ? null : ac.getActions().get(id);
        },
        new ActionParamSerializer()
    );
  }

  @Override
  public ActionDispatcher actionDispatcher(ActionTicketRegistry tickets) {
    return (id, params) -> {
      Action<?> action = React.action(id);
      if (action == null) {
        throw new NotFoundResponse("Unknown action: " + id);
      }
      ActionParams coerced = new ActionParamCoercer().coerce(action, params);
      ActionTicket<?> ticket = action.createForceful(coerced);
      String ticketId = track(tickets, ticket);
      ticket.queue();
      return new ActionDispatcher.DispatchResult(ticketId, "queued");
    };
  }

  static <T extends ActionParams> String track(ActionTicketRegistry tickets, ActionTicket<T> ticket) {
    String id = tickets.register();
    ticket.onStart(started -> tickets.update(id, ActionTicketRegistry.State.RUNNING, ""));
    ticket.onTerminal(finished -> {
      if (finished.isFailed()) {
        Throwable failure = finished.getFailure();
        String message = failure == null || failure.getMessage() == null
            ? "Action failed"
            : failure.getMessage();
        tickets.update(id, ActionTicketRegistry.State.FAILED, message);
        return;
      }
      tickets.update(id, ActionTicketRegistry.State.DONE, completedMessage(finished));
    });
    return id;
  }

  private static <T extends ActionParams> String completedMessage(ActionTicket<T> ticket) {
    try {
      return ticket.getAction().getCompletedMessage(ticket);
    } catch (Throwable ignored) {
      return "";
    }
  }

  @Override
  public ConsoleCommandDispatcher console() {
    return new BukkitConsoleCommandDispatcher();
  }

  @Override
  public WebMutationReporter mutationReporter(AuditLog auditLog) {
    return new BukkitWebMutationReporter(auditLog);
  }

  @Override
  public IncidentScore incidentScore() {
    Supplier<SamplerIncidentScore.IncidentScoreSnapshot> override = controller.getIncidentSnapshotSupplier();
    if (override != null) {
      return incidentScore(override.get());
    }
    SamplerIncidentScore sampler = React.instance != null ? React.sampler(SamplerIncidentScore.class) : null;
    if (sampler == null) {
      return IncidentScore.EMPTY;
    }
    sampler.sample();
    return incidentScore(sampler.snapshot());
  }

  public static IncidentScore incidentScore(SamplerIncidentScore.IncidentScoreSnapshot snapshot) {
    if (snapshot == null) {
      return IncidentScore.EMPTY;
    }
    return new IncidentScore(snapshot.sampledAtMs(), snapshot.score(), snapshot.available(), snapshot.evidence());
  }

  @Override
  public String incidentState() {
    FeatureIncidentMode feature = React.instance != null ? React.feature(FeatureIncidentMode.class) : null;
    if (feature == null) {
      return "UNKNOWN";
    }
    if (!feature.isEnabled()) {
      return "DISABLED";
    }
    return feature.isIncidentActive() ? "ACTIVE" : "NORMAL";
  }

  @Override
  public List<IncidentRecord> incidents(int limit) {
    IncidentController incidents = React.instance != null ? React.controller(IncidentController.class) : null;
    return incidents == null ? List.of() : incidents.recent(limit);
  }

  @Override
  public EnvironmentDto hostEnvironment() {
    TelemetryController telemetry = React.instance != null ? React.controller(TelemetryController.class) : null;
    return telemetry == null ? null : telemetry.hostSnapshot().environment();
  }

  private IntegrationController integrationController() {
    IntegrationController ic = controller.getIntegrationController();
    return ic != null ? ic : (React.instance != null ? React.controller(IntegrationController.class) : null);
  }

  @Override
  public List<IntegrationStatusDto> integrations() {
    IntegrationController ic = integrationController();
    return ic == null ? List.of() : integrationDtos(ic.statuses());
  }

  public static List<IntegrationStatusDto> integrationDtos(List<IntegrationController.IntegrationStatus> statuses) {
    List<IntegrationStatusDto> dtos = new ArrayList<>(statuses.size());
    for (IntegrationController.IntegrationStatus status : statuses) {
      IntegrationStatusDto dto = new IntegrationStatusDto();
      dto.pluginId = status.pluginId();
      dto.health = status.health().name();
      dto.protocol = status.protocol();
      dto.lastHeartbeatMs = status.lastHeartbeatMs();
      dto.message = status.message();
      dtos.add(dto);
    }
    return dtos;
  }

  @Override
  public List<String> integrationTimeline(int limit) {
    IntegrationController ic = integrationController();
    return ic == null ? List.of() : ic.recentTimeline(limit);
  }

  @Override
  public Object runtimeMetrics() {
    return new RuntimeMetricsResource(() -> React.instance != null ? React.controller(RuntimeTelemetryController.class) : null)
        .snapshotData();
  }

  @Override
  public PluginApiPackRoutes pluginApiPacks(WebMutationReporter reporter) {
    return new PluginApiPackResource(
        () -> {
          PluginApiPackController packs = controller.getPluginApiPackController();
          return packs != null ? packs : (React.instance != null ? React.controller(PluginApiPackController.class) : null);
        },
        reporter
    );
  }

  @Override
  public ConfigSectionDto[] configTree() {
    return new ConfigTreeSerializer().serialize(ReactConfiguration.get());
  }

  @Override
  public boolean applyConfig(String path, Object value) {
    return WebController.applyConfigValueOnServerThread(path, value);
  }

  @Override
  public int applyPreset(String name) {
    return WebController.applyPresetOnServerThread(name);
  }

  @Override
  public void tellPairingIssuer(UUID issuerId, String linkLabel, PairingNotices.Refusal refusal, String origin) {
    if (React.instance == null) {
      return;
    }
    MessageKey why = refusal == PairingNotices.Refusal.EXPIRED ? CommandMessages.WEB_REFUSED_EXPIRED : CommandMessages.WEB_REFUSED_ORIGIN;
    MessageArgument[] arguments = refusal == PairingNotices.Refusal.ORIGIN
        ? new MessageArgument[]{MessageArgument.untrusted("origin", origin)}
        : new MessageArgument[0];
    J.s(() -> {
      Player player = Bukkit.getPlayer(issuerId);
      if (player == null) {
        return;
      }
      J.runEntity(player, () -> ReactLanguage.sendPrefixed(
          player,
          CommandMessages.WEB_PAIR_REFUSED,
          MessageArgument.untrusted("label", linkLabel),
          MessageArgument.untrusted("reason", ReactLanguage.plain(why, arguments))
      ));
    });
  }
}
