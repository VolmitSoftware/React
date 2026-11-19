package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

final class BukkitPlatformScheduler implements PlatformScheduler {
  @Override
  public boolean isPrimaryThread() {
    return FoliaScheduler.isPrimaryThread();
  }

  @Override
  public boolean isRegionized() {
    return FoliaScheduler.isFoliaThreading(Bukkit.getServer());
  }

  @Override
  public void forceRegionized() {
    FoliaScheduler.forceFoliaThreading(Bukkit.getServer());
  }

  @Override
  public boolean isStopping() {
    return FoliaScheduler.isStopping(Bukkit.getServer());
  }

  @Override
  public boolean isActive() {
    React react = React.instance;
    return react != null && react.isEnabled();
  }

  @Override
  public boolean isReady() {
    React react = React.instance;
    return react != null && react.isReady();
  }

  @Override
  public void queue(Runnable task) {
    React react = React.instance;
    if (!react.isReady()) {
      react.getPrejobs().add(task);
      return;
    }

    React.controller(JobController.class).queue(task);
  }

  @Override
  public boolean isOwnedByCurrentRegion(Object handle) {
    if (handle instanceof Entity entity) {
      return FoliaScheduler.isOwnedByCurrentRegion(entity);
    }
    if (handle instanceof Location location) {
      return FoliaScheduler.isOwnedByCurrentRegion(location);
    }
    return false;
  }

  @Override
  public boolean isOwnedByCurrentRegion(ReactWorld world, int chunkX, int chunkZ) {
    Location anchor = anchor(world, chunkX, chunkZ);
    return anchor != null && FoliaScheduler.isOwnedByCurrentRegion(anchor);
  }

  @Override
  public boolean isPlaced(Object location) {
    return location instanceof Location bukkitLocation && bukkitLocation.getWorld() != null;
  }

  @Override
  public boolean global(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      return FoliaScheduler.runGlobal(React.instance, task);
    }
    return FoliaScheduler.runGlobal(React.instance, task, delayTicks);
  }

  @Override
  public boolean region(ReactWorld world, int chunkX, int chunkZ, Runnable task, int delayTicks) {
    Location anchor = anchor(world, chunkX, chunkZ);
    return anchor != null && region(anchor, task, delayTicks);
  }

  @Override
  public boolean region(Object location, Runnable task, int delayTicks) {
    if (!(location instanceof Location bukkitLocation)) {
      return false;
    }
    return FoliaScheduler.runRegion(React.instance, bukkitLocation, task, Math.max(0, delayTicks));
  }

  @Override
  public boolean entity(Object handle, Runnable task, int delayTicks, Runnable retired) {
    if (!(handle instanceof Entity entity)) {
      return false;
    }

    Runnable guarded = guardEntityTask(entity, task, retired);
    if (!isRegionized()) {
      if (delayTicks <= 0) {
        J.sync(guarded);
      } else {
        J.sync(guarded, delayTicks);
      }
      return true;
    }

    if (delayTicks <= 0 && isOwnedByCurrentRegion(entity)) {
      guarded.run();
      return true;
    }

    if (isStopping()) {
      if (retired != null) {
        retired.run();
      }
      return false;
    }

    if (FoliaScheduler.runEntity(React.instance, entity, task, delayTicks, retired)) {
      return true;
    }

    if (retired != null) {
      retired.run();
    }
    return false;
  }

  @Override
  public boolean async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      return FoliaScheduler.runAsync(React.instance, task);
    }
    return FoliaScheduler.runAsync(React.instance, task, delayTicks);
  }

  @Override
  public boolean later(Runnable task, int delayTicks) {
    try {
      if (delayTicks <= 0) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, task);
      } else {
        Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, task, delayTicks);
      }
      return true;
    } catch (IllegalPluginAccessException e) {
      return false;
    }
  }

  @Override
  public void cancelAll() {
    Plugin plugin = React.instance;
    if (plugin == null) {
      return;
    }

    FoliaScheduler.cancelTasks(plugin);

    try {
      Bukkit.getScheduler().cancelTasks(plugin);
    } catch (UnsupportedOperationException | IllegalPluginAccessException ex) {
      React.verbose("Skipping BukkitScheduler#cancelTasks for React on this server.");
    }
  }

  private static Location anchor(ReactWorld world, int chunkX, int chunkZ) {
    if (world == null || !(world.handle() instanceof World bukkitWorld)) {
      return null;
    }
    return new Location(bukkitWorld, (chunkX << 4) + 8.0, 64.0, (chunkZ << 4) + 8.0);
  }

  private static Runnable guardEntityTask(Entity entity, Runnable runnable, Runnable retired) {
    Runnable localized = entity instanceof Player player
        ? () -> LanguageAudience.run(player.getUniqueId(), runnable) : runnable;
    if (retired == null) {
      return localized;
    }

    return () -> {
      boolean active;
      try {
        active = entity.isValid();
      } catch (Throwable ignored) {
        active = false;
      }

      if (active) {
        localized.run();
      } else {
        retired.run();
      }
    };
  }
}
