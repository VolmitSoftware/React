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

package com.volmit.react.core.controller;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.model.SampledChunk;
import com.volmit.react.model.SampledServer;
import com.volmit.react.util.format.C;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Comparator;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class ObserverController extends TickedObject implements IController {
    private transient final SampledServer sampled;

    public ObserverController() {
        super("react", "observer", 1000);
        sampled = new SampledServer();
    }

    @Edict.PlayerOnly
    @Edict.Command("/react chunk")
    @Edict.Aliases("/react c")
    public static void commandChunkSample() {
        SampledChunk c = React.controller(ObserverController.class).getSampled().getChunk(EdictContext.player().getLocation().getChunk());

        if (c != null) {
            for (String i : c.getValues().keySet()) {
                Sampler s = React.sampler(i);
                EdictContext.sender().sendMessage(s.getName() + ": " + s.format(c.getValues().get(i).get()));
            }
        } else {
            EdictContext.sender().sendMessage(C.RED + "This chunk is not sampled yet. Check back in a second!");
        }
    }

    @Edict.PlayerOnly
    @Edict.Command("/react chunk worst")
    @Edict.Aliases({"/react c worst", "/react c w"})
    public static void commandChunkWorst() {
        SampledChunk c = React.controller(ObserverController.class).absoluteWorst();

        if (c != null) {
            Block b = c.getChunk().getBlock(8, 0, 8);
            Player p = EdictContext.player();
            J.s(() -> p.teleport(c.getChunk().getWorld().getHighestBlockAt(b.getX(), b.getY()).getLocation()));

            for (String i : c.getValues().keySet()) {
                Sampler s = React.sampler(i);
                EdictContext.sender().sendMessage(s.getName() + ": " + s.format(c.getValues().get(i).get()));
            }
        } else {
            EdictContext.sender().sendMessage(C.RED + "No chunks are sampled yet. Check back in a second!");
        }
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
        return sampled.getWorlds().values().stream()
                .flatMap(i -> i.getChunks().values().stream())
                .max(Comparator.comparingDouble(SampledChunk::totalScore)
                        .thenComparingDouble(SampledChunk::highestSubScore))
                .orElse(null);
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
