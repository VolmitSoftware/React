package art.arcane.react.content.sampler;

import org.bukkit.Server;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TickTimeSourcesTest {
  private static final long MS = 1_000_000L;

  @Test
  void paperServersKeepTheServerTickTimesApi() {
    long[] paperTimes = {4L * MS, 6L * MS, 5L * MS};
    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getTickTimes()).thenReturn(paperTimes);

    TickTimeSource source = TickTimeSources.select(server);

    Assertions.assertNotNull(source);
    Assertions.assertArrayEquals(paperTimes, source.read());
    Assertions.assertEquals(7, source.freshTicks(7));
  }

  @Test
  void spigotServersReadVanillaMinecraftServerTickTimes() {
    VanillaServerFixture vanilla = new VanillaServerFixture();
    vanilla.completeTicks(3, 2L * MS);
    vanilla.beginTick();
    Server server = spigotServer(vanilla.minecraftServer());

    TickTimeSource source = TickTimeSources.select(server);

    Assertions.assertNotNull(source);
    Assertions.assertArrayEquals(new long[]{2L * MS, 2L * MS, 2L * MS}, source.read());
  }

  @Test
  void serversWithoutEitherSourceHaveNoWorkTimes() {
    Server server = Mockito.mock(Server.class);
    Mockito.when(server.getTickTimes()).thenThrow(new NoSuchMethodError("getTickTimes"));

    Assertions.assertNull(TickTimeSources.select(server));
    Assertions.assertNull(TickTimeSources.select(null));
    Assertions.assertNull(TickTimeSources.vanilla(new Object()));
  }

  @Test
  void vanillaRingIsReturnedOldestFirstOnceItWraps() {
    VanillaServerFixture vanilla = new VanillaServerFixture();
    TickTimeSource source = TickTimeSources.vanilla(vanilla.craftServer());
    for (int i = 1; i <= 250; i++) {
      vanilla.completeTick(i * MS);
    }
    vanilla.beginTick();

    long[] times = source.read();

    Assertions.assertEquals(VanillaServerFixture.TICK_STATS_SPAN, times.length);
    Assertions.assertEquals(151L * MS, times[0]);
    Assertions.assertEquals(250L * MS, times[times.length - 1]);
    for (int i = 1; i < times.length; i++) {
      Assertions.assertEquals(times[i - 1] + MS, times[i]);
    }
  }

  @Test
  void vanillaFreshTicksFollowCompletedServerTicks() {
    VanillaServerFixture vanilla = new VanillaServerFixture();
    vanilla.completeTicks(40, 3L * MS);
    TickTimeSource source = TickTimeSources.vanilla(vanilla.craftServer());

    vanilla.beginTick();
    Assertions.assertEquals(40, source.read().length);
    Assertions.assertEquals(0, source.freshTicks(5));

    vanilla.finishTick(3L * MS);
    vanilla.completeTicks(3, 9L * MS);
    vanilla.beginTick();
    long[] times = source.read();
    Assertions.assertEquals(4, source.freshTicks(5));
    Assertions.assertEquals(9L * MS, times[times.length - 1]);

    vanilla.finishTick(11L * MS);
    times = source.read();
    Assertions.assertEquals(1, source.freshTicks(5));
    Assertions.assertEquals(11L * MS, times[times.length - 1]);

    source.read();
    Assertions.assertEquals(0, source.freshTicks(5));

    vanilla.beginTick();
    times = source.read();
    Assertions.assertEquals(0, source.freshTicks(5));
    Assertions.assertEquals(11L * MS, times[times.length - 1]);

    vanilla.finishTick(1L * MS);
    vanilla.completeTicks(500, 1L * MS);
    vanilla.beginTick();
    source.read();
    Assertions.assertEquals(VanillaServerFixture.TICK_STATS_SPAN, source.freshTicks(5));
  }

  private static Server spigotServer(Object minecraftServer) {
    Server server = Mockito.mock(Server.class, Mockito.withSettings().extraInterfaces(CraftServerHandle.class));
    Mockito.when(server.getTickTimes()).thenThrow(new NoSuchMethodError("getTickTimes"));
    Mockito.when(((CraftServerHandle) server).getServer()).thenReturn(minecraftServer);
    return server;
  }

  public interface CraftServerHandle {
    Object getServer();
  }
}
