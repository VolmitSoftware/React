package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactModProtectionProvider;
import art.arcane.react.api.protect.ReactModProtectionRule;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Entity protection on mod servers: mod providers' standing rules plus per-entity claims kept as entity tags of the
 * form {@code react-protect:<owner>=<mask>}.
 */
public final class ModProtectionEngine {
  public static final String CLAIM_PREFIX = "react-protect:";
  public static final int MAX_RULES_PER_PROVIDER = 64;
  private static volatile ModProtectionEngine active;

  private final ProtectionEntities entities;
  private final Consumer<String> log;
  private final Map<String, List<CompiledRule>> lastGood = new LinkedHashMap<>();
  private final Set<String> faulted = new LinkedHashSet<>();
  private volatile List<CompiledRule> rules = List.of();

  public ModProtectionEngine(ProtectionEntities entities, Consumer<String> log) {
    this.entities = entities;
    this.log = log == null ? message -> {
    } : log;
  }

  public static ModProtectionEngine active() {
    return active;
  }

  public void install() {
    active = this;
  }

  public void uninstall() {
    if (active == this) {
      active = null;
    }
  }

  /** Re-reads every provider's rules; a provider that throws keeps its last good rules. */
  public int reconcile(List<Provider> providers) {
    Map<String, List<CompiledRule>> next = new LinkedHashMap<>();
    for (Provider binding : providers == null ? List.<Provider>of() : providers) {
      if (binding == null || binding.provider() == null) {
        continue;
      }
      String providerId = providerId(binding);
      if (providerId.isEmpty()) {
        continue;
      }
      try {
        List<ReactModProtectionRule> declared = binding.provider().rules();
        if (declared == null) {
          throw new IllegalStateException("rules() returned null");
        }
        List<CompiledRule> compiled = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ReactModProtectionRule rule : declared) {
          if (compiled.size() >= MAX_RULES_PER_PROVIDER) {
            break;
          }
          CompiledRule candidate = compile(providerId, rule, seen);
          if (candidate != null) {
            compiled.add(candidate);
          }
        }
        next.put(providerId, List.copyOf(compiled));
        faulted.remove(providerId);
      } catch (Throwable failure) {
        synchronized (lastGood) {
          List<CompiledRule> previous = lastGood.get(providerId);
          next.put(providerId, previous == null ? List.of() : previous);
        }
        if (faulted.add(providerId)) {
          log.accept("[protect] provider " + providerId + " from " + binding.owner() + " failed: "
              + failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : " - " + failure.getMessage()));
        }
      }
    }
    List<CompiledRule> flattened = new ArrayList<>();
    synchronized (lastGood) {
      lastGood.clear();
      lastGood.putAll(next);
      for (List<CompiledRule> providerRules : lastGood.values()) {
        flattened.addAll(providerRules);
      }
    }
    rules = List.copyOf(flattened);
    return flattened.size();
  }

  public int operationsFor(Object entity) {
    if (entity == null || !entities.isEntity(entity)) {
      return ReactOperations.NONE;
    }
    Set<String> tags = tagsOf(entity);
    int mask = claimMask(tags);
    List<CompiledRule> current = rules;
    if (current.isEmpty()) {
      return mask;
    }
    String type = text(entities.typeId(entity));
    String world = text(entities.worldName(entity)).toLowerCase(Locale.ROOT);
    for (CompiledRule rule : current) {
      if ((mask & rule.operations()) == rule.operations()) {
        continue;
      }
      if (rule.matchesType(type) && rule.matchesWorld(world) && rule.matchesMarkers(tags) && rule.matchesTags(tags)) {
        mask |= rule.operations();
      }
    }
    return mask;
  }

  public boolean isProtected(Object entity, ReactOperation operation) {
    return ReactOperations.covers(operationsFor(entity), operation);
  }

  public String ownerOf(Object entity) {
    if (entity == null || !entities.isEntity(entity)) {
      return "";
    }
    List<String> owners = new ArrayList<>();
    for (String tag : tagsOf(entity)) {
      Claim claim = Claim.parse(tag);
      if (claim != null) {
        owners.add(claim.owner());
      }
    }
    Collections.sort(owners);
    return String.join(",", owners);
  }

  public boolean protect(Object entity, String modId, int operations) {
    int mask = ReactOperations.sanitize(operations);
    String token = ProtectionText.toOwnerToken(modId);
    if (entity == null || mask == ReactOperations.NONE || token.isEmpty() || !entities.isEntity(entity)) {
      return false;
    }
    int merged = mask;
    for (String tag : tagsOf(entity)) {
      Claim claim = Claim.parse(tag);
      if (claim != null && claim.owner().equals(token)) {
        merged |= claim.mask();
        entities.removeTag(entity, tag);
      }
    }
    return entities.addTag(entity, CLAIM_PREFIX + token + "=" + merged);
  }

  public boolean release(Object entity, String modId) {
    String token = ProtectionText.toOwnerToken(modId);
    if (entity == null || token.isEmpty() || !entities.isEntity(entity)) {
      return false;
    }
    boolean removed = false;
    for (String tag : tagsOf(entity)) {
      Claim claim = Claim.parse(tag);
      if (claim != null && claim.owner().equals(token)) {
        removed |= entities.removeTag(entity, tag);
      }
    }
    return removed;
  }

  public boolean invalidate(Object entity) {
    return entity != null && entities.isEntity(entity);
  }

  /** True when a standing rule keeps React's spawn caps from refusing this spawn. */
  public boolean spawnProtected(String typeId, String worldName, String spawnReason) {
    String type = text(typeId);
    String world = text(worldName).toLowerCase(Locale.ROOT);
    String reason = text(spawnReason).toUpperCase(Locale.ROOT);
    for (CompiledRule rule : rules) {
      if (!ReactOperations.covers(rule.operations(), ReactOperation.SPAWN_CAP)
          || !rule.markerKeys().isEmpty() || !rule.scoreboardTags().isEmpty()) {
        continue;
      }
      if (!rule.matchesType(type) || !rule.matchesWorld(world)) {
        continue;
      }
      if (!rule.spawnReasons().isEmpty() && !rule.spawnReasons().contains(reason)) {
        continue;
      }
      return true;
    }
    return false;
  }

  public int ruleCount() {
    return rules.size();
  }

  private Set<String> tagsOf(Object entity) {
    Set<String> tags = entities.tags(entity);
    return tags == null ? Set.of() : Set.copyOf(tags);
  }

  private static int claimMask(Set<String> tags) {
    int mask = ReactOperations.NONE;
    for (String tag : tags) {
      Claim claim = Claim.parse(tag);
      if (claim != null) {
        mask |= claim.mask();
      }
    }
    return mask;
  }

  private String providerId(Provider binding) {
    try {
      String raw = binding.provider().providerId();
      return ProtectionText.sanitize(raw, 64);
    } catch (Throwable failure) {
      log.accept("[protect] provider from " + binding.owner() + " refused: providerId() threw "
          + failure.getClass().getSimpleName());
      return "";
    }
  }

  private static CompiledRule compile(String providerId, ReactModProtectionRule rule, Set<String> seen) {
    if (rule == null || ReactOperations.sanitize(rule.operations()) == ReactOperations.NONE) {
      return null;
    }
    String ruleId = ProtectionText.sanitize(rule.ruleId(), 96);
    if (ruleId.isEmpty() || !seen.add(ruleId)) {
      return null;
    }
    return new CompiledRule(providerId + "/" + ruleId, ReactOperations.sanitize(rule.operations()),
        clean(rule.entityTypes(), false, true), clean(rule.markerKeys(), false, false),
        clean(rule.scoreboardTags(), false, false), clean(rule.worldNames(), true, false),
        upper(rule.spawnReasons()));
  }

  private static Set<String> clean(Set<String> values, boolean lower, boolean typeId) {
    Set<String> out = new LinkedHashSet<>();
    for (String value : values) {
      String clean = ProtectionText.sanitize(value, 64);
      if (clean.isEmpty()) {
        continue;
      }
      if (typeId) {
        clean = clean.toLowerCase(Locale.ROOT);
        if (clean.indexOf(':') < 0) {
          clean = "minecraft:" + clean;
        }
      } else if (lower) {
        clean = clean.toLowerCase(Locale.ROOT);
      }
      out.add(clean);
    }
    return Set.copyOf(out);
  }

  private static Set<String> upper(Set<String> values) {
    Set<String> out = new LinkedHashSet<>();
    for (String value : values) {
      String clean = ProtectionText.sanitize(value, 64);
      if (!clean.isEmpty()) {
        out.add(clean.toUpperCase(Locale.ROOT));
      }
    }
    return Set.copyOf(out);
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }

  public record Provider(ReactModProtectionProvider provider, String owner) {
  }

  private record Claim(String owner, int mask) {
    private static Claim parse(String tag) {
      if (tag == null || !tag.startsWith(CLAIM_PREFIX)) {
        return null;
      }
      int split = tag.lastIndexOf('=');
      if (split <= CLAIM_PREFIX.length()) {
        return null;
      }
      try {
        int mask = ReactOperations.sanitize(Integer.parseInt(tag.substring(split + 1)));
        return new Claim(tag.substring(CLAIM_PREFIX.length(), split), mask);
      } catch (NumberFormatException invalid) {
        return null;
      }
    }
  }

  private record CompiledRule(String ruleId, int operations, Set<String> entityTypes, Set<String> markerKeys,
                              Set<String> scoreboardTags, Set<String> worldNames, Set<String> spawnReasons) {
    private boolean matchesType(String type) {
      return entityTypes.isEmpty() || entityTypes.contains(type);
    }

    private boolean matchesWorld(String world) {
      return worldNames.isEmpty() || worldNames.contains(world);
    }

    private boolean matchesMarkers(Set<String> tags) {
      return markerKeys.isEmpty() || !Collections.disjoint(markerKeys, tags);
    }

    private boolean matchesTags(Set<String> tags) {
      return scoreboardTags.isEmpty() || !Collections.disjoint(scoreboardTags, tags);
    }
  }
}
