/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.monitor;

import art.arcane.react.React;
import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactPlayer;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSlotClaim;
import art.arcane.volmlib.util.hud.HudSlotRequest;
import art.arcane.volmlib.util.hud.HudSurface;
import art.arcane.volmlib.util.math.M;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.awt.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ActionBarMonitor extends PlayerMonitor {
  private int viewportIndex;
  private MonitorConfiguration configuration;
  private MonitorGroup focus;
  private Map<MonitorGroup, Integer> viewportIndexes;
  private Map<Sampler, Integer> maxLengths;
  private Map<Sampler, Double> lastValue;
  private Map<Sampler, Double> trends;
  private Map<Sampler, Long> lastTimes;
  private int focusUpAnimation = 0;
  private int focusDownAnimation = 0;
  private boolean tickUp = false;
  private boolean locked = false;
  private int lockedPosition;
  private boolean running;
  private int headerViewportSlots;
  private int samplerViewportSlots;
  private HudSlotClaim barClaim;
  private HudSlotClaim focusClaim;
  private long lastResolveMillis;
  private long barDeniedSinceMillis;

  public ActionBarMonitor(ReactPlayer player) {
    super("actionbar", player, 50);
    sleepDelay = 10;
    viewportIndexes = new IdentityHashMap<>();
    lastValue = new HashMap<>();
    maxLengths = new HashMap<>();
    lastTimes = new HashMap<>();
    trends = new HashMap<>();
    sleepingRate = 1000;
    viewportIndex = 0;
    lockedPosition = 0;
    configuration = player.getSettings().getMonitorConfiguration();
    refreshViewportSettings();
  }

  public void refreshConfiguration() {
    configuration = getPlayer().getSettings().getMonitorConfiguration();
    refreshViewportSettings();
    focus = null;
    viewportIndex = 0;
    lockedPosition = 0;
    focusUpAnimation = 0;
    focusDownAnimation = 0;
    viewportIndexes.clear();
    maxLengths.clear();
    lastValue.clear();
    trends.clear();
    lastTimes.clear();
    clearVisibility();
    wakeUp();
  }

  private void refreshViewportSettings() {
    ReactConfiguration.Monitoring monitoring = ReactConfiguration.get().getMonitoring();
    headerViewportSlots = Math.max(1, monitoring.getActionBarHeaderSlots());
    samplerViewportSlots = Math.max(1, monitoring.getActionBarSamplerSlots());
  }

  public boolean isAlwaysFlushing() {
    return true;
  }

  @Override
  public void stop() {
    running = false;
    Player player = getPlayer().getPlayer();
    boolean blankActionBar = barClaim != null && barClaim.granted() == HudSurface.ACTION_BAR;
    boolean blankTitle = focusClaim != null && focusClaim.granted() == HudSurface.TITLE;
    Runnable clearUi = () -> {
      if (blankActionBar) {
        player.sendActionBar(Component.space());
      }
      if (blankTitle) {
        player.sendTitlePart(TitlePart.TITLE, Component.space());
        player.sendTitlePart(TitlePart.SUBTITLE, Component.space());
      }
    };

    if (blankActionBar || blankTitle) {
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

    if (barClaim != null) {
      barClaim.release();
    }
    if (focusClaim != null) {
      focusClaim.release();
    }
    HudBossBarLane lanes = React.lanes();
    if (lanes != null) {
      lanes.hide(player, "react:monitor");
    }
    super.stop();
    unregister();
  }

  @Override
  public void start() {
    Player player = getPlayer().getPlayer();
    barClaim = React.hud().open(player, new HudSlotRequest("react:monitor", HudPriority.AMBIENT, 1500L, List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR)));
    focusClaim = React.hud().open(player, new HudSlotRequest("react:monitor-focus", HudPriority.INTERACTIVE, 1500L, List.of(HudSurface.TITLE)));
    running = true;
    super.start();
  }

  private Component writeHeaderTitle(MonitorGroup group) {
    if (focusUpAnimation > 3) {
      return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(group.getColor()), TextDecoration.BOLD));
    }

    if (focusUpAnimation > 2) {
      return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(group.getColor()))));
    }

    if (focusUpAnimation > 1) {
      return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(group.getColor())))).font(Key.key("uniform")));
    }

    return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(darker(group.getColor()))))).font(Key.key("uniform")));
  }

  private String changify(String color, double change) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb[0] = (float) (M.lerp(hsb[0], hsb[0] + 0.05, change));
    hsb[1] = (float) (M.lerp(hsb[1] * 0.8, 1, change));
    hsb[2] = (float) (M.lerp(hsb[2] * 0.8, 1, change));
    return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
  }

  private String darker(String color) {
    return "#" + Integer.toString(new Color(Integer.parseInt(color.substring(1), 16)).darker().getRGB(), 16);
  }

  private Sampler getFocusedSampler() {
    return getFocusedSampler(focus);
  }

  private Sampler getFocusedSampler(MonitorGroup g) {
    if (g == null) {
      return null;
    }
    return g.getHeadSampler();
  }

  private String colorActivity(String color, double activity) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb[2] = (float) (hsb[2] * (0.8 + (activity * 0.2)));
    hsb[1] = (float) (hsb[1] * (0.7 + (activity * 0.3)));
    return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
  }

  private String colorTrend(String color, double direction) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    float red = 0;
    float blue = 0.5f;
    float h = hsb[0];

    if (direction > 0) {
      h = (float) M.lerp(hsb[0], red, direction * 0.4);
    } else if (direction < 0) {
      direction = -direction;
      h = (float) M.lerp(hsb[0], blue, direction * 0.4);
    }

    return "#" + Integer.toString(Color.getHSBColor(h, hsb[1], hsb[2]).getRGB(), 16);
  }

  private String darkerColor(String color) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb[2] = (float) (hsb[2] * 0.8);
    hsb[1] = (float) (hsb[1] * 0.8);
    return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
  }

  private Double getSamplerValue(Sampler s) {
    Double value = samplers.get(s);

    if (value == null) {
      return value;
    }


    double trend = trends.getOrDefault(s, 0D);
    if (lastValue.get(s) == null || !value.equals(lastValue.get(s))) {
      if (lastValue.get(s) != null) {
        double before = lastValue.get(s);
        double after = value;
        double percentChange = (after - before) / before;
        trend = (percentChange + (trend * 10)) / 11D;
      } else {
        trend *= 0.99D;
      }

      lastValue.put(s, value);
      lastTimes.put(s, System.currentTimeMillis());

    } else {
      trend *= 0.99D;
    }

    trend = Double.isInfinite(trend) || Double.isNaN(trend) ? 0 : trend;
    trends.put(s, trend);


    return value;
  }

  private double getActivity(Sampler s, long span) {
    if (lastTimes.containsKey(s)) {
      long time = System.currentTimeMillis() - span;
      if (lastTimes.get(s) < time) {
        return 0;
      }

      return 1 - ((System.currentTimeMillis() - lastTimes.get(s)) / (double) span);
    }

    return 0;
  }

  private Component writeSubSamplers() {
    if (focus == null || focus.getSamplers() == null || focus.getSamplers().isEmpty()) {
      return Component.space();
    }

    if (!viewportIndexes.containsKey(focus)) {
      viewportIndexes.put(focus, 0);
    }

    int viewportLimit = Math.min(samplerViewportSlots, focus.getSamplers().size());
    Sampler focusedSampler = getFocusedSampler();
    int index = focusedSampler == null ? 0 : focus.getSamplers().indexOf(focusedSampler.getId());
    if (index < 0) {
      index = 0;
    }
    int viewportIndex = viewportIndexes.getOrDefault(focus, 0);

    while (index + 1 > viewportIndex + viewportLimit) {
      viewportIndex++;
    }

    while (index < viewportIndex) {
      viewportIndex--;
    }

    viewportIndexes.put(focus, viewportIndex);

    Component result = Component.empty();
    boolean first = true;
    for (int m = 0; m < viewportLimit; m++) {
      try {
        int calculatedIndex = (viewportIndex + m) % focus.getSamplers().size();
        if (calculatedIndex < 0) {
          calculatedIndex += focus.getSamplers().size();
        }
        Sampler i = React.sampler(focus.getSamplers().get(calculatedIndex));
        if (i == null) {
          continue;
        }
        setVisible(i, true);
        if (!first) {
          result = result.append(Component.space());
        }

        first = false;
        Double value = getSamplerValue(i);
        value = value == null ? 0 : value;

        String color = colorTrend(colorActivity(focus.getColor(), getActivity(i, 5000)), trends.getOrDefault(i, 0D));
        String colorD = darkerColor(color);

        Style s = (locked && getPlayer().isMonitorSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
            : Style.style(TextColor.fromHexString(color));
        Style ss = (locked && getPlayer().isMonitorSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
            : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));

        String formattedValue = i.formattedValue(value);
        String formattedSuffix = i.formattedSuffix(value);
        int l = formattedValue.length() + formattedSuffix.length();
        synchronized (maxLengths) {
          maxLengths.compute(i, (k, v) -> Math.max(v == null ? 0 : v, l));
        }
        result = result.append(i.format(Component.text(formattedValue, s), Component.text(formattedSuffix, ss)));

        if (l < maxLengths.get(i)) {
          result = result.append(Component.text(" ".repeat(maxLengths.get(i) - l)));
        }
      } catch (Throwable e) {
        e.printStackTrace();
        viewportIndexes.put(focus, 0);
      }
    }

    return result;
  }


  private Component writeHeader() {
    if (configuration == null || configuration.getGroups() == null || configuration.getGroups().isEmpty()) {
      return Component.space();
    }

    int viewportLimit = Math.min(headerViewportSlots, configuration.getGroups().size());
    if (focus != null) {
      int index = configuration.getGroups().indexOf(focus);

      while (index + 1 > viewportIndex + viewportLimit) {
        viewportIndex++;
      }

      while (index < viewportIndex) {
        viewportIndex--;
      }
    }

    Component result = Component.empty();
    boolean first = true;
    for (int m = 0; m < viewportLimit; m++) {
      MonitorGroup i = configuration.getGroups().get((viewportIndex + m) % configuration.getGroups().size());
      Sampler headerSampler = getFocusedSampler(i);
      if (headerSampler == null) {
        continue;
      }

      setVisible(headerSampler, true);

      if (!first) {
        result = result.append(Component.space());
      }

      first = false;
      Sampler head = headerSampler;
      Double value = getSamplerValue(head);
      value = value == null ? 0 : value;

      String color = colorTrend(colorActivity(i.getColor(), getActivity(head, 5000)), trends.getOrDefault(head, 0D));
      String colorD = darkerColor(color);

      Style s = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
          : Style.style(TextColor.fromHexString(color));
      Style ss = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
          : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));
      String formattedValue = head.formattedValue(value);
      String formattedSuffix = head.formattedSuffix(value);
      int l = formattedValue.length() + formattedSuffix.length();
      synchronized (maxLengths) {
        maxLengths.compute(head, (k, v) -> Math.max(v == null ? 0 : v, l));
      }
      result = result.append(head.format(Component.text(formattedValue, s), Component.text(formattedSuffix, ss)));

      if (l < maxLengths.get(head)) {
        result = result.append(Component.text(" ".repeat(maxLengths.get(head) - l)));
      }
    }

    return result;
  }

  MonitorGroup getFocusedHeaderGroup() {
    if (configuration == null || configuration.getGroups() == null || configuration.getGroups().isEmpty()) {
      return null;
    }
    return locked ? configuration.getGroups().get(lockedPosition) : getPlayer().isMonitorSneaking() ? configuration.getGroups().get(getPlayer().getScrollPosition(configuration.getGroups().size())) : null;
  }

  Sampler getNextFocusedSampler() {
    if (focus == null || focus.getSamplers() == null || focus.getSamplers().isEmpty()) {
      return null;
    }
    return React.sampler(focus.getSamplers().get(getPlayer().getScrollPosition(focus.getSamplers().size())));
  }

  @Override
  public void flush() {
    if (!running || barClaim == null || focusClaim == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(getPlayer().getPlayer())) {
      J.runEntity(getPlayer().getPlayer(), this::flush);
      return;
    }

    clearVisibility();
    MonitorGroup f = focus;

    if (locked != getPlayer().isLocked()) {
      locked = getPlayer().isLocked();
      if (configuration != null && configuration.getGroups() != null && !configuration.getGroups().isEmpty()) {
        lockedPosition = getPlayer().getScrollPosition(configuration.getGroups().size());
      }
    }

    focus = getFocusedHeaderGroup();

    if (focus != f) {
      if (focus != null) {
        focusUpAnimation = 5;
      } else {
        focusDownAnimation = 2;
      }
    }

    long now = System.currentTimeMillis();
    boolean resolveNow = now - lastResolveMillis >= 250L;
    if (resolveNow) {
      lastResolveMillis = now;
    }

    HudSurface barSurface = resolveNow ? barClaim.resolve() : barClaim.granted();

    if (focus != null) {
      if (locked && getPlayer().isMonitorSneaking()) {
        Sampler nextFocusedSampler = getNextFocusedSampler();
        if (nextFocusedSampler != null) {
          focus.setHeadSampler(nextFocusedSampler.getId());
        }
      }

      HudSurface focusSurface = resolveNow || focus != f ? focusClaim.resolve() : focusClaim.granted();

      if (focusSurface == HudSurface.TITLE) {
        Duration stay = Duration.ofMillis(((int) ((getTinterval() / 50) + 3)) * 50L);
        Duration fadeOut = Duration.ofMillis(20 * 50);
        getPlayer().getPlayer().sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ZERO, stay, fadeOut));

        if (focusUpAnimation >= -10) {
          if (focusUpAnimation > 0) {
            getPlayer().getPlayer().sendTitlePart(TitlePart.TITLE, writeHeaderTitle(focus));
          } else {
            getPlayer().getPlayer().sendTitlePart(TitlePart.TITLE, Component.space());
          }

          focusUpAnimation--;
        }

        getPlayer().getPlayer().sendTitlePart(TitlePart.SUBTITLE, writeSubSamplers());
      }
    } else if (focusDownAnimation > 0) {
      focusDownAnimation--;
      if (focusClaim.granted() == HudSurface.TITLE) {
        getPlayer().getPlayer().clearTitle();
        if (focusDownAnimation == 0) {
          getPlayer().getPlayer().sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000)));
          focusClaim.release();
        }
      }
    }

    Component header = writeHeader();
    HudBossBarLane lanes = React.lanes();

    if (barSurface == HudSurface.ACTION_BAR) {
      barDeniedSinceMillis = 0L;
      getPlayer().getPlayer().sendActionBar(header);
      if (lanes != null) {
        lanes.hide(getPlayer().getPlayer(), "react:monitor");
      }
    } else if (barSurface == HudSurface.BOSS_BAR) {
      if (barDeniedSinceMillis == 0L) {
        barDeniedSinceMillis = now;
      }
      if (now - barDeniedSinceMillis >= 2000L && lanes != null) {
        lanes.show(getPlayer().getPlayer(), "react:monitor", LegacyComponentSerializer.legacySection().serialize(header), 1.0D, BarColor.WHITE, BarStyle.SOLID, 4000L);
      }
    }
  }
}
