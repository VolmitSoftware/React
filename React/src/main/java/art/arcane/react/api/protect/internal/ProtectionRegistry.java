package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactEntityGuardEvent;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ProtectionRegistry implements ProtectionBinding {
  public static final long DEFAULT_MASK_RETENTION_MS = 300_000L;
  public static final String BUILT_IN_PROVIDER_ID = "react";

  private static final Predicate<String> NO_TAG = tag -> false;

  private final String namespace;
  private final ProtectionMaskCache cache = new ProtectionMaskCache();
  private final ProtectionProviderGateway gateway;
  private final Map<String, ProtectionDeclaration> lastGood = new LinkedHashMap<>();
  private final ThreadLocal<Boolean> inGuard = new ThreadLocal<>();

  private volatile ProtectionRuleSet ruleSet = ProtectionRuleSet.empty();
  private volatile List<ReactProtectionRule> builtInRules = List.of();
  private volatile Predicate<Entity> stateAccessible = entity -> true;
  private volatile BooleanSupplier primaryThread = () -> true;
  private volatile Consumer<String> log = message -> {
  };
  private volatile long claimCount;

  public ProtectionRegistry(String namespace) {
    this.namespace = namespace == null || namespace.isBlank() ? "react" : namespace;
    this.gateway = new ProtectionProviderGateway(
        ProtectionProviderGateway.DEFAULT_FAULT_LIMIT,
        ProtectionProviderGateway.DEFAULT_SLOW_PROVIDER_MILLIS,
        message -> log.accept(message));
  }

  public ProtectionRegistry withLog(Consumer<String> sink) {
    this.log = sink == null ? message -> {
    } : sink;
    return this;
  }

  public ProtectionRegistry withStateAccessibility(Predicate<Entity> accessible) {
    this.stateAccessible = accessible == null ? entity -> true : accessible;
    return this;
  }

  public ProtectionRegistry withPrimaryThreadProbe(BooleanSupplier probe) {
    this.primaryThread = probe == null ? () -> true : probe;
    return this;
  }

  public ProtectionRegistry withBuiltInRules(List<ReactProtectionRule> rules) {
    this.builtInRules = rules == null ? List.of() : List.copyOf(rules);
    recompile();
    return this;
  }

  public String namespace() {
    return namespace;
  }

  public ProtectionRuleSet ruleSet() {
    return ruleSet;
  }

  public ProtectionProviderGateway gateway() {
    return gateway;
  }

  public ProtectionMaskCache cache() {
    return cache;
  }

  @Override
  public int operationsFor(Entity entity) {
    if (entity == null) {
      return ReactOperations.NONE;
    }

    UUID id = entity.getUniqueId();
    ProtectionMaskCache.Entry entry = cache.get(id);

    if (entry == null) {
      return hydrate(entity, id);
    }

    ProtectionRuleSet set = ruleSet;

    if (!set.isMarkerSensitive()) {
      return entry.mask();
    }

    return entry.mask() | liveMarkerMask(entity, set);
  }

  @Override
  public boolean isProtected(Entity entity, ReactOperation operation) {
    return ReactOperations.covers(operationsFor(entity), operation);
  }

  @Override
  public String ownerOf(Entity entity) {
    if (entity == null) {
      return "";
    }

    UUID id = entity.getUniqueId();
    ProtectionMaskCache.Entry entry = cache.get(id);

    if (entry == null) {
      hydrate(entity, id);
      entry = cache.get(id);
    }

    return entry == null ? "" : entry.owners();
  }

  @Override
  public int hydrate(Entity entity) {
    return entity == null ? ReactOperations.NONE : hydrate(entity, entity.getUniqueId());
  }

  @Override
  public boolean protect(Entity entity, String ownerName, int operations) {
    int mask = ReactOperations.sanitize(operations);

    if (entity == null || mask == ReactOperations.NONE) {
      return false;
    }

    String token = ProtectionText.toOwnerToken(ownerName);

    if (token.isEmpty()) {
      return false;
    }

    if (!stateAccessible.test(entity)) {
      log.accept("[protect] refused protect(" + token + ") for " + entity.getUniqueId()
          + ": entity state is not accessible from this thread");
      return false;
    }

    NamespacedKey key = ProtectionClaims.keyFor(namespace, token);

    if (key == null) {
      return false;
    }

    ProtectionClaims.add(entity.getPersistentDataContainer(), key, mask);
    cache.invalidate(entity.getUniqueId());
    claimCount++;
    return true;
  }

  @Override
  public boolean invalidate(Entity entity) {
    if (entity == null) {
      return false;
    }

    cache.invalidate(entity.getUniqueId());
    return true;
  }

  @Override
  public boolean release(Entity entity, String ownerName) {
    if (entity == null) {
      return false;
    }

    String token = ProtectionText.toOwnerToken(ownerName);

    if (token.isEmpty()) {
      return false;
    }

    if (!stateAccessible.test(entity)) {
      log.accept("[protect] refused release(" + token + ") for " + entity.getUniqueId()
          + ": entity state is not accessible from this thread");
      return false;
    }

    NamespacedKey key = ProtectionClaims.keyFor(namespace, token);

    if (key == null) {
      return false;
    }

    boolean removed = ProtectionClaims.remove(entity.getPersistentDataContainer(), key);
    cache.invalidate(entity.getUniqueId());
    return removed;
  }

  @Override
  public boolean spawnProtected(EntityType type, String worldName, CreatureSpawnEvent.SpawnReason reason) {
    ProtectionRuleSet set = ruleSet;

    if (set.isEmpty() || !ReactOperations.covers(set.declaredOperations(), ReactOperation.SPAWN_CAP)) {
      return false;
    }

    return ReactOperations.covers(set.spawnMask(type, worldName, reason), ReactOperation.SPAWN_CAP);
  }

  @Override
  public boolean guardAllows(Entity entity, ReactOperation operation) {
    if (entity == null || operation == null) {
      return true;
    }

    if (ReactEntityGuardEvent.getHandlerList().getRegisteredListeners().length == 0) {
      return true;
    }

    if (Boolean.TRUE.equals(inGuard.get())) {
      return true;
    }

    inGuard.set(Boolean.TRUE);

    try {
      ReactEntityGuardEvent event = new ReactEntityGuardEvent(entity, operation, !primaryThread.getAsBoolean());
      Bukkit.getPluginManager().callEvent(event);
      return !event.isCancelled();
    } catch (Throwable e) {
      log.accept("[protect] guard event for " + operation + " on " + entity.getUniqueId() + " failed: "
          + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage())
          + "; treating as a veto");
      return false;
    } finally {
      inGuard.remove();
    }
  }

  public int reconcile(List<ProviderBinding> bindings) {
    List<ProviderBinding> safe = bindings == null ? List.of() : bindings;
    Map<String, ProtectionDeclaration> next = new LinkedHashMap<>();
    Set<String> seen = new LinkedHashSet<>();

    for (ProviderBinding binding : safe) {
      if (binding == null || binding.provider() == null) {
        continue;
      }

      ProtectionProviderGateway.Collected collected = gateway.collect(binding.provider(), binding.pluginName());
      String providerId = collected.providerId();

      if (providerId.isEmpty()) {
        continue;
      }

      seen.add(providerId);

      if (collected.declaration() != null) {
        next.put(providerId, collected.declaration());
      }
    }

    synchronized (lastGood) {
      lastGood.keySet().retainAll(seen);
      lastGood.putAll(next);
    }

    recompile();
    return ruleSet.size();
  }

  public void clear() {
    synchronized (lastGood) {
      lastGood.clear();
    }

    cache.clear();
    recompile();
  }

  @Override
  public int sweep(long nowMs, long retentionMs) {
    return cache.sweep(nowMs, retentionMs);
  }

  public Stats stats() {
    ProtectionRuleSet set = ruleSet;
    return new Stats(set.size(), cache.size(), cache.protectedCount(), claimCount,
        gateway.totalFaults(), gateway.totalRefusals(), gateway.quarantinedProviders());
  }

  private void recompile() {
    List<ProtectionDeclaration> declarations = new ArrayList<>();
    List<ReactProtectionRule> builtIns = builtInRules;

    if (!builtIns.isEmpty()) {
      declarations.add(new ProtectionDeclaration(BUILT_IN_PROVIDER_ID, "React", builtIns));
    }

    synchronized (lastGood) {
      declarations.addAll(lastGood.values());
    }

    ProtectionRuleSet compiled = ProtectionRuleSet.compile(declarations);

    if (compiled.equals(ruleSet)) {
      return;
    }

    ruleSet = compiled;
    cache.clear();
  }

  private int hydrate(Entity entity, UUID id) {
    ProtectionRuleSet set = ruleSet;

    if (!stateAccessible.test(entity)) {
      return cache.maskOr(id, ReactOperations.NONE);
    }

    PersistentDataContainer container = entity.getPersistentDataContainer();
    boolean empty = container == null || container.isEmpty();
    int stable = ReactOperations.NONE;
    int markers = ReactOperations.NONE;
    String owners = "";

    if (!set.isEmpty()) {
      Predicate<String> tagProbe = NO_TAG;

      if (set.isTagSensitive()) {
        Set<String> tags = entity.getScoreboardTags();
        tagProbe = tags == null ? NO_TAG : tags::contains;
      }

      String world = set.isWorldSensitive() ? worldNameOf(entity) : "";
      stable = set.stableMask(entity.getType(), world, tagProbe);

      if (!empty && set.isMarkerSensitive()) {
        markers = set.markerMask(entity.getType(), world, key -> container.has(key), tagProbe);
      }
    }

    if (!empty) {
      ProtectionClaims.Scan scan = ProtectionClaims.scan(container, namespace);
      stable |= scan.mask();
      owners = scan.owners();
    }

    cache.put(id, stable, owners, System.currentTimeMillis());
    return stable | markers;
  }

  private int liveMarkerMask(Entity entity, ProtectionRuleSet set) {
    if (!stateAccessible.test(entity)) {
      return ReactOperations.NONE;
    }

    PersistentDataContainer container = entity.getPersistentDataContainer();

    if (container == null || container.isEmpty()) {
      return ReactOperations.NONE;
    }

    Predicate<String> tagProbe = NO_TAG;

    if (set.isMarkerRuleTagSensitive()) {
      Set<String> tags = entity.getScoreboardTags();
      tagProbe = tags == null ? NO_TAG : tags::contains;
    }

    String world = set.isMarkerRuleWorldSensitive() ? worldNameOf(entity) : "";
    return set.markerMask(entity.getType(), world, key -> container.has(key), tagProbe);
  }

  private static String worldNameOf(Entity entity) {
    try {
      return entity.getWorld() == null ? "" : entity.getWorld().getName();
    } catch (Throwable ignored) {
      return "";
    }
  }

  public record ProviderBinding(ReactProtectionProvider provider, String pluginName) {
  }

  public record Stats(int compiledRules, int cachedEntities, int protectedEntities, long claimWrites,
                      long providerFaults, long providerRefusals, Set<String> quarantinedProviders) {
  }
}
