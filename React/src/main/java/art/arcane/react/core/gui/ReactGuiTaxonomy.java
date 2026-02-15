package art.arcane.react.core.gui;

import art.arcane.react.util.format.C;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Objects;

public final class ReactGuiTaxonomy {
    private ReactGuiTaxonomy() {
    }

    public static EntryGroup groupForId(String id) {
        String normalizedId = normalizeId(id);

        if (normalizedId.startsWith("adapt-")) {
            return new EntryGroup(0, "Adapt Integration", Material.BOOKSHELF);
        }
        if (normalizedId.startsWith("iris-")) {
            return new EntryGroup(1, "Iris Integration", Material.OAK_SAPLING);
        }
        if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
            return new EntryGroup(10, "Tick & Stability", Material.CLOCK);
        }
        if (containsAny(normalizedId, "memory", "gc")) {
            return new EntryGroup(11, "Memory & GC", Material.EXPERIENCE_BOTTLE);
        }
        if (containsAny(normalizedId, "player", "ping")) {
            return new EntryGroup(12, "Players & Network", Material.PLAYER_HEAD);
        }
        if (containsAny(normalizedId, "entity", "spawn")) {
            return new EntryGroup(13, "Entities & Spawns", Material.ZOMBIE_HEAD);
        }
        if (containsAny(normalizedId, "chunk", "world")) {
            return new EntryGroup(14, "Chunks & World", Material.MAP);
        }
        if (containsAny(normalizedId, "redstone")) {
            return new EntryGroup(15, "Redstone", Material.REDSTONE);
        }
        if (containsAny(normalizedId, "hopper")) {
            return new EntryGroup(16, "Hoppers", Material.HOPPER);
        }
        if (containsAny(normalizedId, "fluid")) {
            return new EntryGroup(17, "Fluids", Material.WATER_BUCKET);
        }
        if (containsAny(normalizedId, "physics")) {
            return new EntryGroup(18, "Physics", Material.ANVIL);
        }
        if (containsAny(normalizedId, "event", "listener")) {
            return new EntryGroup(19, "Events", Material.NOTE_BLOCK);
        }
        if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
            return new EntryGroup(20, "React Scheduler", Material.COMPARATOR);
        }
        if (containsAny(normalizedId, "processor", "load", "cpu")) {
            return new EntryGroup(21, "CPU & Host", Material.BLAST_FURNACE);
        }
        if ("unknown".equals(normalizedId)) {
            return new EntryGroup(98, "Fallback", Material.BARRIER);
        }

        return new EntryGroup(99, "Misc", Material.FILLED_MAP);
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

    public record EntryGroup(
            int order,
            String label,
            Material icon
    ) {
    }
}
