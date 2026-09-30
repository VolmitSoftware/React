package art.arcane.react.content.sampler;

final class VanillaServerFixture {
  static final int TICK_STATS_SPAN = 100;

  private final DedicatedServer minecraftServer = new DedicatedServer();
  private final CraftServer craftServer = new CraftServer(minecraftServer);

  Object craftServer() {
    return craftServer;
  }

  Object minecraftServer() {
    return minecraftServer;
  }

  void beginTick() {
    minecraftServer.beginTick();
  }

  void finishTick(long nanos) {
    minecraftServer.finishTick(nanos);
  }

  void completeTick(long nanos) {
    beginTick();
    finishTick(nanos);
  }

  void completeTicks(int count, long nanos) {
    for (int i = 0; i < count; i++) {
      completeTick(nanos);
    }
  }

  public static final class CraftServer {
    private final DedicatedServer server;

    private CraftServer(DedicatedServer server) {
      this.server = server;
    }

    public DedicatedServer getServer() {
      return server;
    }
  }

  public abstract static class MinecraftServer {
    private final long[] tickTimesNanos = new long[TICK_STATS_SPAN];
    private int tickCount;

    public long[] getTickTimesNanos() {
      return tickTimesNanos;
    }

    public int getTickCount() {
      return tickCount;
    }

    void beginTick() {
      tickCount++;
    }

    void finishTick(long nanos) {
      tickTimesNanos[tickCount % TICK_STATS_SPAN] = nanos;
    }
  }

  public static final class DedicatedServer extends MinecraftServer {
  }
}
