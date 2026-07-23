package art.arcane.react.core.gui;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ReactGuiTaxonomy {
  private ReactGuiTaxonomy() {
  }

  public static Category categoryForId(String id) {
    String normalizedId = normalizeId(id);

    if (normalizedId.startsWith("plugin-")) {
      return category(70, "plugins", Material.NOTE_BLOCK, TaxonomyMessages.GROUP_PLUGINS, TaxonomyMessages.GROUP_PLUGINS);
    }
    if (containsAny(normalizedId, "map", "overlay", "heatmap", "-pie", "pie-", "replay", "impact")) {
      return category(10, "diagnostics", Material.FILLED_MAP, TaxonomyMessages.CATEGORY_DIAGNOSTICS, TaxonomyMessages.CATEGORY_DIAGNOSTICS_DESCRIPTION);
    }
    if (containsAny(normalizedId, "redstone", "circuit")) {
      return category(20, "redstone", Material.REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE_DESCRIPTION);
    }
    if (containsAny(normalizedId, "hopper")) {
      return category(30, "hoppers", Material.HOPPER, TaxonomyMessages.CATEGORY_HOPPERS, TaxonomyMessages.CATEGORY_HOPPERS_DESCRIPTION);
    }
    if (containsAny(normalizedId, "entity", "mob", "spawn", "item", "drop", "projectile", "vehicle", "minecart", "orb", "despawn", "stack", "trim", "burst", "farm", "bubbler", "crowd")) {
      return category(40, "entities", Material.ZOMBIE_HEAD, TaxonomyMessages.CATEGORY_ENTITIES, TaxonomyMessages.CATEGORY_ENTITIES_DESCRIPTION);
    }
    if (containsAny(normalizedId, "chunk", "column", "gravity", "crop", "leaf", "portal", "explosion", "fluid", "snow", "fire", "falling", "quarantine", "block")) {
      return category(50, "world", Material.GRASS_BLOCK, TaxonomyMessages.CATEGORY_WORLD, TaxonomyMessages.CATEGORY_WORLD_DESCRIPTION);
    }
    if (containsAny(normalizedId, "view", "tick", "tps", "mspt", "budget", "governor", "activation", "sleep", "surge", "incident", "hibernat", "save", "pathfinder", "tracker", "afk", "spike", "load")) {
      return category(60, "performance", Material.CLOCK, TaxonomyMessages.CATEGORY_PERFORMANCE, TaxonomyMessages.CATEGORY_PERFORMANCE_DESCRIPTION);
    }

    return category(99, "other", Material.CHEST, TaxonomyMessages.CATEGORY_OTHER, TaxonomyMessages.CATEGORY_OTHER_DESCRIPTION);
  }

  public static List<Category> topLevelCategories() {
    return List.of(
        category(10, "diagnostics", Material.FILLED_MAP, TaxonomyMessages.CATEGORY_DIAGNOSTICS, TaxonomyMessages.CATEGORY_DIAGNOSTICS_DESCRIPTION),
        category(20, "redstone", Material.REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE, TaxonomyMessages.CATEGORY_REDSTONE_DESCRIPTION),
        category(30, "hoppers", Material.HOPPER, TaxonomyMessages.CATEGORY_HOPPERS, TaxonomyMessages.CATEGORY_HOPPERS_DESCRIPTION),
        category(40, "entities", Material.ZOMBIE_HEAD, TaxonomyMessages.CATEGORY_ENTITIES, TaxonomyMessages.CATEGORY_ENTITIES_DESCRIPTION),
        category(50, "world", Material.GRASS_BLOCK, TaxonomyMessages.CATEGORY_WORLD, TaxonomyMessages.CATEGORY_WORLD_DESCRIPTION),
        category(60, "performance", Material.CLOCK, TaxonomyMessages.CATEGORY_PERFORMANCE, TaxonomyMessages.CATEGORY_PERFORMANCE_DESCRIPTION)
    );
  }

  public static EntryGroup groupForId(String id) {
    String normalizedId = normalizeId(id);

    if (normalizedId.startsWith("adapt-")) {
      return group(0, TaxonomyMessages.GROUP_ADAPT, Material.BOOKSHELF);
    }
    if (normalizedId.startsWith("iris-")) {
      return group(1, TaxonomyMessages.GROUP_IRIS, Material.OAK_SAPLING);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return group(2, TaxonomyMessages.GROUP_WORMHOLES, Material.ENDER_PEARL);
    }
    if (normalizedId.startsWith("holoui-")) {
      return group(3, TaxonomyMessages.GROUP_HOLOUI, Material.PAINTING);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return group(4, TaxonomyMessages.GROUP_HIDDENORE, Material.DIAMOND_ORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return group(5, TaxonomyMessages.GROUP_BILETOOLS, Material.LIME_DYE);
    }
    if (normalizedId.startsWith("plugin-")) {
      return group(6, TaxonomyMessages.GROUP_PLUGINS, Material.NOTE_BLOCK);
    }
    if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
      return group(10, TaxonomyMessages.GROUP_TICK, Material.CLOCK);
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return group(11, TaxonomyMessages.GROUP_MEMORY, Material.EXPERIENCE_BOTTLE);
    }
    if (containsAny(normalizedId, "player", "ping")) {
      return group(12, TaxonomyMessages.GROUP_PLAYERS, Material.PLAYER_HEAD);
    }
    if (containsAny(normalizedId, "entity", "spawn")) {
      return group(13, TaxonomyMessages.GROUP_ENTITIES, Material.ZOMBIE_HEAD);
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return group(14, TaxonomyMessages.GROUP_CHUNKS, Material.MAP);
    }
    if (containsAny(normalizedId, "redstone")) {
      return group(15, TaxonomyMessages.GROUP_REDSTONE, Material.REDSTONE);
    }
    if (containsAny(normalizedId, "hopper")) {
      return group(16, TaxonomyMessages.GROUP_HOPPERS, Material.HOPPER);
    }
    if (containsAny(normalizedId, "fluid")) {
      return group(17, TaxonomyMessages.GROUP_FLUIDS, Material.WATER_BUCKET);
    }
    if (containsAny(normalizedId, "physics")) {
      return group(18, TaxonomyMessages.GROUP_PHYSICS, Material.ANVIL);
    }
    if (containsAny(normalizedId, "event", "listener")) {
      return group(19, TaxonomyMessages.GROUP_EVENTS, Material.NOTE_BLOCK);
    }
    if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
      return group(20, TaxonomyMessages.GROUP_SCHEDULER, Material.COMPARATOR);
    }
    if (containsAny(normalizedId, "processor", "load", "cpu")) {
      return group(21, TaxonomyMessages.GROUP_CPU, Material.BLAST_FURNACE);
    }
    if ("unknown".equals(normalizedId)) {
      return group(98, TaxonomyMessages.GROUP_FALLBACK, Material.BARRIER);
    }

    return group(99, TaxonomyMessages.GROUP_MISC, Material.FILLED_MAP);
  }

  public static Material iconForId(String id) {
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

  private static Category category(int order, String key, Material icon, TextKey label, TextKey description) {
    return new Category(order, key, ReactLanguage.plain(label), icon, ReactLanguage.plain(description));
  }

  private static EntryGroup group(int order, TextKey label, Material icon) {
    return new EntryGroup(order, ReactLanguage.plain(label), icon);
  }

  public record EntryGroup(
      int order,
      String label,
      Material icon
  ) {
  }

  public record Category(
      int order,
      String key,
      String label,
      Material icon,
      String description
  ) {
  }
}
