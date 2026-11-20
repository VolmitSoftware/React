package art.arcane.react.content.feature;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.BlockHostStub;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

class FeatureLazyGravityTest {
  private final UUID worldId = UUID.randomUUID();
  private BlockHostStub host;
  private FeatureLazyGravity feature;

  @BeforeEach
  void setUp() {
    host = new BlockHostStub();
    Mockito.when(host.blocks.minHeight(worldId)).thenReturn(-64);
    Mockito.when(host.blocks.isChunkLoaded(Mockito.eq(worldId), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(host.blocks.blockType(Mockito.eq(worldId), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
        .thenAnswer(invocation -> invocation.<Integer>getArgument(2) > 60 ? "AIR" : "STONE");
    feature = new FeatureLazyGravity();
    feature.onActivate();
  }

  @AfterEach
  void tearDown() {
    feature.onDeactivate();
    host.close();
  }

  @Test
  void listensAtMonitorLikeTheBukkitHandlers() {
    Assertions.assertEquals(ListenerPriority.MONITOR, host.physicsPriority);
    Assertions.assertEquals(ListenerPriority.MONITOR, host.fallingPriority);
  }

  @Test
  void physicsUpdatesReturnBeforeTouchingTheBlockWhileNothingIsTracked() {
    BlockHost.PhysicsEvent event = Mockito.mock(BlockHost.PhysicsEvent.class);

    host.physics.accept(event);

    Mockito.verifyNoInteractions(event);
  }

  @Test
  void trackedFallProjectsTheVanillaLandingTicks() {
    host.falling.accept(fall(10, 100, 10));

    Assertions.assertEquals(1L, feature.activeTaskCount());
    Assertions.assertTrue(feature.readAndResetProjectedSkippedTicks() > 0L);
  }

  @Test
  void fallsNearPlayersAreBypassed() {
    Mockito.when(host.worlds.anyPlayerNear(Mockito.any(), Mockito.anyDouble())).thenReturn(true);

    host.falling.accept(fall(10, 100, 10));

    Assertions.assertEquals(0L, feature.activeTaskCount());
    Assertions.assertEquals(1L, feature.bypassedCount());
  }

  @Test
  void invalidatedColumnsLeaveTheTrackedTaskCount() {
    host.falling.accept(fall(10, 100, 10));

    host.physics.accept(physics(10, 80, 10));

    Assertions.assertEquals(0L, feature.activeTaskCount());
  }

  @Test
  void unrelatedColumnsKeepTheirTrackedTask() {
    host.falling.accept(fall(10, 100, 10));

    host.physics.accept(physics(11, 80, 10));
    host.breaks.accept(physics(10, 80, 11));

    Assertions.assertEquals(1L, feature.activeTaskCount());
  }

  @Test
  void manyTrackedColumnsInvalidateIndependently() {
    for (int x = 0; x < 256; x++) {
      host.falling.accept(fall(x * 3, 100, -x));
    }
    Assertions.assertEquals(256L, feature.activeTaskCount());

    for (int x = 0; x < 256; x += 2) {
      host.places.accept(physics(x * 3, 70, -x));
    }

    Assertions.assertEquals(128L, feature.activeTaskCount());
  }

  @Test
  void tickHookSkipsOnlyUnderPressureAndUninstallsOnDeactivate() {
    ArgumentCaptor<BlockHost.FallingTickHook> hook = ArgumentCaptor.forClass(BlockHost.FallingTickHook.class);
    ArgumentCaptor<Runnable> installed = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(host.blocks).installFallingTickHook(hook.capture(), installed.capture());
    Assertions.assertTrue(feature.isMeasurementOnly());
    installed.getValue().run();
    Assertions.assertFalse(feature.isMeasurementOnly());

    BlockHost.FallingBlockEvent event = fall(10, 100, 10);
    host.falling.accept(event);
    Assertions.assertFalse(hook.getValue().skipTick(event.entity()));

    feature.onDeactivate();
    Mockito.verify(host.blocks).uninstallFallingTickHook();
    Assertions.assertTrue(feature.isMeasurementOnly());
    feature.onActivate();
  }

  private BlockHost.FallingBlockEvent fall(int x, int y, int z) {
    GameEntity entity = Mockito.mock(GameEntity.class);
    UUID id = UUID.randomUUID();
    Mockito.when(entity.id()).thenReturn(id);
    Mockito.when(entity.location()).thenReturn(new GameLocation(worldId, x + 0.5D, y, z + 0.5D));
    BlockHost.FallingBlockEvent event = Mockito.mock(BlockHost.FallingBlockEvent.class);
    Mockito.when(event.entity()).thenReturn(entity);
    Mockito.when(event.velocityY()).thenReturn(0D);
    return event;
  }

  private BlockHost.PhysicsEvent physics(int x, int y, int z) {
    BlockHost.PhysicsEvent event = Mockito.mock(BlockHost.PhysicsEvent.class);
    Mockito.when(event.location()).thenReturn(new GameLocation(worldId, x, y, z));
    return event;
  }
}
