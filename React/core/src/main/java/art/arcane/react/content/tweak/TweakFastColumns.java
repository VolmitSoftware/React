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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.ChainedColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Columns tweak. Collapses plant columns in chained updates to reduce repeated per-block physics churn.")
public class TweakFastColumns extends ReactTweak {
  public static final String ID = "fast-columns";
  private transient ChainedColumn bamboo;
  private transient ChainedColumn sugarCane;
  private transient ChainedColumn kelp;
  private transient ChainedColumn cactus;
  private transient int maxColumnSize = 16;
  private transient final Set<ColumnCheck> pendingChecks = ConcurrentHashMap.newKeySet();
  private transient final List<Subscription> subscriptions = new ArrayList<>();
  private transient volatile WorldHost worlds;

  public TweakFastColumns() {
    super(ID);
  }

  @Override
  public void onActivate() {
    pendingChecks.clear();
    ReactPlatform platform = ReactPlatforms.get();
    worlds = platform.worldHost().orElse(null);
    BlockHost blocks = platform.blockHost().orElse(null);
    if (blocks == null) {
      return;
    }
    bamboo = new ChainedColumn(blocks, "BAMBOO", "BAMBOO", false);
    sugarCane = new ChainedColumn(blocks, "SUGAR_CANE", "SUGAR_CANE", false);
    cactus = new ChainedColumn(blocks, "CACTUS", "CACTUS", false);
    kelp = new ChainedColumn(blocks, "KELP_PLANT", "KELP", true);
    subscriptions.add(blocks.onBreak(ListenerPriority.MONITOR, true, this::onBreak));
    subscriptions.add(blocks.onPhysics(ListenerPriority.NORMAL, false, this::onPhysics));
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions.clear();
    pendingChecks.clear();
  }

  void onBreak(BlockHost.BlockEvent e) {
    ChainedColumn column = columnFor(e.type());
    if (column != null) {
      column.trigger(e.player(), e.location(), maxColumnSize);
    }
  }

  void onPhysics(BlockHost.PhysicsEvent e) {
    ChainedColumn column = columnFor(e.type());
    if (column == null) {
      return;
    }

    GameLocation location = e.location();
    ColumnCheck check = new ColumnCheck(location.worldId(), location.blockX(), location.blockY(), location.blockZ());
    if (!pendingChecks.add(check)) {
      return;
    }

    try {
      ReactWorld world = worlds == null ? null : worlds.world(check.worldId()).orElse(null);
      if (!J.runChunk(world, check.x() >> 4, check.z() >> 4, () -> runCheck(check, location, column), 2)) {
        pendingChecks.remove(check);
      }
    } catch (RuntimeException failure) {
      pendingChecks.remove(check);
      throw failure;
    }
  }

  private ChainedColumn columnFor(String type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case "BAMBOO" -> bamboo;
      case "SUGAR_CANE" -> sugarCane;
      case "CACTUS" -> cactus;
      case "KELP_PLANT" -> kelp;
      default -> null;
    };
  }

  private void runCheck(ColumnCheck check, GameLocation location, ChainedColumn column) {
    pendingChecks.remove(check);
    triggerIfEmpty(location, column);
  }

  private void triggerIfEmpty(GameLocation location, ChainedColumn column) {
    if (column.isEmpty(location)) {
      column.trigger(location, maxColumnSize);
    }
  }

  private record ColumnCheck(UUID worldId, int x, int y, int z) {
  }
}
