package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class RuntimeMessages {
  public static final TextKey PREFIX = TextKey.of("runtime.prefix", "&7[&bReact&7]&r: ");
  public static final TextKey MISSING_PERMISSION = TextKey.of("runtime.missing_permission", "&cYou lack the permission '{permission}'.&r");
  public static final TextKey UNKNOWN_COMMAND = TextKey.of("runtime.unknown_command", "&cUnknown React command.&r");
  public static final TextKey ACTION_DISABLED = TextKey.of("runtime.action.disabled", "&c{action} is disabled in config.&r");
  public static final TextKey ACTION_QUEUED = TextKey.of("runtime.action.queued", "&bQueued {action}.&r");
  public static final TextKey ACTION_STARTING = TextKey.of("runtime.action.starting", "&bStarting {action}.&r");
  public static final TextKey ACTION_COMPLETED = TextKey.of("runtime.action.completed", "&a{message}&r");
  public static final TextKey MONITOR_ENABLED = TextKey.of("runtime.monitor.enabled", "&aAction bar monitor enabled.&r");
  public static final TextKey MONITOR_DISABLED = TextKey.of("runtime.monitor.disabled", "&eAction bar monitor disabled.&r");
  public static final TextKey HOTLOAD_DIFF = TextKey.of("runtime.hotload.diff", "&aConfig hotloaded:&r &f[{file}]&r &b[{key}]&r &7[{before} -> {after}]&r");
  public static final TextKey HOTLOAD_TRUNCATED = TextKey.of("runtime.hotload.truncated", "&7{count} additional changes were omitted for &f{file}&7.&r");
  public static final TextKey HOTLOAD_MISSING = TextKey.of("runtime.hotload.missing", "not set");
  public static final TextKey HOTLOAD_REMOVED = TextKey.of("runtime.hotload.removed", "removed");
  public static final TextKey ADAPT_INTERACTION_THROTTLED = TextKey.of("runtime.guard.adapt.interaction_throttled", "&eAdapt runtime surge guard smoothed rapid interaction burst.&r");
  public static final TextKey ADAPT_COMBAT_THROTTLED = TextKey.of("runtime.guard.adapt.combat_throttled", "&eAdapt runtime surge guard smoothed combat ability burst.&r");
  public static final TextKey ADAPT_CONSUME_THROTTLED = TextKey.of("runtime.guard.adapt.consume_throttled", "&eAdapt runtime surge guard smoothed item-consume burst.&r");
  public static final TextKey IRIS_MOVEMENT_THROTTLED = TextKey.of("runtime.guard.iris.movement_throttled", "&eIris terrain surge guard throttled new chunk movement.&r");
  public static final TextKey IRIS_TELEPORT_THROTTLED = TextKey.of("runtime.guard.iris.teleport_throttled", "&eIris terrain surge guard throttled teleport into ungenerated terrain.&r");
  public static final TextKey LEGACY_PERMISSION_ENTRY = TextKey.of("runtime.legacy_command.permission_entry", "&7-&r &f{permission}&r");
  public static final TextKey LEGACY_INSUFFICIENT_PERMISSIONS = TextKey.of("runtime.legacy_command.insufficient_permissions", "&cInsufficient permissions.&r");
  public static final TextKey LEGACY_PARAMETERS_IGNORED = TextKey.of("runtime.legacy_command.parameters_ignored", "&eParameters ignored:&r &f{parameters}&r");
  public static final TextKey LEGACY_NO_DESCRIPTION = TextKey.of("runtime.legacy_command.no_description", "No description");
  public static final TextKey MOB_STACKING_UNIQUE = TextKey.of("runtime.mob_stacking.unique", "UNIQUE");
  public static final TextKey ENTITY_KILLER_COUNTDOWN = TextKey.of("runtime.entity_killer.countdown", "{seconds}s");

  private RuntimeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(PREFIX);
    builder.add(MISSING_PERMISSION);
    builder.add(UNKNOWN_COMMAND);
    builder.add(ACTION_DISABLED);
    builder.add(ACTION_QUEUED);
    builder.add(ACTION_STARTING);
    builder.add(ACTION_COMPLETED);
    builder.add(MONITOR_ENABLED);
    builder.add(MONITOR_DISABLED);
    builder.add(HOTLOAD_DIFF);
    builder.add(HOTLOAD_TRUNCATED);
    builder.add(HOTLOAD_MISSING);
    builder.add(HOTLOAD_REMOVED);
    builder.add(ADAPT_INTERACTION_THROTTLED);
    builder.add(ADAPT_COMBAT_THROTTLED);
    builder.add(ADAPT_CONSUME_THROTTLED);
    builder.add(IRIS_MOVEMENT_THROTTLED);
    builder.add(IRIS_TELEPORT_THROTTLED);
    builder.add(LEGACY_PERMISSION_ENTRY);
    builder.add(LEGACY_INSUFFICIENT_PERMISSIONS);
    builder.add(LEGACY_PARAMETERS_IGNORED);
    builder.add(LEGACY_NO_DESCRIPTION);
    builder.add(MOB_STACKING_UNIQUE);
    builder.add(ENTITY_KILLER_COUNTDOWN);
  }
}
