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
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Random Tick Governor feature. Temporarily lowers the random tick speed gamerule while the server is under sustained pressure, restoring it once the server recovers. Crop, leaf, and similar random-tick growth slows only during the governed window.")
public class FeatureRandomTickGovernor extends ReactFeature {
  static boolean isUnderPressure(double tickMs, double engageTickTimeMs, double incident, double engageIncidentScore) {
    return tickMs >= engageTickTimeMs || incident >= engageIncidentScore;
  }

  static boolean isCalm(double tickMs, double releaseTickTimeMs, double incident, double engageIncidentScore) {
    return tickMs <= releaseTickTimeMs && incident < engageIncidentScore;
  }

  public static final String ID = "random-tick-governor";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for random tick governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the governor engages.", impact = "Lower values shed random ticks earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 60;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Incident score (0-100) that also engages the governor.", impact = "Lower values engage during milder incidents; higher values wait for severe incidents.")
  private double engageIncidentScore = 62;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the governor releases.", impact = "Lower values hold the reduction longer for stability; higher values restore growth sooner.")
  private double releaseTickTimeMs = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore growth sooner.")
  private long sustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Random tick speed applied while the governor is engaged.", impact = "Lower values shed more random-tick work but slow growth more during governed windows.")
  private int reducedRandomTickSpeed = 1;
  private transient Map<UUID, Integer> originalByWorld;
  private transient final PressureGate gate = new PressureGate();

  // GameRules constants are paper-only; the legacy GameRule constant survives on spigot
  private static volatile GameRule<Integer> randomTickSpeedRule;

  @SuppressWarnings("removal") // legacy constant only resolved where paper GameRules is absent
  static GameRule<Integer> randomTickSpeedRule() {
    GameRule<Integer> rule = randomTickSpeedRule;
    if (rule == null) {
      try {
        rule = GameRules.RANDOM_TICK_SPEED;
      } catch (NoClassDefFoundError e) {
        rule = GameRule.RANDOM_TICK_SPEED;
      }
      randomTickSpeedRule = rule;
    }
    return rule;
  }

  public FeatureRandomTickGovernor() {
    super(ID);
  }

  @Override
  public void onActivate() {
    originalByWorld = new ConcurrentHashMap<>();
    gate.reset();
  }

  @Override
  public void onDeactivate() {
    if (gate.isEngaged()) {
      gate.reset();
      release();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(1000, tickIntervalMS);
  }

  @Override
  public void onTick() {
    double tickMs = sample(SamplerTickTime.ID);
    double incident = sample(SamplerIncidentScore.ID);
    boolean pressure = isUnderPressure(tickMs, engageTickTimeMs, incident, engageIncidentScore);
    boolean calm = isCalm(tickMs, releaseTickTimeMs, incident, engageIncidentScore);
    long now = System.currentTimeMillis();
    boolean wasEngaged = gate.isEngaged();
    boolean nowEngaged = gate.update(now, pressure, calm, sustainEngageMs, sustainReleaseMs);

    if (!wasEngaged && nowEngaged) {
      engage();
    } else if (wasEngaged && !nowEngaged) {
      release();
    }
  }

  private void engage() {
    int reduced = Math.max(0, reducedRandomTickSpeed);

    J.s(() -> {
      int governed = 0;
      for (World world : Bukkit.getWorlds()) {
        Integer current = world.getGameRuleValue(randomTickSpeedRule());
        if (current == null || current <= reduced) {
          continue;
        }

        originalByWorld.put(world.getUID(), current);
        world.setGameRule(randomTickSpeedRule(), reduced);
        governed++;
      }

      if (governed > 0) {
        React.verbose("Random tick governor engaged: " + governed + " worlds -> randomTickSpeed " + reduced);
      }
    });
  }

  private void release() {
    J.s(() -> {
      int restored = 0;
      for (Map.Entry<UUID, Integer> entry : originalByWorld.entrySet()) {
        World world = Bukkit.getWorld(entry.getKey());
        if (world == null) {
          continue;
        }

        world.setGameRule(randomTickSpeedRule(), entry.getValue());
        restored++;
      }

      originalByWorld.clear();
      if (restored > 0) {
        React.verbose("Random tick governor released: restored " + restored + " worlds");
      }
    });
  }
}
