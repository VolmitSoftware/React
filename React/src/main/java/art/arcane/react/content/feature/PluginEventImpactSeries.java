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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.EventController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PluginEventImpactSeries {
  private static final double ROLLING_DECAY_PER_SECOND = 0.96D;
  private static final long STALE_PLUGIN_MS = 600_000L;
  private static final Map<String, Double> rollingScore = new ConcurrentHashMap<>();
  private static final Map<String, Long> lastSeenMS = new ConcurrentHashMap<>();
  private static long lastUpdateMS = 0L;

  private PluginEventImpactSeries() {
  }

  static synchronized Snapshot snapshot() {
    EventController events = React.controller(EventController.class);
    if (events == null) {
      return new Snapshot(List.of(), 0D);
    }

    events.markSamplerActivity();
    long now = System.currentTimeMillis();
    decayRolling(now);

    Map<String, Double> timeByPlugin = events.snapshotPluginEventTimeMS();
    Map<String, Integer> callsByPlugin = events.snapshotPluginEventCalls();
    Map<String, Entry> current = mergeCurrent(timeByPlugin, callsByPlugin);

    for (Entry entry : current.values()) {
      if (entry.impact() <= 0D) {
        continue;
      }

      rollingScore.merge(entry.plugin(), entry.impact(), Double::sum);
      lastSeenMS.put(entry.plugin(), now);
    }

    prune(now);

    List<Entry> ranked = new ArrayList<>();
    for (Map.Entry<String, Double> entry : rollingScore.entrySet()) {
      String plugin = entry.getKey();
      double rolling = Math.max(0D, entry.getValue());
      Entry currentEntry = current.get(plugin);
      double currentMS = currentEntry == null ? 0D : currentEntry.currentMS();
      int currentCalls = currentEntry == null ? 0 : currentEntry.currentCalls();
      ranked.add(new Entry(plugin, rolling, currentMS, currentCalls));
    }

    ranked.sort(Comparator.comparingDouble(Entry::impact).reversed());
    double total = ranked.stream().mapToDouble(Entry::impact).sum();
    return new Snapshot(ranked, Math.max(0D, total));
  }

  private static Map<String, Entry> mergeCurrent(Map<String, Double> timeByPlugin, Map<String, Integer> callsByPlugin) {
    Map<String, Entry> merged = new HashMap<>();
    if (timeByPlugin != null) {
      for (Map.Entry<String, Double> entry : timeByPlugin.entrySet()) {
        String plugin = normalizePlugin(entry.getKey());
        double currentMS = Math.max(0D, entry.getValue() == null ? 0D : entry.getValue());
        merged.put(plugin, new Entry(plugin, currentMS, currentMS, 0));
      }
    }

    if (callsByPlugin != null) {
      for (Map.Entry<String, Integer> entry : callsByPlugin.entrySet()) {
        String plugin = normalizePlugin(entry.getKey());
        int calls = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
        Entry existing = merged.get(plugin);
        double currentMS = existing == null ? 0D : existing.currentMS();
        // Calls are a light secondary weight so call-heavy plugins still surface when timing is sparse.
        double impact = currentMS + (calls * 0.02D);
        merged.put(plugin, new Entry(plugin, impact, currentMS, calls));
      }
    }

    return merged;
  }

  private static void decayRolling(long now) {
    if (lastUpdateMS <= 0L) {
      lastUpdateMS = now;
      return;
    }

    double elapsedSeconds = Math.max(0D, (now - lastUpdateMS) / 1000D);
    lastUpdateMS = now;
    if (elapsedSeconds <= 0D || rollingScore.isEmpty()) {
      return;
    }

    double decay = Math.pow(ROLLING_DECAY_PER_SECOND, elapsedSeconds);
    rollingScore.replaceAll((plugin, score) -> score * decay);
  }

  private static void prune(long now) {
    rollingScore.entrySet().removeIf(entry -> {
      String plugin = entry.getKey();
      double value = entry.getValue() == null ? 0D : entry.getValue();
      long seen = lastSeenMS.getOrDefault(plugin, 0L);
      boolean stale = now - seen > STALE_PLUGIN_MS;
      boolean tiny = value < 0.01D;
      if (stale || tiny) {
        lastSeenMS.remove(plugin);
        return true;
      }
      return false;
    });
  }

  private static String normalizePlugin(String plugin) {
    if (plugin == null || plugin.isBlank()) {
      return "";
    }

    String trimmed = plugin.trim();
    if (trimmed.isBlank()) {
      return "";
    }

    String lower = trimmed.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(lower.length());
    boolean upper = true;
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if (c == '_' || c == '-') {
        out.append(' ');
        upper = true;
        continue;
      }
      if (Character.isWhitespace(c)) {
        out.append(c);
        upper = true;
        continue;
      }
      if (upper && Character.isLetter(c)) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
        upper = false;
      }
    }

    String value = out.toString().trim();
    return value;
  }

  record Entry(
      String plugin,
      double impact,
      double currentMS,
      int currentCalls
  ) {
  }

  record Snapshot(
      List<Entry> entries,
      double totalImpact
  ) {
  }
}
