package art.arcane.react.core.integration;

import art.arcane.react.React;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
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

public final class GlossEntityOverlayIntegration {
  private static final String GLOSS_API_CLASS = "art.arcane.gloss.api.GlossAPI";
  private static final long DISCOVERY_RETRY_MS = 5_000L;
  private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

  private final Supplier<ServicesManager> servicesManagerSupplier;
  private final LongSupplier clock;
  private volatile Binding binding;
  private volatile long nextDiscoveryMs;

  public GlossEntityOverlayIntegration() {
    this(Bukkit::getServicesManager, System::currentTimeMillis);
  }

  GlossEntityOverlayIntegration(Supplier<ServicesManager> servicesManagerSupplier, LongSupplier clock) {
    this.servicesManagerSupplier = Objects.requireNonNull(servicesManagerSupplier);
    this.clock = Objects.requireNonNull(clock);
  }

  public boolean refresh(LivingEntity entity, int stackCount) {
    Binding active = resolveBinding();
    if (active == null) {
      return false;
    }

    try {
      return Boolean.TRUE.equals(active.refreshMethod().invoke(active.provider(), entity, stackCount));
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      invalidate(active);
      Throwable failure = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
          ? invocation.getCause()
          : exception;
      if (REPORTED_FAILURES.add(failure.getClass().getName() + ":" + failure.getMessage())) {
        React.warn("Could not update a Gloss entity overlay; the integration will retry discovery.", failure);
      }
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
          Method refreshMethod = provider.getClass().getMethod("refreshEntityOverlay", LivingEntity.class, int.class);
          return new Binding(provider, refreshMethod, owner);
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
    return registrations == null ? List.of() : (Collection<RegisteredServiceProvider<?>>) registrations;
  }

  private synchronized void invalidate(Binding failed) {
    if (binding == failed) {
      binding = null;
      nextDiscoveryMs = clock.getAsLong() + DISCOVERY_RETRY_MS;
    }
  }

  private record Binding(Object provider, Method refreshMethod, Plugin owner) {
  }
}
