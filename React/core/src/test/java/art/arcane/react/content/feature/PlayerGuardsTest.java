package art.arcane.react.content.feature;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ReactPlayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

class PlayerGuardsTest {
  @Test
  void adaptGuardCancelsOnlyInteractionsBeyondTheWindowBudget() throws ReflectiveOperationException {
    FeatureAdaptRuntimeSurgeGuard guard = new FeatureAdaptRuntimeSurgeGuard();
    set(guard, "surge", true);
    set(guard, "maxInteractionsPerWindow", 2);
    ReactPlayer player = player(UUID.randomUUID());

    FakeAction first = new FakeAction(player);
    FakeAction second = new FakeAction(player);
    FakeAction third = new FakeAction(player);
    guard.interact(first);
    guard.interact(second);
    guard.interact(third);

    Assertions.assertFalse(first.cancelled);
    Assertions.assertFalse(second.cancelled);
    Assertions.assertTrue(third.cancelled);
  }

  @Test
  void adaptGuardIgnoresActionsWhileNotSurgingOrAlreadyCancelled() throws ReflectiveOperationException {
    FeatureAdaptRuntimeSurgeGuard guard = new FeatureAdaptRuntimeSurgeGuard();
    set(guard, "maxConsumeOpsPerWindow", 1);
    ReactPlayer player = player(UUID.randomUUID());
    FakeAction calm = new FakeAction(player);
    guard.consume(calm);
    guard.consume(calm);
    Assertions.assertFalse(calm.cancelled);

    set(guard, "surge", true);
    FakeAction cancelled = new FakeAction(player);
    cancelled.cancelled = true;
    guard.consume(cancelled);
    Map<?, ?> counters = (Map<?, ?>) get(guard, "consumeOps");
    Assertions.assertTrue(counters.isEmpty());
  }

  @Test
  void adaptGuardHonoursTheBypassPermission() throws ReflectiveOperationException {
    FeatureAdaptRuntimeSurgeGuard guard = new FeatureAdaptRuntimeSurgeGuard();
    set(guard, "surge", true);
    set(guard, "maxCombatOpsPerWindow", 1);
    ReactPlayer player = player(UUID.randomUUID());
    Mockito.when(player.hasPermission("react.secret.adapt.bypass")).thenReturn(true);

    FakeAction first = new FakeAction(player);
    FakeAction second = new FakeAction(player);
    guard.attack(first);
    guard.attack(second);

    Assertions.assertFalse(second.cancelled);
  }

  @Test
  void irisGuardIgnoresMovesWithinOneChunk() {
    FeatureIrisTerrainSurgeGuard guard = new FeatureIrisTerrainSurgeGuard();
    UUID world = UUID.randomUUID();
    FakeMove move = new FakeMove(player(UUID.randomUUID()), new GameLocation(world, 1D, 64D, 1D),
        new GameLocation(world, 15D, 64D, 15D));

    guard.move(move);

    Assertions.assertFalse(move.cancelled);
  }

  private static ReactPlayer player(UUID id) {
    ReactPlayer player = Mockito.mock(ReactPlayer.class);
    Mockito.when(player.id()).thenReturn(id);
    return player;
  }

  private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object get(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static class FakeAction implements PlayerHost.Cancellable {
    private final ReactPlayer player;
    boolean cancelled;

    private FakeAction(ReactPlayer player) {
      this.player = player;
    }

    @Override
    public ReactPlayer player() {
      return player;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class FakeMove extends FakeAction implements PlayerHost.Move {
    private final GameLocation from;
    private final GameLocation to;

    private FakeMove(ReactPlayer player, GameLocation from, GameLocation to) {
      super(player);
      this.from = from;
      this.to = to;
    }

    @Override
    public GameLocation from() {
      return from;
    }

    @Override
    public GameLocation to() {
      return to;
    }
  }
}
