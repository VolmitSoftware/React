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
import art.arcane.react.content.sampler.SamplerTickTime;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Activation Range Governor feature. Temporarily scales down per-world entity activation ranges while the server is under sustained pressure, so the server itself deactivates distant entities instead of ticking them. Unlike dynamic-activation-range (which samples and pauses entities one by one), these ranges are read by the server every tick and apply to all entities instantly. Ranges and villager ticking are restored once the server recovers.")
public class FeatureActivationRangeGovernor extends ReactFeature {
  public static final String ID = "activation-range-governor";
  private static final String[] GOVERNED_FIELDS = {
      "animalActivationRange",
      "monsterActivationRange",
      "raiderActivationRange",
      "miscActivationRange",
      "waterActivationRange",
      "villagerActivationRange",
      "flyingMonsterActivationRange"
  };
  private static final String TICK_INACTIVE_VILLAGERS_FIELD = "tickInactiveVillagers";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for activation range governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the governor engages.", impact = "Lower values shrink activation ranges earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the governor releases.", impact = "Lower values hold the reduction longer for stability; higher values restore full ranges sooner.")
  private double releaseTickTimeMs = 42;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore ranges sooner.")
  private long sustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to animal activation range while engaged.", impact = "Lower values deactivate distant animals sooner during pressure windows.")
  private double animalRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to monster activation range while engaged.", impact = "Lower values deactivate distant monsters sooner; mobs freeze visibly until approached.")
  private double monsterRangeFactor = 0.6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to raider activation range while engaged.", impact = "Lower values shed raider AI cost but can slow active raids; kept conservative by default.")
  private double raiderRangeFactor = 0.8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to misc-entity activation range while engaged.", impact = "Lower values deactivate distant items, projectiles, and ambient entities sooner.")
  private double miscRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to water-mob activation range while engaged.", impact = "Lower values deactivate distant water mobs sooner during pressure windows.")
  private double waterRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to villager activation range while engaged.", impact = "Lower values shed villager brain and POI cost, the most expensive entity class; distant villagers idle until approached.")
  private double villagerRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to flying-monster activation range while engaged.", impact = "Lower values deactivate distant phantoms and ghasts sooner during pressure windows.")
  private double flyingMonsterRangeFactor = 0.6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum activation range in blocks after scaling.", impact = "Higher values cap how aggressively ranges shrink; lower values allow deeper cuts.")
  private int minimumRangeBlocks = 8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Also suspends ticking of inactive villagers while engaged.", impact = "Enable to stop inactive villager restock/brain ticking during pressure windows; disable to only scale ranges.")
  private boolean suspendInactiveVillagerTicking = true;
  private transient Method getHandleMethod;
  private transient Field spigotConfigField;
  private transient Map<String, Field> rangeFields;
  private transient Field tickInactiveVillagersField;
  private transient Map<UUID, Map<String, Integer>> baselinesByWorld;
  private transient Map<UUID, Boolean> villagerTickBaselines;
  private transient boolean resolved;
  private transient boolean supported;
  private transient final PressureGate gate = new PressureGate();

  public FeatureActivationRangeGovernor() {
    super(ID);
  }

  @Override
  public void onActivate() {
    baselinesByWorld = new ConcurrentHashMap<>();
    villagerTickBaselines = new ConcurrentHashMap<>();
    resolved = false;
    supported = false;
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
    boolean pressure = !(tickMs < engageTickTimeMs);
    boolean calm = !(tickMs > releaseTickTimeMs);
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
    if (!resolveSupport()) {
      return;
    }

    int governed = 0;
    for (World world : Bukkit.getWorlds()) {
      try {
        Object config = spigotConfig(world);
        if (config == null) {
          continue;
        }

        Map<String, Integer> baselines = new HashMap<>();
        for (Map.Entry<String, Field> entry : rangeFields.entrySet()) {
          Field field = entry.getValue();
          int base = field.getInt(config);
          if (base <= 0) {
            continue;
          }

          int target = Math.max(Math.max(1, minimumRangeBlocks), (int) Math.round(base * factorFor(entry.getKey())));
          if (target >= base) {
            continue;
          }

          baselines.put(entry.getKey(), base);
          field.setInt(config, target);
          governed++;
        }

        if (!baselines.isEmpty()) {
          baselinesByWorld.put(world.getUID(), baselines);
        }

        if (suspendInactiveVillagerTicking && tickInactiveVillagersField != null) {
          boolean base = tickInactiveVillagersField.getBoolean(config);
          if (base) {
            villagerTickBaselines.put(world.getUID(), true);
            tickInactiveVillagersField.setBoolean(config, false);
            governed++;
          }
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    if (governed > 0) {
      React.verbose("Activation range governor engaged: scaled " + governed + " activation settings across " + baselinesByWorld.size() + " worlds");
    }
  }

  private void release() {
    if (baselinesByWorld == null) {
      return;
    }

    int restored = 0;
    for (Map.Entry<UUID, Map<String, Integer>> worldEntry : baselinesByWorld.entrySet()) {
      World world = Bukkit.getWorld(worldEntry.getKey());
      if (world == null) {
        continue;
      }

      try {
        Object config = spigotConfig(world);
        if (config == null) {
          continue;
        }

        for (Map.Entry<String, Integer> entry : worldEntry.getValue().entrySet()) {
          Field field = rangeFields.get(entry.getKey());
          if (field != null) {
            field.setInt(config, entry.getValue());
            restored++;
          }
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    for (Map.Entry<UUID, Boolean> entry : villagerTickBaselines.entrySet()) {
      World world = Bukkit.getWorld(entry.getKey());
      if (world == null || tickInactiveVillagersField == null) {
        continue;
      }

      try {
        Object config = spigotConfig(world);
        if (config != null) {
          tickInactiveVillagersField.setBoolean(config, entry.getValue());
          restored++;
        }
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    baselinesByWorld.clear();
    villagerTickBaselines.clear();
    if (restored > 0) {
      React.verbose("Activation range governor released: restored " + restored + " activation settings");
    }
  }

  private double factorFor(String fieldName) {
    return switch (fieldName) {
      case "animalActivationRange" -> animalRangeFactor;
      case "monsterActivationRange" -> monsterRangeFactor;
      case "raiderActivationRange" -> raiderRangeFactor;
      case "miscActivationRange" -> miscRangeFactor;
      case "waterActivationRange" -> waterRangeFactor;
      case "villagerActivationRange" -> villagerRangeFactor;
      default -> flyingMonsterRangeFactor;
    };
  }

  private boolean resolveSupport() {
    if (resolved) {
      return supported;
    }

    resolved = true;
    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      resolved = false;
      return false;
    }

    try {
      World world = worlds.get(0);
      getHandleMethod = world.getClass().getMethod("getHandle");
      getHandleMethod.setAccessible(true);
      Object handle = getHandleMethod.invoke(world);
      spigotConfigField = findField(handle.getClass(), "spigotConfig");
      if (spigotConfigField == null) {
        React.warn("Activation Range Governor disabled: spigotConfig is not present on this server software.");
        setEnabled(false);
        return false;
      }

      Object config = spigotConfigField.get(handle);
      Map<String, Field> fields = new LinkedHashMap<>();
      for (String name : GOVERNED_FIELDS) {
        Field field = findField(config.getClass(), name);
        if (field != null && field.getType() == int.class) {
          fields.put(name, field);
        }
      }

      Field villagerTick = findField(config.getClass(), TICK_INACTIVE_VILLAGERS_FIELD);
      if (villagerTick != null && villagerTick.getType() == boolean.class) {
        tickInactiveVillagersField = villagerTick;
      }

      if (fields.isEmpty() && tickInactiveVillagersField == null) {
        React.warn("Activation Range Governor disabled: no activation range fields resolved on this server software.");
        setEnabled(false);
        return false;
      }

      rangeFields = fields;
      supported = true;
      return true;
    } catch (Throwable e) {
      failRuntime(e);
      return false;
    }
  }

  private Object spigotConfig(World world) throws ReflectiveOperationException {
    Object handle = getHandleMethod.invoke(world);
    return handle == null ? null : spigotConfigField.get(handle);
  }

  private Field findField(Class<?> owner, String name) {
    Class<?> current = owner;
    while (current != null && current != Object.class) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }

    return null;
  }

  private void failRuntime(Throwable e) {
    supported = false;
    setEnabled(false);
    React.warn("Activation Range Governor disabled due to runtime incompatibility: " + e.getClass().getSimpleName() + ": " + e.getMessage());
  }
}
