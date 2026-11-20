package art.arcane.react.api.monitor;

import art.arcane.react.platform.Subscription;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Where the action bar monitor draws on a platform: the action bar line, the title and subtitle that show the focused
 * group, and the clock that drives it.
 */
public interface MonitorDisplay {
  /**
   * Calls {@code tick} every 50 milliseconds off the server thread until cancelled.
   */
  Subscription schedule(String id, Runnable tick);

  /**
   * Runs {@code render} where the viewer may be messaged. A platform that must hop threads skips the call while an
   * earlier one for the same viewer is still queued.
   */
  void render(UUID viewerId, Runnable render);

  FocusClaim claimFocus(UUID viewerId);

  /**
   * Shows the monitor header in the viewer's action bar. The header is legacy section text, as the action bar
   * compositor on Bukkit carries it.
   */
  void actionBar(UUID viewerId, String legacyHeader);

  void titleTimes(UUID viewerId, Duration fadeIn, Duration stay, Duration fadeOut);

  void title(UUID viewerId, Component title);

  void subtitle(UUID viewerId, Component subtitle);

  void clearTitle(UUID viewerId);

  /**
   * Removes the monitor from the viewer's screen a few ticks after it stops, blanking the title when the monitor
   * held it.
   */
  void clear(UUID viewerId, boolean blankTitle);

  /**
   * The monitor's hold on the viewer's title, which other HUD users may contend for.
   */
  interface FocusClaim {
    boolean resolve();

    boolean granted();

    void release();
  }
}
