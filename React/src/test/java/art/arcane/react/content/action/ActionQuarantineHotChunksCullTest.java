package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class ActionQuarantineHotChunksCullTest {
  @Test
  void unloadingQuarantineRemovesCulledEntitiesBeforeTheChunkIsSaved() throws ReflectiveOperationException {
    Fixture fixture = new Fixture();
    ActionQuarantineHotChunks.Params params = ActionQuarantineHotChunks.Params.builder().unloadChunk(true).build();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<WorldIdentity> identities = Mockito.mockStatic(WorldIdentity.class)) {
      fixture.bind(react, identities);

      fixture.quarantine(params);

      Mockito.verify(fixture.zombie).remove();
      react.verify(() -> React.kill(Mockito.any(), Mockito.anyInt()), Mockito.never());
      Assertions.assertTrue(fixture.removedBeforeUnload.get());
    }
  }

  @Test
  void loadedQuarantineKeepsTheVisibleCountdown() throws ReflectiveOperationException {
    Fixture fixture = new Fixture();
    ActionQuarantineHotChunks.Params params = ActionQuarantineHotChunks.Params.builder().unloadChunk(false).build();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<WorldIdentity> identities = Mockito.mockStatic(WorldIdentity.class)) {
      fixture.bind(react, identities);

      fixture.quarantine(params);

      Mockito.verify(fixture.zombie, Mockito.never()).remove();
      react.verify(() -> React.kill(Mockito.eq(fixture.zombie), Mockito.anyInt()));
      Mockito.verify(fixture.chunk, Mockito.never()).unload(Mockito.anyBoolean());
    }
  }

  private static final class Fixture {
    private final ActionQuarantineHotChunks action = new ActionQuarantineHotChunks();
    private final World world = Mockito.mock(World.class);
    private final Chunk chunk = Mockito.mock(Chunk.class);
    private final Zombie zombie = Mockito.mock(Zombie.class);
    private final NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    private final AtomicBoolean loaded = new AtomicBoolean(true);
    private final AtomicBoolean removed = new AtomicBoolean(false);
    private final AtomicBoolean removedBeforeUnload = new AtomicBoolean(false);

    private Fixture() {
      Mockito.when(world.isChunkLoaded(3, 4)).thenAnswer(invocation -> loaded.get());
      Mockito.when(world.getChunkAt(3, 4)).thenReturn(chunk);
      Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{zombie});
      Mockito.when(chunk.unload(true)).thenAnswer(invocation -> {
        removedBeforeUnload.set(removed.get());
        loaded.set(false);
        return true;
      });
      Mockito.when(zombie.getTicksLived()).thenReturn(10_000);
      Mockito.doAnswer(invocation -> {
        removed.set(true);
        return null;
      }).when(zombie).remove();
      Mockito.when(playerIndex.isInitialSeedReady()).thenReturn(true);
    }

    private void bind(MockedStatic<React> react, MockedStatic<WorldIdentity> identities) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);
      identities.when(() -> WorldIdentity.resolve("test:world")).thenReturn(Optional.of(world));
    }

    private void quarantine(ActionQuarantineHotChunks.Params params) throws ReflectiveOperationException {
      Class<?> refType = Class.forName("art.arcane.react.content.action.ActionQuarantineHotChunks$ChunkRef");
      Constructor<?> constructor = refType.getDeclaredConstructor(String.class, int.class, int.class);
      constructor.setAccessible(true);
      Object ref = constructor.newInstance("test:world", 3, 4);
      Method quarantine = ActionQuarantineHotChunks.class.getDeclaredMethod(
          "quarantineChunkSync",
          refType,
          ActionQuarantineHotChunks.Params.class
      );
      quarantine.setAccessible(true);
      quarantine.invoke(action, ref, params);
    }
  }
}
