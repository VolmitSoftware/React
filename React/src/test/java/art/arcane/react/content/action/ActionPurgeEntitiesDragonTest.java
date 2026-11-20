package art.arcane.react.content.action;

import art.arcane.react.platform.bukkit.BukkitGameEntity;
import art.arcane.react.model.FilterParams;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionPurgeEntitiesDragonTest {
  @Test
  void defaultParametersProtectEnderDragons() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams();

    Assertions.assertFalse(params.getEntityFilter().allows("ENDER_DRAGON"));
    Assertions.assertFalse(action.canPurge(new BukkitGameEntity(entity(EntityType.ENDER_DRAGON)), params));
  }

  @Test
  void emptyBlacklistStillProtectsEnderDragons() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams().setEntityFilter(
        FilterParams.<String>builder().blacklist(true).build());

    Assertions.assertTrue(params.getEntityFilter().allows("ENDER_DRAGON"));
    Assertions.assertFalse(action.canPurge(new BukkitGameEntity(entity(EntityType.ENDER_DRAGON)), params));
  }

  @Test
  void blacklistWithoutEnderDragonsStillProtectsThem() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams().setEntityFilter(
        FilterParams.<String>builder().type("PLAYER").blacklist(true).build());

    Assertions.assertTrue(params.getEntityFilter().allows("ENDER_DRAGON"));
    Assertions.assertFalse(action.canPurge(new BukkitGameEntity(entity(EntityType.ENDER_DRAGON)), params));
  }

  @Test
  void explicitWhitelistCannotOverrideEnderDragonProtection() {
    ActionPurgeEntities action = new ActionPurgeEntities();
    ActionPurgeEntities.Params params = action.getDefaultParams()
        .setEntityFilter(FilterParams.<String>builder().type("ENDER_DRAGON").blacklist(false).build())
        .setProtectNamedEntities(false);

    Assertions.assertTrue(params.getEntityFilter().allows("ENDER_DRAGON"));
    Assertions.assertFalse(action.canPurge(new BukkitGameEntity(entity(EntityType.ENDER_DRAGON)), params));
  }

  @Test
  void defaultParametersStillAllowOrdinaryMobs() {
    ActionPurgeEntities action = new ActionPurgeEntities();

    Assertions.assertTrue(action.canPurge(new BukkitGameEntity(entity(EntityType.ZOMBIE)), action.getDefaultParams()));
  }

  private Entity entity(EntityType type) {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getType()).thenReturn(type);
    return entity;
  }
}
