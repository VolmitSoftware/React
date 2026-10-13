package art.arcane.react.core.telemetry.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class FoliaRegionTps implements RegionalHealth.Reader {
  private final Server server;
  private final Method method;

  private FoliaRegionTps(Server server, Method method) {
    this.server = server;
    this.method = method;
  }

  public static FoliaRegionTps detect(Server server) {
    try {
      Method method = server.getClass().getMethod("getRegionTPS", World.class, int.class, int.class);
      return method.getReturnType() == double[].class ? new FoliaRegionTps(server, method) : null;
    } catch (NoSuchMethodException missing) {
      return null;
    }
  }

  @Override
  public double read(RegionalHealth.Position position) {
    World world = Bukkit.getWorld(position.worldId());
    if (world == null) {
      return Double.NaN;
    }
    try {
      double[] values = (double[]) method.invoke(server, world, position.chunkX(), position.chunkZ());
      return values == null || values.length == 0 ? Double.NaN : values[0];
    } catch (IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Folia region TPS query failed", failure);
    }
  }
}
