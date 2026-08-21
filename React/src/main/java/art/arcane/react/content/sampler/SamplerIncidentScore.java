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
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class SamplerIncidentScore extends ReactCachedSampler {
  public static final String ID = "incident-score";

  public record Contribution(String name, double weight, double value) {}

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
    return sampleOnMainThread(this::computeScore);
  }

  public List<Contribution> contributions() {
    double tickP95 = sample(SamplerTickMsP95.ID);
    double tickSpikeRate = sample(SamplerTickSpikeRate.ID);
    double gcPercent = sample(SamplerGcTimePercent.ID);
    double backlog = sample(SamplerSchedulerBacklog.ID);
    double backlogGrowth = Math.max(0D, sample(SamplerBacklogGrowthRate.ID));
    double pingP95 = sample(SamplerPlayerPingP95.ID);
    double topChunkCost = sample(SamplerTopChunkCost.ID);
    double redstoneBurstRate = sample(SamplerRedstoneBurstRate.ID);

    List<Contribution> list = new ArrayList<>(8);
    list.add(new Contribution(SamplerTickMsP95.ID, 0.30, tickP95));
    list.add(new Contribution(SamplerTickSpikeRate.ID, 0.15, tickSpikeRate));
    list.add(new Contribution(SamplerGcTimePercent.ID, 0.10, gcPercent));
    list.add(new Contribution(SamplerSchedulerBacklog.ID, 0.12, backlog));
    list.add(new Contribution(SamplerBacklogGrowthRate.ID, 0.08, backlogGrowth));
    list.add(new Contribution(SamplerPlayerPingP95.ID, 0.10, pingP95));
    list.add(new Contribution(SamplerTopChunkCost.ID, 0.08, topChunkCost));
    list.add(new Contribution(SamplerRedstoneBurstRate.ID, 0.07, redstoneBurstRate));
    return list;
  }

  private double computeScore() {
    double tickP95 = sample(SamplerTickMsP95.ID);
    double tickSpikeRate = sample(SamplerTickSpikeRate.ID);
    double gcPercent = sample(SamplerGcTimePercent.ID);
    double backlog = sample(SamplerSchedulerBacklog.ID);
    double backlogGrowth = Math.max(0D, sample(SamplerBacklogGrowthRate.ID));
    double pingP95 = sample(SamplerPlayerPingP95.ID);
    double topChunkCost = sample(SamplerTopChunkCost.ID);
    double redstoneBurstRate = sample(SamplerRedstoneBurstRate.ID);

    double score = 0;
    score += norm(tickP95, 50, 150) * 0.30;
    score += norm(tickSpikeRate, 5, 120) * 0.15;
    score += norm(gcPercent, 2, 25) * 0.10;
    score += norm(backlog, 10, 300) * 0.12;
    score += norm(backlogGrowth, 1, 80) * 0.08;
    score += norm(pingP95, 80, 350) * 0.10;
    score += norm(topChunkCost, 2, 25) * 0.08;
    score += norm(redstoneBurstRate, 2, 80) * 0.07;
    return SamplerMath.clip(score * 100D, 0, 100);
  }

  private double sample(String id) {
    Sampler sampler = getSampler(id);
    return sampler == null ? 0D : sampler.sample();
  }

  private double norm(double value, double min, double max) {
    if (max <= min) {
      return 0;
    }

    return SamplerMath.clip((value - min) / (max - min), 0, 1);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "INCIDENT";
  }
}
