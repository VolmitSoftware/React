package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.monitor.MonitorDisplay;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.hud.HudTitleClaim;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The action bar monitor on Bukkit: React's ticker drives it, the HUD compositor carries the action bar line and the
 * title service arbitrates the title. On Folia a render hops to the player's region.
 */
public final class BukkitMonitorDisplay implements MonitorDisplay {
  public static final BukkitMonitorDisplay INSTANCE = new BukkitMonitorDisplay();
  private final Map<UUID, AtomicBoolean> queuedRenders = new ConcurrentHashMap<>();

  private BukkitMonitorDisplay() {
  }

  @Override
  public Subscription schedule(String id, Runnable tick) {
    TickedObject ticked = new TickedObject("monitor", id, 50) {
      @Override
      public void onTick() {
        tick.run();
      }
    };
    return ticked::unregister;
  }

  @Override
  public void render(UUID viewerId, Runnable render) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }
    if (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(player)) {
      render.run();
      return;
    }
    AtomicBoolean queued = queuedRenders.computeIfAbsent(viewerId, (UUID id) -> new AtomicBoolean());
    if (!queued.compareAndSet(false, true)) {
      return;
    }
    AtomicBoolean completed = new AtomicBoolean();
    Runnable completion = () -> {
      if (completed.compareAndSet(false, true)) {
        queued.set(false);
      }
    };
    Runnable operation = () -> {
      try {
        if (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(player)) {
          render.run();
        }
      } finally {
        completion.run();
      }
    };
    try {
      if (!J.runEntity(player, operation, 0, completion)) {
        completion.run();
      }
    } catch (RuntimeException | Error failure) {
      completion.run();
      throw failure;
    }
  }

  @Override
  public FocusClaim claimFocus(UUID viewerId) {
    HudTitleClaim claim = React.titles().open(Bukkit.getPlayer(viewerId), "react:monitor-focus", HudPriority.INTERACTIVE, 1500L);
    return new FocusClaim() {
      @Override
      public boolean resolve() {
        return claim.resolve();
      }

      @Override
      public boolean granted() {
        return claim.granted();
      }

      @Override
      public void release() {
        claim.release();
      }
    };
  }

  @Override
  public void actionBar(UUID viewerId, String legacyHeader) {
    HudActionBar hud = React.hud();
    Player player = Bukkit.getPlayer(viewerId);
    if (hud != null && player != null) {
      hud.publish(player, new HudSegment("react:monitor", HudPriority.PINNED, 1500L, List.of(HudSlot.CENTER), legacyHeader));
    }
  }

  @Override
  public void titleTimes(UUID viewerId, Duration fadeIn, Duration stay, Duration fadeOut) {
    audience(viewerId).sendTitlePart(TitlePart.TIMES, Title.Times.times(fadeIn, stay, fadeOut));
  }

  @Override
  public void title(UUID viewerId, Component title) {
    audience(viewerId).sendTitlePart(TitlePart.TITLE, title);
  }

  @Override
  public void subtitle(UUID viewerId, Component subtitle) {
    audience(viewerId).sendTitlePart(TitlePart.SUBTITLE, subtitle);
  }

  @Override
  public void clearTitle(UUID viewerId) {
    audience(viewerId).clearTitle();
  }

  @Override
  public void clear(UUID viewerId, boolean blankTitle) {
    queuedRenders.remove(viewerId);
    Player player = Bukkit.getPlayer(viewerId);
    if (player == null) {
      return;
    }
    Runnable clearUi = () -> {
      HudActionBar hud = React.hud();
      if (hud != null) {
        hud.clear(player, "react:monitor");
      }
      if (blankTitle) {
        Audience audience = React.audiences().player(player);
        audience.sendTitlePart(TitlePart.TITLE, Component.space());
        audience.sendTitlePart(TitlePart.SUBTITLE, Component.space());
      }
    };
    try {
      if (J.isFoliaThreading()) {
        if (!J.runEntity(player, clearUi, 3)) {
          J.runEntity(player, clearUi);
        }
      } else {
        J.sync(clearUi, 3);
      }
    } catch (IllegalPluginAccessException e) {
      clearUi.run();
    }
  }

  private static Audience audience(UUID viewerId) {
    Player player = Bukkit.getPlayer(viewerId);
    return player == null ? Audience.empty() : React.audiences().player(player);
  }
}
