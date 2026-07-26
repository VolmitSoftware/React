package art.arcane.react.api.protect;

import art.arcane.react.api.event.ReactCancellableEvent;
import art.arcane.react.api.event.ReactEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReactEntityGuardEventTest {

  @Test
  void theEventOwnsItsHandlerListAndDoesNotShareReactEventsList() {
    HandlerList mine = ReactEntityGuardEvent.getHandlerList();

    Assertions.assertNotNull(mine);
    Assertions.assertNotSame(mine, ReactEvent.getHandlerList());
    Assertions.assertNotSame(mine, ReactCancellableEvent.getHandlerList());
  }

  @Test
  void theEventDoesNotExtendReactsSharedEventBases() {
    Assertions.assertFalse(ReactEvent.class.isAssignableFrom(ReactEntityGuardEvent.class));
    Assertions.assertFalse(ReactCancellableEvent.class.isAssignableFrom(ReactEntityGuardEvent.class));
  }

  @Test
  void instanceHandlersAreTheStaticHandlerList() {
    ReactEntityGuardEvent event = new ReactEntityGuardEvent(
        Mockito.mock(Entity.class), ReactOperation.TRIM, false);
    Assertions.assertSame(ReactEntityGuardEvent.getHandlerList(), event.getHandlers());
  }

  @Test
  void cancellationIsCarriedOnTheEvent() {
    ReactEntityGuardEvent event = new ReactEntityGuardEvent(
        Mockito.mock(Entity.class), ReactOperation.PURGE, false);

    Assertions.assertFalse(event.isCancelled());
    event.setCancelled(true);
    Assertions.assertTrue(event.isCancelled());
    event.setCancelled(false);
    Assertions.assertFalse(event.isCancelled());
  }

  @Test
  void subjectAndOperationAreExposedVerbatim() {
    Entity entity = Mockito.mock(Entity.class);
    ReactEntityGuardEvent event = new ReactEntityGuardEvent(entity, ReactOperation.SLEEP, true);

    Assertions.assertSame(entity, event.getEntity());
    Assertions.assertEquals(ReactOperation.SLEEP, event.getOperation());
    Assertions.assertTrue(event.isAsynchronous());
  }

  @Test
  void theAsyncFlagIsMandatoryAndHonoured() {
    ReactEntityGuardEvent sync = new ReactEntityGuardEvent(
        Mockito.mock(Entity.class), ReactOperation.TRIM, false);
    Assertions.assertFalse(sync.isAsynchronous());
  }

  @Test
  void nullSubjectsAreRejectedAtConstruction() {
    Entity entity = Mockito.mock(Entity.class);
    Assertions.assertThrows(NullPointerException.class,
        () -> new ReactEntityGuardEvent(null, ReactOperation.TRIM, false));
    Assertions.assertThrows(NullPointerException.class,
        () -> new ReactEntityGuardEvent(entity, null, false));
  }
}
