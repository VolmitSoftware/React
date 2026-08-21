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

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Entity Bubbler tweak. Prevents selected projectile or utility entities from being suspended indefinitely in bubble columns.")
public class TweakEntityBubbler extends ReactTweak implements Listener {
  public static final String ID = "entity-bubbler";
  /**
   * List of entity types to check for crowding
   */
  @art.arcane.react.util.project.config.ConfigDoc(value = "Entity types that are removed when detected inside bubble-column or soul-sand bubble lift paths.", impact = "Add more types to aggressively clean bubble elevators, or remove types to allow those entities to travel in water columns.")
  private List<EntityType> entitiedToPreventFromBeingBubbled = Arrays.asList(
      EntityType.ARROW,
      EntityType.ARMOR_STAND,
      EntityType.MINECART,
      EntityType.ENDER_PEARL,
      EntityType.SNOWBALL
  );

  /**
   * Prevents the entity from existing if its being suspended by the soulsand
   * bubble column
   */

  public TweakEntityBubbler() {
    super(ID);
  }

  @Override
  public void onActivate() {
    for (EntityType entityType : entitiedToPreventFromBeingBubbled) {
      React.controller(EntityController.class).registerEntityTickListener(entityType, this::onCrowdCheck);
    }
  }

  /**
   * Checks if the entity is crowded
   */
  public void onCrowdCheck(Entity entity) {
    if (ReactProtection.isProtected(entity, ReactOperation.DESPAWN)) {
      return;
    }

    // Get the crowding factor of the entity when its ticked
    // If the entity is being bubbled, kill it
    if (isEntityBubbled(entity)) {
      kill(entity);
    }
  }

  /**
   * Checks if the entity is being bubbled
   */
  public boolean isEntityBubbled(Entity entity) {
    Location location = entity.getLocation();
    World world = location.getWorld();
    if (world == null)
      return false;
    Block block = world.getBlockAt(location);
    Block blockBelow = block.getRelative(BlockFace.DOWN);
    if (blockBelow.isLiquid() || block.isLiquid()) {
      if (block.getType().equals(Material.BUBBLE_COLUMN) || blockBelow.getType().equals(Material.BUBBLE_COLUMN)) {
        return true;
      } else if (block.getType().equals(Material.SOUL_SAND) || blockBelow.getType().equals(Material.SOUL_SAND)) {
        return true;
      }
    }
    return false;
  }


  private void kill(Entity entity) {
    int delay = (int) (20 * Math.random());
    if (!J.runEntity(entity, () -> React.kill(entity, 3), delay)) {
      React.kill(entity, 3);
    }
  }
}
