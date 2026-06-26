/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.model.MinMax;
import art.arcane.react.util.project.world.WorldDistanceSupport;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Dynamic View Distance feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureDynamicViewDistance extends ReactFeature implements Listener {
  public static final String ID = "dynamic-view-distance";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for update cooldown in dynamic view distance (seconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  public int updateCooldownSeconds = 120;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Grace period after activation before dynamic view distance touches any world (seconds).", impact = "Prevents startup tick spikes from slamming view distance to the floor before the server settles; raise it if your server takes longer to warm up.")
  private int warmupSeconds = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax viewDistance = new MinMax(6, 16);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax simulationDistance = new MinMax(4, 10);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax lerpTickTime = new MinMax(45, 140);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Interpolation range used by dynamic view distance to map observed load into target values.", impact = "Wider ranges allow broader adaptation; tighter ranges keep adjustments more conservative.")
  private MinMax lerpPlayersOnline = new MinMax(3, 100);
  private transient RollingSequence ttAvg;
  private transient long activatedAtMs;
  private transient Map<World, Long> lastUpdate;
  private transient boolean supportsWorldDistanceSetters;
  private transient boolean warnedRuntimeFailure;
  private transient Method setViewDistanceMethod;
  private transient Method setSimulationDistanceMethod;

  public FeatureDynamicViewDistance() {
    super(ID);
  }

  public boolean updateWorld(World world) throws Exception {
    int vd = world.getViewDistance();
    int sd = world.getSimulationDistance();
    int players = Bukkit.getOnlinePlayers().size();
    double gps = ttAvg.getAverage();

    int newVD = M.min(lerp(lerpTickTime, viewDistance, gps),
        lerp(lerpPlayersOnline, viewDistance, players)).intValue();
    int newSD = M.min(lerp(lerpTickTime, simulationDistance, gps),
        lerp(lerpPlayersOnline, simulationDistance, players)).intValue();
    newSD = Math.min(newSD, newVD);

    List<String> m = new ArrayList<>();
    if (vd != newVD) {
      m.add("View Distance: " + vd + " -> " + newVD);
      setViewDistanceMethod.invoke(world, newVD);
    }

    if (sd != newSD) {
      m.add("Simulation Distance: " + sd + " -> " + newSD);
      setSimulationDistanceMethod.invoke(world, newSD);
    }

    if (!m.isEmpty()) {
      React.verbose(world.getName() + ": " + String.join(" ", m));
      return true;
    }

    return false;
  }

  public static double lerp(MinMax range, MinMax output, double inRange) {
    return Math.max(Math.min(output.getMax(),
            M.lerp(output.getMax(), output.getMin(), M.lerpInverse(range.getMin(), range.getMax(), inRange))),
        output.getMin());
  }

  @Override
  public void onActivate() {
    supportsWorldDistanceSetters = WorldDistanceSupport.supportsWorldDistanceSetters();
    warnedRuntimeFailure = false;
    if (!supportsWorldDistanceSetters) {
      setEnabled(false);
      React.warn("Dynamic View Distance disabled: this server software does not expose world distance setters. Use Paper/Purpur to enable this feature.");
      return;
    }
    try {
      setViewDistanceMethod = World.class.getMethod("setViewDistance", int.class);
      setSimulationDistanceMethod = World.class.getMethod("setSimulationDistance", int.class);
    } catch (NoSuchMethodException e) {
      supportsWorldDistanceSetters = false;
      setEnabled(false);
      React.warn("Dynamic View Distance disabled: world distance setters are not resolvable on this server software.");
      return;
    }
    viewDistance.setMax(Math.min(viewDistance.getMax(), Bukkit.getServer().getViewDistance()));
    simulationDistance.setMax(Math.min(simulationDistance.getMax(), Bukkit.getServer().getSimulationDistance()));
    ttAvg = new RollingSequence(10);
    ttAvg.put(0);
    lastUpdate = new HashMap<>();
    activatedAtMs = System.currentTimeMillis();
  }

  @Override
  public void onDeactivate() {
    supportsWorldDistanceSetters = false;
  }

  @Override
  public int getTickInterval() {
    return supportsWorldDistanceSetters ? 1000 : 0;
  }

  @Override
  public void onTick() {
    if (!supportsWorldDistanceSetters || ttAvg == null || lastUpdate == null) {
      return;
    }
    ttAvg.put(React.sampler(SamplerTickTime.ID).sample());
    long now = System.currentTimeMillis();
    if (now - activatedAtMs < Math.max(0, warmupSeconds) * 1000L) {
      return;
    }
    long cooldownMs = Math.max(1L, updateCooldownSeconds) * 1000L;
    for (World i : Bukkit.getWorlds()) {
      if (lastUpdate.getOrDefault(i, 0L) < now - cooldownMs) {
        try {
          if (updateWorld(i)) {
            lastUpdate.put(i, now);
          }
        } catch (Throwable e) {
          if (!warnedRuntimeFailure) {
            warnedRuntimeFailure = true;
            setEnabled(false);
            React.warn("Dynamic View Distance disabled due to runtime incompatibility: " + e.getClass().getSimpleName() + ": " + e.getMessage());
          }
          return;
        }
      }
    }
  }
}
