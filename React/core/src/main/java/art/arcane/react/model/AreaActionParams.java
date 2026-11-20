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

package art.arcane.react.model;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkHost.LoadedChunkCursor;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AreaActionParams {
  private static final int MAX_COORDINATE_WAVE = 256;

  protected String world;
  @Singular
  protected List<ChunkRef> chunks;
  @Builder.Default
  protected boolean allChunks = true;
  private transient boolean traversalInitialized;
  private transient int explicitChunkIndex;
  private transient int estimatedTotalWork;
  private transient LoadedChunkCursor loadedChunkCursor;
  private transient Queue<ChunkRef> coordinateWave;

  public synchronized ChunkRef popChunk() {
    initializeTraversal();
    if (!traversalInitialized) {
      return null;
    }
    if (chunks != null && explicitChunkIndex < chunks.size()) {
      return chunks.get(explicitChunkIndex++);
    }
    if (coordinateWave == null) {
      coordinateWave = new ArrayDeque<>(MAX_COORDINATE_WAVE);
    }
    if (coordinateWave.isEmpty() && loadedChunkCursor != null) {
      coordinateWave.addAll(loadedChunkCursor.next(MAX_COORDINATE_WAVE));
    }
    return coordinateWave.poll();
  }

  public synchronized List<ChunkRef> popChunks(int maximum) {
    int limit = Math.max(0, Math.min(maximum, MAX_COORDINATE_WAVE));
    if (limit == 0) {
      return List.of();
    }
    List<ChunkRef> result = new ArrayList<>(limit);
    while (result.size() < limit) {
      ChunkRef target = popChunk();
      if (target == null) {
        break;
      }
      result.add(target);
    }
    return result;
  }

  public synchronized int estimatedTotalWork() {
    initializeTraversal();
    return estimatedTotalWork;
  }

  public synchronized boolean isSelectionPending() {
    initializeTraversal();
    return !traversalInitialized;
  }

  public synchronized void selectLoadedRadius(ReactWorld selectedWorld, int centerX, int centerZ, int radius) {
    int safeRadius = Math.max(0, Math.min(radius, 10));
    List<ChunkRef> selected = new ArrayList<>();
    ChunkHost host = chunkHost();
    if (selectedWorld != null && host != null) {
      UUID worldId = selectedWorld.id();
      for (LoadedChunkCoordinate coordinate : host.loadedInRadius(worldId, centerX, centerZ, safeRadius)) {
        selected.add(new ChunkRef(worldId, coordinate.chunkX(), coordinate.chunkZ()));
      }
      world = host.worldKey(selectedWorld);
    }
    chunks = selected;
    allChunks = false;
    resetTraversal();
  }

  public synchronized void setWorld(String world) {
    this.world = world;
    resetTraversal();
  }

  public synchronized void setChunks(List<ChunkRef> chunks) {
    this.chunks = chunks;
    resetTraversal();
  }

  public synchronized void setAllChunks(boolean allChunks) {
    this.allChunks = allChunks;
    resetTraversal();
  }

  private void initializeTraversal() {
    if (traversalInitialized) {
      return;
    }
    explicitChunkIndex = 0;
    coordinateWave = new ArrayDeque<>(MAX_COORDINATE_WAVE);
    if (chunks != null && !chunks.isEmpty()) {
      chunks = List.copyOf(chunks);
      estimatedTotalWork = chunks.size();
      traversalInitialized = true;
      return;
    }
    if (!allChunks) {
      estimatedTotalWork = 0;
      traversalInitialized = true;
      return;
    }
    ChunkHost host = chunkHost();
    if (host == null) {
      estimatedTotalWork = 0;
      traversalInitialized = true;
      return;
    }
    if (!host.isLoadedIndexReady()) {
      estimatedTotalWork = 1;
      return;
    }
    if (world == null || world.isBlank()) {
      loadedChunkCursor = host.openLoadedCursor();
    } else {
      ReactWorld selectedWorld = host.world(world).orElse(null);
      loadedChunkCursor = selectedWorld == null ? null : host.openLoadedCursor(selectedWorld.id());
    }
    estimatedTotalWork = loadedChunkCursor == null ? 0 : loadedChunkCursor.estimatedTotal();
    traversalInitialized = true;
  }

  private static ChunkHost chunkHost() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.chunkHost().orElse(null);
  }

  private void resetTraversal() {
    traversalInitialized = false;
    explicitChunkIndex = 0;
    estimatedTotalWork = 0;
    loadedChunkCursor = null;
    coordinateWave = null;
  }
}
