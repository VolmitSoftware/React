package art.arcane.react.core.integration;

import art.arcane.gloss.api.GlossAPI;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlossDropNameIntegrationTest {
  @Test
  void discoveredProviderRefreshesEveryChangedItemAndIsCached() {
    ServicesManager servicesManager = Mockito.mock(ServicesManager.class);
    Plugin owner = Mockito.mock(Plugin.class);
    RecordingGlossAPI provider = new RecordingGlossAPI();
    RegisteredServiceProvider<GlossAPI> registration = registration(provider, owner);
    Item first = Mockito.mock(Item.class);
    Item second = Mockito.mock(Item.class);
    Mockito.when(owner.isEnabled()).thenReturn(true);
    Mockito.when(servicesManager.getKnownServices()).thenReturn(Set.of(GlossAPI.class));
    Mockito.when(servicesManager.getRegistrations(GlossAPI.class)).thenReturn(List.of(registration));
    GlossDropNameIntegration integration = new GlossDropNameIntegration(() -> servicesManager, () -> 1_000L);

    integration.refresh(first, "first {contents}", 2);
    integration.refresh(second, "second {contents}", 4);

    assertEquals(List.of(first, second), provider.refreshed);
    assertEquals(List.of("first {contents}", "second {contents}"), provider.formats);
    assertEquals(List.of(2, 4), provider.entryLimits);
    Mockito.verify(servicesManager, Mockito.times(1)).getKnownServices();
  }

  @Test
  void absentGlossDiscoveryIsThrottled() {
    ServicesManager servicesManager = Mockito.mock(ServicesManager.class);
    AtomicLong clock = new AtomicLong(1_000L);
    Mockito.when(servicesManager.getKnownServices()).thenReturn(Set.of());
    GlossDropNameIntegration integration = new GlossDropNameIntegration(() -> servicesManager, clock::get);
    Item item = Mockito.mock(Item.class);

    integration.refresh(item, "{contents}", 3);
    integration.refresh(item, "{contents}", 3);
    clock.addAndGet(5_000L);
    integration.refresh(item, "{contents}", 3);

    Mockito.verify(servicesManager, Mockito.times(2)).getKnownServices();
  }

  private RegisteredServiceProvider<GlossAPI> registration(GlossAPI provider, Plugin owner) {
    @SuppressWarnings("unchecked")
    RegisteredServiceProvider<GlossAPI> registration = Mockito.mock(RegisteredServiceProvider.class);
    Mockito.when(registration.getProvider()).thenReturn(provider);
    Mockito.when(registration.getPlugin()).thenReturn(owner);
    return registration;
  }

  public static final class RecordingGlossAPI implements GlossAPI {
    private final List<Item> refreshed = new ArrayList<>();
    private final List<String> formats = new ArrayList<>();
    private final List<Integer> entryLimits = new ArrayList<>();

    @Override
    public void refreshDropName(Item item, String bundleFormat, int bundleEntryLimit) {
      refreshed.add(item);
      formats.add(bundleFormat);
      entryLimits.add(bundleEntryLimit);
    }
  }
}
