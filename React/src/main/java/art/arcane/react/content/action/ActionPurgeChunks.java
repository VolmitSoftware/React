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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Purge Chunks action. Attempts to unload selected chunks to reduce active chunk pressure.")
public class ActionPurgeChunks extends ReactAction<ActionPurgeChunks.Params> {
  public static final String ID = "purge-chunks";
  public static final String SHORT = "pc";

  public ActionPurgeChunks() {
    super(ID);
  }

  List<Chunk> pullChunks(ActionTicket<Params> ticket, int max) {
    List<Chunk> c = new ArrayList<>();

    for (int i = 0; i < max; i++) {
      Chunk cc = ticket.getParams().getArea().popChunk();

      if (cc == null) {
        break;
      }

      c.add(cc);
    }

    return c;
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    return "Purged " + ticket.getCount() + " Chunks in " + Form.duration(ticket.getDuration(), 1);
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    if (J.isFoliaThreading()) {
      workOnFolia(ticket);
      return;
    }

    List<Chunk> c = pullChunks(ticket, React.controller(ActionController.class).getActionSpeedMultiplier());

    if (ticket.getTotalWork() <= 1) {
      ticket.setTotalWork(ticket.getParams().getArea().getChunks().size());
    }

    if (c.isEmpty()) {
      ticket.complete();
    } else {
      for (Chunk i : c) {
        purge(i, ticket);
      }

      ticket.addWork(c.size());
    }
  }

  private void workOnFolia(ActionTicket<Params> ticket) {
    List<Chunk> chunks = pullChunks(ticket, React.controller(ActionController.class).getActionSpeedMultiplier());

    if (ticket.getTotalWork() <= 1 && ticket.getParams().getArea().getChunks() != null) {
      ticket.setTotalWork(Math.max(1, ticket.getParams().getArea().getChunks().size() + chunks.size()));
    }

    if (!chunks.isEmpty()) {
      for (Chunk chunk : chunks) {
        purgeFolia(chunk, ticket.getParams());
      }

      ticket.addWork(chunks.size());
    }

    ticket.setCount(ticket.getParams().getPurgedChunks().get());
    if (chunks.isEmpty() && ticket.getParams().getInFlightChunks().get() <= 0) {
      ticket.complete();
    }
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {

  }

  private void purge(Chunk c, ActionTicket<Params> ticket) {
    if (c == null || c.getWorld() == null) {
      return;
    }

    boolean unloaded = J.runChunkResult(c.getWorld(), c.getX(), c.getZ(), () -> {
      if (!c.isLoaded()) {
        return true;
      }

      return c.unload(true);
    }, false);

    if (unloaded) {
      ticket.addCount();
    }
  }

  private void purgeFolia(Chunk chunk, Params params) {
    if (chunk == null || chunk.getWorld() == null || params == null) {
      return;
    }

    World world = chunk.getWorld();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, chunkX, chunkZ, () -> {
      try {
        if (!world.isChunkLoaded(chunkX, chunkZ) || world.unloadChunk(chunkX, chunkZ, true)) {
          params.getPurgedChunks().incrementAndGet();
        }
      } finally {
        params.getInFlightChunks().decrementAndGet();
      }
    });

    if (!scheduled) {
      params.getInFlightChunks().decrementAndGet();
    }
  }


  @Builder
  @Data
  @Accessors(
      chain = true
  )
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Area selection used by purge chunks when choosing target chunks or entities.", impact = "Choose a tighter area for safer, local actions or a wider area for broader remediation.")
    private AreaActionParams area = AreaActionParams.builder().build();
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger purgedChunks = new AtomicInteger(0);

    public Params withWorld(World world) {
      area.setWorld(world.getName());
      area.setAllChunks(true);
      return this;
    }
  }
}
