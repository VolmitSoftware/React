package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class TaxonomyMessages {
  public static final TextKey CATEGORY_DIAGNOSTICS = TextKey.of("taxonomy.category.diagnostics", "Diagnostics & HUD");
  public static final TextKey CATEGORY_DIAGNOSTICS_DESCRIPTION = TextKey.of("taxonomy.category.diagnostics.description", "Live maps, overlays & heatmaps");
  public static final TextKey CATEGORY_REDSTONE = TextKey.of("taxonomy.category.redstone", "Redstone & Circuits");
  public static final TextKey CATEGORY_REDSTONE_DESCRIPTION = TextKey.of("taxonomy.category.redstone.description", "Redstone & circuit throttling");
  public static final TextKey CATEGORY_HOPPERS = TextKey.of("taxonomy.category.hoppers", "Hoppers & Storage");
  public static final TextKey CATEGORY_HOPPERS_DESCRIPTION = TextKey.of("taxonomy.category.hoppers.description", "Hopper & container throughput");
  public static final TextKey CATEGORY_ENTITIES = TextKey.of("taxonomy.category.entities", "Entities & Mobs");
  public static final TextKey CATEGORY_ENTITIES_DESCRIPTION = TextKey.of("taxonomy.category.entities.description", "Mob, item & spawn management");
  public static final TextKey CATEGORY_WORLD = TextKey.of("taxonomy.category.world", "World & Blocks");
  public static final TextKey CATEGORY_WORLD_DESCRIPTION = TextKey.of("taxonomy.category.world.description", "Chunks, blocks & physics");
  public static final TextKey CATEGORY_PERFORMANCE = TextKey.of("taxonomy.category.performance", "Performance & Load");
  public static final TextKey CATEGORY_PERFORMANCE_DESCRIPTION = TextKey.of("taxonomy.category.performance.description", "View distance, tick budgets & governors");
  public static final TextKey CATEGORY_OTHER = TextKey.of("taxonomy.category.other", "Other");
  public static final TextKey CATEGORY_OTHER_DESCRIPTION = TextKey.of("taxonomy.category.other.description", "Uncategorized");
  public static final TextKey GROUP_ADAPT = TextKey.of("taxonomy.group.adapt", "Adapt Integration");
  public static final TextKey GROUP_IRIS = TextKey.of("taxonomy.group.iris", "Iris Integration");
  public static final TextKey GROUP_WORMHOLES = TextKey.of("taxonomy.group.wormholes", "Wormholes Integration");
  public static final TextKey GROUP_GLOSS = TextKey.of("taxonomy.group.gloss", "Gloss Integration");
  public static final TextKey GROUP_HIDDENORE = TextKey.of("taxonomy.group.hiddenore", "HiddenOre Integration");
  public static final TextKey GROUP_BILETOOLS = TextKey.of("taxonomy.group.biletools", "BileTools Integration");
  public static final TextKey GROUP_PLUGINS = TextKey.of("taxonomy.group.plugins", "Plugin Costs");
  public static final TextKey GROUP_REACT = TextKey.of("taxonomy.group.react", "React Integration");
  public static final TextKey GROUP_CORE = TextKey.of("taxonomy.group.core", "React Core");
  public static final TextKey GROUP_TICK = TextKey.of("taxonomy.group.tick", "Tick & Stability");
  public static final TextKey GROUP_MEMORY = TextKey.of("taxonomy.group.memory", "Memory & GC");
  public static final TextKey GROUP_PLAYERS = TextKey.of("taxonomy.group.players", "Players & Network");
  public static final TextKey GROUP_ENTITIES = TextKey.of("taxonomy.group.entities", "Entities & Spawns");
  public static final TextKey GROUP_CHUNKS = TextKey.of("taxonomy.group.chunks", "Chunks & World");
  public static final TextKey GROUP_REDSTONE = TextKey.of("taxonomy.group.redstone", "Redstone");
  public static final TextKey GROUP_HOPPERS = TextKey.of("taxonomy.group.hoppers", "Hoppers");
  public static final TextKey GROUP_FLUIDS = TextKey.of("taxonomy.group.fluids", "Fluids");
  public static final TextKey GROUP_PHYSICS = TextKey.of("taxonomy.group.physics", "Physics");
  public static final TextKey GROUP_EVENTS = TextKey.of("taxonomy.group.events", "Events");
  public static final TextKey GROUP_SCHEDULER = TextKey.of("taxonomy.group.scheduler", "React Scheduler");
  public static final TextKey GROUP_CPU = TextKey.of("taxonomy.group.cpu", "CPU & Host");
  public static final TextKey GROUP_FALLBACK = TextKey.of("taxonomy.group.fallback", "Fallback");
  public static final TextKey GROUP_MISC = TextKey.of("taxonomy.group.misc", "Misc");
  public static final TextKey GROUP_SAMPLER_TRENDS = TextKey.of("taxonomy.group.sampler_trends", "Sampler Trends");
  public static final TextKey GROUP_WORLD_OVERLAYS = TextKey.of("taxonomy.group.world_overlays", "World Overlays");

  private TaxonomyMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(CATEGORY_DIAGNOSTICS);
    builder.add(CATEGORY_DIAGNOSTICS_DESCRIPTION);
    builder.add(CATEGORY_REDSTONE);
    builder.add(CATEGORY_REDSTONE_DESCRIPTION);
    builder.add(CATEGORY_HOPPERS);
    builder.add(CATEGORY_HOPPERS_DESCRIPTION);
    builder.add(CATEGORY_ENTITIES);
    builder.add(CATEGORY_ENTITIES_DESCRIPTION);
    builder.add(CATEGORY_WORLD);
    builder.add(CATEGORY_WORLD_DESCRIPTION);
    builder.add(CATEGORY_PERFORMANCE);
    builder.add(CATEGORY_PERFORMANCE_DESCRIPTION);
    builder.add(CATEGORY_OTHER);
    builder.add(CATEGORY_OTHER_DESCRIPTION);
    builder.add(GROUP_ADAPT);
    builder.add(GROUP_IRIS);
    builder.add(GROUP_WORMHOLES);
    builder.add(GROUP_GLOSS);
    builder.add(GROUP_HIDDENORE);
    builder.add(GROUP_BILETOOLS);
    builder.add(GROUP_PLUGINS);
    builder.add(GROUP_REACT);
    builder.add(GROUP_CORE);
    builder.add(GROUP_TICK);
    builder.add(GROUP_MEMORY);
    builder.add(GROUP_PLAYERS);
    builder.add(GROUP_ENTITIES);
    builder.add(GROUP_CHUNKS);
    builder.add(GROUP_REDSTONE);
    builder.add(GROUP_HOPPERS);
    builder.add(GROUP_FLUIDS);
    builder.add(GROUP_PHYSICS);
    builder.add(GROUP_EVENTS);
    builder.add(GROUP_SCHEDULER);
    builder.add(GROUP_CPU);
    builder.add(GROUP_FALLBACK);
    builder.add(GROUP_MISC);
    builder.add(GROUP_SAMPLER_TRENDS);
    builder.add(GROUP_WORLD_OVERLAYS);
  }
}
