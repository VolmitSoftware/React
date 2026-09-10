package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class DevMessages {
  public static final TextKey ACTIONS_NONE = TextKey.of("dev.suite.actions_none", "&eNo enabled direct actions are available for the dev test suite.&r");
  public static final TextKey SUITE_SCOPE = TextKey.of("dev.suite.scope", "&7Dev suite scope: world=&f{world}&7, purgeRadius=&f{radius}&7 chunks.&r");
  public static final TextKey SUITE_QUEUEING = TextKey.of("dev.suite.queueing", "&7Queueing &f{count}&7 direct actions sequentially. Recursive meta actions stay excluded.&r");
  public static final TextKey SUITE_UNCOVERED = TextKey.of("dev.suite.uncovered", "&eEnabled actions not covered by this suite: {actions}&r");
  public static final TextKey VERIFY_HEADER = TextKey.of("dev.verify.header", "&bReact verify&r &7— optimization-sweep fix checks&r");
  public static final TextKey VERIFY_PLATFORM_FOLIA = TextKey.of("dev.verify.platform.folia", "      &7platform: &aFolia / region-threaded&r");
  public static final TextKey VERIFY_PLATFORM_SINGLE = TextKey.of("dev.verify.platform.single", "      &7platform: &esingle-thread (Paper/Purpur) — Folia-only fixes cannot fire here&r");
  public static final TextKey VERIFY_COMPLETE = TextKey.of("dev.verify.complete", "&bReact verify&r &7— complete.&r");
  public static final TextKey BRIDGE_SKIPPED = TextKey.of("dev.verify.bridge.skipped", "  &e[SKIP]&r &fnms-bridge&7 — unavailable: {reason}&r");
  public static final TextKey BRIDGE_PASS = TextKey.of("dev.verify.bridge.pass", "  &a[PASS]&r &fnms-bridge&7 — {available} available, {unavailable} unavailable&r");
  public static final TextKey BRIDGE_FAIL = TextKey.of("dev.verify.bridge.fail", "  &c[FAIL]&r &fnms-bridge&7 — {available} available, {unavailable} unavailable&r");
  public static final TextKey BRIDGE_FAILURE = TextKey.of("dev.verify.bridge.failure", "      &c{id} {reason}&r");
  public static final TextKey HOOKS_PASS = TextKey.of("dev.verify.hooks.pass", "  &a[PASS]&r &fnms-hooks&7 — falling-block hook installed&r");
  public static final TextKey HOOKS_WARN = TextKey.of("dev.verify.hooks.warn", "  &e[WARN]&r &fnms-hooks&7 — falling-block hook measurement-only (not installed)&r");
  public static final TextKey HOOKS_RESET = TextKey.of("dev.verify.hooks.reset", "      &7reset() uninstalls all 6 hooks including hopper (reload leak fixed)&r");
  public static final TextKey SAMPLER_MISSING = TextKey.of("dev.verify.sampler.missing", "      &cmissing sampler: {id}&r");
  public static final TextKey SAMPLER_INVALID = TextKey.of("dev.verify.sampler.invalid", "      &c{id} returned {value}&r");
  public static final TextKey SAMPLERS_PASS = TextKey.of("dev.verify.samplers.pass", "  &a[PASS]&r &fsamplers&7 — {checked} checked, {missing} missing, {invalid} invalid&r");
  public static final TextKey SAMPLERS_FAIL = TextKey.of("dev.verify.samplers.fail", "  &c[FAIL]&r &fsamplers&7 — {checked} checked, {missing} missing, {invalid} invalid&r");
  public static final TextKey SAMPLER_VALUES = TextKey.of("dev.verify.samplers.values", "      &f{values}&r");
  public static final TextKey CROP_SKIPPED = TextKey.of("dev.verify.crop.skipped", "  &e[SKIP]&r &fcrop-fast-forward&7 — feature not registered&r");
  public static final TextKey CROP_INFO = TextKey.of("dev.verify.crop.info", "  &7[INFO]&r &fcrop-fast-forward&7 — enabled={enabled}, rate={rate}&r");
  public static final TextKey CROP_OBSERVE = TextKey.of("dev.verify.crop.observe", "      &7Single-step clamp removed (proportional growth). Fires on dormant-to-active wake; observe by leaving and returning to a planted area.&r");
  public static final TextKey GRAVITY_NOT_REGISTERED = TextKey.of("dev.verify.gravity.not_registered", "  &e[SKIP]&r &flazy-gravity&7 — feature not registered&r");
  public static final TextKey GRAVITY_NO_WORLDS = TextKey.of("dev.verify.gravity.no_worlds", "  &e[SKIP]&r &flazy-gravity&7 — no worlds loaded&r");
  public static final TextKey GRAVITY_NO_HEADROOM = TextKey.of("dev.verify.gravity.no_headroom", "  &e[SKIP]&r &flazy-gravity&7 — no headroom above y={ground_y} to drop a test block&r");
  public static final TextKey GRAVITY_DROPPED = TextKey.of("dev.verify.gravity.dropped", "  &7[INFO]&r &flazy-gravity&7 — dropped SAND y={spawn_y} over ground y={ground_y}, checking landing (5s)...&r");
  public static final TextKey GRAVITY_PASS = TextKey.of("dev.verify.gravity.pass", "  &a[PASS]&r &flazy-gravity&7 — block landed within 5s (projection / un-skip / landing path OK)&r");
  public static final TextKey GRAVITY_FAIL = TextKey.of("dev.verify.gravity.fail", "  &c[FAIL]&r &flazy-gravity&7 — block STILL FALLING after 5s (stuck mid-air)&r");
  public static final TextKey AUDIT_HEADER = TextKey.of("dev.audit.header", "&bReact dev audit:&r");
  public static final TextKey AUDIT_FEATURES = TextKey.of("dev.audit.features", "&7- Features: &f{registered}&7 registered, &f{enabled}&7 enabled, &f{active}&7 active&r");
  public static final TextKey AUDIT_TWEAKS = TextKey.of("dev.audit.tweaks", "&7- Tweaks: &f{registered}&7 registered, &f{enabled}&7 enabled, &f{active}&7 active&r");
  public static final TextKey AUDIT_SAMPLERS = TextKey.of("dev.audit.samplers", "&7- Samplers: &f{registered}&7 registered&r");
  public static final TextKey AUDIT_ACTIONS = TextKey.of("dev.audit.actions", "&7- Actions: &f{registered}&7 registered, &f{enabled}&7 enabled&r");
  public static final TextKey AUDIT_EXCLUDED = TextKey.of("dev.audit.excluded", "&7- Excluded from dev suite: &f{incident}&7 (recursive meta action), &f{unknown}&r");
  public static final TextKey SUITE_FINISHED = TextKey.of("dev.suite.finished", "&aReact dev test suite finished.&r");
  public static final TextKey SUITE_SKIPPED = TextKey.of("dev.suite.step.skipped", "&e[{index}/{total}] Skipped {action} because it is unavailable.&r");
  public static final TextKey SUITE_CREATE_FAILED = TextKey.of("dev.suite.step.create_failed", "&c[{index}/{total}] Failed to create {action}: {detail}&r");
  public static final TextKey SUITE_FAILED = TextKey.of("dev.suite.step.failed", "&c[{index}/{total}] {action} failed: {detail}&r");
  public static final TextKey SUITE_STARTING = TextKey.of("dev.suite.step.starting", "&b[{index}/{total}] Starting {action}&r");
  public static final TextKey SUITE_COMPLETED = TextKey.of("dev.suite.step.completed", "&a[{index}/{total}] {message}&r");
  public static final TextKey SUITE_QUEUED = TextKey.of("dev.suite.step.queued", "&7[{index}/{total}] Queued {action}&r");

  private DevMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(ACTIONS_NONE);
    builder.add(SUITE_SCOPE);
    builder.add(SUITE_QUEUEING);
    builder.add(SUITE_UNCOVERED);
    builder.add(VERIFY_HEADER);
    builder.add(VERIFY_PLATFORM_FOLIA);
    builder.add(VERIFY_PLATFORM_SINGLE);
    builder.add(VERIFY_COMPLETE);
    builder.add(BRIDGE_SKIPPED);
    builder.add(BRIDGE_PASS);
    builder.add(BRIDGE_FAIL);
    builder.add(BRIDGE_FAILURE);
    builder.add(HOOKS_PASS);
    builder.add(HOOKS_WARN);
    builder.add(HOOKS_RESET);
    builder.add(SAMPLER_MISSING);
    builder.add(SAMPLER_INVALID);
    builder.add(SAMPLERS_PASS);
    builder.add(SAMPLERS_FAIL);
    builder.add(SAMPLER_VALUES);
    builder.add(CROP_SKIPPED);
    builder.add(CROP_INFO);
    builder.add(CROP_OBSERVE);
    builder.add(GRAVITY_NOT_REGISTERED);
    builder.add(GRAVITY_NO_WORLDS);
    builder.add(GRAVITY_NO_HEADROOM);
    builder.add(GRAVITY_DROPPED);
    builder.add(GRAVITY_PASS);
    builder.add(GRAVITY_FAIL);
    builder.add(AUDIT_HEADER);
    builder.add(AUDIT_FEATURES);
    builder.add(AUDIT_TWEAKS);
    builder.add(AUDIT_SAMPLERS);
    builder.add(AUDIT_ACTIONS);
    builder.add(AUDIT_EXCLUDED);
    builder.add(SUITE_FINISHED);
    builder.add(SUITE_SKIPPED);
    builder.add(SUITE_CREATE_FAILED);
    builder.add(SUITE_FAILED);
    builder.add(SUITE_STARTING);
    builder.add(SUITE_COMPLETED);
    builder.add(SUITE_QUEUED);
  }
}
