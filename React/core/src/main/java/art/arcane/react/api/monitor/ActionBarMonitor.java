package art.arcane.react.api.monitor;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.math.ApproachingValue;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.math.M;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.awt.Color;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's sampler dashboard: the group heads in the action bar, and while the player sneaks, the focused group's
 * name as a title and its samplers as a subtitle. It redraws every 50 ms while values move and every second once
 * they settle.
 */
public class ActionBarMonitor {
  private static final long AWAKE_INTERVAL_MS = 50L;
  private static final long SLEEPING_INTERVAL_MS = 1000L;
  private static final int SLEEP_DELAY = 10;

  private final MonitorViewer viewer;
  private final MonitorDisplay display;
  private final Map<Sampler, Double> samplers = new ConcurrentHashMap<>();
  private final Map<Sampler, ApproachingValue> approachers = new HashMap<>();
  private final Map<Sampler, Double> changers = new ConcurrentHashMap<>();
  private final Map<Sampler, Boolean> visible = new ConcurrentHashMap<>();
  private final Map<MonitorGroup, Integer> viewportIndexes = new IdentityHashMap<>();
  private final Map<Sampler, Integer> maxLengths = new HashMap<>();
  private final Map<Sampler, Double> lastValue = new HashMap<>();
  private final Map<Sampler, Double> trends = new HashMap<>();
  private final Map<Sampler, Long> lastTimes = new HashMap<>();
  private final Object renderLock = new Object();
  private int viewportIndex;
  private MonitorConfiguration configuration;
  private MonitorGroup focus;
  private int focusUpAnimation;
  private int focusDownAnimation;
  private boolean locked;
  private int lockedPosition;
  private int headerViewportSlots;
  private int samplerViewportSlots;
  private int currentSleepDelay = 20;
  private volatile long interval = AWAKE_INTERVAL_MS;
  private volatile long nextTick;
  private volatile boolean running;
  private MonitorDisplay.FocusClaim focusClaim;
  private Subscription ticking = Subscription.NONE;
  private long lastResolveMillis;

  public ActionBarMonitor(MonitorViewer viewer, MonitorDisplay display) {
    this.viewer = viewer;
    this.display = display;
    this.configuration = viewer.getSettings().getMonitorConfiguration();
    refreshViewportSettings();
  }

  public long interval() {
    return interval;
  }

  public boolean isRunning() {
    return running;
  }

  public void wakeUp() {
    currentSleepDelay = SLEEP_DELAY;
    interval = AWAKE_INTERVAL_MS;
    nextTick = 0L;
  }

  public void refreshConfiguration() {
    synchronized (renderLock) {
      configuration = viewer.getSettings().getMonitorConfiguration();
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
    }
    wakeUp();
  }

  private void refreshViewportSettings() {
    MonitoringSettings monitoring = MonitoringSettings.active();
    headerViewportSlots = Math.max(1, monitoring.getActionBarHeaderSlots());
    samplerViewportSlots = Math.max(1, monitoring.getActionBarSamplerSlots());
  }

  public void start() {
    focusClaim = display.claimFocus(viewer.viewerId());
    running = true;
    ticking = display.schedule("monitor:" + viewer.viewerId() + ":actionbar", this::onClock);
  }

  public void stop() {
    running = false;
    ticking.cancel();
    ticking = Subscription.NONE;
    boolean blankTitle = focusClaim != null && focusClaim.granted();
    display.clear(viewer.viewerId(), blankTitle);
    if (focusClaim != null) {
      focusClaim.release();
    }
  }

  private void onClock() {
    long now = System.currentTimeMillis();
    if (!running || now < nextTick) {
      return;
    }
    nextTick = now + interval;
    tick();
  }

  void tick() {
    for (Sampler sampler : changers.keySet()) {
      changers.put(sampler, M.lerp(getChanger(sampler), 0, 0.1));
    }

    boolean flushable = false;
    SampleController sampleController = SamplerHosts.sampleController();
    for (Sampler sampler : visible.keySet()) {
      if (sampler == null || !Boolean.TRUE.equals(visible.get(sampler))) {
        continue;
      }
      if (sampleController != null && !sampleController.canSample(sampler)) {
        continue;
      }
      try {
        synchronized (approachers) {
          Double value = approachers.computeIfAbsent(sampler, (Sampler key) -> new ApproachingValue(0.25)).get(sampler.sample());
          Double old = samplers.put(sampler, value);
          double current = value;
          if (old == null || old != current) {
            changers.put(sampler, M.lerp(getChanger(sampler), 1, 0.333));
            flushable = true;
          }
        }
      } catch (Throwable failure) {
        verbose("Failed to sample " + sampler.getId() + " for monitor " + viewer.viewerId() + ":actionbar");
      }
    }

    currentSleepDelay = flushable ? SLEEP_DELAY : currentSleepDelay - 1;
    interval = currentSleepDelay <= 0 ? SLEEPING_INTERVAL_MS : AWAKE_INTERVAL_MS;
    flush();
  }

  Map<Sampler, Double> sampled() {
    return samplers;
  }

  Map<Sampler, Boolean> visibility() {
    return visible;
  }

  private double getChanger(Sampler sampler) {
    Double value = sampler == null ? null : changers.get(sampler);
    return value == null ? 0 : value;
  }

  void setVisible(Sampler sampler, boolean shown) {
    if (sampler != null) {
      visible.put(sampler, shown);
    }
  }

  void clearVisibility() {
    for (Sampler sampler : visible.keySet()) {
      visible.put(sampler, false);
    }
  }

  public void flush() {
    if (!running || focusClaim == null) {
      return;
    }
    display.render(viewer.viewerId(), () -> {
      if (!running || focusClaim == null) {
        return;
      }
      synchronized (renderLock) {
        LanguageAudience.run(viewer.viewerId(), this::renderForViewer);
      }
    });
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

  private static String darker(String color) {
    return "#" + Integer.toString(new Color(Integer.parseInt(color.substring(1), 16)).darker().getRGB(), 16);
  }

  private static Sampler getFocusedSampler(MonitorGroup group) {
    return group == null ? null : group.getHeadSampler();
  }

  private static String colorActivity(String color, double activity) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb[2] = (float) (hsb[2] * (0.8 + (activity * 0.2)));
    hsb[1] = (float) (hsb[1] * (0.7 + (activity * 0.3)));
    return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
  }

  private static String colorTrend(String color, double direction) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    float red = 0;
    float blue = 0.5f;
    float h = hsb[0];
    if (direction > 0) {
      h = (float) M.lerp(hsb[0], red, direction * 0.4);
    } else if (direction < 0) {
      h = (float) M.lerp(hsb[0], blue, -direction * 0.4);
    }
    return "#" + Integer.toString(Color.getHSBColor(h, hsb[1], hsb[2]).getRGB(), 16);
  }

  private static String darkerColor(String color) {
    Color c = new Color(Integer.parseInt(color.substring(1), 16));
    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb[2] = (float) (hsb[2] * 0.8);
    hsb[1] = (float) (hsb[1] * 0.8);
    return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
  }

  private Double getSamplerValue(Sampler sampler) {
    Double value = samplers.get(sampler);
    if (value == null) {
      return null;
    }

    double trend = trends.getOrDefault(sampler, 0D);
    Double previous = lastValue.get(sampler);
    if (previous == null || !value.equals(previous)) {
      if (previous != null) {
        double percentChange = (value - previous) / previous;
        trend = (percentChange + (trend * 10)) / 11D;
      } else {
        trend *= 0.99D;
      }
      lastValue.put(sampler, value);
      lastTimes.put(sampler, System.currentTimeMillis());
    } else {
      trend *= 0.99D;
    }

    trend = Double.isInfinite(trend) || Double.isNaN(trend) ? 0 : trend;
    trends.put(sampler, trend);
    return value;
  }

  private double getActivity(Sampler sampler, long span) {
    Long last = lastTimes.get(sampler);
    if (last == null) {
      return 0;
    }
    long now = System.currentTimeMillis();
    if (last < now - span) {
      return 0;
    }
    return 1 - ((now - last) / (double) span);
  }

  private Component writeSubSamplers() {
    if (focus == null || focus.getSamplers() == null || focus.getSamplers().isEmpty()) {
      return Component.space();
    }

    viewportIndexes.putIfAbsent(focus, 0);
    int viewportLimit = Math.min(samplerViewportSlots, focus.getSamplers().size());
    Sampler focusedSampler = getFocusedSampler(focus);
    int index = focusedSampler == null ? 0 : focus.getSamplers().indexOf(focusedSampler.getId());
    if (index < 0) {
      index = 0;
    }
    int groupViewport = viewportIndexes.getOrDefault(focus, 0);
    while (index + 1 > groupViewport + viewportLimit) {
      groupViewport++;
    }
    while (index < groupViewport) {
      groupViewport--;
    }
    viewportIndexes.put(focus, groupViewport);

    Component result = Component.empty();
    boolean first = true;
    for (int m = 0; m < viewportLimit; m++) {
      String samplerId = "unknown";
      try {
        int calculatedIndex = Math.floorMod(groupViewport + m, focus.getSamplers().size());
        samplerId = focus.getSamplers().get(calculatedIndex);
        Sampler sampler = SamplerHosts.sampler(samplerId);
        if (sampler == null) {
          continue;
        }
        setVisible(sampler, true);
        if (!first) {
          result = result.append(Component.space());
        }
        first = false;
        Double sampled = getSamplerValue(sampler);
        double value = sampled == null ? 0 : sampled;

        String color = colorTrend(colorActivity(focus.getColor(), getActivity(sampler, 5000)), trends.getOrDefault(sampler, 0D));
        String colorD = darkerColor(color);
        boolean underlined = locked && viewer.isMonitorSneaking() && getFocusedSampler(focus) == sampler;
        Style valueStyle = underlined ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
            : Style.style(TextColor.fromHexString(color));
        Style suffixStyle = underlined ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
            : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));
        result = appendPadded(result, sampler, value, valueStyle, suffixStyle);
      } catch (Throwable failure) {
        verbose("Action-bar monitor could not render sampler " + samplerId);
        viewportIndexes.put(focus, 0);
      }
    }
    return result;
  }

  private Component appendPadded(Component result, Sampler sampler, double value, Style valueStyle, Style suffixStyle) {
    String formattedValue = sampler.formattedValue(value);
    String formattedSuffix = sampler.formattedSuffix(value);
    int length = formattedValue.length() + formattedSuffix.length();
    int max = maxLengths.compute(sampler, (Sampler key, Integer previous) -> Math.max(previous == null ? 0 : previous, length));
    Component appended = result.append(sampler.format(Component.text(formattedValue, valueStyle), Component.text(formattedSuffix, suffixStyle)));
    return length < max ? appended.append(Component.text(" ".repeat(max - length))) : appended;
  }

  private Component writeHeader() {
    if (configuration == null || configuration.getGroups() == null || configuration.getGroups().isEmpty()) {
      return Component.space();
    }

    int viewportLimit = Math.min(headerViewportSlots, configuration.getGroups().size());
    if (focus != null) {
      int index = configuration.getGroups().indexOf(focus);
      if (index >= 0) {
        while (index + 1 > viewportIndex + viewportLimit) {
          viewportIndex++;
        }
        while (index < viewportIndex) {
          viewportIndex--;
        }
      }
    }

    Component result = Component.empty();
    boolean first = true;
    for (int m = 0; m < viewportLimit; m++) {
      MonitorGroup group = configuration.getGroups().get(Math.floorMod(viewportIndex + m, configuration.getGroups().size()));
      Sampler head = getFocusedSampler(group);
      if (head == null) {
        continue;
      }
      setVisible(head, true);
      if (!first) {
        result = result.append(Component.space());
      }
      first = false;
      Double sampled = getSamplerValue(head);
      double value = sampled == null ? 0 : sampled;

      String color = colorTrend(colorActivity(group.getColor(), getActivity(head, 5000)), trends.getOrDefault(head, 0D));
      String colorD = darkerColor(color);
      Style valueStyle = focus == group ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
          : Style.style(TextColor.fromHexString(color));
      Style suffixStyle = focus == group ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
          : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));
      result = appendPadded(result, head, value, valueStyle, suffixStyle);
    }
    return result;
  }

  MonitorGroup getFocusedHeaderGroup() {
    if (configuration == null || configuration.getGroups() == null || configuration.getGroups().isEmpty()) {
      return null;
    }
    List<MonitorGroup> groups = configuration.getGroups();
    int size = groups.size();
    if (locked) {
      return groups.get(Math.floorMod(lockedPosition, size));
    }
    return viewer.isMonitorSneaking() ? groups.get(Math.floorMod(viewer.getScrollPosition(size), size)) : null;
  }

  private Sampler getNextFocusedSampler() {
    if (focus == null || focus.getSamplers() == null || focus.getSamplers().isEmpty()) {
      return null;
    }
    return SamplerHosts.sampler(focus.getSamplers().get(viewer.getScrollPosition(focus.getSamplers().size())));
  }

  private void renderForViewer() {
    UUID viewerId = viewer.viewerId();
    clearVisibility();
    MonitorGroup previousFocus = focus;

    if (locked != viewer.isLocked()) {
      locked = viewer.isLocked();
      if (configuration != null && configuration.getGroups() != null && !configuration.getGroups().isEmpty()) {
        lockedPosition = viewer.getScrollPosition(configuration.getGroups().size());
      }
    }

    focus = getFocusedHeaderGroup();
    if (focus != previousFocus) {
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

    if (focus != null) {
      if (locked && viewer.isMonitorSneaking()) {
        Sampler nextFocusedSampler = getNextFocusedSampler();
        if (nextFocusedSampler != null) {
          focus.setHeadSampler(nextFocusedSampler.getId());
        }
      }

      boolean focusGranted = resolveNow || focus != previousFocus ? focusClaim.resolve() : focusClaim.granted();
      if (focusGranted) {
        Duration stay = Duration.ofMillis(((int) ((interval / 50) + 3)) * 50L);
        display.titleTimes(viewerId, Duration.ZERO, stay, Duration.ofMillis(20 * 50));
        if (focusUpAnimation >= -10) {
          display.title(viewerId, focusUpAnimation > 0 ? writeHeaderTitle(focus) : Component.space());
          focusUpAnimation--;
        }
        display.subtitle(viewerId, writeSubSamplers());
      }
    } else if (focusDownAnimation > 0) {
      focusDownAnimation--;
      if (focusClaim.granted()) {
        display.clearTitle(viewerId);
        if (focusDownAnimation == 0) {
          display.titleTimes(viewerId, Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000));
          focusClaim.release();
        }
      }
    }

    display.actionBar(viewerId, LegacyComponentSerializer.legacySection().serialize(writeHeader()));
  }

  private static void verbose(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.verbose(message);
    }
  }
}
