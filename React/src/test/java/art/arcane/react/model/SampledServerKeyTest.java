package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.testutil.Fakes;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SampledServerKeyTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void blockCounterResolvesTheChunkFromBlockCoordinatesWithoutBuildingABukkitChunk() {
    World world = Fakes.world("block_key");
    Block block = block(world, 53, -23);
    Sampler sampler = sampler("physics");
    ObserverController controller = new ObserverController();

    controller.get(block, sampler).addAndGet(4D);

    Mockito.verify(block, Mockito.never()).getChunk();
    Assertions.assertEquals(4D, controller.sample(world, 3, -2, sampler).orElse(0D));
  }

  @Test
  void repeatedBlockCountersSerializeTheWorldKeyOnlyOnce() {
    World world = Fakes.world("serialize_once");
    Block block = block(world, 53, -23);
    Sampler sampler = sampler("physics");
    ObserverController controller = new ObserverController();

    for (int event = 0; event < 100; event++) {
      controller.get(block, sampler).addAndGet(1D);
    }

    Mockito.verify(world, Mockito.times(1)).getKey();
    Assertions.assertEquals(100D, controller.sample("test:serialize_once", 3, -2, sampler).orElse(0D));
    Assertions.assertEquals(
        "test:serialize_once",
        controller.sampledChunk(world, 3, -2).orElseThrow().getWorldKey()
    );
  }

  @Test
  void worldsAreKeyedByUidAndStillAddressableBySerializedKey() {
    World world = Fakes.world("dual_address");
    SampledServer server = new SampledServer();

    SampledChunk sampled = server.getWorld(world).getChunk(4, 9);

    Assertions.assertSame(sampled, server.optionalChunk(world, 4, 9).orElseThrow());
    Assertions.assertSame(sampled, server.optionalChunk("test:dual_address", 4, 9).orElseThrow());
    Assertions.assertTrue(server.getWorlds().containsKey(world.getUID()));

    server.removeWorld(world);

    Assertions.assertTrue(server.optionalChunk("test:dual_address", 4, 9).isEmpty());
    Assertions.assertTrue(server.optionalChunk(world, 4, 9).isEmpty());
  }

  private static Block block(World world, int blockX, int blockZ) {
    Block block = Mockito.mock(Block.class);
    Chunk chunk = Fakes.chunk(world, blockX >> 4, blockZ >> 4);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(blockX);
    Mockito.when(block.getZ()).thenReturn(blockZ);
    Mockito.when(block.getChunk()).thenReturn(chunk);
    return block;
  }

  private static Sampler sampler(String id) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn(id);
    return sampler;
  }
}
