package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class DevMessages {
  public static final TextKey ACTIONS_NONE = TextKey.of("dev.suite.actions_none", "<yellow>No enabled direct actions are available for the dev test suite.</yellow>");
  public static final TextKey SUITE_SCOPE = TextKey.of("dev.suite.scope", "<gray>Dev suite scope: world=<white>{world}</white>, purgeRadius=<white>{radius}</white> chunks.</gray>");
  public static final TextKey SUITE_QUEUEING = TextKey.of("dev.suite.queueing", "<gray>Queueing <white>{count}</white> direct actions sequentially. Recursive meta actions stay excluded.</gray>");
  public static final TextKey SUITE_UNCOVERED = TextKey.of("dev.suite.uncovered", "<yellow>Enabled actions not covered by this suite: {actions}</yellow>");
  public static final TextKey VERIFY_HEADER = TextKey.of("dev.verify.header", "<aqua>React verify</aqua> <gray>— optimization-sweep fix checks</gray>");
  public static final TextKey VERIFY_PLATFORM_FOLIA = TextKey.of("dev.verify.platform.folia", "      <gray>platform: </gray><green>Folia / region-threaded</green>");
  public static final TextKey VERIFY_PLATFORM_SINGLE = TextKey.of("dev.verify.platform.single", "      <gray>platform: </gray><yellow>single-thread (Paper/Purpur) — Folia-only fixes cannot fire here</yellow>");
  public static final TextKey VERIFY_COMPLETE = TextKey.of("dev.verify.complete", "<aqua>React verify</aqua> <gray>— complete.</gray>");
  public static final TextKey BRIDGE_SKIPPED = TextKey.of("dev.verify.bridge.skipped", "  <yellow>[SKIP]</yellow> <white>nms-bridge</white><gray> — unavailable: {reason}</gray>");
  public static final TextKey BRIDGE_PASS = TextKey.of("dev.verify.bridge.pass", "  <green>[PASS]</green> <white>nms-bridge</white><gray> — {available} available, {unavailable} unavailable</gray>");
  public static final TextKey BRIDGE_FAIL = TextKey.of("dev.verify.bridge.fail", "  <red>[FAIL]</red> <white>nms-bridge</white><gray> — {available} available, {unavailable} unavailable</gray>");
  public static final TextKey BRIDGE_FAILURE = TextKey.of("dev.verify.bridge.failure", "      <red>{id} {reason}</red>");
  public static final TextKey HOOKS_PASS = TextKey.of("dev.verify.hooks.pass", "  <green>[PASS]</green> <white>nms-hooks</white><gray> — falling-block hook installed</gray>");
  public static final TextKey HOOKS_WARN = TextKey.of("dev.verify.hooks.warn", "  <yellow>[WARN]</yellow> <white>nms-hooks</white><gray> — falling-block hook measurement-only (not installed)</gray>");
  public static final TextKey HOOKS_RESET = TextKey.of("dev.verify.hooks.reset", "      <gray>reset() uninstalls all 6 hooks including hopper (reload leak fixed)</gray>");
  public static final TextKey SAMPLER_MISSING = TextKey.of("dev.verify.sampler.missing", "      <red>missing sampler: {id}</red>");
  public static final TextKey SAMPLER_INVALID = TextKey.of("dev.verify.sampler.invalid", "      <red>{id} returned {value}</red>");
  public static final TextKey SAMPLERS_PASS = TextKey.of("dev.verify.samplers.pass", "  <green>[PASS]</green> <white>samplers</white><gray> — {checked} checked, {missing} missing, {invalid} invalid</gray>");
  public static final TextKey SAMPLERS_FAIL = TextKey.of("dev.verify.samplers.fail", "  <red>[FAIL]</red> <white>samplers</white><gray> — {checked} checked, {missing} missing, {invalid} invalid</gray>");
  public static final TextKey SAMPLER_VALUES = TextKey.of("dev.verify.samplers.values", "      <white>{values}</white>");
  public static final TextKey CROP_SKIPPED = TextKey.of("dev.verify.crop.skipped", "  <yellow>[SKIP]</yellow> <white>crop-fast-forward</white><gray> — feature not registered</gray>");
  public static final TextKey CROP_INFO = TextKey.of("dev.verify.crop.info", "  <gray>[INFO]</gray> <white>crop-fast-forward</white><gray> — enabled={enabled}, rate={rate}</gray>");
  public static final TextKey CROP_OBSERVE = TextKey.of("dev.verify.crop.observe", "      <gray>Single-step clamp removed (proportional growth). Fires on dormant-to-active wake; observe by leaving and returning to a planted area.</gray>");
  public static final TextKey GRAVITY_NOT_REGISTERED = TextKey.of("dev.verify.gravity.not_registered", "  <yellow>[SKIP]</yellow> <white>lazy-gravity</white><gray> — feature not registered</gray>");
  public static final TextKey GRAVITY_NO_WORLDS = TextKey.of("dev.verify.gravity.no_worlds", "  <yellow>[SKIP]</yellow> <white>lazy-gravity</white><gray> — no worlds loaded</gray>");
  public static final TextKey GRAVITY_NO_HEADROOM = TextKey.of("dev.verify.gravity.no_headroom", "  <yellow>[SKIP]</yellow> <white>lazy-gravity</white><gray> — no headroom above y={ground_y} to drop a test block</gray>");
  public static final TextKey GRAVITY_DROPPED = TextKey.of("dev.verify.gravity.dropped", "  <gray>[INFO]</gray> <white>lazy-gravity</white><gray> — dropped SAND y={spawn_y} over ground y={ground_y}, checking landing (5s)...</gray>");
  public static final TextKey GRAVITY_PASS = TextKey.of("dev.verify.gravity.pass", "  <green>[PASS]</green> <white>lazy-gravity</white><gray> — block landed within 5s (projection / un-skip / landing path OK)</gray>");
  public static final TextKey GRAVITY_FAIL = TextKey.of("dev.verify.gravity.fail", "  <red>[FAIL]</red> <white>lazy-gravity</white><gray> — block STILL FALLING after 5s (stuck mid-air)</gray>");
  public static final TextKey AUDIT_HEADER = TextKey.of("dev.audit.header", "<aqua>React dev audit:</aqua>");
  public static final TextKey AUDIT_FEATURES = TextKey.of("dev.audit.features", "<gray>- Features: <white>{registered}</white> registered, <white>{enabled}</white> enabled, <white>{active}</white> active</gray>");
  public static final TextKey AUDIT_TWEAKS = TextKey.of("dev.audit.tweaks", "<gray>- Tweaks: <white>{registered}</white> registered, <white>{enabled}</white> enabled, <white>{active}</white> active</gray>");
  public static final TextKey AUDIT_SAMPLERS = TextKey.of("dev.audit.samplers", "<gray>- Samplers: <white>{registered}</white> registered</gray>");
  public static final TextKey AUDIT_ACTIONS = TextKey.of("dev.audit.actions", "<gray>- Actions: <white>{registered}</white> registered, <white>{enabled}</white> enabled</gray>");
  public static final TextKey AUDIT_EXCLUDED = TextKey.of("dev.audit.excluded", "<gray>- Excluded from dev suite: <white>{incident}</white> (recursive meta action), <white>{unknown}</white></gray>");
  public static final TextKey SUITE_FINISHED = TextKey.of("dev.suite.finished", "<green>React dev test suite finished.</green>");
  public static final TextKey SUITE_SKIPPED = TextKey.of("dev.suite.step.skipped", "<yellow>[{index}/{total}] Skipped {action} because it is unavailable.</yellow>");
  public static final TextKey SUITE_CREATE_FAILED = TextKey.of("dev.suite.step.create_failed", "<red>[{index}/{total}] Failed to create {action}: {detail}</red>");
  public static final TextKey SUITE_FAILED = TextKey.of("dev.suite.step.failed", "<red>[{index}/{total}] {action} failed: {detail}</red>");
  public static final TextKey SUITE_STARTING = TextKey.of("dev.suite.step.starting", "<aqua>[{index}/{total}] Starting {action}</aqua>");
  public static final TextKey SUITE_COMPLETED = TextKey.of("dev.suite.step.completed", "<green>[{index}/{total}] {message}</green>");
  public static final TextKey SUITE_QUEUED = TextKey.of("dev.suite.step.queued", "<gray>[{index}/{total}] Queued {action}</gray>");

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
