package art.arcane.react.platform;

/**
 * When a host listener runs relative to the others for the same event, in Bukkit's order: LOWEST first, MONITOR last.
 */
public enum ListenerPriority {
  LOWEST,
  LOW,
  NORMAL,
  HIGH,
  HIGHEST,
  MONITOR
}
