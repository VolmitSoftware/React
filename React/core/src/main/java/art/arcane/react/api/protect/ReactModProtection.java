package art.arcane.react.api.protect;

import art.arcane.react.api.protect.internal.ModProtectionEngine;

/**
 * Per-entity protection on a mod server, the counterpart of a Bukkit plugin's {@code ReactProtection}. The entity is
 * the loader's entity object and the owner is the calling mod's id. Every call answers false or empty while React is
 * not running.
 */
public final class ReactModProtection {
  private ReactModProtection() {
  }

  public static boolean available() {
    return ModProtectionEngine.active() != null;
  }

  public static boolean protect(Object entity, String modId, ReactOperation... operations) {
    return protect(entity, modId, ReactOperations.of(operations));
  }

  public static boolean protect(Object entity, String modId, int operations) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine != null && engine.protect(entity, modId, operations);
  }

  public static boolean release(Object entity, String modId) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine != null && engine.release(entity, modId);
  }

  public static boolean invalidate(Object entity) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine != null && engine.invalidate(entity);
  }

  public static boolean isProtected(Object entity, ReactOperation operation) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine != null && engine.isProtected(entity, operation);
  }

  public static int operationsFor(Object entity) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine == null ? ReactOperations.NONE : engine.operationsFor(entity);
  }

  public static String ownerOf(Object entity) {
    ModProtectionEngine engine = ModProtectionEngine.active();
    return engine == null ? "" : engine.ownerOf(entity);
  }
}
