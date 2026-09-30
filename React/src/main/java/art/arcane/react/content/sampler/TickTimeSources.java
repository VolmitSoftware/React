package art.arcane.react.content.sampler;

import art.arcane.react.React;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class TickTimeSources {
  private static final long[] EMPTY_TICKS = new long[0];

  private TickTimeSources() {
  }

  static TickTimeSource detect() {
    TickTimeSource source = select(Bukkit.getServer());
    if (source == null) {
      React.warn("Tick work time is unavailable on this server; tick-time and tick-ms percentiles report unavailable.");
    } else if (source instanceof VanillaTickTimes) {
      React.info("Tick work time: vanilla MinecraftServer tick times");
    }

    return source;
  }

  static TickTimeSource select(Server server) {
    if (server == null) {
      return null;
    }

    if (hasServerTickTimes(server)) {
      return server::getTickTimes;
    }

    return vanilla(server);
  }

  static TickTimeSource vanilla(Object craftServer) {
    Method serverAccessor = findMethod(craftServer.getClass(), "getServer", null);
    if (serverAccessor == null) {
      return null;
    }

    Object minecraftServer;
    try {
      minecraftServer = serverAccessor.invoke(craftServer);
    } catch (IllegalAccessException | InvocationTargetException failure) {
      React.reportError("Could not reach the vanilla MinecraftServer for tick work times", failure);
      return null;
    }

    if (minecraftServer == null) {
      return null;
    }

    Method tickTimes = findMethod(minecraftServer.getClass(), "getTickTimesNanos", long[].class);
    Method tickCount = findMethod(minecraftServer.getClass(), "getTickCount", int.class);
    if (tickTimes == null || tickCount == null) {
      return null;
    }

    return new VanillaTickTimes(minecraftServer, tickTimes, tickCount);
  }

  private static boolean hasServerTickTimes(Server server) {
    try {
      server.getTickTimes();
      return true;
    } catch (NoSuchMethodError | AbstractMethodError missing) {
      return false;
    }
  }

  private static Method findMethod(Class<?> type, String name, Class<?> returnType) {
    for (Method method : type.getMethods()) {
      if (isAccessor(method, name, returnType)) {
        return accessible(method);
      }
    }

    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      for (Method method : current.getDeclaredMethods()) {
        if (isAccessor(method, name, returnType)) {
          return accessible(method);
        }
      }
    }

    return null;
  }

  private static boolean isAccessor(Method method, String name, Class<?> returnType) {
    return method.getParameterCount() == 0
        && method.getName().equals(name)
        && (returnType == null || method.getReturnType() == returnType);
  }

  private static Method accessible(Method method) {
    return method.trySetAccessible() ? method : null;
  }

  static final class VanillaTickTimes implements TickTimeSource {
    private final Object minecraftServer;
    private final Method tickTimes;
    private final Method tickCount;
    private boolean hasBaseline;
    private int lastTickCount;
    private int lastCompletedTick;
    private int freshTicks;

    private VanillaTickTimes(Object minecraftServer, Method tickTimes, Method tickCount) {
      this.minecraftServer = minecraftServer;
      this.tickTimes = tickTimes;
      this.tickCount = tickCount;
    }

    @Override
    public long[] read() {
      try {
        long[] ring = (long[]) tickTimes.invoke(minecraftServer);
        int count = (int) tickCount.invoke(minecraftServer);
        return capture(ring, count);
      } catch (IllegalAccessException | InvocationTargetException failure) {
        React.reportError("Vanilla MinecraftServer tick times stopped answering; tick work time is now unavailable", failure);
        return null;
      }
    }

    @Override
    public int freshTicks(int observedTicks) {
      return freshTicks;
    }

    private long[] capture(long[] ring, int count) {
      int completed = hasBaseline && count == lastTickCount ? count : count - 1;
      int valid = Math.min(ring.length, Math.max(0, completed));
      freshTicks = hasBaseline ? Math.min(valid, Math.max(0, completed - lastCompletedTick)) : 0;
      hasBaseline = true;
      lastTickCount = count;
      lastCompletedTick = completed;
      if (valid == 0) {
        return EMPTY_TICKS;
      }

      long[] ordered = new long[valid];
      int oldest = completed - valid + 1;
      for (int i = 0; i < valid; i++) {
        ordered[i] = ring[Math.floorMod(oldest + i, ring.length)];
      }

      return ordered;
    }
  }
}
