package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class RuntimeMessages {
  public static final TextKey PREFIX = TextKey.of("runtime.prefix", "<gray>[<aqua>React</aqua>]</gray>: ");
  public static final TextKey MISSING_PERMISSION = TextKey.of("runtime.missing_permission", "<red>You lack the permission '{permission}'.</red>");
  public static final TextKey UNKNOWN_COMMAND = TextKey.of("runtime.unknown_command", "<red>Unknown React command.</red>");
  public static final TextKey ACTION_DISABLED = TextKey.of("runtime.action.disabled", "<red>{action} is disabled in config.</red>");
  public static final TextKey ACTION_QUEUED = TextKey.of("runtime.action.queued", "<aqua>Queued {action}.</aqua>");
  public static final TextKey ACTION_STARTING = TextKey.of("runtime.action.starting", "<aqua>Starting {action}.</aqua>");
  public static final TextKey ACTION_COMPLETED = TextKey.of("runtime.action.completed", "<green>{message}</green>");
  public static final TextKey MONITOR_ENABLED = TextKey.of("runtime.monitor.enabled", "<green>Action bar monitor enabled.</green>");
  public static final TextKey MONITOR_DISABLED = TextKey.of("runtime.monitor.disabled", "<yellow>Action bar monitor disabled.</yellow>");
  public static final TextKey HOTLOAD_DIFF = TextKey.of("runtime.hotload.diff", "<green>Config hotloaded:</green> <white>[{file}]</white> <aqua>[{key}]</aqua> <gray>[{before} -> {after}]</gray>");
  public static final TextKey HOTLOAD_TRUNCATED = TextKey.of("runtime.hotload.truncated", "<gray>{count} additional changes were omitted for <white>{file}</white>.</gray>");
  public static final TextKey ADAPT_INTERACTION_THROTTLED = TextKey.of("runtime.guard.adapt.interaction_throttled", "<yellow>Adapt runtime surge guard smoothed rapid interaction burst.</yellow>");
  public static final TextKey ADAPT_COMBAT_THROTTLED = TextKey.of("runtime.guard.adapt.combat_throttled", "<yellow>Adapt runtime surge guard smoothed combat ability burst.</yellow>");
  public static final TextKey ADAPT_CONSUME_THROTTLED = TextKey.of("runtime.guard.adapt.consume_throttled", "<yellow>Adapt runtime surge guard smoothed item-consume burst.</yellow>");
  public static final TextKey IRIS_MOVEMENT_THROTTLED = TextKey.of("runtime.guard.iris.movement_throttled", "<yellow>Iris terrain surge guard throttled new chunk movement.</yellow>");
  public static final TextKey IRIS_TELEPORT_THROTTLED = TextKey.of("runtime.guard.iris.teleport_throttled", "<yellow>Iris terrain surge guard throttled teleport into ungenerated terrain.</yellow>");
  public static final TextKey LEGACY_PERMISSION_ENTRY = TextKey.of("runtime.legacy_command.permission_entry", "<gray>-</gray> <white>{permission}</white>");
  public static final TextKey LEGACY_INSUFFICIENT_PERMISSIONS = TextKey.of("runtime.legacy_command.insufficient_permissions", "<red>Insufficient permissions.</red>");
  public static final TextKey LEGACY_PARAMETERS_IGNORED = TextKey.of("runtime.legacy_command.parameters_ignored", "<yellow>Parameters ignored:</yellow> <white>{parameters}</white>");
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
