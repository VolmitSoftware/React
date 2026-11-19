package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.platform.CapabilityRefusal;
import art.arcane.react.platform.PlatformCapability;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.ReactExecutors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public final class BukkitReactPlatform implements ReactPlatform {
  private static final BukkitReactPlatform INSTANCE = new BukkitReactPlatform();

  private final PlatformScheduler scheduler = new BukkitPlatformScheduler();

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
}
