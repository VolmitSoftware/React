package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.platform.CapabilityRefusal;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.FurnaceHost;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.VehicleHost;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ViewDistanceHost;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.DropHost;
import art.arcane.react.platform.GameRuleHost;
import art.arcane.react.platform.IncidentHost;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.MapDataHost;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ShorthandHost;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.bukkit.command.BukkitCommandHost;
import art.arcane.react.platform.PlatformCapability;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.content.sampler.BukkitWorldStatsHost;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.PlayerStatsHost;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.platform.RuntimeStatsHost;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.cost.OwnerCostProbe;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.ReactExecutors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public final class BukkitReactPlatform implements ReactPlatform {
  private static final BukkitReactPlatform INSTANCE = new BukkitReactPlatform();

  private final PlatformScheduler scheduler = new BukkitPlatformScheduler();
  private final SamplerHost samplerHost = new BukkitSamplerHost();
  private final RegistryHost registryHost = new BukkitRegistryHost();
  private final TickStatsHost tickStatsHost = new BukkitTickStatsHost();
  private final WorldStatsHost worldStatsHost = new BukkitWorldStatsHost();
  private final PlayerStatsHost playerStatsHost = new BukkitPlayerStatsHost();
  private final RuntimeStatsHost runtimeStatsHost = new BukkitRuntimeStatsHost();
  private final CommandHost commandHost = new BukkitCommandHost();
  private final LanguageHost languageHost = new BukkitLanguageHost();
  private final ComponentHost componentHost = new BukkitComponentHost();
  private final WorldHost worldHost = new BukkitWorldHost();
  private final EntityHost entityHost = new BukkitEntityHost();
  private final HopperHost hopperHost = new BukkitHopperHost();
  private final ViewDistanceHost viewDistanceHost = new BukkitViewDistanceHost();
  // Batch A: items
  private final ItemHost itemHost = new BukkitItemHost();
  private final VehicleHost vehicleHost = new BukkitVehicleHost();
  private final EntityEventHost entityEventHost = new BukkitEntityEventHost();
  // Batch A: ai
  private final EntityActivityHost entityActivityHost = new BukkitEntityActivityHost();
  // Batch A: mobs
  private volatile MobStackHost mobStackHost;
  private final ChunkHost chunkHost = new BukkitChunkHost();
  private final PlayerHost playerHost = new BukkitPlayerHost();
  private final IncidentHost incidentHost = new BukkitIncidentHost();
  private final GameRuleHost gameRuleHost = new BukkitGameRuleHost();
  private final DropHost dropHost = new BukkitDropHost();
  private final ShorthandHost shorthandHost = new BukkitShorthandHost();
  private final MapDataHost mapDataHost = new BukkitMapDataHost();

  private final BlockHost blockHost = new BukkitBlockHost();
  private final GrowthHost growthHost = new BukkitGrowthHost();
  private final RedstoneHost redstoneHost = new BukkitRedstoneHost();
  private final SpawnHost spawnHost = new BukkitSpawnHost();
  private final ExplosionHost explosionHost = new BukkitExplosionHost();
  private final FurnaceHost furnaceHost = new BukkitFurnaceHost();

  public static BukkitReactPlatform instance() {
    return INSTANCE;
  }

  @Override
  public PlatformKind kind() {
    return PlatformKind.BUKKIT;
  }

  @Override
  public String platformName() {
    try {
      String name = Bukkit.getName();
      return name == null || name.isBlank() ? "Bukkit" : name;
    } catch (Throwable ignored) {
      return "Bukkit";
    }
  }

  @Override
  public String minecraftVersion() {
    try {
      String version = Bukkit.getBukkitVersion();
      if (version == null) {
        return "";
      }
      int dash = version.indexOf('-');
      return dash < 0 ? version : version.substring(0, dash);
    } catch (Throwable ignored) {
      return "";
    }
  }

  @Override
  public File dataFolder() {
    React react = React.instance;
    return react == null ? null : react.getDataFolder();
  }

  @Override
  public PlatformScheduler scheduler() {
    return scheduler;
  }

  @Override
  public ExecutorService asyncExecutor() {
    return ReactExecutors.async();
  }

  @Override
  public void info(String message) {
    React.info(message);
  }

  @Override
  public void warn(String message) {
    React.warn(message);
  }

  @Override
  public void warn(String message, Throwable failure) {
    React.warn(message, failure);
  }

  @Override
  public void verbose(String message) {
    React.verbose(message);
  }

  @Override
  public void verbose(Supplier<String> message) {
    React.verbose(message);
  }

  @Override
  public void reportError(String context, Throwable failure) {
    React.reportError(context, failure);
  }

  @Override
  public void reportError(Throwable failure) {
    React.reportError(failure);
  }

  @Override
  public Optional<CapabilityRefusal> unavailable(PlatformCapability capability) {
    return Optional.empty();
  }

  @Override
  public boolean dispatchConsoleCommand(String command) {
    Boolean dispatched = J.sResult(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    return Boolean.TRUE.equals(dispatched);
  }

  @Override
  public Optional<ReactPlayer> player(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    Player player = Bukkit.getPlayer(id);
    return player == null ? Optional.empty() : Optional.of(new BukkitReactPlayer(player));
  }

  @Override
  public Optional<ReactPlayer> player(Object handle) {
    if (handle instanceof ReactPlayer player) {
      return Optional.of(player);
    }
    return handle instanceof Player player ? Optional.of(new BukkitReactPlayer(player)) : Optional.empty();
  }

  @Override
  public Optional<ReactWorld> world(String name) {
    if (name == null) {
      return Optional.empty();
    }
    World world = Bukkit.getWorld(name);
    return world == null ? Optional.empty() : Optional.of(new BukkitReactWorld(world));
  }

  @Override
  public Optional<ReactWorld> world(Object handle) {
    if (handle instanceof ReactWorld world) {
      return Optional.of(world);
    }
    return handle instanceof World world ? Optional.of(new BukkitReactWorld(world)) : Optional.empty();
  }

  @Override
  public Optional<ReactWebServer> webServer() {
    React react = React.instance;
    return react == null ? Optional.empty() : Optional.ofNullable(React.controller(WebController.class));
  }

  // Sampler hosts

  @Override
  public Optional<SamplerHost> samplerHost() {
    return Optional.of(samplerHost);
  }

  @Override
  public Optional<RegistryHost> registryHost() {
    return Optional.of(registryHost);
  }

  @Override
  public Optional<TickStatsHost> tickStatsHost() {
    return Optional.of(tickStatsHost);
  }

  @Override
  public Optional<WorldStatsHost> worldStatsHost() {
    return Optional.of(worldStatsHost);
  }

  @Override
  public Optional<PlayerStatsHost> playerStatsHost() {
    return Optional.of(playerStatsHost);
  }

  @Override
  public Optional<RuntimeStatsHost> runtimeStatsHost() {
    return Optional.of(runtimeStatsHost);
  }

  @Override
  public Optional<OwnerCostProbe> ownerCostProbe() {
    EventController controller = React.instance == null ? null : React.controller(EventController.class);
    return Optional.ofNullable(controller);
  }

  @Override
  public Optional<ReactCommandSender> commandSender(Object handle) {
    if (handle instanceof ReactCommandSender sender) {
      return Optional.of(sender);
    }
    return handle instanceof CommandSender sender ? Optional.of(new BukkitCommandSender(sender)) : Optional.empty();
  }

  @Override
  public Optional<ComponentHost> componentHost() {
    return Optional.of(componentHost);
  }

  @Override
  public Optional<WorldHost> worldHost() {
    return Optional.of(worldHost);
  }

  @Override
  public Optional<EntityHost> entityHost() {
    return Optional.of(entityHost);
  }

  @Override
  public Optional<HopperHost> hopperHost() {
    return Optional.of(hopperHost);
  }

  @Override
  public Optional<ViewDistanceHost> viewDistanceHost() {
    return Optional.of(viewDistanceHost);
  }

  // Batch A: items
  @Override
  public Optional<ItemHost> itemHost() {
    return Optional.of(itemHost);
  }

  @Override
  public Optional<VehicleHost> vehicleHost() {
    return Optional.of(vehicleHost);
  }

  @Override
  public Optional<EntityEventHost> entityEventHost() {
    return Optional.of(entityEventHost);
  }

  // Batch A: ai
  @Override
  public Optional<EntityActivityHost> entityActivityHost() {
    return Optional.of(entityActivityHost);
  }

  // Batch A: mobs
  @Override
  public Optional<MobStackHost> mobStackHost() {
    MobStackHost host = mobStackHost;
    if (host == null) {
      synchronized (this) {
        host = mobStackHost;
        if (host == null) {
          host = new BukkitMobStackHost(new GlossEntityOverlayIntegration(), MobStateAccess.detect());
          mobStackHost = host;
        }
      }
    }
    return Optional.of(host);
  }

  // Chunks, players, world rules and maps

  @Override
  public Optional<ChunkHost> chunkHost() {
    return Optional.of(chunkHost);
  }

  @Override
  public Optional<PlayerHost> playerHost() {
    return Optional.of(playerHost);
  }

  @Override
  public Optional<IncidentHost> incidentHost() {
    return Optional.of(incidentHost);
  }

  @Override
  public Optional<GameRuleHost> gameRuleHost() {
    return Optional.of(gameRuleHost);
  }

  @Override
  public Optional<DropHost> dropHost() {
    return Optional.of(dropHost);
  }

  @Override
  public Optional<ShorthandHost> shorthandHost() {
    return Optional.of(shorthandHost);
  }

  @Override
  public Optional<IntegrationMetricsHost> integrationMetricsHost() {
    return Optional.of(BukkitIntegrationMetricsHost.INSTANCE);
  }

  @Override
  public Optional<ChunkHeatmapHost> chunkHeatmapHost() {
    return Optional.of(BukkitChunkHeatmapHost.INSTANCE);
  }

  @Override
  public Optional<MapAnchorHost> mapAnchorHost() {
    return Optional.of(BukkitMapAnchorHost.INSTANCE);
  }

  @Override
  public Optional<MapDataHost> mapDataHost() {
    return Optional.of(mapDataHost);
  }

  @Override
  public Optional<CommandHost> commandHost() {
    return Optional.of(commandHost);
  }

  @Override
  public Optional<LanguageHost> languageHost() {
    return Optional.of(languageHost);
  }

  // Block, redstone, spawn and explosion hosts

  @Override
  public Optional<BlockHost> blockHost() {
    return Optional.of(blockHost);
  }

  @Override
  public Optional<GrowthHost> growthHost() {
    return Optional.of(growthHost);
  }

  @Override
  public Optional<RedstoneHost> redstoneHost() {
    return Optional.of(redstoneHost);
  }

  @Override
  public Optional<SpawnHost> spawnHost() {
    return Optional.of(spawnHost);
  }


  @Override
  public Optional<ExplosionHost> explosionHost() {
    return Optional.of(explosionHost);
  }

  @Override
  public Optional<FurnaceHost> furnaceHost() {
    return Optional.of(furnaceHost);
  }
}
