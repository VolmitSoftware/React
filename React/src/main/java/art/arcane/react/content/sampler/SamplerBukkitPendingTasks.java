package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class SamplerBukkitPendingTasks extends ReactCachedSampler {
  public static final String ID = "bukkit-pending-tasks";
  private transient volatile boolean schedulerUnsupported;

  public SamplerBukkitPendingTasks() {
    super(ID, 2000);
  }

  @Override
  public Material getIcon() {
    return Material.REPEATER;
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
      return Bukkit.getScheduler().getPendingTasks().size();
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
}
