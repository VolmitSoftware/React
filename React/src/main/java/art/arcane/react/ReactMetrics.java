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

package art.arcane.react;

import art.arcane.react.api.feature.Feature;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.model.ReactConfiguration;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns every bStats type so the main class never references them. bStats is slimjar-provided:
 * any chart code in React.class makes the verifier resolve the relocated CustomChart during
 * main-class linking, before ApplicationBuilder.build() has injected the library (boot NCDFE).
 */
public final class ReactMetrics {
  private Metrics metrics;

  private ReactMetrics(Metrics metrics) {
    this.metrics = metrics;
  }

  public static ReactMetrics start(React plugin, int pluginId) {
    ReactMetrics runtime = new ReactMetrics(new Metrics(plugin, pluginId));
    runtime.registerCharts();
    return runtime;
  }

  // bStats invokes chart callables off the main thread (its own daemon thread on Folia),
  // so every accessor below must read concurrent or immutable state and tolerate a null controller.
  private void registerCharts() {
    metrics.addCustomChart(new AdvancedPie("active_features", () -> {
      FeatureController c = React.controller(FeatureController.class);

      if (c == null) {
        return null;
      }

      Map<String, Feature> active = c.getActiveFeatures();

      if (active == null) {
        return null;
      }

      Map<String, Integer> data = new HashMap<>();

      for (String id : active.keySet()) {
        data.put(id, 1);
      }

      return data;
    }));

    metrics.addCustomChart(new SingleLineChart("registered_features", () -> {
      FeatureController c = React.controller(FeatureController.class);

      if (c == null || c.getFeatures() == null) {
        return null;
      }

      return c.getFeatures().size();
    }));

    metrics.addCustomChart(new SimplePie("unsafe_bytecode",
        () -> String.valueOf(ReactConfiguration.get().isUnsafeBytecode())));
    metrics.addCustomChart(new SimplePie("bytecode_agent",
        () -> String.valueOf(art.arcane.react.core.bridge.BytecodeAgent.isInstalled())));
  }

  public void shutdown() {
    Metrics activeMetrics = metrics;
    metrics = null;
    if (activeMetrics != null) {
      activeMetrics.shutdown();
    }
  }
}
