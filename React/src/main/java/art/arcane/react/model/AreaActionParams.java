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

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCoordinate;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCursor;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.bukkit.World;

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
  protected List<LoadedChunkTarget> chunks;
  @Builder.Default
  protected boolean allChunks = true;
  private transient boolean traversalInitialized;
  private transient int explicitChunkIndex;
  private transient int estimatedTotalWork;
  private transient LoadedChunkCursor loadedChunkCursor;
  private transient Queue<LoadedChunkTarget> coordinateWave;

  public synchronized LoadedChunkTarget popChunk() {
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

  public synchronized List<LoadedChunkTarget> popChunks(int maximum) {
    int limit = Math.max(0, Math.min(maximum, MAX_COORDINATE_WAVE));
    if (limit == 0) {
      return List.of();
    }
    List<LoadedChunkTarget> result = new ArrayList<>(limit);
    while (result.size() < limit) {
      LoadedChunkTarget target = popChunk();
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

  public synchronized void selectLoadedRadius(World selectedWorld, int centerX, int centerZ, int radius) {
    int safeRadius = Math.max(0, Math.min(radius, 10));
    List<LoadedChunkTarget> selected = new ArrayList<>();
    ObserverController observer = observer();
    if (selectedWorld != null && observer != null) {
      UUID worldId = selectedWorld.getUID();
      for (LoadedChunkCoordinate coordinate : observer.loadedChunkCoordinatesInRadius(
          worldId,
          centerX,
          centerZ,
          safeRadius
      )) {
        selected.add(new LoadedChunkTarget(worldId, coordinate.chunkX(), coordinate.chunkZ()));
      }
      world = WorldIdentity.serialize(selectedWorld);
    }
    chunks = selected;
    allChunks = false;
    resetTraversal();
  }

  public synchronized void setWorld(String world) {
    this.world = world;
    resetTraversal();
  }

  public synchronized void setChunks(List<LoadedChunkTarget> chunks) {
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
    ObserverController observer = observer();
    if (observer == null) {
      estimatedTotalWork = 0;
      traversalInitialized = true;
      return;
    }
    if (!observer.isLoadedChunkCoordinateIndexReady()) {
      estimatedTotalWork = 1;
      return;
    }
    if (world == null || world.isBlank()) {
      loadedChunkCursor = observer.openLoadedChunkCursor();
    } else {
      World selectedWorld = WorldIdentity.resolve(world).orElse(null);
      loadedChunkCursor = selectedWorld == null
          ? null
          : observer.openLoadedChunkCursor(selectedWorld.getUID());
    }
    estimatedTotalWork = loadedChunkCursor == null ? 0 : loadedChunkCursor.estimatedTotal();
    traversalInitialized = true;
  }

  private ObserverController observer() {
    return React.instance == null ? null : React.controller(ObserverController.class);
  }

  private void resetTraversal() {
    traversalInitialized = false;
    explicitChunkIndex = 0;
    estimatedTotalWork = 0;
    loadedChunkCursor = null;
    coordinateWave = null;
  }
}
