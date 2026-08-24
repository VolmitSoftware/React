package art.arcane.react;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

class ReactAudienceLifecycleTest {
  private Field providerField;
  private BukkitAudiences previousProvider;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    providerField = React.class.getDeclaredField("audienceProvider");
    providerField.setAccessible(true);
    previousProvider = (BukkitAudiences) providerField.get(null);
  }

  @AfterEach
  void tearDown() throws IllegalAccessException {
    providerField.set(null, previousProvider);
  }

  @Test
  void closeAudienceProviderClosesAndClearsTheCurrentProvider() throws IllegalAccessException {
    BukkitAudiences provider = Mockito.mock(BukkitAudiences.class);
    providerField.set(null, provider);

    React.closeAudienceProvider();
    React.closeAudienceProvider();

    Mockito.verify(provider).close();
    Assertions.assertNull(providerField.get(null));
  }

  @Test
  void closeAudienceProviderClearsStateWhenCloseFails() throws IllegalAccessException {
    BukkitAudiences provider = Mockito.mock(BukkitAudiences.class);
    Mockito.doThrow(new IllegalStateException("close failed")).when(provider).close();
    providerField.set(null, provider);

    Assertions.assertDoesNotThrow(React::closeAudienceProvider);

    Assertions.assertNull(providerField.get(null));
  }
}
