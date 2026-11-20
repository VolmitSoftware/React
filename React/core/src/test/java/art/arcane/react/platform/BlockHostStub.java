package art.arcane.react.platform;

import org.mockito.Mockito;

import java.util.function.Consumer;

/**
 * A mocked {@link BlockHost}, {@link ChunkHost} and {@link WorldHost} bound as the platform, keeping the listeners features subscribe.
 */
public final class BlockHostStub implements AutoCloseable {
  public final BlockHost blocks = Mockito.mock(BlockHost.class);
  public final ChunkHost chunks = Mockito.mock(ChunkHost.class);
  public final WorldHost worlds = Mockito.mock(WorldHost.class);
  public final ReactPlatform platform = Mockito.mock(ReactPlatform.class);
  public Consumer<BlockHost.BlockEvent> breaks;
  public Consumer<BlockHost.BlockEvent> places;
  public Consumer<BlockHost.PhysicsEvent> physics;
  public ListenerPriority physicsPriority;
  public Consumer<BlockHost.FluidFlowEvent> flows;
  public Consumer<BlockHost.FluidLevelEvent> levels;
  public Consumer<BlockHost.FallingBlockEvent> falling;
  public ListenerPriority fallingPriority;

  @SuppressWarnings("unchecked")
  public BlockHostStub() {
    Mockito.when(platform.blockHost()).thenReturn(java.util.Optional.of(blocks));
    Mockito.when(platform.chunkHost()).thenReturn(java.util.Optional.of(chunks));
    Mockito.when(platform.worldHost()).thenReturn(java.util.Optional.of(worlds));
    Mockito.when(blocks.onBreak(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      breaks = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(blocks.onPlace(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      places = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(blocks.onPhysics(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      physicsPriority = invocation.getArgument(0);
      physics = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(blocks.onFluidFlow(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      flows = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(blocks.onFluidLevelChange(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      levels = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(blocks.onFallingBlockChange(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenAnswer(invocation -> {
      fallingPriority = invocation.getArgument(0);
      falling = invocation.getArgument(2);
      return Subscription.NONE;
    });
    Mockito.when(chunks.onChunkLoad(Mockito.any(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(chunks.onChunkUnload(Mockito.any(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(chunks.onWorldUnload(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
  }

  @Override
  public void close() {
    ReactPlatforms.unbind();
  }
}
