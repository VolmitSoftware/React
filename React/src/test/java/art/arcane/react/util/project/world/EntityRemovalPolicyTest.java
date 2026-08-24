package art.arcane.react.util.project.world;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntityRemovalPolicyTest {
  @Test
  void protectsNamedEntityWhenProtectionIsEnabled() {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getCustomName()).thenReturn("Betsy");

    Assertions.assertTrue(EntityRemovalPolicy.protectsNamedEntity(entity, true));
  }

  @Test
  void allowsNamedEntityWhenProtectionIsExplicitlyDisabled() {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getCustomName()).thenReturn("Betsy");

    Assertions.assertFalse(EntityRemovalPolicy.protectsNamedEntity(entity, false));
  }

  @Test
  void doesNotTreatBlankOrMissingNamesAsPlayerNames() {
    Entity blank = Mockito.mock(Entity.class);
    Entity unnamed = Mockito.mock(Entity.class);
    Mockito.when(blank.getCustomName()).thenReturn("  ");

    Assertions.assertFalse(EntityRemovalPolicy.protectsNamedEntity(blank, true));
    Assertions.assertFalse(EntityRemovalPolicy.protectsNamedEntity(unnamed, true));
    Assertions.assertFalse(EntityRemovalPolicy.protectsNamedEntity(null, true));
  }
}
