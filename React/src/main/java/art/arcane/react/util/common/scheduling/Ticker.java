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

package art.arcane.react.util.common.scheduling;


import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.RollingSequence;
import art.arcane.multiburst.BurstExecutor;
import art.arcane.multiburst.MultiBurst;
import art.arcane.react.React;
import art.arcane.react.api.feature.ReactTickedFeature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.ReactTickedTweak;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.scheduling.Looper;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Ticker {
  private static final long SLOW_TICK_WARN_THRESHOLD_MS = 50L;
  private static final long SLOW_TICK_RECENCY_WINDOW_MS = 30_000L;
  private static final long SLOW_TICK_LOG_INTERVAL_MS = 10_000L;
  private static final long SLOW_TICK_ESCALATION_LOG_INTERVAL_MS = 2_500L;
  private static final long SLOW_TICK_CRITICAL_LOG_INTERVAL_MS = 1_000L;
  private static final long[] FOLIA_BACKOFF_STEPS_MS = new long[]{0L, 1000L, 5000L, 15000L, 60000L};
  private static final long CLOSE_JOIN_TIMEOUT_MS = 250L;
  private final KList<Ticked> ticklist;
  private final KList<Ticked> newTicks;
  private final KList<String> removeTicks;
  private final RollingSequence tasksPerSecond;
  private final RollingSequence tickTime;
  private final Looper looper;
  private final Map<String, SlowTickStats> slowTickStats;
  private final Map<String, SlowTickLogState> slowTickLogStates;
  private final Map<String, FoliaViolationState> foliaViolationStates;
  private volatile boolean ticking;
  private volatile boolean closed;

  public Ticker() {
    this.closed = false;
    this.ticklist = new KList<>(4096);
    this.newTicks = new KList<>(128);
    this.removeTicks = new KList<>(128);
    tasksPerSecond = new RollingSequence(20);
    tickTime = new RollingSequence(10);
    slowTickStats = new ConcurrentHashMap<>();
    slowTickLogStates = new ConcurrentHashMap<>();
    foliaViolationStates = new ConcurrentHashMap<>();
    ticking = false;
    looper = new Looper() {
      PrecisionStopwatch p = PrecisionStopwatch.start();
      int tps = 0;
      int tv = 0;

      @Override
      protected long loop() {
        if (closed) {
          return -1;
        }

        if (!ticking) {
          p = PrecisionStopwatch.start();
          tps += tick();
          tickTime.put(p.getMilliseconds());
          tv++;
          if (tv >= 20) {
            tv = 0;
            tasksPerSecond.put(tps);
            tps = 0;
          }
        }

        return 50;
      }
    };
    looper.start();
  }

  public void close() {
    closed = true;
    clear();
    looper.interrupt();
    try {
      looper.join(CLOSE_JOIN_TIMEOUT_MS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      React.verbose("Ticker close wait interrupted.");
    }
  }

  public double getTasksPerSecond() {
    return tasksPerSecond.getAverage();
  }

  public double getTickTime() {
    return tickTime.getAverage();
  }

  public void register(Ticked ticked) {
    if (ticked == null) {
      return;
    }

    synchronized (newTicks) {
      newTicks.add(ticked);
    }
  }

  public void unregister(Ticked ticked) {
    synchronized (removeTicks) {
      removeTicks.add(ticked.getTid());
    }
  }

  public void clear() {
    synchronized (ticklist) {
      ticklist.clear();
    }
    synchronized (removeTicks) {
      removeTicks.clear();
    }
    synchronized (newTicks) {
      newTicks.clear();
    }
    slowTickStats.clear();
    slowTickLogStates.clear();
    foliaViolationStates.clear();

  }

  private int tick() {
    ticking = true;
    if (closed) {
      ticking = false;
      return 0;
    }

    int ix = ticklist.size();
    if (ix > 0) {
      BurstExecutor e = MultiBurst.burst.burst(ix);
      for (int i = 0; i < ix; i++) {
        Ticked ticked = ticklist.get(i);
        if (ticked == null || !ticked.shouldTick()) {
          continue;
        }

        e.queue(() -> executeTick(ticked));
      }
      try {
        e.complete();
      } catch (Throwable throwable) {
        if (isInterruptedFailure(throwable) || closed) {
          Thread.currentThread().interrupt();
          ticking = false;
          return ix;
        }

        throw new RuntimeException("Ticker burst execution failed", throwable);
      }
    }

    synchronized (newTicks) {
      while (!newTicks.isEmpty()) {
        Ticked ticked = newTicks.remove(0);
        if (ticked == null) {
          continue;
        }

        if (containsTickId(ticked.getTid())) {
          continue;
        }

        ticklist.add(ticked);
      }
    }

    synchronized (removeTicks) {
      while (removeTicks.isNotEmpty()) {
        removeTickById(removeTicks.remove(0));
      }
    }

    ticking = false;
    return ix;
  }

  private void executeTick(Ticked ticked) {
    if (shouldBackoffTick(ticked)) {
      return;
    }

    try {
      long start = System.nanoTime();
      ticked.tick();
      clearFoliaViolation(ticked);
      long elapsedMS = (System.nanoTime() - start) / 1_000_000L;
      boolean slow = elapsedMS > SLOW_TICK_WARN_THRESHOLD_MS;
      SlowTickSnapshot snapshot = recordSlowTick(ticked, elapsedMS, slow);
      if (slow) {
        warnSlowTick(ticked, elapsedMS, snapshot);
      } else {
        clearSlowTickLogState(ticked);
      }
    } catch (Throwable exxx) {
      if (closed || React.instance == null || !React.instance.isReady()) {
        return;
      }

      if (J.isThreadOwnershipViolation(exxx)) {
        handleFoliaViolation(ticked, exxx);
        return;
      }

      if (isInterruptedFailure(exxx)) {
        Thread.currentThread().interrupt();
        return;
      }

      try {
        React.warn("Tick task crashed: " + describeTicked(ticked) + " cause=" + summarizeThrowable(exxx));
        exxx.printStackTrace();
      } catch (Throwable logFailure) {
        System.err.println("[React] Failed to emit tick crash log: " + logFailure.getClass().getSimpleName()
            + (logFailure.getMessage() == null ? "" : " - " + logFailure.getMessage()));
      }
    }
  }

  private boolean shouldBackoffTick(Ticked ticked) {
    if (ticked == null || !J.isFoliaThreading()) {
      return false;
    }

    FoliaViolationState state = foliaViolationStates.get(slowTickKey(ticked));
    if (state == null) {
      return false;
    }

    return state.isBackoffActive(System.currentTimeMillis());
  }

  private void clearFoliaViolation(Ticked ticked) {
    if (ticked == null) {
      return;
    }

    foliaViolationStates.remove(slowTickKey(ticked));
  }

  private void handleFoliaViolation(Ticked ticked, Throwable throwable) {
    if (ticked == null) {
      return;
    }

    String key = slowTickKey(ticked);
    FoliaViolationState state = foliaViolationStates.computeIfAbsent(key, ignored -> new FoliaViolationState());
    FoliaViolationSnapshot snapshot = state.record(System.currentTimeMillis());

    if (!snapshot.shouldLog) {
      return;
    }

    try {
      React.warn(
          "Folia region ownership violation: " + describeTicked(ticked)
              + " paused=" + snapshot.backoffMS + "ms"
              + " failures=" + snapshot.failures
              + " cause=" + summarizeThrowable(throwable)
              + " owner=" + slowTickOwner(ticked)
      );
    } catch (Throwable logFailure) {
      System.err.println("[React] Failed to emit Folia ownership violation log: " + logFailure.getClass().getSimpleName()
          + (logFailure.getMessage() == null ? "" : " - " + logFailure.getMessage()));
    }
  }

  private boolean isInterruptedFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof InterruptedException) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }

  private void warnSlowTick(Ticked ticked, long elapsedMS, SlowTickSnapshot snapshot) {
    SlowTickLogDecision logDecision = shouldLogSlowTick(ticked, elapsedMS);
    if (!logDecision.shouldLog) {
      return;
    }

    long intervalMS = safeLong(ticked == null ? null : ticked.getTinterval(), 0L);
    long ageMS = safeLong(ticked == null ? null : ticked.getAge(), 0L);
    long overMS = Math.max(0L, elapsedMS - SLOW_TICK_WARN_THRESHOLD_MS);
    String context = slowTickContext(ticked);
    String cause = slowTickCause(ticked);
    String source = slowTickLikelySource(ticked);
    ReactConfiguration.SlowTickLogMode mode = ReactConfiguration.get().getSlowTickLogMode();
    String message;
    if (mode == ReactConfiguration.SlowTickLogMode.SHORT) {
      message = buildShortSlowTickMessage(ticked, elapsedMS, overMS);
    } else if (mode == ReactConfiguration.SlowTickLogMode.BLAME) {
      message = buildBlameSlowTickMessage(ticked, elapsedMS, overMS, cause, source);
    } else {
      message = buildDetailedSlowTickMessage(
          ticked,
          elapsedMS,
          intervalMS,
          ageMS,
          overMS,
          slowTickOwner(ticked),
          slowTickMethod(ticked),
          source,
          cause,
          snapshot
      );
    }

    if (logDecision.suppressed > 0L) {
      message += " Suppressed " + logDecision.suppressed + " similar warnings over " + logDecision.windowMS + "ms.";
    }

    React.warn(message);

    if (ReactConfiguration.get().isVerbose() && !context.isBlank()) {
      React.verbose("Slow tick context: " + context);
    }
  }

  private String buildShortSlowTickMessage(Ticked ticked, long elapsedMS, long overMS) {
    return "Slow tick [" + slowSeverity(elapsedMS) + "]: "
        + shortTickLabel(ticked)
        + " took " + elapsedMS + "ms"
        + " (+" + overMS + "ms over " + SLOW_TICK_WARN_THRESHOLD_MS + "ms).";
  }

  private String buildBlameSlowTickMessage(Ticked ticked,
                                           long elapsedMS,
                                           long overMS,
                                           String cause,
                                           String source) {
    return "Slow tick [" + slowSeverity(elapsedMS) + "]: "
        + shortTickLabel(ticked)
        + " took " + elapsedMS + "ms"
        + " (+" + overMS + "ms over " + SLOW_TICK_WARN_THRESHOLD_MS + "ms)."
        + " Blame: " + slowTickBlameTarget(source)
        + " caused " + compactPhrase(cause)
        + "; pressure=" + trimSentence(source) + ".";
  }

  private String buildDetailedSlowTickMessage(Ticked ticked,
                                              long elapsedMS,
                                              long intervalMS,
                                              long ageMS,
                                              long overMS,
                                              String owner,
                                              String method,
                                              String source,
                                              String cause,
                                              SlowTickSnapshot snapshot) {
    return "Slow tick detected [" + slowSeverity(elapsedMS) + "]: " + describeTicked(ticked)
        + " took " + elapsedMS + "ms"
        + " (threshold=" + SLOW_TICK_WARN_THRESHOLD_MS + "ms, over=" + overMS + "ms, ratio=" + ratioLabel(elapsedMS, SLOW_TICK_WARN_THRESHOLD_MS)
        + ", interval=" + intervalMS + "ms, budget=" + budgetLabel(elapsedMS, intervalMS) + ", age=" + ageMS + "ms)."
        + " owner=" + owner
        + " method=" + method
        + " source=" + source
        + " cause=" + cause
        + " recurrence=" + snapshot.slowRuns + "/" + snapshot.runs + " slow"
        + " (consecutive=" + snapshot.consecutiveSlowRuns
        + ", last30s=" + snapshot.slowRunsLastWindow
        + ", avgSlow=" + formatMs(snapshot.averageSlowMS)
        + "ms, max=" + snapshot.maxSlowMS + "ms).";
  }

  private String shortTickLabel(Ticked ticked) {
    if (ticked == null) {
      return "unknown-task";
    }

    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      return "feature:" + featureTicked.getComponent().getId();
    }
    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      return "tweak:" + tweakTicked.getComponent().getId();
    }

    String group = ticked.getTgroup() == null ? "unknown-group" : ticked.getTgroup();
    String id = ticked.getTid() == null ? "unknown-id" : ticked.getTid();
    return "task:" + group + ":" + id;
  }

  private String slowTickBlameTarget(String source) {
    if (source == null) {
      return "React";
    }

    String lower = source.toLowerCase(Locale.ROOT);
    if (lower.startsWith("iris ")) {
      return "Iris";
    }
    if (lower.startsWith("adapt ")) {
      return "Adapt";
    }
    if (lower.startsWith("chunk generation/load")) {
      return "Minecraft";
    }
    if (lower.startsWith("react ")) {
      return "React";
    }
    return "React";
  }

  private String trimSentence(String value) {
    if (value == null) {
      return "unknown workload";
    }
    String trimmed = value.trim();
    while (trimmed.endsWith(".")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed.isEmpty() ? "unknown workload" : trimmed;
  }

  private String compactPhrase(String value) {
    String trimmed = trimSentence(value);
    int detailStart = trimmed.indexOf(" (");
    if (detailStart > 0) {
      return trimmed.substring(0, detailStart).trim();
    }
    return trimmed;
  }

  private String describeTicked(Ticked ticked) {
    if (ticked == null) {
      return "unknown-task";
    }

    String schedulerId = ticked.getTgroup() + ":" + ticked.getTid();
    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      var component = featureTicked.getComponent();
      return "feature name=\"" + component.getName()
          + "\" id=" + component.getId()
          + " scheduler=" + schedulerId
          + " class=" + component.getClass().getSimpleName();
    }

    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      var component = tweakTicked.getComponent();
      return "tweak name=\"" + component.getName()
          + "\" id=" + component.getId()
          + " scheduler=" + schedulerId
          + " class=" + component.getClass().getSimpleName();
    }

    return "task scheduler=" + schedulerId + " class=" + ticked.getClass().getSimpleName();
  }

  private String slowTickContext(Ticked ticked) {
    if (ticked == null) {
      return "";
    }

    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      String id = featureTicked.getComponent().getId();
      return "Context: executing feature onTick for /plugins/React/feature/" + id + ".toml (tickIntervalMS controls schedule cadence).";
    }

    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      String id = tweakTicked.getComponent().getId();
      return "Context: executing tweak onTick for /plugins/React/tweak/" + id + ".toml (tickIntervalMS controls schedule cadence).";
    }

    String tid = ticked.getTid() == null ? "" : ticked.getTid();
    if ("map".equalsIgnoreCase(tid)) {
      return "Context: executing map maintenance/update work from /plugins/React/core/map.toml frame-map settings.";
    }

    return "Context: executing scheduled task id=" + tid + " class=" + ticked.getClass().getSimpleName() + ".";
  }

  private SlowTickSnapshot recordSlowTick(Ticked ticked, long elapsedMS, boolean slow) {
    String key = slowTickKey(ticked);
    SlowTickStats stats = slowTickStats.computeIfAbsent(key, ignored -> new SlowTickStats());
    return stats.record(System.currentTimeMillis(), elapsedMS, slow);
  }

  private String slowTickKey(Ticked ticked) {
    if (ticked == null) {
      return "unknown";
    }

    String group = ticked.getTgroup() == null ? "unknown-group" : ticked.getTgroup();
    String id = ticked.getTid() == null ? "unknown-id" : ticked.getTid();
    return group + ":" + id + "|" + ticked.getClass().getName();
  }

  private String slowSeverity(long elapsedMS) {
    if (elapsedMS >= 120L || elapsedMS >= SLOW_TICK_WARN_THRESHOLD_MS * 2L) {
      return "CRITICAL";
    }
    if (elapsedMS >= 90L || elapsedMS * 10L >= SLOW_TICK_WARN_THRESHOLD_MS * 16L) {
      return "HIGH";
    }
    if (elapsedMS >= 65L || elapsedMS * 10L >= SLOW_TICK_WARN_THRESHOLD_MS * 13L) {
      return "MEDIUM";
    }
    return "LOW";
  }

  private String ratioLabel(long value, long baseline) {
    if (baseline <= 0L) {
      return "n/a";
    }
    return String.format(Locale.ROOT, "%.2fx", (double) value / (double) baseline);
  }

  private String budgetLabel(long elapsedMS, long intervalMS) {
    if (intervalMS <= 0L) {
      return "n/a";
    }
    return String.format(Locale.ROOT, "%.1f%%", ((double) elapsedMS * 100D) / (double) intervalMS);
  }

  private String formatMs(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private String slowTickOwner(Ticked ticked) {
    if (ticked == null) {
      return "unknown";
    }

    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      return "/plugins/React/feature/" + featureTicked.getComponent().getId() + ".toml";
    }

    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      return "/plugins/React/tweak/" + tweakTicked.getComponent().getId() + ".toml";
    }

    String tid = ticked.getTid() == null ? "" : ticked.getTid();
    if ("map".equalsIgnoreCase(tid)) {
      return "/plugins/React/core/map.toml";
    }
    if ("hotload".equalsIgnoreCase(tid)) {
      return "/plugins/React/core/hotload.toml";
    }
    return "/plugins/React/core/" + tid + ".toml";
  }

  private String slowTickMethod(Ticked ticked) {
    if (ticked == null) {
      return "unknown";
    }

    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      return featureTicked.getComponent().getClass().getSimpleName() + "#onTick";
    }

    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      return tweakTicked.getComponent().getClass().getSimpleName() + "#onTick";
    }

    return ticked.getClass().getSimpleName() + "#tick";
  }

  private String slowTickCause(Ticked ticked) {
    if (ticked == null) {
      return "Unknown task workload.";
    }

    if (ticked instanceof ReactTickedFeature featureTicked && featureTicked.getComponent() != null) {
      String id = featureTicked.getComponent().getId();
      String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
      if ("incident-mode".equals(lower)) {
        return "Incident mode evaluation workload (sampler.tick-time + sampler.incident-score reads and incident gate checks).";
      }
      if ("feature-trinity-incident-mode".equals(lower)) {
        return "Trinity incident evaluation workload (cross-plugin metric reads and incident gate checks).";
      }
      if (lower.contains("map") || lower.contains("heatmap") || lower.contains("overlay")) {
        return "Map/overlay feature workload (chunk scans and renderer data preparation) for this feature.";
      }
      if (lower.contains("chunk")) {
        return "Chunk-oriented feature workload (sampling, scans, or quarantine bookkeeping) in this feature.";
      }
      if (lower.contains("entity") || lower.contains("spawn")) {
        return "Entity/spawn feature workload in this feature tick.";
      }
      return "Feature-specific onTick workload in this component.";
    }

    if (ticked instanceof ReactTickedTweak tweakTicked && tweakTicked.getComponent() != null) {
      return "Tweak-specific onTick workload in this component.";
    }

    String tid = ticked.getTid() == null ? "" : ticked.getTid().toLowerCase(Locale.ROOT);
    if ("map".equals(tid)) {
      return "Map controller maintenance workload (map metadata repair and frame-map push/update work).";
    }
    if ("hotload".equals(tid)) {
      return "Config hotload workload (watcher scan, diff/canonicalization, and reload apply path).";
    }
    if (tid.contains("feature")) {
      return "Feature scheduler workload in this task.";
    }
    if (tid.contains("action")) {
      return "Action scheduler workload while processing queued action work.";
    }
    return "Scheduled task workload inside this controller.";
  }

  private String slowTickLikelySource(Ticked ticked) {
    double irisQueue = sampleSampler("iris-pregen-queue", -1D);
    double irisStreamMs = sampleSampler("iris-chunk-stream-ms", -1D);
    if (irisStreamMs >= 20_000D && irisQueue <= 0D) {
      irisStreamMs = -1D;
    }

    if (irisQueue > 0D || irisStreamMs >= 12D) {
      return String.format(
          Locale.ROOT,
          "Iris generation pressure (queue=%.0f, stream=%.1fms)",
          Math.max(0D, irisQueue),
          Math.max(0D, irisStreamMs)
      );
    }

    double chunkGenMs = sampleSampler("chunk-gen-ms", -1D);
    double chunkLoadMs = sampleSampler("chunk-load-ms", -1D);
    if (chunkGenMs >= 8D || chunkLoadMs >= 8D) {
      return String.format(
          Locale.ROOT,
          "Chunk generation/load pressure (gen=%.1fms, load=%.1fms)",
          Math.max(0D, chunkGenMs),
          Math.max(0D, chunkLoadMs)
      );
    }

    double reactSyncMs = sampleSampler("react-sync-tick-time", -1D);
    double reactJobs = sampleSampler("react-jobs-queue", -1D);
    double schedulerBacklog = sampleSampler("scheduler-backlog", -1D);
    if (reactSyncMs >= 8D || reactJobs >= 64D || schedulerBacklog >= 64D) {
      return String.format(
          Locale.ROOT,
          "React scheduler pressure (sync=%.1fms, jobs=%.0f, backlog=%.0f)",
          Math.max(0D, reactSyncMs),
          Math.max(0D, reactJobs),
          Math.max(0D, schedulerBacklog)
      );
    }

    if (ticked instanceof ReactTickedFeature) {
      return "React feature workload";
    }
    if (ticked instanceof ReactTickedTweak) {
      return "React tweak workload";
    }
    return "React scheduled task";
  }

  private double sampleSampler(String samplerId, double fallback) {
    try {
      Sampler sampler = React.sampler(samplerId);
      if (sampler == null) {
        return fallback;
      }

      double value = sampler.sample();
      return Double.isFinite(value) ? value : fallback;
    } catch (Throwable ex) {
      React.verbose("Failed to sample '" + samplerId + "': " + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      return fallback;
    }
  }

  private long safeLong(Long value, long fallback) {
    if (value == null) {
      return fallback;
    }
    return Math.max(0L, value);
  }

  private SlowTickLogDecision shouldLogSlowTick(Ticked ticked, long elapsedMS) {
    if (ticked == null) {
      return new SlowTickLogDecision(true, 0L, 0L);
    }

    String key = slowTickKey(ticked);
    SlowTickLogState state = slowTickLogStates.computeIfAbsent(key, ignored -> new SlowTickLogState());
    long now = System.currentTimeMillis();
    int severityRank = slowSeverityRank(elapsedMS);

    synchronized (state) {
      if (state.lastLogMS <= 0L) {
        state.lastLogMS = now;
        state.lastSeverityRank = severityRank;
        state.suppressed = 0L;
        return new SlowTickLogDecision(true, 0L, 0L);
      }

      long sinceLastLog = Math.max(0L, now - state.lastLogMS);
      long threshold = severityRank >= 3 ? SLOW_TICK_CRITICAL_LOG_INTERVAL_MS : SLOW_TICK_LOG_INTERVAL_MS;
      boolean severityEscalated = severityRank > state.lastSeverityRank && sinceLastLog >= SLOW_TICK_ESCALATION_LOG_INTERVAL_MS;
      if (severityEscalated || sinceLastLog >= threshold) {
        long suppressed = state.suppressed;
        state.suppressed = 0L;
        state.lastLogMS = now;
        state.lastSeverityRank = severityRank;
        return new SlowTickLogDecision(true, suppressed, sinceLastLog);
      }

      state.suppressed++;
      state.lastSeverityRank = Math.max(state.lastSeverityRank, severityRank);
      return new SlowTickLogDecision(false, 0L, sinceLastLog);
    }
  }

  private void clearSlowTickLogState(Ticked ticked) {
    if (ticked == null) {
      return;
    }

    slowTickLogStates.remove(slowTickKey(ticked));
  }

  private int slowSeverityRank(long elapsedMS) {
    if (elapsedMS >= 120L || elapsedMS >= SLOW_TICK_WARN_THRESHOLD_MS * 2L) {
      return 3;
    }
    if (elapsedMS >= 90L || elapsedMS * 10L >= SLOW_TICK_WARN_THRESHOLD_MS * 16L) {
      return 2;
    }
    if (elapsedMS >= 65L || elapsedMS * 10L >= SLOW_TICK_WARN_THRESHOLD_MS * 13L) {
      return 1;
    }
    return 0;
  }

  private String summarizeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }

    return root.getClass().getSimpleName() + ": " + message;
  }

  private boolean containsTickId(String id) {
    if (id == null) {
      return false;
    }

    for (int i = 0; i < ticklist.size(); i++) {
      if (id.equals(ticklist.get(i).getTid())) {
        return true;
      }
    }

    return false;
  }

  private void removeTickById(String id) {
    if (id == null) {
      return;
    }

    for (int i = ticklist.size() - 1; i >= 0; i--) {
      if (id.equals(ticklist.get(i).getTid())) {
        ticklist.remove(i);
      }
    }
  }

  private static final class SlowTickStats {
    private final ArrayDeque<Long> recentSlowMS = new ArrayDeque<>();
    private long runs;
    private long slowRuns;
    private long consecutiveSlowRuns;
    private long totalSlowMS;
    private long maxSlowMS;

    private synchronized SlowTickSnapshot record(long nowMS, long elapsedMS, boolean slow) {
      runs++;
      prune(nowMS);

      if (slow) {
        slowRuns++;
        consecutiveSlowRuns++;
        totalSlowMS += elapsedMS;
        maxSlowMS = Math.max(maxSlowMS, elapsedMS);
        recentSlowMS.addLast(nowMS);
        prune(nowMS);
      } else {
        consecutiveSlowRuns = 0L;
      }

      double average = slowRuns <= 0L ? 0D : (double) totalSlowMS / (double) slowRuns;
      return new SlowTickSnapshot(
          runs,
          slowRuns,
          consecutiveSlowRuns,
          recentSlowMS.size(),
          maxSlowMS,
          average
      );
    }

    private void prune(long nowMS) {
      while (!recentSlowMS.isEmpty() && nowMS - recentSlowMS.peekFirst() > SLOW_TICK_RECENCY_WINDOW_MS) {
        recentSlowMS.removeFirst();
      }
    }
  }

  private static final class SlowTickSnapshot {
    private final long runs;
    private final long slowRuns;
    private final long consecutiveSlowRuns;
    private final int slowRunsLastWindow;
    private final long maxSlowMS;
    private final double averageSlowMS;

    private SlowTickSnapshot(long runs,
                             long slowRuns,
                             long consecutiveSlowRuns,
                             int slowRunsLastWindow,
                             long maxSlowMS,
                             double averageSlowMS) {
      this.runs = runs;
      this.slowRuns = slowRuns;
      this.consecutiveSlowRuns = consecutiveSlowRuns;
      this.slowRunsLastWindow = slowRunsLastWindow;
      this.maxSlowMS = maxSlowMS;
      this.averageSlowMS = averageSlowMS;
    }
  }

  private static final class SlowTickLogState {
    private long lastLogMS;
    private int lastSeverityRank;
    private long suppressed;
  }

  private static final class SlowTickLogDecision {
    private final boolean shouldLog;
    private final long suppressed;
    private final long windowMS;

    private SlowTickLogDecision(boolean shouldLog, long suppressed, long windowMS) {
      this.shouldLog = shouldLog;
      this.suppressed = suppressed;
      this.windowMS = windowMS;
    }
  }

  private static final class FoliaViolationState {
    private long failures;
    private long backoffUntilMS;

    private synchronized FoliaViolationSnapshot record(long nowMS) {
      failures++;
      long backoffMS = FOLIA_BACKOFF_STEPS_MS[(int) Math.min(FOLIA_BACKOFF_STEPS_MS.length - 1, Math.max(0L, failures - 1))];
      backoffUntilMS = nowMS + backoffMS;
      boolean shouldLog = failures <= 2 || failures == 3 || failures % 10 == 0;
      return new FoliaViolationSnapshot(failures, backoffMS, shouldLog);
    }

    private synchronized boolean isBackoffActive(long nowMS) {
      return nowMS < backoffUntilMS;
    }
  }

  private static final class FoliaViolationSnapshot {
    private final long failures;
    private final long backoffMS;
    private final boolean shouldLog;

    private FoliaViolationSnapshot(long failures, long backoffMS, boolean shouldLog) {
      this.failures = failures;
      this.backoffMS = backoffMS;
      this.shouldLog = shouldLog;
    }
  }
}
