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

package art.arcane.react.util.project.world;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;

import java.util.UUID;

public class ChainedColumn {
  private final String type;
  private final String drops;
  private final boolean underwater;
  private final BlockHost blocks;

  public ChainedColumn(BlockHost blocks, String type, String drops, boolean underwater) {
    this.blocks = blocks;
    this.type = type;
    this.drops = drops;
    this.underwater = underwater;
  }

  public boolean is(String blockType) {
    return type.equals(blockType);
  }

  public boolean isEmpty(GameLocation block) {
    return underwater
        ? "WATER".equals(blocks.blockType(block.worldId(), block.blockX(), block.blockY(), block.blockZ()))
        : blocks.isEmpty(block.worldId(), block.blockX(), block.blockY(), block.blockZ());
  }

  public void trigger(GameLocation block, int max) {
    trigger(null, block, max);
  }

  public void trigger(GameEntity player, GameLocation block, int max) {
    UUID worldId = block.worldId();
    int x = block.blockX();
    int y = block.blockY();
    int z = block.blockZ();
    int d = 0;
    boolean latched = false;
    String replacement = underwater ? "WATER" : "AIR";

    int maxh = Math.min(blocks.maxHeight(worldId), y + max) - 1;
    for (int i = y; i < maxh; i++) {
      if (type.equals(blocks.blockType(worldId, x, i, z))) {
        latched = true;
        d++;
        boolean hasNext = type.equals(blocks.blockType(worldId, x, i + 1, z));
        blocks.setBlock(worldId, x, i, z, replacement, !hasNext || i == maxh - 1);
      } else if (latched) {
        break;
      }
    }

    if (d > 0) {
      if (player != null) {
        blocks.giveItem(player, new GameLocation(worldId, x + 0.5, y + 0.5, z + 0.5), drops, d);
      } else {
        blocks.dropItem(new GameLocation(worldId, x + 0.5, y, z + 0.5), drops, d);
      }
    }
  }
}
