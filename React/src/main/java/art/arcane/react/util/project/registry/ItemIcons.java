package art.arcane.react.util.project.registry;

import org.bukkit.Material;

import java.util.Locale;

public final class ItemIcons {
  public static final String DEFAULT_ID = "minecraft:slime_ball";

  private ItemIcons() {
  }

  public static String id(Material material) {
    Material resolved = material == null ? Material.SLIME_BALL : material;
    return "minecraft:" + resolved.name().toLowerCase(Locale.ROOT);
  }

  public static Material material(String id) {
    if (id == null || id.isBlank()) {
      return Material.SLIME_BALL;
    }
    Material material = Material.matchMaterial(id);
    return material == null ? Material.SLIME_BALL : material;
  }
}
