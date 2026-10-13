package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.NearbyEntitySampler;
import com.google.gson.Gson;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class TelemetryFixture {
  private static final NearbyEntitySampler.Request SAMPLE = new NearbyEntitySampler.Request("telemetry-fixture", 48D, 32D, 1);
  private final JavaPlugin plugin;
  private final List<Entity> spawned = new ArrayList<>();
  private final AtomicLong samples = new AtomicLong();
  private ScheduledTask task;

  public TelemetryFixture(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean execute(Player player, String[] args) {
    try {
      switch (args[1]) {
        case "begin" -> begin(player, args.length > 2 ? Integer.parseInt(args[2]) : 128);
        case "snapshot" -> { }
        case "cleanup" -> close();
        default -> throw new IllegalArgumentException("Unknown telemetry operation");
      }
      player.sendMessage("REACT_TELEMETRY " + args[1] + " " + new Gson().toJson(snapshot()));
    } catch (Exception failure) {
      plugin.getLogger().log(Level.SEVERE, "Telemetry fixture failed", failure);
      player.sendMessage("REACT_TELEMETRY ERROR " + failure.getMessage());
    }
    return true;
  }

  private void begin(Player player, int count) {
    if (task != null || !spawned.isEmpty() || count < 1 || count > 2_048) {
      throw new IllegalStateException("Telemetry fixture requires an idle fixture and 1 to 2048 entities");
    }
    samples.set(0L);
    Location origin = player.getLocation();
    for (int index = 0; index < count; index++) {
      Location position = origin.clone().add(2D + (index % 16) * 0.25D, 0D, 2D + (index / 16) * 0.02D);
      ArmorStand stand = player.getWorld().spawn(position, ArmorStand.class, entity -> {
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setPersistent(false);
        entity.setVisible(false);
      });
      spawned.add(stand);
    }
    task = player.getScheduler().runAtFixedRate(plugin, ignored -> sample(player), null, 1L, 1L);
  }

  private void sample(Player player) {
    NearbyEntitySampler sampler = React.controller(EntityController.class).getNearbyEntitySampler();
    samples.addAndGet(sampler.sample(player, SAMPLE).size());
  }

  private Map<String, Object> snapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("spawned", spawned.size());
    snapshot.put("samples", samples.get());
    snapshot.put("active", task != null);
    snapshot.put("folia", J.isFoliaThreading());
    HistoryController history = React.controller(HistoryController.class);
    MetricSnapshot captured = history.latestObserved();
    snapshot.put("history", Map.of("sequence", captured.sequence(),
        "capturedAtMs", captured.capturedAtMs(), "values", captured.values()));
    SampleController controller = React.controller(SampleController.class);
    List<String> registered = new ArrayList<>();
    for (Sampler sampler : controller.getSamplers().all()) {
      registered.add(sampler.getId());
    }
    snapshot.put("registered", registered);
    return snapshot;
  }

  public void close() {
    ScheduledTask active = task;
    task = null;
    if (active != null) {
      active.cancel();
    }
    for (Entity entity : spawned) {
      J.runEntity(entity, entity::remove);
    }
    spawned.clear();
  }
}
