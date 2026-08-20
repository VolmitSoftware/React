package art.arcane.react.core.integration;

import art.arcane.react.React;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class GlossDropNameIntegration {
  private static final String GLOSS_API_CLASS = "art.arcane.gloss.api.GlossAPI";
  private static final long DISCOVERY_RETRY_MS = 5_000L;
  private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

  private final Supplier<ServicesManager> servicesManagerSupplier;
  private final LongSupplier clock;

  private volatile Binding binding;
  private volatile long nextDiscoveryMs;

  public GlossDropNameIntegration() {
    this(Bukkit::getServicesManager, System::currentTimeMillis);
  }

  GlossDropNameIntegration(Supplier<ServicesManager> servicesManagerSupplier, LongSupplier clock) {
    this.servicesManagerSupplier = Objects.requireNonNull(servicesManagerSupplier);
    this.clock = Objects.requireNonNull(clock);
  }

  public boolean refresh(Item item, String bundleHeaderFormat, String bundleEntryFormat,
                         String bundleMoreFormat, int bundleEntryLimit) {
    if (item == null) {
      return false;
    }

    Binding active = resolveBinding();
    if (active == null) {
      return false;
    }

    try {
      active.refreshMethod().invoke(active.provider(), item, bundleHeaderFormat,
          bundleEntryFormat, bundleMoreFormat, bundleEntryLimit);
      return true;
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      invalidate(active);
      reportFailure(exception);
      return false;
    }
  }

  public boolean remove(Item item) {
    if (item == null) {
      return false;
    }

    Binding active = resolveBinding();
    if (active == null) {
      return false;
    }

    try {
      active.removeMethod().invoke(active.provider(), item);
      return true;
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      invalidate(active);
      reportFailure(exception);
      return false;
    }
  }

  private Binding resolveBinding() {
    Binding active = binding;
    if (active != null && active.owner().isEnabled()) {
      return active;
    }

    long now = clock.getAsLong();
    if (now < nextDiscoveryMs) {
      return null;
    }

    synchronized (this) {
      active = binding;
      if (active != null && active.owner().isEnabled()) {
        return active;
      }
      if (now < nextDiscoveryMs) {
        return null;
      }

      nextDiscoveryMs = now + DISCOVERY_RETRY_MS;
      binding = discover();
      return binding;
    }
  }

  private Binding discover() {
    ServicesManager servicesManager = servicesManagerSupplier.get();
    if (servicesManager == null) {
      return null;
    }

    for (Class<?> serviceClass : servicesManager.getKnownServices()) {
      if (serviceClass == null || !GLOSS_API_CLASS.equals(serviceClass.getName())) {
        continue;
      }

      for (RegisteredServiceProvider<?> registration : registrations(servicesManager, serviceClass)) {
        Plugin owner = registration.getPlugin();
        Object provider = registration.getProvider();
        if (owner == null || !owner.isEnabled() || provider == null) {
          continue;
        }

        try {
          Method refreshMethod = provider.getClass().getMethod(
              "refreshDropName", Item.class, String.class, String.class, String.class, int.class);
          Method removeMethod = provider.getClass().getMethod("removeDropPresentation", Item.class);
          return new Binding(provider, refreshMethod, removeMethod, owner);
        } catch (NoSuchMethodException ignored) {
          continue;
        }
      }
    }
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Collection<RegisteredServiceProvider<?>> registrations(ServicesManager servicesManager, Class<?> serviceClass) {
    Collection registrations = servicesManager.getRegistrations((Class) serviceClass);
    if (registrations == null || registrations.isEmpty()) {
      return List.of();
    }
    return (Collection<RegisteredServiceProvider<?>>) registrations;
  }

  private synchronized void invalidate(Binding failed) {
    if (binding == failed) {
      binding = null;
      nextDiscoveryMs = clock.getAsLong() + DISCOVERY_RETRY_MS;
    }
  }

  private void reportFailure(Throwable exception) {
    Throwable failure = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
        ? invocation.getCause()
        : exception;
    String failureKey = failure.getClass().getName() + ":" + failure.getMessage();
    if (REPORTED_FAILURES.add(failureKey)) {
      React.warn("Could not update a Gloss drop presentation; the integration will retry discovery.");
      failure.printStackTrace();
    }
  }

  private record Binding(Object provider, Method refreshMethod, Method removeMethod, Plugin owner) {
  }
}
