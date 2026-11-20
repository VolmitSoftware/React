package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.ViewDistanceHost;
import art.arcane.react.util.project.world.DistanceSupport;
import org.bukkit.Bukkit;
import org.bukkit.World;

final class BukkitViewDistanceHost implements ViewDistanceHost {
  @Override
  public boolean supportsWorldDistance() {
    return DistanceSupport.supportsWorldDistanceSetters();
  }

  @Override
  public int serverViewDistance() {
    return Bukkit.getServer().getViewDistance();
  }

  @Override
  public int serverSimulationDistance() {
    return Bukkit.getServer().getSimulationDistance();
  }

  @Override
  public int viewDistance(ReactWorld world) {
    return ((World) world.handle()).getViewDistance();
  }

  @Override
  public int simulationDistance(ReactWorld world) {
    return ((World) world.handle()).getSimulationDistance();
  }

  @Override
  public void setViewDistance(ReactWorld world, int distance) {
    DistanceSupport.set((World) world.handle(), DistanceSupport.DistanceType.VIEW, distance);
  }

  @Override
  public void setSimulationDistance(ReactWorld world, int distance) {
    DistanceSupport.set((World) world.handle(), DistanceSupport.DistanceType.SIMULATION, distance);
  }
}
