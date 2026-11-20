package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.MapRenderTarget;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BukkitMapTargets {
  private BukkitMapTargets() {
  }

  public static Player player(MapRenderTarget target) {
    return target == null || target.viewerId() == null ? null : Bukkit.getPlayer(target.viewerId());
  }

  public static World world(MapRenderTarget target) {
    return target == null || target.worldId() == null ? null : Bukkit.getWorld(target.worldId());
  }
}
