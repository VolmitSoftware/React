package art.arcane.react.core.integration;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricSource;
import art.arcane.react.api.metric.ReactModMetric;
import art.arcane.react.api.metric.ReactModMetricSource;
import art.arcane.react.util.project.registry.ItemIcons;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Bukkit plugins' {@link ReactMetricSource} services, as the platform-free registry reads them. */
public final class BukkitMetricSources {
  private BukkitMetricSources() {
  }

  public static List<ThirdPartyMetricRegistry.Registration> registrations() {
    Collection<RegisteredServiceProvider<ReactMetricSource>> found =
        Bukkit.getServicesManager().getRegistrations(ReactMetricSource.class);
    if (found == null) {
      return List.of();
    }
    List<ThirdPartyMetricRegistry.Registration> registrations = new ArrayList<>();
    for (RegisteredServiceProvider<ReactMetricSource> registration : found) {
      if (registration == null
          || registration.getProvider() == null
          || registration.getPlugin() == null
          || !registration.getPlugin().isEnabled()) {
        continue;
      }
      registrations.add(new ThirdPartyMetricRegistry.Registration(
          adapt(registration.getProvider()), registration.getPlugin().getName()));
    }
    return registrations;
  }

  public static ReactModMetricSource adapt(ReactMetricSource source) {
    return new Adapted(source);
  }

  public static ReactModMetric toCore(ReactMetric metric) {
    return new ReactModMetric(metric.key(), metric.kind(), metric.unit(), metric.displayName(),
        ItemIcons.id(metric.icon()), metric.decimals());
  }

  private record Adapted(ReactMetricSource source) implements ReactModMetricSource {
    @Override
    public String sourceId() {
      return source.sourceId();
    }

    @Override
    public List<ReactModMetric> metrics() {
      List<ReactMetric> metrics = source.metrics();
      if (metrics == null) {
        return null;
      }
      List<ReactModMetric> converted = new ArrayList<>(metrics.size());
      for (ReactMetric metric : metrics) {
        converted.add(metric == null ? null : toCore(metric));
      }
      return converted;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Adapted adapted && adapted.source == source;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(source);
    }
  }
}
