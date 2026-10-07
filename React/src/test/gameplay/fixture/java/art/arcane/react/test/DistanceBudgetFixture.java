package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.FeatureDynamicViewDistance;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.model.MinMax;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DistanceBudgetFixture {
  private final JavaPlugin plugin;
  private FeatureDynamicViewDistance feature;

  public DistanceBudgetFixture(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean execute(Player player, String[] args) {
    String operation = args[1];
    String playerWorld = player.getWorld().getName();
    boolean scheduled = FoliaScheduler.runGlobal(plugin, () -> {
      String response;
      try {
        switch (operation) {
          case "begin" -> begin();
          case "tick" -> requireFeature().onTick();
          case "recover" -> {
            field(requireFeature(), "playerTickingChunkBudget", Long.MAX_VALUE);
            field(requireFeature(), "playerViewOnlyChunkBudget", Long.MAX_VALUE);
            feature.onTick();
          }
          case "snapshot" -> { }
          case "cleanup" -> close();
          default -> throw new IllegalArgumentException("Unknown distance budget command");
        }
        Map<String, Object> state = snapshot();
        state.put("playerWorld", playerWorld);
        response = "REACT_DISTANCE " + operation + " " + new Gson().toJson(state);
      } catch (Exception failure) {
        plugin.getLogger().log(Level.SEVERE, "Distance budget fixture failed", failure);
        response = "REACT_DISTANCE ERROR " + failure.getMessage();
      }
      String message = response;
      J.runEntity(player, () -> player.sendMessage(message));
    });
    if (!scheduled) {
      player.sendMessage("REACT_DISTANCE ERROR scheduling rejected");
    }
    return true;
  }

  private void begin() throws ReflectiveOperationException {
    if (feature != null) {
      throw new IllegalStateException("Distance budget fixture already active");
    }
    feature = new FeatureDynamicViewDistance();
    field(feature, "playerChunkBudgetEnabled", true);
    field(feature, "playerTickingChunkBudget", 49L);
    field(feature, "playerViewOnlyChunkBudget", 32L);
    field(feature, "budgetRecoveryChecks", 2);
    field(feature, "budgetRecoveryStep", 1);
    field(feature, "warmupSeconds", 0);
    field(feature, "viewDistance", new MinMax(2, 16));
    field(feature, "simulationDistance", new MinMax(2, 10));
    feature.updateCooldownSeconds = 1;
    feature.onActivate();
    feature.onTick();
  }

  private FeatureDynamicViewDistance requireFeature() {
    if (feature == null) {
      throw new IllegalStateException("Distance budget fixture is not active");
    }
    return feature;
  }

  private Map<String, Object> snapshot() throws ReflectiveOperationException {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("active", feature != null);
    state.put("pending", feature != null && ((AtomicBoolean) read(feature, "updateQueued")).get());
    state.put("evaluatedAt", feature == null ? 0L : read(feature, "lastBudgetEvaluationMs"));
    state.put("serverView", Bukkit.getServer().getViewDistance());
    state.put("serverSimulation", Bukkit.getServer().getSimulationDistance());
    state.put("online", Bukkit.getOnlinePlayers().size());
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    state.put("indexReady", index != null && index.isInitialSeedReady());
    state.put("indexedPlayers", index == null ? 0 : index.playerSnapshots().size());
    Sampler sampler = React.sampler(SamplerTickTime.ID);
    double tickTime = sampler == null ? Double.NaN : sampler.sample();
    state.put("tickAvailable", sampler != null && sampler.isSampleAvailable() && Double.isFinite(tickTime));
    if (Double.isFinite(tickTime)) {
      state.put("tickTime", tickTime);
    }
    Map<String, Object> worlds = new LinkedHashMap<>();
    for (World world : Bukkit.getWorlds()) {
      worlds.put(world.getName(), Map.of("view", world.getViewDistance(), "simulation", world.getSimulationDistance()));
    }
    state.put("worlds", worlds);
    return state;
  }

  public void close() {
    if (feature != null) {
      feature.onDeactivate();
      feature = null;
    }
  }

  private static Object read(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void field(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
