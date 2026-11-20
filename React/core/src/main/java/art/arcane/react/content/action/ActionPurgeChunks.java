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

import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.AreaActionParams;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Purge Chunks action. Attempts to unload selected chunks to reduce active chunk pressure.")
public class ActionPurgeChunks extends ReactAction<ActionPurgeChunks.Params> {
  public static final String ID = "purge-chunks";

  public ActionPurgeChunks() {
    super(ID);
  }

  List<ChunkRef> pullChunks(ActionTicket<Params> ticket, int max) {
    return ticket.getParams().getArea().popChunks(max);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    return ReactLanguage.plain(
        ActionMessages.PURGED_CHUNKS,
        MessageArgument.untrusted("chunks", ticket.getCount()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    workOnAsync(ticket);
  }

  private void workOnAsync(ActionTicket<Params> ticket) {
    List<ChunkRef> chunks = pullChunks(ticket, ChunkActionHosts.actionSpeed());

    if (ticket.getTotalWork() <= 1) {
      ticket.setTotalWork(Math.max(1, ticket.getParams().getArea().estimatedTotalWork()));
    }

    if (!chunks.isEmpty()) {
      for (ChunkRef chunk : chunks) {
        purgeFolia(chunk, ticket.getParams());
      }

      ticket.addWork(chunks.size());
    }

    ticket.setCount(ticket.getParams().getPurgedChunks().get());
    if (chunks.isEmpty()
        && ticket.getParams().getInFlightChunks().get() <= 0
        && !ticket.getParams().getArea().isSelectionPending()) {
      ticket.setTotalWork(Math.max(1, ticket.getWork()));
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

  private void purgeFolia(ChunkRef chunk, Params params) {
    ChunkHost host = ChunkActionHosts.chunks();
    if (chunk == null || params == null || host == null) {
      return;
    }

    ReactWorld world = host.world(chunk.worldId()).orElse(null);
    if (world == null) {
      return;
    }
    int chunkX = chunk.chunkX();
    int chunkZ = chunk.chunkZ();
    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world.handle(), chunkX, chunkZ, () -> {
      try {
        if (!host.isLoaded(world, chunkX, chunkZ) || host.unload(world, chunkX, chunkZ)) {
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

    public Params withWorld(String worldKey) {
      area.setWorld(worldKey);
      area.setAllChunks(true);
      return this;
    }
  }
}
