package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import art.arcane.react.api.protect.internal.ProtectionRegistry;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProtectionController extends TickedObject implements IController, Listener {
  public static final NamespacedKey LEGACY_NO_STACK_KEY = new NamespacedKey("volmit", "no-stack");
  public static final String LEGACY_RULE_ID = "legacy-no-stack";

  private static final long RECONCILE_INTERVAL_MS = 30_000L;

  private final transient AtomicBoolean dirty = new AtomicBoolean(true);
  private transient ProtectionRegistry registry;
  private transient long lastReconcileMs;

  public ProtectionController() {
    super("react", "protection", 5000);
  }

  @Override
  public String getName() {
    return "Protection";
  }

  @Override
  public String getId() {
    return "protection";
  }

  @Override
  public void start() {
    registry = new ProtectionRegistry("react")
        .withLog(React::verbose)
        .withPrimaryThreadProbe(J::isPrimaryThread)
        .withStateAccessibility(ProtectionController::entityStateAccessible)
        .withBuiltInRules(builtInRules());

    lastReconcileMs = 0L;
    dirty.set(true);
    ProtectionInstaller.install(registry);
  }

  @Override
  public void postStart() {
    reconcile(System.currentTimeMillis());
  }

  @Override
  public void stop() {
    ProtectionRegistry current = registry;

    if (current != null) {
      ProtectionInstaller.uninstall(current);
      current.clear();
    }

    registry = null;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();

    if (!dirty.get() && now - lastReconcileMs < RECONCILE_INTERVAL_MS) {
      return;
    }

    reconcile(now);
  }

  @EventHandler
  public void on(ServiceRegisterEvent event) {
    if (event.getProvider() != null && event.getProvider().getProvider() instanceof ReactProtectionProvider) {
      dirty.set(true);
    }
  }

  @EventHandler
  public void on(ServiceUnregisterEvent event) {
    if (event.getProvider() != null && event.getProvider().getProvider() instanceof ReactProtectionProvider) {
      dirty.set(true);
    }
  }

  @EventHandler
  public void on(PluginDisableEvent event) {
    dirty.set(true);
  }

  static List<ReactProtectionRule> builtInRules() {
    int legacyOperations = ReactOperations.of(
        ReactOperation.STACK,
        ReactOperation.TRIM,
        ReactOperation.PURGE,
        ReactOperation.SLEEP,
        ReactOperation.DESPAWN);

    return List.of(ReactProtectionRule.of(LEGACY_RULE_ID, legacyOperations).withMarkerKeys(LEGACY_NO_STACK_KEY));
  }

  private void reconcile(long now) {
    ProtectionRegistry current = registry;

    if (current == null) {
      return;
    }

    dirty.set(false);
    lastReconcileMs = now;

    List<ProtectionRegistry.ProviderBinding> bindings = new ArrayList<>();

    try {
      Collection<RegisteredServiceProvider<ReactProtectionProvider>> registrations =
          Bukkit.getServicesManager().getRegistrations(ReactProtectionProvider.class);

      for (RegisteredServiceProvider<ReactProtectionProvider> registration : registrations) {
        if (registration == null
            || registration.getProvider() == null
            || registration.getPlugin() == null
            || !registration.getPlugin().isEnabled()) {
          continue;
        }

        bindings.add(new ProtectionRegistry.ProviderBinding(
            registration.getProvider(),
            registration.getPlugin().getName()));
      }
    } catch (Throwable e) {
      React.warn("[protect] provider discovery failed: " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()), e);
      return;
    }

    current.reconcile(bindings);
  }

  private static boolean entityStateAccessible(Entity entity) {
    if (entity == null) {
      return false;
    }

    return !J.isFoliaThreading() || J.isOwnedByCurrentRegion(entity);
  }
}
