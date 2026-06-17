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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.feature.perworld.ReactScopedPressure;
import art.arcane.react.content.sampler.SamplerPerWorldTickTime;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ConfigDescription("Configuration for Per-World Tick Budget feature. Measures rolling per-world tick-time share, applies engage/release hysteresis against a budget and a panic threshold, and publishes a NORMAL/PRESSURE/PANIC mode per world that other Features can consume cooperatively. Measurement-only by default; existing Features are not rewired in this sprint.")
public class FeaturePerWorldTickBudget extends ReactFeature {
  public static final String ID = "per-world-tick-budget";

  @ConfigDoc(value ="Main evaluation interval for per-world tick budget in milliseconds.", impact = "Lower values react to per-world pressure faster but consume more CPU on the feature tick; higher values smooth the signal but raise time-to-engage.")
  private int tickIntervalMS = 50;
  @ConfigDoc(value ="Per-world rolling mean tick share above which a world enters PRESSURE mode (milliseconds).", impact = "Lower values flag worlds as pressured earlier; higher values reserve PRESSURE for heavier per-world load.")
  private double budgetMs = 35D;
  @ConfigDoc(value ="Per-world rolling mean tick share above which a world enters PANIC mode (milliseconds).", impact = "Lower values escalate sooner; higher values reserve PANIC for severe per-world load.")
  private double panicMs = 50D;
  @ConfigDoc(value ="Sustained evaluation cycles above the threshold required before engaging.", impact = "Higher values absorb short spikes; lower values engage faster but may flap on noisy signals.")
  private int engageSustainTicks = 60;
  @ConfigDoc(value ="Sustained evaluation cycles below the release threshold required before relaxing back toward NORMAL.", impact = "Higher values hold the mode for stability; lower values release sooner.")
  private int releaseSustainTicks = 60;
  @ConfigDoc(value ="Release threshold (milliseconds) the per-world mean must stay under to leave PRESSURE/PANIC, measured below budgetMs to avoid flapping.", impact = "Lower values hold the elevated mode longer for stability; higher values restore NORMAL sooner.")
  private double releaseMs = 28D;

  private transient final Map<UUID, WorldState> stateByWorld = new ConcurrentHashMap<>();

  public FeaturePerWorldTickBudget() {
    super(ID);
  }

  @Override
  public void onActivate() {
    stateByWorld.clear();
    ReactScopedPressure.clearAll();
    ReactScopedPressure.setEnabled(true);
  }

  @Override
  public void onDeactivate() {
    ReactScopedPressure.setEnabled(false);
    ReactScopedPressure.clearAll();
    stateByWorld.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(10, tickIntervalMS);
  }

  @Override
  public void onTick() {
    Set<UUID> live = new HashSet<>();
    for (World world : Bukkit.getWorlds()) {
      UUID worldId = world.getUID();
      live.add(worldId);
      WorldState state = stateByWorld.computeIfAbsent(worldId, ignored -> new WorldState());
      double meanMs = SamplerPerWorldTickTime.meanMsFor(world);
      PerWorldPressure.Mode next = step(state, meanMs);
      ReactScopedPressure.publish(world, new PerWorldPressure(next, meanMs));
    }

    Iterator<Map.Entry<UUID, WorldState>> iterator = stateByWorld.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, WorldState> entry = iterator.next();
      if (live.contains(entry.getKey())) {
        continue;
      }
      iterator.remove();
      World stale = Bukkit.getWorld(entry.getKey());
      if (stale != null) {
        ReactScopedPressure.clear(stale);
      }
    }
  }

  public Map<UUID, PerWorldPressure.Mode> snapshotModes() {
    Map<UUID, PerWorldPressure.Mode> snapshot = new ConcurrentHashMap<>(stateByWorld.size());
    for (Map.Entry<UUID, WorldState> entry : stateByWorld.entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().mode);
    }
    return snapshot;
  }

  public Object2IntOpenHashMap<PerWorldPressure.Mode> snapshotModeHistogram() {
    Object2IntOpenHashMap<PerWorldPressure.Mode> histogram = new Object2IntOpenHashMap<>();
    histogram.defaultReturnValue(0);
    for (WorldState state : stateByWorld.values()) {
      histogram.addTo(state.mode, 1);
    }
    return histogram;
  }

  private PerWorldPressure.Mode step(WorldState state, double meanMs) {
    boolean overPanic = meanMs >= panicMs;
    boolean overBudget = meanMs >= budgetMs;
    boolean underRelease = meanMs <= releaseMs;

    switch (state.mode) {
      case NORMAL -> {
        if (overPanic) {
          state.aboveStreak++;
          state.belowStreak = 0;
          if (state.aboveStreak >= Math.max(1, engageSustainTicks)) {
            state.aboveStreak = 0;
            state.mode = PerWorldPressure.Mode.PANIC;
          }
          return state.mode;
        }
        if (overBudget) {
          state.aboveStreak++;
          state.belowStreak = 0;
          if (state.aboveStreak >= Math.max(1, engageSustainTicks)) {
            state.aboveStreak = 0;
            state.mode = PerWorldPressure.Mode.PRESSURE;
          }
          return state.mode;
        }
        state.aboveStreak = 0;
        state.belowStreak = 0;
        return state.mode;
      }
      case PRESSURE -> {
        if (overPanic) {
          state.aboveStreak++;
          state.belowStreak = 0;
          if (state.aboveStreak >= Math.max(1, engageSustainTicks)) {
            state.aboveStreak = 0;
            state.mode = PerWorldPressure.Mode.PANIC;
          }
          return state.mode;
        }
        if (underRelease) {
          state.belowStreak++;
          state.aboveStreak = 0;
          if (state.belowStreak >= Math.max(1, releaseSustainTicks)) {
            state.belowStreak = 0;
            state.mode = PerWorldPressure.Mode.NORMAL;
          }
          return state.mode;
        }
        state.aboveStreak = 0;
        state.belowStreak = 0;
        return state.mode;
      }
      case PANIC -> {
        if (overPanic) {
          state.aboveStreak = 0;
          state.belowStreak = 0;
          return state.mode;
        }
        if (overBudget) {
          state.belowStreak++;
          state.aboveStreak = 0;
          if (state.belowStreak >= Math.max(1, releaseSustainTicks)) {
            state.belowStreak = 0;
            state.mode = PerWorldPressure.Mode.PRESSURE;
          }
          return state.mode;
        }
        if (underRelease) {
          state.belowStreak++;
          state.aboveStreak = 0;
          if (state.belowStreak >= Math.max(1, releaseSustainTicks)) {
            state.belowStreak = 0;
            state.mode = PerWorldPressure.Mode.NORMAL;
          }
          return state.mode;
        }
        state.aboveStreak = 0;
        state.belowStreak = 0;
        return state.mode;
      }
    }

    return state.mode;
  }

  private static final class WorldState {
    private PerWorldPressure.Mode mode = PerWorldPressure.Mode.NORMAL;
    private int aboveStreak = 0;
    private int belowStreak = 0;
  }
}
