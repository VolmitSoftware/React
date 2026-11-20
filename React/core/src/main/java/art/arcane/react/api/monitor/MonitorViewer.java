package art.arcane.react.api.monitor;

import art.arcane.react.model.PlayerSettings;

import java.util.UUID;

/**
 * The player an action bar monitor draws for: their saved dashboard and the sneak, double-sneak lock and
 * sneak-scroll input that moves the monitor's focus.
 */
public interface MonitorViewer {
  UUID viewerId();

  PlayerSettings getSettings();

  boolean isMonitorSneaking();

  boolean isLocked();

  int getScrollPosition(int maxRemainder);
}
