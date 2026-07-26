package art.arcane.react.content.PAPI;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerPerWorldTickTime;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Set;
import java.util.UUID;

public class ReactPlaceholderPublisher extends TickedObject implements ReactPlaceholderSource.SamplerReader {
  public static final String ID = "placeholders";
  private static final long PUBLISH_INTERVAL_MS = 1000L;

  private transient final ReactPlaceholderSource source;

  public ReactPlaceholderPublisher(ReactPlaceholderSource source) {
    super("react", ID, PUBLISH_INTERVAL_MS);
    this.source = source;
  }

  @Override
  public void onTick() {
    source.publish(this, SamplerPerWorldTickTime.snapshotMeansMs());
  }

  @Override
  public Set<String> ids() {
    Registry<Sampler> samplers = samplers();
    return samplers == null ? Set.of() : samplers.ids();
  }

  @Override
  public double sample(String samplerId) {
    Registry<Sampler> samplers = samplers();

    if (samplers == null) {
      return Double.NaN;
    }

    Sampler sampler = samplers.get(samplerId);
    return sampler == null ? Double.NaN : sampler.sample();
  }

  public void seedOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID playerId = player.getUniqueId();
      J.runEntity(player, () -> source.trackPlayerWorld(playerId, player.getWorld().getUID()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    source.trackPlayerWorld(e.getPlayer().getUniqueId(), e.getPlayer().getWorld().getUID());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    source.trackPlayerWorld(e.getPlayer().getUniqueId(), e.getPlayer().getWorld().getUID());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    source.trackPlayerWorld(e.getPlayer().getUniqueId(), e.getRespawnLocation().getWorld().getUID());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    source.releasePlayer(e.getPlayer().getUniqueId());
  }

  private Registry<Sampler> samplers() {
    SampleController controller = React.controller(SampleController.class);
    return controller == null ? null : controller.getSamplers();
  }
}
