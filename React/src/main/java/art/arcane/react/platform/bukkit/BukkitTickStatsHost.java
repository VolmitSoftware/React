package art.arcane.react.platform.bukkit;

import art.arcane.react.content.sampler.TickTimeSource;
import art.arcane.react.content.sampler.TickTimeSources;
import art.arcane.react.platform.TickStatsHost;
import org.bukkit.Bukkit;

import java.util.Optional;

final class BukkitTickStatsHost implements TickStatsHost {
  @Override
  public Optional<TickTimeSource> tickTimeSource() {
    return Optional.ofNullable(TickTimeSources.detect());
  }

  @Override
  public int pendingSchedulerTasks() {
    return Bukkit.getScheduler().getPendingTasks().size();
  }
}
