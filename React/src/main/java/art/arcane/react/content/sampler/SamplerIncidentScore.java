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

package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class SamplerIncidentScore extends ReactCachedSampler {
  public static final String ID = "incident-score";
  private static final List<MetricSpec> METRICS = List.of(
      new MetricSpec(SamplerTickMsP95.ID, 0.30D, 50D, 150D),
      new MetricSpec(SamplerTickSpikeRate.ID, 0.15D, 5D, 120D),
      new MetricSpec(SamplerGcTimePercent.ID, 0.10D, 2D, 25D),
      new MetricSpec(SamplerSchedulerBacklog.ID, 0.12D, 10D, 300D),
      new MetricSpec(SamplerBacklogGrowthRate.ID, 0.08D, 1D, 80D),
      new MetricSpec(SamplerPlayerPingP95.ID, 0.10D, 80D, 350D),
      new MetricSpec(SamplerTopChunkCost.ID, 0.08D, 2D, 25D),
      new MetricSpec(SamplerRedstoneBurstRate.ID, 0.07D, 2D, 80D)
  );

  private transient volatile IncidentScoreSnapshot latestSnapshot = IncidentScoreSnapshot.empty();

  public SamplerIncidentScore() {
    super(ID, 1000);
  }

  @Override
  public Sampler getSampler(String id) {
    SampleController controller = React.controller(SampleController.class);
    return controller == null ? null : controller.getSampler(id);
  }

  @Override
  public Material getIcon() {
    return Material.TOTEM_OF_UNDYING;
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(this::captureSnapshot);
  }

  @Override
  public boolean isSampleAvailable() {
    return latestSnapshot.available();
  }

  public IncidentScoreSnapshot snapshot() {
    return latestSnapshot;
  }

  public List<IncidentEvidence> contributions() {
    return latestSnapshot.evidence();
  }

  @Override
  public void start() {
    latestSnapshot = IncidentScoreSnapshot.empty();
    super.start();
  }

  private double captureSnapshot() {
    List<CapturedMetric> captured = new ArrayList<>(METRICS.size());
    double availableWeight = 0D;
    for (MetricSpec spec : METRICS) {
      Sampler sampler = getSampler(spec.id());
      boolean available = sampler != null && sampler.isSampleAvailable();
      double value = 0D;
      if (available) {
        value = sampler.sample();
        available = Double.isFinite(value);
      }
      if (available && SamplerBacklogGrowthRate.ID.equals(spec.id())) {
        value = Math.max(0D, value);
      }
      if (available) {
        availableWeight += spec.weight();
      }
      captured.add(new CapturedMetric(spec, sampler, available, value));
    }

    List<IncidentEvidence> evidence = new ArrayList<>(captured.size());
    double score = 0D;
    for (CapturedMetric metric : captured) {
      MetricSpec spec = metric.spec();
      double pressure = metric.available()
          ? SamplerMath.clip((metric.value() - spec.minimum()) / (spec.maximum() - spec.minimum()), 0D, 1D)
          : 0D;
      double scorePoints = metric.available() && availableWeight > 0D
          ? pressure * (spec.weight() / availableWeight) * 100D
          : 0D;
      score += scorePoints;
      evidence.add(new IncidentEvidence(
          spec.id(),
          metric.sampler() == null ? title(spec.id()) : metric.sampler().getName(),
          metric.available(),
          metric.value(),
          metric.available() ? display(metric.sampler(), metric.value()) : "Unavailable",
          pressure,
          spec.weight(),
          scorePoints,
          spec.minimum(),
          spec.maximum()
      ));
    }

    double clippedScore = SamplerMath.clip(score, 0D, 100D);
    latestSnapshot = new IncidentScoreSnapshot(
        System.currentTimeMillis(),
        clippedScore,
        availableWeight > 0D,
        evidence
    );
    return clippedScore;
  }

  private String display(Sampler sampler, double value) {
    if (sampler == null) {
      return Form.f(value, 2);
    }
    String suffix = sampler.formattedSuffix(value);
    return sampler.formattedValue(value) + (suffix == null || suffix.isBlank() ? "" : " " + suffix);
  }

  private String title(String id) {
    String[] words = id.split("-");
    StringBuilder title = new StringBuilder(id.length());
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (!title.isEmpty()) {
        title.append(' ');
      }
      title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return title.toString();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "INCIDENT";
  }

  private record MetricSpec(String id, double weight, double minimum, double maximum) {
  }

  private record CapturedMetric(MetricSpec spec, Sampler sampler, boolean available, double value) {
  }

  public record IncidentScoreSnapshot(
      long sampledAtMs,
      double score,
      boolean available,
      List<IncidentEvidence> evidence
  ) {
    public IncidentScoreSnapshot {
      evidence = List.copyOf(evidence);
    }

    public static IncidentScoreSnapshot empty() {
      return new IncidentScoreSnapshot(0L, 0D, false, List.of());
    }
  }
}
