package art.arcane.react.content.action;

import art.arcane.react.platform.bukkit.BukkitGameEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionPurgeEntitiesNamedEntityTest {
  @Test
  void defaultParametersProtectPlayerNamedEntities() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams();
    Entity entity = entity("Betsy");

    Assertions.assertTrue(params.isProtectNamedEntities());
    Assertions.assertFalse(action.canPurge(new BukkitGameEntity(entity), params));
  }

  @Test
  void explicitOverrideMakesPlayerNamedEntitiesEligible() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams().setProtectNamedEntities(false);

    Assertions.assertTrue(action.canPurge(new BukkitGameEntity(entity("Betsy")), params));
  }

  private Entity entity(String customName) {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(entity.getCustomName()).thenReturn(customName);
    return entity;
  }
}
