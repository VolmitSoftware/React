package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;

public class SamplerSpawnerSpawns extends ReactCachedRateSampler implements Listener {
  public static final String ID = "spawner-spawns";

  public SamplerSpawnerSpawns() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.SPAWNER;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(SpawnerSpawnEvent event) {
    increment();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(TrialSpawnerSpawnEvent event) {
    increment();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "/s";
  }
}
