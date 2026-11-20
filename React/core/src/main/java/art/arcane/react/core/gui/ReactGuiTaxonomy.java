package art.arcane.react.core.gui;

import art.arcane.react.core.telemetry.timing.WorldTickSeries;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerGauge;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ReactGuiTaxonomy {
  private ReactGuiTaxonomy() {
  }

  public static Category categoryForId(String id) {
    String normalizedId = normalizeId(id);

    OwnerGauge gauge = OwnerGauge.forId(normalizedId);
    if (gauge != null) {
      return gauge.network()
          ? category(60, "performance", "minecraft:clock", TaxonomyMessages.CATEGORY_PERFORMANCE, TaxonomyMessages.CATEGORY_PERFORMANCE_DESCRIPTION)
          : category(50, "world", "minecraft:grass_block", TaxonomyMessages.CATEGORY_WORLD, TaxonomyMessages.CATEGORY_WORLD_DESCRIPTION);
    }
    if (OwnerCostModel.isOwnerScopedId(normalizedId)) {
      TextKey owners = OwnerNoun.pick(TaxonomyMessages.GROUP_PLUGINS, TaxonomyMessages.GROUP_MODS);
      return category(70, "plugins", "minecraft:note_block", owners, owners);
    }
    if (containsAny(normalizedId, "map", "overlay", "heatmap", "-pie", "pie-", "replay", "impact")) {
      return category(10, "diagnostics", "minecraft:filled_map", TaxonomyMessages.CATEGORY_DIAGNOSTICS, TaxonomyMessages.CATEGORY_DIAGNOSTICS_DESCRIPTION);
    }
    if (containsAny(normalizedId, "redstone", "circuit")) {
      return category(20, "redstone", "minecraft:redstone", TaxonomyMessages.CATEGORY_REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE_DESCRIPTION);
    }
    if (containsAny(normalizedId, "hopper")) {
      return category(30, "hoppers", "minecraft:hopper", TaxonomyMessages.CATEGORY_HOPPERS, TaxonomyMessages.CATEGORY_HOPPERS_DESCRIPTION);
    }
    if (containsAny(normalizedId, "entity", "mob", "spawn", "item", "drop", "projectile", "vehicle", "minecart", "orb", "despawn", "stack", "trim", "burst", "farm", "bubbler", "crowd")) {
      return category(40, "entities", "minecraft:zombie_head", TaxonomyMessages.CATEGORY_ENTITIES, TaxonomyMessages.CATEGORY_ENTITIES_DESCRIPTION);
    }
    if (containsAny(normalizedId, "chunk", "column", "gravity", "crop", "leaf", "portal", "explosion", "fluid", "snow", "fire", "falling", "quarantine", "block")) {
      return category(50, "world", "minecraft:grass_block", TaxonomyMessages.CATEGORY_WORLD, TaxonomyMessages.CATEGORY_WORLD_DESCRIPTION);
    }
    if (containsAny(normalizedId, "view", "tick", "tps", "mspt", "budget", "governor", "activation", "sleep", "surge", "incident", "hibernat", "save", "pathfinder", "tracker", "afk", "spike", "load")) {
      return category(60, "performance", "minecraft:clock", TaxonomyMessages.CATEGORY_PERFORMANCE, TaxonomyMessages.CATEGORY_PERFORMANCE_DESCRIPTION);
    }

    return category(99, "other", "minecraft:chest", TaxonomyMessages.CATEGORY_OTHER, TaxonomyMessages.CATEGORY_OTHER_DESCRIPTION);
  }

  public static List<Category> topLevelCategories() {
    return List.of(
        category(10, "diagnostics", "minecraft:filled_map", TaxonomyMessages.CATEGORY_DIAGNOSTICS, TaxonomyMessages.CATEGORY_DIAGNOSTICS_DESCRIPTION),
        category(20, "redstone", "minecraft:redstone", TaxonomyMessages.CATEGORY_REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE_DESCRIPTION),
        category(30, "hoppers", "minecraft:hopper", TaxonomyMessages.CATEGORY_HOPPERS, TaxonomyMessages.CATEGORY_HOPPERS_DESCRIPTION),
        category(40, "entities", "minecraft:zombie_head", TaxonomyMessages.CATEGORY_ENTITIES, TaxonomyMessages.CATEGORY_ENTITIES_DESCRIPTION),
        category(50, "world", "minecraft:grass_block", TaxonomyMessages.CATEGORY_WORLD, TaxonomyMessages.CATEGORY_WORLD_DESCRIPTION),
        category(60, "performance", "minecraft:clock", TaxonomyMessages.CATEGORY_PERFORMANCE, TaxonomyMessages.CATEGORY_PERFORMANCE_DESCRIPTION)
    );
  }

  public static EntryGroup groupForId(String id) {
    String normalizedId = normalizeId(id);
    OwnerGauge gauge = OwnerGauge.forId(normalizedId);
    if (gauge != null) {
      return gauge.network()
          ? group(12, TaxonomyMessages.GROUP_PLAYERS, "minecraft:player_head")
          : group(14, TaxonomyMessages.GROUP_CHUNKS, "minecraft:map");
    }

    if (normalizedId.startsWith("adapt-")) {
      return group(0, TaxonomyMessages.GROUP_ADAPT, "minecraft:bookshelf");
    }
    if (normalizedId.startsWith("iris-")) {
      return group(1, TaxonomyMessages.GROUP_IRIS, "minecraft:oak_sapling");
    }
    if (normalizedId.startsWith("wormholes-")) {
      return group(2, TaxonomyMessages.GROUP_WORMHOLES, "minecraft:ender_pearl");
    }
    if (normalizedId.startsWith("gloss-")) {
      return group(3, TaxonomyMessages.GROUP_GLOSS, "minecraft:painting");
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return group(4, TaxonomyMessages.GROUP_HIDDENORE, "minecraft:diamond_ore");
    }
    if (normalizedId.startsWith("biletools-")) {
      return group(5, TaxonomyMessages.GROUP_BILETOOLS, "minecraft:lime_dye");
    }
    if (normalizedId.startsWith("animus-")) {
      return group(7, TaxonomyMessages.GROUP_ANIMUS, "minecraft:armor_stand");
    }
    if (OwnerCostModel.isOwnerScopedId(normalizedId)) {
      return group(6, OwnerNoun.pick(TaxonomyMessages.GROUP_PLUGINS, TaxonomyMessages.GROUP_MODS), "minecraft:note_block");
    }
    if (WorldTickSeries.isWorldTickId(normalizedId)) {
      return group(9, TaxonomyMessages.GROUP_DIMENSIONS, "minecraft:recovery_compass");
    }
    if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
      return group(10, TaxonomyMessages.GROUP_TICK, "minecraft:clock");
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return group(11, TaxonomyMessages.GROUP_MEMORY, "minecraft:experience_bottle");
    }
    if (containsAny(normalizedId, "player", "ping")) {
      return group(12, TaxonomyMessages.GROUP_PLAYERS, "minecraft:player_head");
    }
    if (containsAny(normalizedId, "entity", "spawn")) {
      return group(13, TaxonomyMessages.GROUP_ENTITIES, "minecraft:zombie_head");
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return group(14, TaxonomyMessages.GROUP_CHUNKS, "minecraft:map");
    }
    if (containsAny(normalizedId, "redstone")) {
      return group(15, TaxonomyMessages.GROUP_REDSTONE, "minecraft:redstone");
    }
    if (containsAny(normalizedId, "hopper")) {
      return group(16, TaxonomyMessages.GROUP_HOPPERS, "minecraft:hopper");
    }
    if (containsAny(normalizedId, "fluid")) {
      return group(17, TaxonomyMessages.GROUP_FLUIDS, "minecraft:water_bucket");
    }
    if (containsAny(normalizedId, "physics")) {
      return group(18, TaxonomyMessages.GROUP_PHYSICS, "minecraft:anvil");
    }
    if (containsAny(normalizedId, "event", "listener")) {
      return group(19, TaxonomyMessages.GROUP_EVENTS, "minecraft:note_block");
    }
    if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
      return group(20, TaxonomyMessages.GROUP_SCHEDULER, "minecraft:comparator");
    }
    if (containsAny(normalizedId, "processor", "load", "cpu")) {
      return group(21, TaxonomyMessages.GROUP_CPU, "minecraft:blast_furnace");
    }
    if ("unknown".equals(normalizedId)) {
      return group(98, TaxonomyMessages.GROUP_FALLBACK, "minecraft:barrier");
    }

    return group(99, TaxonomyMessages.GROUP_MISC, "minecraft:filled_map");
  }

  public static String iconForId(String id) {
    return groupForId(id).icon();
  }

  public static String groupLabel(String id) {
    return groupForId(id).label();
  }

  public static int groupOrder(String id) {
    return groupForId(id).order();
  }

  public static String normalizeId(String id) {
    return Objects.toString(id, "").trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizeSortKey(String value) {
    return C.stripColor(Objects.toString(value, "")).toLowerCase(Locale.ROOT).replace(" ", "");
  }

  private static boolean containsAny(String value, String... keys) {
    if (value == null || keys == null) {
      return false;
    }

    for (String key : keys) {
      if (key != null && !key.isBlank() && value.contains(key)) {
        return true;
      }
    }

    return false;
  }

  private static Category category(int order, String key, String icon, TextKey label, TextKey description) {
    return new Category(order, key, ReactLanguage.plain(label), icon, ReactLanguage.plain(description));
  }

  private static EntryGroup group(int order, TextKey label, String icon) {
    return new EntryGroup(order, ReactLanguage.plain(label), icon);
  }

  public record EntryGroup(
      int order,
      String label,
      String icon
  ) {
  }

  public record Category(
      int order,
      String key,
      String label,
      String icon,
      String description
  ) {
  }
}
