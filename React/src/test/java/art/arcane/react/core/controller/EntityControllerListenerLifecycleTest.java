package art.arcane.react.core.controller;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

class EntityControllerListenerLifecycleTest {
  @Test
  void unregisterRemovesEveryRegistrationForListenerIdentity() {
    EntityController controller = new EntityController();
    controller.setEntityTickListeners(new ConcurrentHashMap<>());
    controller.setAllEntityTickListeners(new CopyOnWriteArrayList<>());
    Consumer<Entity> listener = entity -> {
    };

    controller.registerEntityTickListener(EntityType.ITEM, listener);
    controller.registerEntityTickListener(EntityType.ZOMBIE, listener);
    controller.registerEntityTickListener(listener);
    controller.unregisterEntityTickListener(listener);

    Assertions.assertTrue(controller.getAllEntityTickListeners().isEmpty());
    Assertions.assertFalse(controller.getEntityTickListeners().containsKey(EntityType.ITEM));
    Assertions.assertFalse(controller.getEntityTickListeners().containsKey(EntityType.ZOMBIE));
  }
}
