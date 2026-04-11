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

package art.arcane.react.core.controller;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledServer;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class ObserverController extends TickedObject implements IController {
  private transient final SampledServer sampled;

  public ObserverController() {
    super("react", "observer", 1000);
    sampled = new SampledServer();
  }


  @Override
  public void onTick() {

  }

  @Override
  public String getName() {
    return "Observer";
  }

  @Override
  public void start() {

  }

  @Override
  public void stop() {

  }

  @Override
  public void postStart() {

  }

  public SampledChunk absoluteWorst() {
    SampledChunk worst = null;
    double worstTotal = Double.NEGATIVE_INFINITY;
    double worstSub = Double.NEGATIVE_INFINITY;

    for (SampledWorld world : sampled.getWorlds().values()) {
      for (SampledChunk chunk : world.getChunks().values()) {
        double total = chunk.totalScore();
        double sub = chunk.highestSubScore();
        if (total > worstTotal || (total == worstTotal && sub > worstSub)) {
          worst = chunk;
          worstTotal = total;
          worstSub = sub;
        }
      }
    }

    return worst;
  }

  public AtomicDouble get(Block b, Sampler sampler) {
    return get(b.getChunk(), sampler);
  }

  public AtomicDouble get(Chunk c, Sampler sampler) {
    return sampled.getChunk(c).get(sampler.getId());
  }

  public Optional<Double> sample(Chunk c, Sampler s) {
    return sampled.optionalChunk(c).flatMap(i -> i.optional(s.getId())).map(AtomicDouble::get);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent event) {
    sampled.removeChunk(event.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldUnloadEvent event) {
    sampled.removeWorld(event.getWorld());
  }
}
