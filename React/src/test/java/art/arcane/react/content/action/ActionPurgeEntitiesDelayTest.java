package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

class ActionPurgeEntitiesDelayTest {
  @Test
  void countdownFollowsTheLoadedSecondsToPurge() throws ReflectiveOperationException {
    ActionPurgeEntities action = new ActionPurgeEntities();
    Field seconds = ActionPurgeEntities.class.getDeclaredField("secondsToPurge");
    seconds.setAccessible(true);
    seconds.setInt(action, 60);
    ActionPurgeEntities.Params params = action.getDefaultParams();
    Method purge = ActionPurgeEntities.class.getDeclaredMethod("purge", Entity.class, ActionPurgeEntities.Params.class);
    purge.setAccessible(true);
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(Mockito.any(Entity.class), Mockito.any(Runnable.class), Mockito.anyInt()))
          .thenReturn(false);

      for (int attempt = 0; attempt < 32; attempt++) {
        purge.invoke(action, entity, params);
      }

      ArgumentCaptor<Integer> delays = ArgumentCaptor.forClass(Integer.class);
      react.verify(() -> React.kill(Mockito.eq(entity), delays.capture()), Mockito.times(32));
      List<Integer> captured = delays.getAllValues();
      for (int delay : captured) {
        Assertions.assertTrue(delay >= 59 && delay <= 61, "delay " + delay + " outside the configured window");
      }
    }
  }
}
