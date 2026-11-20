package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;

public class SamplerBukkitPendingTasks extends ReactCachedSampler {
  public static final String ID = "bukkit-pending-tasks";
  private transient volatile boolean schedulerUnsupported;

  public SamplerBukkitPendingTasks() {
    super(ID, 2000);
  }

  @Override
  public String getName() {
    return OwnerNoun.pick("Bukkit Pending Tasks", "Pending Tasks");
  }

  @Override
  public String getIcon() {
    return "minecraft:repeater";
  }

  @Override
  public void start() {
    super.start();
    schedulerUnsupported = false;
  }

  @Override
  public double onSample() {
    if (schedulerUnsupported) {
      return 0D;
    }

    try {
      TickStatsHost host = SamplerHosts.ticks().orElse(null);
      if (host == null) {
        schedulerUnsupported = true;
        return 0D;
      }
      return host.pendingSchedulerTasks();
    } catch (UnsupportedOperationException ex) {
      schedulerUnsupported = true;
      return 0D;
    }
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " tasks";
  }

  @Override
  public boolean isSampleAvailable() {
    return !schedulerUnsupported;
  }
}
