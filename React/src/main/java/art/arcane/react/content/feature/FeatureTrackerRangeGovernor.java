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
import art.arcane.react.api.sampler.Sampler;
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

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Tracker Range Governor feature. Temporarily scales down per-world entity tracking ranges (items, misc, displays, animals, monsters) while the server is under sustained pressure, shrinking entity-tracker and packet fanout. Changes apply to entities as they become tracked, so fast-churning entities like items react within seconds; ranges are restored once the server recovers.")
public class FeatureTrackerRangeGovernor extends ReactFeature {
  public static final String ID = "tracker-range-governor";
  private static final String[] GOVERNED_FIELDS = {
      "itemTrackingRange",
      "miscTrackingRange",
      "displayTrackingRange",
      "animalTrackingRange",
      "monsterTrackingRange",
      "otherTrackingRange"
  };
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for tracker range governor in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the governor engages.", impact = "Lower values shrink tracking ranges earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 55;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the governor releases.", impact = "Lower values hold the reduction longer for stability; higher values restore full ranges sooner.")
  private double releaseTickTimeMs = 42;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before engaging (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long sustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before releasing (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore ranges sooner.")
  private long sustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to item tracking range while engaged.", impact = "Lower values cut more item tracker/packet fanout; players see dropped items from closer only.")
  private double itemRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to misc tracking range (item frames, paintings, xp orbs) while engaged.", impact = "Lower values cut more tracker/packet fanout for decorative and ambient entities.")
  private double miscRangeFactor = 0.5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to display entity tracking range while engaged.", impact = "Lower values cut display-entity fanout; holograms render from closer only while engaged.")
  private double displayRangeFactor = 0.6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to animal tracking range while engaged.", impact = "Lower values cut animal tracker fanout; distant animals appear later while engaged.")
  private double animalRangeFactor = 0.75;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to monster tracking range while engaged.", impact = "Lower values cut monster tracker fanout; distant monsters appear later while engaged.")
  private double monsterRangeFactor = 0.75;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Scale factor applied to other-entity tracking range while engaged.", impact = "Lower values cut fanout for uncategorized entities like projectiles and vehicles.")
  private double otherRangeFactor = 0.75;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum tracking range in blocks after scaling.", impact = "Higher values cap how aggressively ranges shrink; lower values allow deeper cuts.")
  private int minimumRangeBlocks = 16;
  private transient Method getHandleMethod;
  private transient Field spigotConfigField;
  private transient Map<String, Field> rangeFields;
  private transient Map<UUID, Map<String, Integer>> baselinesByWorld;
  private transient boolean resolved;
  private transient boolean supported;
  private transient boolean engaged;
  private transient long pressureSinceMs;
  private transient long calmSinceMs;

  public FeatureTrackerRangeGovernor() {
    super(ID);
  }

  @Override
  public void onActivate() {
    baselinesByWorld = new ConcurrentHashMap<>();
    resolved = false;
    supported = false;
    engaged = false;
    pressureSinceMs = 0;
    calmSinceMs = 0;
  }

  @Override
  public void onDeactivate() {
    if (engaged) {
      release();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(1000, tickIntervalMS);
  }

  @Override
  public void onTick() {
    double tickMs = sampleTickMs();
    long now = System.currentTimeMillis();

    if (!engaged) {
      if (tickMs < engageTickTimeMs) {
        pressureSinceMs = 0;
        return;
      }

      if (pressureSinceMs == 0) {
        pressureSinceMs = now;
        return;
      }

      if (now - pressureSinceMs >= Math.max(0, sustainEngageMs)) {
        engage();
      }

      return;
    }

    if (tickMs > releaseTickTimeMs) {
      calmSinceMs = 0;
      return;
    }

    if (calmSinceMs == 0) {
      calmSinceMs = now;
      return;
    }

    if (now - calmSinceMs >= Math.max(0, sustainReleaseMs)) {
      release();
    }
  }

  private void engage() {
    engaged = true;
    pressureSinceMs = 0;
    calmSinceMs = 0;

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
      } catch (Throwable e) {
        failRuntime(e);
        return;
      }
    }

    if (governed > 0) {
      React.verbose("Tracker range governor engaged: scaled " + governed + " tracking ranges across " + baselinesByWorld.size() + " worlds");
    }
  }

  private void release() {
    engaged = false;
    pressureSinceMs = 0;
    calmSinceMs = 0;

    if (baselinesByWorld == null || baselinesByWorld.isEmpty()) {
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

    baselinesByWorld.clear();
    if (restored > 0) {
      React.verbose("Tracker range governor released: restored " + restored + " tracking ranges");
    }
  }

  private double factorFor(String fieldName) {
    return switch (fieldName) {
      case "itemTrackingRange" -> itemRangeFactor;
      case "miscTrackingRange" -> miscRangeFactor;
      case "displayTrackingRange" -> displayRangeFactor;
      case "animalTrackingRange" -> animalRangeFactor;
      case "monsterTrackingRange" -> monsterRangeFactor;
      default -> otherRangeFactor;
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
        React.warn("Tracker Range Governor disabled: spigotConfig is not present on this server software.");
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

      if (fields.isEmpty()) {
        React.warn("Tracker Range Governor disabled: no tracking range fields resolved on this server software.");
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
    React.warn("Tracker Range Governor disabled due to runtime incompatibility: " + e.getClass().getSimpleName() + ": " + e.getMessage());
  }

  private double sampleTickMs() {
    try {
      Sampler sampler = React.sampler(SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }
}
