package art.arcane.react.util.project.world;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class EntityKillerNamePreservationTest {
  @Test
  void countdownNeverOverwritesOrClearsAnExistingCustomName() {
    Entity entity = Mockito.mock(Entity.class);
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    AtomicReference<Runnable> recurringTask = new AtomicReference<>();
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getCustomName()).thenReturn("Betsy");
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(20))).thenAnswer(invocation -> {
        recurringTask.set(invocation.getArgument(0));
        return 7;
      });
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(1);
        task.run();
        return true;
      });
      EntityKiller killer = new EntityKiller(entity, 10);

      Assertions.assertNotNull(recurringTask.get());
      recurringTask.get().run();
      killer.stop();
    }

    Mockito.verify(container).set(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE), Mockito.eq((byte) 1));
    Mockito.verify(container).remove(Mockito.any(NamespacedKey.class));
    Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
    Mockito.verify(entity, Mockito.never()).setCustomNameVisible(Mockito.anyBoolean());
  }
}
