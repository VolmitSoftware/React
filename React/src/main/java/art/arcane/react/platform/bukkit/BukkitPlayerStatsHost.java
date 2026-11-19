package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.PlayerStatsHost;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class BukkitPlayerStatsHost implements PlayerStatsHost {
  private volatile Method getPingMethod;
  private volatile boolean resolvedGetPing;

  @Override
  public int onlinePlayerCount() {
    Server server = Bukkit.getServer();
    return server == null ? 0 : server.getOnlinePlayers().size();
  }

  @Override
  public Map<UUID, Integer> playerPings() {
    Map<UUID, Integer> pings = new LinkedHashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      Integer ping = readPing(player);
      if (ping != null) {
        pings.put(player.getUniqueId(), ping);
      }
    }
    return pings;
  }

  private Integer readPing(Player player) {
    if (!resolvedGetPing) {
      try {
        getPingMethod = Player.class.getMethod("getPing");
      } catch (Throwable ignored) {
        getPingMethod = null;
      }
      resolvedGetPing = true;
    }

    Method method = getPingMethod;
    if (method != null) {
      try {
        return (Integer) method.invoke(player);
      } catch (Throwable ignored) {
      }
    }

    try {
      Object spigot = player.spigot();
      Method spigotPing = spigot.getClass().getMethod("getPing");
      Object ping = spigotPing.invoke(spigot);
      if (ping instanceof Number n) {
        return n.intValue();
      }
    } catch (Throwable ignored) {
    }

    return null;
  }
}
