package art.arcane.react.core.telemetry.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

class FoliaRegionTpsTest {
  @Test
  void absentApiIsUnavailable() {
    Assertions.assertNull(FoliaRegionTps.detect(Mockito.mock(Server.class)));
  }

  @Test
  void queriesCoordinatesWithoutResolvingOrLoadingChunks() {
    Server server = Mockito.mock(Server.class, Mockito.withSettings().extraInterfaces(RegionApi.class));
    World world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(((RegionApi) server).getRegionTPS(world, -12, 34)).thenReturn(new double[]{17D, 18D});
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      FoliaRegionTps reader = FoliaRegionTps.detect(server);
      Assertions.assertNotNull(reader);
      Assertions.assertEquals(17D, reader.read(new RegionalHealth.Position(worldId, -12, 34)));
      Assertions.assertTrue(Double.isNaN(reader.read(new RegionalHealth.Position(worldId, 0, 0))));
      Mockito.verifyNoInteractions(world);
    }
  }

  public interface RegionApi {
    double[] getRegionTPS(World world, int x, int z);
  }
}
