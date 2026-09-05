package art.arcane.react.core.integration;

import art.arcane.gloss.api.GlossAPI;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

class GlossEntityOverlayIntegrationTest {
  @Test
  void serviceOwnsLabelsOnlyWhenRefreshReturnsTrue() {
    ServicesManager services = Mockito.mock(ServicesManager.class);
    Plugin owner = Mockito.mock(Plugin.class);
    RecordingGlossAPI provider = new RecordingGlossAPI();
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    register(services, owner, provider);
    GlossEntityOverlayIntegration integration = new GlossEntityOverlayIntegration(() -> services, () -> 1_000L);

    Assertions.assertTrue(integration.refresh(entity, 7));
    Assertions.assertSame(entity, provider.entity);
    Assertions.assertEquals(7, provider.stackCount);
    provider.enabled = false;
    Assertions.assertFalse(integration.refresh(entity, 3));
    Assertions.assertEquals(3, provider.stackCount);
    Mockito.verify(services, Mockito.times(1)).getKnownServices();
  }

  @Test
  void rediscoveryWaitsForRetryAndFindsReplacementService() {
    ServicesManager services = Mockito.mock(ServicesManager.class);
    AtomicLong clock = new AtomicLong(1_000L);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(services.getKnownServices()).thenReturn(Set.of());
    GlossEntityOverlayIntegration integration = new GlossEntityOverlayIntegration(() -> services, clock::get);

    Assertions.assertFalse(integration.refresh(entity, 4));
    Assertions.assertFalse(integration.refresh(entity, 4));
    Plugin owner = Mockito.mock(Plugin.class);
    RecordingGlossAPI provider = new RecordingGlossAPI();
    register(services, owner, provider);
    clock.addAndGet(5_000L);
    Assertions.assertTrue(integration.refresh(entity, 4));
    Mockito.when(owner.isEnabled()).thenReturn(false);
    Assertions.assertFalse(integration.refresh(entity, 4));
    Mockito.verify(services, Mockito.times(2)).getKnownServices();
  }

  private void register(ServicesManager services, Plugin owner, GlossAPI provider) {
    @SuppressWarnings("unchecked")
    RegisteredServiceProvider<GlossAPI> registration = Mockito.mock(RegisteredServiceProvider.class);
    Mockito.when(registration.getProvider()).thenReturn(provider);
    Mockito.when(registration.getPlugin()).thenReturn(owner);
    Mockito.when(owner.isEnabled()).thenReturn(true);
    Mockito.when(services.getKnownServices()).thenReturn(Set.of(GlossAPI.class));
    Mockito.when(services.getRegistrations(GlossAPI.class)).thenReturn(List.of(registration));
  }

  public static final class RecordingGlossAPI implements GlossAPI {
    private boolean enabled = true;
    private LivingEntity entity;
    private int stackCount;

    @Override
    public boolean refreshEntityOverlay(LivingEntity target, int count) {
      entity = target;
      stackCount = count;
      return enabled;
    }

    @Override
    public void refreshDropName(Item item, String header, String entry, String more, int limit) {
    }

    @Override
    public void removeDropPresentation(Item item) {
    }
  }
}
